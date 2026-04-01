# Dynamic UI (kai-ui)

**Last verified:** 2026-04-01

AI-generated interactive UI layouts rendered inline in chat messages. The AI produces JSON-based layout definitions wrapped in `kai-ui` code fences. Compose renders them natively with support for forms, buttons, and multi-step flows. Enabled by default; users can disable it in Settings, which removes the instructions from the system prompt. Change applies to new conversations. Parsing and rendering stay active regardless so existing messages with kai-ui blocks always render.

## Concepts

### Layout Blocks

A `kai-ui` code fence inside an assistant message contains a JSON object describing a component tree. The parser splits messages into markdown segments and UI segments, rendered sequentially. Supports both single-object JSON and multi-line NDJSON (one JSON object per line, automatically wrapped in a column).

### Component Types

- **Layout**: column, row, card, spacer, divider
- **Content**: text (with headline/title/body/caption styles), image
- **Interactive**: button (filled variant only), text input, checkbox, select dropdown
- **Data**: list, table

### Actions

Buttons carry an action that fires on click:

- **callback** — collects form data from specified input IDs and sends a structured message back to the AI via the normal chat flow
- **toggle** — shows/hides a target element locally without AI roundtrip
- **open_url** — opens a link in the browser

### Interaction Flow

When a callback fires, the renderer collects input values and formats them as a `[UI Interaction]` user message. The AI receives the event name and form data in conversation history and can respond with new UI, text, or tool calls.

### Layout Lifecycle

Only the latest assistant message's layouts are interactive. Older layouts become read-only with disabled buttons and inputs. Form state is local to each layout; cross-step state lives in conversation history.

### Error Handling

Malformed JSON falls back to rendering as a code block. The parser sanitizes common LLM mistakes like extra trailing braces/brackets using stack-based brace matching. Individual malformed lines in multi-line NDJSON are skipped while valid lines still render. TTS strips `kai-ui` blocks entirely.

### Settings

The feature is controlled by the Dynamic UI toggle in Settings (General tab). When disabled, the system prompt omits kai-ui instructions so the AI stops generating them. Parsing and rendering remain active unconditionally.

## Key Files

| File | Purpose |
|------|---------|
| `composeApp/.../ui/dynamicui/KaiUiNode.kt` | Serializable component tree model (all classes @Immutable) |
| `composeApp/.../ui/dynamicui/UiAction.kt` | Action types (callback, toggle, open_url) |
| `composeApp/.../ui/dynamicui/KaiUiParser.kt` | Detects and parses kai-ui fences into segments, sanitizes malformed JSON |
| `composeApp/.../ui/dynamicui/KaiUiRenderer.kt` | Recursive Compose renderer for the component tree |
| `composeApp/.../ui/chat/composables/BotMessage.kt` | Integration point — renders mixed markdown + UI |
| `composeApp/.../ui/chat/ChatActions.kt` | submitUiCallback action |
| `composeApp/.../ui/chat/ChatViewModel.kt` | Formats callback data and routes to ask() |
| `composeApp/.../data/AppSettings.kt` | isDynamicUiEnabled / setDynamicUiEnabled |
| `composeApp/.../data/RemoteDataRepository.kt` | Conditionally includes kai-ui instructions in system prompt |
