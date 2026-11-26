package com.astracode.editor

import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LspJsonRpcFrameCodecTest {
    @Test
    fun contentLengthCountsUtf8BytesRatherThanStringCharacters() {
        val json = """{"text":"ह😀"}"""
        val frame = LspJsonRpcFrameCodec.encode(json)
        val separator = findHeaderEnd(frame)
        val header = String(frame, 0, separator, StandardCharsets.US_ASCII)
        val declaredLength = header.substringAfter("Content-Length: ").toInt()

        assertEquals(json.toByteArray(StandardCharsets.UTF_8).size, declaredLength)
        assertTrue(declaredLength > json.length)
    }

    @Test
    fun decoderHandlesHeadersAndPayloadSplitAcrossReads() {
        val json = """{"jsonrpc":"2.0","method":"textDocument/didOpen","text":"ह😀"}"""
        val frame = LspJsonRpcFrameCodec.encode(json)
        val decoder = LspJsonRpcFrameDecoder()
        val firstChunk = frame.copyOfRange(0, 7)
        val secondChunk = frame.copyOfRange(7, frame.size)

        assertTrue(decoder.append(firstChunk).isEmpty())
        assertEquals(7, decoder.bufferedByteCount)
        assertEquals(listOf(json), decoder.append(secondChunk))
        assertEquals(0, decoder.bufferedByteCount)
    }

    @Test
    fun decoderReturnsEveryFrameFromOneRead() {
        val first = LspJsonRpcFrameCodec.encode("""{"id":1,"method":"initialize"}""")
        val second = LspJsonRpcFrameCodec.encode("""{"id":2,"method":"initialized"}""")
        val combined = first + second

        assertEquals(
            listOf(
                """{"id":1,"method":"initialize"}""",
                """{"id":2,"method":"initialized"}""",
            ),
            LspJsonRpcFrameDecoder().append(combined),
        )
    }

    @Test
    fun decoderRejectsMissingOrDuplicateContentLengthHeaders() {
        val missing = "Content-Type: application/vscode-jsonrpc; charset=utf-8\r\n\r\n{}".toByteArray()
        val duplicate = (
            "Content-Length: 2\r\ncontent-length: 2\r\n\r\n{}"
        ).toByteArray()

        assertFailsWith<IllegalArgumentException> {
            LspJsonRpcFrameDecoder().append(missing)
        }
        assertFailsWith<IllegalArgumentException> {
            LspJsonRpcFrameDecoder().append(duplicate)
        }
    }

    @Test
    fun decoderRejectsMalformedUtf8Payload() {
        val frame = "Content-Length: 1\r\n\r\n".toByteArray(StandardCharsets.US_ASCII) +
            byteArrayOf(0xFF.toByte())
        assertFailsWith<IllegalArgumentException> {
            LspJsonRpcFrameDecoder().append(frame)
        }
    }

    @Test
    fun decoderEnforcesConfiguredPayloadLimit() {
        val decoder = LspJsonRpcFrameDecoder(maxPayloadBytes = 32)
        val frame = "Content-Length: 33\r\n\r\n".toByteArray(StandardCharsets.US_ASCII)
        assertFailsWith<IllegalArgumentException> {
            decoder.append(frame)
        }
    }

    private fun findHeaderEnd(frame: ByteArray): Int {
        for (start in 0..frame.size - 4) {
            if (
                frame[start] == 13.toByte() &&
                frame[start + 1] == 10.toByte() &&
                frame[start + 2] == 13.toByte() &&
                frame[start + 3] == 10.toByte()
            ) return start
        }
        error("LSP header terminator not found")
    }
}
