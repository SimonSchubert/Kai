@file:OptIn(ExperimentalVoiceApi::class)

package com.inspiredandroid.kai.screenshots

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.inspiredandroid.kai.ui.DarkColorScheme
import com.inspiredandroid.kai.ui.LightColorScheme
import com.inspiredandroid.kai.ui.Theme
import com.inspiredandroid.kai.ui.chat.ChatScreenContent
import com.inspiredandroid.kai.ui.chat.ChatUiState
import com.inspiredandroid.kai.ui.chat.History
import com.inspiredandroid.kai.ui.dynamicui.KaiUiParser
import com.inspiredandroid.kai.ui.dynamicui.KaiUiRenderer
import com.inspiredandroid.kai.ui.dynamicui.LocalPreviewImages
import kotlinx.collections.immutable.persistentListOf
import nl.marc_apps.tts.experimental.ExperimentalVoiceApi
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.setResourceReaderAndroidContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Screenshot tests for all kai-ui component types and realistic screen scenarios.
 */
@OptIn(ExperimentalResourceApi::class)
class KaiUiScreenshotTest {

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

    private fun Paparazzi.snapKaiUi(
        json: String,
        colorScheme: ColorScheme = DarkColorScheme,
        wrapInCard: Boolean = true,
    ) {
        val segments = KaiUiParser.parse("```kai-ui\n$json\n```")
        val uiSegment = segments.filterIsInstance<KaiUiParser.UiSegment>().first()
        snap(colorScheme) {
            KaiUiRenderer(
                node = uiSegment.node,
                isInteractive = true,
                onCallback = { _, _ -> },
                modifier = Modifier.padding(12.dp),
                wrapInCard = wrapInCard,
            )
        }
    }

    private fun Paparazzi.snapKaiUiWithImage(
        json: String,
        imageResource: String,
        imageUrl: String = "preview://image",
        colorScheme: ColorScheme = DarkColorScheme,
    ) {
        val node = KaiUiParser.parse("```kai-ui\n$json\n```")
            .filterIsInstance<KaiUiParser.UiSegment>().first()
        val bitmap = BitmapFactory.decodeStream(javaClass.getResourceAsStream(imageResource))
        val imageBitmap = bitmap.asImageBitmap()
        snap(colorScheme) {
            CompositionLocalProvider(LocalPreviewImages provides mapOf(imageUrl to imageBitmap)) {
                KaiUiRenderer(
                    node = node.node,
                    isInteractive = true,
                    onCallback = { _, _ -> },
                    modifier = Modifier.padding(12.dp),
                    wrapInCard = true,
                )
            }
        }
    }

