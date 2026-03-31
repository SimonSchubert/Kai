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
    )

    val chatEmptyState = ChatUiState(
        actions = noOpChatActions,
        history = persistentListOf(),
        showPrivacyInfo = false,
    )

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
                content = "I'm Kai, a personal assistant designed to stick around and actually get to know you.\n" +
                    "\n" +
                    "What makes me different from typical chatbots:\n" +
                    "\n" +
                    "**I remember.** We talk today, I store what matters\u2014your preferences, projects, how you like things done. Next month, I still know.\n" +
                    "\n" +
                    "**I have opinions.** Ask me what I think and I'll tell you. I'm not a search engine with politeness filters.\n" +
                    "\n" +
                    "**I get things done.** Set reminders, check email, manage your calendar, run shell commands\u2014I'm hooked into your device and your life, not just conversation.\n" +
                    "\n" +
                    "**I learn.** Not just facts, but patterns. What works for you, what doesn't. I get better the longer we work together.\n" +
                    "\n" +
                    "**No corporate filler.** \"I'd be happy to help!\" is dead to me. Just results, clear thinking, and the occasional \"huh, that's interesting.\"\n" +
                    "\n" +
                    "So\u2014what's on your mind?",
            ),
        ),
    )

    private val codeBlocks = """

Kotlin

```kotlin
val fibs = generateSequence(0 to 1) { it.second to it.first + it.second }.map { it.first }
```

Swift

```swift
let fizzBuzz = (1...100).map { ${'$'}0 % 15 == 0 ? "FizzBuzz" : ${'$'}0 % 3 == 0 ? "Fizz" : ${'$'}0 % 5 == 0 ? "Buzz" : "\(${'$'}0)" }
```

C

```c
for(int i=1;i<101;)printf(i%3?"":"Fizz"),printf(i%5?"":"Buzz")||printf("%d",i),puts(""),i++;
```

Python

```python
print(*(f"{i}:{'Fizz'*(i%3<1)+'Buzz'*(i%5<1)or i}"for i in range(1,101)),sep='\n')
```

JavaScript

```javascript
[...Array(100)].map((_,i)=>console.log((++i%3?'':'Fizz')+(i%5?'':'Buzz')||i))
```"""

    val chatWithCodeExample = ChatUiState(
        actions = noOpChatActions,
        history = persistentListOf(
            History(
                id = "1",
                role = History.Role.USER,
                content = "show me beautiful one liners for kotlin, swift, c, python, js",
            ),
            History(
                id = "2",
                role = History.Role.ASSISTANT,
                content = "Here are some short, beautiful one-liners in each language (2025 edition 😄):" + codeBlocks,
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

    fun localizedChatWithCodeExample(locale: String): ChatUiState {
        val json = loadJson(locale)
        val chat = json["chatWithCodeExample"]!!.jsonObject
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
                    content = chat["codeIntro"]!!.jsonPrimitive.content + codeBlocks,
                ),
            ),
        )
    }
}
