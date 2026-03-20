# MCP Servers

**Last verified:** 2026-03-20

Kai supports external tool servers via the [Model Context Protocol](https://modelcontextprotocol.io/) (MCP). Users can connect to remote MCP servers using Streamable HTTP transport and use their tools alongside native tools.

## Concepts

### MCP Server

A remote service that exposes tools via the MCP JSON-RPC protocol. Each server has a name, URL, optional authentication headers, and an enabled state. Server configurations are persisted as JSON in app settings.

### MCP Tool

A tool discovered from a connected MCP server. Wraps the server's tool definition as a native `Tool` implementation so it integrates seamlessly with the existing tool executor and AI request pipeline. Each MCP tool has an ID of `mcp_{serverId}_{toolName}` and can be individually toggled.

### Popular Servers

A curated list of ~8 verified free MCP endpoints that require no API key. Displayed as quick-add shortcuts in the add server bottom sheet. Selection criteria: free, no auth required, Streamable HTTP transport, practically useful, reasonably stable.

## Adding a Server

In the Tools tab of settings, the "MCP Servers" section appears above native tools. Users can:

- Tap "Add MCP Server" to open a bottom sheet
- Enter a name, URL, and optional authorization header manually
- Or pick from the popular servers list for one-tap setup

## Connection Flow

When a server is added or enabled:

1. Kai creates an `McpClient` for the server URL and headers
2. Sends an `initialize` JSON-RPC request with client capabilities
3. Sends a `notifications/initialized` notification
4. Calls `tools/list` to discover available tools
5. Registers discovered tools with their metadata (name, description, input schema)
6. The server appears as connected (green dot) in settings

On app startup, all enabled MCP servers are automatically reconnected in the background in parallel. Connection state is protected by a mutex to prevent data races from concurrent connections. Individual server failures do not block other servers from connecting.

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

Custom headers (e.g., `Authorization: Bearer <token>`) can be configured per server and are sent with every request. The popular servers list contains only servers that require no authentication.

## Integration with Tools

MCP tools are automatically available to the AI — no changes needed to the tool executor or request serialization. The platform layer's `getAvailableTools()` includes enabled MCP tools from the `McpServerManager`. MCP tools have a 60-second timeout (vs 30s default for native tools). MCP tools are only shown within their server's expanded card in settings, not in the native tools list.

Tool calls to MCP servers go through the same execution pipeline as native tools: the tool executor finds the tool by name, the `McpTool` wrapper sends a `tools/call` JSON-RPC request to the server, and the result is returned to the AI.

## Limitations

- HTTP/SSE transport only (no stdio)
- CORS may block MCP server requests on the web platform
- MCP tool parameters preserve full JSON Schema (including nested `items`, `properties`, `enum`) for accurate API serialization

## Key Files

| File | Purpose |
|---|---|
| `composeApp/src/commonMain/.../mcp/McpClient.kt` | MCP JSON-RPC client over HTTP/SSE |
| `composeApp/src/commonMain/.../mcp/McpServerManager.kt` | Server lifecycle, connection, tool discovery |
| `composeApp/src/commonMain/.../mcp/McpTool.kt` | Wraps MCP tools as native Tool implementations |
| `composeApp/src/commonMain/.../mcp/McpServerConfig.kt` | Server configuration data model |
| `composeApp/src/commonMain/.../mcp/McpModels.kt` | JSON-RPC DTOs and MCP-specific models |
| `composeApp/src/commonMain/.../mcp/PopularMcpServers.kt` | Curated list of verified MCP endpoints |
| `composeApp/src/commonMain/.../ui/settings/SettingsScreen.kt` | McpServersSection, McpServerCard composables |
| `composeApp/src/commonMain/.../ui/settings/SettingsViewModel.kt` | MCP connection management and UI state |
| `composeApp/src/commonMain/.../ui/settings/SettingsUiState.kt` | McpServerUiState, McpConnectionStatus |
| `composeApp/src/commonMain/.../data/AppSettings.kt` | MCP server config persistence |
