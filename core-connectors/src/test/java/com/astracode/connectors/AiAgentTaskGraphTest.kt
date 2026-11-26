package com.astracode.connectors

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AiAgentTaskGraphTest {
    @Test
    fun normalizesTaskIdsAndPreservesDependencyOrder() {
        val result = AiAgentTaskGraph.normalize(
            listOf(
                AiAgentTask(" first ", " First ", " Inspect"),
                AiAgentTask("second", "Second", "Implement", listOf("first")),
                AiAgentTask("third", "Third", "Verify", listOf("second")),
            ),
        )
        assertEquals(listOf("first", "second", "third"), result.map { it.id })
        assertEquals(listOf("first"), result[1].dependsOn)
    }

    @Test
    fun acceptsOnlyExplicitWorkspaceInputPaths() {
        val tasks = listOf(
            AiAgentTask(
                id = "editor",
                title = "Editor",
                instructions = "Update editor code",
                inputPaths = listOf("feature-editor/Editor.kt"),
                expectedOutput = "A complete editor proposal",
            ),
        )

        val normalized = AiAgentTaskGraph.normalize(
            tasks = tasks,
            allowedInputPaths = setOf("feature-editor/Editor.kt", "app/MainActivity.kt"),
        )

        assertEquals(listOf("feature-editor/Editor.kt"), normalized.single().inputPaths)
        assertEquals("A complete editor proposal", normalized.single().expectedOutput)
    }

    @Test
    fun rejectsInputPathsOutsideApprovedWorkspaceSnapshot() {
        val tasks = listOf(
            AiAgentTask(
                id = "editor",
                title = "Editor",
                instructions = "Read another file",
                inputPaths = listOf("../private/secret.txt"),
                expectedOutput = "A result",
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            AiAgentTaskGraph.normalize(
                tasks = tasks,
                allowedInputPaths = setOf("feature-editor/Editor.kt"),
            )
        }
    }

    @Test
    fun deduplicatesInputPathsAndRejectsTooMany() {
        val task = AiAgentTask(
            id = "editor",
            title = "Editor",
            instructions = "Use selected files",
            inputPaths = listOf("a.kt", "a.kt", "b.kt"),
            expectedOutput = "A result",
        )
        val normalized = AiAgentTaskGraph.normalize(
            tasks = listOf(task),
            allowedInputPaths = setOf("a.kt", "b.kt"),
        )
        assertEquals(listOf("a.kt", "b.kt"), normalized.single().inputPaths)

        val tooMany = task.copy(inputPaths = (1..61).map { "file$it.kt" })
        assertFailsWith<IllegalArgumentException> {
            AiAgentTaskGraph.normalize(listOf(tooMany))
        }
    }

    @Test
    fun computesTransitiveAncestorsInDependencyOrder() {
        val first = AiAgentTask("first", "First", "One")
        val second = AiAgentTask("second", "Second", "Two", dependsOn = listOf("first"))
        val third = AiAgentTask("third", "Third", "Three", dependsOn = listOf("second"))
        val tasks = listOf(first, second, third)

        assertEquals(
            linkedSetOf("second", "first"),
            AiAgentTaskGraph.ancestorIds(third, tasks),
        )
    }

    @Test
    fun workerSeesOnlyAssignedInputsAndCompletedDependencyOutputs() {
        val base = AiAgentTask(
            id = "base",
            title = "Base",
            instructions = "Create shared helper",
            expectedOutput = "A shared helper",
        )
        val worker = AiAgentTask(
            id = "worker",
            title = "Worker",
            instructions = "Use helper",
            dependsOn = listOf("base"),
            inputPaths = listOf("src/Editor.kt"),
            expectedOutput = "Updated editor implementation",
        )
        val completed = mapOf(
            "base" to AiAgentTaskResult(
                task = base,
                status = AiAgentTaskStatus.COMPLETED,
                proposal = AiWorkspaceProposal(
                    "Created helper",
                    listOf(
                        AiWorkspaceFileChange(
                            AiChangeOperation.CREATE,
                            "src/Shared.kt",
                            "class Shared",
                            "Dependency output",
                        ),
                    ),
                ),
            ),
        )
        val available = listOf(
            WorkspaceFileSnapshot("src/Editor.kt", "class Editor"),
            WorkspaceFileSnapshot("src/Terminal.kt", "class Terminal"),
            WorkspaceFileSnapshot("src/Shared.kt", "class Shared"),
        )

        val selected = AiAgentTaskGraph.selectInputFiles(
            task = worker,
            allTasks = listOf(base, worker),
            availableFiles = available,
            completedResults = completed,
        )

        assertEquals(
            setOf("src/Editor.kt", "src/Shared.kt"),
            selected.map { it.path }.toSet(),
        )
    }

    @Test
    fun rejectsUnknownDependencies() {
        assertFailsWith<IllegalArgumentException> {
            AiAgentTaskGraph.normalize(
                listOf(AiAgentTask("worker", "Worker", "Do work", listOf("missing"))),
            )
        }
    }

    @Test
    fun rejectsSelfDependencies() {
        assertFailsWith<IllegalArgumentException> {
            AiAgentTaskGraph.normalize(
                listOf(AiAgentTask("worker", "Worker", "Do work", listOf("worker"))),
            )
        }
    }

    @Test
    fun rejectsDependencyCycles() {
        assertFailsWith<IllegalArgumentException> {
            AiAgentTaskGraph.normalize(
                listOf(
                    AiAgentTask("a", "A", "A", listOf("b")),
                    AiAgentTask("b", "B", "B", listOf("c")),
                    AiAgentTask("c", "C", "C", listOf("a")),
                ),
            )
        }
    }

    @Test
    fun rejectsMoreThanFourTasks() {
        assertFailsWith<IllegalArgumentException> {
            AiAgentTaskGraph.normalize(
                (1..5).map { AiAgentTask("task-$it", "Task $it", "Do work") },
            )
        }
    }
}
