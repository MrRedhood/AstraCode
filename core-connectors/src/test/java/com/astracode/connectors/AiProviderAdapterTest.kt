package com.astracode.connectors

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AiProviderAdapterTest {
    @Test
    fun providerCatalogAndStreamingEndpointsAreCentralized() {
        assertEquals(
            "https://api.openai.com/v1/models",
            AiProviderAdapters.forProvider(AiProvider.OPENAI).modelCatalogUrl(),
        )
        assertEquals(
            "https://api.openai.com/v1/responses",
            AiProviderAdapters.forProvider(AiProvider.OPENAI).streamingUrl(),
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000",
            AiProviderAdapters.forProvider(AiProvider.GOOGLE_GEMINI).modelCatalogUrl(),
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/interactions",
            AiProviderAdapters.forProvider(AiProvider.GOOGLE_GEMINI).streamingUrl(),
        )
        assertEquals(
            "https://api.anthropic.com/v1/models?limit=1000&lifecycle=active",
            AiProviderAdapters.forProvider(AiProvider.ANTHROPIC).modelCatalogUrl(),
        )
        assertEquals(
            "https://api.anthropic.com/v1/messages",
            AiProviderAdapters.forProvider(AiProvider.ANTHROPIC).streamingUrl(),
        )
        assertEquals(
            "https://openrouter.ai/api/v1/models",
            AiProviderAdapters.forProvider(AiProvider.OPENROUTER).modelCatalogUrl(),
        )
        assertEquals(
            "https://openrouter.ai/api/v1/chat/completions",
            AiProviderAdapters.forProvider(AiProvider.OPENROUTER).streamingUrl(),
        )
    }

    @Test
    fun eachProviderUsesItsOwnAuthenticationHeaders() {
        assertEquals(
            mapOf("Authorization" to "Bearer test-key"),
            AiProviderAdapters.forProvider(AiProvider.OPENAI).authenticationHeaders("test-key"),
        )
        assertEquals(
            mapOf("x-goog-api-key" to "test-key"),
            AiProviderAdapters.forProvider(AiProvider.GOOGLE_GEMINI).authenticationHeaders("test-key"),
        )
        assertEquals(
            mapOf(
                "x-api-key" to "test-key",
                "anthropic-version" to "2026-10-21",
            ),
            AiProviderAdapters.forProvider(AiProvider.ANTHROPIC).authenticationHeaders("test-key"),
        )
        assertEquals(
            mapOf("Authorization" to "Bearer test-key"),
            AiProviderAdapters.forProvider(AiProvider.OPENROUTER).authenticationHeaders("test-key"),
        )
    }

    @Test
    fun customProviderBuildsModelAndCompletionUrlsFromHttpsBase() {
        val adapter = AiProviderAdapters.forProvider(AiProvider.OPENAI_COMPATIBLE)
        val base = "https://api.example.com/v1"
        assertEquals("$base/models", adapter.modelCatalogUrl(base))
        assertEquals("$base/chat/completions", adapter.streamingUrl(base))
        assertEquals(
            "Bearer test-key",
            adapter.authenticationHeaders("test-key").getValue("Authorization"),
        )
    }

    @Test
    fun customProviderAcceptsAlreadyCompletedEndpointSuffixes() {
        val adapter = AiProviderAdapters.forProvider(AiProvider.OPENAI_COMPATIBLE)
        assertEquals(
            "https://api.example.com/v1/models",
            adapter.modelCatalogUrl("https://api.example.com/v1/models"),
        )
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            adapter.streamingUrl("https://api.example.com/v1/chat/completions"),
        )
    }

    @Test
    fun customProviderRejectsInsecureOrCredentialBearingBaseUrls() {
        val adapter = AiProviderAdapters.forProvider(AiProvider.OPENAI_COMPATIBLE)
        assertFailsWith<IllegalArgumentException> {
            adapter.streamingUrl("http://api.example.com/v1")
        }
        assertFailsWith<IllegalArgumentException> {
            adapter.streamingUrl("https://user:password@api.example.com/v1")
        }
        assertFailsWith<IllegalArgumentException> {
            adapter.streamingUrl("https://api.example.com/v1?token=secret")
        }
        assertFailsWith<IllegalArgumentException> {
            adapter.streamingUrl("https://api.example.com/v1#fragment")
        }
        assertFailsWith<IllegalArgumentException> {
            adapter.streamingUrl("")
        }
    }

    @Test
    fun missingApiKeyIsRejectedBeforeRequestCreation() {
        assertFailsWith<IllegalArgumentException> {
            AiProviderAdapters.forProvider(AiProvider.OPENAI).authenticationHeaders(" ")
        }
    }
}
