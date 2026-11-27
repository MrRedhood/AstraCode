package com.astracode.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EditorBufferTest {
    @Test
    fun exposesUtf8ByteLengthAndLineState() {
        val buffer = EditorBuffer("ह\nb")
        assertEquals(5, buffer.byteLength)
        assertEquals(2, buffer.lineCount)
        buffer.insert(3, "x")
        assertEquals("हx\nb", buffer.text())
        assertEquals(1, buffer.lineOfOffset(5))
    }

    @Test
    fun exportsRawUtf8ForPersistenceAndProtocols() {
        val buffer = EditorBuffer("A😀")
        assertEquals("A😀".toByteArray(Charsets.UTF_8).toList(), buffer.bytes().toList())
    }

    @Test
    fun appliesWidgetRangeEditsThroughUtf8ByteOffsets() {
        val buffer = EditorBuffer("Aह😀B")

        assertEquals("ह", buffer.replaceTextRange(1, 2, "नम"))
        assertEquals("Aनम😀B", buffer.text())
        assertEquals("😀", buffer.replaceTextRange(3, 5, "🌍"))
        assertEquals("Aनम🌍B", buffer.text())
        assertEquals(buffer.text().toByteArray(Charsets.UTF_8).size, buffer.byteLength)
    }

    @Test
    fun boundedRangeEditsRejectOversizedDocumentsWithoutMutation() {
        val buffer = EditorBuffer("Aह😀B")
        val previousBytes = buffer.byteLength

        assertEquals(null, buffer.replaceTextRangeWithinLimit(1, 2, "longer", previousBytes.toLong()))
        assertEquals("Aह😀B", buffer.text())
        assertEquals(previousBytes, buffer.byteLength)

        assertEquals("A😀B", buffer.replaceTextRangeWithinLimit(1, 2, "", 5L))
        assertEquals("A😀B", buffer.text())
        assertEquals(6, buffer.byteLength)
    }

    @Test
    fun rejectsWidgetRangesThatSplitSurrogatePairs() {
        val buffer = EditorBuffer("A😀B")
        assertFailsWith<IllegalArgumentException> {
            buffer.replaceTextRange(2, 3, "x")
        }
        assertEquals("A😀B", buffer.text())
    }

    @Test
    fun mapsTextWidgetIndicesToAndFromUtf8LspPositions() {
        val buffer = EditorBuffer("Aह😀\nB")
        assertEquals(LspPosition(0, 8), buffer.lspPositionForTextIndex(4))
        assertEquals(LspPosition(1, 0), buffer.lspPositionForTextIndex(5))
        assertEquals(4, buffer.textIndexForLspPosition(LspPosition(0, 8)))
        assertEquals(5, buffer.textIndexForLspPosition(LspPosition(1, 0)))
        assertFailsWith<IllegalArgumentException> {
            buffer.textIndexForLspPosition(LspPosition(0, 2))
        }
    }

    @Test
    fun mapsTextWidgetIndicesAtTheUiBoundary() {
        val buffer = EditorBuffer("Aह😀B")
        assertEquals(1, buffer.byteOffsetForTextIndex(1))
        assertEquals(4, buffer.byteOffsetForTextIndex(2))
        assertEquals(8, buffer.byteOffsetForTextIndex(4))
        assertEquals(4, buffer.textIndexForByteOffset(8))
        assertFailsWith<IllegalArgumentException> { buffer.byteOffsetForTextIndex(3) }
        assertFailsWith<IllegalArgumentException> { buffer.textIndexForByteOffset(6) }
    }
}
