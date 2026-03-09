# Multi-Service

**Last verified:** 2026-03-09

Kai supports 11 LLM providers (plus a built-in Free tier). Each provider uses either the **OpenAI-compatible** chat format (most services) or the **Gemini native** API. Users can configure multiple service instances, reorder them, and Kai automatically falls back through the chain on failure.

## Concepts

### Service

A supported LLM provider. Each service is defined by:

- A unique name and ID
- Whether it requires an API key
- Which API format it uses (OpenAI-compatible or Gemini native)
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

## API Formats

Most services use the **OpenAI-compatible** chat completions format. **Gemini** uses Google's native Generative Language API.

The **OpenAI-Compatible API** service supports a custom base URL, defaulting to `localhost:11434` for local Ollama setups.

## Supported Services

| Service | `id` | Requires API Key | API Type |
|---|---|---|---|
| Free | `free` | No | OpenAI-compatible |
| Gemini | `gemini` | Yes | Gemini native |
| OpenAI | `openai` | Yes | OpenAI-compatible |
| DeepSeek | `deepseek` | Yes | OpenAI-compatible |
| Mistral | `mistral` | Yes | OpenAI-compatible |
| xAI | `xai` | Yes | OpenAI-compatible |
| OpenRouter | `openrouter` | Yes | OpenAI-compatible |
| GroqCloud | `groqcloud` | Yes | OpenAI-compatible |
| NVIDIA | `nvidia` | Yes | OpenAI-compatible |
| Cerebras | `cerebras` | Yes | OpenAI-compatible |
| Ollama Cloud | `ollamacloud` | Yes | OpenAI-compatible |
| OpenAI-Compatible API | `openai-compatible` | No (optional) | OpenAI-compatible |

## Connection Validation

When the user enters or changes an API key (or base URL), the app validates the connection after an 800 ms debounce and shows a status indicator: **checking**, **connected**, **invalid key**, **quota exhausted**, **rate limited**, or **connection failed**. Validation also runs for all services when the settings screen opens. On a successful connection, the available model list is refreshed.

## Model Selection

When a connection is validated and models are fetched, the app auto-selects a model if none is chosen — preferring "kimi-k2.5" if available, otherwise the first model in the list. Services filter their model lists:
- OpenAI shows only chat-oriented models
- GroqCloud shows only models marked as active
- Other services show all non-retired models

## Settings UI

Users manage services through the settings screen:
- **Add** — pick from the list of available services (can add the same service multiple times)
- **Remove** — delete an instance and its stored credentials
- **Reorder** — drag to change priority (first = primary, rest = fallbacks)
- **Configure** — per-instance API key, model selection, base URL (OpenAI-Compatible only)
- **Free fallback toggle** — controls whether Free is appended as last resort

## Key Files

| File | Purpose |
|---|---|
| `composeApp/src/commonMain/.../data/Service.kt` | Service definitions, all provider metadata |
| `composeApp/src/commonMain/.../data/AppSettings.kt` | Service instance storage, credential persistence, migration |
| `composeApp/src/commonMain/.../data/RemoteDataRepository.kt` | Fallback chain, request orchestration |
| `composeApp/src/commonMain/.../network/Requests.kt` | HTTP clients for both API formats |
| `composeApp/src/commonMain/.../ui/settings/SettingsViewModel.kt` | Connection validation, service management UI logic |
