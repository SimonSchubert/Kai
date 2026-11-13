# Thinking Effort

**Last verified:** 2026-10-29

Models that support extended reasoning accept an effort level. Kai lets the user choose it per model, so a quick question can run cheap while a hard task can think longer.

## Concepts

### Thinking Effort

One of `NONE`, `LOW`, `MEDIUM`, `HIGH`, stored per model id. `MEDIUM` is the default so existing users see no behavior change until they pick a level.

### Provider Translation

| Provider | Wire field |
|---|---|
| OpenAI-compatible (GPT-5 family) | `reasoning_effort` on `/v1/chat/completions` |
| OpenAI Responses API (GPT-5.6+) | `reasoning.effort` on `/v1/responses` |
| Anthropic, Gemini | Not sent — those APIs use their own thinking budgets, which this build does not translate yet |

`NONE` is never serialized: the field is omitted entirely so providers with strict request validators do not reject an unknown value.

## Behavior

- Settings → Services → a configured provider shows a row of effort chips under the model picker once a model is selected.
- The choice is persisted by model id, so switching models restores each model's previous level.
- The effort is applied to every request path: plain chat, the tool-calling loop, the tool-loop bailout, and the Responses API variant.
- Models that do not reason simply ignore or reject the field depending on provider policy; the app does not try to detect support per model beyond the provider family.

## Key Files

| File | Purpose |
|---|---|
| `composeApp/src/commonMain/.../data/Service.kt` | `ThinkingEffort` enum |
| `composeApp/src/commonMain/.../data/AppSettings.kt` | Per-model persistence (`thinking_effort_<modelId>`) |
| `composeApp/src/commonMain/.../data/DataRepository.kt` | Repository surface |
| `composeApp/src/commonMain/.../data/RemoteDataRepository.kt` | Reads the effort per request, applies it to chat/Responses/bailout calls |
| `composeApp/src/commonMain/.../network/Requests.kt` | `reasoningEffort` parameter on both request builders |
| `composeApp/src/commonMain/.../network/dtos/openaicompatible/OpenAICompatibleChatRequestDto.kt` | `reasoning_effort` field |
| `composeApp/src/commonMain/.../network/dtos/openairesponses/OpenAIResponsesRequestDto.kt` | `reasoning.effort` field |
| `composeApp/src/commonMain/.../ui/settings/SettingsUiState.kt` | `SettingsModel.thinkingEffort` |
| `composeApp/src/commonMain/.../ui/settings/ServicesSettings.kt` | Effort chip selector |
| `composeApp/src/commonMain/.../ui/settings/SettingsViewModel.kt` | Persists the selection and updates state |
