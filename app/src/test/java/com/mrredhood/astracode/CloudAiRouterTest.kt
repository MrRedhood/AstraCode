package com.mrredhood.astracode

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudAiRouterTest {
    private val request = AiGenerationRequest(
        messages = listOf(AiChatMessage(AiMessageRole.USER, "Explain this code"))
    )

    private class FakeProvider(
        override val id: CloudAiProviderId,
        private val modelCapabilities: Map<String, AiModelCapabilities>
    ) : CloudAiProvider {
        var generationCalls = 0

        override fun capabilities(modelId: String): AiModelCapabilities? =
            modelCapabilities[modelId]

        override suspend fun generate(
            modelId: String,
            request: AiGenerationRequest
        ): AiProviderResult {
            generationCalls++
            return AiProviderResult.Success(AiGenerationResponse("ok", modelId))
        }
    }

    private val capabilities = AiModelCapabilities(
        contextWindowTokens = 32_000,
        maxOutputTokens = 4_000,
        supportsVision = true
    )

    @Test
    fun resolvesOnlyTheExplicitProviderAndModel() {
        val gemini = FakeProvider(CloudAiProviderId.GEMINI, mapOf("gemini-test" to capabilities))
        val openAi = FakeProvider(
            CloudAiProviderId.OPENAI_COMPATIBLE,
            mapOf("openai-test" to capabilities)
        )
        val router = CloudAiRouter(listOf(gemini, openAi))
        val target = AiModelTarget(CloudAiProviderId.OPENAI_COMPATIBLE, "openai-test")

        val result = router.resolve(target, request)

        assertTrue(result is AiRouteResolution.Ready)
        assertSame(openAi, (result as AiRouteResolution.Ready).provider)
        assertEquals(target, result.target)
        assertEquals(0, gemini.generationCalls)
        assertEquals(0, openAi.generationCalls)
    }

    @Test
    fun doesNotFallBackWhenProviderIsMissing() {
        val router = CloudAiRouter(emptyList())

        val result = router.resolve(
            AiModelTarget(CloudAiProviderId.GEMINI, "gemini-test"),
            request
        )

        assertEquals(
            AiRouteResolution.Rejected(AiRouteRejection.PROVIDER_NOT_REGISTERED),
            result
        )
    }

    @Test
    fun doesNotFallBackWhenModelIsUnsupported() {
        val provider = FakeProvider(CloudAiProviderId.GEMINI, emptyMap())
        val router = CloudAiRouter(listOf(provider))

        val result = router.resolve(
            AiModelTarget(CloudAiProviderId.GEMINI, "unknown-model"),
            request
        )

        assertEquals(
            AiRouteResolution.Rejected(AiRouteRejection.MODEL_NOT_SUPPORTED),
            result
        )
        assertEquals(0, provider.generationCalls)
    }

    @Test
    fun rejectsOutputLimitAboveModelCapability() {
        val provider = FakeProvider(CloudAiProviderId.GEMINI, mapOf("gemini-test" to capabilities))
        val router = CloudAiRouter(listOf(provider))
        val oversizedRequest = request.copy(maxOutputTokens = 4_001)

        val result = router.resolve(
            AiModelTarget(CloudAiProviderId.GEMINI, "gemini-test"),
            oversizedRequest
        )

        assertEquals(
            AiRouteResolution.Rejected(AiRouteRejection.OUTPUT_LIMIT_EXCEEDED),
            result
        )
    }

    @Test
    fun rejectsDuplicateProviderIds() {
        val first = FakeProvider(CloudAiProviderId.GEMINI, emptyMap())
        val second = FakeProvider(CloudAiProviderId.GEMINI, emptyMap())

        val error = runCatching { CloudAiRouter(listOf(first, second)) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun validatesRequestsAndModelIds() {
        assertTrue(runCatching { AiGenerationRequest(emptyList()) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(
            runCatching {
                AiGenerationRequest(listOf(AiChatMessage(AiMessageRole.ASSISTANT, "No user")))
            }.exceptionOrNull() is IllegalArgumentException
        )
        assertTrue(
            runCatching {
                AiGenerationRequest(
                    listOf(AiChatMessage(AiMessageRole.USER, "hello")),
                    temperature = Double.NaN
                )
            }.exceptionOrNull() is IllegalArgumentException
        )
        assertTrue(
            runCatching {
                AiModelTarget(CloudAiProviderId.GEMINI, " model ")
            }.exceptionOrNull() is IllegalArgumentException
        )
    }

    @Test
    fun providerGenerationReturnsTypedOutcome() = runBlocking {
        val provider = FakeProvider(CloudAiProviderId.GEMINI, mapOf("gemini-test" to capabilities))

        val result = provider.generate("gemini-test", request)

        assertTrue(result is AiProviderResult.Success)
        assertEquals("ok", (result as AiProviderResult.Success).response.text)
        assertEquals(1, provider.generationCalls)
    }
}
