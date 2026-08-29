package com.uzairansar.hermex.ui

import android.content.Context
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uzairansar.hermex.core.model.ProfileSummary
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileShortcutPublisherTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun clearDynamicShortcuts() {
        ShortcutManagerCompat.removeAllDynamicShortcuts(context)
    }

    @Test
    fun publishesProfileDeepLinksThroughThePlatformShortcutManager() {
        ProfileShortcutPublisher(context).publish(
            listOf(ProfileSummary(name = "review", displayName = "Review")),
        )

        val shortcut = ShortcutManagerCompat.getDynamicShortcuts(context)
            .first { it.id.startsWith("profile_") }
        assertEquals("New Chat: Review", shortcut.shortLabel.toString())
        assertEquals("hermes-agent://new-chat-profile?profile=review", shortcut.intent.dataString)
        assertTrue(shortcut.isEnabled)
    }
}
