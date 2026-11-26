package com.astracode.connectors

import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class AiAgentTask(
    val id: String,
    val title: String,
    val instructions: String,
    val dependsOn: List<String> = emptyList(),
    val inputPaths: List<String> = emptyList(),
    val expectedOutput: String = "",
)

data class AiAgentProfile(
    val id: String = "workspace-developer",
    val name: String = "Workspace developer",
    val role: String = "Inspect the workspace, implement the requested changes, and return reviewable file proposals.",
    val providerOverride: AiProvider? = null,
    val modelOverride: String? = null,
    val customBaseUrlOverride: String? = null,
    val allowedToolNames: Set<String> = AiToolRegistry.DEFAULT_TOOL_NAMES,
    val maxAgentTasks: Int = 4,
    val maxToolRounds: Int = 4,
    val maxParallelAgents: Int = 3,
    val maxContextBytes: Long = 600L * 1024L,
    val maxDependencyFileBytes: Int = 96 * 1024,
    val maxDependencyContextBytes: Long = 600L * 1024L,
) {
    init {
        require(maxAgentTasks in 1..4)
        require(maxToolRounds in 0..8)
        require(maxParallelAgents in 1..3)
        require(maxContextBytes in 1L..(4L * 1024L * 1024L))
        require(maxDependencyFileBytes in 1..(1024 * 1024))
        require(maxDependencyContextBytes in 1L..(4L * 1024L * 1024L))
        require(allowedToolNames.all { it in AiToolRegistry.DEFAULT_TOOL_NAMES }) {
            "Agent profiles may only enable declared read-only workspace tools"
        }
        require(modelOverride == null || modelOverride.isNotBlank())
        require(customBaseUrlOverride == null || customBaseUrlOverride.isNotBlank())
    }
}

enum class AiAgentTaskStatus {
    COMPLETED,
    FAILED,
    BLOCKED,
    CANCELLED,
    CONFLICTED,
}

data class AiAgentAuditEntry(
    val sequence: Int,
    val agentId: String,
    val action: String,
    val detail: String,
    val timestampEpochMillis: Long,
)

data class AiAgentTaskResult(
    val task: AiAgentTask,
    val status: AiAgentTaskStatus,
    val proposal: AiWorkspaceProposal?,
    val error: String? = null,
)

data class AiAgentRunReport(
    val summary: String,
    val proposal: AiWorkspaceProposal,
    val taskResults: List<AiAgentTaskResult>,
    val conflictingPaths: Set<String>,
    val auditTrail: List<AiAgentAuditEntry>,
) {
    val succeeded: Boolean get() = taskResults.all {
        it.status == AiAgentTaskStatus.COMPLETED
    }
}

/**
 * Runs a bounded agent plan. Tasks whose dependencies are already complete
 * execute concurrently. File writes are never performed by an agent; all
 * changes are returned to the existing user review/apply workflow.
 */
