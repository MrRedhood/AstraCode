package com.mrredhood.astracode

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Supplies credentials at call time. Implementations should use a secure vault and never log keys. */
fun interface AiCredentialSource {
    suspend fun apiKey(providerId: CloudAiProviderId): String?
}

data class AiHttpRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: String = "",
    val method: String = "POST"
)

data class AiHttpResponse(val statusCode: Int, val body: String)

interface AiHttpTransport {
    suspend fun execute(request: AiHttpRequest): AiHttpResponse
}

/**
 * Minimal HTTPS-only transport that avoids redirects (which could forward credentials to another
 * host), bounds both request and response sizes, and does not log request content or headers.
 */
class UrlConnectionAiHttpTransport : AiHttpTransport {
    override suspend fun execute(request: AiHttpRequest): AiHttpResponse = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val uri = URI(request.url)
        require("https".equals(uri.scheme, ignoreCase = true) && !uri.host.isNullOrBlank()) {
            "AI endpoint must use HTTPS"
        }
        require(uri.userInfo == null && uri.fragment == null) {
            "AI endpoint must not contain user information or a fragment"
        }
        require(request.method == "GET" || request.method == "POST") { "Unsupported HTTP method" }
        val requestBytes = request.body.toByteArray(StandardCharsets.UTF_8)
        if (requestBytes.size > MAX_REQUEST_BYTES) throw AiRequestLimitException()
        require(request.method == "POST" || request.body.isEmpty()) { "GET requests must not contain a body" }
        val connection = (URL(request.url).openConnection() as? HttpURLConnection)
            ?: throw IOException("Unsupported network connection")
        try {
            connection.requestMethod = request.method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.doOutput = request.method == "POST"
            if (request.method == "POST") {
                connection.setFixedLengthStreamingMode(requestBytes.size)
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            connection.setRequestProperty("Accept", "application/json")
            request.headers.forEach { (name, value) ->
                require(name.matches(Regex("[A-Za-z0-9-]{1,64}"))) { "Invalid HTTP header name" }
                require(value.none { it == '\r' || it == '\n' }) { "Invalid HTTP header value" }
                connection.setRequestProperty(name, value)
            }
            currentCoroutineContext().ensureActive()
            if (request.method == "POST") {
                connection.outputStream.use { it.write(requestBytes) }
            }
            currentCoroutineContext().ensureActive()
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { readBoundedUtf8(it, MAX_RESPONSE_BYTES) }.orEmpty()
            currentCoroutineContext().ensureActive()
            AiHttpResponse(status, body)
        } finally {
            connection.disconnect()
        }
    }

    private fun readBoundedUtf8(stream: java.io.InputStream, cap: Int): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            total += count
            if (total > cap) throw AiResponseLimitException()
            output.write(buffer, 0, count)
        }
        return String(output.toByteArray(), StandardCharsets.UTF_8)
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
        const val MAX_REQUEST_BYTES = 64 * 1024 * 1024
        /** Covers nested JSON escaping for a maximum-size AI create-file proposal. */
        const val MAX_RESPONSE_BYTES = 64 * 1024 * 1024
    }
}

private class AiRequestLimitException : IOException("AI request exceeded the configured size bound")
private class AiResponseLimitException : IOException("AI response exceeded the configured size bound")
private class AiUnsupportedAttachmentException(message: String) : IllegalArgumentException(message)

/**
 * Shared failure mapping and safety checks. All protocol classes below have independent request and
 * response mappings; API keys are acquired at call time and are never embedded in URLs.
 */
