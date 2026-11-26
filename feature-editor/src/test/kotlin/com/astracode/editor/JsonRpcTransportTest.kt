package com.astracode.editor

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class JsonRpcTransportTest {
    @Test
    fun writesUtf8ByteLengthRatherThanCharacterCount() {
        val output = ByteArrayOutputStream()
        val json = """{"jsonrpc":"2.0","method":"window/logMessage","params":{"message":"ह😀"}}"""
        JsonRpcFrameCodec.writeMessage(output, json)
        val framed = output.toByteArray()
        val separator = "\r\n\r\n".toByteArray(Charsets.US_ASCII)
        val headerEnd = framed.indexOfSubsequence(separator)
        val header = String(framed, 0, headerEnd, Charsets.US_ASCII)
        val declaredLength = header.substringAfter(": ").toInt()
        assertEquals(json.toByteArray(Charsets.UTF_8).size, declaredLength)
        assertEquals(json, JsonRpcFrameCodec.readMessage(ByteArrayInputStream(framed)))
    }

    @Test
    fun readsMultipleHeadersWithoutDependingOnTheirOrder() {
        val json = """{"jsonrpc":"2.0","id":1,"result":{}}"""
        val bytes = (
            "Content-Type: application/vscode-jsonrpc; charset=utf-8\r\n" +
                "content-length: " + json.toByteArray(Charsets.UTF_8).size + "\r\n\r\n" + json
            ).toByteArray(Charsets.UTF_8)
        assertEquals(json, JsonRpcFrameCodec.readMessage(ByteArrayInputStream(bytes)))
    }

    @Test
    fun returnsNullOnlyForCleanEndOfStream() {
        assertNull(JsonRpcFrameCodec.readMessage(ByteArrayInputStream(byteArrayOf())))
    }

    @Test
    fun rejectsMissingLengthDuplicateHeadersAndOversizedFrames() {
        assertFailsWith<IOException> {
            JsonRpcFrameCodec.readMessage(ByteArrayInputStream("X-Test: 1\r\n\r\n".toByteArray()))
        }
        assertFailsWith<IOException> {
            JsonRpcFrameCodec.readMessage(
                ByteArrayInputStream("Content-Length: 0\r\ncontent-length: 0\r\n\r\n".toByteArray()),
            )
        }
        assertFailsWith<IOException> {
            JsonRpcFrameCodec.readMessage(
                ByteArrayInputStream("Content-Length: 5\r\n\r\n12345".toByteArray()),
                maximumMessageBytes = 4,
            )
        }
    }

    @Test
    fun rejectsTruncatedBodyAndMalformedHeaders() {
        assertFailsWith<EOFException> {
            JsonRpcFrameCodec.readMessage(
                ByteArrayInputStream("Content-Length: 5\r\n\r\nabc".toByteArray()),
            )
        }
        assertFailsWith<IOException> {
            JsonRpcFrameCodec.readMessage(ByteArrayInputStream("broken-header\r\n\r\n".toByteArray()))
        }
    }

    private fun ByteArray.indexOfSubsequence(needle: ByteArray): Int {
        for (start in 0..(size - needle.size)) {
            if (needle.indices.all { this[start + it] == needle[it] }) return start
        }
        return -1
    }
}
