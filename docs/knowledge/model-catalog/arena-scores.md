---
type: Catalog
title: Arena text Elo scores
description: Attested LMArena / Arena.ai text-leaderboard Elo scores mapped onto Kai catalog ids.
tags: [models, arena, elo, lmarena]
status: stable
resource: https://arena.ai/leaderboard/text
stale_after: 2026-08-26
generated: { by: process:update-model-catalog, at: 2026-08-12T19:56:19Z }
verified: { by: process:desktopTest-ModelCatalog, at: 2026-08-12T20:04:07Z }
sources:
  - id: arena-text
    resource: https://arena.ai/leaderboard/text
    title: Arena text leaderboard (overall)
  - id: matching-policy
    resource: /matching-policy.md
    title: Catalog matching and estimate policy
  - id: model-catalog-playbook
    resource: /refresh-playbook.md
    title: Refresh model-catalog playbook
---

# Policy

An Elo number is **attested** only when it comes from the Arena **text / overall** leaderboard (`resource` above).[^arena-text]

- Store the leaderboard's integer score (the number before `±`).
- Map each arena name onto catalog ids using the [matching policy](matching-policy.md).
- Scores for catalog ids that are not on the board are **estimated**. They live in the auto-fill section of `ModelCatalog.arenaScores` and must not be quoted as leaderboard facts.

Replace this snapshot only via the [refresh playbook](refresh-playbook.md).

# Snapshot

| Field | Value |
|---|---|
| Board | Text arena, overall, style control as shown on the page |
| Fetched | 2026-08-12 (page date Aug 12, 2026) |
| Models on board | 390 |
| Votes (page) | 7,775,261 |
| Catalog ids receiving an attested score | 328 |
| Catalog ids still estimated | 707 |

# Attested (text arena)

Arena name → Elo → catalog ids that carry this score after the refresh.