abstract class JsonCloudAiProvider(
    final override val id: CloudAiProviderId,
    baseUrl: String,
    private val credentials: AiCredentialSource,
    private val transport: AiHttpTransport,
    modelCapabilities: Map<String, AiModelCapabilities>
) : CloudAiProvider {
    protected val baseUrl: String = validateBaseUrl(baseUrl)
    private val capabilitiesByModel = modelCapabilities.toMap()

    final override fun capabilities(modelId: String): AiModelCapabilities? =
        capabilitiesByModel[modelId]?.copy(supportsVision = false, supportsStreaming = false)

    protected abstract fun pathAndHeaders(apiKey: String, modelId: String): Pair<String, Map<String, String>>
    protected abstract fun createPayload(modelId: String, request: AiGenerationRequest): JSONObject
    protected abstract fun parsePayload(modelId: String, payload: JSONObject): AiGenerationResponse

    final override suspend fun generate(
        modelId: String,
        request: AiGenerationRequest
    ): AiProviderResult {
        val capabilities = capabilities(modelId)
            ?: return failure(AiProviderFailureCode.UNSUPPORTED_MODEL, false, "The selected model is not configured for this provider.")
        if (request.maxOutputTokens != null && request.maxOutputTokens > capabilities.maxOutputTokens) {
            return failure(AiProviderFailureCode.INVALID_REQUEST, false, "Requested output limit exceeds this model's configured limit.")
        }

        return try {
            currentCoroutineContext().ensureActive()
            val apiKey = credentials.apiKey(id)?.trim()
            if (apiKey.isNullOrEmpty()) {
                return failure(AiProviderFailureCode.MISSING_CREDENTIALS, false, "Add an API key for the selected provider.")
            }
            if (apiKey.length > MAX_API_KEY_LENGTH || apiKey.any { it == '\r' || it == '\n' || Character.isISOControl(it) }) {
                return failure(AiProviderFailureCode.MISSING_CREDENTIALS, false, "The configured API credential is invalid.")
            }

            val (path, headers) = pathAndHeaders(apiKey, modelId)
            val payload = createPayload(modelId, request).toString()
            if (payload.length > UrlConnectionAiHttpTransport.MAX_REQUEST_BYTES) {
                return failure(AiProviderFailureCode.INVALID_REQUEST, false, "The provider payload exceeds the 64 MiB request limit. Reduce the attachment size.")
            }
            val response = transport.execute(AiHttpRequest(joinUrl(baseUrl, path), headers, payload))
            currentCoroutineContext().ensureActive()
            if (response.statusCode !in 200..299) return statusFailure(response.statusCode)
            val parsed = try {
                JSONObject(response.body)
            } catch (error: JSONException) {
                return failure(AiProviderFailureCode.UNKNOWN, false, "The provider returned an unreadable response.")
            }
            AiProviderResult.Success(parsePayload(modelId, parsed))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (requestTooLarge: AiRequestLimitException) {
            failure(AiProviderFailureCode.INVALID_REQUEST, false, "The attachment payload exceeds the 64 MiB request limit. Reduce file sizes.")
        } catch (unsupported: AiUnsupportedAttachmentException) {
            failure(AiProviderFailureCode.INVALID_REQUEST, false, unsupported.message ?: "The selected provider cannot accept this attachment format.")
        } catch (tooLarge: AiResponseLimitException) {
            failure(AiProviderFailureCode.RESPONSE_TOO_LARGE, false, "The provider response exceeded the 4 MiB safety limit.")
        } catch (timeout: SocketTimeoutException) {
            failure(AiProviderFailureCode.NETWORK, true, "The provider request timed out.")
        } catch (io: IOException) {
            failure(AiProviderFailureCode.NETWORK, true, "Unable to reach the provider. Check your connection and try again.")
        } catch (invalid: IllegalArgumentException) {
            failure(AiProviderFailureCode.INVALID_REQUEST, false, "The provider request configuration is invalid.")
        } catch (json: JSONException) {
            failure(AiProviderFailureCode.UNKNOWN, false, "The provider response did not contain the expected fields.")
        } catch (unexpected: Exception) {
            failure(AiProviderFailureCode.UNKNOWN, false, "The provider request failed unexpectedly.")
        }
    }

    private fun statusFailure(status: Int): AiProviderResult.Failure = when (status) {
        401, 403 -> failure(AiProviderFailureCode.AUTHENTICATION, false, "The provider rejected the API credential.")
        404 -> failure(AiProviderFailureCode.UNSUPPORTED_MODEL, false, "The provider endpoint or model was not found.")
        408, 425, 500, 502, 503, 504 -> failure(AiProviderFailureCode.UNAVAILABLE, true, "The provider is temporarily unavailable.")
        429 -> failure(AiProviderFailureCode.RATE_LIMITED, true, "The provider rate limit was reached.")
        400, 413, 422 -> failure(AiProviderFailureCode.INVALID_REQUEST, false, "The provider rejected the request parameters.")
        else -> if (status >= 500) {
            failure(AiProviderFailureCode.UNAVAILABLE, true, "The provider is temporarily unavailable.")
        } else {
            failure(AiProviderFailureCode.UNKNOWN, false, "The provider returned HTTP $status.")
        }
    }

    protected fun maxOutput(request: AiGenerationRequest, modelId: String): Int =
        request.maxOutputTokens ?: minOf(DEFAULT_MAX_OUTPUT_TOKENS, requireNotNull(capabilities(modelId)).maxOutputTokens)

    protected fun usage(input: Int?, output: Int?): AiTokenUsage? =
        if (input == null && output == null) null else AiTokenUsage(input, output)

    private fun failure(code: AiProviderFailureCode, retryable: Boolean, detail: String) =
        AiProviderResult.Failure(AiProviderFailure(code, retryable, detail))

    companion object {
        const val DEFAULT_MAX_OUTPUT_TOKENS = 1024
        const val MAX_API_KEY_LENGTH = 4096

        fun validateBaseUrl(value: String): String {
            val cleaned = value.trim().trimEnd('/')
            val uri = runCatching { URI(cleaned) }.getOrNull()
                ?: throw IllegalArgumentException("AI endpoint URL is invalid")
            require("https".equals(uri.scheme, ignoreCase = true)) { "AI endpoint must use HTTPS" }
            require(!uri.host.isNullOrBlank()) { "AI endpoint must include a host" }
            require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
                "AI endpoint must not include credentials, query or fragment"
            }
            return cleaned
        }

        fun joinUrl(base: String, path: String): String = base.trimEnd('/') + "/" + path.trimStart('/')

    }
}


