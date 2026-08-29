package com.uzairansar.hermex.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uzairansar.hermex.core.runSuspendCatching
import com.uzairansar.hermex.core.model.GitCommitResponse
import com.uzairansar.hermex.core.model.GitFileChange
import com.uzairansar.hermex.core.model.GitMutationResponse
import com.uzairansar.hermex.core.network.ApiError
import com.uzairansar.hermex.data.repository.GitRepository
import com.uzairansar.hermex.ui.git.failureMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ChatGitPhase(val title: String) {
    GeneratingMessage("Generating message..."),
    Committing("Committing..."),
    Pushing("Pushing..."),
}

data class ChatGitUiState(
    val hasRepository: Boolean = false,
    val branch: String? = null,
    val files: List<GitFileChange> = emptyList(),
    val changedCount: Int = 0,
    val totalAdditions: Int = 0,
    val totalDeletions: Int = 0,
    val truncated: Boolean = false,
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val phase: ChatGitPhase? = null,
    val notice: String? = null,
    val error: String? = null,
) {
    val hasChanges: Boolean = changedCount > 0
}

class ChatGitViewModel(
    private val sessionId: String,
    private val repository: GitRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatGitUiState(isLoading = true))
    val state: StateFlow<ChatGitUiState> = _state
    private var refreshJob: Job? = null
    private var refreshGeneration = 0L

    init {
        refresh()
    }

    fun refresh(clearMessages: Boolean = true) {
        val generation = ++refreshGeneration
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    error = if (clearMessages) null else it.error,
                    notice = if (clearMessages) null else it.notice,
                )
            }
            runSuspendCatching { repository.status(sessionId) }
                .onSuccess { status ->
                    if (generation != refreshGeneration) return@onSuccess
                    val files = status.files.orEmpty()
                    val hasRepository = status.isGit != false && (status.isGit == true || status.error == null)
                    _state.update {
                        it.copy(
                            hasRepository = hasRepository,
                            branch = status.branch,
                            files = files,
                            changedCount = status.changedCount ?: files.size,
                            totalAdditions = status.totalAdditions ?: files.sumOf { file -> file.additions ?: 0 },
                            totalDeletions = status.totalDeletions ?: files.sumOf { file -> file.deletions ?: 0 },
                            truncated = status.truncated == true,
                            isLoading = false,
                            error = status.error ?: if (clearMessages) null else it.error,
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException || generation != refreshGeneration) return@onFailure
                    _state.update {
                        it.copy(
                            hasRepository = false,
                            isLoading = false,
                            error = error.friendlyGitMessage("Could not load git status."),
                        )
                    }
                }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(notice = null, error = null) }
    }

    fun fetch() {
        remote("Fetch complete") { repository.fetch(sessionId) }
    }

    fun pull() {
        remote("Pull complete") { repository.pull(sessionId) }
    }

    fun push() {
        remote("Push complete") { repository.push(sessionId) }
    }

    private fun remote(success: String, action: suspend () -> GitMutationResponse) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, phase = null, notice = null, error = null) }
        viewModelScope.launch {
            runSuspendCatching { action() }
                .onSuccess { response ->
                    val responseError = response.failureMessage("The server rejected this git action.")
                    if (responseError != null) {
                        _state.update { it.copy(isMutating = false, error = responseError) }
                    } else {
                        _state.update { it.copy(isMutating = false, notice = response.message ?: success) }
                        refresh()
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isMutating = false, error = error.friendlyGitMessage("Git action failed.")) }
                }
        }
    }

    fun quickCommit(push: Boolean) {
        val snapshot = _state.value
        if (snapshot.isMutating) return
        val paths = snapshot.files.mapNotNull { it.serverPath() }
        if (paths.isEmpty()) {
            _state.update { it.copy(error = "There are no changes to commit.", notice = null) }
            return
        }
        if (snapshot.truncated) {
            _state.update {
                it.copy(
                    error = "Too many changes to quick-commit. Commit in smaller batches, or use git directly.",
                    notice = null,
                )
            }
            return
        }

        viewModelScope.launch {
            val newlyStagedPaths = snapshot.files
                .filter { it.staged != true }
                .mapNotNull { it.serverPath() }
            var stagedByQuickCommit = false
            _state.update { it.copy(isMutating = true, phase = ChatGitPhase.GeneratingMessage, error = null, notice = null) }
            runSuspendCatching {
                repository.stage(sessionId, paths).throwIfError()
                stagedByQuickCommit = true
                val suggestion = repository.commitMessage(sessionId)
                suggestion.failureMessage("The server could not generate a commit message.")?.let(::error)
                val message = suggestion.message?.trim().orEmpty()
                if (message.isBlank()) error("No commit message could be generated.")

                _state.update { it.copy(phase = ChatGitPhase.Committing) }
                val commit = repository.commit(sessionId, message)
                commit.failureMessage("The server could not commit these changes.")?.let(::error)

                var pushFailure: String? = null
                if (push) {
                    _state.update { it.copy(phase = ChatGitPhase.Pushing) }
                    pushFailure = runSuspendCatching { repository.push(sessionId) }
                        .fold(
                            onSuccess = { response -> response.failureMessage("Push failed.") },
                            onFailure = { error -> error.friendlyGitMessage("Push failed.") },
                        )
                }

                QuickCommitResult(
                    commit = commit,
                    messageWasTruncated = suggestion.truncated == true,
                    pushFailure = pushFailure,
                    pushed = push && pushFailure == null,
                )
            }.onSuccess { result ->
                val notice = result.successNotice()
                val error = result.pushFailure?.let { "Committed, but the push failed. $it" }
                _state.update { it.copy(isMutating = false, phase = null, notice = notice, error = error) }
                refresh()
            }.onFailure { error ->
                val rollbackError = if (stagedByQuickCommit && newlyStagedPaths.isNotEmpty()) {
                    withContext(NonCancellable) {
                        runSuspendCatching {
                            repository.unstage(sessionId, newlyStagedPaths).throwIfError()
                        }.exceptionOrNull()
                    }
                } else {
                    null
                }
                val failureMessage = error.friendlyGitMessage("Could not commit changes.")
                val rollbackMessage = rollbackError?.friendlyGitMessage("Could not restore the previous staging state.")
                _state.update {
                    it.copy(
                        isMutating = false,
                        phase = null,
                        error = listOfNotNull(failureMessage, rollbackMessage).joinToString(" "),
                    )
                }
                refresh(clearMessages = false)
            }
        }
    }
}

private data class QuickCommitResult(
    val commit: GitCommitResponse,
    val messageWasTruncated: Boolean,
    val pushFailure: String?,
    val pushed: Boolean,
) {
    fun successNotice(): String {
        val sha = commit.shortSha ?: commit.sha?.take(7)
        val base = if (pushed) "Commit & push complete" else "Commit complete"
        val parts = buildList {
            add(base)
            if (sha != null) add(sha)
            if (messageWasTruncated) add("message may be partial")
        }
        return parts.joinToString(" - ")
    }
}

private fun GitMutationResponse.throwIfError() {
    failureMessage("The server rejected this git action.")?.let(::error)
}

private fun GitFileChange.serverPath(): String? =
    (path ?: workspacePath)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

private fun Throwable.friendlyGitMessage(fallback: String): String {
    val body = (this as? ApiError.Http)?.body.orEmpty()
    if (body.contains("destructive_git_disabled", ignoreCase = true)) {
        return "Writes disabled on server. Enable HERMES_WEBUI_WORKSPACE_GIT_DESTRUCTIVE=1 on the server to use this."
    }
    if (body.contains("active_stream", ignoreCase = true)) {
        return "Wait for the active response to finish before changing this repository."
    }
    return message ?: fallback
}
