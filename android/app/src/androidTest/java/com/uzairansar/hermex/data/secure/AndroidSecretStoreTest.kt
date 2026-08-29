@file:Suppress("DEPRECATION")

package com.uzairansar.hermex.data.secure

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidSecretStoreTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun resetBeforeTest() {
        AndroidSecretStore.reset(context)
    }

    @After
    fun resetAfterTest() {
        AndroidSecretStore.reset(context)
    }

    @Test
    fun encryptsValuesAndPersistsAcrossStoreInstances() {
        AndroidSecretStore(context).apply {
            putString("server_token", "top-secret-value")
            putString("cookie_one", "cookie-secret")
        }

        val restored = AndroidSecretStore(context)
        val rawPreferences = context.getSharedPreferences("hermex_secure_v2", Context.MODE_PRIVATE).all.toString()

        assertEquals("top-secret-value", restored.getString("server_token"))
        assertEquals("cookie-secret", restored.getString("cookie_one"))
        assertFalse(rawPreferences.contains("server_token"))
        assertFalse(rawPreferences.contains("top-secret-value"))
    }

    @Test
    fun removesKeysAndPrefixesWithoutDisturbingOtherSecrets() {
        val store = AndroidSecretStore(context)
        store.putString("cookie_one", "one")
        store.putString("cookie_two", "two")
        store.putString("server", "kept")

        store.remove("cookie_one")
        store.clearPrefix("cookie_")

        assertNull(store.getString("cookie_one"))
        assertNull(store.getString("cookie_two"))
        assertEquals("kept", store.getString("server"))
    }

    @Suppress("DEPRECATION")
    @Test
    fun migratesExistingEncryptedSharedPreferences() {
        val legacy = EncryptedSharedPreferences.create(
            context,
            "hermex_secure",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        check(legacy.edit().putString("servers", "legacy-secret").commit())

        val store = AndroidSecretStore(context)

        assertEquals("legacy-secret", store.getString("servers"))
        assertEquals("legacy-secret", AndroidSecretStore(context).getString("servers"))
        assertTrue(context.getSharedPreferences("hermex_secure", Context.MODE_PRIVATE).all.isEmpty())
    }
}