private fun attachmentBytes(attachment: AiChatAttachment): ByteArray {
    val data = attachment.data
        ?: throw AiUnsupportedAttachmentException("An attachment could not be loaded. Remove it and attach the file again.")
    if (data.size != attachment.byteCount || data.size > AiChatAttachmentPolicy.MAX_FILE_BYTES) {
        throw AiUnsupportedAttachmentException("An attachment does not match its saved size. Attach it again.")
    }
    return data
}

private fun attachmentBase64(attachment: AiChatAttachment): String =
    Base64.getEncoder().encodeToString(attachmentBytes(attachment))

private fun attachmentDataUri(attachment: AiChatAttachment): String =
    "data:" + AiChatAttachmentPolicy.normalizeMimeType(attachment.mimeType) +
        ";base64," + attachmentBase64(attachment)

private fun attachmentText(attachment: AiChatAttachment): String? {
    if (!AiChatAttachmentPolicy.isTextLike(attachment.name, attachment.mimeType)) return null
    val data = attachmentBytes(attachment)
    return AiChatAttachmentPolicy.decodeUtf8(data)
        ?: throw AiUnsupportedAttachmentException(
            "The text attachment '" + AiChatAttachmentPolicy.safeLabel(attachment.name) +
                "' is not valid UTF-8. Remove it or attach a UTF-8 text copy."
        )
}

private fun attachmentLabelledText(attachment: AiChatAttachment, decoded: String): String =
    "[Attached file: " + AiChatAttachmentPolicy.safeLabel(attachment.name) + "]\n" + decoded

