package com.astracode.connectors

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

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

/** Small HTTP transport shared by all provider adapters. */
internal class AiProviderTransport {
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
        } catch (error: AiProviderRequestException) {
            throw error
        } catch (error: Exception) {
            throw AiProviderRequestException(
                provider.displayName + " network request failed",
                null,
                true,
                error,
            )
        } finally {
            connection.disconnect()
        }
    }

    fun stream(
        url: String,
        apiKey: String,
        provider: AiProvider,
        body: JSONObject,
        onEvent: (AiStreamEvent) -> Unit,
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
            return accumulator.result().let { result ->
                if (result.text.isBlank() && result.toolCalls.isEmpty()) {
                    val responseError = result.finalPayload?.optString("status")
                    if (responseError != null && responseError != "completed") {
                        throw AiProviderRequestException(
                            provider.displayName + " returned status " + responseError,
                            null,
                            false,
                        )
                    }
                    throw IOException(provider.displayName + " returned no text or tool calls")
                }
                result
            }
        } catch (error: AiProviderRequestException) {
            throw error
        } catch (error: AiProviderProtocolException) {
            throw AiProviderRequestException(
                provider.displayName + " returned invalid streaming data: " + error.message,
                null,
                false,
                error,
            )
        } catch (error: IOException) {
            throw AiProviderRequestException(
                provider.displayName + " streaming request failed: " + (error.message ?: "network error"),
                null,
                true,
                error,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(
        url: String,
        method: String,
        apiKey: String,
        provider: AiProvider,
        hasBody: Boolean,
    ): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.instanceFollowRedirects = false
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/json")
        if (hasBody) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        when {
            provider.googleKeyHeader -> connection.setRequestProperty("x-goog-api-key", apiKey)
            provider.anthropicHeaders -> {
                connection.setRequestProperty("x-api-key", apiKey)
                connection.setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
            }
            else -> connection.setRequestProperty("Authorization", "Bearer " + apiKey)
        }
        return connection
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
                    throw IOException("AI response exceeded size limit")
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
            throw IOException(provider.displayName + " returned invalid JSON", error)
        }
        result.optJSONObject("error")?.let { error ->
            throw IOException(error.optString("message", provider.displayName + " request failed"))
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
        const val ANTHROPIC_VERSION = "2026-10-21"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 120_000
        const val MAX_RESPONSE_CHARS = 2 * 1024 * 1024
    }
}
