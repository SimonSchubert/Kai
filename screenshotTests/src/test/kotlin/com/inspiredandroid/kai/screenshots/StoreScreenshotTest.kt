@file:OptIn(ExperimentalVoiceApi::class)

package com.inspiredandroid.kai.screenshots

import android.graphics.BitmapFactory
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalInspectionMode
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.inspiredandroid.kai.ui.DarkColorScheme
import com.inspiredandroid.kai.ui.LightColorScheme
import com.inspiredandroid.kai.ui.Theme
import com.inspiredandroid.kai.ui.chat.ChatScreenContent
import com.inspiredandroid.kai.ui.dynamicui.LocalPreviewImages
import com.inspiredandroid.kai.ui.settings.SettingsScreenContent
import nl.marc_apps.tts.experimental.ExperimentalVoiceApi
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.setResourceReaderAndroidContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.Locale

@RunWith(Parameterized::class)
class StoreScreenshotTest(
    private val locale: String,
    private val playStoreLocale: String,
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{1}")
        fun locales() = StoreLocales.all
    }

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_9A.copy(softButtons = false),
        showSystemUi = true,
        useDeviceResolution = true,
    )

    private lateinit var originalLocale: Locale
    private val previewImages = mutableMapOf<String, ImageBitmap>()

    @OptIn(ExperimentalResourceApi::class)
    @Before
    fun setup() {
        originalLocale = Locale.getDefault()
        val newLocale = if (locale.contains("-")) {
            val parts = locale.split("-")
            Locale(parts[0], parts[1])
        } else {
            Locale(locale)
        }
        Locale.setDefault(newLocale)

        val deviceLocale = if (locale.contains("-")) {
            val parts = locale.split("-")
            "${parts[0]}-r${parts[1]}"
        } else {
            locale
        }
        paparazzi.unsafeUpdateConfig(
            deviceConfig = DeviceConfig.PIXEL_9A.copy(
                softButtons = false,
                locale = deviceLocale,
            ),
        )
        setResourceReaderAndroidContext(paparazzi.context)
        loadPreviewImage("resource://orc_survival.png", "/orc_survival.png")
        loadPreviewImage("resource://cacio_e_pepe.png", "/cacio_e_pepe.png")
    }

    private fun loadPreviewImage(key: String, resourcePath: String) {
        val bitmap = BitmapFactory.decodeStream(javaClass.getResourceAsStream(resourcePath)) ?: return
        previewImages[key] = bitmap.asImageBitmap()
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    private fun snap(
        name: String,
        colorScheme: ColorScheme,
        content: @Composable () -> Unit,
    ) {
        val theme = if (colorScheme == DarkColorScheme) {
            "android:Theme.Material.NoActionBar"
        } else {
            "android:Theme.Material.Light.NoActionBar"
        }
        paparazzi.unsafeUpdateConfig(theme = theme)
        paparazzi.snapshot(name = "store_${playStoreLocale}_$name") {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalPreviewImages provides previewImages,
            ) {
                Theme(colorScheme = colorScheme) {
                    content()
                }
            }
        }
    }

    @Test
    fun chatEmptyState() {
        snap("01", LightColorScheme) {
            ChatScreenContent(
                uiState = ScreenshotTestData.chatEmptyState,
                FakeTextToSpeechInstance(),
            )
        }
    }

    @Test
    fun chatWithMessages() {
        snap("02", DarkColorScheme) {
            ChatScreenContent(
                uiState = ScreenshotTestData.localizedChatWithMessages(locale),
                FakeTextToSpeechInstance(),
            )
        }
    }

    @Test
    fun chatWithDynamicUi() {
        snap("03", LightColorScheme) {
            ChatScreenContent(
                uiState = ScreenshotTestData.localizedChatWithDynamicUi(locale),
                FakeTextToSpeechInstance(),
            )
        }
    }

    @Test
    fun settingsFree() {
        snap("04", DarkColorScheme) {
            SettingsScreenContent(
                uiState = ScreenshotTestData.freeConnected,
                terminalPreviewLines = ScreenshotTestData.sandboxTerminalLines,
            )
        }
    }

    @Test
    fun settingsTools() {
        snap("05", LightColorScheme) {
            SettingsScreenContent(
                uiState = ScreenshotTestData.settingsTools,
                terminalPreviewLines = ScreenshotTestData.sandboxTerminalLines,
            )
        }
    }

    @Test
    fun settingsAgent() {
        snap("06", DarkColorScheme) {
            SettingsScreenContent(
                uiState = ScreenshotTestData.settingsAgent,
                terminalPreviewLines = ScreenshotTestData.sandboxTerminalLines,
            )
        }
    }

    @Test
    fun settingsSandbox() {
        snap("07", DarkColorScheme) {
            SettingsScreenContent(
                uiState = ScreenshotTestData.settingsSandbox,
                sandboxState = ScreenshotTestData.sandboxState,
                terminalPreviewLines = ScreenshotTestData.sandboxTerminalLines,
            )
        }
    }
}
