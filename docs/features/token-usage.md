# Token Usage & Context Window

**Last verified:** 2026-10-29

Chat surfaces how many tokens the last assistant turn used and how full the model's context window is, so users can tell when a conversation is approaching the provider's limit.

## Concepts

### Token Usage

Prompt, completion, and total token counts reported by the provider on the response. Only providers that return a `usage` object expose it; when absent, no indicator is shown for that turn.

- OpenAI-compatible chat completions: `usage.prompt_tokens`, `usage.completion_tokens`, `usage.total_tokens`.
- OpenAI Responses API: `usage.input_tokens`, `usage.output_tokens`, `usage.total_tokens`.

Tool-loop turns accumulate usage on the final answer-bearing call: intermediate tool-call turns do not each display a counter.

### Context Window

The model's maximum input length in tokens, resolved from the curated model catalog (falling back to the default when a model is unknown). The ratio is `totalTokens / contextWindow`, capped at 100%.

## Behavior

- Each assistant message renders a compact footer: `Tokens: <prompt>+<completion>=<total> · Context: <window> (<percent>%)` when usage is available.
- The context segment is omitted when the window is unknown (e.g. free-tier models with no catalog entry).
- Starting a new request clears the previous turn's usage; history entries keep their own counts so scrolling back shows per-response usage.
- The ratio is informational only: context trimming and compaction still run on their own thresholds and never depend on this display.

## Key Files

| File | Purpose |
|---|---|
| `composeApp/src/commonMain/.../network/dtos/openaicompatible/OpenAICompatibleChatResponseDto.kt` | `Usage` DTO for chat completions |
| `composeApp/src/commonMain/.../network/dtos/openairesponses/OpenAIResponsesResponseDto.kt` | `Usage` DTO for the Responses API |
| `composeApp/src/commonMain/.../data/RemoteDataRepository.kt` | Extracts usage, converts to `TokenUsage`, exposes `tokenUsage` and stamps each assistant `History` |
| `composeApp/src/commonMain/.../data/DataRepository.kt` | `tokenUsage` state and `clearTokenUsage()` |
| `composeApp/src/commonMain/.../data/ServiceEntry.kt` | Carries the active model's context window to the chat UI |
| `composeApp/src/commonMain/.../ui/chat/ChatUiState.kt` | `History.tokenUsage` |
| `composeApp/src/commonMain/.../ui/chat/composables/BotMessage.kt` | Token/context footer |
| `composeApp/src/commonMain/.../ui/chat/ChatScreen.kt` | Passes usage and window into the message composable |
