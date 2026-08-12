---
type: Catalog
title: Popular MCP servers
description: Curated free Streamable HTTP MCP endpoints for Kai’s one-tap add sheet.
tags: [mcp, popular-servers]
status: stable
stale_after: 2026-08-26
generated: { by: process:update-popular-mcp-servers, at: 2026-08-12T20:49:34Z }
verified: { by: process:desktopTest-PopularMcpServers, at: 2026-08-12T20:51:16Z }
sources:
  - id: selection-policy
    resource: /selection-policy.md
    title: Selection and probe policy
  - id: popular-mcp-playbook
    resource: /refresh-playbook.md
    title: Refresh popular MCP servers playbook
---

# Policy

Include a host when **all** of the following hold (see [selection-policy.md](selection-policy.md)):

1. HTTPS Streamable HTTP MCP (`initialize` + `tools/list` like the app client).
2. Free to add (optional free API key allowed).
3. A real end-user job, not a demo echo server.

A probe is **attested live** only when that initialize + tools/list handshake succeeds. Auth-optional hosts (Jina AI) need initialize without a key.

Replace this snapshot only via the [refresh playbook](refresh-playbook.md).

# Current set

Snapshot mirrored into `popularMcpServers` (runtime). 13 hosts.

| Name | URL | Auth | Probe | Tools |
|---|---|---|---|---|
| Context7 | `https://mcp.context7.com/mcp` | none | attested live | 2 — `resolve-library-id`, `query-docs` |
| MDN | `https://mcp.mdn.mozilla.net` | none | attested live | 3 — `get-compat`, `get-doc`, `search` |
| DeepWiki | `https://mcp.deepwiki.com/mcp` | none | attested live | 3 — `ask_question`, `read_wiki_contents`, `read_wiki_structure` |
| Parallel Search | `https://search.parallel.ai/mcp` | none | attested live | 2 — `web_search`, `web_fetch` |
| Yahoo Finance | `https://gateway.mcpservers.org/yahoo-finance/mcp` | none | attested live | 4 — `get_quote`, `search`, `get_chart`, `quote_summary` |
| CoinGecko | `https://mcp.api.coingecko.com/mcp` | none | attested live | 2 — `execute`, `search_docs` |
| Jina AI | `https://mcp.jina.ai/v1` | optional key | attested live (no key) | 22 — read/search/screenshot family |
| Open-Meteo Weather | `https://mcp.open-mcp.org/api/server/open-weather@latest/mcp` | none | attested live | 2 — `expandSchema`, `getweatherdata` |
| Kiwi.com | `https://mcp.kiwi.com` | none | attested live | 2 — `search-flight`, `feedback-to-devs` |
| Malwarebytes | `https://scamguard.malwarebytes.com/claude/mcp` | none | attested live | 6 — `reputation-check_link`, `reputation-check_phone`, `reputation-check_email`, `reputation-report`, `reputation-whois`, `reputation-scan_all` |
| tldraw | `https://tldraw-mcp-app.tldraw.workers.dev/mcp` | none | attested live | 6 — `search`, `exec`, `_exec_callback`, `_get_canvas_state`, `read_checkpoint`, `save_checkpoint` |
| Find-A-Domain | `https://api.findadomain.dev/mcp` | none | attested live | 2 — `check_domain`, `list_tlds` |
| SubwayInfo NYC | `https://subwayinfo.nyc/mcp` | none | attested live | 25 — MTA / bus / ferry arrivals and alerts |

# Snapshot

| Field | Value |
|---|---|
| Probed | 2026-08-12T20:49:34Z |
| Handshake | `initialize` protocol `2024-11-05` + `notifications/initialized` + `tools/list` |
| Current | 13 attested live |
| Dropped this refresh | Manifold Markets (`https://api.manifold.markets/v0/mcp`) — Cloudflare **526** invalid origin TLS, `retryable: false` |

# Notes

- Jina AI still uses `requiresAuth: true` in Kotlin so the add sheet shows a key field. The probe listed 22 tools without a key; search tools still need a free jina.ai key.
- `remote.mcpservers.org` Fetch and Sequential Thinking stay out (removed earlier).
- Do not add replacement hosts without an explicit product decision.
