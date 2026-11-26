package com.astracode.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PieceTableTest {
    @Test
    fun insertsAndDeletesAcrossPieceBoundaries() {
        val table = PieceTable("abcdef")
        table.insert(3, "XYZ")
        assertEquals("abcXYZdef", table.toString())
        assertEquals("cXY", table.delete(2, 5))
        assertEquals("abZdef", table.toString())
    }

    @Test
    fun replacesMiddleAndKeepsPrefixAndSuffix() {
        val table = PieceTable("fun main() {}")
        table.replace(4, 8, "launch")
        assertEquals("fun launch() {}", table.toString())
    }

    @Test
    fun undoAndRedoRestoreExactContent() {
        val table = PieceTable("one")
        table.insert(3, " two")
        table.replace(0, 3, "ONE")
        assertEquals("ONE two", table.toString())
        assertTrue(table.undo())
        assertEquals("one two", table.toString())
        assertTrue(table.undo())
        assertEquals("one", table.toString())
        assertTrue(table.redo())
        assertTrue(table.redo())
        assertEquals("ONE two", table.toString())
        assertFalse(table.canRedo)
    }

    @Test
    fun handlesUtf16SurrogatePairsAndNewlines() {
        val table = PieceTable("a😀b
c")
        assertEquals(2, table.lineCount())
        table.insert(3, "!")
        assertEquals("a😀!b
c", table.toString())
        assertEquals(0, table.lineOfOffset(3))
        assertEquals(1, table.lineOfOffset(table.length))
    }

    @Test
    fun textFieldSnapshotsBecomeMinimalEdits() {
        val table = PieceTable("alpha beta gamma")
        table.replaceDocumentText("alpha BETA gamma")
        assertEquals("alpha BETA gamma", table.toString())
        table.undo()
        assertEquals("alpha beta gamma", table.toString())
    }

    @Test
    fun compactPreservesTheDocumentAndClearsHistory() {
        val table = PieceTable("start")
        table.insert(5, "-value")
        table.compact()
        assertEquals("start-value", table.toString())
        assertFalse(table.canUndo)
        assertEquals(1, table.pieceCount)
    }
}
