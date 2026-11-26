package com.astracode.connectors

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the user's provider key with an AES-256 key held by Android Keystore.
 * The plaintext key is never written to app preferences or logs.
 */
class ApiKeyVault(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun save(apiKey: String) {
        val normalized = apiKey.trim()
        require(normalized.length in 20..4_096) { "Enter a valid API key" }
        require(!normalized.contains('\n') && !normalized.contains('\r')) {
            "The API key must be a single line"
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(normalized.toByteArray(StandardCharsets.UTF_8))
        val committed = preferences.edit()
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .commit()
        check(committed) { "Could not save the encrypted API key" }
    }

    fun read(): String? {
        val ivValue = preferences.getString(KEY_IV, null) ?: return null
        val ciphertextValue = preferences.getString(KEY_CIPHERTEXT, null) ?: return null

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_BITS, Base64.decode(ivValue, Base64.NO_WRAP)),
        )
        val plaintext = cipher.doFinal(Base64.decode(ciphertextValue, Base64.NO_WRAP))
        return String(plaintext, StandardCharsets.UTF_8)
    }

    fun clear() {
        preferences.edit().remove(KEY_IV).remove(KEY_CIPHERTEXT).commit()
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "astracode_encrypted_provider_keys"
        const val KEY_IV = "openai_iv"
        const val KEY_CIPHERTEXT = "openai_ciphertext"
        const val KEY_ALIAS = "com.astracode.openai.api-key.v1"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
