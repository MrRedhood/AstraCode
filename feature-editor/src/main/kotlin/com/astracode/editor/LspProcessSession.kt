package com.astracode.editor

import java.io.Closeable
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

enum class LspProcessSessionState {
    CREATED,
    INITIALIZING,
    READY,
    SHUTTING_DOWN,
    CLOSED,
    FAILED,
}

/**
 * Owns a language-server process from initialize through shutdown.
 *
 * The session does not expose UTF-8 document operations until the server has
 * explicitly negotiated UTF-8 in its initialize response.
 */
class LspProcessSession private constructor(
    private val transport: JsonRpcProcessTransport,
    private val rpc: LspJsonRpcClient,
    private val onFailure: (Throwable) -> Unit,
    private val notificationListeners: CopyOnWriteArrayList<(String, Map<String, Any?>) -> Unit>,
) : Closeable {
    private val stateLock = Any()
    private val failureReported = AtomicBoolean(false)

    @Volatile
    var state: LspProcessSessionState = LspProcessSessionState.CREATED
        private set

    @Volatile
    var negotiatedCapabilities: LspNegotiatedCapabilities? = null
        private set

    fun initialize(params: LspInitializeParams): CompletableFuture<LspNegotiatedCapabilities> {
        synchronized(stateLock) {
            check(state == LspProcessSessionState.CREATED) {
                "Language server initialization can only run once"
            }
            state = LspProcessSessionState.INITIALIZING
        }

        return rpc.request("initialize", params.toWireMap())
            .thenApply { result ->
                val negotiated = LspPositionEncodingNegotiator.negotiateInitializeResult(result)
                rpc.notify("initialized", emptyMap<String, Any?>())
                negotiatedCapabilities = negotiated
                synchronized(stateLock) {
                    check(state == LspProcessSessionState.INITIALIZING) {
                        "Language server stopped during initialization"
                    }
                    state = LspProcessSessionState.READY
                }
                negotiated
            }
            .whenComplete { _, error ->
                if (error != null) {
                    markFailed(unwrap(error))
                    close()
                }
            }
    }

    /**
     * Adds a listener for server notifications. The listener runs on the
     * language-server reader thread and should dispatch UI work as needed.
     */
    fun addNotificationListener(listener: (String, Map<String, Any?>) -> Unit): Closeable {
        check(state != LspProcessSessionState.CLOSED && state != LspProcessSessionState.FAILED) {
            "Language-server session is not accepting notification listeners"
        }
        notificationListeners += listener
        return Closeable { notificationListeners.remove(listener) }
    }

    fun request(method: String, params: Any? = null): CompletableFuture<Any?> {
        check(state == LspProcessSessionState.READY) {
            "Language server must negotiate UTF-8 before requests are sent"
        }
        return rpc.request(method, params)
    }

    fun notify(method: String, params: Any? = null) {
        check(state == LspProcessSessionState.READY) {
            "Language server must negotiate UTF-8 before notifications are sent"
        }
        rpc.notify(method, params)
    }

    fun shutdown(): CompletableFuture<Unit> {
        synchronized(stateLock) {
            check(state == LspProcessSessionState.READY) { "Language server is not initialized" }
            state = LspProcessSessionState.SHUTTING_DOWN
        }
        return rpc.request("shutdown")
            .handle { _, error ->
                if (error != null) {
                    val cause = unwrap(error)
                    markFailed(cause)
                    close()
                    throw CompletionException(cause)
                }
                runCatching { rpc.notify("exit") }
                close()
                Unit
            }
    }

    private fun handleTransportFailure(error: Throwable) {
        val shutdownInProgress = synchronized(stateLock) {
            state == LspProcessSessionState.SHUTTING_DOWN
        }
        if (shutdownInProgress) {
            // A process may close stdout after the shutdown response and exit
            // while the client is sending the protocol exit notification.
            rpc.failAll(error)
        } else {
            markFailed(error)
        }
    }

    private fun markFailed(error: Throwable) {
        val changed = synchronized(stateLock) {
            if (state == LspProcessSessionState.CLOSED || state == LspProcessSessionState.FAILED) {
                false
            } else {
                state = LspProcessSessionState.FAILED
                true
            }
        }
        if (!changed) return
        rpc.failAll(error)
        if (failureReported.compareAndSet(false, true)) {
            runCatching { onFailure(error) }
        }
    }

    private fun unwrap(error: Throwable): Throwable =
        if (error is CompletionException && error.cause != null) error.cause!! else error

    override fun close() {
        synchronized(stateLock) {
            if (state == LspProcessSessionState.CLOSED) return
            if (state != LspProcessSessionState.FAILED) state = LspProcessSessionState.CLOSED
        }
        rpc.close()
        transport.close()
    }

    companion object {
        fun start(
            command: List<String>,
            workspaceRoot: File,
            workingDirectory: File,
            environment: Map<String, String> = emptyMap(),
            maximumMessageBytes: Int = JsonRpcFrameCodec.DEFAULT_MAX_MESSAGE_BYTES,
            onNotification: (String, Map<String, Any?>) -> Unit = { _, _ -> },
            onServerRequest: (String, Map<String, Any?>) -> Any? = { method, _ ->
                throw LspJsonRpcError(-32601, "Method not found: " + method)
            },
            onFailure: (Throwable) -> Unit = {},
        ): LspProcessSession {
            val transportReference = AtomicReference<JsonRpcProcessTransport?>()
            val sessionReference = AtomicReference<LspProcessSession?>()
            val startupLock = Any()
            var earlyTransportFailure: Throwable? = null
            val notificationListeners = CopyOnWriteArrayList<(String, Map<String, Any?>) -> Unit>()
            notificationListeners += onNotification
            val rpc = LspJsonRpcClient(
                sendMessage = { message ->
                    val activeTransport = transportReference.get()
                        ?: throw IllegalStateException("Language-server transport is not ready")
                    activeTransport.send(message)
                },
                onNotification = { method, params ->
                    notificationListeners.forEach { listener ->
                        runCatching { listener(method, params) }
                    }
                },
                onServerRequest = onServerRequest,
                onProtocolError = onFailure,
            )
            val transport = JsonRpcProcessTransport.start(
                command = command,
                workspaceRoot = workspaceRoot,
                workingDirectory = workingDirectory,
                environment = environment,
                maximumMessageBytes = maximumMessageBytes,
                onMessage = rpc::acceptMessage,
                onFailure = { error ->
                    val activeSession = synchronized(startupLock) {
                        val current = sessionReference.get()
                        if (current == null) earlyTransportFailure = error
                        current
                    }
                    activeSession?.handleTransportFailure(error)
                },
            )
            transportReference.set(transport)
            val session = LspProcessSession(transport, rpc, onFailure, notificationListeners)
            val startupFailure = synchronized(startupLock) {
                sessionReference.set(session)
                earlyTransportFailure
            }
            startupFailure?.let(session::handleTransportFailure)
            return session
        }
    }
}
