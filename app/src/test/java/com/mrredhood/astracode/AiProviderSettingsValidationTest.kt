package com.mrredhood.astracode

import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderSettingsValidationTest {
    @Test
    fun acceptsDefaultEndpointWhenProviderHasOne() {
        AiProviderSettingsValidation.validate(
            AiProviderConfiguration(CloudAiProviderId.OPENROUTER, "vendor/model")
        )
    }

    @Test
    fun requiresEndpointForCustomOpenAiCompatibleProvider() {
        assertTrue(runCatching {
            AiProviderSettingsValidation.validate(
                AiProviderConfiguration(CloudAiProviderId.OPENAI_COMPATIBLE, "my-cloud-model")
            )
        }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun rejectsHttpAndCredentialsInEndpointUrl() {
        assertTrue(runCatching {
            AiProviderSettingsValidation.validate(
                AiProviderConfiguration(CloudAiProviderId.OPENROUTER, "vendor/model", "http://example.com/v1")
            )
        }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching {
            AiProviderSettingsValidation.validate(
                AiProviderConfiguration(CloudAiProviderId.OPENROUTER, "vendor/model", "https://user:pass@example.com/v1")
            )
        }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun rejectsBlankOrMalformedModelIds() {
        listOf("", "   ", "model id", " model", "model ").forEach { model ->
            assertTrue(
                "Expected rejection for model: $model",
                runCatching {
                    AiProviderSettingsValidation.validate(
                        AiProviderConfiguration(CloudAiProviderId.OPENROUTER, model)
                    )
                }.exceptionOrNull() is IllegalArgumentException
            )
        }
    }

    @Test
    fun acceptsValidCustomEndpoint() {
        AiProviderSettingsValidation.validate(
            AiProviderConfiguration(
                CloudAiProviderId.OPENAI_COMPATIBLE,
                "example-model",
                "https://api.example.com/v1"
            )
        )
    }
}
