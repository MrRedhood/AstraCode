package com.astracode.connectors

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiFallbackPolicyTest {
    @Test
    fun fallbackIsDisabledByDefault() {
        val policy = AiFallbackPolicy()
        assertTrue(policy.candidates(AiProvider.OPENAI).isEmpty())
        assertFalse(policy.allows(AiProvider.OPENROUTER, AiProvider.OPENAI))
    }

    @Test
    fun onlyExplicitlyAllowedProvidersAreCandidates() {
        val policy = AiFallbackPolicy(
            enabled = true,
            allowedProviderIds = listOf("anthropic", "openrouter", "not-a-provider", "openai"),
        )
        assertEquals(
            listOf(AiProvider.ANTHROPIC, AiProvider.OPENROUTER),
            policy.candidates(AiProvider.OPENAI),
        )
        assertTrue(policy.allows(AiProvider.ANTHROPIC, AiProvider.OPENAI))
        assertFalse(policy.allows(AiProvider.OPENAI, AiProvider.OPENAI))
        assertFalse(policy.allows(AiProvider.GOOGLE_GEMINI, AiProvider.OPENAI))
    }

    @Test
    fun duplicateAndPrimaryDestinationsAreRemoved() {
        val policy = AiFallbackPolicy(
            enabled = true,
            allowedProviderIds = listOf("openrouter", "openrouter", "openai"),
        )
        assertEquals(listOf(AiProvider.OPENROUTER), policy.candidates(AiProvider.OPENAI))
    }
}
