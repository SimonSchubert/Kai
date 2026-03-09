# Chat & Conversations

**Last verified:** 2026-03-09

Kai's chat system manages the message history, conversation persistence, image attachments, and speech output. Conversations are service-independent — switching providers does not affect which conversation is loaded or restored.

## Concepts

### Conversation

A persisted chat session containing an id (UUID), message list, and timestamps (`createdAt`, `updatedAt`). Conversations are stored in an encrypted file and restored across app launches.

### History

The in-memory message list that drives the UI. Each entry has a role: USER, ASSISTANT, TOOL, or TOOL_EXECUTING. History is the source of truth during a session; it is written to a Conversation on save.

## Conversation Lifecycle

- On launch, the latest conversation (by `updatedAt`) is restored automatically
- "New Chat" clears history and unsets the current conversation ID
- A new conversation ID (UUID) is generated on first message send
- Conversations are saved after each assistant response
- Only the most recent 20 exchanges are persisted per conversation
- Only the current conversation is persisted — starting a new chat replaces the previous one
- Conversations are service-independent — switching services does not affect which conversation is loaded
- No UI exists to browse or switch between saved conversations

## Message Sending

- User message is added to history, then an API call is made via the fallback chain
- Tool calls are executed inline (TOOL_EXECUTING shown during execution, TOOL result stored after)
- On success, the conversation is saved
- On failure, an error is displayed with a retry button

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

## UI Elements

- **Top bar**: New Chat, TTS toggle, Settings (on mobile; on non-mobile, Settings is in the navigation tab bar)
- **Messages**: user (right-aligned, with optional image preview), assistant (Markdown-rendered + action buttons), tool executing (spinner), loading indicator, error with retry
- **Input**: text field, send button, attachment button, file chip
- **Empty state**: animated logo + welcome message
- **Drag-and-drop**: supported for file attachments

## Key Files

| File | Purpose |
|---|---|
| `composeApp/src/commonMain/.../data/Conversation.kt` | Conversation and message data classes |
| `composeApp/src/commonMain/.../data/ConversationStorage.kt` | Encryption, serialization, file I/O |
| `composeApp/src/commonMain/.../data/RemoteDataRepository.kt` | History management, conversation save/restore, message sending |
| `composeApp/src/commonMain/.../ui/chat/ChatViewModel.kt` | Chat UI state, send/retry/regenerate actions |
| `composeApp/src/commonMain/.../ui/chat/ChatScreen.kt` | Chat UI composables |
