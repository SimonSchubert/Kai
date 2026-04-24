# Chat & Conversations

**Last verified:** 2026-04-24

Kai's chat system manages the message history, conversation persistence, file attachments, and speech output. Conversations are service-independent — switching providers does not affect which conversation is loaded or restored. Multiple conversations are persisted and browsable via a history sheet.

## Concepts

### Conversation

A persisted chat session containing an id (UUID), message list, timestamps (`createdAt`, `updatedAt`), a title, and a type (`chat`, `heartbeat`, or `interactive`). Conversations are stored in an encrypted file and restored across app launches.

### History

The in-memory message list that drives the UI. Each entry has a role: USER, ASSISTANT, TOOL, or TOOL_EXECUTING. History is the source of truth during a session; it is written to a Conversation on save.

### Conversation Title

Auto-derived from the first user message when a conversation is saved for the first time. Truncated to ~50 characters at a word boundary. Once set, titles are not updated.

## Conversation Lifecycle

- The "current conversation" pointer is persisted across launches: opening the app restores whichever conversation was last active, including an empty new chat the user explicitly started
- If the persisted pointer references a conversation that no longer exists (or is null because the user started a new chat), the app opens to an empty new chat
- "New Chat" clears history, unsets the current conversation pointer, and persists the empty state — so an unused new chat survives an app restart
- A new conversation ID (UUID) is generated on first successful save (after the first assistant response) and immediately becomes the persisted current pointer
- Conversations are saved after each assistant response
- Only the most recent 20 exchanges are persisted per conversation
- Multiple conversations are persisted — starting a new chat preserves previous conversations
- Conversations are service-independent — switching services does not affect which conversation is loaded
- Interactive vs normal chat mode is persisted alongside the current pointer, so an empty interactive chat also survives a restart

## Chat History

- A history icon appears in the top bar when saved conversations other than the current one exist
- Tapping it opens a bottom sheet listing all chat conversations sorted by last updated (newest first)
- Each item shows the title and formatted date
- Non-interactive conversations are outlined with a primary-colored border; interactive-mode conversations get an animated gradient border. The active conversation's title is rendered in the primary color (inactive titles use onBackground)
- Tapping an item loads that conversation and dismisses the sheet
- Each item has a delete button that defers deletion with a snackbar "Undo" option (~4 seconds) before the conversation is permanently removed
- Deleting the active conversation clears the chat
- Heartbeat conversations are included in the history list with a "Heartbeat" label badge, and can also be accessed via the heartbeat banner

## Message Sending

- User message is added to history, then an API call is made via the fallback chain
- Tool calls are executed inline (TOOL_EXECUTING shown during execution, TOOL result stored after)
- On success, the conversation is saved
- On failure, an error is displayed with a retry button

## Cancel

- While a request is in progress, a stop button replaces the send button in the input field
- Clicking stop cancels the ongoing API request and any in-flight tool executions
- After cancellation, the loading state clears and the send button reappears when typing

## Retry & Regenerate

- **Retry** resends the current prompt
- **Regenerate** removes all messages after the last user message, then resends

## File Attachments

Multiple files can be attached to a single prompt. Each file is added one at a time via the file picker or drag-and-drop, and appears as a chip below the input. Clicking a chip removes that specific file from the queue. All queued files are cleared after the prompt is sent. Three categories of files are supported:

### Images
- Attach via file picker or drag-and-drop
- Compressed to JPEG and Base64-encoded
- Maximum raw input size: 50 MB; maximum size after compression: 15 MB — rejected with a size error if exceeded
- Sent as `image_url` (OpenAI-compatible), `image` block (Anthropic), or `inline_data` (Gemini)
- Shown as a preview thumbnail (max 200dp wide) inside the user message bubble
- Clicking the thumbnail opens a full-screen viewer with pinch-to-zoom, double-tap to toggle zoom, pan when zoomed, and a close button in the top-right (also dismissable via Escape/back)

