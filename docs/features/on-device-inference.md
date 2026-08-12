# On-Device Inference (LiteRT)

**Last verified:** 2026-08-12

Kai can run AI models directly on the user's device using Google's LiteRT LM SDK. This enables fully offline, private inference with no API key, no internet connection, and no cost. Available on **Android**, **Desktop** (macOS, Linux, Windows), and **iOS**.

## How It Works

Models are downloaded from HuggingFace's litert-community and stored locally on the device. When the user sends a message, the model runs entirely on-device using GPU acceleration (with CPU fallback where the platform supports it). The engine initializes on first use (~10 seconds) and stays loaded for 5 minutes of inactivity before automatically releasing memory.

### Download integrity

Every catalog model is pinned to an **immutable HuggingFace revision** rather than a branch, so the bytes served for a given app version never change. Each catalog entry also records the model file's expected SHA-256 and its exact size.

The digest is computed while the download streams to disk, so verification costs no extra read of a file that can be several gigabytes. A download is accepted only when both the length and the digest match; otherwise the partial file is discarded and the failure is reported inline in Settings. The verified digest is recorded in a marker file stored next to the model, written before the model becomes visible under its final name — a model is never present on disk without its marker.

Verification also runs at load time. Before a model is handed to the inference engine, its marker is compared against the pinned digest; when the marker is missing — which is the case for models downloaded by app versions that predate this check — the file is hashed once and the result recorded, making every later load a string comparison. A model whose bytes do not match is refused rather than loaded, and the user is told to delete it and download it again. Nothing is deleted automatically at load time: a rejected file stays on disk for the user to keep, replace, or remove from Settings. Because the observed digest is recorded either way, a file already known not to match is refused immediately instead of being re-read on every attempt.

**Imported models are exempt from verification.** An import whose file name matches a catalog model takes over that catalog slot, so it would otherwise be measured against a digest it was never meant to match — a user's own copy of a model, or one from a different upstream revision, would be rejected. Such an import records that the file is user-supplied, and the load-time check skips it. Imports that land under their own name carry no digest at all and are likewise never checked. Only files Kai itself downloaded are held to the pinned digest.

Bumping a catalog model means updating its pinned revision, digest, and size together. Pin policy, last HuggingFace check, and the bump playbook live in the knowledge bundle under `docs/knowledge/litert/`. Runtime pins stay in the catalog in code; both are checked together with the `update-litert-models` skill. A unit test fails the build if any entry reverts to a mutable branch URL. A newer `main` on HuggingFace is not applied until product asks to bump.

## Available Models

| Model | Size | GPU Memory (Android) | Default Context | Max Context | Tool calling |
|-------|------|---------------------|-----------------|-------------|--------------|
| Gemma 4 E2B IT | 2.59 GB | 676 MB | 4K tokens | 32K tokens | ✅ reliable |
| Gemma 4 E4B IT | 3.66 GB | 710 MB | 4K tokens | 32K tokens | ✅ reliable |
| Gemma 4 12B IT | 6.55 GB | 4000 MB | 8K tokens | 32K tokens | ✅ reliable |
| Qwen3 0.6B | 614 MB (~586 MiB) | 300 MB | 4K tokens | 32K tokens | ⚠️ chat-only in practice |

