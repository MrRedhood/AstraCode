package com.astracode.connectors

internal data class AiAgentProposalMergeResult(
    val changes: List<AiWorkspaceFileChange>,
    val conflictingPaths: Set<String>,
    val conflictedTaskIds: Set<String>,
)

/**
 * Merges file proposals without applying them to storage.
 *
 * Changes to the same path are allowed only when the tasks are in one
 * dependency chain. Parallel, unrelated agents touching the same path cause
 * that path to be withheld from the final proposal.
 */
internal object AiAgentProposalMerger {
    fun merge(
        originalFiles: List<WorkspaceFileSnapshot>,
        tasks: List<AiAgentTask>,
        results: List<AiAgentTaskResult>,
    ): AiAgentProposalMergeResult {
        val taskById = tasks.associateBy { it.id }
        val changesByPath = results.flatMap { result ->
            result.proposal?.changes.orEmpty().map { result.task.id to it }
        }.groupBy { it.second.path }

        fun dependsTransitively(taskId: String, ancestorId: String): Boolean {
            val pending = taskById[taskId]?.dependsOn.orEmpty().toMutableList()
            val visited = mutableSetOf<String>()
            while (pending.isNotEmpty()) {
                val dependency = pending.removeAt(pending.lastIndex)
                if (!visited.add(dependency)) continue
                if (dependency == ancestorId) return true
                pending += taskById[dependency]?.dependsOn.orEmpty()
            }
            return false
        }

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

        val conflictedTaskIds = changesByPath
            .filterKeys { it in conflicts }
            .values
            .flatten()
            .map { it.first }
            .toSet()

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

        val originalPaths = originalFiles.map { it.path }.toSet()
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
                            if (existedBeforeAgents) finalChange else null
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

        return AiAgentProposalMergeResult(
            changes = mergedChanges,
            conflictingPaths = conflicts,
            conflictedTaskIds = conflictedTaskIds,
        )
    }
}
