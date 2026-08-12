---
okf_version: "0.2"
---

# Popular MCP server knowledge

OKF v0.2 bundle for Kai’s **curated one-tap MCP endpoints**. Agents use this folder for selection policy, live-probe provenance, and freshness when refreshing the runtime list.

## Runtime vs knowledge

| Layer | Path | Role |
|---|---|---|
| **Runtime** (shipped) | `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/mcp/PopularMcpServers.kt` | Source of truth for the Settings → Tools one-tap list |
| **Knowledge** (this bundle) | `docs/knowledge/popular-mcp/` | Selection policy, last probe snapshot, sources, stale dates |
| **Refresh skill** | `.grok/skills/update-popular-mcp-servers/SKILL.md` | Probes endpoints, updates Kotlin + this bundle |

The app **never** reads this directory at runtime. No live MCP probes are made in production to decide which popular servers to show.

## Scope

- **In:** Remote **Streamable HTTP** MCP endpoints that are free (or free with an optional key) and that pass `initialize` + `tools/list`.
- **Out:** stdio servers, paid-only hosts, and any endpoint that is not MCP.
- **Out:** Product behavior of MCP connect / tool execution — that stays in [`docs/features/mcp.md`](../../features/mcp.md).

## Catalogs

* [Popular MCP servers](servers.md) — current list and last probe results

## Policy

* [Selection and probe policy](selection-policy.md) — what may be added, what must be dropped, how a probe is attested

## Playbooks

* [Refresh popular MCP servers](refresh-playbook.md) — end-to-end update procedure (also `/update-popular-mcp-servers`)

## History

* [Update log](log.md)
