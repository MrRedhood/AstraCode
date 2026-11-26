package com.astracode.connectors

import java.net.URI

/**
 * Provider-specific endpoint and authentication information.
 *
 * Request-body formats remain in the gateway because each API has a different
 * conversation/tool protocol. This adapter keeps routing and credentials in
 * one place so adding a provider does not require changing the HTTP transport.
 */
internal data class AiProviderAdapter(
    val provider: AiProvider,
    private val modelCatalogEndpoint: String?,
    private val streamingEndpoint: String?,
    private val apiKeyHeader: String,
    private val apiKeyPrefix: String = "",
    private val extraHeaders: Map<String, String> = emptyMap(),
    private val customEndpoint: Boolean = false,
) {
    fun modelCatalogUrl(customBaseUrl: String = ""): String =
        customEndpointUrl(customBaseUrl, "models")
            ?: modelCatalogEndpoint
            ?: throw IllegalStateException(provider.displayName + " has no model catalogue endpoint")

    fun streamingUrl(customBaseUrl: String = ""): String =
        customEndpointUrl(customBaseUrl, "chat/completions")
            ?: streamingEndpoint
            ?: throw IllegalStateException(provider.displayName + " has no streaming endpoint")

    fun authenticationHeaders(apiKey: String): Map<String, String> {
        require(apiKey.isNotBlank()) { "An API key is required for " + provider.displayName }
        return buildMap {
            put(apiKeyHeader, apiKeyPrefix + apiKey)
            putAll(extraHeaders)
        }
    }

    private fun customEndpointUrl(baseUrl: String, suffix: String): String? {
        if (!customEndpoint) return null
        val base = validateCustomBaseUrl(baseUrl)
        return appendEndpoint(base, suffix)
    }

    companion object {
        private fun validateCustomBaseUrl(value: String): String {
            require(value.isNotBlank() && value.length <= MAX_CUSTOM_URL_CHARS) {
                "Enter the OpenAI-compatible HTTPS API base URL"
            }
            val uri = try {
                URI(value.trim())
            } catch (error: Exception) {
                throw IllegalArgumentException("The custom API base URL is invalid", error)
            }
            require(
                uri.scheme.equals("https", ignoreCase = true) &&
                    !uri.host.isNullOrBlank() &&
                    uri.userInfo == null &&
                    uri.query == null &&
                    uri.fragment == null,
            ) {
                "The custom API base URL must use HTTPS and cannot contain credentials, query, or fragment"
            }
            return value.trim().trimEnd('/')
        }

        private fun appendEndpoint(baseUrl: String, suffix: String): String {
            val base = baseUrl.trimEnd('/')
            return if (base.endsWith("/" + suffix)) base else "$base/$suffix"
        }

        private const val MAX_CUSTOM_URL_CHARS = 2_048
    }
}

internal object AiProviderAdapters {
    private val adapters = mapOf(
        AiProvider.OPENAI to AiProviderAdapter(
            provider = AiProvider.OPENAI,
            modelCatalogEndpoint = "https://api.openai.com/v1/models",
            streamingEndpoint = "https://api.openai.com/v1/responses",
            apiKeyHeader = "Authorization",
            apiKeyPrefix = "Bearer ",
        ),
        AiProvider.GOOGLE_GEMINI to AiProviderAdapter(
            provider = AiProvider.GOOGLE_GEMINI,
            modelCatalogEndpoint = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000",
            streamingEndpoint = "https://generativelanguage.googleapis.com/v1beta/interactions",
            apiKeyHeader = "x-goog-api-key",
        ),
        AiProvider.ANTHROPIC to AiProviderAdapter(
            provider = AiProvider.ANTHROPIC,
            modelCatalogEndpoint = "https://api.anthropic.com/v1/models?limit=1000&lifecycle=active",
            streamingEndpoint = "https://api.anthropic.com/v1/messages",
            apiKeyHeader = "x-api-key",
            extraHeaders = mapOf("anthropic-version" to ANTHROPIC_API_VERSION),
        ),
        AiProvider.OPENROUTER to AiProviderAdapter(
            provider = AiProvider.OPENROUTER,
            modelCatalogEndpoint = "https://openrouter.ai/api/v1/models",
            streamingEndpoint = "https://openrouter.ai/api/v1/chat/completions",
            apiKeyHeader = "Authorization",
            apiKeyPrefix = "Bearer ",
        ),
        AiProvider.OPENAI_COMPATIBLE to AiProviderAdapter(
            provider = AiProvider.OPENAI_COMPATIBLE,
            modelCatalogEndpoint = null,
            streamingEndpoint = null,
            apiKeyHeader = "Authorization",
            apiKeyPrefix = "Bearer ",
            customEndpoint = true,
        ),
    )

    fun forProvider(provider: AiProvider): AiProviderAdapter =
        adapters[provider] ?: throw IllegalArgumentException(
            "No provider adapter is registered for " + provider.displayName,
        )

    private const val ANTHROPIC_API_VERSION = "2026-10-21"
}
