package com.uzairansar.hermex

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.uzairansar.hermex.ui.HermexApp
import com.uzairansar.hermex.ui.SavedStatePolicy
import com.uzairansar.hermex.ui.reportHermexShortcutUsage
import com.uzairansar.hermex.ui.chat.StreamRecoveryService
import com.uzairansar.hermex.ui.localization.localizedString
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {
    private val shortcutIntentChannel = Channel<Intent>(
        capacity = MAXIMUM_PENDING_NAVIGATION_INTENTS,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private var pendingShortcutIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingShortcutIntent = savedInstanceState?.restoredShortcutIntent()
            ?: intent.takeIf { savedInstanceState == null }?.sanitizedForNavigation()?.also {
                it.reportHermexShortcutUsage(this)
            }
        val container = (application as HermexApplication).container
        setContent {
            HermexApp(
                container = container,
                shortcutIntents = shortcutIntentChannel.receiveAsFlow(),
                onResetSecureStorage = {
                    (application as HermexApplication).resetSecureStorage()
                },
                onShortcutIntentConsumed = { consumed ->
                    if (pendingShortcutIntent === consumed) pendingShortcutIntent = null
                },
            )
        }
        pendingShortcutIntent?.let { shortcutIntentChannel.trySend(it) }
    }

    override fun onResume() {
        super.onResume()
        if (!StreamRecoveryService.resumePending(this)) {
            Toast.makeText(
                this,
                localizedString("Background stream recovery could not start."),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val sanitized = intent.sanitizedForNavigation()
        sanitized.reportHermexShortcutUsage(this)
        setIntent(sanitized)
        pendingShortcutIntent = sanitized
        shortcutIntentChannel.trySend(sanitized)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingShortcutIntent?.let { pending ->
            outState.putString(
                KEY_PENDING_SHORTCUT_ACTION,
                pending.action?.take(SavedStatePolicy.MaximumIntentActionCharacters),
            )
            outState.putString(
                KEY_PENDING_SHORTCUT_DATA,
                pending.dataString?.take(SavedStatePolicy.MaximumNavigationUriCharacters),
            )
        }
        super.onSaveInstanceState(outState)
    }

    private companion object {
        const val KEY_PENDING_SHORTCUT_ACTION = "pending_shortcut_action"
        const val KEY_PENDING_SHORTCUT_DATA = "pending_shortcut_data"
        const val MAXIMUM_PENDING_NAVIGATION_INTENTS = 16
    }
}

internal fun Intent.sanitizedForNavigation(): Intent = Intent(
    action?.take(SavedStatePolicy.MaximumIntentActionCharacters),
    data?.takeIf { it.toString().length <= SavedStatePolicy.MaximumNavigationUriCharacters },
)

private fun Bundle.restoredShortcutIntent(): Intent? {
    val action = getString("pending_shortcut_action")?.take(SavedStatePolicy.MaximumIntentActionCharacters)
    val data = getString("pending_shortcut_data")
        ?.take(SavedStatePolicy.MaximumNavigationUriCharacters)
        ?.let(Uri::parse)
    return if (action == null && data == null) null else Intent(action, data)
}
