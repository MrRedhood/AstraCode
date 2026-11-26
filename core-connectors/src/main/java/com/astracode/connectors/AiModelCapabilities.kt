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
    UNKNOWN,
}

data class AiModelCapabilities(
    val provider: AiProvider,
    val modelId: String,
    val displayName: String,
    val contextWindowTokens: Long? = null,
    val maxOutputTokens: Long? = null,
    val inputModalities: Set<String> = setOf("text"),
    val outputModalities: Set<String> = setOf("text"),
    val supportsToolCalling: AiCapabilitySupport = AiCapabilitySupport.UNKNOWN,
    val supportsStreaming: AiCapabilitySupport = AiCapabilitySupport.UNKNOWN,
    val supportedParameters: Set<String> = emptySet(),
    val source: AiCapabilitySource = AiCapabilitySource.UNKNOWN,
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

    fun capabilitySummary(): String {
        val context = contextWindowTokens?.let { it.toString() + " tokens context" } ?: "context unknown"
        val output = maxOutputTokens?.let { it.toString() + " max output" } ?: "output limit unknown"
        return listOf(
            context,
            output,
            "tools " + supportsToolCalling.name.lowercase(),
            "streaming " + supportsStreaming.name.lowercase(),
            "input " + inputModalities.sorted().joinToString(","),
            "output " + outputModalities.sorted().joinToString(","),
        ).joinToString(" · ")
    }
}

data class AiCapabilityRequirements(
    val requiresToolCalling: Boolean = false,
    val requiresStreaming: Boolean = true,
    val minimumContextTokens: Long? = null,
    val requiredInputModalities: Set<String> = setOf("text"),
    val requiredOutputModalities: Set<String> = setOf("text"),
)
