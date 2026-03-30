# Tools

**Last verified:** 2026-03-30

Kai's tools feature allows the AI to execute external functions during conversations — web search, notifications, calendar events, shell commands, memory operations, and more. Tools are defined with a schema, executed with safety guards, and managed through per-tool toggles in settings.

## Concepts

### Tool

An executable function the AI can invoke during a conversation. Each tool declares a schema (name, description, parameters), a timeout (default 30 seconds), and an execute method that receives parsed arguments and returns a result.

### Tool Schema

The machine-readable definition sent to the AI provider so it knows which tools are available and how to call them. Contains the tool name, a natural-language description, and a map of parameter definitions (type, description, required flag).

### Tool Info

Display metadata used in the settings UI. Contains an id, human-readable name and description (with optional localized string resources), and the current enabled state. Returned by the platform layer for all tools regardless of whether they are currently enabled.

### Tool Executor

The component that looks up a tool by name, parses JSON arguments into a typed map, runs the tool with its declared timeout, catches errors, and truncates oversized results. Acts as the bridge between raw AI tool-call JSON and the typed tool implementations.

## Available Tools

### Common (cross-platform)

| Tool | Description | Default |
|---|---|---|
| `web_search` | Search the web for current information | Enabled |
| `get_local_time` | Get the current local date and time | Enabled |
| `get_location_from_ip` | Get estimated location from IP address | Enabled |
| `open_url` | Open a URL, link, or local file on the device | Enabled |

#### open_url platform behavior

The `open_url` tool accepts both web URLs and `file://` URIs. Each platform opens URLs using its native mechanism:

- **Android** — Uses `ACTION_VIEW` intents. For `file://` URIs, converts to `content://` via FileProvider with MIME type detection so the file opens in the appropriate app (e.g. `.html` files open in the browser).
- **Desktop** — Uses `java.awt.Desktop.browse()`.
- **iOS** — Uses `UIApplication.openURL()`.
- **Web** — Uses `window.open()` with `_blank` target.

### Memory (always on)

| Tool | Description |
|---|---|
| `memory_store` | Store or update a memory with a descriptive key |
| `memory_forget` | Delete a stored memory by its exact key |
| `memory_learn` | Store a structured learning with a category |
| `memory_reinforce` | Reinforce a stored memory by incrementing its hit count |

Memory tools cannot be individually disabled. They are available whenever the memory feature is enabled.

### Scheduling & Heartbeat

Scheduling tools and heartbeat tools are available when the scheduling feature is enabled. See the heartbeat spec for details on heartbeat-specific tools.

### Email

Email tools are available when the email feature is enabled and accounts are configured.

### Platform-specific (Android)

| Tool | Description | Default |
|---|---|---|
| `send_notification` | Send a push notification to the device | Enabled |
| `create_calendar_event` | Create a calendar event on the device | Enabled |
| `set_alarm` | Set an alarm or countdown timer | Enabled |
| `execute_shell_command` | Execute a shell command on the device | Disabled |

#### Linux Sandbox (Android)

When the Linux Sandbox is set up and enabled, `execute_shell_command` routes commands through proot into an Alpine Linux rootfs instead of running them in Android's native shell. This provides:

