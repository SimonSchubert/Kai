package com.inspiredandroid.kai.ui.sandbox

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.SandboxController
import com.inspiredandroid.kai.SandboxFileEntry
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.sandbox_files_delete_failed
import kai.composeapp.generated.resources.sandbox_files_delete_success
import kai.composeapp.generated.resources.sandbox_files_editor_closed_after_delete
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
    data class Loaded(val path: String, val original: String, val current: String) : EditorState {
        val dirty: Boolean get() = original != current
    }

    data class Binary(val path: String) : EditorState
    data class Error(val path: String, val message: String) : EditorState
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
)

class SandboxFileBrowserViewModel(
    private val sandboxController: SandboxController,
) : ViewModel() {

    private val _state = MutableStateFlow(FileBrowserUiState())
    val state = _state.asStateFlow()

    fun start(initialPath: String) {
        if (_state.value.entries.isNotEmpty() && _state.value.currentPath == initialPath) return
        navigateTo(initialPath)
    }

    fun navigateTo(path: String) {
        val normalized = normalize(path)
        val current = _state.value
        if (current.currentPath == normalized && current.entries.isNotEmpty()) {
            if (current.editor != null) _state.update { it.copy(editor = null) }
            return
        }
        _state.update { it.copy(currentPath = normalized, loading = true, error = null, editor = null) }
        viewModelScope.launch { refreshCurrent() }
    }

    private suspend fun refreshCurrent() {
        val path = _state.value.currentPath
        val entries = sandboxController.listDirectory(path)
        _state.update { it.copy(entries = entries, loading = false) }
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
                val result = sandboxController.openFile(entry.path)
                if (result.isSuccess) return@launch
            }
            loadInEditor(entry.path)
        }
    }

    fun openInExternalApp(path: String) {
        viewModelScope.launch {
            val result = sandboxController.openFile(path)
            if (result.isFailure) {
                _state.update { it.copy(snackbarMessage = Res.string.sandbox_files_open_failed) }
            }
        }
    }

    fun loadAsText(path: String) {
        viewModelScope.launch {
            loadInEditor(path)
        }
    }

    private suspend fun loadInEditor(path: String) {
        _state.update { it.copy(editor = EditorState.Loading) }
        val text = sandboxController.readTextFile(path)
        _state.update {
            it.copy(
                editor = if (text != null) {
                    EditorState.Loaded(path = path, original = text, current = text)
                } else {
                    EditorState.Binary(path)
                },
            )
        }
    }

    fun updateEditorContent(content: String) {
        _state.update { state ->
            val editor = state.editor
            if (editor is EditorState.Loaded) {
                state.copy(editor = editor.copy(current = content))
            } else {
                state
            }
        }
    }

    fun save() {
        val editor = _state.value.editor as? EditorState.Loaded ?: return
        viewModelScope.launch {
            val ok = sandboxController.writeTextFile(editor.path, editor.current)
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
            val ok = sandboxController.deleteEntry(entry.path, recursive = entry.isDirectory)
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
            val result = sandboxController.renameEntry(entry.path, newName)
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

    fun consumeSnackbar() {
        _state.update { it.copy(snackbarMessage = null) }
    }

    private fun editorPathOf(editor: EditorState?): String? = when (editor) {
        is EditorState.Loaded -> editor.path
        is EditorState.Binary -> editor.path
        is EditorState.Error -> editor.path
        else -> null
    }

    private fun normalize(path: String): String {
        if (path.isEmpty()) return "/"
        if (!path.startsWith("/")) return "/$path"
        if (path.length > 1 && path.endsWith("/")) return path.dropLast(1)
        return path
    }
}
