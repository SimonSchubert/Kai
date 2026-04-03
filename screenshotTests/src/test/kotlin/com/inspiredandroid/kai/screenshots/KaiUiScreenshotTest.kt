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
import com.inspiredandroid.kai.ui.LightColorScheme
import com.inspiredandroid.kai.ui.Theme
import com.inspiredandroid.kai.ui.chat.ChatScreenContent
import com.inspiredandroid.kai.ui.chat.ChatUiState
import com.inspiredandroid.kai.ui.chat.History
import com.inspiredandroid.kai.ui.dynamicui.KaiUiParser
import com.inspiredandroid.kai.ui.dynamicui.KaiUiRenderer
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
            """{"type":"column","spacing":8,"children":[
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
            """{"type":"column","spacing":8,"children":[
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
            """{"type":"column","spacing":12,"children":[
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
            """{"type":"column","spacing":12,"children":[
                {"type":"text","value":"Single Select","style":"title"},
                {"type":"chip_group","id":"mood","chips":[
                    {"label":"Happy","value":"happy"},
                    {"label":"Neutral","value":"neutral"},
                    {"label":"Sad","value":"sad"}
                ]},
                {"type":"text","value":"Multi Select","style":"title"},
                {"type":"chip_group","id":"tags","multiSelect":true,"chips":[
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
            """{"type":"column","spacing":12,"children":[
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
            """{"type":"column","spacing":8,"children":[
                {"type":"text","value":"Cards & Layout","style":"title","bold":true},
                {"type":"card","padding":12,"children":[
                    {"type":"text","value":"Card Title","style":"title"},
                    {"type":"text","value":"Cards group related content together.","style":"body"}
                ]},
                {"type":"divider"},
                {"type":"row","spacing":8,"children":[
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
            """{"type":"column","spacing":8,"children":[
                {"type":"text","value":"Icons","style":"title","bold":true},
                {"type":"row","spacing":12,"children":[
                    {"type":"icon","name":"home"},
                    {"type":"icon","name":"search"},
                    {"type":"icon","name":"settings"},
                    {"type":"icon","name":"person"},
                    {"type":"icon","name":"favorite","color":"error"},
                    {"type":"icon","name":"star","color":"primary"},
                    {"type":"icon","name":"notifications"},
                    {"type":"icon","name":"email"}
                ]},
                {"type":"row","spacing":12,"children":[
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
            """{"type":"column","spacing":8,"children":[
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
            """{"type":"column","spacing":4,"children":[
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
            """{"type":"column","spacing":8,"children":[
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
            """{"type":"column","spacing":8,"children":[
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
            """{"type":"column","spacing":12,"children":[
                {"type":"text","value":"Preferences","style":"headline","bold":true},
                {"type":"text","value":"Customize your experience","style":"caption"},
                {"type":"divider"},
                {"type":"text_input","id":"display_name","label":"Display Name","value":"Simon"},
                {"type":"select","id":"language","label":"Language","options":["English","German","Japanese","Portuguese"],"selected":"English"},
                {"type":"switch","id":"dark_mode","label":"Dark Mode","checked":true},
                {"type":"switch","id":"notifications","label":"Push Notifications","checked":false},
                {"type":"slider","id":"font_size","label":"Font Size","min":12,"max":24,"value":16,"step":1},
                {"type":"divider"},
                {"type":"chip_group","id":"interests","multiSelect":true,"chips":[
                    {"label":"Technology","value":"tech"},
                    {"label":"Science","value":"science"},
                    {"label":"Music","value":"music"},
                    {"label":"Sports","value":"sports"},
                    {"label":"Travel","value":"travel"}
                ]},
                {"type":"spacer","height":8},
                {"type":"row","spacing":8,"children":[
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
            """{"type":"column","spacing":8,"children":[
                {"type":"text","value":"Daily Dashboard","style":"headline","bold":true},
                {"type":"text","value":"Wednesday, April 2","style":"caption"},
                {"type":"divider"},
                {"type":"card","children":[
                    {"type":"row","spacing":8,"children":[
                        {"type":"icon","name":"email","color":"primary"},
                        {"type":"text","value":"12 Unread","style":"body","bold":true}
                    ]},
                    {"type":"row","spacing":8,"children":[
                        {"type":"icon","name":"calendar","color":"primary"},
                        {"type":"text","value":"3 Meetings","style":"body","bold":true}
                    ]},
                    {"type":"row","spacing":8,"children":[
                        {"type":"icon","name":"check","color":"primary"},
                        {"type":"text","value":"7 Tasks Done","style":"body","bold":true}
                    ]}
                ]},
                {"type":"text","value":"Upcoming","style":"title"},
                {"type":"card","children":[
                    {"type":"row","spacing":8,"children":[
                        {"type":"icon","name":"calendar"},
                        {"type":"column","children":[
                            {"type":"text","value":"Team Standup","style":"body","bold":true},
                            {"type":"text","value":"10:00 AM - 10:15 AM","style":"caption"}
                        ]}
                    ]}
                ]},
                {"type":"card","children":[
                    {"type":"row","spacing":8,"children":[
                        {"type":"icon","name":"calendar"},
                        {"type":"column","children":[
                            {"type":"text","value":"Design Review","style":"body","bold":true},
                            {"type":"text","value":"2:00 PM - 3:00 PM","style":"caption"}
                        ]}
                    ]}
                ]},
                {"type":"card","children":[
                    {"type":"row","spacing":8,"children":[
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
            """{"type":"card","padding":12,"children":[""" +
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
        paparazzi.snapKaiUi(
            """{"type":"column","spacing":8,"children":[
                {"type":"text","value":"Pasta Carbonara","style":"headline","bold":true},
                {"type":"text","value":"Classic Italian comfort food","style":"caption"},
                {"type":"divider"},
                {"type":"row","spacing":16,"children":[
                    {"type":"column","children":[
                        {"type":"icon","name":"calendar","size":20},
                        {"type":"text","value":"25 min","style":"caption"}
                    ]},
                    {"type":"column","children":[
                        {"type":"icon","name":"person","size":20},
                        {"type":"text","value":"4 servings","style":"caption"}
                    ]},
                    {"type":"column","children":[
                        {"type":"icon","name":"star","size":20,"color":"primary"},
                        {"type":"text","value":"4.8/5","style":"caption"}
                    ]}
                ]},
                {"type":"divider"},
                {"type":"text","value":"Ingredients","style":"title"},
                {"type":"list","ordered":false,"items":[
                    {"type":"text","value":"400g spaghetti"},
                    {"type":"text","value":"200g guanciale or pancetta"},
                    {"type":"text","value":"4 egg yolks + 2 whole eggs"},
                    {"type":"text","value":"100g Pecorino Romano"},
                    {"type":"text","value":"Black pepper to taste"}
                ]},
                {"type":"divider"},
                {"type":"text","value":"Instructions","style":"title"},
                {"type":"accordion","title":"Step 1: Cook pasta","children":[
                    {"type":"text","value":"Bring a large pot of salted water to boil. Cook spaghetti until al dente.","style":"body"}
                ]},
                {"type":"accordion","title":"Step 2: Prepare guanciale","children":[
                    {"type":"text","value":"Cut guanciale into small strips. Cook in a pan over medium heat until crispy.","style":"body"}
                ]},
                {"type":"accordion","title":"Step 3: Mix egg sauce","children":[
                    {"type":"text","value":"Whisk egg yolks, whole eggs, and grated Pecorino together. Season with pepper.","style":"body"}
                ]},
                {"type":"accordion","title":"Step 4: Combine","children":[
                    {"type":"text","value":"Toss hot pasta with guanciale off heat. Quickly stir in egg mixture. The residual heat cooks the eggs into a creamy sauce.","style":"body"}
                ]},
                {"type":"spacer","height":8},
                {"type":"button","label":"Start Cooking Timer","variant":"filled","action":{"type":"callback","event":"start_timer"}},
                {"type":"button","label":"Save Recipe","variant":"outlined","action":{"type":"callback","event":"save"}}
            ]}""",
            colorScheme = LightColorScheme,
        )
    }

    // --- Display elements (quote, badge, stat, avatar) ---

    @Test
    fun displayElements() {
        paparazzi.snapKaiUi(
            """{"type":"column","spacing":12,"children":[
                {"type":"text","value":"Display Elements","style":"headline","bold":true},
                {"type":"divider"},
                {"type":"text","value":"Quote","style":"title"},
                {"type":"quote","text":"The only way to do great work is to love what you do.","source":"Steve Jobs"},
                {"type":"divider"},
                {"type":"text","value":"Badges","style":"title"},
                {"type":"row","spacing":8,"children":[
                    {"type":"badge","value":"3","color":"primary"},
                    {"type":"badge","value":"New","color":"secondary"},
                    {"type":"badge","value":"!","color":"error"}
                ]},
                {"type":"divider"},
                {"type":"text","value":"Stats","style":"title"},
                {"type":"row","spacing":16,"children":[
                    {"type":"stat","value":"1,234","label":"Users","description":"↑ 12%"},
                    {"type":"stat","value":"$56K","label":"Revenue"},
                    {"type":"stat","value":"99.9%","label":"Uptime"}
                ]},
                {"type":"divider"},
                {"type":"text","value":"Avatars","style":"title"},
                {"type":"row","spacing":12,"children":[
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
            """{"type":"column","spacing":12,"children":[
                {"type":"row","spacing":12,"children":[
                    {"type":"avatar","name":"Simon Schubert","size":56},
                    {"type":"column","children":[
                        {"type":"text","value":"Simon Schubert","style":"title","bold":true},
                        {"type":"row","spacing":6,"children":[
                            {"type":"badge","value":"Pro","color":"primary"},
                            {"type":"badge","value":"Verified","color":"secondary"}
                        ]}
                    ]}
                ]},
                {"type":"divider"},
                {"type":"row","spacing":16,"children":[
                    {"type":"stat","value":"127","label":"Posts"},
                    {"type":"stat","value":"1.2K","label":"Followers"},
                    {"type":"stat","value":"342","label":"Following"}
                ]},
                {"type":"divider"},
                {"type":"quote","text":"Building the future, one line of code at a time."},
                {"type":"divider"},
                {"type":"text","value":"Recent Activity","style":"title"},
                {"type":"card","children":[
                    {"type":"row","spacing":8,"children":[
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
        paparazzi.snapKaiUi(
            """{"type":"column","spacing":8,"children":[
                {"type":"text","value":"\u2694\ufe0f The Goblin Tunnels","style":"headline","bold":true},
                {"type":"text","value":"Chapter 3 \u2022 Depth: Level 2","style":"caption","color":"secondary"},
                {"type":"text","value":"You descend deeper into the darkness. The air grows thick with the smell of damp stone and something rotten. Your torchlight dances across crude carvings on the walls — warnings, perhaps, from those who came before. A low growl echoes from somewhere ahead.","style":"body"},
                {"type":"text","value":"The tunnel forks. To the left, faint firelight flickers. To the right, silence — and a cold draft that makes your torch sputter.","style":"body"},
                {"type":"divider"},
                {"type":"row","spacing":16,"children":[
                    {"type":"stat","value":"14/20","label":"HP"},
                    {"type":"stat","value":"Lv 3","label":"Level"},
                    {"type":"stat","value":"42g","label":"Gold"},
                    {"type":"stat","value":"7","label":"DEF"}
                ]},
                {"type":"progress","value":0.7,"label":"14/20 HP"},
                {"type":"divider"},
                {"type":"text","value":"\ud83c\udf92 Inventory","style":"title"},
                {"type":"row","spacing":8,"children":[
                    {"type":"badge","value":"\ud83d\udde1\ufe0f Iron Sword","color":"secondary"},
                    {"type":"badge","value":"\ud83d\udee1\ufe0f Shield","color":"secondary"},
                    {"type":"badge","value":"\ud83e\udded Health Potion","color":"primary"},
                    {"type":"badge","value":"\ud83d\udd25 Torch","color":"tertiary"}
                ]},
                {"type":"divider"},
                {"type":"alert","severity":"warning","message":"\ud83d\udc7a Two orcs block the left passage! They sit around a small fire, gnawing on bones. They haven't noticed you yet."},
                {"type":"spacer","height":4},
                {"type":"button","label":"\u2694\ufe0f Attack with sword","variant":"filled","action":{"type":"callback","event":"attack"}},
                {"type":"button","label":"\ud83e\udded Drink health potion first","variant":"outlined","action":{"type":"callback","event":"potion"}},
                {"type":"button","label":"\ud83e\udd2b Sneak past in the shadows","variant":"outlined","action":{"type":"callback","event":"sneak"}},
                {"type":"button","label":"\ud83d\udc68\u200d\ud83e\uddb2 Take the right tunnel","variant":"outlined","action":{"type":"callback","event":"right_tunnel"}}
            ]}""",
        )
    }

    // --- Scenario: Task management ---

    @Test
    fun scenario_taskManagement_light() {
        paparazzi.snapKaiUi(
            """{"type":"column","spacing":10,"children":[
                {"type":"text","value":"Your Tasks","style":"headline","bold":true},
                {"type":"text","value":"Here's what's on your plate today and coming up this week.","style":"body","color":"secondary"},
                {"type":"row","spacing":16,"children":[
                    {"type":"stat","value":"4","label":"Open"},
                    {"type":"stat","value":"3","label":"Done"},
                    {"type":"stat","value":"1","label":"Upcoming"}
                ]},
                {"type":"progress","value":0.43,"label":"43% complete"},
                {"type":"divider"},
                {"type":"text","value":"Today","style":"title","bold":true},
                {"type":"card","padding":12,"children":[
                    {"type":"column","spacing":6,"children":[
                        {"type":"row","spacing":8,"children":[
                            {"type":"checkbox","id":"t1","label":"Grocery run"},
                            {"type":"spacer"},
                            {"type":"badge","value":"Low","color":"secondary"}
                        ]},
                        {"type":"text","value":"Milk, eggs, bread, vegetables","style":"caption"}
                    ]}
                ]},
                {"type":"card","padding":12,"children":[
                    {"type":"column","spacing":6,"children":[
                        {"type":"row","spacing":8,"children":[
                            {"type":"checkbox","id":"t2","label":"Review PR #247"},
                            {"type":"spacer"},
                            {"type":"badge","value":"Med","color":"primary"}
                        ]},
                        {"type":"text","value":"Auth middleware refactor by @lisa","style":"caption"}
                    ]}
                ]},
                {"type":"divider"},
                {"type":"text","value":"This Week","style":"title","bold":true},
                {"type":"card","padding":12,"children":[
                    {"type":"column","spacing":6,"children":[
                        {"type":"row","spacing":8,"children":[
                            {"type":"checkbox","id":"t3","label":"Book Berlin flights"},
                            {"type":"spacer"},
                            {"type":"badge","value":"Med","color":"primary"}
                        ]},
                        {"type":"text","value":"Apr 5 \u2022 Check Lufthansa and EasyJet","style":"caption"}
                    ]}
                ]},
                {"type":"card","padding":12,"children":[
                    {"type":"column","spacing":6,"children":[
                        {"type":"row","spacing":8,"children":[
                            {"type":"checkbox","id":"t4","label":"Dentist appointment"},
                            {"type":"spacer"},
                            {"type":"badge","value":"High","color":"error"}
                        ]},
                        {"type":"text","value":"Apr 7 at 10:00 AM \u2022 Dr. Weber","style":"caption"}
                    ]}
                ]},
                {"type":"divider"},
                {"type":"button","label":"Add New Task","variant":"filled","action":{"type":"callback","event":"add_task"}}
            ]}""",
            colorScheme = LightColorScheme,
        )
    }

    // --- Scenario: Memories ---

    @Test
    fun scenario_memories_dark() {
        paparazzi.snapKaiUi(
            """{"type":"column","spacing":10,"children":[
                {"type":"text","value":"Your Memories","style":"headline","bold":true},
                {"type":"text","value":"Kai remembers what matters to you. Frequently used memories get promoted into the system prompt.","style":"body","color":"secondary"},
                {"type":"row","spacing":16,"children":[
                    {"type":"stat","value":"12","label":"Total"},
                    {"type":"stat","value":"3","label":"Promoted"},
                    {"type":"stat","value":"87","label":"Recalled"}
                ]},
                {"type":"divider"},
                {"type":"text","value":"Promoted","style":"title","bold":true,"color":"primary"},
                {"type":"card","padding":12,"children":[
                    {"type":"column","spacing":6,"children":[
                        {"type":"row","spacing":8,"children":[
                            {"type":"icon","name":"star","color":"primary"},
                            {"type":"text","value":"Dark mode, concise answers","style":"body","bold":true}
                        ]},
                        {"type":"text","value":"Recalled 14 times \u2022 Promoted to system prompt","style":"caption","color":"primary"}
                    ]}
                ]},
                {"type":"card","padding":12,"children":[
                    {"type":"column","spacing":6,"children":[
                        {"type":"row","spacing":8,"children":[
                            {"type":"icon","name":"star","color":"primary"},
                            {"type":"text","value":"Software engineer, mainly Kotlin","style":"body","bold":true}
                        ]},
                        {"type":"text","value":"Recalled 9 times \u2022 Promoted to system prompt","style":"caption","color":"primary"}
                    ]}
                ]},
                {"type":"divider"},
                {"type":"text","value":"Facts & Preferences","style":"title","bold":true},
                {"type":"card","padding":12,"children":[
                    {"type":"column","spacing":6,"children":[
                        {"type":"row","spacing":8,"children":[
                            {"type":"icon","name":"pets"},
                            {"type":"text","value":"Cat named Pixel","style":"body","bold":true}
                        ]},
                        {"type":"text","value":"Recalled 4 times","style":"caption"}
                    ]}
                ]},
                {"type":"card","padding":12,"children":[
                    {"type":"column","spacing":6,"children":[
                        {"type":"row","spacing":8,"children":[
                            {"type":"icon","name":"restaurant"},
                            {"type":"text","value":"Vegetarian, allergic to nuts","style":"body","bold":true}
                        ]},
                        {"type":"text","value":"Recalled 3 times","style":"caption"}
                    ]}
                ]},
                {"type":"card","padding":12,"children":[
                    {"type":"column","spacing":6,"children":[
                        {"type":"row","spacing":8,"children":[
                            {"type":"icon","name":"location_on"},
                            {"type":"text","value":"Lives in Berlin, moved from Munich","style":"body","bold":true}
                        ]},
                        {"type":"text","value":"Recalled 2 times","style":"caption"}
                    ]}
                ]}
            ]}""",
        )
    }

    // --- All elements combined (light) ---

    @Test
    fun allElements_light() {
        paparazzi.snapKaiUi(
            """{"type":"column","spacing":10,"children":[
                {"type":"text","value":"Component Showcase","style":"headline","bold":true},
                {"type":"divider"},
                {"type":"text","value":"Buttons","style":"title"},
                {"type":"row","spacing":6,"children":[
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
                {"type":"chip_group","id":"ch1","multiSelect":true,"chips":[{"label":"Tag 1","value":"1"},{"label":"Tag 2","value":"2"},{"label":"Tag 3","value":"3"}]},
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
                {"type":"row","spacing":8,"children":[
                    {"type":"icon","name":"home"},
                    {"type":"icon","name":"star","color":"primary"},
                    {"type":"icon","name":"favorite","color":"error"}
                ]}
            ]}""",
            colorScheme = LightColorScheme,
        )
    }
}
