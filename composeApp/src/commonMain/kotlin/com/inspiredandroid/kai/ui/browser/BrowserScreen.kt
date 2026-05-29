package com.inspiredandroid.kai.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inspiredandroid.kai.currentPlatform
import com.inspiredandroid.kai.Platform
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isOverlayMinimized by remember { mutableStateOf(false) }
    var offsetX by remember { mutableStateOf(20f) }
    var offsetY by remember { mutableStateOf(400f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Browser") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Address Bar
                TextField(
                    value = uiState.url,
                    onValueChange = { viewModel.onUrlChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Enter URL") }
                )

                // WebView Area
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (currentPlatform is Platform.Mobile.Android) {
                        AndroidWebView(url = uiState.url)
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("WebView not supported on this platform yet.")
                        }
                    }
                }
            }

            // Floating AI Overlay
            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .width(if (isOverlayMinimized) 64.dp else 300.dp)
                    .then(
                        if (!isOverlayMinimized) Modifier.heightIn(max = 400.dp) else Modifier.height(64.dp)
                    )
                    .shadow(8.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    }
            ) {
                if (isOverlayMinimized) {
                    IconButton(
                        onClick = { isOverlayMinimized = false },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Show AI",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "AI Assistant",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { isOverlayMinimized = true }) {
                                Icon(Icons.Default.Close, contentDescription = "Minimize", modifier = Modifier.size(16.dp))
                            }
                        }

                        uiState.aiResponse?.let {
                            Box(modifier = Modifier.weight(1f, fill = false).padding(vertical = 4.dp)) {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            HorizontalDivider()
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            TextField(
                                value = uiState.aiPrompt,
                                onValueChange = { viewModel.onPromptChange(it) },
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodySmall,
                                placeholder = { Text("Ask AI...", style = MaterialTheme.typography.bodySmall) },
                                trailingIcon = {
                                    if (uiState.isAiThinking) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        IconButton(onClick = { viewModel.sendPrompt() }) {
                                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
expect fun AndroidWebView(url: String)
