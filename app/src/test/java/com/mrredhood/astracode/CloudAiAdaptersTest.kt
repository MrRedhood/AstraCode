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
    fun openAiCompatibleSerializesImageAndPdfAsNativeContentParts() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(200, """
            {"model":"test-model","choices":[{"message":{"role":"assistant","content":"seen"}}]}
        """.trimIndent()))
        val image = AiChatAttachment(
            java.util.UUID.randomUUID().toString(), "image.png", "image/png", 3, data = byteArrayOf(1, 2, 3)
        )
        val pdf = AiChatAttachment(
            java.util.UUID.randomUUID().toString(), "report.pdf", "application/pdf", 2, data = byteArrayOf(4, 5)
        )
        val req = request.copy(messages = listOf(
            AiChatMessage(AiMessageRole.SYSTEM, "Follow the system rule."),
            AiChatMessage(AiMessageRole.USER, "Review these files.", attachments = listOf(image, pdf))
        ))
        val result = provider(CloudAiProviderId.OPENROUTER, transport = transport).generate("test-model", req)
        assertTrue(result is AiProviderResult.Success)
        val parts = JSONObject(requireNotNull(transport.captured).body)
            .getJSONArray("messages").getJSONObject(1).getJSONArray("content")
        assertEquals("text", parts.getJSONObject(0).getString("type"))
        assertEquals("image_url", parts.getJSONObject(1).getString("type"))
        assertTrue(parts.getJSONObject(1).getJSONObject("image_url").getString("url").endsWith("AQID"))
        assertEquals("file", parts.getJSONObject(2).getString("type"))
        assertEquals("data:application/pdf;base64,BAU=", parts.getJSONObject(2).getJSONObject("file").getString("file_data"))
    }

    @Test
    fun geminiSerializesImageAudioVideoAndPdfInlineData() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(200, """
            {"modelVersion":"test-model","candidates":[{"content":{"parts":[{"text":"ok"}]}}]}
        """.trimIndent()))
        fun file(name: String, mime: String, bytes: ByteArray) =
            AiChatAttachment(java.util.UUID.randomUUID().toString(), name, mime, bytes.size, data = bytes)
        val attachments = listOf(
            file("photo.png", "image/png", byteArrayOf(1, 2, 3)),
            file("voice.mp3", "audio/mpeg", byteArrayOf(4, 5)),
            file("clip.mp4", "video/mp4", byteArrayOf(6, 7)),
            file("report.pdf", "application/pdf", byteArrayOf(8, 9))
        )
        val req = request.copy(messages = listOf(
            AiChatMessage(AiMessageRole.USER, "Inspect these files.", attachments = attachments)
        ))
        val result = GeminiGenerateContentProvider(
            "https://generativelanguage.googleapis.com/v1beta",
            Credentials(), transport, capabilities
        ).generate("test-model", req)
        assertTrue(result is AiProviderResult.Success)
        val parts = JSONObject(requireNotNull(transport.captured).body)
            .getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        val mimes = (0 until parts.length()).mapNotNull { index ->
            parts.optJSONObject(index)?.optJSONObject("inlineData")?.optString("mimeType")
        }
        assertEquals(listOf("image/png", "audio/mpeg", "video/mp4", "application/pdf"), mimes)
        assertEquals("AQID", parts.getJSONObject(1).getJSONObject("inlineData").getString("data"))
        assertEquals("BAU=", parts.getJSONObject(2).getJSONObject("inlineData").getString("data"))
    }

    @Test
    fun unsupportedCohereAudioIsRejectedBeforeNetworkRequest() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(200, """
            {"message":{"content":[{"type":"text","text":"ok"}]}}
        """.trimIndent()))
        val audio = AiChatAttachment(
            java.util.UUID.randomUUID().toString(), "voice.mp3", "audio/mpeg", 3, data = byteArrayOf(1, 2, 3)
        )
        val req = request.copy(messages = listOf(
            AiChatMessage(AiMessageRole.USER, "Transcribe this audio.", attachments = listOf(audio))
        ))
        val result = CohereChatV2Provider("https://api.cohere.com/v2", Credentials(), transport, capabilities)
            .generate("test-model", req)
        assertTrue(result is AiProviderResult.Failure)
        assertEquals(AiProviderFailureCode.INVALID_REQUEST, (result as AiProviderResult.Failure).failure.code)
        assertTrue(result.failure.detail.contains("Gemini"))
        assertEquals(0, transport.calls)
    }

    @Test
    fun transportResponseBudgetCanCarryMaximumCreateProposalWithEscaping() {
        assertEquals(64 * 1024 * 1024, UrlConnectionAiHttpTransport.MAX_RESPONSE_BYTES)
        assertTrue(UrlConnectionAiHttpTransport.MAX_RESPONSE_BYTES > 4 * AiWorkspaceToolProtocol.MAX_CREATE_BYTES)
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
