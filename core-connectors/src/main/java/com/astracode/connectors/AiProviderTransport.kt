package com.astracode.connectors

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class AiProviderRequestException(
    message: String,
    val statusCode: Int?,
    val retryable: Boolean,
    cause: Throwable? = null,
) : IOException(message, cause)

class AiProviderProtocolException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

class AiRequestCancelledException(
    message: String = "The AI request was cancelled.",
    cause: Throwable? = null,
) : IOException(message, cause)

/** Small HTTP transport shared by all provider adapters. */
internal class AiProviderTransport {
    private val cancellationRequested = AtomicBoolean(false)
    private val activeConnections = ConcurrentHashMap.newKeySet<HttpURLConnection>()

    /** Starts a new top-level user operation. */
    fun beginOperation() {
        cancellationRequested.set(false)
    }

    /** Immediately disconnects all active provider requests for this operation. */
    fun cancelActiveRequests() {
        cancellationRequested.set(true)
        activeConnections.toList().forEach { connection ->
            runCatching { connection.disconnect() }
        }
    }

    fun isCancellationRequested(): Boolean = cancellationRequested.get()

    fun getJson(
        url: String,
        apiKey: String,
        provider: AiProvider,
    ): JSONObject {
        val connection = openConnection(url, "GET", apiKey, provider, false)
        try {
            val status = connection.responseCode
            val body = readResponse(connection, status)
            if (status !in 200..299) throw requestError(provider, status, body)
            return parseJson(body, provider)
        } catch (cancelled: AiRequestCancelledException) {
            throw cancelled
        } catch (error: AiProviderRequestException) {
            if (cancellationRequested.get()) {
                throw AiRequestCancelledException(cause = error)
            }
            throw error
        } catch (error: AiProviderProtocolException) {
            throw AiProviderRequestException(
                provider.displayName + " returned an invalid model catalogue",
                null,
                false,
                error,
            )
        } catch (error: Exception) {
            if (cancellationRequested.get()) throw AiRequestCancelledException(cause = error)
            throw AiProviderRequestException(
                provider.displayName + " network request failed",
                null,
                true,
                error,
            )
        } finally {
            releaseConnection(connection)
        }
    }

    fun stream(
        url: String,
        apiKey: String,
        provider: AiProvider,
        body: JSONObject,
        onEvent: (AiStreamEvent) -> Unit,
        pricing: AiModelPricing? = null,
    ): AiStreamResult {
        body.put("stream", true)
        val connection = openConnection(url, "POST", apiKey, provider, true)
        try {
            connection.setRequestProperty("Accept", "text/event-stream")
            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(StandardCharsets.UTF_8))
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                val response = readResponse(connection, status)
                throw requestError(provider, status, response)
            }
            val accumulator = AiStreamAccumulator(provider)
            val input = connection.inputStream.bufferedReader(StandardCharsets.UTF_8)
            input.use { reader -> AiSseReader.read(reader, accumulator, onEvent) }
            return accumulator.result().let { rawResult ->
                val result = rawResult.copy(usage = rawResult.usage?.withPricing(pricing))
                val terminalStatus = result.finalPayload?.optString("status")
                if (terminalStatus in setOf("failed", "cancelled", "incomplete")) {
                    throw AiProviderRequestException(
                        provider.displayName + " returned status " + terminalStatus,
                        null,
                        false,
                    )
                }
                if (result.finalPayload == null) {
                    throw AiProviderProtocolException(
                        provider.displayName + " stream ended without a completion event",
                    )
                }
                if (result.text.isBlank() && result.toolCalls.isEmpty() &&
                    terminalStatus != "requires_action"
                ) {
                    throw AiProviderProtocolException(provider.displayName + " returned no text or tool calls")
                }
                result.usage?.let { usage -> onEvent(AiStreamEvent.Usage(usage)) }
                result
            }
        } catch (error: AiProviderRequestException) {
            if (cancellationRequested.get()) {
                throw AiRequestCancelledException(cause = error)
            }
            throw error
        } catch (error: AiProviderProtocolException) {
            throw AiProviderRequestException(
                provider.displayName + " returned invalid streaming data: " + error.message,
                null,
                false,
                error,
            )
        } catch (error: IOException) {
            if (cancellationRequested.get()) throw AiRequestCancelledException(cause = error)
            throw AiProviderRequestException(
                provider.displayName + " streaming request failed: " + (error.message ?: "network error"),
                null,
                true,
                error,
            )
        } finally {
            releaseConnection(connection)
        }
    }

    private fun openConnection(
        url: String,
        method: String,
        apiKey: String,
        provider: AiProvider,
        hasBody: Boolean,
    ): HttpURLConnection {
        if (cancellationRequested.get()) throw AiRequestCancelledException()
        val connection = URL(url).openConnection() as HttpURLConnection
        activeConnections.add(connection)
        try {
            if (cancellationRequested.get()) throw AiRequestCancelledException()
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            if (hasBody) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            AiProviderAdapters.forProvider(provider)
                .authenticationHeaders(apiKey)
                .forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (cancellationRequested.get()) throw AiRequestCancelledException()
            return connection
        } catch (error: Exception) {
            releaseConnection(connection)
            if (cancellationRequested.get()) throw AiRequestCancelledException(cause = error)
            throw error
        }
    }

    private fun releaseConnection(connection: HttpURLConnection) {
        activeConnections.remove(connection)
        runCatching { connection.disconnect() }
    }

    private fun readResponse(connection: HttpURLConnection, status: Int): String {
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader(StandardCharsets.UTF_8)?.use { reader ->
            val result = StringBuilder()
            val buffer = CharArray(4_096)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                if (result.length + count > MAX_RESPONSE_CHARS) {
                    throw AiProviderProtocolException("AI response exceeded size limit")
                }
                result.append(buffer, 0, count)
            }
            result.toString()
        }.orEmpty()
    }

    private fun parseJson(value: String, provider: AiProvider): JSONObject {
        val result = try {
            JSONObject(value)
        } catch (error: Exception) {
            throw AiProviderProtocolException(provider.displayName + " returned invalid JSON", error)
        }
        result.optJSONObject("error")?.let { error ->
            val details = listOf(
                error.optString("type"),
                error.optString("code"),
                error.optString("message"),
            ).joinToString(" ").lowercase()
            val retryable = listOf(
                "rate_limit", "rate limit", "overloaded", "timeout",
                "temporarily_unavailable", "temporarily unavailable",
                "internal server", "server_error", "service unavailable",
            ).any { it in details }
            throw AiProviderRequestException(
                error.optString("message", provider.displayName + " request failed"),
                null,
                retryable,
            )
        }
        return result
    }

    private fun requestError(provider: AiProvider, status: Int, body: String): AiProviderRequestException {
        val message = runCatching {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
        }.getOrNull()
        val retryable = status == 408 || status == 409 || status == 425 ||
            status == 429 || status in 500..599
        return AiProviderRequestException(
            message ?: (provider.displayName + " request failed with HTTP " + status),
            status,
            retryable,
        )
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 120_000
        const val MAX_RESPONSE_CHARS = 2 * 1024 * 1024
    }
}
