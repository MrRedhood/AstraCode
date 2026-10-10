package com.mrredhood.astracode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorSnapshotCodecTest {
    @Test
    fun roundTripsSnapshotContentAndMetadata() {
        val snapshot = EditorSnapshotRecord(
            id = "1791500000000-01234567-89ab-cdef-0123-456789abcdef",
            fileName = "MainActivity.kt",
            createdAtMillis = 1791500000000L,
            content = "fun main() {\n  println(\"你好 🛰️\")\n}\n",
        )
        assertEquals(snapshot, EditorSnapshotCodec.decode(requireNotNull(EditorSnapshotCodec.encode(snapshot))))
    }

    @Test
    fun acceptsSnapshotAtNewTwoMiBLimit() {
        val content = "x".repeat(EditorSnapshotCodec.MAX_CONTENT_BYTES)
        val record = EditorSnapshotRecord(
            "1791500000000-01234567-89ab-cdef-0123-456789abcdef",
            "large.txt",
            1791500000000L,
            content,
        )
        val encoded = requireNotNull(EditorSnapshotCodec.encode(record))
        assertEquals(content.length, requireNotNull(EditorSnapshotCodec.decode(encoded)).content.length)
    }

    @Test
    fun rejectsInvalidIdsOversizeContentAndCorruptRecords() {
        val invalid = EditorSnapshotRecord("invalid", "file.txt", 1L, "body")
        assertNull(EditorSnapshotCodec.encode(invalid))
        val oversized = EditorSnapshotRecord(
            "1791500000000-01234567-89ab-cdef-0123-456789abcdef",
            "file.txt",
            1L,
            "x".repeat(EditorSnapshotCodec.MAX_CONTENT_BYTES + 1),
        )
        assertNull(EditorSnapshotCodec.encode(oversized))
        assertNull(EditorSnapshotCodec.decode(byteArrayOf(1, 2, 3)))
    }
}

class EditorTextDiffTest {
    @Test
    fun reportsNoDifferenceForEqualContent() {
        val result = EditorTextDiff.compare("same\ntext", "same\ntext")
        assertEquals(listOf("No differences from this snapshot."), result.lines)
        assertFalse(result.approximate)
    }

    @Test
    fun representsInsertedRemovedAndChangedLines() {
        val result = EditorTextDiff.compare("alpha\nbeta\ngamma", "alpha\nBETA\ngamma\ndelta")
        assertTrue(result.lines.any { it == "- beta" })
        assertTrue(result.lines.any { it == "+ BETA" })
        assertTrue(result.lines.any { it == "+ delta" })
        assertTrue(result.lines.any { it == "  alpha" })
    }

    @Test
    fun summarizesLargeLineInputsWithoutSplitLists() {
        val commonPrefix = "same\n".repeat(100_000)
        val result = EditorTextDiff.compare(commonPrefix + "old", commonPrefix + "new")
        assertTrue(result.approximate)
        assertTrue(result.lines.any { it == "- old" })
        assertTrue(result.lines.any { it == "+ new" })
    }

    @Test
    fun usesBoundedSummaryForLargeInputs() {
        val old = (1..500).joinToString("\n") { "line-$it" }
        val new = (1..500).joinToString("\n") { if (it == 250) "changed-$it" else "line-$it" }
        val result = EditorTextDiff.compare(old, new)
        assertTrue(result.approximate)
        assertTrue(result.lines.first().contains("Large file diff"))
        assertTrue(result.lines.size <= 240)
    }
}
