package com.uzairansar.hermex

import android.content.Intent
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.uzairansar.hermex.data.share.SharedAttachment
import com.uzairansar.hermex.data.share.SharedDraftStore
import com.uzairansar.hermex.data.share.SharedDraftPolicy
import com.uzairansar.hermex.data.share.copyAcceptedSharedAttachment
import com.uzairansar.hermex.ui.localization.localizedString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class ShareActivity : ComponentActivity() {
    private val importViewModel by viewModels<ShareImportViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        importViewModel.start(intent)
        lifecycleScope.launch {
            val result = importViewModel.result.filterNotNull().first()
            if (!importViewModel.consumeResult()) return@launch
            if (result.saved) {
                if (result.rejectedAttachmentCount > 0) {
                    Toast.makeText(
                        this@ShareActivity,
                        "${result.rejectedAttachmentCount} - ${localizedString("Could Not Load Attachment")}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                startActivity(Intent(this@ShareActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    data = Uri.parse("hermes-agent://share")
                })
            } else {
                Toast.makeText(
                    this@ShareActivity,
                    localizedString("Could Not Load Attachment"),
                    Toast.LENGTH_LONG,
                ).show()
            }
            finish()
        }
    }
}

class ShareImportViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val started = AtomicBoolean(false)
    private val handled = AtomicBoolean(false)
    private val _result = MutableStateFlow<ShareImportResult?>(null)
    val result: StateFlow<ShareImportResult?> = _result

    fun start(intent: Intent) {
        if (!started.compareAndSet(false, true)) return
        val app = getApplication<android.app.Application>()
        val shareIntent = Intent(intent)
        viewModelScope.launch(Dispatchers.IO) {
            _result.value = try {
                if (shareIntent.action != Intent.ACTION_SEND && shareIntent.action != Intent.ACTION_SEND_MULTIPLE) {
                    ShareImportResult(saved = false, rejectedAttachmentCount = 0)
                } else {
                    val store = SharedDraftStore(app)
                    val text = runCatching { shareIntent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() }
                        .getOrNull()
                        .orEmpty()
                        .take(MAXIMUM_SHARED_TEXT_CHARACTERS)
                    val subject = runCatching { shareIntent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString() }
                        .getOrNull()
                        .orEmpty()
                        .take(MAXIMUM_SHARED_SUBJECT_CHARACTERS)
                    val uriSelection = shareIntent.sharedAttachmentUris(SharedDraftPolicy.MAXIMUM_SHARED_ATTACHMENT_COUNT)
                    var rejected = uriSelection.overflowCount
                    val attachments = buildList {
                        for (uri in uriSelection.uris) {
                            val attachment = app.cacheSharedAttachment(uri)
                            if (attachment == null) rejected += 1 else add(attachment)
                        }
                    }
                    ShareImportResult(
                        saved = store.savePendingDraft(
                            text = SharedDraftPolicy.draftText(subject = subject, text = text),
                            attachments = attachments,
                        ),
                        rejectedAttachmentCount = rejected,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                ShareImportResult(saved = false, rejectedAttachmentCount = 0)
            }
        }
    }

    fun consumeResult(): Boolean = handled.compareAndSet(false, true)
}

data class ShareImportResult(
    val saved: Boolean,
    val rejectedAttachmentCount: Int,
)

private fun Context.cacheSharedAttachment(uri: Uri): SharedAttachment? {
        val displayName = (
            runCatching { sharedDisplayName(uri) }.getOrNull()
                ?: uri.lastPathSegment?.substringAfterLast('/')
        )
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "shared-file"
        val safeName = displayName
            .replace(Regex("""[^\w.\- ]"""), "_")
            .ifBlank { "shared-file" }
            .take(MAXIMUM_SHARED_FILENAME_CHARACTERS)
        val file = File(cacheDir, "shared-${UUID.randomUUID()}-$safeName")
        val copiedBytes = runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                copyAcceptedSharedAttachment(input = input, destination = file)
            }
        }.getOrNull() ?: return null
        if (!SharedDraftPolicy.acceptsByteCount(copiedBytes)) {
            runCatching { file.delete() }
            return null
        }

        return SharedAttachment(
            uri = uri.toString(),
            displayName = displayName.take(MAXIMUM_SHARED_FILENAME_CHARACTERS),
            mimeType = runCatching { contentResolver.getType(uri) }.getOrNull(),
            cachedPath = file.absolutePath,
        )
    }

private fun Context.sharedDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
        }
        return null
}

internal data class SharedAttachmentUriSelection(val uris: List<Uri>, val overflowCount: Int)

internal fun Intent.sharedAttachmentUris(limit: Int): SharedAttachmentUriSelection {
    val accepted = mutableListOf<Uri>()
    val seen = mutableSetOf<String>()
    var overflowCount = 0

    fun accept(uri: Uri?) {
        uri ?: return
        if (!seen.add(uri.toString())) return
        if (accepted.size >= limit.coerceAtLeast(0)) {
            overflowCount += 1
            return
        }
        accepted += uri
    }

    val streamValues = runCatching {
        @Suppress("DEPRECATION")
        when (val value = extras?.get(Intent.EXTRA_STREAM)) {
            null -> emptyList()
            is ArrayList<*> -> value
            else -> listOf(value)
        }
    }.getOrElse {
        overflowCount += 1
        emptyList()
    }
    val inspectedStreamValues = streamValues.take(MAXIMUM_SHARED_URI_CANDIDATES)
    overflowCount += streamValues.size - inspectedStreamValues.size
    for (value in inspectedStreamValues) {
        if (value is Uri) accept(value) else overflowCount += 1
    }

    accept(runCatching { data }.getOrNull())
    runCatching { clipData }.getOrNull()?.let { clips ->
        var index = 0
        val inspectedItemCount = clips.itemCount.coerceAtMost(MAXIMUM_SHARED_URI_CANDIDATES)
        overflowCount += clips.itemCount - inspectedItemCount
        while (index < inspectedItemCount) {
            runCatching { clips.getItemAt(index).uri }
                .onSuccess(::accept)
                .onFailure { overflowCount += 1 }
            index += 1
        }
    }
    return SharedAttachmentUriSelection(accepted, overflowCount)
}

private const val MAXIMUM_SHARED_TEXT_CHARACTERS = 100_000
private const val MAXIMUM_SHARED_SUBJECT_CHARACTERS = 2_000
private const val MAXIMUM_SHARED_FILENAME_CHARACTERS = 120
private const val MAXIMUM_SHARED_URI_CANDIDATES = 256
