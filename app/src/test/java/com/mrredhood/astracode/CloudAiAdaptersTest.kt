package com.mrredhood.astracode

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudAiAdaptersTest {
    private val capabilities = mapOf(
        "test-model" to AiModelCapabilities(contextWindowTokens = 16_000, maxOutputTokens = 2_000, supportsVision = true, supportsStreaming = true)
    )
    private val request = AiGenerationRequest(
        messages = listOf(
            AiChatMessage(AiMessageRole.SYSTEM, "Follow the system rule."),
            AiChatMessage(AiMessageRole.USER, "Explain this code.")
        ),
        temperature = 0.2,
        maxOutputTokens = 500
    )

    private class Credentials(private val value: String? = "unit-test-key") : AiCredentialSource {
        override suspend fun apiKey(providerId: CloudAiProviderId): String? = value
    }

    private class FakeTransport(private var next: AiHttpResponse) : AiHttpTransport {
        var captured: AiHttpRequest? = null
        var calls: Int = 0
        override suspend fun execute(request: AiHttpRequest): AiHttpResponse {
            captured = request
            calls++
            return next
        }
        fun respond(status: Int, body: String) { next = AiHttpResponse(status, body) }
    }

    private fun provider(id: CloudAiProviderId, baseUrl: String = "https://example.test/v1", transport: FakeTransport) =
        OpenAiCompatibleChatProvider(id, baseUrl, Credentials(), transport, capabilities)

    @Test
    fun openAiCompatibleSerializesRequestAndParsesTextAndUsage() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(200, """
            {"model":"test-model","choices":[{"message":{"role":"assistant","content":"Hello world"}}],
             "usage":{"prompt_tokens":12,"completion_tokens":7}}
        """.trimIndent()))
        val result = provider(CloudAiProviderId.OPENROUTER, transport = transport).generate("test-model", request)

        assertTrue(result is AiProviderResult.Success)
        val success = (result as AiProviderResult.Success).response
        assertEquals("Hello world", success.text)
        assertEquals(AiTokenUsage(12, 7), success.usage)
        val captured = requireNotNull(transport.captured)
        assertEquals("https://example.test/v1/chat/completions", captured.url)
        assertEquals("Bearer unit-test-key", captured.headers["Authorization"])
        val body = JSONObject(captured.body)
        assertEquals("test-model", body.getString("model"))
        assertEquals(500, body.getInt("max_tokens"))
        assertEquals("system", body.getJSONArray("messages").getJSONObject(0).getString("role"))
        assertFalse(body.getBoolean("stream"))
    }

    @Test
    fun openAiCompatibleParsesTextContentBlocksWhenProviderUsesArray() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(200, """
            {"choices":[{"message":{"content":[{"type":"text","text":"one "},{"type":"text","text":"two"}]}}]}
        """.trimIndent()))
        val result = provider(CloudAiProviderId.XAI, transport = transport).generate("test-model", request)
        assertEquals("one two", (result as AiProviderResult.Success).response.text)
    }

    @Test
    fun openAiResponsesUsesNativeEndpointAndDisablesServerStorage() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(200, """
            {"model":"test-model","output":[{"type":"message","content":[{"type":"output_text","text":"Native response"}]}],
             "usage":{"input_tokens":8,"output_tokens":4}}
        """.trimIndent()))
        val adapter = OpenAiResponsesProvider("https://api.openai.com/v1", Credentials(), transport, capabilities)
        val result = adapter.generate("test-model", request)
        assertEquals("Native response", (result as AiProviderResult.Success).response.text)
        val captured = requireNotNull(transport.captured)
        assertEquals("https://api.openai.com/v1/responses", captured.url)
        val body = JSONObject(captured.body)
        assertFalse(body.getBoolean("store"))
        assertEquals("Follow the system rule.", body.getString("instructions"))
        assertEquals(500, body.getInt("max_output_tokens"))
    }

    @Test
    fun geminiUsesNativePayloadAndHeaderAuthentication() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(200, """
            {"modelVersion":"test-model","candidates":[{"content":{"parts":[{"text":"Gemini "},{"text":"answer"}]}}],
             "usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":5}}
        """.trimIndent()))
        val adapter = GeminiGenerateContentProvider("https://generativelanguage.googleapis.com/v1beta", Credentials(), transport, capabilities)
        val result = adapter.generate("test-model", request)
        val success = (result as AiProviderResult.Success).response
        assertEquals("Gemini answer", success.text)
        assertEquals(AiTokenUsage(10, 5), success.usage)
        val captured = requireNotNull(transport.captured)
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/test-model:generateContent", captured.url)
        assertEquals("unit-test-key", captured.headers["x-goog-api-key"])
        assertNull(captured.headers["Authorization"])
        val body = JSONObject(captured.body)
        assertTrue(body.has("systemInstruction"))
        assertEquals("user", body.getJSONArray("contents").optJSONObject(0).getString("role"))
    }

    @Test
    fun anthropicUsesNativeMessagesHeadersAndSystemField() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(200, """
            {"model":"test-model","content":[{"type":"text","text":"Claude reply"}],
             "usage":{"input_tokens":9,"output_tokens":6}}
        """.trimIndent()))
        val adapter = AnthropicMessagesProvider("https://api.anthropic.com", Credentials(), transport, capabilities)
        val result = adapter.generate("test-model", request)
        val success = (result as AiProviderResult.Success).response
        assertEquals("Claude reply", success.text)
        assertEquals(AiTokenUsage(9, 6), success.usage)
        val captured = requireNotNull(transport.captured)
        assertEquals("https://api.anthropic.com/v1/messages", captured.url)
        assertEquals("unit-test-key", captured.headers["x-api-key"])
        assertEquals("2023-06-01", captured.headers["anthropic-version"])
        val body = JSONObject(captured.body)
        assertEquals("Follow the system rule.", body.getString("system"))
        assertEquals(500, body.getInt("max_tokens"))
        assertEquals("user", body.getJSONArray("messages").getJSONObject(0).getString("role"))
    }

    @Test
    fun cohereUsesNativeV2EndpointAndBearerAuth() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(200, """
            {"model":"test-model","message":{"content":[{"type":"text","text":"Cohere response"}]},
             "usage":{"tokens":{"input_tokens":11,"output_tokens":5}}}
        """.trimIndent()))
        val adapter = CohereChatV2Provider("https://api.cohere.com/v2", Credentials(), transport, capabilities)
        val result = adapter.generate("test-model", request)
        val success = (result as AiProviderResult.Success).response
        assertEquals("Cohere response", success.text)
        assertEquals(AiTokenUsage(11, 5), success.usage)
        val captured = requireNotNull(transport.captured)
        assertEquals("https://api.cohere.com/v2/chat", captured.url)
        assertEquals("Bearer unit-test-key", captured.headers["Authorization"])
    }

    @Test
    fun missingCredentialFailsWithoutSendingNetworkRequest() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(200, """{"choices":[]}"""))
        val adapter = OpenAiCompatibleChatProvider(
            CloudAiProviderId.OPENAI_COMPATIBLE, "https://example.test/v1", Credentials(null), transport, capabilities
        )
        val result = adapter.generate("test-model", request)
        assertTrue(result is AiProviderResult.Failure)
        assertEquals(AiProviderFailureCode.MISSING_CREDENTIALS, (result as AiProviderResult.Failure).failure.code)
        assertEquals(0, transport.calls)
    }

    @Test
    fun httpFailureIsMappedWithoutExposingRawResponseBody() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(401, """{"error":"secret internal server detail"}"""))
        val result = provider(CloudAiProviderId.OPENROUTER, transport = transport).generate("test-model", request)
        val failure = (result as AiProviderResult.Failure).failure
        assertEquals(AiProviderFailureCode.AUTHENTICATION, failure.code)
        assertFalse(failure.detail.contains("secret internal server detail"))
    }

    @Test
    fun oversizedOutputRequestRejectedBeforeTransport() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(200, """{}"""))
        val adapter = OpenAiCompatibleChatProvider(
            CloudAiProviderId.OPENROUTER, "https://example.test/v1", Credentials(), transport, capabilities
        )
        val result = adapter.generate("test-model", request.copy(maxOutputTokens = 2_001))
        assertEquals(AiProviderFailureCode.INVALID_REQUEST, (result as AiProviderResult.Failure).failure.code)
        assertEquals(0, transport.calls)
    }

    @Test
    fun factoryCreatesSeparateNativeProtocolAdaptersAndRejectsHttpEndpoints() {
        val factory = CloudAiProviderFactory(Credentials(), FakeTransport(AiHttpResponse(200, "{}")))
        assertTrue(factory.create(CloudAiProviderId.OPENROUTER, modelCapabilities = capabilities) is OpenAiCompatibleChatProvider)
        assertTrue(factory.create(CloudAiProviderId.OPENAI, modelCapabilities = capabilities) is OpenAiResponsesProvider)
        assertTrue(factory.create(CloudAiProviderId.GEMINI, modelCapabilities = capabilities) is GeminiGenerateContentProvider)
        assertTrue(factory.create(CloudAiProviderId.ANTHROPIC, modelCapabilities = capabilities) is AnthropicMessagesProvider)
        assertTrue(factory.create(CloudAiProviderId.COHERE, modelCapabilities = capabilities) is CohereChatV2Provider)
        assertTrue(runCatching {
            factory.create(CloudAiProviderId.OPENAI_COMPATIBLE, "http://insecure.example/v1", capabilities)
        }.exceptionOrNull() is IllegalArgumentException)
    }
}
