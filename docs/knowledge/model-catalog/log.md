# Model-catalog knowledge update log

## 2026-08-12

* **Update**: Live refresh via `process:update-model-catalog` from [arena.ai/leaderboard/text](https://arena.ai/leaderboard/text) (Aug 12, 2026; 390 models; 7.8M votes).
  * **Attested** — existing catalog scores moved to the live board where the name matched exactly, via punctuation / `:free` aliases, or as a same-model alias that already shared a score. Typical drift is 1–3 Elo. Named examples: `qwen3.8-max` 1497 → 1491, `kimi-k3-max` 1485 → 1489, `claude-opus-5-max` 1488 → 1491, `muse-spark-1.2-xhigh` 1498 → 1499.
  * **New catalog entries** — `grok-4.6` / `grok-4.6-high` (1464), `muse-glimmer` (1426), `solar-pro4` (1378), `nemotron-3.5-lightning` (1350).
  * **Not copied** — dated snapshots onto a different dated/generic id; thinking / xHigh tiers onto the base id; unrelated ids that only shared a source line (`gpt-4o-mini` vs `gpt-oss-20b`, `command-r` vs `command-r-08-2024`, `glm-4-plus` vs `glm-4-plus-0111`).
  * **Estimates** — left unchanged except where an id was newly attested.
* **Initialization**: Created OKF model-catalog bundle (arena scores + matching policy). Runtime source of truth remains `ModelCatalog.kt`.
