package com.astracode.connectors

/**
 * Fallback is disabled by default because a fallback can send source code to a
 * different company. Every destination must be explicitly allow-listed.
 */
data class AiFallbackPolicy(
    val enabled: Boolean = false,
    val allowedProviderIds: List<String> = emptyList(),
) {
    fun candidates(primary: AiProvider): List<AiProvider> {
        if (!enabled) return emptyList()
        return allowedProviderIds
            .mapNotNull { id -> AiProvider.entries.firstOrNull { it.id == id } }
            .filter { it != primary }
            .distinct()
    }

    fun allows(provider: AiProvider, primary: AiProvider): Boolean =
        enabled && provider != primary && candidates(primary).contains(provider)

    companion object {
        fun fromStoredValues(enabled: Boolean, providerIds: Set<String>): AiFallbackPolicy =
            AiFallbackPolicy(
                enabled = enabled,
                allowedProviderIds = AiProvider.entries
                    .filter { providerIds.contains(it.id) }
                    .map { it.id },
            )
    }
}
