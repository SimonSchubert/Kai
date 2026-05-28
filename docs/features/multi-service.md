# Multi-Service

**Last verified:** 2026-05-28

Kai supports 27 LLM providers (plus a built-in Free tier). Each provider uses one of three API formats: **OpenAI-compatible** (most services), **Gemini native**, or **Anthropic native** -- plus **LiteRT on-device** for local inference. Users can configure multiple service instances, reorder them, and Kai automatically falls back through the chain on failure.

## Concepts

### Service

A supported LLM provider. Each service is defined by:

- A unique name and ID
- Whether it requires an API key
- Which API format it uses (OpenAI-compatible, Gemini native, or Anthropic native)
- Links to the provider's API-key management page

### Service Instance

A configured connection to a service. Users can add multiple instances of the same service (e.g. two OpenAI accounts with different keys). Each instance stores its own:

- API key
- Selected model
- Base URL (relevant for the OpenAI-Compatible API service)

### Free Tier

A built-in service that requires no API key. Free is never shown in the service picker — it is used as:

- The sole service when no other services are configured
- A last-resort fallback when "Use as fallback" is enabled (default)

## Fallback Chain

1. Configured instances are tried in the order the user arranged them
2. Only instances with valid API keys are considered
3. If no instances are configured, the Free tier is used as the only service
4. If instances exist and "Use as fallback" is enabled (default), the Free tier is appended as the last resort
5. Each service attempt retries up to 2 times with increasing delays before moving to the next service in the chain
6. On failure, the next instance in the chain is tried; if all fail, the last error is shown
7. If a fallback succeeds, the response indicates which service answered
8. While the chain is being walked, the thinking indicator shows per-attempt status — the name of the service currently being tried, or the reason the previous one failed before moving on — so silent fallbacks are visible to the user
9. Entries whose context window can't fit the current chat history are skipped during the walk
10. On-device (Local Model) failures are not silently absorbed — they short-circuit the fallback chain so the user sees the actual error rather than being quietly bumped to a cloud service
11. Certain non-retryable errors (notably Anthropic's "insufficient credits" and quota-exhausted responses from OpenAI-compatible providers) bypass both the per-service retry loop and the fallback chain — the error surfaces immediately

## API Formats

Most services use the **OpenAI-compatible** chat completions format. **Gemini** uses Google's native Generative Language API. **Anthropic** uses its own Messages API with `x-api-key` header authentication and a different request/response structure. **LiteRT** runs inference on-device using Google's LiteRT LM SDK -- no HTTP, no API key, fully offline.

The **OpenAI-Compatible API** service supports a custom base URL, defaulting to `localhost:11434/v1` for local Ollama setups. The base URL should include the version path segment (e.g., `http://localhost:11434/v1` or `https://my-provider.com/api/v1`), following the OpenAI SDK convention. Kai appends only `/chat/completions` or `/models` to this base URL.

## Supported Services

| Service | `id` | Requires API Key | API Type |
|---|---|---|---|
| Free | `free` | No | OpenAI-compatible |
| **Atlas Cloud** | `atlascloud` | Yes | OpenAI-compatible |
| Gemini | `gemini` | Yes | Gemini native |
| Anthropic | `anthropic` | Yes | Anthropic native |
| OpenAI | `openai` | Yes | OpenAI-compatible |
| DeepSeek | `deepseek` | Yes | OpenAI-compatible |
| Mistral | `mistral` | Yes | OpenAI-compatible |
| xAI | `xai` | Yes | OpenAI-compatible |
| OpenRouter | `openrouter` | Yes | OpenAI-compatible |
| GroqCloud | `groqcloud` | Yes | OpenAI-compatible |
| NVIDIA | `nvidia` | Yes | OpenAI-compatible |
| Cerebras | `cerebras` | Yes | OpenAI-compatible |
| Ollama Cloud | `ollamacloud` | Yes | OpenAI-compatible |
| LongCat | `longcat` | Yes | OpenAI-compatible (ships with a curated default model list; also exposes a `/models` endpoint used during validation) |
| Together AI | `together` | Yes | OpenAI-compatible |
| Hugging Face | `huggingface` | Yes | OpenAI-compatible |
| Venice AI | `venice` | Yes | OpenAI-compatible |
| Moonshot AI | `moonshot` | Yes | OpenAI-compatible |
| Z.AI | `zai` | Yes | OpenAI-compatible |
| Z.AI Coding Plan | `zai-coding-plan` | Yes | OpenAI-compatible |
| MiniMax | `minimax` | Yes | OpenAI-compatible |
| AIHubMix | `aihubmix` | Yes | OpenAI-compatible |
| Deep Infra | `deepinfra` | Yes | OpenAI-compatible |
| Fireworks AI | `fireworksai` | Yes | OpenAI-compatible |
| OpenCode | `opencode` | Yes | OpenAI-compatible |
| Public AI | `publicai` | Yes | OpenAI-compatible |
| OpenAI-Compatible API | `openai-compatible` | No (optional) | OpenAI-compatible |
| Local Model | `litert` | No | On-device (LiteRT LM) |

## Connection Validation

When the user enters or changes an API key (or base URL), the app validates the connection after an 800 ms debounce and shows a status indicator: **checking**, **connected**, **invalid key**, **quota exhausted**, **rate limited**, or **connection failed**. Validation also runs for all services when the settings screen opens. Services validate by fetching their model list — Gemini, Anthropic, and OpenAI-compatible services (including LongCat) each call their respective models endpoint. On a successful connection, the available model list is refreshed.

## Model Selection

When a connection is validated and models are fetched, the app auto-selects a model if none is chosen — first checking for a per-service default model (e.g. LongCat defaults to "LongCat-Flash-Lite"), then preferring "kimi-k2.5" if available, otherwise the first model in the list. Services filter their model lists:
- OpenAI shows only chat-oriented models (prefix filter)
- GroqCloud shows only models marked as active
- Together AI filters by `type == "chat"` to exclude non-chat models (embedding, code, etc.)
- Other services show all non-retired models

### Model Cards

The model picker modal shows each candidate as a card with consistent metadata regardless of provider:

- **Title** (top left) — a human-readable display name from the curated catalog or the provider's API; falls back to the raw model id only when no display name is available
- **Arena score** (top right) — LMArena Elo rating as colored text, gradient from green (>= 1400) through lime/yellow to orange (< 1250)
- **Detail line** (below the title) — release date, parameter count, and context window joined into a single muted line separated by ` · ` (e.g. `Mar 2025 · 70B · 200K ctx`); any missing field is simply omitted from the line

The card representing the currently selected model is highlighted with a filled accent background so users can identify their current choice at a glance when reopening the picker.

The modal includes sort chips (Date, Score, Ctx) below the search field. Tapping a chip switches which field is active; all sorts are descending (highest or most recent first), with no ascending option. Default sort is by score.

Context window and release date come from two sources, merged by the mapping layer: a bundled curated catalog of well-known models, and whatever the provider's own models endpoint returns (e.g. OpenAI-compat `context_window` and `created`, Anthropic `created_at`). The curated catalog wins; provider-supplied values are used only as a fallback when the catalog has no entry for that model. The catalog is hand-maintained to correct inconsistencies in what providers report. Models not present in the catalog still render — they just use whatever the API provided, and any unknown fields are hidden.

## Chat Screen Service Toggle

The chat screen builds an "interactive services" list that includes two distinct Free entries — **Free FAST** and **Free EXPERT** — followed by every configured non-Free service whose currently selected model supports tool use (agentic flows). When this list contains more than one entry, a circular service icon button appears to the right of the chat input, next to the send/stop button. Because the two Free modes alone already count as two entries, the toggle can appear even when no non-Free services are configured. The icon represents the current primary service (each service has its own simplified vector icon). Tapping it opens a dropdown listing the eligible services with their icons, names, and model IDs; the current primary is highlighted with a primary container background. The dropdown is height-capped to the screen and becomes scrollable when the eligible list is long enough to overflow, so every entry stays reachable.

Selecting a non-Free entry reorders the configured list so the chosen service becomes first (primary). Selecting a Free entry (FAST or EXPERT) instead flips an "is Free primary" flag and records the chosen Free mode — Free can be promoted to primary independently of the configured fallback order, without rearranging the non-Free chain. The fallback chain picks up the new state automatically. The fallback walker also skips any entry whose context window can't fit the current chat history, so very long conversations may transparently move past services that would otherwise be eligible.

## Attachments

Image attachments are broadly supported across cloud services. The built-in **Free** tier is the one cloud exception — its proxy fans out to a chain that includes text-only fallback models, so images are not offered when Free is the active service. PDF attachments are advertised only by services with native document support — currently Anthropic, Gemini, OpenAI, and OpenRouter — and only those services accept PDFs in a chat turn. The on-device Local Model hides file attachment affordances entirely; users running purely locally don't see attachment buttons.

## Settings UI

Users manage services through the settings screen:
- **Add** — pick from the list of available services (can add the same service multiple times); the OpenAI-Compatible API and the on-device Local Model are pinned to the top of the picker, followed by the highlighted featured provider Atlas Cloud, with the remaining providers sorted alphabetically
- **Remove** — delete an instance and its stored credentials; deletion is deferred with a snackbar "Undo" option (~4 seconds) before the service is permanently removed
- **Reorder** — drag to change priority (first = primary, rest = fallbacks)
- **Configure** — per-instance API key, model selection, base URL (OpenAI-Compatible only)
- **Free fallback toggle** — controls whether Free is appended as last resort

## Key Files

| File | Purpose |
|---|---|
| `composeApp/src/commonMain/.../data/Service.kt` | Service definitions, all provider metadata |
| `composeApp/src/commonMain/.../data/ModelCatalog.kt` | Curated context window / release date for well-known models |
| `composeApp/src/commonMain/.../data/ModelTransformations.kt` | Maps provider model DTOs to `SettingsModel`, merges with catalog |
| `composeApp/src/commonMain/.../data/AppSettings.kt` | Service instance storage, credential persistence, migration |
| `composeApp/src/commonMain/.../data/RemoteDataRepository.kt` | Fallback chain, request orchestration |
| `composeApp/src/commonMain/.../network/Requests.kt` | HTTP clients for all three API formats |
| `composeApp/src/commonMain/.../network/dtos/anthropic/` | Anthropic Messages API DTOs |
| `composeApp/src/commonMain/.../ui/settings/SettingsViewModel.kt` | Connection validation, service management UI logic |
| `composeApp/src/commonMain/.../ui/chat/ChatScreen.kt` | Chat screen, renders ServiceSelector |
| `composeApp/src/commonMain/.../ui/chat/composables/ServiceSelector.kt` | Compact service toggle dropdown |
| `composeApp/src/commonMain/.../ui/chat/ChatViewModel.kt` | Wires service selection and reordering |