- A full Linux userland with bash, coreutils, and standard utilities
- Package management via `apk` (Alpine's package manager)
- Optional Python installation (`python3`, `pip`)
- Network access (shares the host network stack)

**Setup flow:** The sandbox requires a one-time setup that downloads the Alpine Linux minirootfs (~3 MB). Proot is bundled in the APK as a native library. After setup, users can optionally install Python (~25 MB additional).

**Architecture:** Proot is a user-space chroot implementation that intercepts syscalls via ptrace. No root access is required. The sandbox files live in the app's external files directory under `linux-sandbox/`.

**Settings:** The sandbox section appears in Settings > Tools on Android. Users can set up, reset, install Python, and toggle whether shell commands use the sandbox or the native Android shell.

#### Shell command parameters

Both platforms support these parameters:

| Parameter | Type | Description |
|---|---|---|
| `command` | string (required) | The shell command to execute |
| `timeout` | integer | Timeout in seconds (desktop default 30, max 120; Android default 30, max 60) |
| `working_dir` | string | Working directory for the command |
| `env` | object | Environment variables to set as key-value pairs |
| `background` | boolean | Run in background and return a session_id immediately |

When `background=true`, the command starts asynchronously and returns a `session_id`. The AI then uses the `manage_process` tool to check status, retrieve output, or terminate the process.

The desktop tool dynamically includes the detected OS (macOS/Linux/Windows) and shell in its description so the AI knows the execution context.

On desktop, dangerous environment variables (PATH, LD_PRELOAD, DYLD_INSERT_LIBRARIES) cannot be overridden via the `env` parameter.

#### Process management

The `manage_process` tool is automatically available when the shell command tool is enabled. It supports these actions:

| Action | Description |
|---|---|
| `list` | Show all running and finished background processes |
| `log` | Get output from a process (with offset/limit for paging) |
| `kill` | Terminate a running process |
| `remove` | Remove a finished process from the list |

#### Output handling

Command output uses smart middle truncation: when output exceeds the limit, both the beginning and end are preserved with a truncation marker in the middle. This ensures error messages (typically at the end) are not lost.

Output limits: desktop 30,000 chars per stream, Android 15,000 chars per stream. Final tool results are truncated at 20,000 chars.

### Platform-specific (Desktop)

| Tool | Description | Default |
|---|---|---|
| `execute_shell_command` | Execute a shell command on the host machine | Disabled |
| `manage_process` | Manage background shell processes (list, log, kill, remove) | Disabled (enabled with shell command) |

## Execution Flow

1. The AI responds with one or more tool calls (name + JSON arguments)
2. For each call, a TOOL_EXECUTING entry is added to chat history (shows a spinning icon in the UI)
3. All tool calls in the response are executed in parallel using coroutine async/await
4. Each execution goes through the tool executor: find tool by name, parse arguments, run with timeout, truncate result
5. TOOL_EXECUTING entries are removed and replaced with TOOL result entries (tool call id, tool name, result string)
6. The updated history (including tool results) is sent back to the AI
7. The AI may respond with more tool calls — repeat from step 1
8. When the AI responds with no tool calls, the final text is returned to the user

The loop supports OpenAI-compatible, Gemini, and Anthropic provider formats, with provider-specific serialization of tool calls and results.

## Safety Guards

### Iteration limit

The tool loop runs a maximum of 15 iterations. If exceeded, the AI is asked to respond with the best answer it has so far, with tools removed from the request.

### Repeated call detection

Each tool call produces a signature from its name and arguments hash. If the same signature pattern appears 3 consecutive times, the loop is stopped and the AI is asked to respond with what it has.

### Timeout

Each tool has a configurable timeout defaulting to 30 seconds. If execution exceeds the timeout, the call is cancelled and an error is returned as the tool result.

### Result truncation

Tool results longer than 8,000 characters are truncated with a note indicating the original length.

### Context trimming

Between tool loop iterations, the message history is trimmed to fit within the model's context window. All three providers (OpenAI-compatible, Gemini, Anthropic) perform inter-iteration trimming. Context window sizes are estimated per model (e.g. Gemini 2.5 = 1M tokens, Claude = 200K, GPT-4o = 128K, small local models = 8–32K) and oldest messages are dropped first while preserving the system prompt.

### Context window overflow protection

When the fallback chain is active, each fallback service is checked before use. If the current conversation exceeds a fallback model's estimated context window, that service is skipped. If no service in the chain has a large enough window, an error message is shown to the user.

### Chat history compaction

When conversation history exceeds 70% of the primary model's context window, an AI-powered compaction runs before the next API call. Older messages are summarized into a single compact entry via a separate LLM call, while the most recent 4 user exchanges are kept verbatim. If the summarization call fails, older messages are dropped as a fallback.

## MCP Servers

See [mcp.md](mcp.md) for the full MCP feature spec.

## Tool Enablement

Tool availability is controlled at multiple levels:

- **Feature-level gates** — memory tools require memory enabled, scheduling/heartbeat tools require scheduling enabled, email tools require email enabled
- **Per-tool toggles** — individual tools can be enabled or disabled in settings, persisted with a `tool_enabled_` key prefix
- **Default state** — most tools default to enabled; `execute_shell_command` defaults to disabled
- **Always-on** — memory tools have no individual toggle; they are on whenever memory is enabled

The platform layer assembles the final list of available tools by checking all gates and per-tool settings, and only enabled tools are sent to the AI provider.

## Settings UI

The tools tab in settings displays a responsive grid of toggle cards:

- 3 columns when the screen is at least 800dp wide
- 2 columns when at least 500dp wide
- 1 column on narrow screens

Each card shows the tool name, a short description, and a toggle switch. Clicking anywhere on the card toggles the tool. Cards use a semi-transparent surface variant background.

Only individually toggleable tools appear in the grid — always-on tools (memory) and feature-gated tool groups (scheduling, email) are controlled by their respective feature toggles elsewhere in settings.

## Chat UI

### Waiting response row

When loading, a single composite row appears at the bottom of the chat list containing:
- A **waiting chip** with a pulsing dot (scale 0.6→1.0, alpha 0.4→1.0, 800ms reverse animation) and cycling text ("Thinking…", "Working…", "Brewing…" rotating every 3 seconds with Crossfade animation), using surface variant colors. The chip uses `animateContentSize` (300ms) for smooth text transitions
- **Tool chips** for each currently executing tool, displayed in a Row next to the waiting chip with build icon + tool name, using primary container colors
- TOOL_EXECUTING entries are no longer rendered as separate list items
- Completed tool results (TOOL role) are not shown in the UI

## Key Files

| File | Purpose |
|---|---|
| `composeApp/src/commonMain/.../network/tools/Tool.kt` | Tool interface, ToolSchema, ParameterSchema |
| `composeApp/src/commonMain/.../network/tools/ToolInfo.kt` | Display metadata for settings |
| `composeApp/src/commonMain/.../data/ToolExecutor.kt` | Execution, JSON parsing, timeout, truncation |
| `composeApp/src/commonMain/.../data/RemoteDataRepository.kt` | Tool loop (Gemini + OpenAI), parallel execution |
| `composeApp/src/commonMain/.../tools/CommonTools.kt` | Common tool implementations |
| `composeApp/src/commonMain/.../Platform.kt` | Platform expect declarations for available tools |
| `composeApp/src/androidMain/.../Platform.android.kt` | Android-specific tool implementations |
| `composeApp/src/androidMain/.../sandbox/LinuxSandboxManager.kt` | Sandbox lifecycle, setup, proot management |
| `composeApp/src/androidMain/.../sandbox/ProotExecutor.kt` | Proot command building and execution |
| `composeApp/src/androidMain/.../sandbox/RootfsDownloader.kt` | Alpine rootfs download and extraction |
| `composeApp/src/desktopMain/.../tools/ProcessManager.kt` | Desktop background process tracking |
| `composeApp/src/desktopMain/.../tools/ProcessManagerTool.kt` | Desktop process management tool |
| `composeApp/src/androidMain/.../tools/ProcessManager.kt` | Android background process tracking |
| `composeApp/src/androidMain/.../tools/ProcessManagerTool.kt` | Android process management tool |
| `composeApp/src/commonMain/.../SandboxController.kt` | Cross-platform sandbox interface |
| `composeApp/src/commonMain/.../ui/settings/SettingsScreen.kt` | ToolsContent, ToolItem, LinuxSandboxSection composables |
| `composeApp/src/commonMain/.../ui/chat/composables/ToolMessage.kt` | Executing/completed UI indicators |
| `composeApp/src/commonMain/.../data/AppSettings.kt` | Tool enabled state persistence |
