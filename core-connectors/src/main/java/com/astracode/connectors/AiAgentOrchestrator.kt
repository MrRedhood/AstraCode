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
)

data class AiAgentProfile(
    val id: String = "workspace-developer",
    val name: String = "Workspace developer",
    val role: String = "Inspect the workspace, implement the requested changes, and return reviewable file proposals.",
    val maxToolRounds: Int = 4,
    val maxParallelAgents: Int = 3,
) {
    init {
        require(maxToolRounds in 0..8)
        require(maxParallelAgents in 1..3)
    }
}

enum class AiAgentTaskStatus {
    COMPLETED,
    FAILED,
    BLOCKED,
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

        record(profile.id, "planning", "Creating bounded independent subtasks")
        onEvent(AiStreamEvent.Status("Agent planner: breaking the task into independent work items"))
        val plannedTasks = assistant.planAgentTasks(
            provider = provider,
            model = model,
            customBaseUrl = customBaseUrl,
            objective = objective,
            files = files,
            fallbackPolicy = fallbackPolicy,
            fallbackModels = fallbackModels,
            fallbackBaseUrls = fallbackBaseUrls,
            onEvent = onEvent,
        )
        val tasks = AiAgentTaskGraph.normalize(plannedTasks, MAX_AGENT_TASKS)
        record(profile.id, "planned", tasks.joinToString { it.id })

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

