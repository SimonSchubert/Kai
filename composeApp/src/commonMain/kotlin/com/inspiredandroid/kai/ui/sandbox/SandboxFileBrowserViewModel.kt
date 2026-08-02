package com.inspiredandroid.kai.ui.sandbox

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.FileBrowserSource
import com.inspiredandroid.kai.SandboxFileEntry
import com.inspiredandroid.kai.TextFileResult
import io.github.vinceglb.filekit.PlatformFile
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.sandbox_files_delete_failed
import kai.composeapp.generated.resources.sandbox_files_delete_success
import kai.composeapp.generated.resources.sandbox_files_editor_closed_after_delete
import kai.composeapp.generated.resources.sandbox_files_import_failed
import kai.composeapp.generated.resources.sandbox_files_import_success
import kai.composeapp.generated.resources.sandbox_files_open_failed
import kai.composeapp.generated.resources.sandbox_files_rename_error_collision
import kai.composeapp.generated.resources.sandbox_files_rename_error_invalid
import kai.composeapp.generated.resources.sandbox_files_rename_failed
import kai.composeapp.generated.resources.sandbox_files_rename_success
import kai.composeapp.generated.resources.sandbox_files_save_failed
import kai.composeapp.generated.resources.sandbox_files_save_success
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

private val TEXT_EXTENSIONS = setOf(
    "txt", "md", "log", "conf", "cfg", "ini", "sh", "bash", "py", "json",
    "yaml", "yml", "kt", "kts", "java", "xml", "html", "htm", "css", "js",
    "ts", "csv", "toml", "properties", "gradle", "rb", "go", "c", "h", "cpp",
)

@Immutable
sealed interface EditorState {
    data object Loading : EditorState

    /**
     * [readOnly] marks a buffer that was decoded lossily on the user's request. It can be
     * looked at but never saved — writing it back would replace the file's real bytes
     * with the replacement characters the decoder substituted.
     */
    data class Loaded(
        val path: String,
        val original: String,
        val current: String,
        val readOnly: Boolean = false,
    ) : EditorState {
        val dirty: Boolean get() = !readOnly && original != current
    }

    /** Not valid UTF-8. Offers a forced, read-only decode. */
    data class Binary(val path: String) : EditorState

    /** Past the editor's cap — no forced read, since a truncated buffer can't be edited safely. */
    data class TooLarge(val path: String, val sizeBytes: Long) : EditorState

    data class Unreadable(val path: String) : EditorState
}

@Immutable
data class RenameState(
    val originalEntry: SandboxFileEntry,
    val input: String,
    val error: StringResource? = null,
)

@Immutable
data class FileBrowserUiState(
    val currentPath: String = "/",
    val entries: List<SandboxFileEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val editor: EditorState? = null,
    val snackbarMessage: StringResource? = null,
    val pendingDelete: SandboxFileEntry? = null,
    val renaming: RenameState? = null,
    val importing: Boolean = false,
)

