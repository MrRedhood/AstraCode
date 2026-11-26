package com.astracode.editor

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Frames JSON-RPC messages using the LSP Content-Length transport.
 *
 * Content-Length is the number of UTF-8 payload bytes, not Kotlin String
 * characters. The decoder supports partial reads and several frames in one
 * read, as happens with pipes and sockets.
 */
object LspJsonRpcFrameCodec {
    fun encode(jsonPayload: String): ByteArray {
        require(jsonPayload.isNotBlank()) { "LSP JSON-RPC payload must not be blank" }
        val payload = jsonPayload.toByteArray(StandardCharsets.UTF_8)
        val header = "Content-Length: ${payload.size}\r\n\r\n"
            .toByteArray(StandardCharsets.US_ASCII)
        return ByteArray(header.size + payload.size).also { frame ->
            header.copyInto(frame, 0)
            payload.copyInto(frame, header.size)
        }
    }

    internal fun decodeUtf8(payload: ByteArray): String {
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(payload))
                .toString()
        } catch (error: CharacterCodingException) {
            throw IllegalArgumentException("LSP payload is not valid UTF-8", error)
        }
    }
}

/** Incremental LSP frame decoder. Feed each arbitrary transport chunk to append(). */
class LspJsonRpcFrameDecoder(
    private val maxPayloadBytes: Int = DEFAULT_MAX_PAYLOAD_BYTES,
    private val maxHeaderBytes: Int = DEFAULT_MAX_HEADER_BYTES,
) {
    private var pending = ByteArray(0)

    init {
        require(maxPayloadBytes in 1..MAX_ALLOWED_PAYLOAD_BYTES)
        require(maxHeaderBytes in 32..MAX_ALLOWED_HEADER_BYTES)
    }

    val bufferedByteCount: Int get() = pending.size

    fun reset() {
        pending = ByteArray(0)
    }

    fun append(chunk: ByteArray): List<String> {
        if (chunk.isEmpty()) return emptyList()
        pending = pending + chunk
        val frames = mutableListOf<String>()

        while (true) {
            val headerEnd = findHeaderTerminator(pending)
            if (headerEnd < 0) {
                require(pending.size <= maxHeaderBytes) {
                    "LSP message header exceeded the configured size limit"
                }
                break
            }
            require(headerEnd <= maxHeaderBytes) {
                "LSP message header exceeded the configured size limit"
            }

            val contentLength = parseContentLength(pending.copyOfRange(0, headerEnd))
            require(contentLength in 1..maxPayloadBytes) {
                "LSP payload length is outside the configured size limit"
            }
            val bodyStart = headerEnd + HEADER_TERMINATOR_BYTES
            val frameEndLong = bodyStart.toLong() + contentLength.toLong()
            require(frameEndLong <= Int.MAX_VALUE) { "LSP frame length is too large" }
            val frameEnd = frameEndLong.toInt()
            if (pending.size < frameEnd) break

            val payload = pending.copyOfRange(bodyStart, frameEnd)
            frames += LspJsonRpcFrameCodec.decodeUtf8(payload)
            pending = pending.copyOfRange(frameEnd, pending.size)
        }

        require(pending.size <= maxHeaderBytes + maxPayloadBytes) {
            "Buffered LSP data exceeded the configured size limit"
        }
        return frames
    }

    private fun findHeaderTerminator(bytes: ByteArray): Int {
        if (bytes.size < HEADER_TERMINATOR_BYTES) return -1
        val terminator = byteArrayOf(13, 10, 13, 10)
        for (start in 0..bytes.size - HEADER_TERMINATOR_BYTES) {
            var matches = true
            for (offset in terminator.indices) {
                if (bytes[start + offset] != terminator[offset]) {
                    matches = false
                    break
                }
            }
            if (matches) return start
        }
        return -1
    }

    private fun parseContentLength(headerBytes: ByteArray): Int {
        require(headerBytes.all { (it.toInt() and 0xFF) < 128 }) {
            "LSP headers must contain ASCII characters"
        }
        val header = String(headerBytes, StandardCharsets.US_ASCII)
        var contentLength: String? = null
        header.split("\r\n").forEach { line ->
            if (line.isBlank()) return@forEach
            val separator = line.indexOf(':')
            require(separator > 0) { "Malformed LSP header line" }
            val name = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim()
            if (name.equals("Content-Length", ignoreCase = true)) {
                require(contentLength == null) { "Duplicate LSP Content-Length header" }
                contentLength = value
            }
        }
        val value = contentLength ?: throw IllegalArgumentException("LSP Content-Length header is missing")
        require(value.isNotEmpty() && value.all { it in '0'..'9' }) {
            "LSP Content-Length must be a non-negative integer"
        }
        return value.toLongOrNull()
            ?.takeIf { it in 1..maxPayloadBytes.toLong() }
            ?.toInt()
            ?: throw IllegalArgumentException("LSP Content-Length is outside the configured size limit")
    }

    private companion object {
        const val DEFAULT_MAX_PAYLOAD_BYTES = 8 * 1024 * 1024
        const val DEFAULT_MAX_HEADER_BYTES = 8 * 1024
        const val MAX_ALLOWED_PAYLOAD_BYTES = 64 * 1024 * 1024
        const val MAX_ALLOWED_HEADER_BYTES = 64 * 1024
        const val HEADER_TERMINATOR_BYTES = 4
    }
}
