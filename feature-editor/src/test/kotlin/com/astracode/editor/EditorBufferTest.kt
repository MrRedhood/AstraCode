package com.astracode.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditorBufferTest {
    @Test
    fun exposesUtf8ByteLengthAndLineState() {
        val buffer = EditorBuffer("ह\nb")
        assertEquals(4, buffer.byteLength)
        assertEquals(2, buffer.lineCount)
        buffer.insert(3, "x")
        assertEquals("हx\nb", buffer.text())
        assertTrue(buffer.canUndo)
        buffer.undo()
        assertEquals("ह\nb", buffer.text())
    }

    @Test
    fun exportsRawUtf8ForPersistenceAndProtocols() {
        val buffer = EditorBuffer("A😀")
        assertEquals("A😀".toByteArray(Charsets.UTF_8).toList(), buffer.bytes().toList())
    }
}
