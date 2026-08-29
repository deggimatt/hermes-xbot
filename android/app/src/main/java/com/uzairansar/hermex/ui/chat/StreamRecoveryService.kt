package com.uzairansar.hermex.ui.chat

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.uzairansar.hermex.AppVisibilityTracker
import com.uzairansar.hermex.HermexApplication
import com.uzairansar.hermex.core.model.SessionStatusResponse
import com.uzairansar.hermex.core.network.HermesApiClient
import com.uzairansar.hermex.core.network.HermesJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.ConcurrentHashMap

class StreamRecoveryService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private lateinit var store: StreamRecoveryStore
    private lateinit var notifier: StreamStatusNotifier

    override fun onCreate() {
        super.onCreate()
        store = StreamRecoveryStore(this)
        notifier = StreamStatusNotifier(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CLEAR_SERVER -> {
                intent.serverId()?.let { serverId ->
                    store.records()
                        .filter { it.serverId == serverId }
                        .forEach { record ->
                            jobs.remove(record.key)?.cancel()
                            store.remove(record.key)
                            notifier.clear(record.serverId, record.sessionId)
                        }
                }
            }
            ACTION_STOP -> {
                val keys = intent.recordKeys(store.records())
                keys.forEach { key ->
                    jobs.remove(key)?.cancel()
                    store.remove(key)
                }
                intent.serverId()?.let { serverId ->
                    intent.sessionId()?.let { sessionId -> notifier.clear(serverId, sessionId) }
                }
            }
            else -> intent?.record()?.let { record ->
                store.records()
                    .filter { it.sessionKey == record.sessionKey && it.streamId != record.streamId }
                    .forEach { replaced ->
                        jobs.remove(replaced.key)?.cancel()
                        store.remove(replaced.key)
                    }
                store.put(record)
            }
        }

        val records = store.records()
        if (records.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        ensureForeground(records.first())
        records.forEach(::launchMonitor)
        // Records are durable and age out after five hours, so a killed monitor can safely resume.
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        abandonAllRecovery()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun launchMonitor(record: StreamRecoveryRecord) {
        jobs.computeIfAbsent(record.key) {
            scope.launch {
                val ownerJob = currentCoroutineContext()[Job]!!
                var consecutiveFailures = 0
                var client: HermesApiClient? = null
                try {
                    while (store.contains(record)) {
                        if (streamRecoveryRecordExpired(record.startedAtMillis, System.currentTimeMillis())) {
                            if (store.removeIfCurrent(record)) {
                                notifier.clear(record.serverId, record.sessionId)
                            }
                            return@launch
                        }
                        if (LiveStreamOwnerRegistry.hasOwner(record.serverId, record.sessionId, record.streamId)) {
                            delay(POLL_INTERVAL_MILLIS)
                            continue
                        }
                        val status = try {
                            val activeClient = client ?: (application as HermexApplication).container
                                .apiClient(record.serverId.toHttpUrl())
                                .also { client = it }
                            activeClient.chatStreamStatus(record.streamId).also { consecutiveFailures = 0 }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Throwable) {
                            consecutiveFailures += 1
                            client = null
                            delay(streamRecoveryRetryDelayMillis(consecutiveFailures))
                            continue
                        }
                        if (!status.isActiveFor(record.streamId)) {
                            finish(record, completedNormally = status.error.isNullOrBlank())
                            return@launch
                        }
                        delay(POLL_INTERVAL_MILLIS)
                    }
                } finally {
                    jobs.remove(record.key, ownerJob)
                    stopIfNoActiveJobs()
                }
            }
        }
    }

    private suspend fun finish(record: StreamRecoveryRecord, completedNormally: Boolean) {
        if (!store.removeIfCurrent(record)) return
        notifier.clear(record.serverId, record.sessionId)
        if (completedNormally) {
            val settings = (application as HermexApplication).container.localSettingsRepository
            val enabled = settings.responseCompletionNotificationsEnabled.first()
            if (enabled && !AppVisibilityTracker.isActive()) {
                notifier.showResponseComplete(record.serverId, record.sessionId)
            }
        }
        val remaining = store.records()
        if (remaining.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            ensureForeground(remaining.first())
        }
    }

    private fun stopIfNoActiveJobs() {
        val records = store.records()
        if (!streamRecoveryShouldStop(jobs.size, records.size)) {
            records.firstOrNull()?.let(::ensureForeground)
            return
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun abandonAllRecovery() {
        val records = store.records()
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        records.forEach { record ->
            store.remove(record.key)
            notifier.clear(record.serverId, record.sessionId)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureForeground(record: StreamRecoveryRecord) {
        val notification = notifier.ongoingNotification(
            serverId = record.serverId,
            sessionId = record.sessionId,
            streamId = record.streamId,
            recoveryLabel = "Checking background response",
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                notifier.notificationId(record.serverId, record.sessionId),
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(notifier.notificationId(record.serverId, record.sessionId), notification)
        }
    }

    private fun SessionStatusResponse.isActiveFor(expectedStreamId: String): Boolean {
        if (active == false) return false
        if (active == true || isStreaming == true) return true
        return streamId?.takeIf { it.isNotBlank() } == expectedStreamId ||
            activeStreamId?.takeIf { it.isNotBlank() } == expectedStreamId
    }

    companion object {
        private const val ACTION_START = "com.uzairansar.hermex.action.START_STREAM_RECOVERY"
        private const val ACTION_STOP = "com.uzairansar.hermex.action.STOP_STREAM_RECOVERY"
        private const val ACTION_CLEAR_SERVER = "com.uzairansar.hermex.action.CLEAR_SERVER_STREAM_RECOVERY"
        private const val EXTRA_SERVER_ID = "server_id"
        private const val EXTRA_SESSION_ID = "session_id"
        private const val EXTRA_STREAM_ID = "stream_id"
        private const val POLL_INTERVAL_MILLIS = 2_000L

        fun start(context: Context, serverId: String, sessionId: String, streamId: String): Boolean = runCatching {
            check(StreamRecoveryStore(context).put(StreamRecoveryRecord(serverId, sessionId, streamId))) {
                "Could not persist background stream recovery."
            }
            val intent = Intent(context, StreamRecoveryService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SERVER_ID, serverId)
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_STREAM_ID, streamId)
            }
            ContextCompat.startForegroundService(context, intent)
        }.isSuccess

        fun resumePending(context: Context): Boolean {
            if (StreamRecoveryStore(context).records().isEmpty()) return true
            val intent = Intent(context, StreamRecoveryService::class.java).apply {
                action = ACTION_START
            }
            return runCatching { ContextCompat.startForegroundService(context, intent) }.isSuccess
        }

        fun stop(context: Context, serverId: String, sessionId: String, streamId: String? = null) {
            val store = StreamRecoveryStore(context)
            val matching = store.records()
                .filter { it.serverId == serverId && it.sessionId == sessionId && (streamId == null || it.streamId == streamId) }
            matching.forEach { store.remove(it.key) }
            val streamIds = matching.map { it.streamId }.ifEmpty { listOfNotNull(streamId) }
            streamIds.forEach { stoppedStreamId ->
                val intent = Intent(context, StreamRecoveryService::class.java).apply {
                    action = ACTION_STOP
                    putExtra(EXTRA_SERVER_ID, serverId)
                    putExtra(EXTRA_SESSION_ID, sessionId)
                    putExtra(EXTRA_STREAM_ID, stoppedStreamId)
                }
                runCatching { context.startService(intent) }
            }
        }

        fun clearServer(context: Context, serverId: String) {
            val intent = Intent(context, StreamRecoveryService::class.java).apply {
                action = ACTION_CLEAR_SERVER
                putExtra(EXTRA_SERVER_ID, serverId)
            }
            runCatching { context.startService(intent) }
                .onFailure {
                    val store = StreamRecoveryStore(context)
                    store.records()
                        .filter { it.serverId == serverId }
                        .forEach { store.remove(it.key) }
                }
        }

        private fun Intent.serverId(): String? = getStringExtra(EXTRA_SERVER_ID)?.takeIf { it.isNotBlank() }
        private fun Intent.sessionId(): String? = getStringExtra(EXTRA_SESSION_ID)?.takeIf { it.isNotBlank() }
        private fun Intent.recordKeys(records: List<StreamRecoveryRecord>): List<String> {
            val serverId = serverId() ?: return emptyList()
            val sessionId = sessionId() ?: return emptyList()
            val streamId = getStringExtra(EXTRA_STREAM_ID)?.takeIf { it.isNotBlank() }
            if (streamId != null) return listOf(StreamRecoveryRecord.key(serverId, sessionId, streamId))
            return records.filter {
                it.serverId == serverId && it.sessionId == sessionId
            }.map { it.key }
        }
        private fun Intent.record(): StreamRecoveryRecord? {
            val serverId = serverId() ?: return null
            val sessionId = sessionId() ?: return null
            val streamId = getStringExtra(EXTRA_STREAM_ID)?.takeIf { it.isNotBlank() } ?: return null
            return StreamRecoveryRecord(serverId, sessionId, streamId)
        }
    }
}

@Serializable
private data class StreamRecoveryRecord(
    val serverId: String,
    val sessionId: String,
    val streamId: String,
    val startedAtMillis: Long = System.currentTimeMillis(),
) {
    val sessionKey: String get() = sessionKey(serverId, sessionId)
    val key: String get() = key(serverId, sessionId, streamId)

    companion object {
        fun sessionKey(serverId: String, sessionId: String): String = "$serverId\u0000$sessionId"
        fun key(serverId: String, sessionId: String, streamId: String): String =
            "$serverId\u0000$sessionId\u0000$streamId"
    }
}

private class StreamRecoveryStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun records(): List<StreamRecoveryRecord> = preferences.getString(RECORDS_KEY, null)
        ?.let { runCatching { HermesJson.decodeFromString<List<StreamRecoveryRecord>>(it) }.getOrNull() }
        .orEmpty()

    @Synchronized
    fun put(record: StreamRecoveryRecord): Boolean {
        val updated = records().associateBy { it.key }.toMutableMap().apply { put(record.key, record) }
        return persist(updated.values.toList())
    }

    @Synchronized
    fun remove(key: String): Boolean {
        return persist(records().filterNot { it.key == key })
    }

    @Synchronized
    fun removeIfCurrent(record: StreamRecoveryRecord): Boolean {
        val current = records()
        if (current.none { it.key == record.key }) return false
        return persist(current.filterNot { it.key == record.key })
    }

    @Synchronized
    fun contains(record: StreamRecoveryRecord): Boolean = records().any { it.key == record.key }

    private fun persist(records: List<StreamRecoveryRecord>): Boolean =
        preferences.edit().putString(RECORDS_KEY, HermesJson.encodeToString(records)).commit()

    private companion object {
        const val PREFERENCES_NAME = "hermex_stream_recovery"
        const val RECORDS_KEY = "records"
    }
}

internal fun streamRecoveryRetryDelayMillis(consecutiveFailures: Int): Long {
    val exponent = (consecutiveFailures - 1).coerceIn(0, 5)
    return (2_000L shl exponent).coerceAtMost(60_000L)
}

internal fun streamRecoveryShouldRetry(consecutiveFailures: Int): Boolean = consecutiveFailures > 0

internal fun streamRecoveryShouldStop(activeJobCount: Int, durableRecordCount: Int): Boolean =
    activeJobCount == 0 && durableRecordCount == 0

internal fun streamRecoveryRecordExpired(startedAtMillis: Long, nowMillis: Long): Boolean =
    nowMillis - startedAtMillis >= MAX_STREAM_RECOVERY_AGE_MILLIS

private const val MAX_STREAM_RECOVERY_AGE_MILLIS = 5L * 60L * 60L * 1_000L