            ready.chunked(profile.maxParallelAgents).forEach { wave ->
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
                                applyDependencyProposals(files, task, completed)
                            }.getOrElse { error ->
                                record(task.id, "failed", error.message ?: "Dependency context could not be built")
                                return@Callable AiAgentTaskResult(
                                    task,
                                    AiAgentTaskStatus.FAILED,
                                    null,
                                    error.message ?: "Dependency context could not be built",
                                )
                            }
                            val prompt = buildString {
                                appendLine("Main objective:")
                                appendLine(objective)
                                appendLine()
                                appendLine("Your assignment:")
                                appendLine(task.title)
                                appendLine(task.instructions)
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
                                    provider = provider,
                                    model = model,
                                    customBaseUrl = customBaseUrl,
                                    task = prompt,
                                    files = taskFiles,
                                    fallbackPolicy = fallbackPolicy,
                                    fallbackModels = fallbackModels,
                                    fallbackBaseUrls = fallbackBaseUrls,
                                    enableTools = enableReadOnlyTools,
                                    maxToolRounds = profile.maxToolRounds,
                                    onEvent = { event -> onEvent(withAgentId(event, task.id)) },
                                )
                                record(task.id, "completed", proposal.summary)
                                AiAgentTaskResult(task, AiAgentTaskStatus.COMPLETED, proposal)
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
                    wave.zip(futures).forEach { (_, future) ->
                        val result = runCatching { future.get() }.getOrElse { error ->
                            val task = wave.getOrNull(futures.indexOf(future))
                                ?: AiAgentTask("unknown", "Agent", "")
                            AiAgentTaskResult(
                                task, AiAgentTaskStatus.FAILED, null,
                                error.message ?: "Agent execution failed",
                            )
                        }
                        completed[result.task.id] = result
                    }
                } finally {
                    executor.shutdownNow()
                    executor.awaitTermination(2, TimeUnit.SECONDS)
                }
            }
            pending.removeAll(ready.toSet())
        }

        val results = tasks.mapNotNull { completed[it.id] }
        val taskById = tasks.associateBy { it.id }
        val depthCache = mutableMapOf<String, Int>()
        fun dependencyDepth(taskId: String, visiting: MutableSet<String> = mutableSetOf()): Int {
            depthCache[taskId]?.let { return it }
            if (!visiting.add(taskId)) return 0
            val depth = 1 + (taskById[taskId]?.dependsOn.orEmpty()
                .maxOfOrNull { dependencyDepth(it, visiting.toMutableSet()) } ?: 0)
            visiting.remove(taskId)
            depthCache[taskId] = depth
            return depth
        }
        fun dependsTransitively(taskId: String, ancestorId: String): Boolean {
            val pendingDependencies = taskById[taskId]?.dependsOn.orEmpty().toMutableList()
            val visited = mutableSetOf<String>()
            while (pendingDependencies.isNotEmpty()) {
                val dependency = pendingDependencies.removeAt(pendingDependencies.lastIndex)
                if (!visited.add(dependency)) continue
                if (dependency == ancestorId) return true
                pendingDependencies += taskById[dependency]?.dependsOn.orEmpty()
            }
            return false
        }

        val changesByPath = results.flatMap { result ->
            result.proposal?.changes.orEmpty().map { result.task.id to it }
        }.groupBy { it.second.path }
        val conflicts = changesByPath.filterValues { changes ->
            val taskIds = changes.map { it.first }.distinct()
            taskIds.indices.any { first ->
                (first + 1 until taskIds.size).any { second ->
                    val left = taskIds[first]
                    val right = taskIds[second]
                    !dependsTransitively(left, right) && !dependsTransitively(right, left)
                }
            }
        }.keys
        val originalPaths = files.map { it.path }.toSet()
        val mergedChanges = changesByPath
            .filterKeys { it !in conflicts }
            .mapNotNull { (path, entries) ->
                val ordered = entries.sortedBy { dependencyDepth(it.first) }
                val finalChange = ordered.last().second
                if (ordered.size == 1) {
                    finalChange
                } else {
                    val existedBeforeAgents = path in originalPaths
                    when (finalChange.operation) {
                        AiChangeOperation.DELETE -> {
                            if (existedBeforeAgents) finalChange
                            else null
                        }
                        AiChangeOperation.CREATE, AiChangeOperation.MODIFY -> {
                            finalChange.copy(
                                operation = if (existedBeforeAgents) {
                                    AiChangeOperation.MODIFY
                                } else {
                                    AiChangeOperation.CREATE
                                },
                            )
                        }
                    }
                }
            }
        val finalResults = results.map { result ->
            if (result.proposal?.changes?.any { it.path in conflicts } == true) {
                result.copy(status = AiAgentTaskStatus.CONFLICTED)
            } else result
        }
        val completedCount = finalResults.count { it.status == AiAgentTaskStatus.COMPLETED }
        val failedCount = finalResults.count {
            it.status == AiAgentTaskStatus.FAILED || it.status == AiAgentTaskStatus.BLOCKED
        }
        val summary = buildString {
            append(completedCount)
            append(" agent task(s) completed; ")
            append(failedCount)
            append(" failed or blocked; ")
            append(finalResults.count { it.status == AiAgentTaskStatus.CONFLICTED })
            append(" task(s) involved in ")
            append(conflicts.size)
            append(" conflicting file path(s) withheld from the proposal.")
        }
        conflicts.forEach { path -> record(profile.id, "conflict", "Withheld overlapping change: " + path) }
        record(profile.id, "finished", summary)
        return AiAgentRunReport(
            summary = summary,
            proposal = AiWorkspaceProposal(summary, mergedChanges),
            taskResults = finalResults,
            conflictingPaths = conflicts,
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
                        require(bytes <= MAX_DEPENDENCY_FILE_BYTES) {
                            "Dependency output is too large to pass safely to the next agent: " + change.path
                        }
                        current[change.path] = WorkspaceFileSnapshot(change.path, change.content)
                    }
                }
            }
        }
        val result = current.values.toList()
        val totalBytes = result.sumOf {
            it.content.toByteArray(Charsets.UTF_8).size.toLong()
        }
        require(totalBytes <= MAX_DEPENDENCY_CONTEXT_BYTES) {
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

    private companion object {
        const val MAX_AGENT_TASKS = 4
        const val MAX_DEPENDENCY_FILE_BYTES = 96 * 1024
        const val MAX_DEPENDENCY_CONTEXT_BYTES = 600L * 1024L
    }
}
