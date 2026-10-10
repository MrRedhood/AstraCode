package com.mrredhood.astracode

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class AiDiscoveredModel(
    val id: String,
    val displayName: String,
    val contextWindowTokens: Int? = null
)

sealed class AiModelDiscoveryResult {
    data class Success(val models: List<AiDiscoveredModel>) : AiModelDiscoveryResult()
    data class Failure(val failure: AiProviderFailure) : AiModelDiscoveryResult()
}

/**
 * Uses each provider's documented model-list route. This is a metadata request only; it never sends
 * workspace content. Responses are bounded by the shared HTTP transport and model IDs are validated
 * before exposing them to UI state.
 */
class CloudAiModelDiscoveryService(
    private val credentials: AiCredentialSource,
    private val transport: AiHttpTransport = UrlConnectionAiHttpTransport()
) {
    suspend fun discover(
        providerId: CloudAiProviderId,
        baseUrlOverride: String? = null
    ): AiModelDiscoveryResult {
        val definition = CloudAiProviderCatalog.find(providerId)
            ?: return failure(AiProviderFailureCode.UNSUPPORTED_MODEL, "Unknown provider.")
        val endpoint = try {
            val cleaned = baseUrlOverride?.trim()?.takeIf { it.isNotEmpty() }
                ?: definition.defaultBaseUrl
                ?: return failure(AiProviderFailureCode.INVALID_REQUEST, "This provider requires a custom HTTPS endpoint.")
            JsonCloudAiProvider.validateBaseUrl(cleaned)
        } catch (error: IllegalArgumentException) {
            return failure(AiProviderFailureCode.INVALID_REQUEST, "The configured HTTPS endpoint is invalid.")
        }

        return try {
            currentCoroutineContext().ensureActive()
            val apiKey = credentials.apiKey(providerId)?.trim()
            if (apiKey.isNullOrEmpty()) {
                return failure(AiProviderFailureCode.MISSING_CREDENTIALS, "Add an API key for the selected provider.")
            }

            val (url, headers) = modelListRequest(providerId, definition.apiProtocol, endpoint, apiKey)
            val response = transport.execute(AiHttpRequest(url = url, headers = headers, method = "GET"))
            currentCoroutineContext().ensureActive()
            if (response.statusCode !in 200..299) return statusFailure(response.statusCode)
            val payload = JSONObject(response.body)
            val models = parseModels(definition.apiProtocol, payload)
                .distinctBy { it.id }
                .take(MAX_MODELS)
            AiModelDiscoveryResult.Success(models)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (timeout: SocketTimeoutException) {
            failure(AiProviderFailureCode.NETWORK, "The provider model list request timed out.")
        } catch (io: IOException) {
            failure(AiProviderFailureCode.NETWORK, "Unable to reach the provider. Check your connection and try again.")
        } catch (json: JSONException) {
            failure(AiProviderFailureCode.UNKNOWN, "The provider returned an unreadable model list.")
        } catch (invalid: IllegalArgumentException) {
            failure(AiProviderFailureCode.INVALID_REQUEST, "The provider endpoint or response is invalid.")
        } catch (unexpected: Exception) {
            failure(AiProviderFailureCode.UNKNOWN, "Model discovery failed unexpectedly.")
        }
    }

    private fun modelListRequest(
        providerId: CloudAiProviderId,
        protocol: CloudAiApiProtocol,
        base: String,
        apiKey: String
    ): Pair<String, Map<String, String>> = when (protocol) {
        CloudAiApiProtocol.OPENAI_CHAT_COMPLETIONS,
        CloudAiApiProtocol.OPENAI_RESPONSES ->
            JsonCloudAiProvider.joinUrl(base, "models") to mapOf("Authorization" to "Bearer $apiKey")

        CloudAiApiProtocol.GEMINI_GENERATE_CONTENT ->
            JsonCloudAiProvider.joinUrl(base, "models?pageSize=100") to mapOf("x-goog-api-key" to apiKey)

        CloudAiApiProtocol.ANTHROPIC_MESSAGES -> {
            val path = if (base.endsWith("/v1")) "models?limit=100" else "v1/models?limit=100"
            JsonCloudAiProvider.joinUrl(base, path) to mapOf(
                "x-api-key" to apiKey,
                "anthropic-version" to ANTHROPIC_VERSION
            )
        }

        CloudAiApiProtocol.COHERE_CHAT_V2 -> {
            val uri = URI(base)
            val root = "${uri.scheme}://${uri.rawAuthority}"
            "${root}/v1/models?endpoint=chat&page_size=100" to mapOf("Authorization" to "Bearer $apiKey")
        }
    }

    private fun parseModels(protocol: CloudAiApiProtocol, payload: JSONObject): List<AiDiscoveredModel> {
        val array = when (protocol) {
            CloudAiApiProtocol.OPENAI_CHAT_COMPLETIONS,
            CloudAiApiProtocol.OPENAI_RESPONSES,
            CloudAiApiProtocol.ANTHROPIC_MESSAGES -> payload.optJSONArray("data")
            CloudAiApiProtocol.GEMINI_GENERATE_CONTENT -> payload.optJSONArray("models")
            CloudAiApiProtocol.COHERE_CHAT_V2 -> payload.optJSONArray("models") ?: payload.optJSONArray("data")
        } ?: throw JSONException("Missing models array")

        val output = ArrayList<AiDiscoveredModel>(minOf(array.length(), MAX_MODELS))
        for (index in 0 until array.length().coerceAtMost(MAX_MODELS)) {
            val item = array.optJSONObject(index) ?: continue
            if (protocol == CloudAiApiProtocol.GEMINI_GENERATE_CONTENT) {
                val methods = item.optJSONArray("supportedGenerationMethods")
                if (methods != null && !methods.containsString("generateContent")) continue
            }
            if (protocol == CloudAiApiProtocol.COHERE_CHAT_V2) {
                val endpoints = item.optJSONArray("endpoints")
                if (endpoints != null && !endpoints.containsString("chat")) continue
            }

            val rawId = when (protocol) {
                CloudAiApiProtocol.GEMINI_GENERATE_CONTENT -> item.optString("name")
                CloudAiApiProtocol.COHERE_CHAT_V2 -> item.optString("name").ifBlank { item.optString("id") }
                else -> item.optString("id")
            }.takeIf { it.isNotBlank() } ?: continue
            val id = if (protocol == CloudAiApiProtocol.GEMINI_GENERATE_CONTENT) {
                rawId.removePrefix("models/")
            } else {
                rawId
            }
            if (!isValidModelId(id)) continue

            val displayName = when (protocol) {
                CloudAiApiProtocol.GEMINI_GENERATE_CONTENT -> item.optString("displayName")
                CloudAiApiProtocol.ANTHROPIC_MESSAGES -> item.optString("display_name")
                CloudAiApiProtocol.COHERE_CHAT_V2 -> item.optString("name")
                else -> item.optString("name")
            }.takeIf { it.isNotBlank() } ?: id
            val context = item.optIntOrNull("context_length")
                ?: item.optIntOrNull("context_window")
                ?: item.optIntOrNull("inputTokenLimit")
            output += AiDiscoveredModel(
                id = id,
                displayName = displayName.take(MAX_DISPLAY_NAME_CHARS),
                contextWindowTokens = context?.takeIf { it > 0 }
            )
        }
        return output
    }

    private fun isValidModelId(id: String): Boolean =
        id.isNotBlank() &&
            id.length <= AiModelTarget.MAX_MODEL_ID_LENGTH &&
            id.none { it.isWhitespace() || Character.isISOControl(it) }

    private fun statusFailure(status: Int): AiModelDiscoveryResult.Failure = when (status) {
        401, 403 -> failure(AiProviderFailureCode.AUTHENTICATION, "The provider rejected the API credential.")
        408, 425, 500, 502, 503, 504 -> failure(AiProviderFailureCode.UNAVAILABLE, "The provider is temporarily unavailable.")
        429 -> failure(AiProviderFailureCode.RATE_LIMITED, "The provider rate limit was reached.")
        400, 404, 413, 422 -> failure(AiProviderFailureCode.INVALID_REQUEST, "The provider rejected the model-list request.")
        else -> if (status >= 500) {
            failure(AiProviderFailureCode.UNAVAILABLE, "The provider is temporarily unavailable.")
        } else {
            failure(AiProviderFailureCode.UNKNOWN, "The provider returned HTTP $status.")
        }
    }

    private fun failure(
        code: AiProviderFailureCode,
        detail: String
    ) = AiModelDiscoveryResult.Failure(AiProviderFailure(code, code == AiProviderFailureCode.NETWORK, detail))

    companion object {
        const val MAX_MODELS = 200
        const val MAX_DISPLAY_NAME_CHARS = 160
        const val ANTHROPIC_VERSION = "2023-06-01"
    }
}

private fun JSONArray.containsString(expected: String): Boolean =
    (0 until length()).any { index -> optString(index) == expected }

private fun JSONObject.optIntOrNull(name: String): Int? =
    if (!has(name) || isNull(name)) null else optInt(name).takeIf { it > 0 }


/**
 * Selects a likely general-purpose text/chat model without asking users to know provider-specific IDs.
 * Provider model-list order remains the final fallback because providers often return recommended models first.
 */
object AiDefaultModelSelector {
    private val nonChatMarkers = listOf(
        "embed", "moderation", "whisper", "transcrib", "text-to-speech", "-tts",
        "rerank", "ranker", "dall-e", "stable-diffusion", "realtime", "audio",
        "speech", "video", "imagegen", "image-generation"
    )

    private val preferredMarkers: Map<CloudAiProviderId, List<String>> = mapOf(
        CloudAiProviderId.OPENROUTER to listOf("openrouter/auto", "flash", "mini", "claude", "gpt-", "deepseek-chat", "llama"),
        CloudAiProviderId.OPENAI to listOf("gpt-4o-mini", "gpt-4.1-mini", "gpt-5-mini", "gpt-", "o4-mini", "o3-mini"),
        CloudAiProviderId.OPENAI_COMPATIBLE to listOf("chat", "gpt-", "llama", "qwen", "deepseek"),
        CloudAiProviderId.GEMINI to listOf("flash", "gemini-"),
        CloudAiProviderId.ANTHROPIC to listOf("haiku", "sonnet", "claude-"),
        CloudAiProviderId.XAI to listOf("mini", "grok-"),
        CloudAiProviderId.DEEPSEEK to listOf("deepseek-chat", "deepseek-"),
        CloudAiProviderId.MISTRAL to listOf("small", "mistral-"),
        CloudAiProviderId.GROQ to listOf("llama", "deepseek", "qwen", "groq-"),
        CloudAiProviderId.TOGETHER_AI to listOf("llama", "qwen", "deepseek", "meta-llama"),
        CloudAiProviderId.FIREWORKS_AI to listOf("llama", "qwen", "deepseek"),
        CloudAiProviderId.PERPLEXITY to listOf("sonar"),
        CloudAiProviderId.CEREBRAS to listOf("llama", "qwen", "deepseek"),
        CloudAiProviderId.SAMBANOVA to listOf("deepseek", "llama", "qwen"),
        CloudAiProviderId.NVIDIA_NIM to listOf("llama", "nemotron", "qwen"),
        CloudAiProviderId.COHERE to listOf("command-r", "command")
    )

    fun select(providerId: CloudAiProviderId, models: List<AiDiscoveredModel>): AiDiscoveredModel? {
        val candidates = models.filter { model ->
            model.id.isNotBlank() &&
                nonChatMarkers.none { marker -> model.id.contains(marker, ignoreCase = true) } &&
                nonChatMarkers.none { marker -> model.displayName.contains(marker, ignoreCase = true) }
        }
        if (candidates.isEmpty()) return null
        for (marker in preferredMarkers[providerId].orEmpty()) {
            candidates.firstOrNull { it.id.contains(marker, ignoreCase = true) }?.let { return it }
        }
        return candidates.first()
    }
}
