package com.astracode.filesystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Utf8RepairDecoderTest {
    @Test
    fun `valid Unicode is identical across every read window size`() {
        val expected = "ASCII ¢ € 𐍈 🧪"
        val bytes = expected.toByteArray(Charsets.UTF_8)
        for (windowSize in 1..bytes.size) {
            assertEquals(expected, decodeInWindows(bytes, windowSize).first, "window size=$windowSize")
        }
    }

    @Test
    fun `malformed sequences follow WHATWG replacement behavior`() {
        // Expected outputs were cross-checked with the independent WHATWG TextDecoder oracle.
        val cases = listOf(
            byteArrayOf(0x80.toByte()) to "\uFFFD",
            byteArrayOf(0xC0.toByte(), 0xAF.toByte()) to "\uFFFD\uFFFD",
            byteArrayOf(0xE2.toByte(), 0x82.toByte()) to "\uFFFD",
            byteArrayOf(0xED.toByte(), 0xA0.toByte(), 0x80.toByte()) to "\uFFFD\uFFFD\uFFFD",
            byteArrayOf(0xF4.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte()) to "\uFFFD\uFFFD\uFFFD\uFFFD",
            byteArrayOf(0xE2.toByte(), 0x28, 0xA1.toByte()) to "\uFFFD(\uFFFD",
            byteArrayOf(0xE0.toByte(), 0x80.toByte(), 0x80.toByte()) to "\uFFFD\uFFFD\uFFFD",
        )

        for ((bytes, expected) in cases) {
            for (windowSize in 1..bytes.size) {
                val (actual, repairs) = decodeInWindows(bytes, windowSize)
                assertEquals(expected, actual, "bytes=${bytes.joinToString { "%02X".format(it) }}, window=$windowSize")
                assertEquals(expected.count { it == '\uFFFD' }, repairs.size)
            }
        }
    }

    @Test
    fun `default mode strips only a leading UTF-8 BOM`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "text\uFEFF".toByteArray()
        val (text, repairs, hadBom) = decodeInWindows(bytes, 1)
        assertEquals("text\uFEFF", text)
        assertTrue(hadBom)
        assertTrue(repairs.isEmpty())

        val preserved = Utf8RepairDecoder(ignoreBom = true).let { decoder ->
            decoder.decode(bytes, endOfInput = true)
        }
        assertEquals("\uFEFFtext\uFEFF", preserved)
    }

    @Test
    fun `repair provenance uses document UTF16 and source byte offsets`() {
        val bytes = byteArrayOf('a'.code.toByte(), 0xE2.toByte(), 0x82.toByte(), 'b'.code.toByte())
        val (text, repairs) = decodeInWindows(bytes, 1)
        assertEquals("a\uFFFD" + "b", text)
        assertEquals(listOf(RepairedSpan(documentUtf16Offset = 1, sourceByteOffset = 1, sourceByteLength = 2)), repairs)
    }

    @Test
    fun `decoder cannot be used after end of input`() {
        val decoder = Utf8RepairDecoder()
        decoder.decode(byteArrayOf(0x61), endOfInput = true)
        assertFalse(decoder.hadLeadingBom)
        kotlin.test.assertFailsWith<IllegalStateException> { decoder.decode(byteArrayOf(0x62)) }
    }

    private fun decodeInWindows(bytes: ByteArray, windowSize: Int): Triple<String, List<RepairedSpan>, Boolean> {
        val decoder = Utf8RepairDecoder()
        val output = StringBuilder()
        var offset = 0
        while (offset < bytes.size) {
            val count = minOf(windowSize, bytes.size - offset)
            output.append(decoder.decode(bytes, offset, count))
            offset += count
        }
        output.append(decoder.decode(ByteArray(0), endOfInput = true))
        return Triple(output.toString(), decoder.repairedSpans, decoder.hadLeadingBom)
    }
}
