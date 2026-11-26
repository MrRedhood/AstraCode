package com.astracode.connectors

import java.io.IOException
import java.util.concurrent.Callable
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
        it.status == AiAgentTaskStatus.COMPLETED || it.status == AiAgentTaskStatus.CONFLICTED
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
        val tasks = normalizeTasks(plannedTasks)
        require(tasks.size in 1..MAX_AGENT_TASKS) { "The agent planner returned an invalid task count" }
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
                                    files = files,
                                    fallbackPolicy = fallbackPolicy,
                                    fallbackModels = fallbackModels,
                                    fallbackBaseUrls = fallbackBaseUrls,
                                    enableTools = true,
                                    maxToolRounds = profile.maxToolRounds,
                                    onEvent = { event -> onEvent(withAgentId(event, task.id)) },
                                )
                                record(task.id, "completed", proposal.summary)
                                AiAgentTaskResult(task, AiAgentTaskStatus.COMPLETED, proposal)
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
        val changesByPath = results.flatMap { result ->
            result.proposal?.changes.orEmpty().map { result.task.id to it }
        }.groupBy { it.second.path }
        val conflicts = changesByPath.filterValues { it.map { pair -> pair.first }.distinct().size > 1 }.keys
        val mergedChanges = changesByPath
            .filterKeys { it !in conflicts }
            .values
            .mapNotNull { it.firstOrNull()?.second }
        val failed = results.count { it.status == AiAgentTaskStatus.FAILED || it.status == AiAgentTaskStatus.BLOCKED }
        val summary = buildString {
            append(results.size)
            append(" agent task(s) completed; ")
            append(failed)
            append(" failed or blocked; ")
            append(conflicts.size)
            append(" conflicting file path(s) withheld from the proposal.")
        }
        conflicts.forEach { path -> record(profile.id, "conflict", "Withheld overlapping change: " + path) }
        record(profile.id, "finished", summary)

        val finalResults = results.map { result ->
            if (result.proposal?.changes?.any { it.path in conflicts } == true) {
                result.copy(status = AiAgentTaskStatus.CONFLICTED)
            } else result
        }
        return AiAgentRunReport(
            summary = summary,
            proposal = AiWorkspaceProposal(summary, mergedChanges),
            taskResults = finalResults,
            conflictingPaths = conflicts,
            auditTrail = synchronized(audit) { audit.sortedBy { it.sequence } },
        )
    }

    private fun normalizeTasks(tasks: List<AiAgentTask>): List<AiAgentTask> {
        val ids = mutableSetOf<String>()
        val normalized = tasks.take(MAX_AGENT_TASKS).mapIndexed { index, task ->
            val id = task.id.trim().take(48).ifBlank { "agent-" + (index + 1) }
            require(ids.add(id)) { "The agent planner returned duplicate task IDs" }
            task.copy(
                id = id,
                title = task.title.trim().take(120).ifBlank { "Task " + (index + 1) },
                instructions = task.instructions.trim().take(4_000),
                dependsOn = task.dependsOn.distinct().filter { it != id },
            )
        }
        val allowedIds = normalized.map { it.id }.toSet()
        normalized.forEach { task ->
            require(task.dependsOn.all { it in allowedIds }) {
                "Agent task " + task.id + " depends on an unknown task"
            }
        }
        return normalized
    }

    private fun withAgentId(event: AiStreamEvent, agentId: String): AiStreamEvent = when (event) {
        is AiStreamEvent.TextDelta -> event.copy(agentId = agentId)
        is AiStreamEvent.Status -> event.copy(agentId = agentId)
        is AiStreamEvent.ToolStarted -> event.copy(agentId = agentId)
        is AiStreamEvent.ToolFinished -> event.copy(agentId = agentId)
    }

    private companion object {
        const val MAX_AGENT_TASKS = 4
    }
}
