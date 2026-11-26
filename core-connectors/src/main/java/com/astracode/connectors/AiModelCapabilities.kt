package com.astracode.connectors

/**
 * Capability status is tri-state: lack of metadata is not treated as unsupported.
 */
enum class AiCapabilitySupport {
    SUPPORTED,
    UNSUPPORTED,
    UNKNOWN,
}

enum class AiCapabilitySource {
    PROVIDER_MODEL_METADATA,
    PROVIDER_API_CONTRACT,
    PROVIDER_METADATA_AND_API_CONTRACT,
    USER_CONFIRMED,
    UNKNOWN,
}

/**
 * Dynamic model prices are sourced from provider metadata where available.
 * Prices are expressed per one million billable tokens.
 */
data class AiModelPricing(
    val inputUsdPerMillionTokens: Double,
    val outputUsdPerMillionTokens: Double,
    val cachedInputUsdPerMillionTokens: Double? = null,
) {
    init {
        require(inputUsdPerMillionTokens.isFinite() && inputUsdPerMillionTokens >= 0.0)
        require(outputUsdPerMillionTokens.isFinite() && outputUsdPerMillionTokens >= 0.0)
        require(
            cachedInputUsdPerMillionTokens == null ||
                (cachedInputUsdPerMillionTokens.isFinite() && cachedInputUsdPerMillionTokens >= 0.0),
        )
    }

    /**
     * Returns null when cached tokens exist but the provider has not published
     * a cached-input rate. Applying the ordinary prompt rate would overstate
     * or understate the real amount depending on the provider.
     */
    fun estimateCostUsd(
        inputTokens: Long,
        outputTokens: Long,
        cachedInputTokens: Long = 0L,
    ): Double? {
        require(inputTokens >= 0 && outputTokens >= 0)
        require(cachedInputTokens in 0..inputTokens)
        if (cachedInputTokens > 0 && cachedInputUsdPerMillionTokens == null) return null
        val uncachedInputTokens = inputTokens - cachedInputTokens
        val uncachedCost = uncachedInputTokens.toDouble() * inputUsdPerMillionTokens
        val cachedCost = cachedInputTokens.toDouble() *
            (cachedInputUsdPerMillionTokens ?: 0.0)
        val outputCost = outputTokens.toDouble() * outputUsdPerMillionTokens
        return (uncachedCost + cachedCost + outputCost) / 1_000_000.0
    }
}

data class AiModelCapabilities(
    val provider: AiProvider,
    val modelId: String,
    val displayName: String,
    val contextWindowTokens: Long? = null,
    val maxOutputTokens: Long? = null,
    // Empty means provider metadata did not disclose the supported modalities.
    val inputModalities: Set<String> = emptySet(),
    val outputModalities: Set<String> = emptySet(),
    val supportsToolCalling: AiCapabilitySupport = AiCapabilitySupport.UNKNOWN,
    val supportsStreaming: AiCapabilitySupport = AiCapabilitySupport.UNKNOWN,
    val supportedParameters: Set<String> = emptySet(),
    val source: AiCapabilitySource = AiCapabilitySource.UNKNOWN,
    val pricing: AiModelPricing? = null,
) {
    fun canSatisfy(requirements: AiCapabilityRequirements): Boolean {
        if (requirements.requiresToolCalling &&
            supportsToolCalling != AiCapabilitySupport.SUPPORTED
        ) return false
        if (requirements.requiresStreaming &&
            supportsStreaming != AiCapabilitySupport.SUPPORTED
        ) return false
        if (requirements.minimumContextTokens != null &&
            (contextWindowTokens == null || contextWindowTokens < requirements.minimumContextTokens)
        ) return false
        if (!inputModalities.containsAll(requirements.requiredInputModalities)) return false
        if (!outputModalities.containsAll(requirements.requiredOutputModalities)) return false
        return true
    }

    fun withUserConfirmedCapabilities(
        verifiedContextWindowTokens: Long?,
        toolCallingConfirmed: Boolean,
        streamingConfirmed: Boolean,
    ): AiModelCapabilities {
        require(verifiedContextWindowTokens == null || verifiedContextWindowTokens > 0)
        val hasConfirmation = verifiedContextWindowTokens != null ||
            toolCallingConfirmed || streamingConfirmed
        if (!hasConfirmation) return this
        return copy(
            contextWindowTokens = verifiedContextWindowTokens ?: contextWindowTokens,
            supportsToolCalling = when {
                supportsToolCalling == AiCapabilitySupport.UNSUPPORTED -> AiCapabilitySupport.UNSUPPORTED
                toolCallingConfirmed -> AiCapabilitySupport.SUPPORTED
                else -> supportsToolCalling
            },
            supportsStreaming = when {
                supportsStreaming == AiCapabilitySupport.UNSUPPORTED -> AiCapabilitySupport.UNSUPPORTED
                streamingConfirmed -> AiCapabilitySupport.SUPPORTED
                else -> supportsStreaming
            },
            source = AiCapabilitySource.USER_CONFIRMED,
        )
    }

    fun capabilitySummary(): String {
        val context = contextWindowTokens?.let { it.toString() + " tokens context" } ?: "context unknown"
        val output = maxOutputTokens?.let { it.toString() + " max output" } ?: "output limit unknown"
        return listOf(
            context,
            output,
            "tools " + supportsToolCalling.name.lowercase(),
            "streaming " + supportsStreaming.name.lowercase(),
            "input " + inputModalities.sorted().joinToString(",").ifBlank { "unknown" },
            "output " + outputModalities.sorted().joinToString(",").ifBlank { "unknown" },
        ).joinToString(" · ")
    }
}

data class AiCapabilityRequirements(
    val requiresToolCalling: Boolean = false,
    val requiresStreaming: Boolean = true,
    val minimumContextTokens: Long? = null,
    val requiredInputModalities: Set<String> = emptySet(),
    val requiredOutputModalities: Set<String> = emptySet(),
)
