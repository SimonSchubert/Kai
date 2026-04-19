@file:OptIn(ExperimentalVoiceApi::class)

package com.inspiredandroid.kai.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.inference.MODEL_CATALOG
import com.inspiredandroid.kai.ui.DarkColorScheme
import com.inspiredandroid.kai.ui.Theme
import com.inspiredandroid.kai.ui.chat.ChatScreenContent
import com.inspiredandroid.kai.ui.chat.ChatUiState
import com.inspiredandroid.kai.ui.chat.History
import com.inspiredandroid.kai.ui.settings.ConfiguredServiceEntry
import com.inspiredandroid.kai.ui.settings.ConnectionStatus
import com.inspiredandroid.kai.ui.settings.SettingsModel
import com.inspiredandroid.kai.ui.settings.SettingsScreenContent
import com.inspiredandroid.kai.ui.settings.SettingsTab
import com.inspiredandroid.kai.ui.settings.SettingsUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import nl.marc_apps.tts.experimental.ExperimentalVoiceApi
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.setResourceReaderAndroidContext
import org.jetbrains.compose.resources.vectorResource
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Screenshots for the /run-gemma-locally/ SEO landing page on kai9000.com.
 * Each test renders a specific state of the LiteRT settings card or the chat "Initializing" state.
 * Snapshots are copied to site/img/gemma-local-*.png by the updateScreenshots Gradle task.
 */
@OptIn(ExperimentalResourceApi::class)
class GemmaLocalScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_9A.copy(softButtons = false),
        showSystemUi = true,
        maxPercentDifference = 0.1,
    )

    @Before
    fun setup() {
        setResourceReaderAndroidContext(paparazzi.context)
    }

    private fun Paparazzi.snap(
        colorScheme: ColorScheme,
        content: @Composable () -> Unit,
    ) {
        val theme = if (colorScheme == DarkColorScheme) {
            "android:Theme.Material.NoActionBar"
        } else {
            "android:Theme.Material.Light.NoActionBar"
        }
        unsafeUpdateConfig(theme = theme)

        snapshot {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                Theme(colorScheme = colorScheme) {
                    content()
                }
            }
        }
    }

    @Test
    fun gemmaLocal_settings_dark() {
        paparazzi.snap(DarkColorScheme) {
            AddServiceSheetPreview(
                services = Service.all
                    .filter { it != Service.Free }
                    .sortedWith(
                        compareBy<Service> { !(it is Service.OpenAICompatible || it.isOnDevice) }
                            .thenBy { it.displayName },
                    )
                    .toImmutableList(),
            )
        }
    }

    @Test
    fun gemmaLocal_modelCard_dark() {
        paparazzi.snap(DarkColorScheme) {
            SettingsScreenContent(uiState = GemmaLocalTestData.services())
        }
    }

    @Test
    fun gemmaLocal_contextSlider_dark() {
        paparazzi.snap(DarkColorScheme) {
            SettingsScreenContent(
                uiState = GemmaLocalTestData.services(
                    modelContextTokens = mapOf(
                        MODEL_E2B.id to 16_384,
                        MODEL_E4B.id to 8_192,
                    ),
                ),
            )
        }
    }

    @Test
    fun gemmaLocal_download_dark() {
        paparazzi.snap(DarkColorScheme) {
            SettingsScreenContent(
                uiState = GemmaLocalTestData.services(
                    downloadingModelId = MODEL_E2B.id,
                    downloadProgress = 0.42f,
                ),
            )
        }
    }

    @Test
    fun gemmaLocal_select_dark() {
        paparazzi.snap(DarkColorScheme) {
            SettingsScreenContent(
                uiState = GemmaLocalTestData.services(
                    downloadedIds = listOf(MODEL_E2B.id),
                    selectedId = MODEL_E2B.id,
                ),
            )
        }
    }

    @Test
    fun gemmaLocal_chat_dark() {
        paparazzi.snap(DarkColorScheme) {
            ChatScreenContent(
                uiState = GemmaLocalTestData.chatInitializing,
                FakeTextToSpeechInstance(),
            )
        }
    }
}

