package com.astracode.editor

import java.io.Closeable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean

data class LspJsonRpcError(
    val code: Int,
    override val message: String,
    val data: Any? = null,
) : RuntimeException(message)

class LspJsonRpcClient(
    private val sendMessage: (String) -> Unit,
    private val onNotification: (String, Map<String, Any?>) -> Unit = { _, _ -> },
    private val onServerRequest: (String, Map<String, Any?>) -> Any? = { method, _ ->
        throw LspJsonRpcError(-32601, "Method not found: " + method)
    },
    private val onProtocolError: (Throwable) -> Unit = {},
    private val requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
) : Closeable {
    init {
        require(requestTimeoutMillis in 1L..MAX_REQUEST_TIMEOUT_MILLIS) {
            "Request timeout must be between 1 ms and 5 minutes"
        }
    }
    private val nextRequestId = AtomicLong(1L)
    private val closed = AtomicBoolean(false)
    private val pendingLock = Any()
    private val pending = ConcurrentHashMap<Long, CompletableFuture<Any?>>()

    val pendingRequestCount: Int get() = pending.size

    fun request(method: String, params: Any? = null): CompletableFuture<Any?> {
        require(method.isNotBlank()) { "JSON-RPC method is required" }
        val future = CompletableFuture<Any?>()
        val id: Long
        synchronized(pendingLock) {
            check(!closed.get()) { "JSON-RPC client is closed" }
            require(pending.size < MAX_PENDING_REQUESTS) { "Too many pending LSP requests" }
            id = nextRequestId.getAndIncrement()
            check(id > 0L) { "JSON-RPC request ID space exhausted" }
            check(pending.putIfAbsent(id, future) == null) { "Duplicate JSON-RPC request ID" }
        }
        val timeoutTask = AtomicReference<ScheduledFuture<*>?>()
        future.whenComplete { _, error ->
            pending.remove(id, future)
            timeoutTask.getAndSet(null)?.cancel(false)
            val cause = (error as? CompletionException)?.cause ?: error
            if (future.isCancelled || cause is TimeoutException) {
                sendCancellation(id)
            }
        }

        val message = linkedMapOf<String, Any?>(
            "jsonrpc" to JSON_RPC_VERSION,
            "id" to id,
            "method" to method,
        )
        if (params != null) message["params"] = params

        try {
            sendMessage(LspJsonCodec.encode(message))
            val scheduled = REQUEST_TIMEOUT_EXECUTOR.schedule(
                { future.completeExceptionally(TimeoutException("LSP request timed out: " + method)) },
                requestTimeoutMillis,
                TimeUnit.MILLISECONDS,
            )
            timeoutTask.set(scheduled)
            if (future.isDone) scheduled.cancel(false)
        } catch (error: Throwable) {
            pending.remove(id, future)
            future.completeExceptionally(error)
        }
        return future
    }

    fun notify(method: String, params: Any? = null) {
        require(method.isNotBlank()) { "JSON-RPC method is required" }
        check(!closed.get()) { "JSON-RPC client is closed" }
        val message = linkedMapOf<String, Any?>(
            "jsonrpc" to JSON_RPC_VERSION,
            "method" to method,
        )
        if (params != null) message["params"] = params
        sendMessage(LspJsonCodec.encode(message))
    }

    /** Called by the JSON-RPC transport for each complete UTF-8 framed message. */
    fun acceptMessage(json: String) {
        if (closed.get()) return
        try {
            val message = LspJsonCodec.decodeObject(json)
            require(message["jsonrpc"] == JSON_RPC_VERSION) { "Unsupported JSON-RPC version" }
            val method = message["method"] as? String
            if (method != null) {
                val params = message["params"].asObjectOrEmpty()
                if (message.containsKey("id")) {
                    handleServerRequest(message["id"], method, params)
                } else {
                    onNotification(method, params)
                }
                return
            }

            val id = (message["id"] as? Number)?.toLong()
                ?: throw IllegalArgumentException("JSON-RPC response has no numeric request ID")
            val future = pending[id] ?: return
            val errorObject = message["error"] as? Map<*, *>
            if (errorObject != null) {
                val code = (errorObject["code"] as? Number)?.toInt()
                    ?: throw IllegalArgumentException("JSON-RPC error has no numeric code")
                val errorMessage = errorObject["message"] as? String
                    ?: throw IllegalArgumentException("JSON-RPC error has no message")
                future.completeExceptionally(
                    LspJsonRpcError(code, errorMessage, errorObject["data"]),
                )
            } else if (message.containsKey("result")) {
                future.complete(message["result"])
            } else {
                throw IllegalArgumentException("JSON-RPC response has neither result nor error")
            }
        } catch (error: Throwable) {
            runCatching { onProtocolError(error) }
        }
    }

    private fun handleServerRequest(
        id: Any?,
        method: String,
        params: Map<String, Any?>,
    ) {
        require(id is String || id is Number) { "JSON-RPC server request has an invalid ID" }
        try {
            val result = onServerRequest(method, params)
            sendResponse(id, mapOf("result" to result))
        } catch (error: LspJsonRpcError) {
            val body = linkedMapOf<String, Any?>(
                "code" to error.code,
                "message" to error.message,
            )
            if (error.data != null) body["data"] = error.data
            sendResponse(id, mapOf("error" to body))
        } catch (error: Throwable) {
            sendResponse(
                id,
                mapOf(
                    "error" to mapOf(
                        "code" to -32603,
                        "message" to (error.message ?: "Internal JSON-RPC error"),
                    ),
                ),
            )
        }
    }

    private fun sendCancellation(id: Long) {
        runCatching {
            sendMessage(
                LspJsonCodec.encode(
                    mapOf(
                        "jsonrpc" to JSON_RPC_VERSION,
                        "method" to "$/cancelRequest",
                        "params" to mapOf("id" to id),
                    ),
                ),
            )
        }
    }

    private fun sendResponse(id: Any, body: Map<String, Any?>) {
        val message = linkedMapOf<String, Any?>(
            "jsonrpc" to JSON_RPC_VERSION,
            "id" to id,
        )
        message.putAll(body)
        sendMessage(LspJsonCodec.encode(message))
    }

    fun failAll(cause: Throwable) {
        val requestsToFail = synchronized(pendingLock) {
            if (!closed.compareAndSet(false, true)) return
            pending.values.toList().also { pending.clear() }
        }
        requestsToFail.forEach { it.completeExceptionally(cause) }
    }

    override fun close() {
        failAll(IllegalStateException("JSON-RPC client closed"))
    }

    private fun Any?.asObjectOrEmpty(): Map<String, Any?> {
        if (this == null) return emptyMap()
        @Suppress("UNCHECKED_CAST")
        return this as? Map<String, Any?>
            ?: throw IllegalArgumentException("JSON-RPC params must be an object")
    }

    private companion object {
        const val JSON_RPC_VERSION = "2.0"
        const val MAX_PENDING_REQUESTS = 128
        const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 60_000L
        const val MAX_REQUEST_TIMEOUT_MILLIS = 5 * 60_000L
        val REQUEST_TIMEOUT_EXECUTOR = ScheduledThreadPoolExecutor(1) { runnable ->
            Thread(runnable, "astracode-lsp-timeout").apply { isDaemon = true }
        }.apply {
            removeOnCancelPolicy = true
        }
    }
}
