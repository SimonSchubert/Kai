---
type: Catalog
title: Ollama Cloud free-tier models
description: Ollama Cloud models that fit free-plan / light-usage quotas (usage level Low).
tags: [models, free-tier, ollama-cloud]
status: stable
resource: https://ollama.com/v1/models
stale_after: 2026-08-25
generated: { by: process:update-free-tier-models, at: 2026-08-11T17:42:05Z }
verified: { by: process:desktopTest-FreeTierModels, at: 2026-08-11T17:43:36Z }
sources:
  - id: ollama-v1-models
    resource: https://ollama.com/v1/models
    title: Ollama Cloud models API
  - id: ollama-library
    resource: https://ollama.com/library
    title: Ollama model library (usage levels)
  - id: free-tier-playbook
    resource: /refresh-playbook.md
    title: Refresh free-tier catalogs playbook
---

# Policy

Include a model id when **all** of the following hold:

1. It appears (or is a clear API-style form of a model) on the Ollama Cloud models API.[^ollama-v1-models]
2. The library page for that model reports usage level **Low** (best free-plan fit).[^ollama-library]
3. It is not **High** or **Extra High** (Pro-oriented heavy models).

Default product policy: **Low only**. Do not add **Medium** unless product explicitly expands scope.

## Id shape

- Store **API-style** ids as returned by `/v1/models` (often without a `-cloud` suffix), lowercase.
- Runtime `normalizeOllamaId` maps library-style aliases (`*:cloud`, `*-cloud`) onto these entries — do **not** duplicate every alias in this set unless needed for a distinct free-eligible id.
- When checking library pages, also try `{id}:cloud` and `{name}:{tag}-cloud` when the API omits the cloud tag.

When unsure about free eligibility, leave the id **out** (conservative).

# Current set

Snapshot mirrored into `FreeTierModels.ollamaCloudFree` (runtime). Replace this list only via the [refresh playbook](refresh-playbook.md).

- `gpt-oss:20b`
- `gemma4:31b`
- `nemotron-3-nano:30b`

# Notes

- Free-ness applies only to the **Ollama Cloud** service instance.
- Example alias coverage at runtime: `gemma4:31b-cloud` matches `gemma4:31b` after normalization.

[^ollama-v1-models]: Ollama Cloud models API
[^ollama-library]: Ollama model library (usage levels)