- #1 `claude-fable-5` — **1507** (±5) → `claude-fable-5`
- #2 `claude-opus-4-6-thinking` — **1505** (±4) → `claude-opus-4-6-thinking`
- #3 `claude-opus-4-7-thinking` — **1502** (±4) → `claude-opus-4-7-thinking`
- #4 `muse-spark-1.2 (xHigh)` — **1499** (±10) → `muse-spark-1.2-xhigh`
- #5 `claude-opus-4-6` — **1497** (±3) → `claude-opus-4-6`, `claude-opus-4.6`
- #6 `claude-opus-4-7` — **1494** (±4) → `claude-opus-4-7`, `claude-opus-4.7`
- #7 `claude-opus-5-high` — **1494** (±5) → `claude-opus-5-high`
- #8 `claude-opus-5-max` — **1491** (±7) → `claude-opus-5-max`
- #9 `qwen3.8-max` — **1491** (±8) → `qwen3.8-max`
- #10 `muse-spark-1.1` — **1489** (±6) → `muse-spark-1.1`
- #11 `kimi-k3-max` — **1489** (±6) → `kimi-k3-max`
- #12 `muse-spark` — **1488** (±6) → `muse-spark`
- #13 `gemini-3.1-pro-preview` — **1486** (±3) → `gemini-3.1-pro-preview`
- #14 `gemini-3-pro` — **1485** (±4) → `gemini-3-pro`
- #15 `gemini-3.6-flash` — **1484** (±6) → `gemini-3.6-flash`
- #16 `claude-opus-4-8-thinking` — **1482** (±5) → `claude-opus-4-8-thinking`
- #17 `gpt-5.5-high` — **1482** (±4) → `gpt-5.5-high`
- #18 `gpt-5.6-sol-xhigh` — **1481** (±6) → `gpt-5.6-sol-xhigh`
- #19 `gemini-3.5-flash-high` — **1477** (±5) → `gemini-3.5-flash-high`
- #20 `gpt-5.5` — **1477** (±4) → `gpt-5.5`
- #21 `gpt-5.4-high` — **1477** (±4) → `gpt-5.4-high`
- #22 `gpt-5.2-chat-latest-20260210` — **1476** (±4) → `gpt-5.2-chat-latest-20260210`
- #23 `gemini-3.5-flash-medium` — **1475** (±5) → `gemini-3.5-flash-medium`
- #24 `grok-4.20-beta1` — **1475** (±5) → `grok-4.20-beta1`
- #25 `qwen3.7-max-preview` — **1474** (±10) → `qwen3.7-max-preview`
- #26 `claude-opus-4-8` — **1474** (±5) → `claude-opus-4-8`, `claude-opus-4.8`
- #27 `gpt-5.5-instant` — **1473** (±5) → `gpt-5.5-instant`
- #28 `gemini-3-flash` — **1473** (±4) → `gemini-3-flash`
- #29 `claude-opus-4-5-20251101-thinking-32k` — **1473** (±4) → `claude-opus-4-5-20251101-thinking-32k`
- #30 `claude-sonnet-4-6` — **1472** (±4) → `claude-sonnet-4-6`, `claude-sonnet-4.6`
- #31 `grok-4.20-beta-0309-reasoning` — **1472** (±4) → `grok-4.20-beta-0309-reasoning`
- #32 `glm-5.2-max` — **1471** (±5) → `glm-5.2-max`
- #33 `grok-4.20-multi-agent-beta-0309` — **1471** (±4) → `grok-4.20-multi-agent-beta-0309`
- #34 `claude-opus-4-5-20251101` — **1469** (±3) → `claude-opus-4-5-20251101`
- #35 `grok-4.5` — **1468** (±5) → `grok-4.5`
- #36 `ernie-5.1` — **1468** (±5) → `ernie-5.1`
- #37 `mimo-v2.5-pro` — **1468** (±4) → `mimo-v2.5-pro`
- #38 `glm-5.1` — **1467** (±4) → `glm-5.1`
- #39 `qwen3.5-max-preview` — **1466** (±5) → `qwen3.5-max-preview`
- #40 `grok-4.1-thinking` — **1465** (±3) → `grok-4.1-thinking`
- #41 `gpt-5.4` — **1465** (±4) → `gpt-5.4`
- #42 `gpt-5.6-terra-xhigh` — **1465** (±6) → `gpt-5.6-terra-xhigh`
- #43 `grok-4.6-high` — **1464** (±12) → `grok-4-6-high`, `grok-4.6-high`
- #44 `claude-sonnet-5-high` — **1462** (±5) → `claude-sonnet-5-high`
- #45 `kimi-k2.6` — **1461** (±5) → `kimi-k2-6`, `kimi-k2.6`
- #46 `qwen3.6-max-preview` — **1460** (±8) → `qwen3.6-max-preview`
- #47 `grok-4.1` — **1459** (±3) → `grok-4.1`
- #48 `qwen3.7-plus` — **1458** (±5) → `qwen3.7-plus`
- #49 `gemini-3-flash (thinking-minimal)` — **1458** (±3) → `gemini-3-flash-thinking-minimal`
- #50 `deepseek-v4-pro` — **1458** (±4) → `deepseek-v4-pro`, `deepseek-v4-pro:free`
- #51 `gemini-3.5-flash-lite` — **1458** (±6) → `gemini-3.5-flash-lite`
- #52 `glm-5` — **1457** (±4) → `glm-5`
- #53 `hy3` — **1457** (±9) → `hy3`
- #54 `claude-sonnet-4-5-20250929-thinking-32k` — **1456** (±3) → `claude-sonnet-4-5-20250929-thinking-32k`
- #55 `deepseek-v4-pro-high-preview` — **1456** (±4) → `deepseek-v4-pro-high-preview`
- #56 `dola-seed-2.0-pro` — **1456** (±3) → `dola-seed-2.0-pro`
- #57 `claude-sonnet-4-5-20250929` — **1455** (±3) → `claude-sonnet-4-5-20250929`
- #58 `gpt-5.1-high` — **1455** (±4) → `gpt-5.1-high`
- #59 `gemma-4-31b` — **1451** (±8) → `gemma-4-31b`
- #60 `gpt-5.6-luna-xhigh` — **1451** (±6) → `gpt-5.6-luna-xhigh`
- #61 `kimi-k2.5-thinking` — **1450** (±3) → `kimi-k2.5-thinking`
- #62 `ernie-5.0-preview-1203` — **1449** (±7) → `ernie-5.0-preview-1203`
- #63 `claude-opus-4-1-20250805-thinking-16k` — **1449** (±3) → `claude-opus-4-1-20250805-thinking-16k`
- #64 `gpt-5.3-chat-latest` — **1449** (±4) → `gpt-5.3-chat-latest`
- #65 `mimo-v2-pro` — **1448** (±5) → `mimo-v2-pro`
- #66 `gpt-5.4-mini-high` — **1448** (±4) → `gpt-5.4-mini-high`
- #67 `claude-opus-4-1-20250805` — **1447** (±3) → `claude-opus-4-1-20250805`
- #68 `ernie-5.0-0110` — **1446** (±4) → `ernie-5.0-0110`
- #69 `gemini-2.5-pro` — **1445** (±2) → `gemini-2.5-pro`
- #70 `gpt-4.5-preview-2025-02-27` — **1445** (±6) → `gpt-4.5-preview-2025-02-27`
- #71 `qwen3.6-plus` — **1443** (±4) → `qwen3.6-plus`, `qwen3.6-plus-free`
- #72 `minimax-m3` — **1443** (±5) → `minimax-m3`
- #73 `chatgpt-4o-latest-20250326` — **1443** (±3) → `chatgpt-4o-latest-20250326`
- #74 `qwen3.5-397b-a17b` — **1442** (±4) → `qwen3.5-397b-a17b`
- #75 `inkling` — **1442** (±6) → `inkling`
- #76 `glm-4.7` — **1442** (±6) → `glm-4.7`
- #77 `grok-4.3` — **1441** (±4) → `grok-4.3`
- #78 `gpt-5.1` — **1439** (±4) → `gpt-5.1`
- #79 `deepseek-v4-flash-high-preview` — **1438** (±4) → `deepseek-v4-flash-high-preview`
- #80 `gemma-4-26b-a4b` — **1438** (±8) → `gemma-4-26b-a4b`
- #81 `gpt-5.2-high` — **1437** (±4) → `gpt-5.2-high`
- #82 `longcat-flash-chat-2602-exp` — **1436** (±5) → `longcat-flash-chat-2602-exp`
- #83 `gpt-5.2` — **1436** (±3) → `gpt-5.2`
- #84 `deepseek-v4-flash` — **1435** (±4) → `deepseek-v4-flash`, `deepseek-v4-flash-free`, `deepseek-v4-flash:free`
- #85 `qwen3-max-preview` — **1434** (±4) → `qwen3-max-preview`
- #86 `gpt-5-high` — **1434** (±4) → `gpt-5-high`
- #87 `mimo-v2.5` — **1434** (±4) → `mimo-v2.5`, `mimo-v2.5-free`
- #88 `glm-5v-turbo` — **1434** (±7) → `glm-5v-turbo`
- #89 `gemini-3.1-flash-lite-preview` — **1432** (±4) → `gemini-3.1-flash-lite-preview`
- #90 `kimi-k2.5-instant` — **1431** (±7) → `kimi-k2.5-instant`
- #91 `grok-4-1-fast-reasoning` — **1431** (±3) → `grok-4-1-fast-reasoning`
- #92 `o3-2025-04-16` — **1431** (±4) → `o3-2025-04-16`
- #— `AutoEval` — **1431** (±11) → —
- #93 `mimo-v2-omni` — **1430** (±6) → `mimo-v2-omni`
- #94 `kimi-k2-thinking-turbo` — **1430** (±3) → `kimi-k2-thinking-turbo`
- #95 `mistral-medium-3.5` — **1427** (±7) → `mistral-medium-3-5`, `mistral-medium-3.5`
- #96 `gpt-5-chat` — **1427** (±4) → `gpt-5-chat`
- #97 `nvidia-nemotron-3-ultra-550b-a55b-nvfp4` — **1427** (±7) → `nvidia-nemotron-3-ultra-550b-a55b-nvfp4`
- #98 `muse-glimmer` — **1426** (±10) → `muse-glimmer`
- #99 `amazon-nova-experimental-chat-26-02-10` — **1426** (±10) → `amazon-nova-experimental-chat-26-02-10`
- #100 `deepseek-v3.2` — **1425** (±4) → `deepseek-v3-2`, `deepseek-v3.2`
- #101 `deepseek-v3.2-exp-thinking` — **1425** (±7) → `deepseek-v3.2-exp-thinking`
- #102 `claude-opus-4-20250514-thinking-16k` — **1425** (±4) → `claude-opus-4-20250514-thinking-16k`
- #103 `glm-4.6` — **1424** (±4) → `glm-4.6`
- #104 `qwen3-max-2025-09-23` — **1424** (±6) → `qwen3-max-2025-09-23`
- #105 `qwen3-235b-a22b-instruct-2507` — **1423** (±3) → `qwen3-235b-a22b-instruct-2507`
- #106 `deepseek-v3.2-thinking` — **1423** (±4) → —
- #107 `deepseek-v3.2-exp` — **1422** (±6) → `deepseek-v3.2-exp`
- #108 `deepseek-r1-0528` — **1422** (±6) → `deepseek-r1-0528`
- #109 `grok-4-fast-chat` — **1420** (±8) → `grok-4-fast-chat`
- #110 `ernie-5.0-preview-1022` — **1418** (±9) → `ernie-5.0-preview-1022`
- #111 `kimi-k2-0711-preview` — **1418** (±5) → `kimi-k2-0711-preview`
- #112 `kimi-k2-0905-preview` — **1418** (±6) → `kimi-k2-0905-preview`
- #113 `deepseek-v3.1-terminus-thinking` — **1417** (±10) → `deepseek-v3.1-terminus-thinking`
- #114 `deepseek-v3.1` — **1417** (±6) → `deepseek-v3-1`, `deepseek-v3.1`
- #115 `qwen3.5-122b-a10b` — **1417** (±4) → `qwen3.5-122b-a10b`
- #116 `deepseek-v3.1-thinking` — **1416** (±7) → `deepseek-v3.1-thinking`
- #117 `minimax-m2.7` — **1416** (±4) → `minimax-m2.7`
- #118 `amazon-nova-experimental-chat-26-01-10` — **1415** (±10) → `amazon-nova-experimental-chat-26-01-10`
- #119 `mistral-large-3` — **1415** (±3) → `mistral-large-3`
- #120 `deepseek-v3.1-terminus` — **1415** (±10) → `deepseek-v3.1-terminus`
- #121 `qwen3-vl-235b-a22b-instruct` — **1414** (±6) → `qwen3-vl-235b-a22b-instruct`
- #122 `gpt-4.1-2025-04-14` — **1414** (±4) → `gpt-4.1-2025-04-14`
- #123 `hunyuan-hy3-preview` — **1413** (±8) → `hunyuan-hy3-preview`
- #124 `claude-haiku-4-5-20251001` — **1413** (±3) → `claude-haiku-4-5-20251001`
- #125 `claude-opus-4-20250514` — **1413** (±4) → `claude-opus-4-20250514`
- #126 `grok-3-preview-02-24` — **1411** (±4) → `grok-3-preview-02-24`
- #127 `glm-4.5` — **1411** (±5) → `glm-4.5`
- #128 `gemini-2.5-flash` — **1410** (±2) → `gemini-2.5-flash`
- #129 `grok-4-0709` — **1409** (±4) → `grok-4-0709`
- #130 `mistral-medium-2508` — **1409** (±3) → `mistral-medium-2508`
- #131 `qwen3.5-27b` — **1408** (±4) → `qwen3.5-27b`
- #132 `gemini-2.5-flash-preview-09-2025` — **1404** (±4) → `gemini-2.5-flash-preview-09-2025`
- #133 `grok-4-fast-reasoning` — **1404** (±5) → `grok-4-fast-reasoning`
- #134 `qwen3-235b-a22b-no-thinking` — **1403** (±5) → `qwen3-235b-a22b-no-thinking`
- #135 `gpt-5.4-nano-high` — **1402** (±4) → `gpt-5.4-nano-high`
- #136 `o1-2024-12-17` — **1402** (±4) → `o1-2024-12-17`
- #137 `longcat-flash-chat` — **1401** (±6) → `longcat-flash-chat`
- #138 `qwen3-next-80b-a3b-instruct` — **1401** (±5) → `qwen3-next-80b-a3b-instruct`, `qwen3-next-80b-a3b-instruct:free`
- #139 `claude-sonnet-4-20250514-thinking-32k` — **1400** (±4) → `claude-sonnet-4-20250514-thinking-32k`
- #140 `qwen3-235b-a22b-thinking-2507` — **1399** (±7) → `qwen3-235b-a22b-thinking-2507`
- #141 `deepseek-r1` — **1398** (±5) → `deepseek-r1`
- #142 `qwen3.5-flash` — **1397** (±4) → `qwen3.5-flash`
- #143 `deepseek-v3-0324` — **1396** (±4) → `deepseek-v3-0324`
- #144 `qwen3.5-35b-a3b` — **1395** (±4) → `qwen3.5-35b-a3b`
- #145 `qwen3-vl-235b-a22b-thinking` — **1395** (±7) → `qwen3-vl-235b-a22b-thinking`
- #146 `hunyuan-vision-1.5-thinking` — **1395** (±12) → `hunyuan-vision-1.5-thinking`
- #147 `step-3.5-flash` — **1394** (±4) → `step-3.5-flash`
- #148 `amazon-nova-experimental-chat-12-10` — **1394** (±10) → `amazon-nova-experimental-chat-12-10`
- #149 `mimo-v2-flash (non-thinking)` — **1392** (±4) → —
- #150 `minimax-m2.5` — **1390** (±4) → `minimax-m2.5`, `minimax-m2.5-free`, `minimax-m2.5:free`
- #151 `o4-mini-2025-04-16` — **1390** (±4) → `o4-mini-2025-04-16`
- #152 `gpt-5-mini-high` — **1390** (±5) → `gpt-5-mini-high`
- #153 `claude-sonnet-4-20250514` — **1390** (±4) → `claude-sonnet-4-20250514`
- #154 `o1-preview` — **1389** (±5) → `o1-preview`
- #155 `claude-3-7-sonnet-20250219-thinking-32k` — **1388** (±4) → `claude-3-7-sonnet-20250219-thinking-32k`
- #156 `qwen3-coder-480b-a35b-instruct` — **1388** (±5) → `qwen3-coder-480b-a35b-instruct`
- #157 `mistral-medium-2505` — **1387** (±5) → `mistral-medium-2505`
- #158 `hunyuan-t1-20250711` — **1387** (±9) → `hunyuan-t1-20250711`
- #159 `mimo-v2-flash (thinking)` — **1386** (±6) → `mimo-v2-flash-thinking`
- #160 `minimax-m2.1-preview` — **1384** (±5) → `minimax-m2.1-preview`
- #161 `qwen3-30b-a3b-instruct-2507` — **1383** (±5) → `qwen3-30b-a3b-instruct-2507`
- #162 `gpt-4.1-mini-2025-04-14` — **1383** (±4) → `gpt-4.1-mini-2025-04-14`
- #163 `hunyuan-turbos-20250416` — **1383** (±6) → `hunyuan-turbos-20250416`
- #164 `gemini-2.5-flash-lite-preview-09-2025-no-thinking` — **1380** (±3) → `gemini-2.5-flash-lite-preview-09-2025-no-thinking`
- #165 `trinity-large-preview` — **1379** (±4) → `trinity-large-preview`, `trinity-large-preview:free`
- #166 `solar-pro4` — **1378** (±12) → `solar-pro4`
- #167 `glm-4.6v` — **1377** (±11) → `glm-4.6v`
- #168 `gemini-2.5-flash-lite-preview-06-17-thinking` — **1375** (±5) → `gemini-2.5-flash-lite-preview-06-17-thinking`
- #169 `qwen3-235b-a22b` — **1375** (±5) → `qwen3-235b-a22b`
- #170 `qwen2.5-max` — **1374** (±4) → `qwen2.5-max`
- #171 `claude-3-5-sonnet-20241022` — **1373** (±3) → `claude-3-5-sonnet-20241022`
- #172 `glm-4.5-air` — **1373** (±4) → `glm-4.5-air`, `glm-4.5-air:free`
- #173 `claude-3-7-sonnet-20250219` — **1372** (±4) → `claude-3-7-sonnet-20250219`
- #174 `trinity-large-thinking` — **1369** (±5) → `trinity-large-thinking`, `trinity-large-thinking:free`
- #175 `qwen3-next-80b-a3b-thinking` — **1369** (±6) → `qwen3-next-80b-a3b-thinking`
- #176 `glm-4.7-flash` — **1367** (±6) → `glm-4.7-flash`
- #177 `gemma-3-27b-it` — **1366** (±4) → `gemma-3-27b-it`, `gemma-3-27b-it:free`
- #178 `amazon-nova-experimental-chat-11-10` — **1366** (±4) → `amazon-nova-experimental-chat-11-10`
- #179 `o3-mini-high` — **1364** (±5) → `o3-mini-high`
- #180 `minimax-m1` — **1363** (±4) → `minimax-m1`
- #181 `grok-3-mini-high` — **1362** (±5) → `grok-3-mini-high`
- #182 `gemini-2.0-flash-001` — **1360** (±4) → `gemini-2.0-flash-001`
- #183 `nvidia-nemotron-3-super-120b-a12b` — **1360** (±7) → `nvidia-nemotron-3-super-120b-a12b`
- #184 `deepseek-v3` — **1359** (±5) → `deepseek-v3`
- #185 `mistral-small-2506` — **1357** (±5) → `mistral-small-2506`
- #186 `grok-3-mini-beta` — **1356** (±5) → `grok-3-mini-beta`
- #187 `intellect-3` — **1356** (±8) → `intellect-3`
- #188 `command-a-03-2025` — **1354** (±3) → `command-a-03-2025`
- #189 `gemini-2.0-flash-lite-preview-02-05` — **1354** (±4) → `gemini-2.0-flash-lite-preview-02-05`
- #190 `glm-4.5v` — **1353** (±8) → `glm-4.5v`
- #191 `gpt-oss-120b` — **1352** (±4) → `gpt-oss-120b`, `gpt-oss-120b:free`
- #192 `gemini-1.5-pro-002` — **1351** (±3) → `gemini-1.5-pro-002`
- #193 `nvidia-nemotron-3.5-lightning-30b-a3b-nvfp4` — **1350** (±11) → `nvidia-nemotron-3.5-lightning-30b-a3b-nvfp4`
- #194 `amazon-nova-experimental-chat-10-20` — **1350** (±6) → `amazon-nova-experimental-chat-10-20`
- #195 `hunyuan-turbos-20250226` — **1349** (±12) → `hunyuan-turbos-20250226`
- #196 `step-3` — **1348** (±7) → `step-3`
- #197 `o3-mini` — **1348** (±4) → `o3-mini`
- #198 `llama-3.1-nemotron-ultra-253b-v1` — **1348** (±12) → `llama-3.1-nemotron-ultra-253b-v1`
- #199 `amazon-nova-experimental-chat-10-09` — **1347** (±11) → `amazon-nova-experimental-chat-10-09`
- #200 `qwen3-32b` — **1347** (±9) → `qwen3-32b`
- #201 `mercury-2` — **1346** (±11) → `mercury-2`
- #202 `qwen-plus-0125` — **1346** (±8) → `qwen-plus-0125`
- #203 `ling-flash-2.0` — **1346** (±7) → `ling-flash-2.0`
- #204 `gpt-4o-2024-05-13` — **1346** (±3) → `gpt-4o-2024-05-13`
- #205 `minimax-m2` — **1346** (±8) → `minimax-m2`
- #206 `nvidia-llama-3.3-nemotron-super-49b-v1.5` — **1343** (±10) → `nvidia-llama-3.3-nemotron-super-49b-v1.5`
- #207 `glm-4-plus-0111` — **1343** (±8) → `glm-4-plus-0111`
- #208 `claude-3-5-sonnet-20240620` — **1343** (±3) → `claude-3-5-sonnet-20240620`
- #209 `gemma-3-12b-it` — **1342** (±10) → `gemma-3-12b-it`, `gemma-3-12b-it:free`
- #210 `hunyuan-turbo-0110` — **1341** (±12) → `hunyuan-turbo-0110`
- #211 `gpt-5-nano-high` — **1337** (±7) → `gpt-5-nano-high`
- #212 `o1-mini` — **1337** (±4) → `o1-mini`
- #213 `qwq-32b` — **1336** (±4) → `qwq-32b`
- #214 `nova-2-lite` — **1336** (±6) → `nova-2-lite`
- #215 `grok-2-2024-08-13` — **1336** (±4) → `grok-2-2024-08-13`
- #216 `gemini-advanced-0514` — **1336** (±5) → `gemini-advanced-0514`
- #217 `gpt-4o-2024-08-06` — **1335** (±4) → `gpt-4o-2024-08-06`
- #218 `llama-3.1-405b-instruct-bf16` — **1335** (±4) → —
- #219 `step-2-16k-exp-202412` — **1334** (±9) → `step-2-16k-exp-202412`
- #220 `llama-3.1-405b-instruct-fp8` — **1333** (±4) → —
- #221 `olmo-3.1-32b-instruct` — **1329** (±6) → `olmo-3.1-32b-instruct`
- #222 `molmo-2-8b` — **1329** (±21) → `molmo-2-8b`
- #223 `yi-lightning` — **1328** (±5) → `yi-lightning`
- #224 `llama-3.3-nemotron-49b-super-v1` — **1328** (±12) → —
- #225 `qwen3-30b-a3b` — **1327** (±5) → `qwen3-30b-a3b`
- #226 `llama-4-maverick-17b-128e-instruct` — **1327** (±4) → `llama-4-maverick-17b-128e-instruct`
- #227 `hunyuan-large-2025-02-10` — **1326** (±10) → —
- #228 `gpt-4-turbo-2024-04-09` — **1324** (±4) → `gpt-4-turbo-2024-04-09`
- #229 `claude-3-5-haiku-20241022` — **1324** (±3) → `claude-3-5-haiku-20241022`
- #230 `gemini-1.5-pro-001` — **1324** (±4) → `gemini-1.5-pro-001`
- #231 `deepseek-v2.5-1210` — **1323** (±8) → `deepseek-v2.5-1210`
- #232 `llama-4-scout-17b-16e-instruct` — **1322** (±5) → `llama-4-scout-17b-16e-instruct`
- #233 `gpt-4.1-nano-2025-04-14` — **1322** (±8) → `gpt-4.1-nano-2025-04-14`
- #234 `claude-3-opus-20240229` — **1322** (±3) → `claude-3-opus-20240229`
- #235 `ring-flash-2.0` — **1320** (±7) → `ring-flash-2.0`
- #236 `step-1o-turbo-202506` — **1320** (±7) → —
- #237 `glm-4-plus` — **1319** (±5) → `glm-4-plus`
- #238 `llama-3.3-70b-instruct` — **1318** (±3) → `llama-3.3-70b-instruct`, `llama-3.3-70b-instruct:free`
- #239 `gemma-3n-e4b-it` — **1318** (±5) → `gemma-3n-e4b-it`, `gemma-3n-e4b-it:free`
- #240 `qwen-max-0919` — **1318** (±6) → —
- #241 `gpt-4o-mini-2024-07-18` — **1318** (±4) → `gpt-4o-mini-2024-07-18`
- #242 `gpt-oss-20b` — **1318** (±6) → `gpt-oss-20b`, `gpt-oss-20b:free`
- #243 `nvidia-nemotron-3-nano-30b-a3b-bf16` — **1315** (±6) → —
- #244 `qwen2.5-plus-1127` — **1315** (±6) → —
- #245 `athene-v2-chat` — **1314** (±5) → —
- #246 `mistral-large-2407` — **1314** (±4) → `mistral-large-2407`
- #247 `gpt-4-0125-preview` — **1313** (±4) → `gpt-4-0125-preview`
- #248 `gpt-4-1106-preview` — **1312** (±4) → `gpt-4-1106-preview`
- #249 `hunyuan-standard-2025-02-10` — **1311** (±10) → —
- #250 `gemini-1.5-flash-002` — **1309** (±4) → `gemini-1.5-flash-002`
- #251 `grok-2-mini-2024-08-13` — **1308** (±4) → —
- #252 `deepseek-v2.5` — **1307** (±5) → `deepseek-v2.5`
- #253 `athene-70b-0725` — **1307** (±6) → —
- #254 `mercury` — **1306** (±14) → `mercury`
- #255 `granite-4.1-8b` — **1306** (±10) → `granite-4.1-8b`
- #256 `mistral-large-2411` — **1305** (±4) → `mistral-large-2411`
- #257 `olmo-3-32b-think` — **1305** (±8) → `olmo-3-32b-think`
- #258 `magistral-medium-2506` — **1304** (±6) → `magistral-medium-2506`
- #259 `mistral-small-3.1-24b-instruct-2503` — **1303** (±5) → `mistral-small-3.1-24b-instruct-2503`
- #260 `gemma-3-4b-it` — **1303** (±9) → `gemma-3-4b-it`, `gemma-3-4b-it:free`
- #261 `qwen2.5-72b-instruct` — **1303** (±4) → `qwen2.5-72b-instruct`
- #262 `llama-3.1-nemotron-70b-instruct` — **1299** (±8) → `llama-3.1-nemotron-70b-instruct`
- #263 `hunyuan-large-vision` — **1294** (±9) → —
- #264 `llama-3.1-70b-instruct` — **1293** (±4) → `llama-3.1-70b-instruct`
- #265 `amazon-nova-pro-v1.0` — **1290** (±5) → —
- #266 `jamba-1.5-large` — **1289** (±7) → `jamba-1.5-large`
- #267 `gemma-2-27b-it` — **1289** (±3) → `gemma-2-27b-it`
- #268 `reka-core-20240904` — **1288** (±7) → `reka-core-20240904`
- #269 `gpt-4-0314` — **1287** (±5) → `gpt-4-0314`
- #270 `ibm-granite-h-small` — **1287** (±8) → —
- #271 `gemini-1.5-flash-001` — **1286** (±5) → `gemini-1.5-flash-001`
- #272 `llama-3.1-nemotron-51b-instruct` — **1286** (±10) → `llama-3.1-nemotron-51b-instruct`
- #273 `llama-3.1-tulu-3-70b` — **1286** (±10) → —
- #274 `olmo-3.1-32b-think` — **1285** (±7) → —
- #275 `claude-3-sonnet-20240229` — **1281** (±4) → `claude-3-sonnet-20240229`
- #276 `gemma-2-9b-it-simpo` — **1280** (±7) → —
- #277 `nemotron-4-340b-instruct` — **1277** (±5) → `nemotron-4-340b-instruct`
- #278 `llama-3-70b-instruct` — **1276** (±4) → `llama-3-70b-instruct`
- #279 `command-r-plus-08-2024` — **1276** (±7) → `command-r-plus-08-2024`
- #280 `gpt-4-0613` — **1275** (±4) → `gpt-4-0613`
- #281 `mistral-small-24b-instruct-2501` — **1274** (±6) → `mistral-small-24b-instruct-2501`
- #282 `glm-4-0520` — **1273** (±7) → —
- #283 `reka-flash-20240904` — **1272** (±7) → —
- #284 `qwen2.5-coder-32b-instruct` — **1270** (±8) → `qwen2.5-coder-32b-instruct`
- #285 `c4ai-aya-expanse-32b` — **1267** (±5) → `c4ai-aya-expanse-32b`
- #286 `gemma-2-9b-it` — **1267** (±4) → `gemma-2-9b-it`
- #287 `deepseek-coder-v2` — **1265** (±6) → `deepseek-coder-v2`
- #288 `qwen2-72b-instruct` — **1261** (±5) → `qwen2-72b-instruct`
- #289 `command-r-plus` — **1261** (±4) → `command-r-plus`
- #290 `claude-3-haiku-20240307` — **1261** (±4) → `claude-3-haiku-20240307`
- #291 `amazon-nova-lite-v1.0` — **1260** (±5) → —
- #292 `gemini-1.5-flash-8b-001` — **1259** (±4) → `gemini-1.5-flash-8b-001`
- #293 `phi-4` — **1256** (±5) → `phi-4`
- #294 `olmo-2-0325-32b-instruct` — **1251** (±11) → `olmo-2-0325-32b-instruct`
- #295 `command-r-08-2024` — **1250** (±7) → `command-r-08-2024`
- #296 `mistral-large-2402` — **1242** (±5) → —
- #297 `amazon-nova-micro-v1.0` — **1241** (±5) → —
- #298 `jamba-1.5-mini` — **1240** (±7) → `jamba-1.5-mini`
- #299 `ministral-8b-2410` — **1237** (±9) → `ministral-8b-2410`
- #300 `gemini-pro-dev-api` — **1236** (±7) → —
- #301 `qwen1.5-110b-chat` — **1234** (±6) → —
- #302 `hunyuan-standard-256k` — **1233** (±12) → —
- #303 `reka-flash-21b-20240226-online` — **1233** (±7) → —
- #304 `qwen1.5-72b-chat` — **1233** (±5) → —
- #305 `mixtral-8x22b-instruct-v0.1` — **1229** (±5) → `mixtral-8x22b-instruct-v0.1`
- #306 `reka-flash-21b-20240226` — **1226** (±6) → —
- #307 `command-r` — **1226** (±5) → `command-r`
- #308 `gpt-3.5-turbo-0125` — **1225** (±5) → `gpt-3.5-turbo-0125`
- #309 `llama-3-8b-instruct` — **1223** (±4) → `llama-3-8b-instruct`
- #310 `c4ai-aya-expanse-8b` — **1223** (±7) → `c4ai-aya-expanse-8b`
- #311 `gemini-pro` — **1223** (±12) → —
- #312 `mistral-medium` — **1222** (±5) → `mistral-medium`
- #313 `llama-3.1-tulu-3-8b` — **1220** (±11) → —
- #314 `yi-1.5-34b-chat` — **1213** (±5) → —
- #315 `zephyr-orpo-141b-A35b-v0.1` — **1212** (±11) → —
- #316 `llama-3.1-8b-instruct` — **1211** (±4) → `llama-3.1-8b-instruct`
- #317 `granite-3.1-8b-instruct` — **1208** (±11) → `granite-3.1-8b-instruct`
- #318 `qwen1.5-32b-chat` — **1203** (±6) → —
- #319 `gpt-3.5-turbo-1106` — **1203** (±9) → `gpt-3.5-turbo-1106`
- #320 `gemma-2-2b-it` — **1200** (±4) → `gemma-2-2b-it`
- #321 `phi-3-medium-4k-instruct` — **1197** (±5) → `phi-3-medium-4k-instruct`
- #322 `mixtral-8x7b-instruct-v0.1` — **1197** (±4) → `mixtral-8x7b-instruct-v0.1`
- #323 `dbrx-instruct-preview` — **1195** (±6) → —
- #324 `internlm2\_5-20b-chat` — **1191** (±7) → —
- #325 `qwen1.5-14b-chat` — **1191** (±7) → —
- #326 `deepseek-llm-67b-chat` — **1184** (±11) → —
- #327 `wizardlm-70b` — **1184** (±9) → —
- #328 `yi-34b-chat` — **1183** (±7) → `yi-34b-chat`
- #329 `granite-3.0-8b-instruct` — **1182** (±9) → `granite-3.0-8b-instruct`
- #330 `openchat-3.5` — **1182** (±10) → —
- #331 `openchat-3.5-0106` — **1182** (±8) → —
- #332 `gemma-1.1-7b-it` — **1182** (±6) → —
- #333 `snowflake-arctic-instruct` — **1179** (±6) → —
- #334 `granite-3.1-2b-instruct` — **1179** (±11) → `granite-3.1-2b-instruct`
- #335 `tulu-2-dpo-70b` — **1177** (±10) → —
- #336 `openhermes-2.5-mistral-7b` — **1175** (±10) → —
- #337 `vicuna-33b` — **1172** (±6) → —
- #338 `starling-lm-7b-beta` — **1171** (±7) → —
- #339 `phi-3-small-8k-instruct` — **1171** (±6) → `phi-3-small-8k-instruct`
- #340 `llama-2-70b-chat` — **1170** (±5) → —
- #341 `starling-lm-7b-alpha` — **1167** (±8) → —
- #342 `llama-3.2-3b-instruct` — **1166** (±8) → `llama-3.2-3b-instruct`, `llama-3.2-3b-instruct:free`
- #343 `nous-hermes-2-mixtral-8x7b-dpo` — **1164** (±12) → `nous-hermes-2-mixtral-8x7b-dpo`
- #344 `granite-3.0-2b-instruct` — **1156** (±8) → `granite-3.0-2b-instruct`
- #345 `qwq-32b-preview` — **1154** (±11) → `qwq-32b-preview`
- #346 `llama2-70b-steerlm-chat` — **1154** (±13) → —
- #347 `solar-10.7b-instruct-v1.0` — **1152** (±13) → —
- #348 `dolphin-2.2.1-mistral-7b` — **1152** (±15) → —
- #349 `mpt-30b-chat` — **1150** (±12) → —
- #350 `wizardlm-13b` — **1149** (±9) → —
- #351 `mistral-7b-instruct-v0.2` — **1149** (±7) → `mistral-7b-instruct-v0.2`
- #352 `falcon-180b-chat` — **1147** (±17) → —
- #353 `qwen1.5-7b-chat` — **1143** (±10) → —
- #354 `phi-3-mini-4k-instruct-june-2024` — **1143** (±6) → —
- #355 `llama-2-13b-chat` — **1141** (±7) → —
- #356 `vicuna-13b` — **1141** (±7) → —
- #357 `qwen-14b-chat` — **1139** (±11) → —
- #358 `palm-2` — **1138** (±9) → —
- #359 `gemma-7b-it` — **1137** (±9) → —
- #360 `codellama-34b-instruct` — **1136** (±9) → —
- #361 `zephyr-7b-beta` — **1130** (±9) → —
- #362 `phi-3-mini-128k-instruct` — **1129** (±7) → `phi-3-mini-128k-instruct`
- #363 `phi-3-mini-4k-instruct` — **1128** (±6) → `phi-3-mini-4k-instruct`
- #364 `guanaco-33b` — **1127** (±12) → —
- #365 `zephyr-7b-alpha` — **1126** (±16) → —
- #366 `stripedhyena-nous-7b` — **1121** (±11) → —
- #367 `codellama-70b-instruct` — **1119** (±18) → —
- #368 `gemma-1.1-2b-it` — **1116** (±8) → —
- #369 `vicuna-7b` — **1115** (±9) → —
- #370 `smollm2-1.7b-instruct` — **1114** (±14) → —
- #371 `llama-3.2-1b-instruct` — **1111** (±8) → `llama-3.2-1b-instruct`
- #372 `mistral-7b-instruct` — **1110** (±9) → `mistral-7b-instruct`
- #373 `llama-2-7b-chat` — **1108** (±7) → —
- #374 `gemma-2b-it` — **1093** (±11) → —
- #375 `qwen1.5-4b-chat` — **1090** (±9) → —
- #376 `olmo-7b-instruct` — **1073** (±11) → —
- #377 `koala-13b` — **1070** (±10) → —
- #378 `alpaca-13b` — **1069** (±11) → —
- #379 `gpt4all-13b-snoozy` — **1067** (±15) → —
- #380 `mpt-7b-chat` — **1063** (±12) → —
- #381 `chatglm3-6b` — **1056** (±12) → `chatglm3-6b`
- #382 `RWKV-4-Raven-14B` — **1042** (±11) → —
- #383 `chatglm2-6b` — **1024** (±14) → —
- #384 `oasst-pythia-12b` — **1023** (±11) → —
- #385 `chatglm-6b` — **995** (±13) → —
- #386 `fastchat-t5-3b` — **992** (±12) → —
- #387 `dolly-v2-12b` — **981** (±13) → —
- #388 `llama-13b` — **974** (±16) → —
- #389 `stablelm-tuned-alpha-7b` — **953** (±13) → —

# Estimated

707 catalog ids keep a **family / sibling / alias estimate** (or an older attested value that this refresh did not rematch). They are not re-derived on this pass.

Do not treat those numbers as Arena facts. See [matching-policy.md](matching-policy.md).

# Notes

- Runtime lookup strips a leading `provider/` prefix and lowercases the id; catalog keys are already lowercase.
- New board models added to the runtime catalog on this refresh: `grok-4.6` / `grok-4.6-high`, `muse-glimmer`, `solar-pro4`, `nemotron-3.5-lightning`.
- Historical unmatched board rows (older chat models Kai does not ship metadata for) are listed above with `→ —` and were **not** added to `baseEntries`.

[^arena-text]: Arena text leaderboard
