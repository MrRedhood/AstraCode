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
    fun explicitUserConfirmationCanPromoteUnknownCapability() {
        val model = AiModelCapabilities(
            provider = AiProvider.OPENAI,
            modelId = "manually-verified-model",
            displayName = "Manually verified model",
        )
        val confirmed = model.withUserConfirmedCapabilities(
            verifiedContextWindowTokens = 128_000,
            toolCallingConfirmed = true,
            streamingConfirmed = true,
        )
        assertEquals(AiCapabilitySupport.SUPPORTED, confirmed.supportsToolCalling)
        assertEquals(AiCapabilitySupport.SUPPORTED, confirmed.supportsStreaming)
        assertEquals(128_000L, confirmed.contextWindowTokens)
        assertEquals(AiCapabilitySource.USER_CONFIRMED, confirmed.source)
    }

    @Test
    fun noManualConfirmationLeavesUnknownCapabilitiesUnknown() {
        val model = AiModelCapabilities(
            provider = AiProvider.GOOGLE_GEMINI,
            modelId = "unverified",
            displayName = "Unverified",
        )
        val unchanged = model.withUserConfirmedCapabilities(null, false, false)
        assertEquals(AiCapabilitySupport.UNKNOWN, unchanged.supportsToolCalling)
        assertEquals(AiCapabilitySupport.UNKNOWN, unchanged.supportsStreaming)
        assertEquals(AiCapabilitySource.UNKNOWN, unchanged.source)
    }

    @Test
    fun manualContextCanBeAddedWithoutAssumingToolSupport() {
        val model = AiModelCapabilities(
            provider = AiProvider.OPENAI,
            modelId = "model-context-only",
            displayName = "Context-only assertion",
        ).withUserConfirmedCapabilities(64_000, false, false)
        assertEquals(64_000L, model.contextWindowTokens)
        assertEquals(AiCapabilitySupport.UNKNOWN, model.supportsToolCalling)
        assertEquals(AiCapabilitySupport.UNKNOWN, model.supportsStreaming)
        assertEquals(AiCapabilitySource.USER_CONFIRMED, model.source)
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