    private fun Paparazzi.snapFullScreen(
        colorScheme: ColorScheme,
        content: @Composable () -> Unit,
    ) {
        val theme = if (colorScheme == DarkColorScheme) {
            "android:Theme.Material.NoActionBar"
        } else {
            "android:Theme.Material.Light.NoActionBar"
        }
        unsafeUpdateConfig(
            deviceConfig = DeviceConfig.PIXEL_9A.copy(softButtons = false),
            theme = theme,
        )

        snapshot {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                Theme(colorScheme = colorScheme) {
                    content()
                }
            }
        }
    }

    // --- Text styles ---

    @Test
    fun textStyles() {
        paparazzi.snapKaiUi(
            """{"type":"column","children":[
                {"type":"text","value":"Headline Text","style":"headline","bold":true},
                {"type":"text","value":"Title Text","style":"title"},
                {"type":"text","value":"Body text with normal styling. This is the default text style used for paragraphs.","style":"body"},
                {"type":"text","value":"Caption text - smaller, secondary information","style":"caption"},
                {"type":"text","value":"Bold text","bold":true},
                {"type":"text","value":"Italic text","italic":true},
                {"type":"text","value":"Colored text","color":"primary"}
            ]}""",
        )
    }

    // --- Buttons ---

    @Test
    fun buttonVariants() {
        paparazzi.snapKaiUi(
            """{"type":"column","children":[
                {"type":"text","value":"Button Variants","style":"title","bold":true},
                {"type":"button","label":"Filled Button","variant":"filled","action":{"type":"callback","event":"click"}},
                {"type":"button","label":"Outlined Button","variant":"outlined","action":{"type":"callback","event":"click"}},
                {"type":"button","label":"Text Button","variant":"text","action":{"type":"callback","event":"click"}},
                {"type":"button","label":"Tonal Button","variant":"tonal","action":{"type":"callback","event":"click"}},
                {"type":"button","label":"Disabled Button","variant":"filled","enabled":false}
            ]}""",
        )
    }

    // --- Form inputs ---

    @Test
    fun formInputs() {
        paparazzi.snapKaiUi(
            """{"type":"column","children":[
                {"type":"text","value":"Form Inputs","style":"title","bold":true},
                {"type":"text_input","id":"name","label":"Full Name","placeholder":"Enter your name"},
                {"type":"text_input","id":"bio","label":"Bio","placeholder":"Tell us about yourself","multiline":true},
                {"type":"checkbox","id":"agree","label":"I agree to the terms"},
                {"type":"switch","id":"notifications","label":"Enable notifications"},
                {"type":"select","id":"country","label":"Country","options":["USA","Germany","Japan","Brazil"],"selected":"Germany"},
                {"type":"radio_group","id":"size","label":"Size","options":["Small","Medium","Large"],"selected":"Medium"},
                {"type":"slider","id":"volume","label":"Volume","min":0,"max":100,"value":65,"step":5}
            ]}""",
        )
    }

    // --- Chips ---

    @Test
    fun chipGroup() {
        paparazzi.snapKaiUi(
            """{"type":"column","children":[
                {"type":"text","value":"Single Select","style":"title"},
                {"type":"chip_group","id":"mood","chips":[
                    {"label":"Happy","value":"happy"},
                    {"label":"Neutral","value":"neutral"},
                    {"label":"Sad","value":"sad"}
                ]},
                {"type":"text","value":"Multi Select","style":"title"},
                {"type":"chip_group","id":"tags","selection":"multi","chips":[
                    {"label":"Kotlin","value":"kotlin"},
                    {"label":"Swift","value":"swift"},
                    {"label":"Rust","value":"rust"},
                    {"label":"TypeScript","value":"ts"}
                ]}
            ]}""",
        )
    }

    // --- Feedback ---

    @Test
    fun feedbackElements() {
        paparazzi.snapKaiUi(
            """{"type":"column","children":[
                {"type":"text","value":"Feedback","style":"title","bold":true},
                {"type":"progress","label":"Upload progress","value":0.7},
                {"type":"progress","label":"Indeterminate"},
                {"type":"countdown","seconds":3723,"label":"Time remaining"},
                {"type":"alert","severity":"info","title":"Info","message":"This is an informational alert."},
                {"type":"alert","severity":"success","title":"Success","message":"Operation completed successfully!"},
                {"type":"alert","severity":"warning","title":"Warning","message":"Please review before continuing."},
                {"type":"alert","severity":"error","title":"Error","message":"Something went wrong."}
            ]}""",
        )
    }

    // --- Layout ---

    @Test
    fun layoutElements() {
        paparazzi.snapKaiUi(
            """{"type":"column","children":[
                {"type":"text","value":"Cards & Layout","style":"title","bold":true},
                {"type":"card","children":[
                    {"type":"text","value":"Card Title","style":"title"},
                    {"type":"text","value":"Cards group related content together.","style":"body"}
                ]},
                {"type":"divider"},
                {"type":"row","children":[
                    {"type":"icon","name":"star","color":"primary"},
                    {"type":"text","value":"Row with icon and text","style":"body"},
                    {"type":"spacer"},
                    {"type":"icon","name":"arrow_forward"}
                ]},
                {"type":"box","contentAlignment":"center","children":[
                    {"type":"text","value":"Centered in a Box","style":"caption"}
                ]}
            ]}""",
        )
    }

    // --- Icons ---

    @Test
    fun icons() {
        paparazzi.snapKaiUi(
            """{"type":"column","children":[
                {"type":"text","value":"Icons","style":"title","bold":true},
                {"type":"row","children":[
                    {"type":"icon","name":"home"},
                    {"type":"icon","name":"search"},
                    {"type":"icon","name":"settings"},
                    {"type":"icon","name":"person"},
                    {"type":"icon","name":"favorite","color":"error"},
                    {"type":"icon","name":"star","color":"primary"},
                    {"type":"icon","name":"notifications"},
                    {"type":"icon","name":"email"}
                ]},
                {"type":"row","children":[
                    {"type":"icon","name":"edit"},
                    {"type":"icon","name":"delete","color":"error"},
                    {"type":"icon","name":"share"},
                    {"type":"icon","name":"info"},
                    {"type":"icon","name":"warning","color":"warning"},
                    {"type":"icon","name":"check","color":"success"},
                    {"type":"icon","name":"close"},
                    {"type":"icon","name":"refresh"}
                ]}
            ]}""",
        )
    }

    // --- Code block ---

    @Test
    fun codeBlock() {
        paparazzi.snapKaiUi(
            """{"type":"column","children":[
                {"type":"text","value":"Code Block","style":"title","bold":true},
                {"type":"code","language":"kotlin","code":"fun greet(name: String): String {\n    return \"Hello, ${'$'}name!\"\n}"}
            ]}""",
        )
    }

    // --- Navigation: Tabs ---

    @Test
    fun tabs() {
        paparazzi.snapKaiUi(
            """{"type":"tabs","tabs":[
                {"label":"Overview","children":[
                    {"type":"text","value":"Welcome to the overview tab.","style":"body"},
                    {"type":"text","value":"This shows general information.","style":"caption"}
                ]},
                {"label":"Details","children":[
                    {"type":"text","value":"Detailed information goes here.","style":"body"}
                ]},
                {"label":"Settings","children":[
                    {"type":"switch","id":"dark","label":"Dark mode"}
                ]}
            ],"selectedIndex":0}""",
        )
    }

    // --- Navigation: Accordion ---

    @Test
    fun accordion() {
        paparazzi.snapKaiUi(
            """{"type":"column","children":[
                {"type":"text","value":"FAQ","style":"title","bold":true},
                {"type":"accordion","title":"What is Kai?","expanded":true,"children":[
                    {"type":"text","value":"Kai is a personal AI assistant that remembers your preferences and gets things done.","style":"body"}
                ]},
                {"type":"accordion","title":"How does memory work?","children":[
                    {"type":"text","value":"Kai stores key facts about you locally and recalls them in future conversations.","style":"body"}
                ]},
                {"type":"accordion","title":"Is my data private?","children":[
                    {"type":"text","value":"Yes. All memory is stored on-device and never shared.","style":"body"}
                ]}
            ]}""",
        )
    }

    // --- Data: Table ---

    @Test
    fun table() {
        paparazzi.snapKaiUi(
            """{"type":"column","children":[
                {"type":"text","value":"Leaderboard","style":"title","bold":true},
                {"type":"table","headers":["Rank","Player","Score","Level"],
                    "rows":[
                        ["1","Alice","9850","42"],
                        ["2","Bob","8720","38"],
                        ["3","Charlie","7630","35"],
                        ["4","Diana","6540","31"]
                    ]
                }
            ]}""",
        )
    }

    // --- Data: List ---

    @Test
    fun list() {
        paparazzi.snapKaiUi(
            """{"type":"column","children":[
                {"type":"text","value":"Shopping List","style":"title","bold":true},
                {"type":"list","ordered":true,"items":[
                    {"type":"text","value":"Milk"},
                    {"type":"text","value":"Eggs"},
                    {"type":"text","value":"Bread"},
                    {"type":"text","value":"Butter"}
                ]}
            ]}""",
        )
    }

    // --- Scenario: Quiz with timer expired ---

    @Test
    fun scenario_quizTimerExpired_dark() {
        val kaiUiJson = """{"type":"column","children":[{"type":"text","value":"\u23f1\ufe0f Time's Up!","style":"headline","bold":true},{"type":"alert","message":"You didn't submit your answers in time!","severity":"warning"},{"type":"card","children":[{"type":"text","value":"Score: 0/3","style":"headline","bold":true},{"type":"text","value":"The clock beat you this round.","style":"body"}]},{"type":"divider"},{"type":"text","value":"Here are the answers:","style":"title"},{"type":"accordion","title":"Q1: 64, 32, 16, 8, 4, ? = 2","expanded":false,"children":[{"type":"text","value":"Dividing by 2 each time. Classic halving sequence.","style":"body"}]},{"type":"accordion","title":"Q2: Odd shape = Circle","expanded":false,"children":[{"type":"text","value":"Circle is the only shape without straight lines or angles.","style":"body"}]},{"type":"accordion","title":"Q3: 100 machines, 100 widgets = 5 minutes","expanded":false,"children":[{"type":"text","value":"If 5 machines take 5 minutes to make 5 widgets, each machine makes 1 widget in 5 minutes. So 100 machines make 100 widgets in 5 minutes.","style":"body"}]},{"type":"divider"},{"type":"button","label":"Try Again","action":{"type":"callback","event":"retry"},"variant":"filled"}]}"""
        paparazzi.snapKaiUi(kaiUiJson)
    }

    // --- Scenario: Settings form ---

    @Test
    fun scenario_settingsForm_light() {
        paparazzi.snapKaiUi(
            """{"type":"column","children":[
                {"type":"text","value":"Preferences","style":"headline","bold":true},
                {"type":"text","value":"Customize your experience","style":"caption"},
                {"type":"divider"},
                {"type":"text_input","id":"display_name","label":"Display Name","value":"Simon"},
                {"type":"select","id":"language","label":"Language","options":["English","German","Japanese","Portuguese"],"selected":"English"},
                {"type":"switch","id":"dark_mode","label":"Dark Mode","checked":true},
                {"type":"switch","id":"notifications","label":"Push Notifications","checked":false},
                {"type":"slider","id":"font_size","label":"Font Size","min":12,"max":24,"value":16,"step":1},
                {"type":"divider"},
                {"type":"chip_group","id":"interests","selection":"multi","chips":[
                    {"label":"Technology","value":"tech"},
                    {"label":"Science","value":"science"},
                    {"label":"Music","value":"music"},
                    {"label":"Sports","value":"sports"},
                    {"label":"Travel","value":"travel"}
                ]},
                {"type":"spacer","height":8},
                {"type":"row","children":[
                    {"type":"button","label":"Cancel","variant":"outlined","action":{"type":"callback","event":"cancel"}},
                    {"type":"button","label":"Save","variant":"filled","action":{"type":"callback","event":"save","collectFrom":["display_name","language","dark_mode","notifications","font_size","interests"]}}
                ]}
            ]}""",
            colorScheme = LightColorScheme,
        )
    }

    // --- Scenario: Dashboard ---

    @Test
    fun scenario_dashboard_dark() {
        paparazzi.snapKaiUi(
            """{"type":"column","children":[
                {"type":"text","value":"Daily Dashboard","style":"headline","bold":true},
                {"type":"text","value":"Wednesday, April 2","style":"caption"},
                {"type":"divider"},
                {"type":"card","children":[
                    {"type":"row","children":[
                        {"type":"icon","name":"email","color":"primary"},
                        {"type":"text","value":"12 Unread","style":"body","bold":true}
                    ]},
                    {"type":"row","children":[
                        {"type":"icon","name":"calendar","color":"primary"},
                        {"type":"text","value":"3 Meetings","style":"body","bold":true}
                    ]},
                    {"type":"row","children":[
                        {"type":"icon","name":"check","color":"primary"},
                        {"type":"text","value":"7 Tasks Done","style":"body","bold":true}
                    ]}
                ]},
                {"type":"text","value":"Upcoming","style":"title"},
                {"type":"card","children":[
                    {"type":"row","children":[
                        {"type":"icon","name":"calendar"},
                        {"type":"column","children":[
                            {"type":"text","value":"Team Standup","style":"body","bold":true},
                            {"type":"text","value":"10:00 AM - 10:15 AM","style":"caption"}
                        ]}
                    ]}
                ]},
                {"type":"card","children":[
                    {"type":"row","children":[
                        {"type":"icon","name":"calendar"},
                        {"type":"column","children":[
                            {"type":"text","value":"Design Review","style":"body","bold":true},
                            {"type":"text","value":"2:00 PM - 3:00 PM","style":"caption"}
                        ]}
                    ]}
                ]},
                {"type":"card","children":[
                    {"type":"row","children":[
                        {"type":"icon","name":"calendar"},
                        {"type":"column","children":[
                            {"type":"text","value":"Sprint Planning","style":"body","bold":true},
                            {"type":"text","value":"4:00 PM - 5:00 PM","style":"caption"}
                        ]}
                    ]}
                ]},
                {"type":"progress","label":"Sprint progress","value":0.65},
                {"type":"countdown","seconds":14400,"label":"Next meeting in"}
            ]}""",
        )
    }

    // --- Scenario: Quiz in progress (full chat screen) ---

    @Test
    fun scenario_quizInProgress_light() {
        val kaiUiContent = "```kai-ui\n" +
            """{"type":"column","children":[""" +
            """{"type":"text","value":"Brain Teaser #4","style":"headline","bold":true},""" +
            """{"type":"progress","value":0.4,"label":"4 of 10"},""" +
            """{"type":"spacer","height":8},""" +
            """{"type":"card","children":[""" +
            """{"type":"text","value":"A farmer has 17 sheep. All but 9 run away. How many sheep does the farmer have left?","style":"body"}""" +
            """]},""" +
            """{"type":"spacer","height":8},""" +
            """{"type":"text","value":"Select your answer:","style":"title"},""" +
            """{"type":"button","label":"8 sheep","variant":"outlined","action":{"type":"callback","event":"answer","data":{"value":"8"}}},""" +
            """{"type":"button","label":"9 sheep","variant":"outlined","action":{"type":"callback","event":"answer","data":{"value":"9"}}},""" +
            """{"type":"button","label":"17 sheep","variant":"outlined","action":{"type":"callback","event":"answer","data":{"value":"17"}}},""" +
            """{"type":"divider"},""" +
            """{"type":"countdown","seconds":45,"label":"Time remaining"},""" +
            """{"type":"text","value":"Question 4 of 10","style":"caption","color":"secondary"}""" +
            """]}""" +
            "\n```"

        paparazzi.snapFullScreen(LightColorScheme) {
            ChatScreenContent(
                uiState = ChatUiState(
                    actions = ScreenshotTestData.chatEmptyState.actions,
                    history = persistentListOf(
                        History(id = "1", role = History.Role.USER, content = "Give me a brain teaser quiz"),
                        History(id = "2", role = History.Role.ASSISTANT, content = kaiUiContent),
                    ),
                    isInteractiveMode = true,
                ),
                FakeTextToSpeechInstance(),
            )
        }
    }

    // --- Scenario: Recipe card ---

    @Test
    fun scenario_recipeCard_light() {
        paparazzi.snapKaiUiWithImage(
            json = """{"type":"column","children":[
                {"type":"text","value":"Cacio e Pepe","style":"headline","bold":true},
                {"type":"text","value":"Classic Roman pasta \u2022 2 servings","style":"caption","color":"secondary"},
                {"type":"row","children":[
                    {"type":"badge","value":"\u23f1 20 min","color":"secondary"},
                    {"type":"badge","value":"\uD83C\uDF7D 2 servings","color":"secondary"},
                    {"type":"badge","value":"\u2b50 4.9/5","color":"primary"}
                ]},
                {"type":"image","url":"preview://image"},
                {"type":"text","value":"Ingredients","style":"title"},
                {"type":"list","ordered":false,"items":[
                    {"type":"text","value":"200g tonnarelli or spaghetti"},
                    {"type":"text","value":"150g Pecorino Romano, finely grated"},
                    {"type":"text","value":"2 tsp black peppercorns"},
                    {"type":"text","value":"Salt for pasta water"}
                ]},
                {"type":"divider"},
                {"type":"text","value":"Instructions","style":"title"},
                {"type":"accordion","title":"Step 1: Toast pepper & cook pasta","children":[
                    {"type":"text","value":"Toast peppercorns in a dry pan until fragrant, crush coarsely. Boil pasta until al dente, reserve pasta water.","style":"body"}
                ]},
                {"type":"accordion","title":"Step 2: Make the sauce","children":[
                    {"type":"text","value":"Mix grated Pecorino with a few tablespoons of warm pasta water to form a smooth cream.","style":"body"}
                ]},
                {"type":"accordion","title":"Step 3: Combine","children":[
                    {"type":"text","value":"Toss hot pasta with crushed pepper off heat. Add the Pecorino cream and toss vigorously, adding pasta water until silky.","style":"body"}
                ]}
            ]}""",
            imageResource = "/cacio_e_pepe.png",
            colorScheme = LightColorScheme,
        )
    }

    // --- Display elements (quote, badge, stat, avatar) ---

    @Test
    fun displayElements() {
        paparazzi.snapKaiUi(
            """{"type":"column","children":[
                {"type":"text","value":"Display Elements","style":"headline","bold":true},
                {"type":"divider"},
                {"type":"text","value":"Quote","style":"title"},
                {"type":"quote","text":"The only way to do great work is to love what you do.","source":"Steve Jobs"},
                {"type":"divider"},
                {"type":"text","value":"Badges","style":"title"},
                {"type":"row","children":[
                    {"type":"badge","value":"3","color":"primary"},
                    {"type":"badge","value":"New","color":"secondary"},
                    {"type":"badge","value":"!","color":"error"}
                ]},
                {"type":"divider"},
                {"type":"text","value":"Stats","style":"title"},
                {"type":"row","children":[
                    {"type":"stat","value":"1,234","label":"Users","description":"↑ 12%"},
                    {"type":"stat","value":"$56K","label":"Revenue"},
                    {"type":"stat","value":"99.9%","label":"Uptime"}
                ]},
                {"type":"divider"},
                {"type":"text","value":"Avatars","style":"title"},
                {"type":"row","children":[
                    {"type":"avatar","name":"Simon Schubert","size":48},
                    {"type":"avatar","name":"Jane Doe","size":48},
                    {"type":"avatar","size":48}
                ]}
            ]}""",
        )
    }

    // --- Scenario: User profile ---

    @Test
    fun scenario_userProfile_dark() {
        paparazzi.snapKaiUi(
            """{"type":"column","children":[
                {"type":"row","children":[
                    {"type":"avatar","name":"Simon Schubert","size":56},
                    {"type":"column","children":[
                        {"type":"text","value":"Simon Schubert","style":"title","bold":true},
                        {"type":"row","children":[
                            {"type":"badge","value":"Pro","color":"primary"},
                            {"type":"badge","value":"Verified","color":"secondary"}
                        ]}
                    ]}
                ]},
                {"type":"divider"},
                {"type":"row","children":[
                    {"type":"stat","value":"127","label":"Posts"},
                    {"type":"stat","value":"1.2K","label":"Followers"},
                    {"type":"stat","value":"342","label":"Following"}
                ]},
                {"type":"divider"},
                {"type":"quote","text":"Building the future, one line of code at a time."},
                {"type":"divider"},
                {"type":"text","value":"Recent Activity","style":"title"},
                {"type":"card","children":[
                    {"type":"row","children":[
                        {"type":"icon","name":"star","color":"primary"},
                        {"type":"text","value":"Published \"Getting Started with KMP\"","style":"body"}
                    ]},
                    {"type":"text","value":"2 hours ago","style":"caption","color":"secondary"}
                ]},
                {"type":"button","label":"Edit Profile","variant":"outlined","action":{"type":"callback","event":"edit_profile"}}
            ]}""",
        )
    }

    // --- Scenario: Survival game ---

    @Test
    fun scenario_survivalGame_dark() {
        paparazzi.snapKaiUiWithImage(
            json = """{"type":"column","children":[
                {"type":"text","value":"\u2694\ufe0f The Goblin Tunnels","style":"headline","bold":true},
                {"type":"text","value":"Chapter 1 \u2022 The Beginning","style":"caption","color":"secondary"},
                {"type":"row","children":[
                    {"type":"stat","value":"20/20","label":"HP"},
                    {"type":"stat","value":"Lv 1","label":"Level"},
                    {"type":"stat","value":"50g","label":"Gold"},
                    {"type":"stat","value":"2","label":"DEF"}
                ]},
                {"type":"image","url":"preview://image"},
                {"type":"text","value":"You descend deeper into the darkness. The air grows thick with the smell of damp stone and something rotten. Your torchlight dances across crude carvings on the walls — warnings, perhaps, from those who came before. A low growl echoes from somewhere ahead.","style":"body"},
                {"type":"text","value":"The tunnel forks. To the left, faint firelight flickers. To the right, silence — and a cold draft that makes your torch sputter.","style":"body"},
                {"type":"divider"},
                {"type":"text","value":"\ud83c\udf92 Inventory","style":"title"},
                {"type":"row","children":[
                    {"type":"badge","value":"\ud83d\udde1\ufe0f Iron Sword","color":"secondary"},
                    {"type":"badge","value":"\ud83d\udee1\ufe0f Shield","color":"secondary"},
                    {"type":"badge","value":"\ud83e\udded Health Potion","color":"primary"},
                    {"type":"badge","value":"\ud83d\udd25 Torch","color":"tertiary"}
                ]},
                {"type":"divider"},
                {"type":"alert","severity":"warning","message":"\ud83d\udc7a Two orcs block the left passage! They sit around a small fire, gnawing on bones. They haven't noticed you yet."},
                {"type":"spacer","height":4},
                {"type":"button","label":"\u2694\ufe0f Attack with sword","variant":"filled","action":{"type":"callback","event":"attack"}},
                {"type":"button","label":"\ud83e\udd2b Sneak past in the shadows","variant":"outlined","action":{"type":"callback","event":"sneak"}},
                {"type":"button","label":"\ud83d\udc68\u200d\ud83e\uddb2 Take the right tunnel","variant":"outlined","action":{"type":"callback","event":"right_tunnel"}}
            ]}""",
            imageResource = "/orc_survival.png",
        )
    }

    // --- Scenario: Sustainable tech brainstorm ---

    @Test
    fun scenario_sustainableTech_light() {
        paparazzi.snapKaiUi(
            """{"type":"column","children":[
                {"type":"text","value":"Sustainable Tech Project \uD83C\uDF31","style":"headline","bold":true},
                {"type":"card","children":[
                    {"type":"text","value":"Let\u2019s design a project that merges technology with sustainability. Here\u2019s a concept to explore:","style":"body"},
                    {"type":"spacer","size":16},
                    {"type":"quote","text":"What if we built a platform that helps individuals and businesses reduce their carbon footprint\u2014without sacrificing convenience?","source":"Hypothetical You"},
                    {"type":"spacer","size":16},
                    {"type":"text","value":"Project Name: **EcoPulse**","style":"title","bold":true},
                    {"type":"text","value":"A real-time carbon footprint tracker and reduction platform for individuals and businesses.","style":"body"},
                    {"type":"spacer","size":16},
                    {"type":"accordion","title":"Core Features","expanded":false,"children":[
                        {"type":"list","items":[
                            "**Real-Time Tracking**: Monitor energy, transportation, and consumption habits.",
                            "**Automated Insights**: Integrate with smart devices and apps for seamless data collection.",
                            "**Actionable Tips**: Receive personalized recommendations to reduce your footprint.",
                            "**Gamification**: Earn rewards for hitting sustainability milestones.",
                            "**Business Tools**: Track and report emissions for ESG compliance."
                        ],"ordered":false}
                    ]},
                    {"type":"spacer","size":16},
                    {"type":"row","children":[
                        {"type":"button","label":"Refine This Idea","action":{"type":"callback","event":"refine_ecopulse"},"variant":"filled"},
                        {"type":"button","label":"Explore Another Idea","action":{"type":"callback","event":"explore_sustainable_idea"},"variant":"outlined"}
                    ]}
                ]},
                {"type":"card","children":[
                    {"type":"text","value":"Or, brainstorm a different sustainable tech project:","style":"title","bold":true},
                    {"type":"chip_group","id":"sustainable_themes","chips":[
                        {"label":"Circular Economy","value":"circular_economy"},
                        {"label":"Renewable Energy","value":"renewable_energy"},
                        {"label":"Smart Cities","value":"smart_cities"},
                        {"label":"Sustainable Agriculture","value":"sustainable_agriculture"},
                        {"label":"E-Waste Reduction","value":"e_waste"},
                        {"label":"Water Conservation","value":"water_conservation"}
                    ],"selection":"multi"},
                    {"type":"spacer","size":8},
                    {"type":"button","label":"Brainstorm","action":{"type":"callback","event":"brainstorm_sustainable_project","collectFrom":["sustainable_themes"]},"variant":"filled"}
                ]}
            ]}""",
            colorScheme = LightColorScheme,
        )
    }

    // --- Scenario: Memories ---

    @Test
    fun scenario_memories_dark() {
        paparazzi.snapKaiUi(
            """{"type":"column","children":[
                {"type":"text","value":"Our Memories","style":"headline","bold":true},
                {"type":"text","value":"Kai remembers what matters to you. Frequently used memories get promoted into the system prompt.","style":"body","color":"secondary"},
                {"type":"row","children":[
                    {"type":"stat","value":"12","label":"Total"},
                    {"type":"stat","value":"3","label":"Promoted"},
                    {"type":"stat","value":"87","label":"Recalled"}
                ]},
                {"type":"divider"},
                {"type":"text","value":"Promoted","style":"title","bold":true,"color":"primary"},
                {"type":"card","children":[
                    {"type":"column","children":[
                        {"type":"row","children":[
                            {"type":"icon","name":"star"},
                            {"type":"text","value":"Software engineer, mainly Kotlin","style":"body","bold":true}
                        ]},
                        {"type":"text","value":"Recalled 9 times \u2022 Promoted to system prompt","style":"caption","color":"primary"},
                        {"type":"row","children":[
                            {"type":"button","label":"Demote","variant":"outlined","action":{"type":"callback","event":"demote","data":{"id":"2"}}},
                            {"type":"button","label":"Delete","variant":"text","action":{"type":"callback","event":"delete","data":{"id":"2"}}}
                        ]}
                    ]}
                ]},
                {"type":"card","children":[
                    {"type":"column","children":[
                        {"type":"row","children":[
                            {"type":"icon","name":"star"},
                            {"type":"text","value":"Dark mode, concise answers","style":"body","bold":true}
                        ]},
                        {"type":"text","value":"Recalled 14 times \u2022 Promoted to system prompt","style":"caption","color":"primary"},
                        {"type":"row","children":[
                            {"type":"button","label":"Demote","variant":"outlined","action":{"type":"callback","event":"demote","data":{"id":"1"}}},
                            {"type":"button","label":"Delete","variant":"text","action":{"type":"callback","event":"delete","data":{"id":"1"}}}
                        ]}
                    ]}
                ]},
                {"type":"divider"},
                {"type":"text","value":"Facts & Preferences","style":"title","bold":true},
                {"type":"card","children":[
                    {"type":"column","children":[
                        {"type":"row","children":[
                            {"type":"icon","name":"pets"},
                            {"type":"text","value":"Cat named Pixel","style":"body","bold":true}
                        ]},
                        {"type":"row","children":[
                            {"type":"text","value":"Recalled 4 times","style":"caption"},
                            {"type":"badge","value":"promote","color":"primary"}
                        ]}
                    ]}
                ]},
                {"type":"card","children":[
                    {"type":"column","children":[
                        {"type":"row","children":[
                            {"type":"icon","name":"flight"},
                            {"type":"text","value":"Travels often, prefers window seats","style":"body","bold":true}
                        ]},
                        {"type":"row","children":[
                            {"type":"text","value":"Recalled 3 times","style":"caption"},
                            {"type":"badge","value":"promote","color":"primary"}
                        ]}
                    ]}
                ]},
                {"type":"card","children":[
                    {"type":"column","children":[
                        {"type":"row","children":[
                            {"type":"icon","name":"location_on"},
                            {"type":"text","value":"Lives in Berlin, moved from Munich","style":"body","bold":true}
                        ]},
                        {"type":"row","children":[
                            {"type":"text","value":"Recalled 2 times","style":"caption"},
                            {"type":"badge","value":"promote","color":"primary"}
                        ]}
                    ]}
                ]},
                {"type":"divider"},
                {"type":"row","children":[
                    {"type":"button","label":"Add Memory","variant":"filled","action":{"type":"callback","event":"add_memory"}},
                    {"type":"button","label":"Export All","variant":"outlined","action":{"type":"callback","event":"export"}}
                ]}
            ]}""",
        )
    }

    // --- All elements combined (light) ---

    @Test
    fun allElements_light() {
        paparazzi.snapKaiUi(
            """{"type":"column","children":[
                {"type":"text","value":"Component Showcase","style":"headline","bold":true},
                {"type":"divider"},
                {"type":"text","value":"Buttons","style":"title"},
                {"type":"row","children":[
                    {"type":"button","label":"Filled","variant":"filled","action":{"type":"callback","event":"x"}},
                    {"type":"button","label":"Tonal","variant":"tonal","action":{"type":"callback","event":"x"}},
                    {"type":"button","label":"Outlined","variant":"outlined","action":{"type":"callback","event":"x"}}
                ]},
                {"type":"divider"},
                {"type":"text","value":"Inputs","style":"title"},
                {"type":"text_input","id":"i1","label":"Text Field","placeholder":"Type here..."},
                {"type":"checkbox","id":"c1","label":"Checkbox option"},
                {"type":"switch","id":"s1","label":"Toggle switch"},
                {"type":"select","id":"sel1","label":"Dropdown","options":["Option A","Option B","Option C"]},
                {"type":"radio_group","id":"r1","label":"Radio","options":["Alpha","Beta","Gamma"],"selected":"Alpha"},
                {"type":"slider","id":"sl1","label":"Slider","value":50,"min":0,"max":100},
                {"type":"divider"},
                {"type":"text","value":"Chips","style":"title"},
                {"type":"chip_group","id":"ch1","selection":"multi","chips":[{"label":"Tag 1","value":"1"},{"label":"Tag 2","value":"2"},{"label":"Tag 3","value":"3"}]},
                {"type":"divider"},
                {"type":"text","value":"Feedback","style":"title"},
                {"type":"progress","value":0.5,"label":"50%"},
                {"type":"alert","severity":"info","message":"Informational message"},
                {"type":"countdown","seconds":600,"label":"Countdown"},
                {"type":"divider"},
                {"type":"text","value":"Data","style":"title"},
                {"type":"table","headers":["Name","Value"],"rows":[["Alpha","100"],["Beta","200"]]},
                {"type":"list","ordered":true,"items":[{"type":"text","value":"First item"},{"type":"text","value":"Second item"}]},
                {"type":"divider"},
                {"type":"code","language":"kotlin","code":"println(\"Hello\")"},
                {"type":"row","children":[
                    {"type":"icon","name":"home"},
                    {"type":"icon","name":"star","color":"primary"},
                    {"type":"icon","name":"favorite","color":"error"}
                ]}
            ]}""",
            colorScheme = LightColorScheme,
        )
    }
}
