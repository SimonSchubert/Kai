@file:OptIn(ExperimentalMaterial3Api::class)

package com.inspiredandroid.kai.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inspiredandroid.kai.Value
import com.inspiredandroid.kai.Version
import com.inspiredandroid.kai.outlineTextFieldColors
import com.inspiredandroid.kai.ui.settings.SettingsUiState.SettingsModel
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.github_mark
import kai.composeapp.generated.resources.ic_arrow_back
import kai.composeapp.generated.resources.ic_arrow_drop_down
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    onNavigateBack: () -> Unit = {},
) {
    val uiState by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding().statusBarsPadding().imePadding(), horizontalAlignment = CenterHorizontally) {
        TopBar(onNavigateBack = onNavigateBack)

        ServiceSelection(uiState.services, uiState.onClickService)

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 500.dp),
            horizontalAlignment = CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            if (uiState.services.find { it.isSelected }?.id == Value.SERVICE_GROQ) {
                GroqSettings(uiState)
            } else {
                GeminiSettings(uiState)
            }

            Spacer(Modifier.height(16.dp))

            Spacer(Modifier.weight(1f))

            BottomInfo()
        }
    }
}

@Composable
private fun TopBar(onNavigateBack: () -> Unit) {
    Row {
        IconButton(
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            onClick = onNavigateBack,
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_arrow_back),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun BottomInfo() {
    Text(
        text = "AI makes mistakes, double check and don't share sensitive information.",
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onBackground,
    )

    Spacer(Modifier.height(8.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Version: ${Version.appVersion}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.width(8.dp))

        val uriHandler = LocalUriHandler.current
        Icon(
            modifier = Modifier.clip(CircleShape).size(20.dp).clickable(onClick = {
                uriHandler.openUri("https://github.com/SimonSchubert/Kai")
            }),
            painter = painterResource(Res.drawable.github_mark),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun GeminiSettings(uiState: SettingsUiState) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = uiState.geminiApiKey,
        onValueChange = uiState.onChangeGeminiApiKey,
        label = {
            Text(
                "API Key",
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        colors = outlineTextFieldColors(),
    )

    Spacer(Modifier.height(8.dp))

    val annotatedString = remember {
        buildAnnotatedString {
            append("Sign in and copy your API key from ")
            withLink(LinkAnnotation.Url(url = "https://aistudio.google.com/apikey")) {
                withStyle(style = SpanStyle(color = Color.Blue)) {
                    append("aistudio.google.com/apikey")
                }
            }
        }
    }
    Text(
        annotatedString,
        color = MaterialTheme.colorScheme.onBackground,
    )

    Spacer(Modifier.height(16.dp))

    ModelSelection(uiState.geminiSelectedModel, uiState.geminiModels, uiState.onClickGeminiModel)
}

@Composable
private fun GroqSettings(uiState: SettingsUiState) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth().testTag("api_key"),
        value = uiState.groqApiKey,
        onValueChange = uiState.onChangeGroqApiKey,
        label = {
            Text(
                "API Key",
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        colors = outlineTextFieldColors(),
    )

    Spacer(Modifier.height(8.dp))

    val annotatedString = remember {
        buildAnnotatedString {
            append("Sign in and copy your API key from ")
            withLink(LinkAnnotation.Url(url = "https://console.groq.com/keys")) {
                withStyle(style = SpanStyle(color = Color.Blue)) {
                    append("console.groq.com/keys")
                }
            }
            append(" for 1000s of free daily requests.")
        }
    }
    Text(
        annotatedString,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onBackground,
    )

    Spacer(Modifier.height(16.dp))

    ModelSelection(uiState.groqSelectedModel, uiState.groqModels, uiState.onClickGroqModel)
}

@Composable
private fun ModelSelection(
    currentSelectedModel: SettingsModel?,
    models: List<SettingsModel>,
    onClick: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    if (models.isNotEmpty()) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = currentSelectedModel?.id ?: "",
            colors = outlineTextFieldColors(),
            onValueChange = {},
            label = {
                Text(
                    "Model",
                    color = MaterialTheme.colorScheme.onBackground,
                )
            },
            trailingIcon = {
                Icon(
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    imageVector = vectorResource(Res.drawable.ic_arrow_drop_down),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            },
            readOnly = true,
            interactionSource = remember { MutableInteractionSource() }
                .also { interactionSource ->
                    LaunchedEffect(interactionSource) {
                        interactionSource.interactions.collect {
                            if (it is PressInteraction.Release) {
                                expanded = true
                            }
                        }
                    }
                },
        )
        val focusManager = LocalFocusManager.current
        if (expanded) {
            ModalBottomSheet(
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                onDismissRequest = {
                    focusManager.clearFocus()
                    expanded = false
                },
            ) {
                LazyVerticalGrid(
                    GridCells.Adaptive(300.dp),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(models, key = { it.id }) { model ->
                        GroqModelCard(
                            model = model,
                            onClick = {
                                onClick(model.id)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroqModelCard(model: SettingsModel, onClick: () -> Unit) {
    Card(
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).clip(CardDefaults.shape).clickable { onClick() },
        shape = CardDefaults.shape,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = model.id,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = model.subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = model.description,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun ServiceSelection(services: List<SettingsUiState.Service>, onChanged: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow {
        services.forEachIndexed { index, service ->
            SegmentedButton(
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = services.size,
                ),
                onClick = { onChanged(service.id) },
                selected = service.isSelected,
                label = {
                    Text(
                        service.name,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
            )
        }
    }
}
