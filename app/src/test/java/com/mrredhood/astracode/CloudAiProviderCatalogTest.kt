package com.mrredhood.astracode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudAiProviderCatalogTest {
    @Test
    fun everyProviderIdHasOneCatalogEntry() {
        assertEquals(CloudAiProviderId.values().toSet(), CloudAiProviderCatalog.all.map { it.id }.toSet())
        assertEquals(CloudAiProviderCatalog.all.size, CloudAiProviderCatalog.all.map { it.id }.distinct().size)
    }

    @Test
    fun catalogIncludesRequestedProvidersAndCommonAlternatives() {
        val expected = setOf(
            CloudAiProviderId.OPENROUTER,
            CloudAiProviderId.OPENAI,
            CloudAiProviderId.OPENAI_COMPATIBLE,
            CloudAiProviderId.GEMINI,
            CloudAiProviderId.ANTHROPIC,
            CloudAiProviderId.XAI,
            CloudAiProviderId.DEEPSEEK,
            CloudAiProviderId.MISTRAL,
            CloudAiProviderId.GROQ,
            CloudAiProviderId.TOGETHER_AI,
            CloudAiProviderId.FIREWORKS_AI,
            CloudAiProviderId.PERPLEXITY,
            CloudAiProviderId.CEREBRAS,
            CloudAiProviderId.SAMBANOVA,
            CloudAiProviderId.NVIDIA_NIM,
            CloudAiProviderId.COHERE
        )

        assertEquals(expected, CloudAiProviderCatalog.all.map { it.id }.toSet())
    }

    @Test
    fun openAiCompatibleProviderHasDefaultEndpointAndAllowsCustomOverride() {
        val custom = CloudAiProviderCatalog.find(CloudAiProviderId.OPENAI_COMPATIBLE)

        assertNotNull(custom)
        assertEquals("https://api.openai.com/v1", custom!!.defaultBaseUrl)
        assertTrue(custom.supportsCustomBaseUrl)
        assertEquals(CloudAiApiProtocol.OPENAI_CHAT_COMPLETIONS, custom.apiProtocol)
    }

    @Test
    fun catalogEndpointsAndDocumentationUseHttps() {
        CloudAiProviderCatalog.all.forEach { definition ->
            assertTrue(definition.documentationUrl.startsWith("https://"))
            assertTrue(
                "Unexpected non-HTTPS endpoint for ${definition.id}",
                definition.defaultBaseUrl == null || definition.defaultBaseUrl.startsWith("https://")
            )
            assertTrue(definition.credentialLabel.isNotBlank())
            assertTrue(definition.description.isNotBlank())
        }
    }

    @Test
    fun protocolSpecificProvidersAreNotMislabelledAsOpenAiCompatible() {
        assertEquals(
            CloudAiApiProtocol.GEMINI_GENERATE_CONTENT,
            CloudAiProviderCatalog.find(CloudAiProviderId.GEMINI)?.apiProtocol
        )
        assertEquals(
            CloudAiApiProtocol.ANTHROPIC_MESSAGES,
            CloudAiProviderCatalog.find(CloudAiProviderId.ANTHROPIC)?.apiProtocol
        )
        assertEquals(
            CloudAiApiProtocol.COHERE_CHAT_V2,
            CloudAiProviderCatalog.find(CloudAiProviderId.COHERE)?.apiProtocol
        )
    }
}
