package com.mrredhood.astracode

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudAiModelDiscoveryTest {
    private class Credentials(private val key: String? = "test-key") : AiCredentialSource {
        override suspend fun apiKey(providerId: CloudAiProviderId): String? = key
    }

    private class FakeTransport(private val response: AiHttpResponse) : AiHttpTransport {
        var request: AiHttpRequest? = null
        var calls = 0
        override suspend fun execute(request: AiHttpRequest): AiHttpResponse {
            this.request = request
            calls++
            return response
        }
    }

    @Test
    fun discoversOpenRouterModelsWithGetAndBearerAuth() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(200, """
            {"data":[
              {"id":"openai/gpt-test","name":"GPT Test","context_length":32768},
              {"id":"anthropic/claude-test","name":"Claude Test"},
              {"id":"bad model","name":"Invalid"}
            ]}
        """.trimIndent()))
        val service = CloudAiModelDiscoveryService(Credentials(), transport)
        val result = service.discover(CloudAiProviderId.OPENROUTER)

        assertTrue(result is AiModelDiscoveryResult.Success)
        val models = (result as AiModelDiscoveryResult.Success).models
        assertEquals(listOf("openai/gpt-test", "anthropic/claude-test"), models.map { it.id })
        assertEquals(32768, models.first().contextWindowTokens)
        assertEquals("GET", transport.request?.method)
        assertEquals("Bearer test-key", transport.request?.headers?.get("Authorization"))
        assertEquals("https://openrouter.ai/api/v1/models", transport.request?.url)
    }

    @Test
    fun geminiDiscoveryFiltersToGenerationModelsAndRemovesResourcePrefix() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(200, """
            {"models":[
              {"name":"models/gemini-2.5-flash","displayName":"Gemini Flash","supportedGenerationMethods":["generateContent"],"inputTokenLimit":100000},
              {"name":"models/text-embed","displayName":"Embedding","supportedGenerationMethods":["embedContent"]}
            ]}
        """.trimIndent()))
        val service = CloudAiModelDiscoveryService(Credentials(), transport)

        val result = service.discover(CloudAiProviderId.GEMINI)

        assertTrue(result is AiModelDiscoveryResult.Success)
        val models = (result as AiModelDiscoveryResult.Success).models
        assertEquals(1, models.size)
        assertEquals("gemini-2.5-flash", models.single().id)
        assertEquals("Gemini Flash", models.single().displayName)
        assertEquals(100000, models.single().contextWindowTokens)
        assertEquals("test-key", transport.request?.headers?.get("x-goog-api-key"))
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models?pageSize=100",
            transport.request?.url
        )
    }

    @Test
    fun anthropicDiscoveryUsesNativeHeadersAndModelNames() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(200, """
            {"data":[{"id":"claude-test","display_name":"Claude Test"}]}
        """.trimIndent()))
        val service = CloudAiModelDiscoveryService(Credentials(), transport)
        val result = service.discover(CloudAiProviderId.ANTHROPIC)

        assertTrue(result is AiModelDiscoveryResult.Success)
        assertEquals("Claude Test", (result as AiModelDiscoveryResult.Success).models.single().displayName)
        assertEquals("test-key", transport.request?.headers?.get("x-api-key"))
        assertEquals("2023-06-01", transport.request?.headers?.get("anthropic-version"))
        assertEquals("https://api.anthropic.com/v1/models?limit=100", transport.request?.url)
    }

    @Test
    fun cohereDiscoveryUsesItsModelListRoute() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(200, """
            {"models":[
              {"name":"command-r-test","endpoints":["chat"]},
              {"name":"embed-test","endpoints":["embed"]}
            ]}
        """.trimIndent()))
        val service = CloudAiModelDiscoveryService(Credentials(), transport)
        val result = service.discover(CloudAiProviderId.COHERE)

        assertTrue(result is AiModelDiscoveryResult.Success)
        assertEquals(listOf("command-r-test"), (result as AiModelDiscoveryResult.Success).models.map { it.id })
        assertEquals("https://api.cohere.com/v1/models?endpoint=chat&page_size=100", transport.request?.url)
    }

    @Test
    fun missingCredentialDoesNotCallTransport() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(200, """{"data":[]}"""))
        val result = CloudAiModelDiscoveryService(Credentials(null), transport)
            .discover(CloudAiProviderId.OPENAI)

        assertTrue(result is AiModelDiscoveryResult.Failure)
        assertEquals(
            AiProviderFailureCode.MISSING_CREDENTIALS,
            (result as AiModelDiscoveryResult.Failure).failure.code
        )
        assertEquals(0, transport.calls)
    }

    @Test
    fun authenticationFailureDoesNotExposeRawProviderBody() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(401, """{"error":"private diagnostic detail"}"""))
        val result = CloudAiModelDiscoveryService(Credentials(), transport)
            .discover(CloudAiProviderId.OPENAI)
        assertTrue(result is AiModelDiscoveryResult.Failure)
        val failure = (result as AiModelDiscoveryResult.Failure).failure
        assertEquals(AiProviderFailureCode.AUTHENTICATION, failure.code)
        assertFalse(failure.detail.contains("private diagnostic detail"))
    }
}