### Text files
- Supports `.txt`, `.md`, `.json`, `.csv`, `.xml`, `.yaml`, `.html`, `.css`, `.js`, `.ts`, `.kt`, `.py`, `.rs`, `.go`, `.c`, `.cpp`, `.swift`, `.sh`, `.sql`, `.toml`, `.ini`, `.log`, `.gradle`, and more
- Maximum size: 100 KB per file
- Content is decoded at send time and concatenated into the user message with a filename header per file
- Works with all providers (content is inlined as text)
- Shown as a filename chip in the user message bubble

### PDFs
- Base64-encoded without compression
- Maximum size: 20 MB — rejected with a size error if exceeded
- Sent as `document` block (Anthropic) or `inline_data` (Gemini); OpenAI-compatible providers have no native PDF support, so PDFs are not offered as an attachment type on those services
- Shown as a filename chip in the user message bubble

### General behavior
- The attachment button is always shown (text files work with all models)
- Unsupported file types (e.g., `.zip`) show an error message
- Files exceeding the per-category size limit show a size error; size is checked by stat before the file is read, so multi-gigabyte attachments are rejected without allocating memory for the full contents
- Long filenames in chips are truncated with an ellipsis while preserving the extension
- File attachments persist across conversation save/restore via an `attachments` list on each message; older conversations saved with a single-file schema are migrated on load

## Speech Output (TTS)

- Toggle in the top bar enables auto-play of new assistant messages
- Per-message play button on assistant messages
- Markdown is stripped before speaking

## Conversation Storage

- Conversations are persisted through the platform's secure settings store (see [encryption.md](encryption.md))
- The full conversation list is serialized as `ConversationsData` (versioned, currently v2)
- Conversations are upserted — updating a conversation replaces the existing entry by ID, new conversations are appended
- Older builds stored conversations in an encrypted `conversations.enc` file (XOR with a 32-byte random key); on first load that file is decrypted and migrated into secure settings, then deleted

## UI Elements

- **Top bar**: New Chat, Chat History, TTS toggle, Settings (on mobile; on non-mobile, Settings is in the navigation tab bar)
- **Scroll to bottom**: a small floating action button (down arrow) appears when the user has scrolled up past the latest messages; tapping it animates back to the bottom
- **Messages**: user (right-aligned, with optional image preview), assistant (Markdown-rendered + action buttons), tool executing (spinner), loading indicator, error with retry
- **Input**: text field, send/stop button, attachment button, file chip
- **Empty state**: animated logo + welcome message
- **Drag-and-drop**: supported for file attachments
- **History sheet**: bottom sheet listing saved conversations with title, date, active highlight, and delete

## Key Files

| File | Purpose |
|---|---|
| `composeApp/src/commonMain/.../data/Conversation.kt` | Conversation and message data classes, type constants |
| `composeApp/src/commonMain/.../data/ConversationStorage.kt` | Serialization, settings-backed persistence, legacy migration |
| `composeApp/src/commonMain/.../data/FileClassification.kt` | File category enum, MIME/extension classifier, size constants, file exceptions |
| `composeApp/src/commonMain/.../data/RemoteDataRepository.kt` | History management, conversation save/restore/delete, title derivation, message sending |
| `composeApp/src/commonMain/.../ui/chat/ChatViewModel.kt` | Chat UI state, send/retry/regenerate/cancel/loadConversation/deleteConversation actions |
| `composeApp/src/commonMain/.../ui/chat/ChatScreen.kt` | Chat UI composables, history sheet and heartbeat banner wiring |
| `composeApp/src/commonMain/.../ui/chat/composables/ChatHistorySheet.kt` | Bottom sheet listing saved conversations |
| `composeApp/src/commonMain/.../ui/chat/composables/HeartbeatBanner.kt` | Dismissable banner for heartbeat notifications |
| `composeApp/src/commonMain/.../ui/chat/composables/TopBar.kt` | Top bar with new chat, history, TTS, and settings icons |
| `composeApp/src/commonMain/.../ui/chat/composables/QuestionInput.kt` | Text input with send/stop button |
