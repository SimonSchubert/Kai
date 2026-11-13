# Model-catalog knowledge update log

## 2026-10-02

* **Update**: Live refresh via `process:update-model-catalog` from [arena.ai/leaderboard/text](https://arena.ai/leaderboard/text) (Oct 2, 2026; 413 models; 8.6M votes).
  * **Attested** — 42 board rows drifted 1–2 Elo; 62 catalog ids moved with them (exact names, punctuation / `:free` aliases, same-model alias lines). Named examples: `claude-fable-5-high` 1505 → 1504, `gemini-3.8-flash-high` 1494 → 1495, `muse-spark-1.3-max` 1495 → 1494, `glm-5.2-max` 1475 → 1476, `mimo-v2.6-flash` 1454 → 1452.
  * **New catalog entries** — `gpt-6.1-sol-max` (1483), `claude-sonnet-5.5-xhigh` / `claude-sonnet-5-5-xhigh` (1471). Sonnet 5.5: Sep 2026, 1M context per the Anthropic announcement. GPT-6.1 Sol: Oct 2026 (first board appearance); 1,050,000 context is the GPT-6 family default because no figure is published yet.
  * **Not copied** — tier rows onto the shipping base ids: `gpt-6.1-sol`, `claude-sonnet-5.5` / `claude-sonnet-5-5` are estimated from their tier sibling in auto-fill. `Step 5 Preview` (StepFun, no API id / context / pricing) left unmatched.
  * **Estimates** — left unchanged except the new shipping base ids above.

## 2026-10-01

* **Update**: Live refresh via `process:update-model-catalog` from [arena.ai/leaderboard/text](https://arena.ai/leaderboard/text) (Sep 30, 2026; 410 models; 8.6M votes).
  * **Attested** — existing catalog scores moved to the live board where the name matched exactly, via punctuation / `:free` aliases, or as a same-model alias that already shared a score. Typical drift is 1–3 Elo. Named examples: `grok-4.6-high` 1461 → 1453, `solar-pro4` 1377 → 1386, `muse-spark-1.2-xhigh` 1499 → 1494, `nemotron-3.5-lightning` 1355 → 1349, `claude-fable-5.1-max` 1504 → 1501.
  * **New catalog entries** — `gemini-4-argon-high` (1525, new #1), `claude-opus-5.5-high` / `claude-opus-5-5-high` (1504), `muse-spark-1.3-max` (1495), `mimo-v2.6-pro` (1480), `gpt-6-astra-max` (1476), `deepseek-v4.1-flash-max` (1473), `gpt-6-sol-max` (1456), `mimo-v2.6-flash` (1454), `gpt-6-luna-max` (1444), `grok-4.7-xhigh` (1442), plus `claude-fable-5-high` (1505) for the renamed Fable 5 row. All Sep 2026 releases except Fable 5 (Jun 2026).
  * **Not copied** — tier rows onto shipping base ids: `gemini-4-argon`, `claude-opus-5-5` / `claude-opus-5.5`, `muse-spark-1.3`, `gpt-6-astra` / `-sol` / `-luna`, `deepseek-v4.1-flash`, `grok-4.7` are estimated from their tier sibling in auto-fill. Arena renamed `claude-fable-5` → `claude-fable-5-high`; `claude-fable-5` / `claude-fable-latest` keep 1507 as an estimate rather than inheriting the `-high` score.
  * **Estimates** — left unchanged except the new shipping base ids above and moving `claude-fable-5` / `claude-fable-latest` into auto-fill.

## 2026-09-07

* **Update**: Live refresh via `process:update-model-catalog` from [arena.ai/leaderboard/text](https://arena.ai/leaderboard/text) (Sep 2, 2026; 400 models; 8.0M votes).
  * **Attested** — existing catalog scores moved to the live board where the name matched exactly, via punctuation / `:free` aliases, or as a same-model alias that already shared a score. Typical drift is 1–3 Elo. Named examples: `muse-spark-1.1` 1490 → 1492, `glm-5.3-flash` 1469 → 1474, `glm-5.3-max` 1484 → 1482, `gemini-3.7-flash-high` 1490 → 1491, `nemotron-3.5-lightning` 1348 → 1355.
  * **New catalog entries** — `claude-fable-5.1-max` / `claude-fable-5-1-max` (shipping `claude-fable-5.1` / `claude-fable-5-1` estimated from max), `gemini-3.8-flash-high` (shipping `gemini-3.8-flash` estimated from high), `granite-4.2-30b` / `granite-4.2-8b` / `granite-4.2-3b`.
  * **Not copied** — dated snapshots onto a different dated/generic id; thinking / xHigh / max tiers onto the base id (`muse-spark-1.2 (xHigh)` → `muse-spark-1.2-xhigh` only; `claude-fable-5.1-max` is not the shipping base id). `deepseek-v3.1-terminus-thinking` (1418) was split off the `deepseek-v3.1` line (1417).
  * **Estimates** — left unchanged except the new shipping siblings (`claude-fable-5.1`, `gemini-3.8-flash`) and moving `muse-spark-1.2` into auto-fill.
* **Update**: Targeted addition of the shipping GPT-5.6 ids — no board fetch (the 2026-08-29 snapshot is still inside `stale_after`), so the attested list is untouched.
  * **New catalog entries** — `gpt-5.6-sol`, `gpt-5.6-terra`, `gpt-5.6-luna`, and the `gpt-5.6` alias. GA 2026-07-09; 1.05M context / 128K max output per [the OpenAI models reference](https://developers.openai.com/api/docs/models). Added because Kai now routes this family to the Responses API (issue #469) and the ids were falling back to the 100K default context window, which trimmed history far earlier than the model needs.
  * **Estimates** — each base id inherits its attested `-xhigh` sibling: Sol / the `gpt-5.6` alias 1482, Terra 1466, Luna 1452. Recorded in the auto-fill block, not the attested snapshot: the board lists only the `-xhigh` tiers, and the quality-tier rule forbids attesting those onto a base id.
  * **Correction** — `gpt-5.6-*-xhigh` context windows moved 1_100_000 → 1_050_000 to match the documented figure and the rest of the family.

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