private fun requireResponsesFile(attachment: AiChatAttachment) {
    val extension = attachment.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val supported = extension in setOf(
        "pdf", "doc", "docx", "dot", "odt", "rtf", "ppt", "pptx", "pot", "ppa",
        "pps", "xls", "xlsx", "xla", "xlb", "xlc", "xlm", "xlt", "xlw", "csv",
        "tsv", "iif", "txt", "md", "markdown", "json", "html", "xml", "asm",
        "bat", "c", "cc", "conf", "cpp", "css", "cxx", "h", "hh", "htm", "js",
        "ksh", "log", "mjs", "nws", "pl", "py", "rb", "rst", "s", "sql", "tex",
        "text", "vtt", "vcf"
    )
    if (!supported) {
        throw AiUnsupportedAttachmentException(
            "The configured OpenAI Responses API does not accept this file type as an inline file input. Try Gemini for audio/video or remove this attachment."
        )
    }
}

private fun openAiChatContent(message: AiChatMessage): Any {
    if (message.attachments.isEmpty()) return message.content
    val content = JSONArray()
    if (message.content.isNotBlank()) content.put(JSONObject().put("type", "text").put("text", message.content))
    message.attachments.forEach { attachment ->
        val text = attachmentText(attachment)
        when {
            text != null -> content.put(JSONObject().put("type", "text").put("text", attachmentLabelledText(attachment, text)))
            AiChatAttachmentPolicy.isInlineImageMime(attachment.mimeType) -> content.put(
                JSONObject().put("type", "image_url")
                    .put("image_url", JSONObject().put("url", attachmentDataUri(attachment)).put("detail", "auto"))
            )
            AiChatAttachmentPolicy.isPdf(attachment) -> content.put(
                JSONObject().put("type", "file")
                    .put("file", JSONObject()
                        .put("filename", AiChatAttachmentPolicy.safeLabel(attachment.name))
                        .put("file_data", attachmentDataUri(attachment)))
            )
            else -> throw AiUnsupportedAttachmentException(
                "This OpenAI-compatible chat endpoint supports text, PNG/JPEG/WEBP/GIF images and PDF attachments. Use Gemini for audio/video or remove this attachment."
            )
        }
    }
    return content
}

private fun openAiResponsesContent(message: AiChatMessage): Any {
    if (message.attachments.isEmpty()) return message.content
    val content = JSONArray()
    if (message.content.isNotBlank()) content.put(JSONObject().put("type", "input_text").put("text", message.content))
    message.attachments.forEach { attachment ->
        val text = attachmentText(attachment)
        when {
            text != null -> content.put(JSONObject().put("type", "input_text").put("text", attachmentLabelledText(attachment, text)))
            AiChatAttachmentPolicy.isInlineImageMime(attachment.mimeType) -> content.put(
                JSONObject().put("type", "input_image")
                    .put("image_url", attachmentDataUri(attachment)).put("detail", "auto")
            )
            else -> {
                requireResponsesFile(attachment)
                content.put(
                    JSONObject().put("type", "input_file")
                        .put("filename", AiChatAttachmentPolicy.safeLabel(attachment.name))
                        .put("file_data", attachmentDataUri(attachment))
                )
            }
        }
    }
    return content
}

private fun geminiParts(message: AiChatMessage): JSONArray {
    val parts = JSONArray()
    if (message.content.isNotBlank()) parts.put(JSONObject().put("text", message.content))
    message.attachments.forEach { attachment ->
        val text = attachmentText(attachment)
        if (text != null) {
            parts.put(JSONObject().put("text", attachmentLabelledText(attachment, text)))
        } else {
            parts.put(JSONObject().put("inlineData",
                JSONObject()
                    .put("mimeType", AiChatAttachmentPolicy.normalizeMimeType(attachment.mimeType))
                    .put("data", attachmentBase64(attachment))))
        }
    }
    return parts
}

