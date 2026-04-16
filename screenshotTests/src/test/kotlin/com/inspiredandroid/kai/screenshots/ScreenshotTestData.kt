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

    val noOpChatActions = ChatActions(
        ask = {},
        toggleSpeechOutput = {},
        retry = {},
        clearHistory = {},
        setIsSpeaking = { _, _ -> },
        addFile = { _ -> },
        removeFile = { _ -> },
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
        enterInteractiveMode = { },
        exitInteractiveMode = { },
        goBackInteractiveMode = { },
        navigateBranch = { _, _ -> },
        editMessage = { _, _ -> },
    )

    val chatEmptyState = ChatUiState(
        actions = noOpChatActions,
        history = persistentListOf(),
        showPrivacyInfo = false,
    )

    private val survivalGameContent =
        "```kai-ui\n" +
            "{\"type\":\"column\",\"spacing\":8,\"children\":[" +
            "{\"type\":\"text\",\"value\":\"\u2694\ufe0f The Goblin Tunnels\",\"style\":\"headline\",\"bold\":true}," +
            "{\"type\":\"text\",\"value\":\"Chapter 1 \u2022 The Beginning\",\"style\":\"caption\",\"color\":\"secondary\"}," +
            "{\"type\":\"row\",\"spacing\":16,\"children\":[" +
            "{\"type\":\"stat\",\"value\":\"20/20\",\"label\":\"HP\"}," +
            "{\"type\":\"stat\",\"value\":\"Lv 1\",\"label\":\"Level\"}," +
            "{\"type\":\"stat\",\"value\":\"50g\",\"label\":\"Gold\"}," +
            "{\"type\":\"stat\",\"value\":\"2\",\"label\":\"DEF\"}" +
            "]}," +
            "{\"type\":\"image\",\"url\":\"resource://orc_survival.png\"}," +
            "{\"type\":\"text\",\"value\":\"The tunnel forks. To the left, faint firelight flickers. To the right, silence \u2014 and a cold draft that makes your torch sputter.\",\"style\":\"body\"}," +
            "{\"type\":\"divider\"}," +
            "{\"type\":\"alert\",\"severity\":\"warning\",\"message\":\"\uD83D\uDC7A Two orcs block the left passage! They haven't noticed you yet.\"}," +
            "{\"type\":\"spacer\",\"height\":4}," +
            "{\"type\":\"button\",\"label\":\"\u2694\ufe0f Attack with sword\",\"variant\":\"filled\",\"action\":{\"type\":\"callback\",\"event\":\"attack\"}}," +
            "{\"type\":\"button\",\"label\":\"\uD83E\uDD2B Sneak past in the shadows\",\"variant\":\"outlined\",\"action\":{\"type\":\"callback\",\"event\":\"sneak\"}}" +
            "]}\n" +
            "```"

    val chatWithMessages = ChatUiState(
        actions = noOpChatActions,
        history = persistentListOf(
            History(
                id = "1",
                role = History.Role.USER,
                content = "lets play a game",
            ),
            History(
                id = "2",
                role = History.Role.ASSISTANT,
                content = survivalGameContent,
            ),
        ),
    )

    private val recipeContent =
        "```kai-ui\n" +
            "{\"type\":\"column\",\"spacing\":8,\"children\":[" +
            "{\"type\":\"text\",\"value\":\"Cacio e Pepe\",\"style\":\"headline\",\"bold\":true}," +
            "{\"type\":\"row\",\"spacing\":8,\"children\":[{\"type\":\"badge\",\"value\":\"\u23f1 20 min\",\"color\":\"secondary\"},{\"type\":\"badge\",\"value\":\"\uD83C\uDF7D 2 servings\",\"color\":\"secondary\"},{\"type\":\"badge\",\"value\":\"\u2b50 4.9/5\",\"color\":\"primary\"}]}," +
            "{\"type\":\"image\",\"url\":\"resource://cacio_e_pepe.png\",\"aspectRatio\":1.5}," +
            "{\"type\":\"text\",\"value\":\"Ingredients\",\"style\":\"title\"}," +
            "{\"type\":\"list\",\"ordered\":false,\"items\":[" +
            "{\"type\":\"text\",\"value\":\"200g tonnarelli or spaghetti\"}," +
            "{\"type\":\"text\",\"value\":\"150g Pecorino Romano, finely grated\"}," +
            "{\"type\":\"text\",\"value\":\"2 tsp black peppercorns\"}," +
            "{\"type\":\"text\",\"value\":\"Salt for pasta water\"}" +
            "]}," +
            "{\"type\":\"divider\"}," +
            "{\"type\":\"text\",\"value\":\"Instructions\",\"style\":\"title\"}," +
            "{\"type\":\"accordion\",\"title\":\"Step 1: Toast pepper & cook pasta\",\"children\":[{\"type\":\"text\",\"value\":\"Toast peppercorns in a dry pan until fragrant, crush coarsely. Boil pasta until al dente, reserve pasta water.\",\"style\":\"body\"}]}," +
            "{\"type\":\"accordion\",\"title\":\"Step 2: Make the sauce\",\"children\":[{\"type\":\"text\",\"value\":\"Mix grated Pecorino with warm pasta water to form a smooth cream.\",\"style\":\"body\"}]}," +
            "{\"type\":\"accordion\",\"title\":\"Step 3: Combine\",\"children\":[{\"type\":\"text\",\"value\":\"Toss hot pasta with pepper off heat. Add Pecorino cream and toss until silky.\",\"style\":\"body\"}]}" +
            "]}\n" +
            "```"

    val chatWithDynamicUi = ChatUiState(
        actions = noOpChatActions,
        history = persistentListOf(
            History(
                id = "1",
                role = History.Role.USER,
                content = "suggest Cacio e Pepe recipe for 2 servings",
            ),
            History(
                id = "2",
                role = History.Role.ASSISTANT,
                content = recipeContent,
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
                selectedModel = SettingsModel(id = "kimi-k2.5", subtitle = "Kimi K2.5", isSelected = true, displayName = "Kimi K2.5"),
            ),
            ConfiguredServiceEntry(
                instanceId = "anthropic",
                service = Service.Anthropic,
                connectionStatus = ConnectionStatus.Connected,
                apiKey = "sk-ant-••••••••••••••••••••••••••••••••",
                selectedModel = SettingsModel(id = "claude-opus-4-6", subtitle = "Claude Opus 4.6", isSelected = true, displayName = "Claude Opus 4.6"),
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
