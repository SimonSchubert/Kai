# Feature Docs

Feature specs live in `docs/features/`. Each describes a feature from a product/behavior perspective — no Kotlin code blocks, no class/function names in prose.

When you modify logic in a feature area that has a corresponding doc in `docs/features/`:
- Update the doc to reflect the new behavior
- Update the "Last verified" date in the doc header
- Keep the Key Files table accurate (add/remove files as needed)

# Knowledge bundles (OKF)

Agent-curated knowledge that is not a product feature spec lives under `docs/knowledge/` as OKF-style markdown (YAML frontmatter + body).

Current bundles:

- `docs/knowledge/free-tier/` — free-tier model policy, snapshots, and provenance for OpenRouter and Ollama Cloud. Runtime Free badges still come from `FreeTierModels.kt`; refresh **both** via `/update-free-tier-models`.
- `docs/knowledge/model-catalog/` — Arena text Elo matching policy, attested snapshot, and provenance. Runtime Elo still comes from `ModelCatalog.kt`; refresh **both** via `/update-model-catalog`.
- `docs/knowledge/popular-mcp/` — one-tap MCP endpoint policy, last probe snapshot, and provenance. Runtime list still comes from `PopularMcpServers.kt`; refresh **both** via `/update-popular-mcp-servers`.
- `docs/knowledge/litert/` — on-device model pin policy, attested commit/digest/size snapshot, and provenance. Runtime pins still come from `LocalModelCatalog.kt`; check or bump via `/update-litert-models` (no silent bump).
