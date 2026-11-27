package com.astracode.editor

import java.io.Closeable
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.CharacterCodingException
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * JSON-RPC 2.0 stream framing used by Language Server Protocol.
 *
 * LSP uses Content-Length headers followed by UTF-8 JSON. This class handles
 * framing and process lifetime only; JSON parsing and LSP semantics stay above
 * this layer. A language server runs with the app's UID, not in an OS sandbox.
 */
object JsonRpcFrameCodec {
    const val DEFAULT_MAX_MESSAGE_BYTES = 4 * 1024 * 1024
    private const val MAX_HEADER_BYTES = 8 * 1024

    fun readMessage(
        input: InputStream,
        maximumMessageBytes: Int = DEFAULT_MAX_MESSAGE_BYTES,
    ): String? {
        require(maximumMessageBytes in 1..(32 * 1024 * 1024))
        val headers = readHeaders(input) ?: return null
        val length = headers.entries.firstOrNull {
            it.key.equals("Content-Length", ignoreCase = true)
        }?.value?.toIntOrNull() ?: throw IOException("LSP message has no valid Content-Length header")
        if (length !in 1..maximumMessageBytes) {
            throw IOException("LSP message length exceeds the configured limit")
        }

        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(bytes, offset, length - offset)
            if (count < 0) throw EOFException("LSP stream ended in the middle of a message")
            if (count == 0) continue
            offset += count
        }
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (error: CharacterCodingException) {
            throw IOException("LSP message body is not valid UTF-8", error)
        }
    }

    fun writeMessage(
        output: OutputStream,
        json: String,
        maximumMessageBytes: Int = DEFAULT_MAX_MESSAGE_BYTES,
    ) {
        require(maximumMessageBytes in 1..(32 * 1024 * 1024))
        require(json.isNotBlank()) { "JSON-RPC message cannot be empty" }
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > maximumMessageBytes) {
            throw IOException("LSP message length exceeds the configured limit")
        }
        val header = ("Content-Length: " + bytes.size + "\r\n\r\n")
            .toByteArray(StandardCharsets.US_ASCII)
        output.write(header)
        output.write(bytes)
        output.flush()
    }

    private fun readHeaders(input: InputStream): Map<String, String>? {
        val headers = linkedMapOf<String, String>()
        var headerBytes = 0
        var line = StringBuilder()
        var sawAnyByte = false
        while (true) {
            val value = input.read()
            if (value < 0) {
                if (!sawAnyByte && line.isEmpty()) return null
                throw EOFException("LSP stream ended while reading message headers")
            }
            sawAnyByte = true
            headerBytes++
            if (headerBytes > MAX_HEADER_BYTES) {
                throw IOException("LSP message headers exceed the size limit")
            }
            if (value == '\n'.code) {
                val headerLine = line.toString().removeSuffix("\r")
                line = StringBuilder()
                if (headerLine.isEmpty()) return headers
                val separator = headerLine.indexOf(':')
                if (separator <= 0) throw IOException("Malformed LSP message header")
                val name = headerLine.substring(0, separator).trim()
                val content = headerLine.substring(separator + 1).trim()
                if (name.isEmpty()) throw IOException("Malformed LSP message header")
                if (headers.keys.any { it.equals(name, ignoreCase = true) }) {
                    throw IOException("Duplicate LSP message header: " + name)
                }
                headers[name] = content
            } else {
                if (value !in 0x20..0x7E && value != '\t'.code && value != '\r'.code) {
                    throw IOException("LSP headers must be ASCII")
                }
                line.append(value.toChar())
            }
        }
    }
}

/**
 * Owns one language-server process and its framed JSON-RPC streams.
 * Callbacks run on the dedicated reader thread and must dispatch to the UI
 * thread themselves. Closing the transport closes the process.
 */
class JsonRpcProcessTransport private constructor(
    private val process: Process,
    private val maximumMessageBytes: Int,
    private val onMessage: (String) -> Unit,
    private val onFailure: (Throwable) -> Unit,
) : Closeable {
    private val closed = AtomicBoolean(false)
    private val writeLock = Any()
    private val stderrThread = Thread({
        runCatching {
            process.errorStream.buffered().use { stream ->
                val buffer = ByteArray(8 * 1024)
                while (!closed.get() && stream.read(buffer) >= 0) {
                    // Drain stderr to prevent a verbose language server from
                    // blocking on a full OS pipe. Diagnostics are surfaced by
                    // JSON-RPC notifications, not unbounded process logs.
                }
            }
        }
    }, "astracode-lsp-stderr").apply {
        isDaemon = true
        start()
    }
    private val readerThread = Thread(::readLoop, "astracode-lsp-reader").apply {
        isDaemon = true
        start()
    }

    fun send(jsonRpcMessage: String) {
        check(!closed.get()) { "LSP transport is closed" }
        synchronized(writeLock) {
            check(!closed.get()) { "LSP transport is closed" }
            JsonRpcFrameCodec.writeMessage(
                process.outputStream,
                jsonRpcMessage,
                maximumMessageBytes,
            )
        }
    }

    private fun readLoop() {
        try {
            process.inputStream.buffered().use { input ->
                while (!closed.get()) {
                    val message = JsonRpcFrameCodec.readMessage(input, maximumMessageBytes) ?: break
                    onMessage(message)
                }
            }
            if (!closed.get() && process.isAlive) {
                throw IOException("Language server closed its output stream unexpectedly")
            }
        } catch (error: Throwable) {
            if (!closed.get()) runCatching { onFailure(error) }
        } finally {
            close()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { process.outputStream.close() }
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
        if (process.isAlive) {
            process.destroy()
            if (!process.waitFor(PROCESS_EXIT_GRACE_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
            }
        }
    }

    companion object {
        private const val PROCESS_EXIT_GRACE_MS = 500L

        fun start(
            command: List<String>,
            workspaceRoot: File,
            workingDirectory: File,
            environment: Map<String, String> = emptyMap(),
            maximumMessageBytes: Int = JsonRpcFrameCodec.DEFAULT_MAX_MESSAGE_BYTES,
            onMessage: (String) -> Unit,
            onFailure: (Throwable) -> Unit = {},
        ): JsonRpcProcessTransport {
            require(command.isNotEmpty() && command.size <= 64)
            require(command.all { it.isNotBlank() && '\u0000' !in it })
            require(environment.size <= 64)
            require(environment.all { (key, value) ->
                ENVIRONMENT_KEY.matches(key) && '\u0000' !in value && value.length <= 8 * 1024
            })
            val root = workspaceRoot.canonicalFile
            val directory = workingDirectory.canonicalFile
            require(root.isDirectory) { "Workspace root does not exist" }
            require(directory.isDirectory) { "Language-server working directory does not exist" }
            require(directory.toPath().normalize().startsWith(root.toPath().normalize())) {
                "Language-server working directory is outside the workspace"
            }
            require(maximumMessageBytes in 1..(32 * 1024 * 1024))

            val builder = ProcessBuilder(command).directory(directory)
            builder.environment().putAll(environment)
            val process = builder.start()
            return JsonRpcProcessTransport(process, maximumMessageBytes, onMessage, onFailure)
        }

        private val ENVIRONMENT_KEY = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}
