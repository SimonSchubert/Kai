# On-Device Inference (LiteRT)

**Last verified:** 2026-04-08

Kai can run AI models directly on the user's Android device using Google's LiteRT LM SDK. This enables fully offline, private inference with no API key, no internet connection, and no cost.

## How It Works

Models are downloaded from HuggingFace's litert-community and stored locally on the device. When the user sends a message, the model runs entirely on-device using GPU acceleration (with CPU fallback). The engine initializes on first use (~10 seconds) and stays loaded for 5 minutes of inactivity before automatically releasing memory.

## Available Models

| Model | Size | Context Window |
|-------|------|----------------|
| Gemma 4 E2B IT | 2.6 GB | 4K tokens |
| Gemma 4 E4B IT | 3.7 GB | 4K tokens |

Models are `.litertlm` files from the [litert-community](https://huggingface.co/litert-community) organization on HuggingFace.

## Limitations

- **No tool use** -- the 4K context window is too small to include tool schemas
- **No image input** -- limited model capabilities
- **No heartbeat** -- heartbeat requires tool calling
- **No dynamic UI** -- kai-ui prompts are skipped for limited models
- **Android only** -- LiteRT LM SDK is not available on iOS, desktop, or web

## Model Management

Users manage models through the LiteRT service card in Settings:

- **Download** -- each model card shows a download button with size info
- **Select** -- radio button appears after download to set the active model
- **Delete** -- trash icon removes the downloaded model file
- **Cancel** -- active downloads can be cancelled
- **Free space** -- available device storage is shown below the model list

Downloads run in a foreground service with a notification, so they continue when the app is backgrounded.

When the last LiteRT service instance is removed, all downloaded models are automatically deleted.

## Engine Lifecycle

1. **Lazy initialization** -- the engine loads only when the first message is sent
2. **GPU-first** -- attempts GPU backend, falls back to CPU if unavailable
3. **Persistent across messages** -- stays loaded for the duration of the conversation
4. **Auto-release** -- released after 5 minutes of inactivity to free memory
5. **Status indicator** -- the chat shows "Initializing {model name}" with a pulsing dot during engine load

## Fallback Behavior

- LiteRT instances participate in the normal fallback chain
- On non-Android platforms, LiteRT instances are silently skipped
- `askWithTools` (used by heartbeat and scheduling) never falls back to on-device services

## Key Files

| File | Purpose |
|------|---------|
| `composeApp/src/commonMain/.../data/Service.kt` | `Service.LiteRT` definition with `isOnDevice = true` |
| `composeApp/src/commonMain/.../inference/LocalInferenceEngine.kt` | Platform-agnostic interface for on-device inference |
| `composeApp/src/commonMain/.../inference/LocalInferenceEngineProvider.kt` | `expect` factory, returns `null` on non-Android |
| `composeApp/src/androidMain/.../inference/LiteRTInferenceEngine.kt` | Android implementation wrapping LiteRT LM SDK |
| `composeApp/src/androidMain/.../inference/ModelDownloadService.kt` | Foreground service for background downloads |
| `composeApp/src/commonMain/.../data/RemoteDataRepository.kt` | Inference dispatch, engine initialization status |
| `composeApp/src/commonMain/.../ui/settings/SettingsScreen.kt` | `LiteRTSettings` composable for model management |
