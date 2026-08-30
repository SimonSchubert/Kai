# Model-catalog knowledge update log

## 2026-08-29

* **Update**: Live refresh via `process:update-model-catalog` from [arena.ai/leaderboard/text](https://arena.ai/leaderboard/text) (Aug 27, 2026; 395 models; 7.9M votes).
  * **Attested** — existing catalog scores moved to the live board where the name matched exactly, via punctuation / `:free` aliases, or as a same-model alias that already shared a score. Typical drift is 1–3 Elo. Named examples: `qwen3.8-max` 1491 → 1479, `grok-4.6-high` 1464 → 1461, `claude-opus-5-max` 1491 → 1488, `muse-spark-1.2-xhigh` 1499 → 1498, `gemini-3.5-flash-high` 1477 → 1479.
  * **New catalog entries** — `claude-opus-4-6-high` / `4-7-high` / `4-8-high` (and 4.5/Sonnet 4.5 high-32k rows), `gemini-3.7-flash-high` (1490), `gemini-3.6-flash-high` (1481), `glm-5.3-max` (1484) / `glm-5.3-flash` (1469), `deepseek-v4-pro-high-20260813` (1462), `qwen3.8-27b` (1436), `inkling-small` (1407), `grok-3-mini-high` (1364).
  * **Not copied** — dated snapshots onto a different dated/generic id; thinking / xHigh / high tiers onto the base id (`muse-spark-1.2 (xHigh)` → `muse-spark-1.2-xhigh` only; Claude `-high` is not `-thinking`); unrelated ids that only shared a source line (`gpt-4o-mini` vs `gpt-oss-20b`).
  * **Estimates** — left unchanged except where an id was newly attested (`grok-3-mini-high`, `qwen3.6-plus-free`, `minimax-m2.5-free`, `nvidia-llama-3.3-nemotron-super-49b-v1.5`).

## 2026-08-12

* **Update**: Live refresh via `process:update-model-catalog` from [arena.ai/leaderboard/text](https://arena.ai/leaderboard/text) (Aug 12, 2026; 390 models; 7.8M votes).
  * **Attested** — existing catalog scores moved to the live board where the name matched exactly, via punctuation / `:free` aliases, or as a same-model alias that already shared a score. Typical drift is 1–3 Elo. Named examples: `qwen3.8-max` 1497 → 1491, `kimi-k3-max` 1485 → 1489, `claude-opus-5-max` 1488 → 1491, `muse-spark-1.2-xhigh` 1498 → 1499.
  * **New catalog entries** — `grok-4.6` / `grok-4.6-high` (1464), `muse-glimmer` (1426), `solar-pro4` (1378), `nemotron-3.5-lightning` (1350).
  * **Not copied** — dated snapshots onto a different dated/generic id; thinking / xHigh tiers onto the base id; unrelated ids that only shared a source line (`gpt-4o-mini` vs `gpt-oss-20b`, `command-r` vs `command-r-08-2024`, `glm-4-plus` vs `glm-4-plus-0111`).
  * **Estimates** — left unchanged except where an id was newly attested.
* **Initialization**: Created OKF model-catalog bundle (arena scores + matching policy). Runtime source of truth remains `ModelCatalog.kt`.
