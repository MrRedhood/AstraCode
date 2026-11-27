package com.astracode.editor

import java.io.Closeable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap

/**
 * Concrete LanguageServerBridge backed by an initialized UTF-8 LSP process.
 *
 * EditorTextChange offsets are UTF-8 byte offsets. Changes in one call are
 * interpreted sequentially, where each range refers to the document after
 * the preceding change in that same call.
 */
class JsonRpcLanguageServerBridge internal constructor(
    private val requestMessage: (String, Any?) -> CompletableFuture<Any?>,
    private val sendNotification: (String, Any?) -> Unit,
    subscribeNotifications: ((String, Map<String, Any?>) -> Unit) -> Closeable,
) : LanguageServerBridge {
    private class OpenDocumentState(document: EditorDocument) {
        val lock = Any()
        var buffer = EditorBuffer(document.text)
        var version = document.version
    }

    private val closed = AtomicBoolean(false)
    private val documents = ConcurrentHashMap<String, OpenDocumentState>()
    private val diagnosticsListener =
        AtomicReference<(List<EditorDiagnostic>) -> Unit>({})
    private val notificationSubscription = subscribeNotifications(::handleNotification)

    constructor(session: LspProcessSession) : this(
        requestMessage = session::request,
        sendNotification = session::notify,
        subscribeNotifications = session::addNotificationListener,
    )

    override fun openDocument(document: EditorDocument) {
        ensureOpen()
        require(document.uri.isNotBlank()) { "Document URI is required" }
        require(document.languageId.isNotBlank()) { "Document language ID is required" }
        require(document.version >= 0) { "Document version cannot be negative" }
        val state = OpenDocumentState(document)
        check(documents.putIfAbsent(document.uri, state) == null) {
            "Document is already open: ${document.uri}"
        }
        try {
            sendNotification(
                "textDocument/didOpen",
                mapOf(
                    "textDocument" to mapOf(
                        "uri" to document.uri,
                        "languageId" to document.languageId,
                        "version" to document.version,
                        "text" to document.text,
                    ),
                ),
            )
        } catch (error: Throwable) {
            documents.remove(document.uri, state)
            throw error
        }
    }

    override fun changeDocument(uri: String, version: Int, changes: List<EditorTextChange>) {
        ensureOpen()
        require(changes.isNotEmpty()) { "At least one document change is required" }
        val state = documents[uri] ?: throw IllegalArgumentException("Document is not open: " + uri)
        synchronized(state.lock) {
            require(version > state.version) { "Document versions must increase" }

            // Build the new state separately. Invalid edits never partially
            // mutate the live buffer or advance its version.
            val updatedBuffer = EditorBuffer(state.buffer.text())
            val contentChanges = changes.map { change ->
                val bytesBeforeChange = updatedBuffer.bytes()
                require(change.startOffset >= 0 && change.endOffset >= change.startOffset)
                val startPosition = LspPositionCodec.positionForByteOffset(
                    bytesBeforeChange,
                    change.startOffset,
                )
                val endPosition = LspPositionCodec.positionForByteOffset(
                    bytesBeforeChange,
                    change.endOffset,
                )
                require(change.endOffset <= bytesBeforeChange.size) {
                    "Document change range is outside the current document"
                }

                val rangeLength = change.endOffset - change.startOffset
                updatedBuffer.replace(change.startOffset, change.endOffset, change.replacement)
                mapOf(
                    "range" to mapOf(
                        "start" to startPosition.toWireMap(),
                        "end" to endPosition.toWireMap(),
                    ),
                    "rangeLength" to rangeLength,
                    "text" to change.replacement,
                )
            }

            sendNotification(
                "textDocument/didChange",
                mapOf(
                    "textDocument" to mapOf("uri" to uri, "version" to version),
                    "contentChanges" to contentChanges,
                ),
            )
            state.buffer = updatedBuffer
            state.version = version
        }
    }

    override fun closeDocument(uri: String) {
        ensureOpen()
        val state = documents.remove(uri) ?: return
        synchronized(state.lock) {
            sendNotification(
                "textDocument/didClose",
                mapOf("textDocument" to mapOf("uri" to uri)),
            )
        }
        diagnosticsListener.get().invoke(emptyList())
    }

    override fun requestCompletion(
        uri: String,
        offset: Int,
        version: Int,
        callback: (Result<List<String>>) -> Unit,
    ) {
        val params = runCatching {
            val position = positionFor(uri, offset, version)
            mapOf(
                "textDocument" to mapOf("uri" to uri),
                "position" to position.toWireMap(),
            )
        }.getOrElse {
            callbackSafely(callback, Result.failure(it))
            return
        }
        completeRequest("textDocument/completion", params, ::parseCompletionItems, callback)
    }

    override fun requestHover(
        uri: String,
        offset: Int,
        version: Int,
        callback: (Result<String?>) -> Unit,
    ) {
        val params = runCatching {
            val position = positionFor(uri, offset, version)
            mapOf(
                "textDocument" to mapOf("uri" to uri),
                "position" to position.toWireMap(),
            )
        }.getOrElse {
            callbackSafely(callback, Result.failure(it))
            return
        }
        completeRequest("textDocument/hover", params, ::parseHoverContents, callback)
    }

    override fun requestDefinition(
        uri: String,
        offset: Int,
        version: Int,
        callback: (Result<List<String>>) -> Unit,
    ) {
        val params = runCatching {
            val position = positionFor(uri, offset, version)
            mapOf(
                "textDocument" to mapOf("uri" to uri),
                "position" to position.toWireMap(),
            )
        }.getOrElse {
            callbackSafely(callback, Result.failure(it))
            return
        }
        completeRequest("textDocument/definition", params, ::parseDefinitions, callback)
    }

    override fun setDiagnosticsListener(listener: (List<EditorDiagnostic>) -> Unit) {
        diagnosticsListener.set(listener)
    }

    internal fun acceptNotification(method: String, params: Map<String, Any?>) {
        if (closed.get() || method != "textDocument/publishDiagnostics") return
        val uri = params["uri"] as? String ?: return
        val state = documents[uri] ?: return
        val serverVersion = (params["version"] as? Number)?.toInt()
        val diagnostics = synchronized(state.lock) {
            if (serverVersion != null && serverVersion != state.version) return
            val bytes = state.buffer.bytes()
            (params["diagnostics"] as? List<*>)
                .orEmpty()
                .mapNotNull { parseDiagnostic(uri, it, bytes) }
        }
        callbackSafely(diagnosticsListener.get(), Result.success(diagnostics))
    }

    private fun positionFor(uri: String, offset: Int, version: Int): LspPosition {
        ensureOpen()
        val state = documents[uri] ?: throw IllegalArgumentException("Document is not open: " + uri)
        return synchronized(state.lock) {
            check(state.version == version) {
                "The requested document version is stale; expected ${state.version}, got ${version}"
            }
            LspPositionCodec.positionForByteOffset(state.buffer.bytes(), offset)
        }
    }

    private fun parseDiagnostic(
        uri: String,
        raw: Any?,
        documentBytes: ByteArray,
    ): EditorDiagnostic? {
        val diagnostic = raw as? Map<*, *> ?: return null
        val range = diagnostic["range"] as? Map<*, *> ?: return null
        val start = parsePosition(range["start"]) ?: return null
        val end = parsePosition(range["end"]) ?: return null
        val message = diagnostic["message"] as? String ?: return null
        return runCatching {
            val startOffset = LspPositionCodec.byteOffsetForPosition(documentBytes, start)
            val endOffset = LspPositionCodec.byteOffsetForPosition(documentBytes, end)
            if (endOffset < startOffset) return null
            val severity = when ((diagnostic["severity"] as? Number)?.toInt()) {
                1 -> EditorDiagnostic.Severity.ERROR
                2 -> EditorDiagnostic.Severity.WARNING
                4 -> EditorDiagnostic.Severity.HINT
                else -> EditorDiagnostic.Severity.INFORMATION
            }
            EditorDiagnostic(uri, startOffset, endOffset, severity, message)
        }.getOrNull()
    }

    private fun parsePosition(raw: Any?): LspPosition? {
        val position = raw as? Map<*, *> ?: return null
        val line = (position["line"] as? Number)?.toInt() ?: return null
        val character = (position["character"] as? Number)?.toInt() ?: return null
        return runCatching { LspPosition(line, character).also {
            require(it.line >= 0 && it.character >= 0)
        } }.getOrNull()
    }

    private fun parseCompletionItems(raw: Any?): List<String> {
        val items = when (raw) {
            is List<*> -> raw
            is Map<*, *> -> raw["items"] as? List<*> ?: emptyList<Any?>()
            else -> emptyList()
        }
        return items.mapNotNull { item ->
            when (item) {
                is String -> item
                is Map<*, *> -> item["label"] as? String
                else -> null
            }
        }
    }

    private fun parseHoverContents(raw: Any?): String? {
        val result = raw as? Map<*, *> ?: return null
        return formatHoverContent(result["contents"])
    }

    private fun formatHoverContent(raw: Any?): String? = when (raw) {
        is String -> raw
        is List<*> -> raw.mapNotNull(::formatHoverContent).filter(String::isNotBlank)
            .joinToString("\n\n").ifBlank { null }
        is Map<*, *> -> (raw["value"] as? String)
            ?: (raw["language"] as? String)?.let { language ->
                (raw["value"] as? String)?.let { "${language}: ${it}" }
            }
        else -> null
    }

    private fun parseDefinitions(raw: Any?): List<String> {
        val locations = when (raw) {
            is List<*> -> raw
            null -> emptyList()
            else -> listOf(raw)
        }
        return locations.mapNotNull { location ->
            val value = location as? Map<*, *> ?: return@mapNotNull null
            val uri = (value["uri"] as? String) ?: (value["targetUri"] as? String)
                ?: return@mapNotNull null
            val range = (value["range"] as? Map<*, *>)
                ?: (value["targetSelectionRange"] as? Map<*, *>)
                ?: (value["targetRange"] as? Map<*, *>)
                ?: return@mapNotNull uri
            val start = parsePosition(range["start"]) ?: return@mapNotNull uri
            "${uri}#${start.line + 1}:${start.character + 1}"
        }
    }

    private fun <T> completeRequest(
        method: String,
        params: Any?,
        transform: (Any?) -> T,
        callback: (Result<T>) -> Unit,
    ) {
        try {
            requestMessage(method, params).whenComplete { value, error ->
                val result = if (error != null) {
                    Result.failure(unwrap(error))
                } else {
                    runCatching { transform(value) }
                }
                callbackSafely(callback, result)
            }
        } catch (error: Throwable) {
            callbackSafely(callback, Result.failure(error))
        }
    }

    private fun unwrap(error: Throwable): Throwable =
        if (error is CompletionException && error.cause != null) error.cause!! else error

    private fun <T> callbackSafely(callback: (Result<T>) -> Unit, result: Result<T>) {
        runCatching { callback(result) }
    }

    private fun ensureOpen() {
        check(!closed.get()) { "Language-server bridge is closed" }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        documents.keys.toList().forEach { uri ->
            runCatching {
                sendNotification("textDocument/didClose", mapOf("textDocument" to mapOf("uri" to uri)))
            }
        }
        documents.clear()
        runCatching { notificationSubscription.close() }
    }

    private fun LspPosition.toWireMap(): Map<String, Int> = mapOf(
        "line" to line,
        "character" to character,
    )
}
