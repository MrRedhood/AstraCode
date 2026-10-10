package com.mrredhood.astracode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditorDraftRecordCodecTest {
    @Test
    fun roundTripsUnicodeDraftAndBaselineFingerprint() {
        val baseline = "fun main() = println(\"hello\")"
        val record = EditorDraftSnapshot(
            baselineFingerprint = EditorDraftRecordCodec.fingerprint(baseline),
            text = "変更したコード 🛰️\nprintln(\"draft\")",
        )
        assertEquals(record, EditorDraftRecordCodec.decode(requireNotNull(EditorDraftRecordCodec.encode(record))))
    }

    @Test
    fun fingerprintIsStableAndContentSensitive() {
        assertEquals(EditorDraftRecordCodec.fingerprint("same"), EditorDraftRecordCodec.fingerprint("same"))
        assertNotEquals(EditorDraftRecordCodec.fingerprint("same"), EditorDraftRecordCodec.fingerprint("changed"))
    }

    @Test
    fun rejectsOversizedDrafts() {
        val tooLarge = "x".repeat(EditorDraftRecordCodec.MAX_DRAFT_BYTES + 1)
        assertNull(EditorDraftRecordCodec.encode(EditorDraftSnapshot(EditorDraftRecordCodec.fingerprint("base"), tooLarge)))
    }

    @Test
    fun acceptsDraftAtNewTwoMiBLimit() {
        val text = "x".repeat(EditorDraftRecordCodec.MAX_DRAFT_BYTES)
        val encoded = requireNotNull(EditorDraftRecordCodec.encode(
            EditorDraftSnapshot(EditorDraftRecordCodec.fingerprint("base"), text),
        ))
        assertEquals(text.length, requireNotNull(EditorDraftRecordCodec.decode(encoded)).text.length)
    }

    @Test
    fun rejectsCorruptTruncatedAndTrailingRecords() {
        val valid = requireNotNull(EditorDraftRecordCodec.encode(EditorDraftSnapshot(EditorDraftRecordCodec.fingerprint("base"), "draft")))
        assertNull(EditorDraftRecordCodec.decode(byteArrayOf(1, 2, 3)))
        assertNull(EditorDraftRecordCodec.decode(valid.copyOf(valid.size - 1)))
        assertNull(EditorDraftRecordCodec.decode(valid + byteArrayOf(0)))
    }

    @Test
    fun rejectsMalformedFingerprints() {
        assertNull(EditorDraftRecordCodec.encode(EditorDraftSnapshot("not-a-hash", "draft")))
    }
}
