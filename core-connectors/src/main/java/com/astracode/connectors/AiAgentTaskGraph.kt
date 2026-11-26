package com.astracode.connectors

/**
 * Validates model-created agent tasks before any worker is started.
 * This class contains no provider, Android, networking or filesystem code.
 */
internal object AiAgentTaskGraph {
    fun normalize(
        tasks: List<AiAgentTask>,
        maximumTasks: Int = MAX_AGENT_TASKS,
    ): List<AiAgentTask> {
        require(maximumTasks in 1..MAX_AGENT_TASKS) {
            "The configured agent task limit must be between 1 and " + MAX_AGENT_TASKS
        }
        require(tasks.size in 1..maximumTasks) {
            "The agent planner must return between one and " + maximumTasks + " tasks"
        }

        val ids = mutableSetOf<String>()
        val normalized = tasks.mapIndexed { index, task ->
            val id = task.id.trim().take(MAX_TASK_ID_CHARS)
                .ifBlank { "agent-" + (index + 1) }
            require(ids.add(id)) { "The agent planner returned duplicate task IDs" }
            val dependencies = task.dependsOn.map { it.trim() }.distinct()
            require(id !in dependencies) {
                "Agent task " + id + " cannot depend on itself"
            }
            task.copy(
                id = id,
                title = task.title.trim().take(MAX_TASK_TITLE_CHARS)
                    .ifBlank { "Task " + (index + 1) },
                instructions = task.instructions.trim().take(MAX_TASK_INSTRUCTION_CHARS),
                dependsOn = dependencies,
            )
        }

        val taskById = normalized.associateBy { it.id }
        normalized.forEach { task ->
            require(task.dependsOn.all { it in taskById }) {
                "Agent task " + task.id + " depends on an unknown task"
            }
        }

        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        fun visit(taskId: String) {
            if (taskId in visited) return
            require(visiting.add(taskId)) {
                "The agent planner returned a cyclic dependency graph"
            }
            taskById.getValue(taskId).dependsOn.forEach(::visit)
            visiting.remove(taskId)
            visited.add(taskId)
        }
        normalized.forEach { visit(it.id) }
        return normalized
    }

    private const val MAX_AGENT_TASKS = 4
    private const val MAX_TASK_ID_CHARS = 48
    private const val MAX_TASK_TITLE_CHARS = 120
    private const val MAX_TASK_INSTRUCTION_CHARS = 4_000
}