class AiAgentOrchestrator(
    private val assistant: WorkspaceAiAssistant,
) {
    @Throws(IOException::class)
    fun execute(
        provider: AiProvider,
        model: String,
        customBaseUrl: String,
        objective: String,
        files: List<WorkspaceFileSnapshot>,
        profile: AiAgentProfile = AiAgentProfile(),
        fallbackPolicy: AiFallbackPolicy = AiFallbackPolicy(),
        fallbackModels: Map<String, String> = emptyMap(),
        fallbackBaseUrls: Map<String, String> = emptyMap(),
        enableReadOnlyTools: Boolean = true,
        onEvent: (AiStreamEvent) -> Unit = {},
    ): AiAgentRunReport {
        require(objective.isNotBlank()) { "Describe the task for the agent" }
        val agentProvider = profile.providerOverride ?: provider
        val agentModel = profile.modelOverride?.trim()?.takeIf { it.isNotEmpty() } ?: model
        val agentBaseUrl = profile.customBaseUrlOverride ?: customBaseUrl
        if (agentProvider.customEndpoint) {
            require(agentBaseUrl.isNotBlank()) { "The selected agent profile needs an HTTPS endpoint" }
        }
        val initialContextBytes = files.sumOf {
            it.content.toByteArray(Charsets.UTF_8).size.toLong()
        }
        require(initialContextBytes <= profile.maxContextBytes) {
            "The agent profile context limit is smaller than the selected workspace context"
        }
        val audit = java.util.Collections.synchronizedList(mutableListOf<AiAgentAuditEntry>())
        var sequence = 0
        fun record(agentId: String, action: String, detail: String) {
            synchronized(audit) {
                sequence++
                audit += AiAgentAuditEntry(
                    sequence, agentId, action, detail.take(1_000), System.currentTimeMillis(),
                )
            }
        }

        record(
            profile.id,
            "planning",
            "Provider=" + agentProvider.id + " · model=" + agentModel +
                " · parallel=" + profile.maxParallelAgents +
                " · maxTasks=" + profile.maxAgentTasks,
        )
        onEvent(AiStreamEvent.Status("Agent planner: breaking the task into independent subtasks"))
        val plannedTasks = try {
            assistant.planAgentTasks(
                provider = agentProvider,
                model = agentModel,
                customBaseUrl = agentBaseUrl,
                objective = objective,
                files = files,
                fallbackPolicy = fallbackPolicy,
                fallbackModels = fallbackModels,
                fallbackBaseUrls = fallbackBaseUrls,
                onEvent = onEvent,
            )
        } catch (cancelled: AiRequestCancelledException) {
            val summary = "Agent run cancelled during planning. No worker tasks were started."
            record(profile.id, "cancelled", summary)
            return AiAgentRunReport(
                summary = summary,
                proposal = AiWorkspaceProposal(summary, emptyList()),
                taskResults = emptyList(),
                conflictingPaths = emptySet(),
                auditTrail = synchronized(audit) { audit.sortedBy { it.sequence } },
            )
        }
        val tasks = AiAgentTaskGraph.normalize(
            tasks = plannedTasks,
            maximumTasks = profile.maxAgentTasks,
            allowedInputPaths = files.map { it.path }.toSet(),
        )
        record(
            profile.id,
            "planned",
            tasks.joinToString("; ") { task ->
                task.id + " · inputs=[" + task.inputPaths.joinToString(",") +
                    "] · expected=" + task.expectedOutput.take(160)
            },
        )

        val completed = linkedMapOf<String, AiAgentTaskResult>()
        val pending = tasks.toMutableList()
        while (pending.isNotEmpty()) {
            val ready = pending.filter { task ->
                task.dependsOn.all { dependency ->
                    completed[dependency]?.status == AiAgentTaskStatus.COMPLETED
                }
            }
            if (ready.isEmpty()) {
                pending.forEach { task ->
                    completed[task.id] = AiAgentTaskResult(
                        task, AiAgentTaskStatus.BLOCKED, null,
                        "A dependency failed or the task plan contains a cycle.",
                    )
                    record(task.id, "blocked", "Dependency unavailable")
                }
                break
            }

            for (wave in ready.chunked(profile.maxParallelAgents)) {
                if (assistant.isCancellationRequested()) break
                val executor = Executors.newFixedThreadPool(wave.size)
                try {
                    val futures = executor.invokeAll(wave.map { task ->
                        Callable {
                            val dependencyNotes = task.dependsOn.mapNotNull { id ->
                                completed[id]?.let { dependency ->
                                    id + ": " + (dependency.proposal?.summary ?: dependency.error.orEmpty())
                                }
                            }
                            val taskFiles = runCatching {
                                applyDependencyProposals(
                                    originalFiles = files,
                                    task = task,
                                    completed = completed,
                                    maxFileBytes = profile.maxDependencyFileBytes,
                                    maxContextBytes = profile.maxDependencyContextBytes,
                                )
                            }.getOrElse { error ->
                                record(task.id, "failed", error.message ?: "Dependency context could not be built")
                                return@Callable AiAgentTaskResult(
                                    task,
                                    AiAgentTaskStatus.FAILED,
                                    null,
                                    error.message ?: "Dependency context could not be built",
                                )
                            }
                            val scopedFiles = AiAgentTaskGraph.selectInputFiles(
                                task = task,
                                allTasks = tasks,
                                availableFiles = taskFiles,
                                completedResults = completed,
                            )
                            val prompt = buildString {
                                appendLine("Main objective:")
                                appendLine(objective)
                                appendLine()
                                appendLine("Your assignment:")
                                appendLine(task.title)
                                appendLine(task.instructions)
                                appendLine("Expected output:")
                                appendLine(task.expectedOutput.ifBlank { "A complete, reviewable code proposal for this assignment." })
                                appendLine("Input files available to this agent:")
                                if (scopedFiles.isEmpty()) appendLine("(No source files were assigned.)")
                                else scopedFiles.forEach { appendLine("- " + it.path) }
                                if (dependencyNotes.isNotEmpty()) {
                                    appendLine()
                                    appendLine("Completed dependency outputs:")
                                    dependencyNotes.forEach { appendLine(it) }
                                    appendLine("Their proposed file contents are included in the workspace context as the current draft state.")
                                }
                                appendLine()
                                appendLine("Profile: " + profile.name + ". " + profile.role)
                                appendLine("Only return changes required for this assignment.")
                            }
                            record(task.id, "started", task.title)
                            onEvent(AiStreamEvent.Status("Agent " + task.title + " started", task.id))
                            try {
                                val proposal = assistant.createWorkspaceProposal(
                                    provider = agentProvider,
                                    model = agentModel,
                                    customBaseUrl = agentBaseUrl,
                                    task = prompt,
                                    files = scopedFiles,
                                    fallbackPolicy = fallbackPolicy,
                                    fallbackModels = fallbackModels,
                                    fallbackBaseUrls = fallbackBaseUrls,
                                    enableTools = enableReadOnlyTools && profile.allowedToolNames.isNotEmpty(),
                                    allowedToolNames = profile.allowedToolNames,
                                    maxToolRounds = profile.maxToolRounds,
                                    onEvent = { event ->
                                        when (event) {
                                            is AiStreamEvent.ToolStarted ->
                                                record(task.id, "tool_started", event.name)
                                            is AiStreamEvent.ToolFinished ->
                                                record(
                                                    task.id,
                                                    "tool_finished",
                                                    event.name + if (event.succeeded) " succeeded" else " failed",
                                                )
                                            else -> Unit
                                        }
                                        onEvent(withAgentId(event, task.id))
                                    },
                                )
                                record(task.id, "completed", proposal.summary)
                                AiAgentTaskResult(task, AiAgentTaskStatus.COMPLETED, proposal)
                            } catch (cancelled: AiRequestCancelledException) {
                                record(task.id, "cancelled", cancelled.message ?: "Request cancelled")
                                AiAgentTaskResult(
                                    task,
                                    AiAgentTaskStatus.CANCELLED,
                                    null,
                                    cancelled.message ?: "Request cancelled",
                                )
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                record(task.id, "failed", error.message ?: error.javaClass.simpleName)
                                AiAgentTaskResult(
                                    task, AiAgentTaskStatus.FAILED, null,
                                    error.message ?: "Agent task failed",
                                )
                            }
                        }
                    })
                    wave.zip(futures).forEach { (task, future) ->
                        val result = try {
                            future.get()
                        } catch (error: java.util.concurrent.ExecutionException) {
                            val cause = error.cause
                            if (cause is CancellationException) throw cause
                            val cancelled = cause as? AiRequestCancelledException
                            if (cancelled != null) {
                                record(task.id, "cancelled", cancelled.message ?: "Request cancelled")
                                AiAgentTaskResult(
                                    task,
                                    AiAgentTaskStatus.CANCELLED,
                                    null,
                                    cancelled.message ?: "Request cancelled",
                                )
                            } else {
                                AiAgentTaskResult(
                                    task,
                                    AiAgentTaskStatus.FAILED,
                                    null,
                                    cause?.message ?: error.message ?: "Agent execution failed",
                                )
                            }
                        }
                        completed[result.task.id] = result
                    }
                } finally {
                    executor.shutdownNow()
                    executor.awaitTermination(2, TimeUnit.SECONDS)
                }
            }
            pending.removeAll(ready.toSet())
            if (assistant.isCancellationRequested()) break
        }

        if (assistant.isCancellationRequested()) {
            tasks.filterNot { it.id in completed }.forEach { task ->
                completed[task.id] = AiAgentTaskResult(
                    task,
                    AiAgentTaskStatus.CANCELLED,
                    null,
                    "Cancelled before this task could start.",
                )
                record(task.id, "cancelled", "Cancelled before task start")
            }
        }

        val results = tasks.mapNotNull { completed[it.id] }
        val merge = AiAgentProposalMerger.merge(files, tasks, results)
        val finalResults = results.map { result ->
            if (result.task.id in merge.conflictedTaskIds) {
                result.copy(status = AiAgentTaskStatus.CONFLICTED)
            } else result
        }
        val completedCount = finalResults.count { it.status == AiAgentTaskStatus.COMPLETED }
        val failedCount = finalResults.count {
            it.status == AiAgentTaskStatus.FAILED || it.status == AiAgentTaskStatus.BLOCKED
        }
        val cancelledCount = finalResults.count { it.status == AiAgentTaskStatus.CANCELLED }
        val summary = buildString {
            append(completedCount)
            append(" agent task(s) completed; ")
            append(failedCount)
            append(" failed or blocked; ")
            append(cancelledCount)
            append(" cancelled; ")
            append(finalResults.count { it.status == AiAgentTaskStatus.CONFLICTED })
            append(" task(s) involved in ")
            append(merge.conflictingPaths.size)
            append(" conflicting file path(s) withheld from the proposal.")
        }
        merge.conflictingPaths.forEach { path ->
            record(profile.id, "conflict", "Withheld overlapping change: " + path)
        }
        record(profile.id, "finished", summary)
        return AiAgentRunReport(
            summary = summary,
            proposal = AiWorkspaceProposal(summary, merge.changes),
            taskResults = finalResults,
            conflictingPaths = merge.conflictingPaths,
            auditTrail = synchronized(audit) { audit.sortedBy { it.sequence } },
        )
    }

    /**
     * Applies completed dependency proposals to the next agent's in-memory
     * context. It never writes to disk and refuses to pass oversized artifacts.
     */
    private fun applyDependencyProposals(
        originalFiles: List<WorkspaceFileSnapshot>,
        task: AiAgentTask,
        completed: Map<String, AiAgentTaskResult>,
        maxFileBytes: Int,
        maxContextBytes: Long,
    ): List<WorkspaceFileSnapshot> {
        val taskByIdForDependencyOrder = completed.mapValues { it.value.task } + (task.id to task)
        fun dependsTransitively(taskId: String, ancestorId: String): Boolean {
            val pending = taskByIdForDependencyOrder[taskId]?.dependsOn.orEmpty().toMutableList()
            val visited = mutableSetOf<String>()
            while (pending.isNotEmpty()) {
                val dependency = pending.removeAt(pending.lastIndex)
                if (!visited.add(dependency)) continue
                if (dependency == ancestorId) return true
                pending += taskByIdForDependencyOrder[dependency]?.dependsOn.orEmpty()
            }
            return false
        }
        val current = linkedMapOf<String, WorkspaceFileSnapshot>()
        originalFiles.forEach { current[it.path] = it }
        val dependencyOrder = mutableListOf<String>()
        val visitedDependencies = mutableSetOf<String>()
        fun collectDependencies(taskId: String) {
            taskByIdForDependencyOrder[taskId]?.dependsOn.orEmpty().forEach { dependencyId ->
                if (visitedDependencies.add(dependencyId)) {
                    collectDependencies(dependencyId)
                    dependencyOrder += dependencyId
                }
            }
        }
        collectDependencies(task.id)

        val changesByPath = dependencyOrder.flatMap { dependencyId ->
            completed[dependencyId]?.proposal?.changes.orEmpty().map { dependencyId to it }
        }.groupBy { it.second.path }
        changesByPath.forEach { (path, changes) ->
            val taskIds = changes.map { it.first }.distinct()
            val hasIndependentEdits = taskIds.indices.any { first ->
                (first + 1 until taskIds.size).any { second ->
                    !dependsTransitively(taskIds[first], taskIds[second]) &&
                        !dependsTransitively(taskIds[second], taskIds[first])
                }
            }
            require(!hasIndependentEdits) {
                "Dependency agents proposed conflicting changes to " + path
            }
        }

        dependencyOrder.forEach { dependencyId ->
            val proposal = completed[dependencyId]?.proposal ?: return@forEach
            proposal.changes.forEach { change ->
                when (change.operation) {
                    AiChangeOperation.DELETE -> current.remove(change.path)
                    AiChangeOperation.CREATE, AiChangeOperation.MODIFY -> {
                        val bytes = change.content.toByteArray(Charsets.UTF_8).size
                        require(bytes <= maxFileBytes) {
                            "Dependency output is too large to pass safely to the next agent: " + change.path
                        }
                        val existing = current[change.path]
                        current[change.path] = WorkspaceFileSnapshot(
                            path = change.path,
                            content = change.content,
                            writeProtected = existing?.writeProtected ?: false,
                        )
                    }
                }
            }
        }
        val result = current.values.toList()
        val totalBytes = result.sumOf {
            it.content.toByteArray(Charsets.UTF_8).size.toLong()
        }
        require(totalBytes <= maxContextBytes) {
            "Dependency workspace context exceeds the safe agent context limit"
        }
        return result
    }

    private fun withAgentId(event: AiStreamEvent, agentId: String): AiStreamEvent = when (event) {
        is AiStreamEvent.TextDelta -> event.copy(agentId = agentId)
        is AiStreamEvent.Status -> event.copy(agentId = agentId)
        is AiStreamEvent.ToolStarted -> event.copy(agentId = agentId)
        is AiStreamEvent.ToolFinished -> event.copy(agentId = agentId)
    }

}
