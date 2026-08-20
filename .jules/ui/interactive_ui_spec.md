# Generative UI ("Interactive UI") Specification

This document details the exact technical implementation, state management, and file mappings for the Generative UI feature in Kai. This is intended as a development reference document with explicit code bindings.

## Overview
Generative UI allows the AI to render interactive compose components using JSON structures within `kai-ui` code fences instead of just text/markdown. It operates in two primary modes:
1. **Dynamic UI:** Inline components mixed with standard Markdown inside standard chat messages.
2. **Interactive UI Mode:** A full-screen immersive mode where the *entire* AI response is parsed as a single UI layout without visible Markdown.

## File Mappings and Roles

### UI / View Layer
- `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/chat/ChatScreen.kt`
  - **Role:** Routes UI based on mode. When `uiState.isInteractiveMode && !uiState.isRestoring` is true, it renders the full-screen `InteractiveModeScreen`. Otherwise, it falls back to standard `ChatModeScreen`.
  - **Key Functions:** `InteractiveModeScreen(uiState: ChatUiState)` renders the immersive prompt input, navigation bar (back/exit), and displays the active tool calling/agentic loop interface.
- `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/chat/composables/BotMessage.kt`
  - **Role:** Integration point for parsing and rendering the actual message content.
  - **Key Workflow:** Markdown is parsed, and blocks of type `KaiUiBlock` are intercepted. These are rendered via the `KaiUiRenderer()`.
- `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/chat/ChatUiState.kt`
  - **Role:** Holds the `isInteractiveMode` flag and manages UI state.
- `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/chat/ChatActions.kt`
  - **Role:** Exposes lambdas for controlling the Interactive UI lifecycle (`enterInteractiveMode`, `exitInteractiveMode`, `goBackInteractiveMode`, `submitUiCallback`, `resubmit`).

### State Management & ViewModel
- `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/chat/ChatViewModel.kt`
  - **Role:** Lifecycle and execution of the Interactive UI flow.
  - **Key Functions:**
    - `enterInteractiveMode()` / `exitInteractiveMode()`: Toggles the `isInteractiveMode` flag via `DataRepository`.
    - `goBackInteractiveMode()`: Uses `dataRepository.popLastExchange()` or clears history to step backwards without exiting the mode entirely.
    - `submitUiCallback(event, data)`: Dispatches form values back to the agent as a user prompt (`Pressed: <event>` or `Responded with: ...`).
    - `retryIfNoValidKaiUi()`: Called automatically on submission. If the LLM responds without a `kai-ui` code fence, it injects a system correction prompt (`[SYSTEM] Your previous response failed to render as interactive UI...`) and retries up to 2 times.

### Prompt & System Constraints
- `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/ChatSystemPromptBuilder.kt`
  - **Role:** Modifies the system prompt based on active modes.
  - **Key Functions:** `appendDynamicUiSection()` and `appendInteractiveUiSection()`.
  - **Behavior:** For `INTERACTIVE_UI`, it appends rules forcing the LLM to output ONLY a single `kai-ui` block, strictly detailing that users see *nothing* outside of it. It injects `KAI_UI_COMPONENT_CATALOG` to map JSON schemas.

### Data & Persistence
- `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/DataRepository.kt` & `RemoteDataRepository.kt`
  - **Role:** Manages conversation state and history persistence.
  - **Key Interactions:** Saves interactive sessions with the `TYPE_INTERACTIVE` constant so they automatically re-open in full-screen mode on load. `setInteractiveMode(enabled)` persists the active mode.

### Parsing and Rendering (Dynamic UI Engine)
- `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/dynamicui/KaiUiParser.kt`
  - **Role:** Extracts and sanitizes the `kai-ui` JSON block using `parseUiBlockBody()`. Repairs common JSON structural errors like unclosed braces.
- `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/dynamicui/KaiUiNodeBuilders.kt`
  - **Role:** Uses `parseNode()` to map a `JsonElement` to `KaiUiNode` implementations with heavy fault tolerance. Drops unknown fields or unsupported types quietly.
- `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/dynamicui/KaiUiNode.kt` & `UiAction.kt`
  - **Role:** The serializable domain models (e.g. `ColumnNode`, `RowNode`, `ButtonNode`).
  - **Actions:** `CallbackAction`, `ToggleAction`, `OpenUrlAction`, `CopyToClipboardAction`.
- `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/dynamicui/KaiUiRenderer.kt`
  - **Role:** The recursive Compose rendering loop mapping `KaiUiNode` models to visual components.
  - **Key Functions:** `KaiUiRenderer()` tracks local states (like form inputs `formState: mutableStateMapOf<String, String>()`).

## Execution Flow: Callback Submit
1. The user taps a button configured with a `CallbackAction` inside the rendered `KaiUiRenderer`.
2. The `KaiUiRenderer` triggers the `onCallback(event, data)` lambda.
3. This propagates to `submitUiCallback()` in `ChatViewModel`.
4. The ViewModel formats the payload as standard text ("Pressed: $event" or "Responded with...") and submits it via `DataRepository.ask()`.
5. If `isInteractiveMode` is active and the returning AI response lacks a `kai-ui` block, `ChatViewModel.retryIfNoValidKaiUi()` intercepts the response and prompts the AI to retry formatting as JSON.
6. The new output flows back into the markdown pipeline, is parsed by `KaiUiParser`, and handed to `KaiUiRenderer`.
