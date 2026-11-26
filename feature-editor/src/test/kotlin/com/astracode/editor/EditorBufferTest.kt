package com.astracode.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditorBufferTest {
    @Test
    fun exposesLineAndHistoryState() {
        val buffer = EditorBuffer("a\nb")
        assertEquals(2, buffer.lineCount)
        buffer.insert(1, "x")
        assertEquals("ax\nb", buffer.text())
        assertTrue(buffer.canUndo)
        buffer.undo()
        assertEquals("a\nb", buffer.text())
    }
}
