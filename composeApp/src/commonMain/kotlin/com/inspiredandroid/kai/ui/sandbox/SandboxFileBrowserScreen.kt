package com.inspiredandroid.kai.ui.sandbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inspiredandroid.kai.SandboxFileEntry
import com.inspiredandroid.kai.formatFileSize
import com.inspiredandroid.kai.ui.handCursor
import com.inspiredandroid.kai.ui.kaiAdaptiveCardBorder
import com.inspiredandroid.kai.ui.kaiAdaptiveCardColors
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel

private const val DEFAULT_INITIAL_PATH = "/root"

@Composable
fun SandboxFilesContent(
    modifier: Modifier = Modifier,
    initialPath: String = DEFAULT_INITIAL_PATH,
    viewModel: SandboxFileBrowserViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(initialPath) {
        viewModel.start(initialPath)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.snackbarMessage) {
        val resource = state.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(getString(resource))
        viewModel.consumeSnackbar()
    }

    Box(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            PathBar(
                currentPath = state.currentPath,
                editor = state.editor,
                onNavigateTo = viewModel::navigateTo,
                onSave = viewModel::save,
            )
            val editor = state.editor
            if (editor == null) {
                FileList(state = state, onOpen = viewModel::openEntry)
            } else {
                EditorBody(
                    editor = editor,
                    onChange = viewModel::updateEditorContent,
                    onOpenExternal = viewModel::openInExternalApp,
                    onLoadAsText = viewModel::loadAsText,
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        ) { Snackbar(snackbarData = it) }
    }
}

@Composable
private fun PathBar(
    currentPath: String,
    editor: EditorState?,
    onNavigateTo: (String) -> Unit,
    onSave: () -> Unit,
) {
    val editorPath = (editor as? EditorState.Loaded)?.path
        ?: (editor as? EditorState.Binary)?.path
    val editorFileName = remember(editorPath) { editorPath?.substringAfterLast('/') }
    val saveDirty = (editor as? EditorState.Loaded)?.dirty == true

    val segments = remember(currentPath) {
        val parts = currentPath.split("/").filter { it.isNotEmpty() }
        val acc = mutableListOf<Pair<String, String>>()
        acc += "/" to "/"
        var built = ""
        for (p in parts) {
            built = "$built/$p"
            acc += p to built
        }
        acc
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                segments.forEachIndexed { index, (label, target) ->
                    if (index > 0) Separator()
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onNavigateTo(target) }
                            .handCursor()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
                if (editorFileName != null) {
                    Separator()
                    Text(
                        text = editorFileName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
            if (editor is EditorState.Loaded) {
                TextButton(
                    onClick = onSave,
                    enabled = saveDirty,
                    modifier = Modifier.handCursor(),
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun Separator() {
    Text(
        text = "›",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun FileList(
    state: FileBrowserUiState,
    onOpen: (SandboxFileEntry) -> Unit,
) {
    if (state.loading && state.entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (state.error != null) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(state.error, color = MaterialTheme.colorScheme.error)
        }
        return
    }
    if (state.entries.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                "Empty directory",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(state.entries, key = { it.path }) { entry ->
            FileRow(entry = entry, onClick = { onOpen(entry) })
        }
    }
}

@Composable
private fun FileRow(entry: SandboxFileEntry, onClick: () -> Unit) {
    Card(
        colors = kaiAdaptiveCardColors(),
        border = kaiAdaptiveCardBorder(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .handCursor()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (entry.isDirectory) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!entry.isDirectory) {
                    Text(
                        text = formatFileSize(entry.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorBody(
    editor: EditorState,
    onChange: (String) -> Unit,
    onOpenExternal: (String) -> Unit,
    onLoadAsText: (String) -> Unit,
) {
    when (editor) {
        EditorState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is EditorState.Binary -> Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                "Binary or too large to preview as text.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onOpenExternal(editor.path) }, modifier = Modifier.handCursor()) {
                    Text("Open in app")
                }
                TextButton(onClick = { onLoadAsText(editor.path) }, modifier = Modifier.handCursor()) {
                    Text("Force open as text")
                }
            }
            Spacer(Modifier.weight(1f))
        }

        is EditorState.Error -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(editor.message, color = MaterialTheme.colorScheme.error)
        }

        is EditorState.Loaded -> Surface(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            OutlinedTextField(
                value = editor.current,
                onValueChange = onChange,
                modifier = Modifier.fillMaxSize(),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        }
    }
}
