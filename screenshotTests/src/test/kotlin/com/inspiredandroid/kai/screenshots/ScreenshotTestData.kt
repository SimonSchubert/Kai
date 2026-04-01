package com.inspiredandroid.kai.screenshots

import com.inspiredandroid.kai.data.MemoryEntry
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.getPlatformToolDefinitions
import com.inspiredandroid.kai.ui.chat.ChatActions
import com.inspiredandroid.kai.ui.chat.ChatUiState
import com.inspiredandroid.kai.ui.chat.History
import com.inspiredandroid.kai.ui.settings.ConfiguredServiceEntry
import com.inspiredandroid.kai.ui.settings.ConnectionStatus
import com.inspiredandroid.kai.ui.settings.McpConnectionStatus
import com.inspiredandroid.kai.ui.settings.McpServerUiState
import com.inspiredandroid.kai.ui.settings.SandboxUiState
import com.inspiredandroid.kai.ui.settings.SettingsModel
import com.inspiredandroid.kai.ui.settings.SettingsTab
import com.inspiredandroid.kai.ui.settings.SettingsUiState
import com.inspiredandroid.kai.ui.settings.TerminalLine
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object ScreenshotTestData {

    private val noOpChatActions = ChatActions(
        ask = {},
        toggleSpeechOutput = {},
        retry = {},
        clearHistory = {},
        setIsSpeaking = { _, _ -> },
        setFile = { _ -> },
        startNewChat = { },
        regenerate = {},
        cancel = {},
        selectService = {},
        loadConversation = {},
        deleteConversation = {},
        clearSnackbar = {},
        clearUnreadHeartbeat = {},
        undoDeleteConversation = { },
        submitUiCallback = { _, _ -> },
    )

    val chatEmptyState = ChatUiState(
        actions = noOpChatActions,
        history = persistentListOf(),
        showPrivacyInfo = false,
    )

    private val introUiContent =
        "I'm Kai \u2014 a personal assistant that sticks around and actually gets to know you.\n\n" +
            "```kai-ui\n" +
            "{\"type\":\"column\",\"children\":[" +
            "{\"type\":\"card\",\"children\":[" +
            "{\"type\":\"text\",\"value\":\"I remember\",\"style\":\"title\",\"bold\":true}," +
            "{\"type\":\"text\",\"value\":\"Your preferences, projects, how you like things done. Next month, I still know.\",\"style\":\"body\"}" +
            "]}," +
            "{\"type\":\"card\",\"children\":[" +
            "{\"type\":\"text\",\"value\":\"I get things done\",\"style\":\"title\",\"bold\":true}," +
            "{\"type\":\"text\",\"value\":\"Reminders, email, calendar, shell commands \u2014 hooked into your device and your life.\",\"style\":\"body\"}" +
            "]}," +
            "{\"type\":\"card\",\"children\":[" +
            "{\"type\":\"text\",\"value\":\"I learn\",\"style\":\"title\",\"bold\":true}," +
            "{\"type\":\"text\",\"value\":\"Not just facts, but patterns. What works for you, what doesn't. I get better over time.\",\"style\":\"body\"}" +
            "]}," +
            "{\"type\":\"button\",\"label\":\"Let's go\",\"action\":{\"type\":\"callback\",\"event\":\"start\"}}," +
            "{\"type\":\"button\",\"label\":\"Set me up\",\"action\":{\"type\":\"callback\",\"event\":\"setup\"}}" +
            "]}\n" +
            "```"

    val chatWithMessages = ChatUiState(
        actions = noOpChatActions,
        history = persistentListOf(
            History(
                id = "1",
                role = History.Role.USER,
                content = "who are you?",
            ),
            History(
                id = "2",
                role = History.Role.ASSISTANT,
                content = introUiContent,
            ),
        ),
    )

    private val dynamicUiContent =
        "```kai-ui\n" +
            "{\"type\":\"column\",\"children\":[" +
            "{\"type\":\"text\",\"value\":\"Logical Reasoning\",\"style\":\"headline\",\"bold\":true}," +
            "{\"type\":\"text\",\"value\":\"You mentioned you enjoyed brain teasers last week, so I\u2019m tailoring these to your level. Here\u2019s a classic to warm up before your 3 PM meeting.\",\"style\":\"body\"}," +
            "{\"type\":\"text\",\"value\":\"A man is looking at a photograph. Someone asks him: \\\"Who is that in the picture?\\\" He replies: \\\"I have no brothers or sisters, but that man's father is my father's son.\\\"\",\"style\":\"body\"}," +
            "{\"type\":\"text\",\"value\":\"Take a moment to work through the statement carefully. The key is to figure out who \\\"my father's son\\\" refers to, and then work outward from there.\",\"style\":\"body\"}," +
            "{\"type\":\"divider\"}," +
            "{\"type\":\"text\",\"value\":\"Question 1 of 10\",\"style\":\"caption\",\"color\":\"secondary\"}," +
            "{\"type\":\"text\",\"value\":\"Who is in the photograph?\",\"style\":\"title\"}," +
            "{\"type\":\"button\",\"label\":\"Himself\",\"action\":{\"type\":\"callback\",\"event\":\"answer\",\"data\":{\"value\":\"himself\"}}}," +
            "{\"type\":\"button\",\"label\":\"His father\",\"action\":{\"type\":\"callback\",\"event\":\"answer\",\"data\":{\"value\":\"father\"}}}," +
            "{\"type\":\"button\",\"label\":\"His son\",\"action\":{\"type\":\"callback\",\"event\":\"answer\",\"data\":{\"value\":\"son\"}}}" +
            "]}\n" +
            "```"

    val chatWithDynamicUi = ChatUiState(
        actions = noOpChatActions,
        history = persistentListOf(
            History(
                id = "1",
                role = History.Role.USER,
                content = "Give me a quick IQ test",
            ),
            History(
                id = "2",
                role = History.Role.ASSISTANT,
                content = dynamicUiContent,
            ),
        ),
    )

    val freeConnected = SettingsUiState(
        currentTab = SettingsTab.Services,
        configuredServices = persistentListOf(
            ConfiguredServiceEntry(
                instanceId = "moonshot",
                service = Service.Moonshot,
                connectionStatus = ConnectionStatus.Connected,
                apiKey = "sk-••••••••••••••••••••••••••••••••••••",
                selectedModel = SettingsModel(id = "kimi-k2.5", subtitle = "Kimi K2.5", isSelected = true),
            ),
            ConfiguredServiceEntry(
                instanceId = "anthropic",
                service = Service.Anthropic,
                connectionStatus = ConnectionStatus.Connected,
                apiKey = "sk-ant-••••••••••••••••••••••••••••••••",
                selectedModel = SettingsModel(id = "claude-opus-4-6", subtitle = "Claude Opus 4.6", isSelected = true),
            ),
        ),
        availableServicesToAdd = persistentListOf(Service.OpenAI, Service.DeepSeek, Service.Mistral),
    )

    val settingsGeneral = SettingsUiState(
        currentTab = SettingsTab.General,
        soulText = "",
        isMemoryEnabled = true,
        memories = persistentListOf(
            MemoryEntry(
                key = "user_name",
                content = "The user's name is Simon",
                createdAt = 1709300000000,
                updatedAt = 1709300000000,
            ),
            MemoryEntry(
                key = "preferred_language",
                content = "Prefers Kotlin for app development",
                createdAt = 1709310000000,
                updatedAt = 1709310000000,
            ),
        ),
    )

    val settingsSandbox = SettingsUiState(
        currentTab = SettingsTab.Sandbox,
    )

    val sandboxState = SandboxUiState(
        showSandbox = true,
        sandboxInstalled = true,
        sandboxReady = true,
        sandboxDiskUsageMB = 578,
        sandboxPackagesInstalled = true,
        isSandboxEnabled = true,
    )

    private val fastfetchOutput =
        "       .hddddddddddddddddddddddh.\n" +
            "      :dddddddddddddddddddddddddd:\n" +
            "     /dddddddddddddddddddddddddddd/\n" +
            "    +dddddddddddddddddddddddddddddd+\n" +
            "  \u0060sdddddddddddddddddddddddddddddddds\u0060\n" +
            " \u0060ydddddddddddd++hdddddddddddddddddddy\u0060\n" +
            ".hddddddddddd+\u0060  \u0060+ddddh:-sdddddddddddh.\n" +
            "hdddddddddd+\u0060      \u0060+y:    .sddddddddddh\n" +
            "ddddddddh+\u0060   \u0060//\u0060   \u0060.\u0060     -sddddddddd\n" +
            "ddddddh+\u0060   \u0060/hddh/\u0060   \u0060:s-    -sddddddd\n" +
            "ddddh+\u0060   \u0060/+/dddddh/\u0060   \u0060+s-    -sddddd\n" +
            "ddd+\u0060   \u0060/o\u0060 :dddddddh/\u0060   \u0060oy-    .yddd\n" +
            "hdddyo+ohddyosdddddddddho+oydddy++ohdddh\n" +
            ".hddddddddddddddddddddddddddddddddddddh.\n" +
            " \u0060yddddddddddddddddddddddddddddddddddy\u0060\n" +
            "  \u0060sdddddddddddddddddddddddddddddddds\u0060\n" +
            "    +dddddddddddddddddddddddddddddd+\n" +
            "     /dddddddddddddddddddddddddddd/\n" +
            "      :dddddddddddddddddddddddddd:\n" +
            "       .hddddddddddddddddddddddh.root@localhost\n" +
            "--------------\n" +
            "OS: Alpine Linux v3.21 aarch64\n" +
            "Kernel: Linux 6.1.145-android14-11-gfa1d6308d1fe-ab14691759\n" +
            "Uptime: 3 days, 12 hours, 51 mins\n" +
            "Packages: 65 (apk)\n" +
            "Shell: libproot.so\n" +
            "Terminal: iredandroid.kai\n" +
            "CPU: Cortex-A520*4 + Cortex-A720*3 + Cortex-X4 (8) @ 3.10 GHz\n" +
            "Memory: 6.75 GiB / 7.39 GiB (91%)"

    val sandboxTerminalLines = listOf(
        TerminalLine.Command("fastfetch"),
        TerminalLine.Output(fastfetchOutput),
    )

    val settingsTools = SettingsUiState(
        currentTab = SettingsTab.Tools,
        tools = getPlatformToolDefinitions().toImmutableList(),
        mcpServers = persistentListOf(
            McpServerUiState(
                id = "context7",
                name = "Context7",
                url = "https://context7.liam.sh/mcp",
                isEnabled = true,
                connectionStatus = McpConnectionStatus.Connected,
                tools = persistentListOf(),
            ),
            McpServerUiState(
                id = "manifold_markets",
                name = "Manifold Markets",
                url = "https://api.manifold.markets/v0/mcp",
                isEnabled = true,
                connectionStatus = McpConnectionStatus.Connected,
                tools = persistentListOf(),
            ),
        ),
    )

    // --- Localized data loading for StoreScreenshotTest ---

    private fun loadJson(locale: String): JsonObject {
        val stream = ScreenshotTestData::class.java.getResourceAsStream("/screenshot-data/$locale.json")
            ?: error("Missing screenshot data for locale: $locale")
        val text = stream.bufferedReader().use { it.readText() }
        return Json.parseToJsonElement(text).jsonObject
    }

    fun localizedChatWithMessages(locale: String): ChatUiState {
        val json = loadJson(locale)
        val chat = json["chatWithMessages"]!!.jsonObject
        return ChatUiState(
            actions = noOpChatActions,
            history = persistentListOf(
                History(
                    id = "1",
                    role = History.Role.USER,
                    content = chat["userMessage"]!!.jsonPrimitive.content,
                ),
                History(
                    id = "2",
                    role = History.Role.ASSISTANT,
                    content = chat["assistantMessage"]!!.jsonPrimitive.content,
                ),
            ),
        )
    }

    fun localizedChatWithDynamicUi(locale: String): ChatUiState {
        val json = loadJson(locale)
        val chat = json["chatWithDynamicUi"]!!.jsonObject
        return ChatUiState(
            actions = noOpChatActions,
            history = persistentListOf(
                History(
                    id = "1",
                    role = History.Role.USER,
                    content = chat["userMessage"]!!.jsonPrimitive.content,
                ),
                History(
                    id = "2",
                    role = History.Role.ASSISTANT,
                    content = chat["assistantMessage"]!!.jsonPrimitive.content,
                ),
            ),
        )
    }
}
