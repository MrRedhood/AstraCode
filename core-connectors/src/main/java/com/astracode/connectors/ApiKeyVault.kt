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

/** Encrypts a provider API key with a provider-specific AES-256 Android Keystore key. */
class ApiKeyVault(context: Context, providerId: String = "openai") {
    private val provider = providerId.lowercase().also {
        require(it.matches(Regex("[a-z0-9_-]{1,40}"))) { "Invalid provider identifier" }
    }
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val ivPreference = provider + "_iv"
    private val dataPreference = provider + "_ciphertext"
    private val alias = if (provider == "openai") {
        "com.astracode.openai.api-key.v1"
    } else {
        "com.astracode." + provider + ".api-key.v1"
    }

    fun save(apiKey: String) {
        val value = apiKey.trim()
        require(value.length in 20..4_096 && !value.contains('\n') && !value.contains('\r')) {
            "Enter a valid single-line API key"
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        check(preferences.edit()
            .putString(ivPreference, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(dataPreference, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .commit()) { "Could not save the encrypted API key" }
    }

    fun read(): String? {
        val iv = preferences.getString(ivPreference, null) ?: return null
        val encrypted = preferences.getString(dataPreference, null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        return String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), StandardCharsets.UTF_8)
    }

    fun clear() {
        check(preferences.edit().remove(ivPreference).remove(dataPreference).commit()) {
            "Could not remove the saved API key"
        }
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
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
        const val PREFERENCES = "astracode_encrypted_provider_keys"
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
