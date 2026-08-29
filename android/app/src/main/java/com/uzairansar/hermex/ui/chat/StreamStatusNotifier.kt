package com.uzairansar.hermex.ui.chat

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.uzairansar.hermex.AppVisibilityTracker
import com.uzairansar.hermex.MainActivity
import com.uzairansar.hermex.R
import com.uzairansar.hermex.ui.localization.localizedString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class StreamStatusNotifier(private val context: Context) {
    private val notificationManager = NotificationManagerCompat.from(context)

    fun monitor(
        serverId: String,
        sessionId: String,
        state: StateFlow<ChatUiState>,
        completionNotificationsEnabled: () -> Boolean,
        showResponseExcerpts: () -> Boolean,
        appIsActive: () -> Boolean = AppVisibilityTracker::isActive,
    ) {
        val key = monitorKey(serverId, sessionId)
        val owner = Any()
        val job = monitorScope.launch(start = CoroutineStart.LAZY) {
            var previousCompletionTrigger = state.value.responseCompletionTrigger
            var observedStreaming = state.value.isStreaming
            var recoveryStreamId: String? = null
            try {
                state.collect { current ->
                    if (current.isStreaming) {
                        observedStreaming = true
                        current.activeStreamId?.takeIf { it.isNotBlank() && serverId.isNotBlank() }?.let { streamId ->
                            if (recoveryStreamId != streamId) {
                                recoveryStreamId?.let { previous ->
                                    LiveStreamOwnerRegistry.release(serverId, sessionId, previous, owner)
                                    StreamRecoveryService.stop(context, serverId, sessionId, previous)
                                }
                                recoveryStreamId = streamId
                                LiveStreamOwnerRegistry.acquire(serverId, sessionId, streamId, owner)
                                if (!StreamRecoveryService.start(context, serverId, sessionId, streamId)) {
                                    Toast.makeText(
                                        context,
                                        context.localizedString("Background stream recovery could not start."),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        }
                        show(
                            serverId = serverId,
                            sessionId = sessionId,
                            streamId = current.activeStreamId,
                            recoveryLabel = current.activeStreamRecoveryLabel,
                            toolActivity = current.liveToolActivity,
                            preview = current.messages.lastOrNull { it.role == "assistant" }?.displayText
                                ?.takeIf { showResponseExcerpts() },
                        )
                    } else {
                        clear(serverId, sessionId)
                        recoveryStreamId?.let { streamId ->
                            LiveStreamOwnerRegistry.release(serverId, sessionId, streamId, owner)
                            StreamRecoveryService.stop(context, serverId, sessionId, streamId)
                        }
                        if (
                            current.responseCompletionTrigger > previousCompletionTrigger &&
                            ResponseCompletionNotificationPolicy.shouldSchedule(
                                preferenceEnabled = completionNotificationsEnabled(),
                                canPostNotifications = canPostNotifications(COMPLETION_CHANNEL_ID),
                                completedNormally = true,
                                appIsActive = appIsActive(),
                            )
                        ) {
                            showResponseComplete(serverId, sessionId)
                        }
                        if (observedStreaming) {
                            monitorJobs.computeIfPresent(key) { _, registration ->
                                registration.takeUnless { it.owner === owner }
                            }
                            cancel()
                        }
                    }
                    previousCompletionTrigger = current.responseCompletionTrigger
                }
            } finally {
                recoveryStreamId?.let { streamId ->
                    LiveStreamOwnerRegistry.release(serverId, sessionId, streamId, owner)
                }
            }
        }
        monitorJobs.put(key, MonitorRegistration(owner, job))?.job?.cancel()
        job.start()
    }

    fun release(serverId: String, sessionId: String) {
        monitorJobs.remove(monitorKey(serverId, sessionId))?.job?.cancel()
    }

    @SuppressLint("MissingPermission")
    fun show(
        serverId: String,
        sessionId: String,
        streamId: String?,
        recoveryLabel: String?,
        toolActivity: String?,
        preview: String?,
    ) {
        ensureChannel()
        if (!canPostNotifications(CHANNEL_ID)) return
        runCatching {
            notificationManager.notify(
                notificationId(serverId, sessionId),
                ongoingNotification(serverId, sessionId, streamId, recoveryLabel, toolActivity, preview),
            )
        }
    }

    internal fun ongoingNotification(
        serverId: String,
        sessionId: String,
        streamId: String?,
        recoveryLabel: String? = null,
        toolActivity: String? = null,
        preview: String? = null,
    ): Notification {
        ensureChannel()
        val normalizedRecoveryLabel = recoveryLabel?.takeIf { it.isNotBlank() }
        val title = if (normalizedRecoveryLabel != null) {
            if (normalizedRecoveryLabel.startsWith("Checking", ignoreCase = true)) {
                context.localizedString("Checking stream")
            } else {
                context.localizedString("Hermes is reconnecting the response stream")
            }
        } else {
            context.localizedString("Responding")
        }
        val content = normalizedRecoveryLabel?.let { label ->
            if (label.startsWith("Checking", ignoreCase = true)) {
                context.localizedString("Checking stream")
            } else {
                context.localizedString("Reconnecting stream")
            }
        }
            ?: toolActivity
            ?: preview?.takeIf { it.isNotBlank() }?.take(120)
            ?: context.localizedString("Streaming")
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hermex_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(contentIntent(serverId, sessionId, complete = false))
            .setSubText(streamId?.take(8))
            .build()
    }

    fun clear(serverId: String, sessionId: String) {
        notificationManager.cancel(notificationId(serverId, sessionId))
    }

    @SuppressLint("MissingPermission")
    fun showResponseComplete(serverId: String, sessionId: String) {
        ensureCompletionChannel()
        if (!canPostNotifications(COMPLETION_CHANNEL_ID)) return
        val notification = NotificationCompat.Builder(context, COMPLETION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hermex_launcher_monochrome)
            .setContentTitle(context.localizedString("Hermes response complete"))
            .setContentText(context.localizedString("The assistant finished responding."))
            .setAutoCancel(true)
            .setContentIntent(contentIntent(serverId, sessionId, complete = true))
            .build()
        runCatching {
            notificationManager.notify(completionNotificationId(serverId, sessionId), notification)
        }
    }

    internal fun notificationId(serverId: String, sessionId: String): Int =
        notificationIdentifier("stream", serverId, sessionId)

    private fun completionNotificationId(serverId: String, sessionId: String): Int =
        notificationIdentifier("complete", serverId, sessionId)

    private fun contentIntent(serverId: String, sessionId: String, complete: Boolean): PendingIntent {
        val uri = Uri.Builder()
            .scheme("hermes-agent")
            .authority("chat")
            .appendQueryParameter("serverId", serverId)
            .appendQueryParameter("sessionId", sessionId)
            .build()
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val requestKey = if (complete) "complete" else "stream"
        return PendingIntent.getActivity(
            context,
            notificationIdentifier(requestKey, serverId, sessionId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.localizedString("Streaming"), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.localizedString("Responding")
                setShowBadge(false)
            },
        )
    }

    private fun ensureCompletionChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(COMPLETION_CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(COMPLETION_CHANNEL_ID, context.localizedString("Response complete"), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.localizedString("The assistant finished responding.")
                setShowBadge(true)
            },
        )
    }

    internal fun canPostNotifications(channelId: String): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!permissionGranted || !notificationManager.areNotificationsEnabled()) return false
        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(channelId)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun canPostCompletionNotifications(): Boolean = canPostNotifications(COMPLETION_CHANNEL_ID)

    private fun monitorKey(serverId: String, sessionId: String): String = "$serverId\u0000$sessionId"

    private companion object {
        const val CHANNEL_ID = "chat_stream_status"
        const val COMPLETION_CHANNEL_ID = "response_completion"
        val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val monitorJobs = ConcurrentHashMap<String, MonitorRegistration>()
    }

    private data class MonitorRegistration(val owner: Any, val job: Job)
}

internal fun notificationIdentifier(namespace: String, serverId: String, sessionId: String): Int {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$namespace\u0000$serverId\u0000$sessionId".toByteArray(Charsets.UTF_8))
    return ByteBuffer.wrap(digest, 0, Int.SIZE_BYTES).int
}

internal object LiveStreamOwnerRegistry {
    private val owners = ConcurrentHashMap<String, Any>()

    fun acquire(serverId: String, sessionId: String, streamId: String, owner: Any) {
        owners[key(serverId, sessionId, streamId)] = owner
    }

    fun release(serverId: String, sessionId: String, streamId: String, owner: Any) {
        owners.remove(key(serverId, sessionId, streamId), owner)
    }

    fun hasOwner(serverId: String, sessionId: String, streamId: String): Boolean =
        owners.containsKey(key(serverId, sessionId, streamId))

    private fun key(serverId: String, sessionId: String, streamId: String): String =
        "$serverId\u0000$sessionId\u0000$streamId"
}