private fun anthropicContent(message: AiChatMessage): Any {
    if (message.attachments.isEmpty()) return message.content
    val content = JSONArray()
    if (message.content.isNotBlank()) content.put(JSONObject().put("type", "text").put("text", message.content))
    message.attachments.forEach { attachment ->
        val text = attachmentText(attachment)
        when {
            text != null -> content.put(JSONObject().put("type", "text").put("text", attachmentLabelledText(attachment, text)))
            AiChatAttachmentPolicy.isInlineImageMime(attachment.mimeType) -> content.put(
                JSONObject().put("type", "image")
                    .put("source", JSONObject()
                        .put("type", "base64")
                        .put("media_type", AiChatAttachmentPolicy.normalizeMimeType(attachment.mimeType))
                        .put("data", attachmentBase64(attachment)))
            )
            AiChatAttachmentPolicy.isPdf(attachment) -> content.put(
                JSONObject().put("type", "document")
                    .put("source", JSONObject()
                        .put("type", "base64")
                        .put("media_type", "application/pdf")
                        .put("data", attachmentBase64(attachment)))
            )
            else -> throw AiUnsupportedAttachmentException(
                "The configured Anthropic Messages API adapter supports text, PNG/JPEG/WEBP/GIF images and PDF documents in this message. Use Gemini for audio/video or remove this attachment."
            )
        }
    }
    return content
}

private fun cohereContent(message: AiChatMessage): Any {
    if (message.attachments.isEmpty()) return message.content
    val content = JSONArray()
    if (message.content.isNotBlank()) content.put(JSONObject().put("type", "text").put("text", message.content))
    message.attachments.forEach { attachment ->
        val text = attachmentText(attachment)
        when {
            text != null -> content.put(JSONObject().put("type", "text").put("text", attachmentLabelledText(attachment, text)))
            AiChatAttachmentPolicy.isInlineImageMime(attachment.mimeType) -> content.put(
                JSONObject().put("type", "image_url")
                    .put("image_url", JSONObject().put("url", attachmentDataUri(attachment)))
            )
            else -> throw AiUnsupportedAttachmentException(
                "The configured Cohere Chat API adapter supports text and PNG/JPEG/WEBP/GIF image attachments. Use Gemini for audio/video or remove this attachment."
            )
        }
    }
    return content
}

/** OpenAI Chat Completions-compatible adapter for OpenRouter, xAI and other compatible endpoints. */
class OpenAiCompatibleChatProvider(
    id: CloudAiProviderId,
    baseUrl: String,
    credentials: AiCredentialSource,
    transport: AiHttpTransport = UrlConnectionAiHttpTransport(),
    modelCapabilities: Map<String, AiModelCapabilities>
) : JsonCloudAiProvider(id, baseUrl, credentials, transport, modelCapabilities) {
    override fun pathAndHeaders(apiKey: String, modelId: String) =
        "chat/completions" to mapOf("Authorization" to "Bearer $apiKey")

    override fun createPayload(modelId: String, request: AiGenerationRequest): JSONObject {
        val messages = JSONArray()
        request.messages.forEach { message ->
            messages.put(JSONObject()
                .put("role", message.role.name.lowercase(Locale.ROOT))
                .put("content", openAiChatContent(message)))
        }
        val payload = JSONObject().put("model", modelId).put("messages", messages).put("stream", false)
        request.maxOutputTokens?.let { payload.put("max_tokens", it) }
        request.temperature?.let { payload.put("temperature", it) }
        return payload
    }

    override fun parsePayload(modelId: String, payload: JSONObject): AiGenerationResponse {
        val choices = payload.optJSONArray("choices") ?: throw JSONException("Missing choices")
        val message = choices.optJSONObject(0)?.optJSONObject("message") ?: throw JSONException("Missing message")
        val content = message.opt("content")
        val text = when (content) {
            is String -> content
            is JSONArray -> textFromContentArray(content)
            else -> ""
        }
        if (text.isBlank()) throw JSONException("Missing generated text")
        val tokenUsage = payload.optJSONObject("usage")
        return AiGenerationResponse(
            text = text,
            modelId = payload.optString("model").takeIf { it.isNotBlank() } ?: modelId,
            usage = usage(tokenUsage?.nullableInt("prompt_tokens"), tokenUsage?.nullableInt("completion_tokens"))
        )
    }
}

