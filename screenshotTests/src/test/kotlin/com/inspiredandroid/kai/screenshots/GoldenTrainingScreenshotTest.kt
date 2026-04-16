@file:OptIn(ExperimentalVoiceApi::class)

package com.inspiredandroid.kai.screenshots

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.inspiredandroid.kai.ui.DarkColorScheme
import com.inspiredandroid.kai.ui.Theme
import com.inspiredandroid.kai.ui.dynamicui.KaiUiParser
import com.inspiredandroid.kai.ui.dynamicui.KaiUiRenderer
import nl.marc_apps.tts.experimental.ExperimentalVoiceApi
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.setResourceReaderAndroidContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Renders all golden fine-tuning training examples as screenshots.
 *
 * ## How to run
 *
 * ```
 * ./gradlew :screenshotTests:recordPaparazziDebug --tests "*GoldenTrainingScreenshotTest*"
 * ```
 *
 * Screenshots are saved to `screenshotTests/src/test/snapshots/images/`.
 */
@OptIn(ExperimentalResourceApi::class)
class GoldenTrainingScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_9A.copy(softButtons = false),
        showSystemUi = false,
        maxPercentDifference = 0.1,
    )

    @Before
    fun setup() {
        setResourceReaderAndroidContext(paparazzi.context)
    }

    // region Helpers --------------------------------------------------------------------------

    private fun Paparazzi.snap(
        colorScheme: ColorScheme = DarkColorScheme,
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

    private fun Paparazzi.snapKaiUi(json: String, wrapInCard: Boolean = true) {
        val ui = KaiUiParser.parseUiBlockBody(json) as KaiUiParser.UiBlockResult.Ui
        snap {
            KaiUiRenderer(
                node = ui.node,
                isInteractive = true,
                onCallback = { _, _ -> },
                modifier = Modifier.padding(12.dp),
                wrapInCard = wrapInCard,
            )
        }
    }

    /**
     * Extracts the kai-ui JSON from a golden markdown file's assistant response.
     * Returns the raw JSON inside the kai-ui fence.
     */
    private fun extractKaiUiJson(goldenFile: File): String {
        val content = goldenFile.readText()
        val assistantMatch = Regex("## Assistant\\n(.*?)$", RegexOption.DOT_MATCHES_ALL).find(content)
            ?: error("No ## Assistant section in ${goldenFile.name}")
        val assistantContent = assistantMatch.groupValues[1].trim()
        val fenceMatch = Regex("```kai-ui\\n(.*?)```", RegexOption.DOT_MATCHES_ALL).find(assistantContent)
            ?: error("No kai-ui fence in ${goldenFile.name}")
        return fenceMatch.groupValues[1].trim()
    }

    private fun goldenDir(): File {
        var dir = File(System.getProperty("user.dir"))
        while (dir.parentFile != null) {
            val candidate = File(dir, "tools/finetuning/golden")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        error("Could not find tools/finetuning/golden/ directory")
    }

    private fun goldenFile(name: String): File {
        val file = File(goldenDir(), name)
        check(file.exists()) { "Golden file not found: ${file.absolutePath}" }
        return file
    }

    // endregion

    // region Training example screenshots -----------------------------------------------------

    @Test
    fun golden_12_complex_booking() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("12-complex-booking.md")), wrapInCard = false)
    }

    @Test
    fun golden_23_emoji_icons() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("23-emoji-icons.md")), wrapInCard = false)
    }

    @Test
    fun golden_28_box_centered() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("28-box-centered.md")), wrapInCard = false)
    }

    @Test
    fun golden_29_divider_sections() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("29-divider-sections.md")))
    }

    @Test
    fun golden_31_dnd_game() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("31-dnd-game.md")), wrapInCard = false)
    }

    @Test
    fun golden_32_coding_challenge() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("32-coding-challenge.md")), wrapInCard = false)
    }

    @Test
    fun golden_33_smart_home() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("33-smart-home.md")), wrapInCard = false)
    }

    @Test
    fun golden_34_fitness_tracker() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("34-fitness-tracker.md")), wrapInCard = false)
    }

    @Test
    fun golden_35_budget_tracker() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("35-budget-tracker.md")), wrapInCard = false)
    }

    @Test
    fun golden_36_language_quiz() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("36-language-quiz.md")), wrapInCard = false)
    }

    @Test
    fun golden_37_travel_itinerary() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("37-travel-itinerary.md")), wrapInCard = false)
    }

    @Test
    fun golden_38_movie_browser() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("38-movie-browser.md")), wrapInCard = false)
    }

    @Test
    fun golden_40_product_comparison() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("40-product-comparison.md")), wrapInCard = false)
    }

    @Test
    fun golden_41_project_board() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("41-project-board.md")), wrapInCard = false)
    }

    @Test
    fun golden_42_poll_results() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("42-poll-results.md")), wrapInCard = false)
    }

    @Test
    fun golden_43_daily_planner() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("43-daily-planner.md")), wrapInCard = false)
    }

    @Test
    fun golden_44_callback_response() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("44-callback-response.md")), wrapInCard = false)
    }

    @Test
    fun golden_45_simple_decision() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("45-simple-decision.md")))
    }

    @Test
    fun golden_46_search_results() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("46-search-results.md")), wrapInCard = false)
    }

    @Test
    fun golden_47_confirmation_screen() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("47-confirmation-screen.md")), wrapInCard = false)
    }

    @Test
    fun golden_48_tutorial_lesson() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("48-tutorial-lesson.md")), wrapInCard = false)
    }

    @Test
    fun golden_49_wrong_answer() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("49-wrong-answer.md")), wrapInCard = false)
    }

    @Test
    fun golden_50_pros_cons() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("50-pros-cons.md")), wrapInCard = false)
    }

    @Test
    fun golden_52_news_digest() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("52-news-digest.md")), wrapInCard = false)
    }

    @Test
    fun golden_53_flashcard() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("53-flashcard.md")), wrapInCard = false)
    }

    @Test
    fun golden_54_gift_finder() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("54-gift-finder.md")))
    }

    @Test
    fun golden_55_session_summary() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("55-session-summary.md")), wrapInCard = false)
    }

    @Test
    fun golden_56_gift_results() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("56-gift-results.md")), wrapInCard = false)
    }

    @Test
    fun golden_57_meal_planner() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("57-meal-planner.md")), wrapInCard = false)
    }

    @Test
    fun golden_58_interview_prep() {
        paparazzi.snapKaiUi(extractKaiUiJson(goldenFile("58-interview-prep.md")), wrapInCard = false)
    }

    // endregion
}