class SandboxFileBrowserViewModel(
    private val files: FileBrowserSource,
) : ViewModel() {

    private val _state = MutableStateFlow(FileBrowserUiState())
    val state = _state.asStateFlow()

    /**
     * Called every time the browser becomes visible. The agent mutates the sandbox
     * behind our back, so re-entering an already-loaded directory re-lists it
     * instead of serving the cache.
     */
    fun start(initialPath: String) {
        val normalized = normalize(initialPath)
        val current = _state.value
        if (current.currentPath != normalized || current.entries.isEmpty()) {
            navigateTo(normalized)
            return
        }
        viewModelScope.launch {
            refreshCurrent(silent = true)
            reloadEditorIfClean()
        }
    }

    fun navigateTo(path: String) {
        val normalized = normalize(path)
        val current = _state.value
        // Already showing this directory: keep the entries on screen and re-list
        // underneath, so re-tapping a breadcrumb doesn't flash a spinner.
        val samePath = current.currentPath == normalized && current.entries.isNotEmpty()
        _state.update { it.copy(currentPath = normalized, loading = !samePath, error = null, editor = null) }
        viewModelScope.launch { refreshCurrent(silent = samePath) }
    }

    /**
     * A [silent] refresh leaves the state instance untouched when the listing is
     * unchanged. That matters: an equal state is conflated by the StateFlow, so the
     * list never recomposes and its scroll position cannot shift.
     */
    private suspend fun refreshCurrent(silent: Boolean = false) {
        val path = _state.value.currentPath
        val entries = files.listDirectory(path)
        _state.update {
            when {
                // Navigated away while listing — the newer load owns the state.
                it.currentPath != path -> it

                silent && it.entries == entries -> it

                else -> it.copy(entries = entries, loading = false)
            }
        }
    }

    /**
     * Picks up agent edits to the file currently open in the editor. Unsaved user
     * edits always win — reloading over them would lose work, and saving a stale
     * buffer afterwards would clobber what the agent wrote.
     */
    private suspend fun reloadEditorIfClean() {
        val editor = _state.value.editor as? EditorState.Loaded ?: return
        if (editor.dirty || editor.readOnly) return
        val text = (files.readTextFile(editor.path) as? TextFileResult.Text)?.content ?: return
        if (text == editor.original) return
        _state.update {
            val latest = it.editor
            if (latest is EditorState.Loaded && latest.path == editor.path && !latest.dirty) {
                it.copy(editor = latest.copy(original = text, current = text))
            } else {
                it
            }
        }
    }

    fun openEntry(entry: SandboxFileEntry) {
        if (entry.isDirectory) {
            navigateTo(entry.path)
            return
        }
        viewModelScope.launch {
            val ext = entry.name.substringAfterLast('.', "").lowercase()
            val preferText = ext in TEXT_EXTENSIONS
            if (!preferText) {
                val result = files.openFile(entry.path)
                if (result.isSuccess) return@launch
            }
            loadInEditor(entry.path)
        }
    }

    fun openInExternalApp(path: String) {
        viewModelScope.launch {
            val result = files.openFile(path)
            if (result.isFailure) {
                _state.update { it.copy(snackbarMessage = Res.string.sandbox_files_open_failed) }
            }
        }
    }

    /** The escape hatch from [EditorState.Binary]: decode the bytes anyway, read-only. */
    fun loadAsText(path: String) {
        viewModelScope.launch {
            loadInEditor(path, force = true)
        }
    }

    private suspend fun loadInEditor(path: String, force: Boolean = false) {
        _state.update { it.copy(editor = EditorState.Loading) }
        val editor = when (val result = files.readTextFile(path, force = force)) {
            is TextFileResult.Text -> EditorState.Loaded(
                path = path,
                original = result.content,
                current = result.content,
                readOnly = !result.editable,
            )

            TextFileResult.Binary -> EditorState.Binary(path)

            is TextFileResult.TooLarge -> EditorState.TooLarge(path, result.sizeBytes)

            TextFileResult.Unreadable -> EditorState.Unreadable(path)
        }
        _state.update { it.copy(editor = editor) }
    }

    fun updateEditorContent(content: String) {
        _state.update { state ->
            val editor = state.editor
            if (editor is EditorState.Loaded && !editor.readOnly) {
                state.copy(editor = editor.copy(current = content))
            } else {
                state
            }
        }
    }

    fun save() {
        val editor = _state.value.editor as? EditorState.Loaded ?: return
        if (editor.readOnly) return
        viewModelScope.launch {
            val ok = files.writeTextFile(editor.path, editor.current)
            if (ok) {
                _state.update {
                    it.copy(
                        editor = editor.copy(original = editor.current),
                        snackbarMessage = Res.string.sandbox_files_save_success,
                    )
                }
            } else {
                _state.update { it.copy(snackbarMessage = Res.string.sandbox_files_save_failed) }
            }
        }
    }

    fun requestDelete(entry: SandboxFileEntry) {
        _state.update { it.copy(pendingDelete = entry) }
    }

    fun cancelDelete() {
        _state.update { it.copy(pendingDelete = null) }
    }

    fun confirmDelete() {
        val entry = _state.value.pendingDelete ?: return
        _state.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            val ok = files.deleteEntry(entry.path, recursive = entry.isDirectory)
            if (ok) {
                val editor = _state.value.editor
                val editorPath = editorPathOf(editor)
                val editorClosed = editorPath != null && editorPath == entry.path
                val snackbar = if (editorClosed) {
                    Res.string.sandbox_files_editor_closed_after_delete
                } else {
                    Res.string.sandbox_files_delete_success
                }
                _state.update {
                    it.copy(
                        editor = if (editorClosed) null else it.editor,
                        snackbarMessage = snackbar,
                    )
                }
                refreshCurrent()
            } else {
                _state.update { it.copy(snackbarMessage = Res.string.sandbox_files_delete_failed) }
            }
        }
    }

    fun requestRename(entry: SandboxFileEntry) {
        _state.update { it.copy(renaming = RenameState(originalEntry = entry, input = entry.name)) }
    }

    fun updateRenameInput(value: String) {
        _state.update { state ->
            val rename = state.renaming ?: return@update state
            state.copy(renaming = rename.copy(input = value, error = null))
        }
    }

    fun cancelRename() {
        _state.update { it.copy(renaming = null) }
    }

    fun confirmRename() {
        val rename = _state.value.renaming ?: return
        val entry = rename.originalEntry
        val newName = rename.input.trim()
        if (newName.isEmpty() || newName == entry.name ||
            newName.contains('/') || newName.contains('\\') ||
            newName == "." || newName == ".."
        ) {
            if (newName == entry.name) {
                _state.update { it.copy(renaming = null) }
            } else {
                _state.update {
                    it.copy(renaming = rename.copy(error = Res.string.sandbox_files_rename_error_invalid))
                }
            }
            return
        }
        viewModelScope.launch {
            val result = files.renameEntry(entry.path, newName)
            result.fold(
                onSuccess = { newPath ->
                    val editor = _state.value.editor
                    val updatedEditor = if (editor is EditorState.Loaded && editor.path == entry.path) {
                        editor.copy(path = newPath)
                    } else if (editor is EditorState.Binary && editor.path == entry.path) {
                        EditorState.Binary(newPath)
                    } else {
                        editor
                    }
                    _state.update {
                        it.copy(
                            renaming = null,
                            editor = updatedEditor,
                            snackbarMessage = Res.string.sandbox_files_rename_success,
                        )
                    }
                    refreshCurrent()
                },
                onFailure = { e ->
                    val message = e.message
                    val errorRes = when {
                        message == "collision" -> Res.string.sandbox_files_rename_error_collision
                        e is IllegalArgumentException -> Res.string.sandbox_files_rename_error_invalid
                        else -> null
                    }
                    if (errorRes != null) {
                        _state.update {
                            it.copy(renaming = rename.copy(error = errorRes))
                        }
                    } else {
                        _state.update {
                            it.copy(
                                renaming = null,
                                snackbarMessage = Res.string.sandbox_files_rename_failed,
                            )
                        }
                    }
                },
            )
        }
    }

    /**
     * Copies a file picked from device storage into the directory on screen. Until this
     * existed the only way into the sandbox was the agent's shell.
     */
    fun importFile(source: PlatformFile) {
        if (_state.value.importing) return
        val directory = _state.value.currentPath
        _state.update { it.copy(importing = true) }
        viewModelScope.launch {
            val result = files.importFile(directory, source)
            _state.update {
                it.copy(
                    importing = false,
                    snackbarMessage = if (result.isSuccess) {
                        Res.string.sandbox_files_import_success
                    } else {
                        Res.string.sandbox_files_import_failed
                    },
                )
            }
            // The listing only gains a row when the import landed in the directory the
            // user is still looking at; refreshCurrent is a no-op otherwise.
            if (result.isSuccess) refreshCurrent()
        }
    }

    fun consumeSnackbar() {
        _state.update { it.copy(snackbarMessage = null) }
    }

    private fun editorPathOf(editor: EditorState?): String? = when (editor) {
        is EditorState.Loaded -> editor.path
        is EditorState.Binary -> editor.path
        is EditorState.TooLarge -> editor.path
        is EditorState.Unreadable -> editor.path
        else -> null
    }

    private fun normalize(path: String): String {
        if (path.isEmpty()) return "/"
        if (!path.startsWith("/")) return "/$path"
        if (path.length > 1 && path.endsWith("/")) return path.dropLast(1)
        return path
    }
}