/** Native OpenAI Responses API adapter. */
class OpenAiResponsesProvider(
    baseUrl: String,
    credentials: AiCredentialSource,
    transport: AiHttpTransport = UrlConnectionAiHttpTransport(),
    modelCapabilities: Map<String, AiModelCapabilities>
) : JsonCloudAiProvider(CloudAiProviderId.OPENAI, baseUrl, credentials, transport, modelCapabilities) {
    override fun pathAndHeaders(apiKey: String, modelId: String) =
        "responses" to mapOf("Authorization" to "Bearer $apiKey")

    override fun createPayload(modelId: String, request: AiGenerationRequest): JSONObject {
        val systemText = request.messages.filter { it.role == AiMessageRole.SYSTEM }.joinToString("\n\n") { it.content }
        val input = JSONArray()
        request.messages.filter { it.role != AiMessageRole.SYSTEM }.forEach { message ->
            input.put(JSONObject()
                .put("role", message.role.name.lowercase(Locale.ROOT))
                .put("content", openAiResponsesContent(message)))
        }
        val payload = JSONObject()
            .put("model", modelId)
            .put("input", input)
            .put("store", false)
            .put("max_output_tokens", maxOutput(request, modelId))
        if (systemText.isNotBlank()) payload.put("instructions", systemText)
        request.temperature?.let { payload.put("temperature", it) }
        return payload
    }

    override fun parsePayload(modelId: String, payload: JSONObject): AiGenerationResponse {
        val outputText = StringBuilder()
        val output = payload.optJSONArray("output") ?: JSONArray()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            if (item.optString("type") != "message") continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val block = content.optJSONObject(j) ?: continue
                if (block.optString("type") == "output_text") outputText.append(block.optString("text"))
            }
        }
        if (outputText.isBlank()) {
            val convenientText = payload.optString("output_text")
            if (convenientText.isNotBlank()) outputText.append(convenientText)
        }
        if (outputText.isBlank()) throw JSONException("Missing output text")
        val tokenUsage = payload.optJSONObject("usage")
        return AiGenerationResponse(
            outputText.toString(),
            payload.optString("model").takeIf { it.isNotBlank() } ?: modelId,
            usage(tokenUsage?.nullableInt("input_tokens"), tokenUsage?.nullableInt("output_tokens"))
        )
    }
}

