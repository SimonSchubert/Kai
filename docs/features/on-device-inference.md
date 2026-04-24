# On-Device Inference (LiteRT)

**Last verified:** 2026-04-24

Kai can run AI models directly on the user's device using Google's LiteRT LM SDK. This enables fully offline, private inference with no API key, no internet connection, and no cost. Available on Android and Desktop (macOS, Linux, Windows).

## How It Works

Models are downloaded from HuggingFace's litert-community and stored locally on the device. When the user sends a message, the model runs entirely on-device using GPU acceleration (with CPU fallback). The engine initializes on first use (~10 seconds) and stays loaded for 5 minutes of inactivity before automatically releasing memory.

## Available Models

| Model | Size | GPU Memory (Android) | Default Context | Max Context | Tool calling |
|-------|------|---------------------|-----------------|-------------|--------------|
| Gemma 4 E2B IT | 2.58 GB | 676 MB | 4K tokens | 32K tokens | ✅ reliable |
| Gemma 4 E4B IT | 3.65 GB | 710 MB | 4K tokens | 32K tokens | ✅ reliable |
| Qwen3 0.6B | 586 MB | 300 MB | 4K tokens | 32K tokens | ⚠️ chat-only in practice |

Models are `.litertlm` files from the [litert-community](https://huggingface.co/litert-community) organization on HuggingFace.

## Tool support

The application uses **litert-lm's native function calling** (`automaticToolCalling = true` on `ConversationConfig`): each exposed Kai tool is wrapped in an `OpenApiTool` adapter, registered on the conversation, and the engine drives the tool loop internally. The model uses its trained tool format and `chat()` returns the final assistant text after all tool round-trips complete. Tools are available **at any context size** — there's no threshold gating.

Only a small **allowlist** of tools is exposed on-device, because small Gemma models (2-4B params) struggle to emit valid function-call syntax for tools with many parameters or complex value types, and litert-lm's strict ANTLR parser crashes the call when the syntax is malformed.

The allowlist (in `RemoteDataRepository.LOCAL_TOOL_ALLOWLIST`) currently exposes: `get_local_time`, `get_location_from_ip`, `web_search`, `open_url`, `memory_store`, `memory_forget`, `memory_reinforce`, and `execute_shell_command` (when the user has enabled the shell tool in Settings). Email tools, task scheduling (`schedule_task` / `list_tasks` / `cancel_task`), MCP server tools, structured `memory_learn`, heartbeat-config tools, and `promote_learning` are excluded — they require a remote model.

**Qwen3 0.6B caveat:** the model is wired to the same allowlist but at 0.6 B params it rarely emits valid function-call syntax — it tends to hallucinate answers (e.g. a fictional time) instead of invoking `get_local_time`. Treat Qwen3 as a chat-only model in practice; pick Gemma 4 E2B/E4B for anything that relies on tools.

The system prompt for on-device runs is built directly from the `CHAT_LOCAL` variant of `buildChatSystemPrompt` — it contains only the sections a small Gemma can handle (soul + basic memory guidance + runtime Context block). Memory categories, scheduled tasks, Structured Learning guidance, and kai-ui sections are never composed in.

Interactive UI mode is **not supported** on-device: the kai-ui component schema is too large and too structurally complex for 2-4B Gemma models to reliably produce valid kai-ui JSON. The "Start interactive mode" button in the chat empty-state is hidden when the primary service is on-device, and on-device services are also filtered out of the quick-switch service selector while Interactive Mode is active so a user already in Interactive Mode can't switch to them. Users who need interactive UI should switch to a remote service.

See [system-prompts.md](system-prompts.md) and `ChatSystemPromptBuilderTest` for the full contract.

If the engine throws (e.g. the model does emit malformed tool-call syntax that the ANTLR parser rejects), the application catches the `RuntimeException`, logs it, and retries the call **once** with no tools — the user gets a plain-chat answer instead of a hard error.

## Other limitations

- **No image input** -- the `LocalInferenceEngine` interface only accepts text messages
- **No dynamic UI** -- kai-ui prompts are skipped for on-device runs (the schema is too large for the native template parser)
- **Not available on iOS or web** -- LiteRT LM SDK supports Android and JVM only
- **Requires a 64-bit device** -- the LiteRT-LM AAR only ships `arm64-v8a` and `x86_64` native libraries. On pure 32-bit devices (armeabi-v7a), the LiteRT service card is hidden; the app still works with remote services.

## Model Management

Users manage models through the LiteRT service card in Settings:

- **Download** -- each model card shows a download button with size info; disk space is validated before starting
- **Select** -- radio button appears after download to set the active model
- **Delete** -- trash icon removes the downloaded model file
- **Cancel** -- active downloads can be cancelled
- **Error display** -- download failures (network, disk space, incomplete) are shown inline in the settings UI
- **Context size slider** -- each model has a slider to adjust context size (4K–32K tokens in 1K steps); available before download so users can preview performance impact
- **Performance indicator** -- each model shows a Good/OK/Poor label based on total device RAM vs estimated resident memory at the selected context size. The estimate sums the model file size (proxy for resident weights after mmap/PLE), a per-model baseline for GPU/KV working memory, and a per-token KV cache cost that scales with context. Thresholds: Good >= 2.5x, OK >= 1.85x, Poor < 1.85x of total device RAM -- the extra headroom over 1x accounts for OS reservation and GPU-driver overhead.
- **Free space** -- available device storage is shown below the model list

On Android, downloads run in a foreground service with a notification so they continue when the app is backgrounded. The service is only started once the HTTP connection is established, so a pre-connection failure (e.g. offline) surfaces as an inline error without leaving a promised-but-unfulfilled foreground service. On Desktop, downloads run in a background coroutine.

When the last LiteRT service instance is removed, all downloaded models are automatically deleted.

## Engine Lifecycle

1. **Lazy initialization** -- the engine loads only when the first message is sent
2. **GPU-first** -- attempts GPU backend, falls back to CPU if unavailable
3. **Memory check** -- verifies sufficient RAM (model size + 512 MB headroom) before loading
4. **Persistent across messages** -- stays loaded for the duration of the conversation
5. **Inference timeout** -- individual inference calls are capped at 2 minutes
6. **Auto-release** -- released after 5 minutes of inactivity to free memory (always re-armed, even on errors)
7. **Status indicator** -- the chat shows "Initializing {model name}" with a pulsing dot during engine load

## Platform Differences

| Aspect | Android | Desktop |
|--------|---------|---------|
| Model storage | `context.filesDir/litert_models` | `~/.kai/litert_models` |
| Memory check | `ActivityManager.getMemoryInfo()` | Skipped — desktop OSes manage memory via swap and cache eviction |
| Disk space | `StatFs.availableBytes` | `File.usableSpace` |
| Download notification | Foreground service with notification | No notification (no OS restriction) |

## Fallback Behavior

- LiteRT instances participate in the normal fallback chain
- On unsupported platforms (iOS, web), LiteRT instances are silently skipped
- `askWithTools` (used by heartbeat and scheduling) prefers remote services and falls back to on-device when no remote is configured. The on-device fallback works at any context size, since the simple-tool allowlist has no schema-overhead penalty.

## Key Files

| File | Purpose |
|------|---------|
| `composeApp/src/commonMain/.../data/Service.kt` | `Service.LiteRT` definition with `isOnDevice = true` |
| `composeApp/src/commonMain/.../inference/LocalInferenceEngine.kt` | Platform-agnostic interface for on-device inference |
| `composeApp/src/commonMain/.../inference/InferencePlatform.kt` | `expect` declarations for platform-specific operations |
| `composeApp/src/commonMain/.../inference/LocalInferenceEngineProvider.kt` | `expect` factory, returns `null` on unsupported platforms |
| `composeApp/src/jvmShared/.../inference/LiteRTInferenceEngine.kt` | Shared Android+Desktop implementation wrapping LiteRT LM SDK |
| `composeApp/src/androidMain/.../inference/InferencePlatform.android.kt` | Android platform implementations (storage, memory, notifications) |
| `composeApp/src/desktopMain/.../inference/InferencePlatform.jvm.kt` | Desktop platform implementations (storage, memory) |
| `composeApp/src/androidMain/.../inference/ModelDownloadService.kt` | Android foreground service for background downloads |
| `composeApp/src/commonMain/.../data/RemoteDataRepository.kt` | Inference dispatch, engine initialization status |
| `composeApp/src/commonMain/.../ui/settings/SettingsScreen.kt` | `LiteRTSettings` composable for model management |
