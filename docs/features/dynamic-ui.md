# Dynamic UI (kai-ui)

**Last verified:** 2026-04-22

AI-generated interactive UI layouts rendered inline in chat messages. The AI produces JSON-based layout definitions wrapped in `kai-ui` code fences. Compose renders them natively with support for forms, buttons, and multi-step flows. Enabled by default; users can disable it in Settings, which removes the instructions from the system prompt. Change applies to new conversations. Parsing and rendering stay active regardless so existing messages with kai-ui blocks always render.

## Concepts

### Layout Blocks

A `kai-ui` code fence inside an assistant message contains a JSON object describing a component tree. The unified markdown parser treats kai-ui fences as first-class AST blocks, alongside headings, paragraphs, and other markdown; the renderer dispatches each block to its composable. Supports both single-object JSON and multi-line NDJSON (one JSON object per line, automatically wrapped in a column).

### Component Types

- **Layout**: column, row, card, box, divider (spacing between children is fixed by the renderer — the LLM does not control it)
- **Content**: text (with headline/title/body/caption styles), image (optional aspect ratio to prevent distortion on wide screens), icon (curated material icon set or any emoji), code (syntax-highlighted block with a built-in copy-to-clipboard icon in the top-right corner)
- **Interactive**: button (filled/outlined/text/tonal variants), text input, checkbox, switch, select dropdown, radio group, slider, chip group (single-select, multi-select, or display-only tags)
- **Feedback**: progress (determinate/indeterminate), countdown (relative duration with optional expiry action), alert (info/success/warning/error)
- **Navigation**: tabs (tabbed content), accordion (collapsible sections)
- **Display**: quote (blockquote with accent border), badge (colored count/status pill), stat (large metric display), avatar (circular image or initials)
- **Data**: list, table

### Actions

Buttons carry an action that fires on click (chip groups are form inputs, not action carriers):

- **callback** — collects form data from specified input IDs and sends a structured message back to the AI via the normal chat flow (e.g. "Pressed: event_name" or "Responded with: key: value"). The AI then responds with text or more UI. The prompt explicitly tells the AI to only offer callback buttons for actions it can fulfill — callbacks do not trigger system operations like printing, file export, or downloads. Clipboard access is available via the dedicated copy action below.
- **toggle** — shows/hides a target element locally without AI roundtrip
- **open_url** — opens a link in the browser
- **copy_to_clipboard** — writes a literal string to the system clipboard locally, no AI roundtrip. A button carrying this action always renders as a compact clipboard icon button regardless of its variant; the button's label is ignored. Intended for surfacing copyable values (snippets, commands, tokens) alongside the content they belong to.

### Interaction Flow

When a callback fires, the renderer collects input values and formats them as a `[UI Interaction]` user message. The AI receives the event name and form data in conversation history and can respond with new UI, text, or tool calls. While the callback is in flight, the clicked button shows an inline circular spinner in place of its label; other buttons in the same layout become disabled.

The resulting user message in the chat renders as a **frozen snapshot** of the form the user just submitted — the originating kai-ui is re-rendered non-interactively with the submitted values injected into the form fields, instead of showing a cryptic "Responded with: key: value" text. The text form is still what the AI receives; only the UI rendering changes. Snapshots are persisted with the conversation, so reloading a saved conversation preserves them. Messages saved before this feature shipped (or plain text submissions without an originating kai-ui) fall back to the normal text bubble.

### Layout Lifecycle

Only the latest assistant message's layouts are interactive. Older layouts become read-only with disabled buttons and inputs. Form state is local to each layout; cross-step state lives in conversation history.

### Error Handling

Errors are absorbed as locally as possible so a single bad field never kills a whole block. The parser first repairs common JSON syntax mistakes (extra trailing braces/brackets via stack-based brace matching, truncated mid-stream responses, `"key="` typos, and missing closing braces between sibling objects in an array — e.g. when the LLM writes `,{` where a key was expected), then walks the resulting tree field-by-field. Each field reader tolerates the wrong value type (objects where a string was expected, bare strings where an object list was expected, string booleans, numeric strings) and falls back to the data-class default if nothing can be salvaged — the node still builds, the offending field is simply missing from the rendered UI. Unknown node types in `children` or `items` are silently dropped. Only JSON so malformed that the syntax repair can't produce a parseable tree falls back to rendering as a code block. Individual malformed lines in multi-line NDJSON are skipped while valid lines still render. TTS strips `kai-ui` blocks entirely.

