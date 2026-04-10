# On-Device Inference (LiteRT)

**Last verified:** 2026-04-10

Kai can run AI models directly on the user's device using Google's LiteRT LM SDK. This enables fully offline, private inference with no API key, no internet connection, and no cost. Available on Android and Desktop (macOS, Linux, Windows).

## How It Works

Models are downloaded from HuggingFace's litert-community and stored locally on the device. When the user sends a message, the model runs entirely on-device using GPU acceleration (with CPU fallback). The engine initializes on first use (~10 seconds) and stays loaded for 5 minutes of inactivity before automatically releasing memory.

## Available Models

| Model | Size | GPU Memory (Android) | Default Context | Max Context |
|-------|------|---------------------|-----------------|-------------|
| Gemma 4 E2B IT | 2.58 GB | 676 MB | 4K tokens | 32K tokens |
| Gemma 4 E4B IT | 3.65 GB | 710 MB | 4K tokens | 32K tokens |

Models are `.litertlm` files from the [litert-community](https://huggingface.co/litert-community) organization on HuggingFace.

## Limitations

- **No tool use** -- the 4K context window is too small to include tool schemas
- **No image input** -- limited model capabilities
- **No heartbeat** -- heartbeat requires tool calling
- **No dynamic UI** -- kai-ui prompts are skipped for limited models
- **Not available on iOS or web** -- LiteRT LM SDK supports Android and JVM only

## Model Management

Users manage models through the LiteRT service card in Settings:

- **Download** -- each model card shows a download button with size info; disk space is validated before starting
- **Select** -- radio button appears after download to set the active model
- **Delete** -- trash icon removes the downloaded model file
- **Cancel** -- active downloads can be cancelled
- **Error display** -- download failures (network, disk space, incomplete) are shown inline in the settings UI
- **Context size slider** -- each model has a slider to adjust context size (4K–32K tokens in 1K steps); available before download so users can preview performance impact
- **Performance indicator** -- each model shows a Good/OK/Poor label based on total device RAM vs estimated GPU memory at the selected context size (Good: RAM >= 3x, OK: >= 1.5x, Poor: < 1.5x); memory estimate scales linearly with context size via per-model KV cache cost
- **Free space** -- available device storage is shown below the model list

On Android, downloads run in a foreground service with a notification so they continue when the app is backgrounded. On Desktop, downloads run in a background coroutine.

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
| Memory check | `ActivityManager.getMemoryInfo()` | `OperatingSystemMXBean.freePhysicalMemorySize` |
| Disk space | `StatFs.availableBytes` | `File.usableSpace` |
| Download notification | Foreground service with notification | No notification (no OS restriction) |

## Fallback Behavior

- LiteRT instances participate in the normal fallback chain
- On unsupported platforms (iOS, web), LiteRT instances are silently skipped
- `askWithTools` (used by heartbeat and scheduling) never falls back to on-device services

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
