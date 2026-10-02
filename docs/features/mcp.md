# MCP Servers

**Last verified:** 2026-10-02

Kai supports external tool servers via the [Model Context Protocol](https://modelcontextprotocol.io/) (MCP). Users can connect to remote MCP servers using Streamable HTTP transport and use their tools alongside native tools.

## Concepts

### MCP Server

A remote service that exposes tools via the MCP JSON-RPC protocol. Each server has a name, URL, optional authentication headers, and an enabled state. Server configurations are persisted as JSON in app settings. If the saved list ever becomes unreadable, it is backed up to a separate settings entry before the next save replaces it, so a corrupt blob is never silently lost.

### MCP Tool

A tool discovered from a connected MCP server. Each one is wrapped so it behaves like a built-in tool and integrates seamlessly with the existing tool executor and AI request pipeline. Each MCP tool has an ID of `mcp_{serverId}_{toolName}` and can be individually toggled.

### Popular Servers

A curated list of verified free MCP endpoints. Displayed as quick-add shortcuts in the add server bottom sheet. Selection criteria: free, Streamable HTTP transport, practically useful, reasonably stable. Most require no API key and one-tap add. Jina AI prefills the form with an optional API key field (stored as `Authorization: Bearer …` when provided); the server can still be added without a key, but search tools need a free key from jina.ai. Existing user-defined headers are never overwritten. The current names live in the runtime popular-server list in code; selection policy, last probe results, and a mirrored snapshot live in the knowledge bundle under `docs/knowledge/popular-mcp/`. Both are refreshed together with the `update-popular-mcp-servers` skill. Dead hosts (`remote.mcpservers.org` Fetch and Sequential Thinking) were removed earlier.

## Adding a Server

In the Tools tab of settings, the "MCP Servers" section appears above native tools. Users can:

- Tap "Add MCP Server" to open a bottom sheet
- Enter a name, URL, and any number of custom headers manually (e.g., `Authorization`, plus additional vendor-specific headers); rows can be added or removed individually
- Or pick from the popular servers list: no-auth servers one-tap add; auth-optional servers (Jina AI) prefill name/URL and show an optional API key field (Add works without a key)

MCP server configurations are included in the settings export/import feature, so the full set of servers (and their headers) can be moved between devices.

## Connection Flow

When a server is added or enabled:

1. Kai creates an MCP client for the server URL and headers
2. Sends an `initialize` JSON-RPC request with client capabilities
3. Sends a `notifications/initialized` notification
4. Calls `tools/list` to discover available tools
5. Registers discovered tools with their metadata (name, description, input schema)
6. The server appears as connected (green dot) in settings

When the chat screen first opens, all enabled MCP servers are reconnected in the background in parallel. The first time the settings screen becomes visible in a session, the same connect sweep runs once (alongside connection validation for services); later returns to settings do not automatically re-connect failed servers. Servers also reconnect when the user enables a server, taps refresh, or imports MCP settings. Connection state is protected by a mutex to prevent data races from concurrent connections, and individual server failures do not block other servers from connecting.

## Server Management

Each server card in settings shows:

- A status dot (green=connected, orange=connecting, red=error, grey=unknown), an enable/disable toggle, and a dropdown chevron
- Clicking anywhere on the card expands/collapses it
- When expanded: discovered tools with individual toggles, refresh button, remove button (removal is deferred with a snackbar "Undo" option before permanent deletion)
- Disabling a server disconnects it immediately and the status dot reflects the change

The UI uses the same card style, status dot colors, and spacing as the Services tab for visual consistency.

## Transport

Only Streamable HTTP transport is supported:

- POST requests with `Content-Type: application/json` and `Accept: application/json, text/event-stream`
- The client tracks `Mcp-Session-Id` headers for session management
- Both direct JSON responses and SSE (Server-Sent Events) responses are handled
- No stdio transport support

## Authentication

Custom headers (e.g., `Authorization: Bearer <token>`) can be configured per server and are sent with every request. Auth-optional popular servers (Jina AI) collect an optional API key in the add sheet and, when provided, store it as an Authorization header (`Bearer` is prefixed automatically when missing). The server can be added with no key.

## Integration with Tools

MCP tools are automatically available to the AI — no changes needed to the tool executor or request serialization. The platform's list of available tools includes every enabled tool from connected MCP servers. MCP tools have a 60-second timeout (vs 30s default for native tools). MCP tools are only shown within their server's expanded card in settings, not in the native tools list.

Tool calls to MCP servers go through the same execution pipeline as native tools: the tool executor finds the tool by name, the MCP tool wrapper sends a `tools/call` JSON-RPC request to the server, and the result is returned to the AI.

Every tool must have a unique name in a request. An MCP tool whose name is already taken — by a built-in tool or by another server's tool — is offered to the AI as `<server id>_<tool name>` (then `_2`, `_3`, … if needed, limited to 64 characters of `A–Z a–z 0–9 _ -`). Calls to the renamed tool still invoke the server's original tool name; the per-tool enable switches in settings are unaffected. Stopping a run while an MCP tool call is in flight cancels it rather than returning a failed result.

## Limitations

- HTTP/SSE transport only (no stdio)
- CORS may block MCP server requests on the web platform
- MCP tool parameters preserve full JSON Schema (including nested `items`, `properties`, `enum`) for accurate API serialization

## Key Files

| File | Purpose |
|---|---|
| `composeApp/src/commonMain/.../mcp/McpClient.kt` | MCP JSON-RPC client over HTTP/SSE |
| `composeApp/src/commonMain/.../mcp/McpServerManager.kt` | Server lifecycle, connection, tool discovery |
| `composeApp/src/commonMain/.../data/SettingsJson.kt` | Shared settings-backed JSON persistence: decode-or-default, encode-and-write, locked read-modify-write |
| `composeApp/src/commonMain/.../mcp/McpTool.kt` | Wraps MCP tools as native Tool implementations |
| `composeApp/src/commonMain/.../mcp/McpServerConfig.kt` | Server configuration data model |
| `composeApp/src/commonMain/.../mcp/McpModels.kt` | JSON-RPC DTOs and MCP-specific models |
| `composeApp/src/commonMain/.../mcp/PopularMcpServers.kt` | Curated list of verified MCP endpoints |
| `docs/knowledge/popular-mcp/` | OKF bundle: selection policy, last probe snapshot, refresh playbook |
| `composeApp/src/commonMain/.../ui/settings/McpSection.kt` | MCP server card UI, add-server bottom sheet with multi-header editor |
| `composeApp/src/commonMain/.../ui/settings/SettingsScreen.kt` | Hosts the MCP section inside the Tools tab content |
| `composeApp/src/commonMain/.../ui/settings/SettingsViewModel.kt` | MCP connection management and UI state |
| `composeApp/src/commonMain/.../ui/settings/SettingsUiState.kt` | McpServerUiState, McpConnectionStatus |
| `composeApp/src/commonMain/.../data/AppSettings.kt` | MCP server config persistence, backup of undecodable JSON |
