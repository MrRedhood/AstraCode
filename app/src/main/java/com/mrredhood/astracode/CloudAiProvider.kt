package com.mrredhood.astracode

/**
 * Cloud services supported by the provider boundary. Local model execution is intentionally absent.
 */
enum class CloudAiProviderId(val displayName: String) {
    GEMINI("Gemini"),
    OPENAI_COMPATIBLE("OpenAI-compatible"),
    ANTHROPIC("Anthropic")
}

/** An explicit provider/model pair. Routing never infers a different provider from a model name. */
data class AiModelTarget(
    val providerId: CloudAiProviderId,
    val modelId: String
) {
    init {
        require(modelId.isNotBlank()) { "Model ID must not be blank" }
        require(modelId == modelId.trim()) { "Model ID must not begin or end with whitespace" }
        require(modelId.length <= MAX_MODEL_ID_LENGTH) { "Model ID is too long" }
        require(modelId.none { it.isWhitespace() || Character.isISOControl(it) }) {
            "Model ID must not contain whitespace or control characters"
        }
    }

    companion object {
        const val MAX_MODEL_ID_LENGTH = 128
    }
}

enum class AiMessageRole { SYSTEM, USER, ASSISTANT }

data class AiChatMessage(
    val role: AiMessageRole,
    val content: String
) {
    init {
        require(content.isNotBlank()) { "Message content must not be blank" }
    }
}

/**
 * Provider-neutral input. The character limit bounds accidental giant prompts on mobile;
 * adapters remain responsible for provider-specific token/context limits.
 */
data class AiGenerationRequest(
    val messages: List<AiChatMessage>,
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null
) {
    init {
        require(messages.isNotEmpty()) { "At least one message is required" }
        require(messages.size <= MAX_MESSAGES) { "Too many messages" }
        require(messages.any { it.role == AiMessageRole.USER }) {
            "At least one user message is required"
        }
        require(messages.sumOf { it.content.length.toLong() } <= MAX_TOTAL_CHARACTERS) {
            "Prompt exceeds the mobile-safe character limit"
        }
        require(temperature == null || (temperature.isFinite() && temperature in 0.0..2.0)) {
            "Temperature must be between 0 and 2"
        }
        require(maxOutputTokens == null || maxOutputTokens in 1..MAX_OUTPUT_TOKENS) {
            "Output token limit is outside the supported range"
        }
    }

    companion object {
        const val MAX_MESSAGES = 100
        const val MAX_TOTAL_CHARACTERS = 2L * 1024L * 1024L
        const val MAX_OUTPUT_TOKENS = 1_000_000
    }
}

data class AiModelCapabilities(
    val contextWindowTokens: Int,
    val maxOutputTokens: Int,
    val supportsVision: Boolean = false,
    val supportsStreaming: Boolean = false
) {
    init {
        require(contextWindowTokens > 0) { "Context window must be positive" }
        require(maxOutputTokens > 0) { "Output limit must be positive" }
        require(maxOutputTokens <= contextWindowTokens) {
            "Output limit cannot exceed the context window"
        }
    }
}

data class AiTokenUsage(
    val inputTokens: Int? = null,
    val outputTokens: Int? = null
) {
    init {
        require(inputTokens == null || inputTokens >= 0) { "Input token count cannot be negative" }
        require(outputTokens == null || outputTokens >= 0) { "Output token count cannot be negative" }
    }
}

data class AiGenerationResponse(
    val text: String,
    val modelId: String,
    val usage: AiTokenUsage? = null
) {
    init {
        require(modelId.isNotBlank()) { "Response model ID must not be blank" }
    }
}

enum class AiProviderFailureCode {
    NETWORK,
    AUTHENTICATION,
    RATE_LIMITED,
    INVALID_REQUEST,
    UNAVAILABLE,
    CANCELLED,
    UNSUPPORTED_MODEL,
    UNKNOWN
}

/**
 * The detail field must contain sanitized, user-safe text only. Provider adapters must not copy
 * raw response bodies, request headers, credentials, or full prompts into failures or logs.
 */
data class AiProviderFailure(
    val code: AiProviderFailureCode,
    val retryable: Boolean,
    val detail: String
)

sealed class AiProviderResult {
    data class Success(val response: AiGenerationResponse) : AiProviderResult()
    data class Failure(val failure: AiProviderFailure) : AiProviderResult()
}

/**
 * Implementations call cloud services only. Credential lookup and HTTP details belong to the
 * adapter boundary, not the request model or UI. Implementations must propagate coroutine
 * cancellation and return sanitized failures.
 */
interface CloudAiProvider {
    val id: CloudAiProviderId

    /** Return null when this provider does not currently support the requested model. */
    fun capabilities(modelId: String): AiModelCapabilities?

    suspend fun generate(modelId: String, request: AiGenerationRequest): AiProviderResult
}

enum class AiRouteRejection {
    PROVIDER_NOT_REGISTERED,
    MODEL_NOT_SUPPORTED,
    OUTPUT_LIMIT_EXCEEDED
}

sealed class AiRouteResolution {
    data class Ready(
        val target: AiModelTarget,
        val provider: CloudAiProvider,
        val capabilities: AiModelCapabilities
    ) : AiRouteResolution()

    data class Rejected(val reason: AiRouteRejection) : AiRouteResolution()
}

/**
 * Resolves an explicit target without fallback. A missing provider/model is surfaced instead of
 * sending workspace content to another vendor or model implicitly.
 */
class CloudAiRouter(providers: Collection<CloudAiProvider>) {
    private val providersById: Map<CloudAiProviderId, CloudAiProvider>

    init {
        val indexed = providers.associateBy { it.id }
        require(indexed.size == providers.size) { "Provider IDs must be unique" }
        providersById = indexed
    }

    fun resolve(target: AiModelTarget, request: AiGenerationRequest): AiRouteResolution {
        val provider = providersById[target.providerId]
            ?: return AiRouteResolution.Rejected(AiRouteRejection.PROVIDER_NOT_REGISTERED)
        val capabilities = provider.capabilities(target.modelId)
            ?: return AiRouteResolution.Rejected(AiRouteRejection.MODEL_NOT_SUPPORTED)
        val requestedLimit = request.maxOutputTokens
        if (requestedLimit != null && requestedLimit > capabilities.maxOutputTokens) {
            return AiRouteResolution.Rejected(AiRouteRejection.OUTPUT_LIMIT_EXCEEDED)
        }
        return AiRouteResolution.Ready(target, provider, capabilities)
    }
}