Models are `.litertlm` files from the [litert-community](https://huggingface.co/litert-community) organization on HuggingFace. Sizes are the exact byte counts recorded in the catalog, which are checked against the downloaded file.

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
- **Not available on web** -- the WASM build returns no local engine. iOS uses a platform-specific LiteRT bridge rather than the Android/JVM AAR.
- **Requires a 64-bit device (Android)** -- the LiteRT-LM AAR only ships `arm64-v8a` and `x86_64` native libraries. On pure 32-bit devices (armeabi-v7a), the LiteRT service card is hidden; the app still works with remote services.
- **Requires AVX2 on x86_64 Linux** -- the LiteRT native binary is compiled with AVX2+ instructions, so older CPUs (e.g. Intel Ivy Bridge / 3rd-gen Core) would SIGILL on model load. On Linux desktop, the engine probes `/proc/cpuinfo` for the `avx2` flag and hides the LiteRT service card when missing. Remote services are unaffected.

## Model Management

Users manage models through the LiteRT service card in Settings:

- **Download** -- each model card shows a download button with size info; disk space is validated before starting
- **Import** -- a file picker accepts any `.litertlm` already on the device (e.g. from Downloads, Files, or another app that can export the file). The file is **copied** into Kai's private model storage (streaming — never loaded fully into memory). If the file name matches a catalog model, it fills that catalog slot and is marked as user-supplied so it is exempt from the integrity check; otherwise it appears under an **Imported** section with synthetic context/performance defaults
- **Select** -- radio button appears after download/import to set the active model; a successful import auto-selects the new model
- **Delete** -- trash icon removes the downloaded or imported model file
- **Cancel** -- active downloads and imports can be cancelled
- **Error display** -- download and import failures (network, disk space, incomplete, failed integrity check, invalid extension) are shown inline in the settings UI. A model refused at load time reports a separate integrity error in the chat area, pointing the user at Delete and re-download
- **Context size slider** -- each model has a slider to adjust context size (starting at the model's default up to 32K tokens in 1K steps); available before download so users can preview performance impact. Gemma 4 12B defaults to 8K (higher than the 4K default used for the smaller E2B/E4B models). Imported custom models default to 4K (max 32K)
- **Performance indicator** -- each model shows a Good/OK/Poor label based on total device RAM vs estimated resident memory at the selected context size. The estimate sums the model file size (proxy for resident weights after mmap/PLE), a per-model baseline for GPU/KV working memory, and a per-token KV cache cost that scales with context. Thresholds: Good >= 2.5x, OK >= 1.85x, Poor < 1.85x of total device RAM -- the extra headroom over 1x accounts for OS reservation and GPU-driver overhead. Custom imports use a size-based heuristic for the GPU baseline
- **Free space** -- available device storage is shown below the model list

On Android, downloads run in a foreground service with a notification so they continue when the app is backgrounded. The service is only started once the HTTP connection is established, so a pre-connection failure (e.g. offline) surfaces as an inline error without leaving a promised-but-unfulfilled foreground service. On Desktop, downloads run in a background coroutine. Import always copies into app storage (same pattern as Gallery / PocketPal); Kai cannot silently scan other apps' private model directories.

When the last LiteRT service instance is removed, all downloaded and imported models are automatically deleted.

## Engine Lifecycle

1. **Lazy initialization** -- the engine loads only when the first message is sent
2. **GPU-first** -- attempts GPU backend, falls back to CPU if unavailable (where the platform supports both)
3. **Memory check (Android/JVM path)** -- refuses to load when available memory is below a fixed **512 MB** headroom floor (not model-size + headroom). Desktop skips the check and reports effectively unlimited available memory so the OS swap/cache path is trusted
4. **Persistent across messages** -- stays loaded for the duration of the conversation
5. **Inference timeout** -- individual inference calls are capped at 2 minutes
6. **Auto-release** -- released after 5 minutes of inactivity to free memory (always re-armed, even on errors)
7. **Status indicator** -- the chat shows "Initializing {model name}" with a pulsing dot during engine load
8. **Stop during load** -- the native model load cannot be interrupted; pressing stop cancels the message, and the load finishes in the background without marking the engine as failed. A follow-up message waits for the in-flight load instead of starting a second one

## Platform Differences

| Aspect | Android | Desktop | iOS |
|--------|---------|---------|-----|
| Model storage | `context.filesDir/litert_models` (catalog under `{id}/` with its digest marker alongside, imports under `imports/`) | `~/.kai/litert_models` (same layout) | App sandbox path via the iOS LiteRT bridge (same layout) |
| Memory check | `ActivityManager.getMemoryInfo()` vs 512 MB floor | Skipped — desktop OSes manage memory via swap and cache eviction | Platform bridge |
| Disk space | `StatFs.availableBytes` | `File.usableSpace` | Platform bridge |
| Download notification | Foreground service with notification | No notification (no OS restriction) | Platform bridge |
| Model import | FileKit file picker → stream-copy into app storage | Same | Same |

## Fallback Behavior

- LiteRT instances participate in the normal fallback chain
- On unsupported platforms (iOS, web), LiteRT instances are silently skipped
- `askWithTools` (used by heartbeat and scheduling) prefers remote services and falls back to on-device when no remote is configured. The on-device fallback works at any context size, since the simple-tool allowlist has no schema-overhead penalty.

## Key Files

| File | Purpose |
|------|---------|
| `composeApp/src/commonMain/.../data/Service.kt` | `Service.LiteRT` definition with `isOnDevice = true` |
| `composeApp/src/commonMain/.../inference/LocalInferenceEngine.kt` | Platform-agnostic interface for on-device inference |
| `composeApp/src/commonMain/.../inference/LocalModelCatalog.kt` | Bundled model list (pinned revisions, expected digests, sizes, GPU baselines, context defaults) and digest-comparison helpers |
| `docs/knowledge/litert/` | OKF bundle: pin policy, attested commit/digest/size snapshot, refresh playbook |
| `composeApp/src/commonMain/.../inference/LocalModelImport.kt` | Import path helpers, custom model id/filename sanitization, synthetic metadata |
| `composeApp/src/commonMain/.../inference/InferencePlatform.kt` | `expect` declarations for platform-specific operations |
| `composeApp/src/commonMain/.../inference/LocalInferenceEngineProvider.kt` | `expect` factory, returns `null` on unsupported platforms |
| `composeApp/src/jvmShared/.../inference/LiteRTInferenceEngine.kt` | Shared Android+Desktop implementation wrapping LiteRT LM SDK |
| `composeApp/src/androidMain/.../inference/InferencePlatform.android.kt` | Android platform implementations (storage, memory, notifications) |
| `composeApp/src/desktopMain/.../inference/InferencePlatform.jvm.kt` | Desktop platform implementations (storage, memory) |
| `composeApp/src/iosMain/.../inference/IosLiteRTInferenceEngine.kt` | iOS LiteRT engine implementation |
| `composeApp/src/iosMain/.../inference/LocalInferenceEngineProvider.ios.kt` | iOS factory wiring |
| `composeApp/src/androidMain/.../inference/ModelDownloadService.kt` | Android foreground service for background downloads |
| `composeApp/src/commonMain/.../data/RemoteDataRepository.kt` | Inference dispatch, engine initialization status, local tool allowlist |
| `composeApp/src/commonMain/.../network/NetworkExceptions.kt` | Maps inference failures, including a failed integrity check, to user-facing errors |
| `composeApp/src/commonMain/.../ui/settings/ServicesSettings.kt` | LiteRT service card: model list, download/import controls, progress and error display |
| `composeApp/src/commonMain/.../ui/settings/SettingsScreen.kt` | Hosts service settings including LiteRT model management |
