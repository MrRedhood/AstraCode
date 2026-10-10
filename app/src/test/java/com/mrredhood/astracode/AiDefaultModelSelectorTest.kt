package com.mrredhood.astracode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiDefaultModelSelectorTest {
    @Test
    fun prefersProviderAppropriateGeneralChatModel() {
        val models = listOf(
            AiDiscoveredModel("text-embedding-large", "Text Embedding"),
            AiDiscoveredModel("gpt-4o-mini", "GPT-4o mini"),
            AiDiscoveredModel("gpt-realtime", "GPT Realtime")
        )
        assertEquals("gpt-4o-mini", AiDefaultModelSelector.select(CloudAiProviderId.OPENAI, models)?.id)
    }

    @Test
    fun usesFirstEligibleTextModelWhenNoPreferredNameMatches() {
        val models = listOf(
            AiDiscoveredModel("vendor-embed-v2", "Embedding"),
            AiDiscoveredModel("vendor-general-v2", "General text model")
        )
        assertEquals("vendor-general-v2", AiDefaultModelSelector.select(CloudAiProviderId.OPENROUTER, models)?.id)
    }

    @Test
    fun returnsNullWhenProviderListsNoChatCapableModel() {
        val models = listOf(
            AiDiscoveredModel("embed-v2", "Embedding"),
            AiDiscoveredModel("audio-transcription", "Audio transcription")
        )
        assertNull(AiDefaultModelSelector.select(CloudAiProviderId.GEMINI, models))
    }
}
