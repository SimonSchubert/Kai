# Reasoning Content Handling

**Last verified:** 2026-05-20

Reasoning-capable models (DeepSeek R1, GLM thinking, Qwen thinking, Kimi thinking, Magistral, gpt-oss, etc.) return their chain-of-thought separately from the final answer. Kai handles reasoning along two axes: **wire-side** (whether to echo the trace back to the provider on the next request) and **display-side** (whether to show it to the user in the chat UI). When a turn also contains `tool_calls`, some providers require the chain-of-thought to be echoed back to preserve reasoning continuity across the tool round-trip — and others strictly reject the same field. This page documents what each provider does, what Kai sends, and where we trade fidelity for simplicity.

## Why per-provider handling

OpenAI's chat-completions schema has no standardized place for prior-turn reasoning, so each provider invented their own. The result is three sources of variation:

- **Field name** — `reasoning_content` (DeepSeek lineage), `reasoning` (OpenRouter/Cerebras lineage), `reasoning_details[]` (preserves thought signatures for Anthropic/Gemini via OpenRouter), or embedded `<think>...</think>` tags inside `content` (MiniMax M2 native, older Magistral).
- **Whether echoing back is required** — some providers reconstruct internal state from it (Z.AI Coding Plan, OpenCode Zen via DeepSeek route, Kimi `k2.6` with `thinking.keep="all"`); others accept it as a no-op; others reject any unknown field outright.
- **Paired flags** — preservation sometimes requires an additional request flag (Z.AI `clear_thinking: false`, Fireworks `reasoning_history: "preserved"`).

## Outgoing assistant-message behavior matrix

Behavior of each provider when an `assistant`-role message with prior `tool_calls` carries a `reasoning_content` field.

