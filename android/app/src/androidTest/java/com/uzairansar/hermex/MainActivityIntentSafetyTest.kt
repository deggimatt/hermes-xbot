package com.uzairansar.hermex

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uzairansar.hermex.ui.SavedStatePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityIntentSafetyTest {
    @Test
    fun navigationIntentKeepsOnlyActionAndData() {
        val source = Intent(Intent.ACTION_VIEW, Uri.parse("hermes-agent://settings")).apply {
            putExtra("oversized", "secret")
            clipData = ClipData.newPlainText("label", "private")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addCategory(Intent.CATEGORY_BROWSABLE)
        }

        val sanitized = source.sanitizedForNavigation()

        assertEquals(Intent.ACTION_VIEW, sanitized.action)
        assertEquals(Uri.parse("hermes-agent://settings"), sanitized.data)
        assertFalse(sanitized.hasExtra("oversized"))
        assertNull(sanitized.clipData)
        assertEquals(0, sanitized.flags)
        assertNull(sanitized.categories)
    }

    @Test
    fun oversizedNavigationFieldsCannotReachTheQueueOrSavedState() {
        val source = Intent(
            "a".repeat(SavedStatePolicy.MaximumIntentActionCharacters + 100),
            Uri.parse("hermes-agent://chat?value=${"x".repeat(SavedStatePolicy.MaximumNavigationUriCharacters)}"),
        )

        val sanitized = source.sanitizedForNavigation()

        assertEquals(SavedStatePolicy.MaximumIntentActionCharacters, sanitized.action?.length)
        assertNull(sanitized.data)
    }
}