### Settings

The feature is controlled by the Dynamic UI toggle in Settings (General tab). When disabled, the system prompt omits kai-ui instructions so the AI stops generating them. Parsing and rendering remain active unconditionally.

## Interactive UI Mode

A dedicated full-screen mode where the AI produces complete screen layouts via kai-ui. The user navigates between screens by clicking buttons — no chat scrolling, no markdown visible.

### Entering Interactive Mode

Users click "Start Interactive UI" on the empty chat state. This enters interactive mode with a text input where they describe what they want (e.g., "Build me a quiz app"). The first message goes to the AI with an enhanced system prompt, and the AI responds with a full-screen UI.

### Screen Navigation

Each AI response replaces the previous screen entirely. Only the latest assistant message's kai-ui renders, taking the full available space. A top bar provides back and exit buttons.

### Back Button

The back button removes the last exchange (user message + assistant response) from conversation history, making the previous assistant response the active screen again. When only one exchange remains, pressing back clears the history and returns to the initial prompt input — interactive mode stays active. To leave interactive mode entirely, the user uses the exit button in the top bar.

### Auto-Retry on Parse Failure

If the AI responds without valid kai-ui blocks, the system automatically retries up to 2 times, sending the parse error details back to the AI so it can fix its JSON.

### Conversation Persistence

Interactive sessions are saved with type `interactive`. Loading an interactive conversation from history automatically re-enters interactive mode.

### System Prompt

In interactive mode, the system prompt instructs the AI to respond ONLY with a single kai-ui code fence — no markdown text outside the fence. The AI is told the user cannot see anything outside the rendered UI.

## Key Files

| File | Purpose |
|------|---------|
| `composeApp/.../ui/dynamicui/KaiUiNode.kt` | Serializable component tree model — 28 node types, all @Immutable |
| `composeApp/.../ui/dynamicui/UiAction.kt` | Action types (callback, toggle, open_url) |
| `composeApp/.../ui/dynamicui/KaiUiParser.kt` | Sanitizes malformed JSON and decodes kai-ui fence bodies via `parseUiBlockBody` |
| `composeApp/.../ui/dynamicui/KaiUiNodeBuilders.kt` | Tolerant field-by-field construction of KaiUiNode instances from JsonElement |
| `composeApp/.../ui/dynamicui/KaiUiRenderer.kt` | Recursive Compose renderer for the component tree, wrapInCard option |
| `composeApp/.../ui/markdown/MarkdownParser.kt` | Unified markdown parser; emits `KaiUiBlock` AST nodes for kai-ui fences |
| `composeApp/.../ui/markdown/MarkdownRenderer.kt` | Compose renderer that dispatches each block (including kai-ui) to its composable |
| `composeApp/.../ui/chat/composables/BotMessage.kt` | Integration point — runs the parser and renders past kai-ui read-only via `isInteractive = false` |
| `composeApp/.../ui/chat/ChatScreen.kt` | Branches between chat mode and interactive mode |
| `composeApp/.../ui/chat/composables/EmptyState.kt` | "Start Interactive UI" button |
| `composeApp/.../ui/chat/ChatActions.kt` | submitUiCallback, enterInteractiveMode, exitInteractiveMode, goBackInteractiveMode |
| `composeApp/.../ui/chat/ChatViewModel.kt` | Interactive mode lifecycle, auto-retry on parse failure |
| `composeApp/.../ui/chat/ChatUiState.kt` | isInteractiveMode state flag |
| `composeApp/.../data/DataRepository.kt` | popLastExchange, setInteractiveMode/isInteractiveModeActive |
| `composeApp/.../data/RemoteDataRepository.kt` | Interactive mode system prompt, TYPE_INTERACTIVE conversation saving |
| `composeApp/.../data/AppSettings.kt` | isDynamicUiEnabled / setDynamicUiEnabled |
| `composeApp/.../data/Conversation.kt` | TYPE_INTERACTIVE conversation type constant |
