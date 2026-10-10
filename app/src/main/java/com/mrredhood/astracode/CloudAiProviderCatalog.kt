package com.mrredhood.astracode

/**
 * Wire protocol required by a provider adapter. Similar-looking providers are not assumed to
 * share request/response formats unless this catalog explicitly identifies that protocol.
 */
enum class CloudAiApiProtocol(val displayName: String) {
    OPENAI_RESPONSES("OpenAI Responses API"),
    OPENAI_CHAT_COMPLETIONS("OpenAI-compatible Chat Completions"),
    GEMINI_GENERATE_CONTENT("Gemini generateContent"),
    ANTHROPIC_MESSAGES("Anthropic Messages API"),
    COHERE_CHAT_V2("Cohere Chat v2")
}

/**
 * Static provider metadata only; it is not proof that an API adapter or credentials are configured.
 * Base URLs are roots expected by future adapters, which add protocol-specific paths themselves.
 */
data class CloudAiProviderDefinition(
    val id: CloudAiProviderId,
    val apiProtocol: CloudAiApiProtocol,
    val defaultBaseUrl: String?,
    val documentationUrl: String,
    val credentialLabel: String,
    val supportsCustomBaseUrl: Boolean,
    val description: String
) {
    init {
        require(documentationUrl.startsWith("https://")) {
            "Provider documentation must use HTTPS"
        }
        require(defaultBaseUrl == null || defaultBaseUrl.startsWith("https://")) {
            "Default provider endpoints must use HTTPS"
        }
        require(defaultBaseUrl != null || supportsCustomBaseUrl) {
            "A provider without a default endpoint must allow a custom base URL"
        }
        require(credentialLabel.isNotBlank()) { "Credential label must not be blank" }
        require(description.isNotBlank()) { "Provider description must not be blank" }
    }
}

/**
 * Cloud provider catalog for settings and future adapters. A catalog entry is not a connected
 * integration; live adapters, secret storage and connection tests are tracked separately.
 */
