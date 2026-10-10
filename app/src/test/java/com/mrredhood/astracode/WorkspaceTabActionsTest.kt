package com.mrredhood.astracode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class WorkspaceTabActionsTest {
    private fun tab(id: String, name: String = "$id.kt") =
        WorkspaceEditorTab(id, name, "text/plain", true)

    @Test
    fun openingExistingTabUpdatesMetadataWithoutDuplicatingIt() {
        val original = listOf(tab("a"), tab("b"))
        val opened = WorkspaceTabActions.open(original, tab("a", "Renamed.kt"))
        assertEquals(2, opened?.size)
        assertEquals("Renamed.kt", opened?.first()?.displayName)
        assertEquals(listOf("a", "b"), opened?.map { it.documentId })
    }

    @Test
    fun newTabsKeepOpeningOrderAndRespectTheLimit() {
        var tabs = emptyList<WorkspaceEditorTab>()
        repeat(WorkspaceTabActions.MAX_OPEN_TABS) { index ->
            tabs = requireNotNull(WorkspaceTabActions.open(tabs, tab("file-$index")))
        }
        assertEquals(WorkspaceTabActions.MAX_OPEN_TABS, tabs.size)
        assertNull(WorkspaceTabActions.open(tabs, tab("overflow")))
    }

    @Test
    fun closingOneTabLeavesTheOtherTabsInTheirOriginalOrder() {
        val tabs = listOf(tab("a"), tab("b"), tab("c"))
        val remaining = WorkspaceTabActions.close(tabs, "b")
        assertEquals(listOf("a", "c"), remaining.map { it.documentId })
        assertNotNull(remaining.firstOrNull { it.documentId == "a" })
    }

    @Test
    fun closingUnknownTabDoesNotChangeTheList() {
        val tabs = listOf(tab("a"), tab("b"))
        assertEquals(tabs, WorkspaceTabActions.close(tabs, "missing"))
    }
}
