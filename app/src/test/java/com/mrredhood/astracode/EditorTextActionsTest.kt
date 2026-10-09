package com.mrredhood.astracode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditorTextActionsTest {
    @Test
    fun findNextIsCaseInsensitiveAndWraps() {
        assertEquals(0, EditorTextActions.findNext("Alpha alpha", "alpha", 0))
        assertEquals(6, EditorTextActions.findNext("Alpha alpha", "ALPHA", 5))
        assertEquals(0, EditorTextActions.findNext("Alpha alpha", "alpha", 8))
        assertNull(EditorTextActions.findNext("Alpha", "missing", 0))
        assertNull(EditorTextActions.findNext("Alpha", "", 0))
    }

    @Test
    fun countsNonOverlappingMatchesCaseInsensitively() {
        assertEquals(3, EditorTextActions.countMatches("One one ONE", "one"))
        assertEquals(2, EditorTextActions.countMatches("aaaa", "aa"))
        assertEquals(0, EditorTextActions.countMatches("nothing", "x"))
        assertEquals(0, EditorTextActions.countMatches("anything", ""))
    }

    @Test
    fun replaceNextReplacesMatchAtOrAfterStartingOffset() {
        val result = EditorTextActions.replaceNext("one ONE", "one", "two", 3)
        requireNotNull(result)
        assertEquals("one two", result.text)
        assertEquals(7, result.selectionStart)
        assertEquals(7, result.selectionEnd)
        assertEquals(1, result.replacements)
    }

    @Test
    fun replaceAllTouchesOnlyOriginalNonOverlappingMatches() {
        val result = EditorTextActions.replaceAll("One one ONE!", "one", "two")
        assertEquals("two two two!", result.text)
        assertEquals(3, result.replacements)
        assertEquals(0, result.selectionStart)
        assertEquals(0, result.selectionEnd)
    }

    @Test
    fun replaceAllWithNoMatchPreservesText() {
        val result = EditorTextActions.replaceAll("leave unchanged", "missing", "new")
        assertEquals("leave unchanged", result.text)
        assertEquals(0, result.replacements)
    }
}
