package com.astracode.connectors

/**
 * Validates model-created agent tasks before any worker is started.
 * This class contains no provider, Android, networking or filesystem code.
 */
internal object AiAgentTaskGraph {
    fun normalize(
        tasks: List<AiAgentTask>,
        maximumTasks: Int = MAX_AGENT_TASKS,
        allowedInputPaths: Set<String>? = null,
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
            val inputPaths = task.inputPaths.map { it.trim() }.distinct()
            require(inputPaths.size <= MAX_INPUT_PATHS) {
                "Agent task " + id + " requested too many input files"
            }
            require(inputPaths.none { it.isBlank() || it.length > MAX_PATH_CHARS }) {
                "Agent task " + id + " contains an invalid input path"
            }
            if (allowedInputPaths != null) {
                require(inputPaths.all { it in allowedInputPaths }) {
                    "Agent task " + id + " requested a file outside the approved workspace context"
                }
            }
            task.copy(
                id = id,
                title = task.title.trim().take(MAX_TASK_TITLE_CHARS)
                    .ifBlank { "Task " + (index + 1) },
                instructions = task.instructions.trim().take(MAX_TASK_INSTRUCTION_CHARS),
                dependsOn = dependencies,
                inputPaths = inputPaths,
                expectedOutput = task.expectedOutput.trim().take(MAX_EXPECTED_OUTPUT_CHARS)
                    .ifBlank { "A complete, reviewable result for the assigned task." },
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


    fun ancestorIds(
        task: AiAgentTask,
        allTasks: List<AiAgentTask>,
    ): Set<String> {
        val byId = allTasks.associateBy { it.id }
        val result = linkedSetOf<String>()
        fun visit(taskId: String) {
            byId[taskId]?.dependsOn.orEmpty().forEach { dependencyId ->
                if (result.add(dependencyId)) visit(dependencyId)
            }
        }
        visit(task.id)
        return result
    }

    private const val MAX_AGENT_TASKS = 4
    private const val MAX_TASK_ID_CHARS = 48
    private const val MAX_TASK_TITLE_CHARS = 120
    private const val MAX_TASK_INSTRUCTION_CHARS = 4_000
    private const val MAX_EXPECTED_OUTPUT_CHARS = 2_000
    private const val MAX_INPUT_PATHS = 60
    private const val MAX_PATH_CHARS = 512
}
