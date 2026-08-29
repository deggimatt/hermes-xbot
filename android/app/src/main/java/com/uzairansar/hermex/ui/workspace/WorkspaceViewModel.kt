package com.uzairansar.hermex.ui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.uzairansar.hermex.ui.SavedStatePolicy
import com.uzairansar.hermex.ui.setBoundedString
import com.uzairansar.hermex.core.runSuspendCatching
import com.uzairansar.hermex.core.model.FileResponse
import com.uzairansar.hermex.core.model.WorkspaceEntry
import com.uzairansar.hermex.core.model.WorkspaceRoot
import com.uzairansar.hermex.data.repository.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class WorkspaceUiState(
    val roots: List<WorkspaceRoot> = emptyList(),
    val currentPath: String? = null,
    val searchText: String = "",
    val entries: List<WorkspaceEntry> = emptyList(),
    val preview: FileResponse? = null,
    val binaryPreview: BinaryPreview? = null,
    val isLoading: Boolean = true,
    val isPreviewLoading: Boolean = false,
    val error: String? = null,
)

data class BinaryPreview(
    val path: String,
    val bytes: ByteArray?,
    val isImage: Boolean,
    val mimeType: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BinaryPreview) return false
        return path == other.path &&
            isImage == other.isImage &&
            mimeType == other.mimeType &&
            when {
                bytes == null -> other.bytes == null
                other.bytes == null -> false
                else -> bytes.contentEquals(other.bytes)
            }
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + (bytes?.contentHashCode() ?: 0)
        result = 31 * result + isImage.hashCode()
        result = 31 * result + mimeType.hashCode()
        return result
    }
}