| Provider | Status | Wire field expected | Notes | Source |
|---|---|---|---|---|
| Groq | **Rejected** | n/a — strip on send | Strict schema validator: `400 ('messages.N': property 'reasoning_content' is unsupported)` on any unknown assistant-message field | [console.groq.com/docs/reasoning](https://console.groq.com/docs/reasoning) |
| DeepSeek | **Required** | `reasoning_content` | Thinking-mode models (deepseek-chat, deepseek-v3.2, deepseek-v4-pro) return 400 (`The reasoning_content in the thinking mode must be passed back to the API`) if the field is missing on a follow-up turn after tool_calls. The legacy `deepseek-reasoner` doesn't expose tools so the gate `if (toolCalls != null)` keeps it unaffected | [api-docs.deepseek.com/guides/thinking_mode](https://api-docs.deepseek.com/guides/thinking_mode) |
| Cerebras | **Rejected (wrong field)** | `reasoning` (not `reasoning_content`) | GLM-4.7 multi-step returns 400 when sent `reasoning_content`; expects `reasoning`. Kai sends neither (mode = `NONE`) | [inference-docs.cerebras.ai/api-reference/chat-completions](https://inference-docs.cerebras.ai/api-reference/chat-completions) |
| Z.AI Coding Plan | **Required** | `reasoning_content` | Preserved Thinking is on by default on `/api/coding/paas/v4`; dropping the field breaks reasoning coherence | [docs.z.ai/guides/capabilities/thinking-mode](https://docs.z.ai/guides/capabilities/thinking-mode) |
| OpenCode Zen (DeepSeek route) | **Required** | `reasoning_content` | Pass-through gateway. When routing to DeepSeek-V4 Pro thinking, once any assistant message carries reasoning, all subsequent assistant messages must | [opencode.ai/docs/zen/](https://opencode.ai/docs/zen/) |
| Moonshot / Kimi | **Required for `kimi-k2.6` with `thinking.keep="all"`** | `reasoning_content` | Only that specific model + flag combination requires the echo. Other Kimi thinking models accept it as a no-op. Kai does not currently set `thinking.keep` | [platform.kimi.com/docs/api/chat](https://platform.kimi.com/docs/api/chat) |
| Fireworks AI | **Accepted (documented)** | `reasoning_content` | Officially supported field on `ChatMessage`. Full preservation also requires `reasoning_history: "preserved"` on the request — Kai does not set this | [docs.fireworks.ai/api-reference/post-chatcompletions](https://docs.fireworks.ai/api-reference/post-chatcompletions) |
| Z.AI standard | **Accepted (documented, inert without flag)** | `reasoning_content` | Preserved Thinking is opt-in on `/api/paas/v4`; without `clear_thinking: false` (which Kai does not send) the echo is ignored | [docs.z.ai/guides/capabilities/thinking-mode](https://docs.z.ai/guides/capabilities/thinking-mode) |
| OpenRouter | **Accepted (alias)** | Canonical `reasoning`; `reasoning_content` is a documented alias. Anthropic/Gemini-via-OR need `reasoning_details[]` with thought signatures, which Kai does not send | [openrouter.ai/docs/guides/best-practices/reasoning-tokens](https://openrouter.ai/docs/guides/best-practices/reasoning-tokens) |
| LongCat | **Tolerated (undocumented)** | Schema documents `role` + `content` only; field is passed through silently | [longcat.chat/platform/docs/APIDocs.html](https://longcat.chat/platform/docs/APIDocs.html) |
| Venice AI | **Tolerated (undocumented)** | Pass-through policy: "Request fields not listed may be passed through but are not validated" | [docs.venice.ai](https://docs.venice.ai) |
| MiniMax M2 | **Tolerated but wrong mechanism** | Native mode expects `<think>...</think>` inside `content`; split mode expects `reasoning_details`. Top-level `reasoning_content` is undocumented and likely ignored | [platform.minimax.io/docs/guides/text-m2-function-call](https://platform.minimax.io/docs/guides/text-m2-function-call) |
| xAI, NVIDIA, Mistral, Ollama Cloud, Together, HuggingFace, DeepInfra, AIHubMix, Public AI, OpenAI, Free, OpenAI-Compatible API | **Accepted (silent ignore)** | Either documented or behave as permissive OpenAI-compatible proxies that drop unknown fields | (per-provider docs) |
| Anthropic, Gemini, LiteRT | Out of scope | These use entirely separate request DTOs (Anthropic Messages API, Gemini Generative Language API, on-device LiteRT). Reasoning is handled inside those code paths, not via `reasoning_content`. | — |

## What Kai does today

Kai gates the field on `Service.reasoningRequestMode` (`NONE` or `REASONING_CONTENT`). When `REASONING_CONTENT` is set and the prior assistant turn carried `tool_calls`, Kai emits the field on the next request.

Services currently set to `REASONING_CONTENT`: DeepSeek, OpenRouter, LongCat, Venice, Moonshot, Z.AI, Z.AI Coding Plan, MiniMax, Fireworks, OpenCode.

All other services use the default `NONE` (the field is stripped on send). This is the safe default — any service we don't yet have evidence about will not regress.

The chain-of-thought is preserved on `History.reasoningContent` regardless of the wire-side decision so the UI can render thinking traces independently of what gets transmitted on the next request. This applies to assistant turns received over the OpenAI-compatible path; the Anthropic and Gemini paths have their own thinking handling and do not currently populate this field. Capture happens going forward — conversations saved before the persistence support was added will not retroactively gain reasoning content on reload.

## Display in chat UI

The chain-of-thought is always rendered when present. Each assistant bubble with reasoning content prepends a collapsible "Thinking" section above the answer: collapsed by default, the first line of the most recent reasoning segment shown as a preview; expanded reveals the full trace in a dim blockquote. Thinking-only turns (where the model returned reasoning but no answer, typically as a precursor to a tool call) surface as standalone reasoning bubbles while in flight; once the answer arrives, they're absorbed into the answer's grouped section so a multi-tool response shows a single "Thinking" disclosure rather than several.

Reasoning is only visible on messages captured after persistence support landed, and only on the OpenAI-compatible path. Old conversations saved before persistence appear unchanged.

## Known gaps

Documented here so future work has a starting point. None of these are bugs today; they are fidelity improvements:

- **OpenRouter `reasoning_details[]`** — needed to preserve thought signatures for Anthropic and Gemini models routed via OpenRouter. Without them, those models lose continuity across tool calls.
- **MiniMax M2 native mode** — should embed `<think>...</think>` inside `content` rather than (or in addition to) sending a top-level field; currently the field is silently ignored.
- **MiniMax M2 split mode** — alternative path uses `reasoning_details` instead.
- **Z.AI standard `clear_thinking: false`** — without this paired request flag, Preserved Thinking is off and our echo is a no-op.
- **Fireworks `reasoning_history: "preserved"`** — without this paired request flag, Fireworks does not actually preserve reasoning across turns.
- **Cerebras `reasoning` field** — Cerebras uses `reasoning`, not `reasoning_content`. Mode is currently `NONE` because we have no `reasoning` field on the request DTO. Adding it would unlock GLM-4.7 multi-step on Cerebras.
- **Per-model dispatch** — `reasoningRequestMode` is per-service. Moonshot is set to `REASONING_CONTENT` because of Kimi `k2.6`, but the flag is inert for older Kimi thinking models. Per-model precision would tighten this.

Adding any of these means either widening `ReasoningRequestMode` (new enum values), adding fields to `OpenAICompatibleChatRequestDto`, adding paired-flag plumbing on the request side, or moving to a per-model handler. None of these have been done because the current binary dispatch covers all known live-broken cases.

## Key Files

| File | Purpose |
|---|---|
| `composeApp/src/commonMain/.../data/Service.kt` | `ReasoningRequestMode` enum + per-service mode assignment |
| `composeApp/src/commonMain/.../ui/chat/ChatUiState.kt` | `History.toGroqMessageDto()` — gates emission of `reasoning_content` based on mode |
| `composeApp/src/commonMain/.../data/RemoteDataRepository.kt` | `buildOpenAIMessages()` — passes `Service.reasoningRequestMode` into the DTO mapper |
| `composeApp/src/commonMain/.../network/dtos/openaicompatible/OpenAICompatibleChatRequestDto.kt` | Request DTO with `@SerialName("reasoning_content")` on assistant messages |
| `composeApp/src/commonMain/.../network/dtos/openaicompatible/OpenAICompatibleChatResponseDto.kt` | Response DTO; reads `reasoning_content` and `reasoning` and normalizes to `effectiveReasoning` |
| `composeApp/src/commonTest/.../ui/chat/ToGroqMessageDtoReasoningTest.kt` | Guards the per-mode emission behavior |
| `composeApp/src/commonMain/.../data/Conversation.kt` | `Conversation.Message.reasoningContent` — persisted reasoning trace for round-tripping across app restarts |
| `composeApp/src/commonMain/.../ui/chat/composables/BotMessage.kt` | Renders the dim-blockquote reasoning section above the answer when `reasoningContent` is supplied |
| `composeApp/src/commonMain/.../ui/chat/ChatScreen.kt` | Groups all reasoning segments in a response under the answer-bearing assistant message; renders standalone thinking-only bubbles for in-flight turns |
