package com.astracode.connectors

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiModelCapabilitiesTest {
    @Test
    fun unknownToolSupportDoesNotQualifyForToolRequiredFallback() {
        val model = AiModelCapabilities(
            provider = AiProvider.OPENAI,
            modelId = "model-a",
            displayName = "Model A",
            contextWindowTokens = 128_000,
            supportsStreaming = AiCapabilitySupport.SUPPORTED,
            supportsToolCalling = AiCapabilitySupport.UNKNOWN,
        )
        assertFalse(model.canSatisfy(AiCapabilityRequirements(requiresToolCalling = true)))
    }

    @Test
    fun verifiedCapabilitiesSatisfyMatchingRequirements() {
        val model = AiModelCapabilities(
            provider = AiProvider.OPENROUTER,
            modelId = "model-b",
            displayName = "Model B",
            contextWindowTokens = 200_000,
            maxOutputTokens = 16_000,
            inputModalities = setOf("text", "image"),
            outputModalities = setOf("text"),
            supportsToolCalling = AiCapabilitySupport.SUPPORTED,
            supportsStreaming = AiCapabilitySupport.SUPPORTED,
            source = AiCapabilitySource.PROVIDER_MODEL_METADATA,
        )
        assertTrue(
            model.canSatisfy(
                AiCapabilityRequirements(
                    requiresToolCalling = true,
                    requiresStreaming = true,
                    minimumContextTokens = 100_000,
                    requiredInputModalities = setOf("text", "image"),
                ),
            ),
        )
    }

    @Test
    fun missingContextAndModalitiesDoNotPassRequirements() {
        val model = AiModelCapabilities(
            provider = AiProvider.ANTHROPIC,
            modelId = "model-c",
            displayName = "Model C",
            supportsStreaming = AiCapabilitySupport.SUPPORTED,
            supportsToolCalling = AiCapabilitySupport.SUPPORTED,
        )
        assertFalse(
            model.canSatisfy(
                AiCapabilityRequirements(
                    requiresToolCalling = true,
                    minimumContextTokens = 50_000,
                    requiredInputModalities = setOf("text", "image"),
                ),
            ),
        )
    }
}
