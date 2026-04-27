package com.inspiredandroid.kai.ui.sandbox

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.SandboxController
import com.inspiredandroid.kai.SandboxFileEntry
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.sandbox_files_open_failed
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
data class FileBrowserUiState(
    val currentPath: String = "/",
    val entries: List<SandboxFileEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val editor: EditorState? = null,
    val snackbarMessage: StringResource? = null,
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
        viewModelScope.launch {
            val entries = sandboxController.listDirectory(normalized)
            _state.update { it.copy(entries = entries, loading = false) }
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

    fun consumeSnackbar() {
        _state.update { it.copy(snackbarMessage = null) }
    }

    private fun normalize(path: String): String {
        if (path.isEmpty()) return "/"
        if (!path.startsWith("/")) return "/$path"
        if (path.length > 1 && path.endsWith("/")) return path.dropLast(1)
        return path
    }
}
