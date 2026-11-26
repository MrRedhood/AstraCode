package com.astracode.connectors

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiAgentProposalMergerTest {
    @Test
    fun independentAgentsChangingSamePathWithholdThatPath() {
        val tasks = listOf(
            AiAgentTask("ui", "UI", "Change the screen"),
            AiAgentTask("logic", "Logic", "Change the state logic"),
        )
        val results = listOf(
            completed(
                tasks[0],
                change(AiChangeOperation.MODIFY, "app/Main.kt", "UI version"),
            ),
            completed(
                tasks[1],
                change(AiChangeOperation.MODIFY, "app/Main.kt", "Logic version"),
            ),
        )

        val merged = AiAgentProposalMerger.merge(
            originalFiles = listOf(WorkspaceFileSnapshot("app/Main.kt", "Original")),
            tasks = tasks,
            results = results,
        )

        assertTrue(merged.changes.isEmpty())
        assertEquals(setOf("app/Main.kt"), merged.conflictingPaths)
        assertEquals(setOf("ui", "logic"), merged.conflictedTaskIds)
    }

    @Test
    fun dependentEditsToExistingFileMergeInDependencyOrder() {
        val tasks = listOf(
            AiAgentTask("base", "Base", "Create the initial implementation"),
            AiAgentTask("refine", "Refine", "Improve the implementation", listOf("base")),
            AiAgentTask("finish", "Finish", "Finalize the implementation", listOf("refine")),
        )
        val results = listOf(
            completed(tasks[0], change(AiChangeOperation.MODIFY, "src/Engine.kt", "base")),
            completed(tasks[1], change(AiChangeOperation.MODIFY, "src/Engine.kt", "refined")),
            completed(tasks[2], change(AiChangeOperation.MODIFY, "src/Engine.kt", "final")),
        )

        val merged = AiAgentProposalMerger.merge(
            originalFiles = listOf(WorkspaceFileSnapshot("src/Engine.kt", "old")),
            tasks = tasks,
            results = results,
        )

        assertTrue(merged.conflictingPaths.isEmpty())
        assertEquals(1, merged.changes.size)
        assertEquals(AiChangeOperation.MODIFY, merged.changes.single().operation)
        assertEquals("final", merged.changes.single().content)
    }

    @Test
    fun dependentCreateThenModifyRemainsACreateProposal() {
        val tasks = listOf(
            AiAgentTask("create", "Create", "Create a new file"),
            AiAgentTask("modify", "Modify", "Improve that file", listOf("create")),
        )
        val results = listOf(
            completed(tasks[0], change(AiChangeOperation.CREATE, "src/New.kt", "first")),
            completed(tasks[1], change(AiChangeOperation.MODIFY, "src/New.kt", "final")),
        )

        val merged = AiAgentProposalMerger.merge(emptyList(), tasks, results)

        assertTrue(merged.conflictingPaths.isEmpty())
        assertEquals(AiChangeOperation.CREATE, merged.changes.single().operation)
        assertEquals("final", merged.changes.single().content)
    }

    @Test
    fun dependentCreateThenDeleteProducesNoFileChange() {
        val tasks = listOf(
            AiAgentTask("create", "Create", "Create a temporary file"),
            AiAgentTask("delete", "Delete", "Remove the temporary file", listOf("create")),
        )
        val results = listOf(
            completed(tasks[0], change(AiChangeOperation.CREATE, "tmp/generated.kt", "temporary")),
            completed(tasks[1], change(AiChangeOperation.DELETE, "tmp/generated.kt", "")),
        )

        val merged = AiAgentProposalMerger.merge(emptyList(), tasks, results)

        assertTrue(merged.changes.isEmpty())
        assertTrue(merged.conflictingPaths.isEmpty())
    }

    @Test
    fun independentChangesToDifferentPathsAreAllKept() {
        val tasks = listOf(
            AiAgentTask("editor", "Editor", "Change editor"),
            AiAgentTask("terminal", "Terminal", "Change terminal"),
        )
        val results = listOf(
            completed(tasks[0], change(AiChangeOperation.MODIFY, "editor/Editor.kt", "editor")),
            completed(tasks[1], change(AiChangeOperation.CREATE, "terminal/Panel.kt", "terminal")),
        )

        val merged = AiAgentProposalMerger.merge(emptyList(), tasks, results)

        assertTrue(merged.conflictingPaths.isEmpty())
        assertFalse(merged.conflictedTaskIds.isNotEmpty())
        assertEquals(
            setOf("editor/Editor.kt", "terminal/Panel.kt"),
            merged.changes.map { it.path }.toSet(),
        )
    }

    private fun completed(
        task: AiAgentTask,
        vararg changes: AiWorkspaceFileChange,
    ) = AiAgentTaskResult(
        task = task,
        status = AiAgentTaskStatus.COMPLETED,
        proposal = AiWorkspaceProposal("Proposal from " + task.id, changes.toList()),
    )

    private fun change(
        operation: AiChangeOperation,
        path: String,
        content: String,
    ) = AiWorkspaceFileChange(
        operation = operation,
        path = path,
        content = content,
        reason = "Test change",
    )
}
