package com.mrredhood.astracode

import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderSettingsValidationTest {
    @Test
    fun acceptsApiKeyOnlyConfigurationForEveryCataloguedProvider() {
        CloudAiProviderId.values().forEach { provider ->
            AiProviderSettingsValidation.validate(AiProviderConfiguration(provider, ""))
        }
    }

    @Test
    fun acceptsDefaultEndpointWhenProviderHasOne() {
        AiProviderSettingsValidation.validate(AiProviderConfiguration(CloudAiProviderId.OPENROUTER, "vendor/model"))
    }

    @Test
    fun rejectsHttpAndCredentialsInEndpointUrl() {
        assertTrue(runCatching {
            AiProviderSettingsValidation.validate(AiProviderConfiguration(CloudAiProviderId.OPENROUTER, "vendor/model", "http://example.com/v1"))
        }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching {
            AiProviderSettingsValidation.validate(AiProviderConfiguration(CloudAiProviderId.OPENROUTER, "vendor/model", "https://user:pass@example.com/v1"))
        }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun rejectsMalformedNonBlankModelIds() {
        listOf("model id", " model", "model ").forEach { model ->
            assertTrue(runCatching {
                AiProviderSettingsValidation.validate(AiProviderConfiguration(CloudAiProviderId.OPENROUTER, model))
            }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun acceptsValidCustomEndpoint() {
        AiProviderSettingsValidation.validate(AiProviderConfiguration(
            CloudAiProviderId.OPENAI_COMPATIBLE, "example-model", "https://api.example.com/v1"
        ))
    }
}