private val MODEL_E2B = MODEL_CATALOG.first { it.id == "gemma-4-e2b-it" }
private val MODEL_E4B = MODEL_CATALOG.first { it.id == "gemma-4-e4b-it" }
private val MODEL_QWEN3 = MODEL_CATALOG.first { it.id == "qwen3-0.6b" }

/**
 * Mirrors the "Add service" ModalBottomSheet content in SettingsScreen. Production uses
 * `ModalBottomSheet`, which relies on runtime positioning and doesn't render in layoutlib.
 */
@Composable
private fun AddServiceSheetPreview(services: ImmutableList<Service>) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1D1B20)),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 16.dp)
                        .size(width = 32.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant),
                )
                services.forEachIndexed { index, service ->
                    val itemShape = RoundedCornerShape(
                        topStart = if (index == 0) 12.dp else 0.dp,
                        topEnd = if (index == 0) 12.dp else 0.dp,
                        bottomStart = if (index == services.lastIndex) 12.dp else 0.dp,
                        bottomEnd = if (index == services.lastIndex) 12.dp else 0.dp,
                    )
                    val isSpecial = service.isOnDevice || service is Service.OpenAICompatible
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = itemShape,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .then(
                                        if (isSpecial) {
                                            Modifier.background(
                                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                shape = RoundedCornerShape(8.dp),
                                            )
                                        } else {
                                            Modifier
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = vectorResource(service.icon),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = service.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private object GemmaLocalTestData {

    private const val LITERT_INSTANCE_ID = "litert"

    fun services(
        downloadedIds: List<String> = emptyList(),
        selectedId: String? = null,
        downloadingModelId: String? = null,
        downloadProgress: Float? = null,
        modelContextTokens: Map<String, Int> = emptyMap(),
    ): SettingsUiState {
        val downloadedModels = downloadedIds.map { id ->
            val source = MODEL_CATALOG.first { it.id == id }
            SettingsModel(
                id = source.id,
                subtitle = source.displayName,
                displayName = source.displayName,
                isSelected = selectedId == id,
            )
        }
        val selectedModel = selectedId?.let { id -> downloadedModels.firstOrNull { it.id == id } }

        return SettingsUiState(
            currentTab = SettingsTab.Services,
            configuredServices = persistentListOf(
                ConfiguredServiceEntry(
                    instanceId = LITERT_INSTANCE_ID,
                    service = Service.LiteRT,
                    connectionStatus = ConnectionStatus.Connected,
                    selectedModel = selectedModel,
                    models = downloadedModels.toImmutableList(),
                ),
            ),
            expandedServiceId = LITERT_INSTANCE_ID,
            localAvailableModels = persistentListOf(MODEL_E2B, MODEL_E4B, MODEL_QWEN3),
            // 8 GB keeps the Good/OK/Poor indicator in its middle bands across both models.
            totalDeviceMemoryBytes = 8L * 1024L * 1024L * 1024L,
            localFreeSpaceBytes = 24L * 1024L * 1024L * 1024L,
            localDownloadingModelId = downloadingModelId,
            localDownloadProgress = downloadProgress,
            modelContextTokens = modelContextTokens.toImmutableMap(),
        )
    }

    val chatInitializing = ChatUiState(
        actions = ScreenshotTestData.noOpChatActions,
        history = persistentListOf(
            History(
                id = "u1",
                role = History.Role.USER,
                content = "Summarize the last book I read in one paragraph.",
            ),
            History(
                id = "t1",
                role = History.Role.TOOL_EXECUTING,
                content = "",
                toolName = "Initializing ${MODEL_E2B.displayName}",
                isStatusMessage = true,
            ),
        ),
        isLoading = true,
    )
}
