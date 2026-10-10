package com.mrredhood.astracode

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Per-provider configuration contains no API secret. */
data class AiProviderConfiguration(
    val providerId: CloudAiProviderId,
    val modelId: String,
    val baseUrlOverride: String = ""
)

object AiProviderSettingsValidation {
    fun validate(configuration: AiProviderConfiguration) {
        val definition = requireNotNull(CloudAiProviderCatalog.find(configuration.providerId)) {
            "Unknown AI provider"
        }
        AiModelTarget(configuration.providerId, configuration.modelId)
        val endpoint = configuration.baseUrlOverride.trim()
        if (endpoint.isEmpty()) {
            require(definition.defaultBaseUrl != null) {
                "This provider requires a custom HTTPS endpoint"
            }
        } else {
            require(definition.supportsCustomBaseUrl) {
                "This provider does not permit a custom endpoint"
            }
            JsonCloudAiProvider.validateBaseUrl(endpoint)
        }
    }
}

class AiSecretStoreException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/**
 * Encrypts provider API keys with an AES-256-GCM key generated inside Android Keystore.
 * Only base64 IV/ciphertext is stored in app-private preferences; API keys are never stored as text.
 */
class AndroidKeystoreApiKeyVault(context: Context) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun hasKey(providerId: CloudAiProviderId): Boolean {
        val encrypted = preferences.contains(ciphertextName(providerId))
        val iv = preferences.contains(ivName(providerId))
        if (encrypted != iv) {
            throw AiSecretStoreException("Stored API key data is incomplete. Remove the key and save it again.")
        }
        return encrypted
    }

    fun save(providerId: CloudAiProviderId, apiKey: String) {
        val cleaned = apiKey.trim()
        require(cleaned.isNotEmpty()) { "API key cannot be blank" }
        require(cleaned.length <= MAX_API_KEY_LENGTH) { "API key is too long" }
        require(cleaned.none { it == '\r' || it == '\n' || Character.isISOControl(it) }) {
            "API key contains unsupported control characters"
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateKey())
            cipher.updateAAD(aad(providerId))
            val encrypted = cipher.doFinal(cleaned.toByteArray(StandardCharsets.UTF_8))
            val success = preferences.edit()
                .putString(ivName(providerId), Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString(ciphertextName(providerId), Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .commit()
            if (!success) throw AiSecretStoreException("Could not persist the encrypted API key.")
        } catch (error: AiSecretStoreException) {
            throw error
        } catch (error: Exception) {
            throw AiSecretStoreException("Could not encrypt the API key with Android Keystore.", error)
        }
    }

    fun read(providerId: CloudAiProviderId): String? {
        val encodedIv = preferences.getString(ivName(providerId), null)
        val encodedCiphertext = preferences.getString(ciphertextName(providerId), null)
        if (encodedIv == null && encodedCiphertext == null) return null
        if (encodedIv == null || encodedCiphertext == null) {
            throw AiSecretStoreException("Stored API key data is incomplete. Remove the key and save it again.")
        }
        try {
            val key = loadExistingKey()
                ?: throw AiSecretStoreException("The Android Keystore key is missing. Remove the saved API key and enter it again.")
            val iv = Base64.decode(encodedIv, Base64.NO_WRAP)
            val ciphertext = Base64.decode(encodedCiphertext, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(aad(providerId))
            return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        } catch (error: AiSecretStoreException) {
            throw error
        } catch (error: Exception) {
            throw AiSecretStoreException("Could not decrypt the saved API key. Remove it and enter it again.", error)
        }
    }

    fun delete(providerId: CloudAiProviderId) {
        val success = preferences.edit()
            .remove(ivName(providerId))
            .remove(ciphertextName(providerId))
            .commit()
        if (!success) throw AiSecretStoreException("Could not remove the saved API key.")
    }

    private fun loadExistingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    }

    private fun loadOrCreateKey(): SecretKey {
        loadExistingKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun aad(providerId: CloudAiProviderId): ByteArray =
        ("AstraCode:AI-key:v1:" + providerId.name).toByteArray(StandardCharsets.UTF_8)

    private fun ivName(providerId: CloudAiProviderId) = "${providerId.name}.iv"
    private fun ciphertextName(providerId: CloudAiProviderId) = "${providerId.name}.ciphertext"

    companion object {
        const val PREFERENCES_NAME = "astracode_ai_secrets"
        const val MAX_API_KEY_LENGTH = 4096
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "astracode.ai.api-key.aes-gcm.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val KEY_SIZE_BITS = 256
    }
}

/** Stores non-secret provider settings separately from credentials. */
class AiProviderSettingsRepository(context: Context) : AiCredentialSource {
    private val preferences = context.applicationContext.getSharedPreferences(
        SETTINGS_PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val vault = AndroidKeystoreApiKeyVault(context)

    fun selectedProviderId(): CloudAiProviderId =
        preferences.getString(KEY_SELECTED_PROVIDER, null)
            ?.let { name -> CloudAiProviderId.values().firstOrNull { it.name == name } }
            ?: CloudAiProviderId.OPENROUTER

    fun loadConfiguration(providerId: CloudAiProviderId): AiProviderConfiguration =
        AiProviderConfiguration(
            providerId = providerId,
            modelId = preferences.getString(modelKey(providerId), "").orEmpty(),
            baseUrlOverride = preferences.getString(endpointKey(providerId), "").orEmpty()
        )

    fun saveConfiguration(configuration: AiProviderConfiguration) {
        AiProviderSettingsValidation.validate(configuration)
        val success = preferences.edit()
            .putString(KEY_SELECTED_PROVIDER, configuration.providerId.name)
            .putString(modelKey(configuration.providerId), configuration.modelId.trim())
            .putString(endpointKey(configuration.providerId), configuration.baseUrlOverride.trim())
            .commit()
        if (!success) throw IllegalStateException("Could not persist AI provider settings.")
    }

    fun saveApiKey(providerId: CloudAiProviderId, apiKey: String) = vault.save(providerId, apiKey)
    fun hasApiKey(providerId: CloudAiProviderId): Boolean = vault.hasKey(providerId)
    fun removeApiKey(providerId: CloudAiProviderId) = vault.delete(providerId)

    override suspend fun apiKey(providerId: CloudAiProviderId): String? = vault.read(providerId)

    companion object {
        const val SETTINGS_PREFERENCES_NAME = "astracode_ai_settings"
        private const val KEY_SELECTED_PROVIDER = "selected_provider"
        private fun modelKey(id: CloudAiProviderId) = "${id.name}.model"
        private fun endpointKey(id: CloudAiProviderId) = "${id.name}.endpoint"
    }
}
