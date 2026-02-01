package com.inspiredandroid.kai.screenshots

import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.getPlatformToolDefinitions
import com.inspiredandroid.kai.ui.chat.ChatActions
import com.inspiredandroid.kai.ui.chat.ChatUiState
import com.inspiredandroid.kai.ui.chat.History
import com.inspiredandroid.kai.ui.settings.ConnectionStatus
import com.inspiredandroid.kai.ui.settings.SettingsModel
import com.inspiredandroid.kai.ui.settings.SettingsTab
import com.inspiredandroid.kai.ui.settings.SettingsUiState

object ScreenshotTestData {

    private val noOpChatActions = ChatActions(
        ask = {},
        toggleSpeechOutput = {},
        retry = {},
        clearHistory = {},
        setIsSpeaking = { _, _ -> },
        setFile = { _ -> },
        startNewChat = { },
        resetScrollFlag = {},
    )

    val chatEmptyState = ChatUiState(
        actions = noOpChatActions,
        history = emptyList(),
        isUsingSharedKey = false,
    )

    val chatWithMessages = ChatUiState(
        actions = noOpChatActions,
        history = listOf(
            History(
                id = "1",
                role = History.Role.USER,
                content = "provide a list of biggest ever built moving vehicles",
            ),
            History(
                id = "2",
                role = History.Role.ASSISTANT,
                content = "Here is a list of the **biggest ever built moving vehicles** (focusing on massive mobile/self-propelled land machines, ranked roughly by a combination of physical size, length, weight, and historical/fame recognition):\n" +
                    "\n" +
                    "- **Overburden Conveyor Bridge F60**  \n" +
                    "  Largest by overall physical dimensions / length. Length: ~502 m, Height: ~80 m, Weight: ~13,600 tons. Built in East Germany (1988–1991) for lignite mining overburden removal; moves very slowly on rails.\n" +
                    "\n" +
                    "- **Bagger 293** (bucket-wheel excavator)  \n" +
                    "  Heaviest land vehicle ever built (~14,200–14,196 tons). Height: ~96 m, Length: ~225 m. Built in 1995 by TAKRAF; Guinness record holder for largest/heaviest terrestrial excavator; removes up to ~240,000 tons of material per day.\n" +
                    "\n" +
                    "- **Bagger 288** (bucket-wheel excavator)  \n" +
                    "  Previously the heaviest land vehicle record holder. Height: ~96 m, Length: ~220 m, Weight: ~13,500 tons. Built in 1978 by Krupp/TAKRAF; famous for its 22 km relocation crossing roads, rivers, and highways.\n" +
                    "\n" +
                    "- **NASA Crawler-Transporter** (CT-1 and CT-2)  \n" +
                    "  Heaviest self-powered land vehicle (Guinness record after upgrades). Weight: ~2,721–3,016 tons, Dimensions: 40 m × 35 m. Built in 1965; transports rockets/spacecraft at Kennedy Space Center (max speed ~1–2 mph).\n" +
                    "\n" +
                    "- **BelAZ-75710** (mining dump truck)  \n" +
                    "  Largest truck by payload capacity (~450 tons). Empty weight: ~360 tons, Length: ~20.6 m. Modern ultra-class haul truck; one of the biggest wheeled moving vehicles.\n" +
                    "\n" +
                    "- **LeTourneau L-2350** (wheel loader / front-end loader)  \n" +
                    "  Largest earthmover/loader by Guinness records. Massive bucket capacity; tire diameter ~4 m. Used in heavy mining; one of the biggest wheeled loaders.\n" +
                    "\n" +
                    "Other notable huge moving machines include:\n" +
                    "- Big Muskie (historical stripping shovel, ~13,500 tons, dismantled)\n" +
                    "- Marion 6360 (\"The Captain\", giant stripping shovel, ~12,700 tons, scrapped)\n" +
                    "- Various giant tunnel boring machines (e.g., Herrenknecht models up to ~17–20 m diameter cutters, though often not fully self-propelled in the same way)\n" +
                    "\n" +
                    "Note: \"Biggest\" depends on the metric—length (F60), weight (Bagger 293), self-powered mobility (NASA Crawler), or payload (BelAZ). These are primarily land-based industrial/mining/transport vehicles rather than ships, aircraft, or trains.",
            ),
        ),
        hasSavedConversations = true,
    )

    val chatWithCodeExample = ChatUiState(
        actions = noOpChatActions,
        history = listOf(
            History(
                id = "1",
                role = History.Role.USER,
                content = "show me beautiful one liners for kotlin, swift, c, python, js",
            ),
            History(
                id = "2",
                role = History.Role.ASSISTANT,
                content = """Here are some short, beautiful one-liners in each language (2025 edition 😄):
                    
Kotlin

```kotlin
val fibs = generateSequence(0 to 1) { it.second to it.first + it.second }.map { it.first }
```

Swift

```swift
let fizzBuzz = (1...100).map { $0 % 15 == 0 ? "FizzBuzz" : $0 % 3 == 0 ? "Fizz" : $0 % 5 == 0 ? "Buzz" : "\($0)" }
```

C

```c
for(int i=1;i<101;)printf(i%3?"":"Fizz"),printf(i%5?"":"Buzz")||printf("%d",i),puts(""),i++);
```

Python

```python
print(*(f"{i}:{'Fizz'*(i%3<1)+'Buzz'*(i%5<1)or i}"for i in range(1,101)),sep='\n')
```

JavaScript

```javascript
[...Array(100)].map((_,i)=>console.log((++i%3?'':'Fizz')+(i%5?'':'Buzz')||i))
```""",
            ),
        ),
        hasSavedConversations = true,
    )

    val freeConnected = SettingsUiState(
        currentTab = SettingsTab.Services,
        currentService = Service.Free,
        services = Service.all,
        connectionStatus = ConnectionStatus.Connected,
    )

    val settingsTools = SettingsUiState(
        currentTab = SettingsTab.Tools,
        tools = getPlatformToolDefinitions(),
    )
}
