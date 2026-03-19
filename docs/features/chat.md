# Chat & Conversations

**Last verified:** 2026-03-19

Kai's chat system manages the message history, conversation persistence, image attachments, and speech output. Conversations are service-independent — switching providers does not affect which conversation is loaded or restored. Multiple conversations are persisted and browsable via a history sheet.

## Concepts

### Conversation

A persisted chat session containing an id (UUID), message list, timestamps (`createdAt`, `updatedAt`), a title, and a type (`chat` or `heartbeat`). Conversations are stored in an encrypted file and restored across app launches.

### History

The in-memory message list that drives the UI. Each entry has a role: USER, ASSISTANT, TOOL, or TOOL_EXECUTING. History is the source of truth during a session; it is written to a Conversation on save.

### Conversation Title

Auto-derived from the first user message when a conversation is saved for the first time. Truncated to ~50 characters at a word boundary. Once set, titles are not updated.

## Conversation Lifecycle

- On launch, the latest conversation (by `updatedAt`) is restored automatically
- "New Chat" clears history and unsets the current conversation ID
- A new conversation ID (UUID) is generated on first successful save (after the first assistant response)
- Conversations are saved after each assistant response
- Only the most recent 20 exchanges are persisted per conversation
- Multiple conversations are persisted — starting a new chat preserves previous conversations
- Conversations are service-independent — switching services does not affect which conversation is loaded

## Chat History

- A history icon appears in the top bar when saved conversations other than the current one exist
- Tapping it opens a bottom sheet listing all chat conversations sorted by last updated (newest first)
- Each item shows the title and formatted date
- The active conversation is highlighted with the primary color
- Tapping an item loads that conversation and dismisses the sheet
- Each item has a delete button that removes the conversation from storage
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

## Image Attachments

- Attach via file picker or drag-and-drop
- Images are compressed and Base64-encoded
- Sent as `image_url` (OpenAI) or `inline_data` (Gemini)
- The attachment button is only shown when the current model supports images
- Attached images are shown as a preview thumbnail (max 200dp wide) inside the user message bubble

## Speech Output (TTS)

- Toggle in the top bar enables auto-play of new assistant messages
- Per-message play button on assistant messages
- Markdown is stripped before speaking

## Conversation Storage

- Conversations are stored in an encrypted file (`conversations.enc`)
- XOR-based encryption with a 32-byte random key stored in app settings
- The full conversation list is serialized as `ConversationsData` (versioned, currently v2)
- Conversations are upserted — updating a conversation replaces the existing entry by ID, new conversations are appended

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
| `composeApp/src/commonMain/.../data/RemoteDataRepository.kt` | History management, conversation save/restore/delete, title derivation, message sending |
| `composeApp/src/commonMain/.../ui/chat/ChatViewModel.kt` | Chat UI state, send/retry/regenerate/cancel/loadConversation/deleteConversation actions |
| `composeApp/src/commonMain/.../ui/chat/ChatScreen.kt` | Chat UI composables, history sheet and heartbeat banner wiring |
| `composeApp/src/commonMain/.../ui/chat/composables/ChatHistorySheet.kt` | Bottom sheet listing saved conversations |
| `composeApp/src/commonMain/.../ui/chat/composables/HeartbeatBanner.kt` | Dismissable banner for heartbeat notifications |
| `composeApp/src/commonMain/.../ui/chat/composables/TopBar.kt` | Top bar with new chat, history, TTS, and settings icons |
| `composeApp/src/commonMain/.../ui/chat/composables/QuestionInput.kt` | Text input with send/stop button |
