---
type: Playbook
title: Refresh popular MCP servers
description: Probe Streamable HTTP MCP endpoints, update PopularMcpServers.kt and this OKF bundle.
tags: [mcp, popular-servers, playbook]
status: stable
generated: { by: human:simon, at: 2026-08-12T21:30:00Z }
---

# Trigger

- User runs `/update-popular-mcp-servers`
- A one-tap MCP server fails to connect for users
- [servers.md](servers.md) `stale_after` is today or in the past
- A previously listed host is reported dead or moved

# Preconditions

- Read [index.md](index.md) for scope and layering (runtime vs knowledge).
- Read [selection-policy.md](selection-policy.md) before adding or dropping a row.
- Do **not** add live MCP probes to production code.

# Steps

## 1. Read the current list

Read `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/mcp/PopularMcpServers.kt` (`popularMcpServers`) and the **Current set** in [servers.md](servers.md).

## 2. Probe each URL

For every current row, POST Streamable HTTP like the app client (`McpClient`):

1. `initialize` — protocol `2024-11-05`, empty capabilities, `clientInfo` name `Kai 9000`.
2. `notifications/initialized`.
3. `tools/list`.

Headers: `Content-Type: application/json`, `Accept: application/json, text/event-stream`. Forward `Mcp-Session-Id` when present. Accept a JSON body or an SSE `data:` line.

Record: HTTP status, initialize ok/fail, tools/list ok/fail, tool names (or count), error text. Timeout ~15s; retry once on timeout or 5xx.

Auth-optional rows (Jina AI): probe **without** a key. Initialize success is enough.

## 3. Diff and apply

1. Diff probe results against the **Current set** and against Kotlin. Show kept / dropped / probe-failed **before** writing.
2. Drop only rows the [policy](selection-policy.md) says must drop (dead, not MCP, paid initialize, non-retryable origin 526/530).
3. Do **not** add new hosts unless product explicitly expands the list.
4. Update runtime list in:

   `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/mcp/PopularMcpServers.kt`

   Preserve `PopularMcpServer` field order, comments, and helper functions. Update descriptions only when the probe shows they are wrong.
5. If the one-tap list changed, update the server table in `README.md` and the Popular Servers paragraph in `docs/features/mcp.md`.
6. Update this bundle:
   - Replace the **Current set** and probe table in [servers.md](servers.md)
   - Set `generated: { by: process:update-popular-mcp-servers, at: <ISO-8601 UTC> }`
   - Set `stale_after` to ~14 days ahead (`YYYY-MM-DD`)
   - After tests pass, set `verified`
7. Append a dated entry to [log.md](log.md) (newest first under today’s heading).

## 4. Verify

1. `./gradlew :composeApp:compileKotlinDesktop`
2. `./gradlew :composeApp:desktopTest --tests '*PopularMcpServers*'`
3. `./gradlew spotlessApply`
4. Update `PopularMcpServersTest.kt` only if a fixture name/URL used by tests changed.
5. On green tests, set on [servers.md](servers.md):

   `verified: { by: process:desktopTest-PopularMcpServers, at: <ISO-8601 UTC> }`

# Hard rules

- Do **not** add runtime network calls to decide which popular servers to show.
- Do **not** drop a row on a single timeout or 5xx (retry, then keep and mark failed).
- Do **not** add new hosts without an explicit product decision.
- Bundle and Kotlin must not drift: every refresh updates **both**.
