# Popular MCP knowledge update log

## 2026-08-12

* **Update**: Live probe via `process:update-popular-mcp-servers` (initialize protocol `2024-11-05` + `tools/list`).
  * **Attested live** — Context7, MDN, DeepWiki, Parallel Search, Yahoo Finance, CoinGecko, Jina AI (no key; 22 tools listed), Open-Meteo Weather, Kiwi.com, Malwarebytes, tldraw, Find-A-Domain, SubwayInfo NYC.
  * **Dropped** — Manifold Markets (`https://api.manifold.markets/v0/mcp`): Cloudflare 526 invalid origin TLS, `retryable: false` / `owner_action_required`. 14 → 13 hosts.
  * **Policy** — non-retryable origin 526/530 is a must-drop, distinct from a flaky timeout/5xx.
* **Initialization**: Created OKF popular-mcp bundle (selection policy + probe playbook). Runtime source of truth remains `PopularMcpServers.kt`.