/** Native Gemini generateContent adapter; API key is sent in x-goog-api-key, not in the URL. */
class GeminiGenerateContentProvider(
    baseUrl: String,
    credentials: AiCredentialSource,
    transport: AiHttpTransport = UrlConnectionAiHttpTransport(),
    modelCapabilities: Map<String, AiModelCapabilities>
) : JsonCloudAiProvider(CloudAiProviderId.GEMINI, baseUrl, credentials, transport, modelCapabilities) {
    override fun pathAndHeaders(apiKey: String, modelId: String): Pair<String, Map<String, String>> {
        val encodedModel = java.net.URLEncoder.encode(modelId.removePrefix("models/"), "UTF-8").replace("+", "%20")
        return "models/$encodedModel:generateContent" to mapOf("x-goog-api-key" to apiKey)
    }

    override fun createPayload(modelId: String, request: AiGenerationRequest): JSONObject {
        val contents = JSONArray()
        request.messages.filter { it.role != AiMessageRole.SYSTEM }.forEach { message ->
            contents.put(JSONObject()
                .put("role", if (message.role == AiMessageRole.ASSISTANT) "model" else "user")
                .put("parts", geminiParts(message)))
        }
        val payload = JSONObject().put("contents", contents)
        val systemText = request.messages.filter { it.role == AiMessageRole.SYSTEM }.joinToString("\n\n") { it.content }
        if (systemText.isNotBlank()) {
            payload.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemText))))
        }
        val generation = JSONObject().put("candidateCount", 1).put("maxOutputTokens", maxOutput(request, modelId))
        request.temperature?.let { generation.put("temperature", it) }
        payload.put("generationConfig", generation)
        return payload
    }

    override fun parsePayload(modelId: String, payload: JSONObject): AiGenerationResponse {
        val candidates = payload.optJSONArray("candidates") ?: throw JSONException("Missing candidates")
        val content = candidates.optJSONObject(0)?.optJSONObject("content") ?: throw JSONException("Missing candidate content")
        val parts = content.optJSONArray("parts") ?: throw JSONException("Missing candidate parts")
        val text = buildString {
            for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text").orEmpty())
        }
        if (text.isBlank()) throw JSONException("Missing generated text")
        val usageJson = payload.optJSONObject("usageMetadata")
        return AiGenerationResponse(
            text = text,
            modelId = payload.optString("modelVersion").takeIf { it.isNotBlank() } ?: modelId,
            usage = usage(usageJson?.nullableInt("promptTokenCount"), usageJson?.nullableInt("candidatesTokenCount"))
        )
    }
}

/** Native Anthropic Messages API adapter. */
class AnthropicMessagesProvider(
    baseUrl: String,
    credentials: AiCredentialSource,
    transport: AiHttpTransport = UrlConnectionAiHttpTransport(),
    modelCapabilities: Map<String, AiModelCapabilities>
) : JsonCloudAiProvider(CloudAiProviderId.ANTHROPIC, baseUrl, credentials, transport, modelCapabilities) {
    override fun pathAndHeaders(apiKey: String, modelId: String) = "v1/messages" to mapOf(
        "x-api-key" to apiKey,
        "anthropic-version" to "2023-06-01"
    )

    override fun createPayload(modelId: String, request: AiGenerationRequest): JSONObject {
        val messages = JSONArray()
        request.messages.filter { it.role != AiMessageRole.SYSTEM }.forEach { message ->
            messages.put(JSONObject()
                .put("role", message.role.name.lowercase(Locale.ROOT))
                .put("content", anthropicContent(message)))
        }
        val payload = JSONObject()
            .put("model", modelId)
            .put("messages", messages)
            .put("max_tokens", maxOutput(request, modelId))
        val systemText = request.messages.filter { it.role == AiMessageRole.SYSTEM }.joinToString("\n\n") { it.content }
        if (systemText.isNotBlank()) payload.put("system", systemText)
        request.temperature?.let { payload.put("temperature", it) }
        return payload
    }

    override fun parsePayload(modelId: String, payload: JSONObject): AiGenerationResponse {
        val content = payload.optJSONArray("content") ?: throw JSONException("Missing content")
        val text = textFromContentArray(content)
        if (text.isBlank()) throw JSONException("Missing generated text")
        val usageJson = payload.optJSONObject("usage")
        return AiGenerationResponse(
            text,
            payload.optString("model").takeIf { it.isNotBlank() } ?: modelId,
            usage(usageJson?.nullableInt("input_tokens"), usageJson?.nullableInt("output_tokens"))
        )
    }
}

