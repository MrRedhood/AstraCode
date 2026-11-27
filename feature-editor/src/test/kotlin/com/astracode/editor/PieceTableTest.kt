package com.astracode.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.nio.charset.StandardCharsets.UTF_8

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
    fun byteOffsetsCountHindiAndEmojiByTheirUtf8Size() {
        val table = PieceTable("Aह😀B")
        assertEquals(1 + 3 + 4 + 1, table.byteLength)
        table.insert(8, "!")
        assertEquals("Aह😀!B", table.toString())
        assertEquals("😀!", table.substring(4, 9))
        assertEquals(0, table.lineOfOffset(8))
    }

    @Test
    fun rejectsOffsetsInsideUtf8Sequences() {
        val table = PieceTable("a😀b")
        assertFailsWith<IllegalArgumentException> { table.insert(2, "!") }
        assertFailsWith<IllegalArgumentException> { table.delete(1, 4) }
        table.insert(5, "!")
        assertEquals("a😀!b", table.toString())
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
    fun textFieldSnapshotsDiffAtUtf8Boundaries() {
        val table = PieceTable("A😀B")
        table.replaceDocumentText("A😁B")
        assertEquals("A😁B", table.toString())
        table.undo()
        assertEquals("A😀B", table.toString())
        table.replaceDocumentText("ह😀B")
        assertEquals("ह😀B", table.toString())
    }

    @Test
    fun lineCountAndLineOffsetsUseBytePositions() {
        val table = PieceTable("ह😀\nB\n")
        assertEquals(3, table.lineCount())
        assertEquals(0, table.lineOfOffset(7))
        assertEquals(1, table.lineOfOffset(8))
        assertEquals(2, table.lineOfOffset(table.byteLength))
    }

    @Test
    fun automaticCompactionPreservesUndoAndRedoHistory() {
        val largeInsert = "x".repeat(256 * 1024 + 1)
        val table = PieceTable("A", historyLimit = 4)

        table.insert(1, largeInsert)
        table.delete(1, table.byteLength)
        assertEquals("A", table.toString())

        // Deleting the large append buffer triggers automatic compaction.
        // Undo/redo must still replay both edits after the storage is rebuilt.
        assertTrue(table.canUndo)
        assertTrue(table.undo())
        assertEquals("A$largeInsert", table.toString())
        assertTrue(table.undo())
        assertEquals("A", table.toString())
        assertTrue(table.canRedo)

        assertTrue(table.redo())
        assertEquals("A$largeInsert", table.toString())
        assertTrue(table.redo())
        assertEquals("A", table.toString())
    }

    @Test
    fun compactPreservesBytesAndClearsHistory() {
        val table = PieceTable("ह")
        table.insert(3, "😀")
        table.compact()
        assertEquals("ह😀", table.toString())
        assertFalse(table.canUndo)
        assertEquals(1, table.pieceCount)
        assertEquals("ह😀".toByteArray(UTF_8).size, table.byteLength)
    }

    @Test
    fun lineOffsetsRemainCorrectAcrossManyPiecesAndUnicodeText() {
        val table = PieceTable("alphaomega")
        table.insert(5, "\n")
        table.insert(6, "β\n")
        table.insert(0, "head\n")
        table.insert(table.byteLength, "\nlast")

        assertEquals("head\nalpha\nβ\nomega\nlast", table.toString())
        val bytes = table.toByteArray()
        for (offset in 0..bytes.size) {
            val isBoundary = offset == bytes.size || (bytes[offset].toInt() and 0xC0) != 0x80
            if (isBoundary) {
                val expected = bytes.take(offset).count { it == 0x0A.toByte() }
                assertEquals(expected, table.lineOfOffset(offset), "Unexpected line at byte offset $offset")
            }
        }
    }

    @Test
    fun lineOffsetValidationRejectsNegativePastEndAndSplitUtf8Positions() {
        val table = PieceTable("Aह\nB")
        assertFailsWith<IllegalArgumentException> { table.lineOfOffset(-1) }
        assertFailsWith<IllegalArgumentException> { table.lineOfOffset(table.byteLength + 1) }
        assertFailsWith<IllegalArgumentException> { table.lineOfOffset(2) }
        assertEquals(1, table.lineOfOffset(table.byteLength))
    }

}
