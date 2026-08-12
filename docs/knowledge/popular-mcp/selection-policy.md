---
type: Policy
title: Popular MCP selection and probe policy
description: Which remote MCP endpoints may appear in Kai’s one-tap list, and when a probe is attested.
tags: [mcp, popular-servers, policy]
status: stable
generated: { by: human:simon, at: 2026-08-12T21:30:00Z }
---

# Goal

The Settings → Tools add-server sheet should only one-tap endpoints that a user can reach **without paying**, over **Streamable HTTP**, and that still speak MCP.

# Must hold (all of them)

1. **Transport** — HTTPS Streamable HTTP (`POST` with `Content-Type: application/json` and `Accept: application/json, text/event-stream`). No stdio.
2. **Price** — free to add. An optional free API key is allowed (Jina AI). Paid-only hosts stay out.
3. **Usefulness** — a real end-user job (docs, search, weather, transit, …), not a demo echo server.
4. **Probe** — attested live, or kept under the timeout rule below.

# Attested live

A server is **attested live** when a probe matching the app client succeeds:

1. `initialize` with protocol `2024-11-05`, empty capabilities, `clientInfo.name = "Kai 9000"`.
2. `notifications/initialized` (no response required).
3. `tools/list` returns a JSON-RPC result (zero tools is unusual — treat as review, not an automatic drop).

Forward `Mcp-Session-Id` when the host sends one. Parse either a JSON body or an SSE `data:` line.

**Auth-optional** hosts (`requiresAuth: true`): `initialize` without a key is enough. `tools/list` may be empty or return an auth error; do not drop for that.

# Must drop

- DNS failure, connection refused, **404**, **410**, or a response that is not JSON-RPC MCP.
- A host that now requires a paid key for `initialize` itself (401/403 on initialize for a previously no-auth entry).
- Anything that is not Streamable HTTP.
- **Non-retryable origin failure** — Cloudflare/edge **526** (invalid origin TLS), **530** (origin DNS), or a 5xx body that says `retryable: false` / `owner_action_required`. Users cannot one-tap these.

# Must not drop on one flake

Timeouts and **retryable** 5xx: retry **once**. If the retry still fails, **keep** the row and mark the probe as failed. Do not remove a previously live server because this machine had a bad network moment.

# Must not add

- New hosts without an explicit product decision (this list is curated, not scraped).
- stdio, OAuth-only, or paid-only servers.
- Duplicates of an existing host (Jina `/v1` and `/sse` are the same host).

# Auth

Only **Jina AI** is `requiresAuth: true` today. Do not invent default secret headers. Existing user headers are never overwritten at runtime.

# Dead hosts already removed

`remote.mcpservers.org` Fetch and Sequential Thinking were dropped earlier (dead). Do not add them back.
