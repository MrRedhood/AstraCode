package com.mrredhood.astracode

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceAutosavePolicyTest {
    @Test
    fun onlyRequestsSaveWhenStoredTextStillMatchesBaseline() {
        assertEquals(
            WorkspaceAutosaveDecision.Save,
            WorkspaceAutosavePolicy.evaluate("before", "before", false, "after"),
        )
        assertEquals(
            WorkspaceAutosaveDecision.Conflict,
            WorkspaceAutosavePolicy.evaluate("before", "changed elsewhere", false, "after"),
        )
        assertEquals(
            WorkspaceAutosaveDecision.Conflict,
            WorkspaceAutosavePolicy.evaluate("before", "before", true, "after"),
        )
    }

    @Test
    fun treatsACompletedIdenticalWriteAsAlreadySaved() {
        assertEquals(
            WorkspaceAutosaveDecision.Unchanged,
            WorkspaceAutosavePolicy.evaluate("before", "after", false, "after"),
        )
    }

    @Test
    fun skipsUnchangedDraftsAndRejectsOversizeUtf8Content() {
        assertEquals(
            WorkspaceAutosaveDecision.Unchanged,
            WorkspaceAutosavePolicy.evaluate("same", "same", false, "same"),
        )
        val overLimit = "€".repeat(WorkspaceAutosavePolicy.MAX_DRAFT_BYTES / 3 + 1)
        assertEquals(
            WorkspaceAutosaveDecision.TooLarge,
            WorkspaceAutosavePolicy.evaluate("", "", false, overLimit),
        )
    }
}