object CloudAiProviderCatalog {
    val all: List<CloudAiProviderDefinition> = listOf(
        CloudAiProviderDefinition(
            CloudAiProviderId.OPENROUTER,
            CloudAiApiProtocol.OPENAI_CHAT_COMPLETIONS,
            "https://openrouter.ai/api/v1",
            "https://openrouter.ai/docs",
            "OpenRouter API key",
            true,
            "Access models from multiple vendors through one hosted routing API."
        ),
        CloudAiProviderDefinition(
            CloudAiProviderId.OPENAI,
            CloudAiApiProtocol.OPENAI_RESPONSES,
            "https://api.openai.com/v1",
            "https://developers.openai.com/api/reference/overview",
            "OpenAI API key",
            true,
            "Direct access to OpenAI models using the Responses API."
        ),
        CloudAiProviderDefinition(
            CloudAiProviderId.OPENAI_COMPATIBLE,
            CloudAiApiProtocol.OPENAI_CHAT_COMPLETIONS,
            "https://api.openai.com/v1",
            "https://platform.openai.com/docs",
            "OpenAI-compatible API key",
            true,
            "OpenAI-compatible Chat Completions using the standard OpenAI HTTPS endpoint by default."
        ),
        CloudAiProviderDefinition(
            CloudAiProviderId.GEMINI,
            CloudAiApiProtocol.GEMINI_GENERATE_CONTENT,
            "https://generativelanguage.googleapis.com/v1beta",
            "https://ai.google.dev/gemini-api/docs",
            "Google AI API key",
            true,
            "Google Gemini API using its native generateContent protocol."
        ),
        CloudAiProviderDefinition(
            CloudAiProviderId.ANTHROPIC,
            CloudAiApiProtocol.ANTHROPIC_MESSAGES,
            "https://api.anthropic.com",
            "https://platform.claude.com/docs/en/api/overview",
            "Anthropic API key",
            true,
            "Direct Claude Messages API access."
        ),
        CloudAiProviderDefinition(
            CloudAiProviderId.XAI,
            CloudAiApiProtocol.OPENAI_CHAT_COMPLETIONS,
            "https://api.x.ai/v1",
            "https://docs.x.ai/developers/rest-api-reference/inference",
            "xAI API key",
            true,
            "xAI Grok models through its OpenAI-compatible inference API."
        ),
        CloudAiProviderDefinition(
            CloudAiProviderId.DEEPSEEK,
            CloudAiApiProtocol.OPENAI_CHAT_COMPLETIONS,
            "https://api.deepseek.com",
            "https://api-docs.deepseek.com/",
            "DeepSeek API key",
            true,
            "DeepSeek models through its OpenAI-compatible API."
        ),
        CloudAiProviderDefinition(
            CloudAiProviderId.MISTRAL,
            CloudAiApiProtocol.OPENAI_CHAT_COMPLETIONS,
            "https://api.mistral.ai/v1",
            "https://docs.mistral.ai/",
            "Mistral API key",
            true,
            "Mistral models through its OpenAI-compatible endpoint."
        ),
        CloudAiProviderDefinition(
            CloudAiProviderId.GROQ,
            CloudAiApiProtocol.OPENAI_CHAT_COMPLETIONS,
            "https://api.groq.com/openai/v1",
            "https://console.groq.com/docs/overview",
            "Groq API key",
            true,
            "Hosted low-latency inference through Groq's OpenAI-compatible API."
        ),
        CloudAiProviderDefinition(
            CloudAiProviderId.TOGETHER_AI,
            CloudAiApiProtocol.OPENAI_CHAT_COMPLETIONS,
            "https://api.together.xyz/v1",
            "https://docs.together.ai/",
            "Together AI API key",
            true,
            "Hosted open and third-party models through an OpenAI-compatible API."
        ),
        CloudAiProviderDefinition(
            CloudAiProviderId.FIREWORKS_AI,
            CloudAiApiProtocol.OPENAI_CHAT_COMPLETIONS,
            "https://api.fireworks.ai/inference/v1",
            "https://docs.fireworks.ai/",
            "Fireworks AI API key",
            true,
            "Hosted model inference through the Fireworks API."
        ),
        CloudAiProviderDefinition(
            CloudAiProviderId.PERPLEXITY,
            CloudAiApiProtocol.OPENAI_CHAT_COMPLETIONS,
            "https://api.perplexity.ai",
            "https://docs.perplexity.ai/",
            "Perplexity API key",
            true,
            "Perplexity's hosted answer and search-oriented models."
        ),
        CloudAiProviderDefinition(
            CloudAiProviderId.CEREBRAS,
            CloudAiApiProtocol.OPENAI_CHAT_COMPLETIONS,
            "https://api.cerebras.ai/v1",
            "https://inference-docs.cerebras.ai/",
            "Cerebras API key",
            true,
            "Hosted inference through Cerebras' OpenAI-compatible API."
        ),
        CloudAiProviderDefinition(
            CloudAiProviderId.SAMBANOVA,
            CloudAiApiProtocol.OPENAI_CHAT_COMPLETIONS,
            "https://api.sambanova.ai/v1",
            "https://docs.sambanova.ai/",
            "SambaNova API key",
            true,
            "Hosted inference through SambaNova's OpenAI-compatible API."
        ),
        CloudAiProviderDefinition(
            CloudAiProviderId.NVIDIA_NIM,
            CloudAiApiProtocol.OPENAI_CHAT_COMPLETIONS,
            "https://integrate.api.nvidia.com/v1",
            "https://build.nvidia.com/",
            "NVIDIA API key",
            true,
            "NVIDIA-hosted NIM endpoints using the OpenAI-compatible chat contract where supported."
        ),
        CloudAiProviderDefinition(
            CloudAiProviderId.COHERE,
            CloudAiApiProtocol.COHERE_CHAT_V2,
            "https://api.cohere.com/v2",
            "https://docs.cohere.com/reference/chat",
            "Cohere API key",
            true,
            "Cohere's native Chat v2 API."
        )
    )

    private val byId: Map<CloudAiProviderId, CloudAiProviderDefinition> = all.associateBy { it.id }

    init {
        require(byId.size == all.size) { "Provider catalog contains duplicate IDs" }
        require(byId.keys == CloudAiProviderId.values().toSet()) {
            "Every provider ID must have exactly one catalog definition"
        }
    }

    fun find(id: CloudAiProviderId): CloudAiProviderDefinition? = byId[id]
}
