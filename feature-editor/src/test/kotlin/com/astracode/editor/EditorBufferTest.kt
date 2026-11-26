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
