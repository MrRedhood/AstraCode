package com.mrredhood.astracode

internal data class EditorTextEdit(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val replacements: Int,
)

/** Pure text helpers for find/replace in the editor's unsaved draft. */
internal object EditorTextActions {
    fun findNext(text: String, query: String, fromIndex: Int): Int? {
        if (query.isEmpty()) return null
        val start = fromIndex.coerceIn(0, text.length)
        val next = text.indexOf(query, start, ignoreCase = true)
        if (next >= 0) return next
        if (start > 0) {
            val wrapped = text.indexOf(query, 0, ignoreCase = true)
            if (wrapped >= 0 && wrapped < start) return wrapped
        }
        return null
    }

    fun countMatches(text: String, query: String): Int {
        if (query.isEmpty()) return 0
        var count = 0
        var cursor = 0
        while (cursor <= text.length - query.length) {
            val found = text.indexOf(query, cursor, ignoreCase = true)
            if (found < 0) break
            count++
            cursor = found + query.length
        }
        return count
    }

    fun matchNumber(text: String, query: String, matchStart: Int): Int? {
        if (query.isEmpty()) return null
        var ordinal = 0
        var cursor = 0
        while (cursor <= text.length - query.length) {
            val found = text.indexOf(query, cursor, ignoreCase = true)
            if (found < 0) return null
            ordinal++
            if (found == matchStart) return ordinal
            cursor = found + query.length
        }
        return null
    }

    fun replaceNext(text: String, query: String, replacement: String, fromIndex: Int): EditorTextEdit? {
        val start = findNext(text, query, fromIndex) ?: return null
        val updated = text.replaceRange(start, start + query.length, replacement)
        val caret = start + replacement.length
        return EditorTextEdit(updated, caret, caret, 1)
    }

    fun replaceAll(text: String, query: String, replacement: String): EditorTextEdit {
        if (query.isEmpty()) return EditorTextEdit(text, 0, 0, 0)
        val output = StringBuilder(text.length)
        var cursor = 0
        var count = 0
        while (cursor <= text.length - query.length) {
            val found = text.indexOf(query, cursor, ignoreCase = true)
            if (found < 0) break
            output.append(text, cursor, found)
            output.append(replacement)
            cursor = found + query.length
            count++
        }
        output.append(text, cursor, text.length)
        return EditorTextEdit(output.toString(), 0, 0, count)
    }
}