/** Native Cohere Chat v2 adapter. */
class CohereChatV2Provider(
    baseUrl: String,
    credentials: AiCredentialSource,
    transport: AiHttpTransport = UrlConnectionAiHttpTransport(),
    modelCapabilities: Map<String, AiModelCapabilities>
) : JsonCloudAiProvider(CloudAiProviderId.COHERE, baseUrl, credentials, transport, modelCapabilities) {
    override fun pathAndHeaders(apiKey: String, modelId: String) =
        "chat" to mapOf("Authorization" to "Bearer $apiKey")

    override fun createPayload(modelId: String, request: AiGenerationRequest): JSONObject {
        val messages = JSONArray()
        request.messages.forEach { message ->
            messages.put(JSONObject()
                .put("role", message.role.name.lowercase(Locale.ROOT))
                .put("content", cohereContent(message)))
        }
        val payload = JSONObject()
            .put("model", modelId)
            .put("messages", messages)
            .put("stream", false)
            .put("max_tokens", maxOutput(request, modelId))
        request.temperature?.let { payload.put("temperature", it) }
        return payload
    }

    override fun parsePayload(modelId: String, payload: JSONObject): AiGenerationResponse {
        val content = payload.optJSONObject("message")?.optJSONArray("content") ?: throw JSONException("Missing message content")
        val text = textFromContentArray(content)
        if (text.isBlank()) throw JSONException("Missing generated text")
        val usageJson = payload.optJSONObject("usage")
        val tokenCounts = usageJson?.optJSONObject("tokens")
        val billed = payload.optJSONObject("meta")?.optJSONObject("billed_units")
        val inputTokens = tokenCounts?.nullableInt("input_tokens")
            ?: usageJson?.nullableInt("input_tokens")
            ?: billed?.nullableInt("input_tokens")
        val outputTokens = tokenCounts?.nullableInt("output_tokens")
            ?: usageJson?.nullableInt("output_tokens")
            ?: billed?.nullableInt("output_tokens")
        return AiGenerationResponse(
            text,
            modelId,
            usage(inputTokens, outputTokens)
        )
    }
}

private fun textFromContentArray(array: JSONArray, textField: String = "text"): String =
    buildString {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            if (item.optString("type") == "text") append(item.optString(textField))
        }
    }

private fun JSONObject.nullableInt(name: String): Int? =
    if (!has(name) || isNull(name)) null else optInt(name).takeIf { it >= 0 }

/** Creates protocol-specific providers from catalog metadata and caller-owned configuration. */
class CloudAiProviderFactory(
    private val credentials: AiCredentialSource,
    private val transport: AiHttpTransport = UrlConnectionAiHttpTransport()
) {
    fun create(
        id: CloudAiProviderId,
        baseUrlOverride: String? = null,
        modelCapabilities: Map<String, AiModelCapabilities>
    ): CloudAiProvider {
        val definition = requireNotNull(CloudAiProviderCatalog.find(id)) { "Unknown AI provider" }
        require(baseUrlOverride == null || definition.supportsCustomBaseUrl) {
            "Custom endpoints are not supported for this provider"
        }
        val endpoint = baseUrlOverride?.trim()?.takeIf { it.isNotEmpty() }
            ?: definition.defaultBaseUrl
            ?: throw IllegalArgumentException("A custom HTTPS endpoint is required for this provider")
        val safeEndpoint = JsonCloudAiProvider.validateBaseUrl(endpoint)
        return when (definition.apiProtocol) {
            CloudAiApiProtocol.OPENAI_CHAT_COMPLETIONS ->
                OpenAiCompatibleChatProvider(id, safeEndpoint, credentials, transport, modelCapabilities)
            CloudAiApiProtocol.OPENAI_RESPONSES ->
                OpenAiResponsesProvider(safeEndpoint, credentials, transport, modelCapabilities)
            CloudAiApiProtocol.GEMINI_GENERATE_CONTENT ->
                GeminiGenerateContentProvider(safeEndpoint, credentials, transport, modelCapabilities)
            CloudAiApiProtocol.ANTHROPIC_MESSAGES ->
                AnthropicMessagesProvider(safeEndpoint, credentials, transport, modelCapabilities)
            CloudAiApiProtocol.COHERE_CHAT_V2 ->
                CohereChatV2Provider(safeEndpoint, credentials, transport, modelCapabilities)
        }
    }
}
