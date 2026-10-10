package com.mrredhood.astracode

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreApiKeyVaultTest {
    @Test
    fun encryptsKeysAtRestAndRoundTripsAcrossVaultInstances() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(
            AndroidKeystoreApiKeyVault.PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        preferences.edit().clear().commit()

        val secret = "unit-test-secret-do-not-store-plain"
        val first = AndroidKeystoreApiKeyVault(context)
        first.save(CloudAiProviderId.OPENROUTER, secret)

        assertFalse(preferences.all.toString().contains(secret))
        assertTrue(first.hasKey(CloudAiProviderId.OPENROUTER))
        assertEquals(secret, AndroidKeystoreApiKeyVault(context).read(CloudAiProviderId.OPENROUTER))

        first.delete(CloudAiProviderId.OPENROUTER)
        assertFalse(first.hasKey(CloudAiProviderId.OPENROUTER))
        assertNull(first.read(CloudAiProviderId.OPENROUTER))
        preferences.edit().clear().commit()
    }
}
