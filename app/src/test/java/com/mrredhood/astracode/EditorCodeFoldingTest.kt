package com.mrredhood.astracode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorCodeFoldingTest {
    @Test
    fun reportsUtf8BytesLineCountAndLongestLine() {
        val source = "fun x() {\n  println(\"🛰️\")\n}\n"
        val result = EditorCodeFolding.analyze("Main.kt", source)
        assertEquals(source.toByteArray(Charsets.UTF_8).size, result.metrics.utf8Bytes)
        assertEquals(4, result.metrics.lineCount)
        assertEquals("  println(\"🛰️\")".length, result.metrics.longestLine)
    }

    @Test
    fun detectsMultilineBlocksButIgnoresBracesInStringsAndComments() {
        val source = """
            fun run() {
                val text = "not a block } {"
                // } and { are comments
                /* { ignored } */
                if (ready) {
                    execute()
                }
            }
        """.trimIndent()
        val result = EditorCodeFolding.analyze("Main.kt", source)
        assertTrue(result.syntaxSupported)
        assertFalse(result.regions.isEmpty())
        assertEquals(0, result.regions.first().startLine)
        assertEquals(source.lines().lastIndex, result.regions.first().endLine)
        assertTrue(result.regions.any { it.startLine == 4 && it.endLine == 6 })
    }

    @Test
    fun leavesUnsupportedLanguagesUnfolded() {
        val result = EditorCodeFolding.analyze("script.py", "def run():\n    return {1: 2}")
        assertFalse(result.syntaxSupported)
        assertTrue(result.regions.isEmpty())
    }

    @Test
    fun boundsAnalysisAndVisibleFoldRows() {
        val tooManyLines = "\n".repeat(EditorCodeFolding.MAX_FOLD_LINES)
        val limited = EditorCodeFolding.analyze("Large.kt", tooManyLines)
        assertTrue(limited.limitExceeded)
        assertTrue(limited.regions.isEmpty())
        assertEquals(EditorCodeFolding.MAX_FOLD_LINES + 1, limited.metrics.lineCount)

        val source = "fun run() {\n  first()\n  second()\n}\nafter()"
        val analysis = EditorCodeFolding.analyze("Main.kt", source)
        val lines = source.split('\n')
        val folded = EditorCodeFolding.visibleLines(lines, analysis, setOf(0))
        assertTrue(folded.any { it.placeholder && it.text == "… 2 lines folded …" })
        assertTrue(folded.any { it.lineNumber == 3 && it.text == "}" })
        assertTrue(folded.any { it.text == "after()" })

        val indexedRows = EditorCodeFolding.createRows(analysis, setOf(0))
        val indexed = (0 until indexedRows.rowCount).map { indexedRows.rowAt(it, analysis, source) }
        assertEquals(folded, indexed)
    }
}