class WorkspaceViewModel(
    private val sessionId: String,
    private val repository: WorkspaceRepository,
    private val savedStateHandle: SavedStateHandle? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(
        WorkspaceUiState(
            currentPath = savedStateHandle?.get<String>(SAVED_CURRENT_PATH)
                ?.let { SavedStatePolicy.boundedInput(it) },
            searchText = savedStateHandle?.get<String>(SAVED_SEARCH_TEXT)
                ?.let { SavedStatePolicy.boundedInput(it, SavedStatePolicy.MaximumSearchCharacters) }
                .orEmpty(),
        ),
    )
    val state: StateFlow<WorkspaceUiState> = _state
    private var refreshJob: Job? = null
    private var refreshGeneration = 0L
    private var previewJob: Job? = null
    private var previewGeneration = 0L

    init {
        refresh()
    }

    fun refresh() {
        val path = _state.value.currentPath
        val generation = ++refreshGeneration
        previewJob?.cancel()
        previewGeneration += 1
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, preview = null, binaryPreview = null) }
            runSuspendCatching {
                coroutineScope {
                    val roots = async { runSuspendCatching { repository.workspaces() } }
                    val list = repository.list(sessionId, path)
                    roots.await().getOrNull() to list
                }
            }.onSuccess { (roots, list) ->
                if (generation != refreshGeneration) return@onSuccess
                _state.update {
                    if (it.currentPath != path) return@update it
                    val resolvedPath = (list.path ?: path)?.let { value -> SavedStatePolicy.boundedInput(value) }
                    savedStateHandle?.setBoundedString(SAVED_CURRENT_PATH, resolvedPath)
                    it.copy(
                        roots = roots ?: it.roots,
                        currentPath = resolvedPath,
                        entries = list.entries.orEmpty().sortedWith(compareBy<WorkspaceEntry> { entry ->
                            if (entry.type == "directory" || entry.type == "dir") 0 else 1
                        }.thenBy { entry -> entry.name ?: entry.path.orEmpty() }),
                        isLoading = false,
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException || generation != refreshGeneration) return@onFailure
                _state.update {
                    if (it.currentPath == path) {
                        it.copy(isLoading = false, error = error.message ?: "Could not load workspace.")
                    } else {
                        it
                    }
                }
            }
        }
    }

    fun open(entry: WorkspaceEntry) {
        val path = entry.path ?: entry.name ?: return
        val isDirectory = entry.type == "directory" || entry.type == "dir" || entry.type == "folder"
        if (isDirectory) {
            loadPath(path)
        } else {
            preview(path)
        }
    }

    fun openRoot(root: WorkspaceRoot) {
        loadPath(root.path)
    }

    fun goRoot() {
        loadPath(null)
    }

    fun loadPath(path: String?) {
        previewJob?.cancel()
        previewGeneration += 1
        val boundedPath = path?.let { value -> SavedStatePolicy.boundedInput(value) }
        savedStateHandle?.setBoundedString(SAVED_CURRENT_PATH, boundedPath)
        _state.update { it.copy(currentPath = boundedPath, preview = null, binaryPreview = null, isPreviewLoading = false) }
        refresh()
    }

    fun goUp() {
        val current = _state.value.currentPath?.trimEnd('/', '\\') ?: return
        val parent = current.substringBeforeLast('/', missingDelimiterValue = "")
            .ifBlank { current.substringBeforeLast('\\', missingDelimiterValue = "") }
            .ifBlank { null }
        loadPath(parent)
    }

    fun updateSearchText(value: String) {
        val boundedValue = SavedStatePolicy.boundedInput(value, SavedStatePolicy.MaximumSearchCharacters)
        savedStateHandle?.setBoundedString(SAVED_SEARCH_TEXT, boundedValue, SavedStatePolicy.MaximumSearchCharacters)
        _state.update { it.copy(searchText = boundedValue) }
    }

    fun reportError(message: String) {
        _state.update { it.copy(error = message) }
    }

    fun closePreview() {
        previewJob?.cancel()
        previewGeneration += 1
        _state.update { it.copy(preview = null, binaryPreview = null, isPreviewLoading = false) }
    }

    private fun preview(path: String) {
        previewJob?.cancel()
        val generation = ++previewGeneration
        previewJob = viewModelScope.launch {
            _state.update { it.copy(isPreviewLoading = true, error = null, preview = null, binaryPreview = null) }
            if (WorkspaceFilePreviewPolicy.isKnownUnsupportedBinary(path)) {
                if (generation != previewGeneration) return@launch
                _state.update {
                    it.copy(
                        binaryPreview = BinaryPreview(
                            path = path,
                            bytes = null,
                            isImage = false,
                            mimeType = WorkspaceFilePreviewPolicy.mimeType(path),
                        ),
                        isPreviewLoading = false,
                    )
                }
                return@launch
            }
            if (WorkspaceFilePreviewPolicy.shouldLoadRawPreview(path)) {
                loadBinaryPreview(path)
                return@launch
            }
            runSuspendCatching { repository.file(sessionId, path) }
                .onSuccess { preview ->
                    if (generation != previewGeneration) return@onSuccess
                    if (shouldRenderWorkspaceTextPreview(preview.content)) {
                        _state.update { it.copy(preview = preview, isPreviewLoading = false) }
                    } else {
                        _state.update {
                            it.copy(
                                binaryPreview = BinaryPreview(
                                    path = path,
                                    bytes = null,
                                    isImage = false,
                                    mimeType = WorkspaceFilePreviewPolicy.mimeType(path),
                                ),
                                isPreviewLoading = false,
                            )
                        }
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    if (generation != previewGeneration) return@onFailure
                    if (WorkspaceFilePreviewPolicy.isRasterImage(path)) {
                        loadBinaryPreview(path, generation)
                    } else {
                        _state.update { it.copy(isPreviewLoading = false, error = error.message ?: "Could not open file.") }
                    }
                }
        }
    }

    private suspend fun loadBinaryPreview(path: String, generation: Long = previewGeneration) {
        runSuspendCatching { repository.rawFile(sessionId, path) }
            .onSuccess { bytes ->
                if (generation != previewGeneration) return@onSuccess
                _state.update {
                    it.copy(
                        binaryPreview = BinaryPreview(
                            path = path,
                            bytes = bytes,
                            isImage = WorkspaceFilePreviewPolicy.isRasterImage(path),
                            mimeType = WorkspaceFilePreviewPolicy.mimeType(path),
                        ),
                        isPreviewLoading = false,
                    )
                }
            }
            .onFailure { error ->
                if (error is CancellationException) return@onFailure
                if (generation == previewGeneration) {
                    _state.update { it.copy(isPreviewLoading = false, error = error.message ?: "Could not open file.") }
                }
            }
    }

    private companion object {
        const val SAVED_CURRENT_PATH = "workspace_current_path"
        const val SAVED_SEARCH_TEXT = "workspace_search_text"
    }

}

internal fun shouldRenderWorkspaceTextPreview(content: String?): Boolean {
    val text = content ?: return false
    if ('\u0000' in text) return false
    if (text.isEmpty()) return true
    val suspicious = text.count { character ->
        (character.code < 32 && character != '\n' && character != '\r' && character != '\t') ||
            character == '\uFFFD'
    }
    return suspicious <= maxOf(8, text.length / 100)
}
