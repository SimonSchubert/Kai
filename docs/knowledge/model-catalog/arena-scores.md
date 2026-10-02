---
type: Catalog
title: Arena text Elo scores
description: Attested LMArena / Arena.ai text-leaderboard Elo scores mapped onto Kai catalog ids.
tags: [models, arena, elo, lmarena]
status: stable
resource: https://arena.ai/leaderboard/text
stale_after: 2026-10-15
generated: { by: process:update-model-catalog, at: 2026-10-01T08:03:00Z }
verified: { by: process:desktopTest-ModelCatalog, at: 2026-10-01T08:04:39Z }
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
| Fetched | 2026-10-01 (page date Sep 30, 2026) |
| Models on board | 410 |
| Votes (page) | 8,602,501 |
| Catalog ids receiving an attested score | 511 |
| Catalog ids still estimated | 573 |

# Attested (text arena)

Arena name → Elo → catalog ids that carry this score after the refresh.

- #1 `gemini-4-argon-high` — **1525** (±9) → `gemini-4-argon-high`
- #2 `claude-opus-4-6-high` — **1505** (±3) → `claude-opus-4-6-high`, `claude-opus-4.6-high`
- #3 `claude-fable-5-high` — **1505** (±4) → `claude-fable-5-high`
- #4 `claude-opus-5.5-high` — **1504** (±10) → `claude-opus-5-5-high`, `claude-opus-5.5-high`
- #5 `claude-opus-4-7-high` — **1502** (±4) → `claude-opus-4-7-high`, `claude-opus-4.7-high`
- #6 `claude-fable-5.1-max` — **1501** (±7) → `claude-fable-5-1-max`, `claude-fable-5.1-max`
- #7 `claude-opus-4-6` — **1497** (±3) → `claude-opus-4-6`, `claude-opus-4.6`
- #8 `muse-spark-1.3-max` — **1495** (±6) → `muse-spark-1.3-max`
- #9 `claude-opus-4-7` — **1494** (±4) → `claude-opus-4-7`, `claude-opus-4.7`
- #10 `muse-spark-1.2 (xHigh)` — **1494** (±10) → `muse-spark-1.2-xhigh`
- #11 `gemini-3.8-flash-high` — **1494** (±5) → `gemini-3.8-flash-high`
- #12 `muse-spark-1.1` — **1492** (±4) → `muse-spark-1.1`
- #13 `claude-opus-5-high` — **1491** (±4) → `claude-opus-5`, `claude-opus-5-high`
- #14 `claude-opus-5-max` — **1489** (±5) → `claude-opus-5-max`
- #15 `muse-spark` — **1489** (±6) → `muse-spark`
- #16 `gemini-3.7-flash-high` — **1488** (±5) → `gemini-3.7-flash-high`
- #17 `kimi-k3-max` — **1488** (±5) → `kimi-k3-max`
- #18 `gemini-3.1-pro-preview` — **1487** (±3) → `gemini-3.1-pro-preview`
- #19 `gemini-3-pro` — **1485** (±4) → `gemini-3-pro`
- #20 `gpt-5.6-sol-xhigh` — **1484** (±4) → `gpt-5.6-sol-xhigh`
- #21 `gemini-3.6-flash-high` — **1483** (±4) → `gemini-3.6-flash-high`
- #22 `gpt-5.5-high` — **1481** (±4) → `gpt-5.5-high`
- #23 `claude-opus-4-8-high` — **1481** (±4) → `claude-opus-4-8-high`, `claude-opus-4.8-high`
- #24 `qwen3.8-max` — **1481** (±5) → `qwen3.8-max`
- #25 `mimo-v2.6-pro` — **1480** (±9) → `mimo-v2.6-pro`
- #26 `glm-5.3-max` — **1479** (±6) → `glm-5.3-max`
- #27 `gemini-3.5-flash-high` — **1477** (±4) → `gemini-3.5-flash`, `gemini-3.5-flash-high`
- #28 `gpt-5.5` — **1477** (±4) → `gpt-5.5`
- #29 `gemini-3.5-flash-medium` — **1476** (±4) → `gemini-3.5-flash-medium`
- #30 `gpt-6-astra-max` — **1476** (±7) → `gpt-6-astra-max`
- #31 `gpt-5.2-chat-latest-20260210` — **1476** (±4) → `gpt-5.2-chat-latest-20260210`
- #32 `glm-5.2-max` — **1475** (±4) → `glm-5.2`, `glm-5.2-max`, `glm5.2`
- #33 `gpt-5.4-high` — **1475** (±4) → `gpt-5.4-high`
- #34 `grok-4.20-beta1` — **1475** (±5) → `grok-4.20-beta1`
- #35 `qwen3.7-max-preview` — **1475** (±10) → `qwen3.7-max-preview`
- #36 `claude-opus-4-8` — **1475** (±4) → `claude-opus-4-8`, `claude-opus-4.8`
- #37 `claude-opus-4-5-20251101-high-32k` — **1474** (±4) → `claude-opus-4-5-20251101-high-32k`
- #38 `glm-5.3-flash` — **1474** (±5) → `glm-5.3-flash`
- #39 `gpt-5.5-instant` — **1473** (±5) → `gpt-5.5-instant`
- #40 `deepseek-v4.1-flash-max` — **1473** (±7) → `deepseek-v4.1-flash-max`
- #41 `gemini-3-flash` — **1473** (±4) → `gemini-3-flash`
- #42 `claude-sonnet-4-6` — **1472** (±4) → `claude-sonnet-4-6`, `claude-sonnet-4.6`
- #43 `grok-4.20-beta-0309-reasoning` — **1472** (±4) → `grok-4.20-beta-0309-reasoning`
- #44 `grok-4.20-multi-agent-beta-0309` — **1471** (±4) → `grok-4.20-multi-agent-beta-0309`
- #45 `claude-opus-4-5-20251101` — **1470** (±3) → `claude-opus-4-5`, `claude-opus-4-5-20251101`, `claude-opus-4.5`
- #46 `ernie-5.1` — **1468** (±5) → `ernie-5.1`
- #47 `mimo-v2.5-pro` — **1467** (±4) → `mimo-v2.5-pro`
- #48 `grok-4.5` — **1466** (±4) → `grok-4.5`
- #49 `grok-4.1-thinking` — **1465** (±3) → `grok-4.1-thinking`
- #50 `gpt-5.6-terra-xhigh` — **1465** (±4) → `gpt-5.6-terra-xhigh`
- #51 `gpt-5.4` — **1465** (±4) → `gpt-5.4`
- #52 `qwen3.5-max-preview` — **1465** (±5) → `qwen3.5-max-preview`
- #53 `glm-5.1` — **1465** (±4) → `glm-5.1`
- #54 `deepseek-v4-pro-high-20260813` — **1464** (±7) → `deepseek-v4-pro-high-20260813`
- #55 `claude-sonnet-5-high` — **1462** (±4) → `claude-sonnet-5`, `claude-sonnet-5-high`
- #56 `kimi-k2.6` — **1461** (±4) → `kimi-k2-6`, `kimi-k2.6`
- #57 `qwen3.6-max-preview` — **1460** (±8) → `qwen3.6-max-preview`
- #58 `grok-4.1` — **1459** (±3) → `grok-4.1`
- #59 `gemini-3-flash (thinking-minimal)` — **1458** (±3) → `gemini-3-flash-thinking-minimal`
- #60 `deepseek-v4-pro` — **1458** (±4) → `deepseek-v4-pro`, `deepseek-v4-pro:free`
- #61 `glm-5` — **1458** (±4) → `glm-5`, `glm5`
- #62 `hy3` — **1457** (±7) → `hy3`
- #63 `claude-sonnet-4-5-20250929-high-32k` — **1457** (±3) → `claude-sonnet-4-5-20250929-high-32k`
- #64 `dola-seed-2.0-pro` — **1457** (±3) → `dola-seed-2.0-pro`
- #65 `qwen3.7-plus` — **1456** (±4) → `qwen3.7-plus`
- #66 `gpt-6-sol-max` — **1456** (±7) → `gpt-6-sol-max`
- #67 `gpt-5.1-high` — **1456** (±4) → `gpt-5.1-high`
- #68 `claude-sonnet-4-5-20250929` — **1455** (±3) → `claude-sonnet-4-5`, `claude-sonnet-4-5-20250929`, `claude-sonnet-4.5`
- #69 `deepseek-v4-pro-high-preview` — **1455** (±4) → `deepseek-v4-pro-high-preview`
- #70 `gemini-3.5-flash-lite` — **1454** (±4) → `gemini-3.5-flash-lite`
- #71 `mimo-v2.6-flash` — **1454** (±8) → `mimo-v2.6-flash`
- #72 `gpt-5.6-luna-xhigh` — **1453** (±4) → `gpt-5.6-luna-xhigh`
- #73 `grok-4.6-high` — **1453** (±5) → `grok-4-6`, `grok-4-6-high`, `grok-4.6`, `grok-4.6-high`
- #74 `gemma-4-31b` — **1453** (±7) → `gemma-4-31b`, `gemma-4-31b-it`, `gemma-4-31b-it-heretic`, `gemma-4-31b-it:free`, `gemma4:31b`
- #75 `kimi-k2.5-thinking` — **1450** (±3) → `kimi-k2.5-thinking`
- #76 `gpt-5.3-chat-latest` — **1450** (±4) → `gpt-5.3-chat-latest`
- #77 `claude-opus-4-1-20250805-thinking-16k` — **1450** (±3) → `claude-opus-4-1-20250805-thinking-16k`
- #78 `ernie-5.0-preview-1203` — **1449** (±7) → `ernie-5.0-preview-1203`
- #79 `claude-opus-4-1-20250805` — **1448** (±3) → `claude-opus-4-1`, `claude-opus-4-1-20250805`, `claude-opus-4.1`
- #80 `mimo-v2-pro` — **1448** (±5) → `mimo-v2-pro`
- #81 `gpt-5.4-mini-high` — **1447** (±4) → `gpt-5.4-mini-high`
- #82 `ernie-5.0-0110` — **1447** (±4) → `ernie-5.0`, `ernie-5.0-0110`
- #83 `gemini-2.5-pro` — **1446** (±2) → `gemini-2.5-pro`
- #84 `gpt-4.5-preview-2025-02-27` — **1445** (±6) → `gpt-4.5-preview-2025-02-27`
- #85 `gpt-6-luna-max` — **1444** (±7) → `gpt-6-luna-max`
- #86 `qwen3.6-plus` — **1443** (±4) → `qwen3.6-plus`, `qwen3.6-plus-free`
- #87 `chatgpt-4o-latest-20250326` — **1443** (±3) → `chatgpt-4o-latest-20250326`
- #88 `grok-4.7-xhigh` — **1442** (±8) → `grok-4.7-xhigh`
- #89 `inkling` — **1442** (±5) → `inkling`
- #90 `qwen3.5-397b-a17b` — **1442** (±3) → `qwen3.5-397b-a17b`, `qwen3.5:397b`
- #91 `glm-4.7` — **1442** (±6) → `glm-4.7`, `glm4.7`, `zai-glm-4.7`
- #92 `grok-4.3` — **1441** (±4) → `grok-4.3`
- #93 `minimax-m3` — **1440** (±4) → `minimax-m3`
- #94 `qwen3.8-27b` — **1439** (±5) → `qwen3.8-27b`
- #95 `gpt-5.1` — **1438** (±4) → `gpt-5.1`
- #96 `deepseek-v4-flash-high-preview` — **1438** (±4) → `deepseek-v4-flash-high-preview`
- #97 `gemma-4-26b-a4b` — **1437** (±7) → `gemma-4-26b-a4b`, `gemma-4-26b-a4b-it`, `gemma-4-26b-a4b-it:free`
- #98 `gpt-5.2-high` — **1437** (±4) → `gpt-5.2-high`
- #99 `longcat-flash-chat-2602-exp` — **1437** (±5) → `longcat-flash-chat-2602-exp`
- #100 `deepseek-v4-flash` — **1436** (±4) → `deepseek-v4-flash`, `deepseek-v4-flash-free`, `deepseek-v4-flash:free`
- #101 `gpt-5.2` — **1436** (±3) → `gpt-5.2`
- #102 `gpt-5-high` — **1434** (±5) → `gpt-5-high`
- #103 `qwen3-max-preview` — **1434** (±5) → `qwen3-max-preview`
- #104 `glm-5v-turbo` — **1434** (±6) → `glm-5v-turbo`
- #105 `mimo-v2.5` — **1434** (±4) → `mimo-v2.5`, `mimo-v2.5-free`
- #106 `gemini-3.1-flash-lite-preview` — **1433** (±4) → `gemini-3.1-flash-lite-preview`
- #107 `o3-2025-04-16` — **1432** (±4) → `o3`, `o3-2025-04-16`
- #108 `mimo-v2-omni` — **1431** (±6) → `mimo-v2-omni`
- #109 `kimi-k2.5-instant` — **1431** (±7) → `kimi-k2.5-instant`
- #110 `grok-4-1-fast-reasoning` — **1430** (±3) → `grok-4-1-fast-reasoning`
- #111 `kimi-k2-thinking-turbo` — **1430** (±3) → `kimi-k2-thinking-turbo`
- #112 `mistral-medium-3.5` — **1427** (±6) → `mistral-medium-2604`, `mistral-medium-3-5`, `mistral-medium-3.5`, `mistral-medium-c21211-r0-75`
- #113 `gpt-5-chat` — **1427** (±4) → `gpt-5-chat`
- #114 `amazon-nova-experimental-chat-26-02-10` — **1427** (±10) → `amazon-nova-experimental-chat-26-02-10`
- #115 `nvidia-nemotron-3-ultra-550b-a55b-nvfp4` — **1426** (±7) → `nemotron-3-ultra`, `nemotron-3-ultra-550b-a55b`, `nemotron-3-ultra-550b-a55b:free`, `nemotron-3-ultra-free`, `nvidia-nemotron-3-ultra-550b-a55b-nvfp4`
- #116 `claude-opus-4-20250514-thinking-16k` — **1426** (±4) → `claude-opus-4-20250514-thinking-16k`
- #117 `muse-glimmer` — **1425** (±10) → `muse-glimmer`
- #118 `deepseek-v3.2-exp-thinking` — **1425** (±7) → `deepseek-v3.2-exp-thinking`
- #119 `deepseek-v3.2` — **1425** (±3) → `deepseek-v3-2`, `deepseek-v3.2`
- #120 `glm-4.6` — **1424** (±4) → `glm-4.6`
- #121 `qwen3-max-2025-09-23` — **1423** (±7) → `qwen3-max`, `qwen3-max-2025-09-23`
- #122 `deepseek-v3.2-thinking` — **1423** (±4) → `deepseek-v3.2-thinking`
- #123 `qwen3-235b-a22b-instruct-2507` — **1422** (±3) → `qwen-3-235b-a22b-instruct-2507`, `qwen3-235b-a22b-2507`, `qwen3-235b-a22b-instruct-2507`
- #124 `deepseek-v3.2-exp` — **1422** (±6) → `deepseek-v3.2-exp`
- #125 `deepseek-r1-0528` — **1422** (±6) → `deepseek-r1-0528`
- #126 `grok-4-fast-chat` — **1419** (±8) → `grok-4-fast-chat`
- #127 `ernie-5.0-preview-1022` — **1419** (±9) → `ernie-5.0-preview-1022`
- #128 `kimi-k2-0905-preview` — **1419** (±7) → `kimi-k2-0905-preview`
- #129 `kimi-k2-0711-preview` — **1418** (±5) → `kimi-k2-0711-preview`
- #130 `deepseek-v3.1` — **1417** (±6) → `deepseek-v3-1`, `deepseek-v3.1`, `deepseek-v3.1:671b`
- #131 `deepseek-v3.1-terminus-thinking` — **1417** (±10) → `deepseek-v3.1-terminus-thinking`
- #132 `qwen3.5-122b-a10b` — **1416** (±4) → `qwen3.5-122b-a10b`
- #133 `deepseek-v3.1-thinking` — **1416** (±7) → `deepseek-v3.1-thinking`
- #134 `minimax-m2.7` — **1415** (±3) → `minimax-m2.7`
- #135 `gpt-4.1-2025-04-14` — **1415** (±4) → `gpt-4.1`, `gpt-4.1-2025-04-14`
- #136 `deepseek-v3.1-terminus` — **1415** (±10) → `deepseek-v3.1-terminus`
- #137 `claude-opus-4-20250514` — **1414** (±4) → `claude-opus-4`, `claude-opus-4-20250514`
- #138 `mistral-large-3` — **1414** (±3) → `mistral-large-2512`, `mistral-large-3`
- #139 `claude-haiku-4-5-20251001` — **1414** (±2) → `claude-haiku-4-5`, `claude-haiku-4-5-20251001`, `claude-haiku-4.5`
- #140 `qwen3-vl-235b-a22b-instruct` — **1413** (±7) → `qwen3-vl-235b-a22b-instruct`, `qwen3-vl:235b`, `qwen3-vl:235b-instruct`
- #141 `amazon-nova-experimental-chat-26-01-10` — **1413** (±10) → `amazon-nova-experimental-chat-26-01-10`
- #142 `grok-3-preview-02-24` — **1411** (±4) → `grok-3-preview-02-24`
- #143 `glm-4.5` — **1411** (±5) → `glm-4.5`
- #144 `grok-4-0709` — **1411** (±4) → `grok-4`, `grok-4-0709`
- #145 `gemini-2.5-flash` — **1409** (±2) → `gemini-2.5-flash`
- #146 `hunyuan-hy3-preview` — **1409** (±7) → `hunyuan-hy3-preview`, `hy3-preview`, `hy3-preview:free`
- #147 `qwen3.5-27b` — **1409** (±4) → `qwen3.5-27b`
- #148 `mistral-medium-2508` — **1408** (±3) → `mistral-medium-2508`, `mistral-medium-3.1`
- #149 `Inkling Small` — **1405** (±5) → `inkling-small`
- #150 `grok-4-fast-reasoning` — **1405** (±5) → `grok-4-fast-reasoning`
- #151 `gemini-2.5-flash-preview-09-2025` — **1403** (±4) → `gemini-2.5-flash-preview-09-2025`
- #152 `qwen3-235b-a22b-no-thinking` — **1403** (±5) → `qwen3-235b-a22b-no-thinking`
- #153 `o1-2024-12-17` — **1402** (±4) → `o1`, `o1-2024-12-17`
- #154 `claude-sonnet-4-20250514-thinking-32k` — **1402** (±4) → `claude-sonnet-4-20250514-thinking-32k`
- #155 `longcat-flash-chat` — **1401** (±6) → `longcat-flash-chat`
- #156 `gpt-5.4-nano-high` — **1401** (±4) → `gpt-5.4-nano-high`
- #157 `qwen3-235b-a22b-thinking-2507` — **1400** (±7) → `qwen3-235b-a22b-thinking-2507`
- #158 `qwen3-next-80b-a3b-instruct` — **1399** (±5) → `qwen3-next-80b-a3b-instruct`, `qwen3-next-80b-a3b-instruct:free`, `qwen3-next:80b`
- #159 `deepseek-r1` — **1398** (±5) → `deepseek-r1`, `deepseek-reasoner`
- #160 `qwen3.5-flash` — **1396** (±4) → `qwen3.5-flash`, `qwen3.5-flash-02-23`
- #161 `deepseek-v3-0324` — **1396** (±4) → `deepseek-chat-v3-0324`, `deepseek-v3-0324`
- #162 `hunyuan-vision-1.5-thinking` — **1395** (±12) → `hunyuan-vision-1.5-thinking`
- #163 `qwen3-vl-235b-a22b-thinking` — **1395** (±7) → `qwen3-vl-235b-a22b-thinking`
- #164 `amazon-nova-experimental-chat-12-10` — **1394** (±9) → `amazon-nova-experimental-chat-12-10`
- #165 `qwen3.5-35b-a3b` — **1394** (±4) → `qwen3.5-35b-a3b`
- #166 `step-3.5-flash` — **1393** (±4) → `step-3.5-flash`
- #167 `mimo-v2-flash (non-thinking)` — **1392** (±3) → —
- #168 `minimax-m2.5` — **1391** (±4) → `minimax-m2.5`, `minimax-m2.5-free`, `minimax-m2.5:free`
- #169 `o4-mini-2025-04-16` — **1391** (±4) → `o4-mini`, `o4-mini-2025-04-16`
- #170 `claude-sonnet-4-20250514` — **1391** (±4) → `claude-sonnet-4`, `claude-sonnet-4-20250514`
- #171 `gpt-5-mini-high` — **1390** (±5) → `gpt-5-mini-high`
- #172 `o1-preview` — **1389** (±5) → `o1-preview`, `o1-preview-2024-09-12`
- #173 `claude-3-7-sonnet-20250219-thinking-32k` — **1388** (±4) → `claude-3-7-sonnet-20250219-thinking-32k`
- #174 `mistral-medium-2505` — **1387** (±5) → `mistral-medium-2505`, `mistral-medium-3`, `mistral-medium-3-instruct`
- #175 `qwen3-coder-480b-a35b-instruct` — **1387** (±5) → `qwen3-coder-480b-a35b`, `qwen3-coder-480b-a35b-instruct`, `qwen3-coder:480b`
- #176 `hunyuan-t1-20250711` — **1387** (±9) → `hunyuan-t1-20250711`
- #177 `mimo-v2-flash (thinking)` — **1387** (±6) → `mimo-v2-flash-thinking`
- #178 `solar-pro4` — **1386** (±6) → `solar-pro-4`, `solar-pro4`
- #179 `minimax-m2.1-preview` — **1385** (±5) → `minimax-m2.1-preview`
- #180 `qwen3-30b-a3b-instruct-2507` — **1383** (±5) → `qwen3-30b-a3b-instruct-2507`
- #181 `gpt-4.1-mini-2025-04-14` — **1383** (±4) → `gpt-4.1-mini`, `gpt-4.1-mini-2025-04-14`
- #182 `hunyuan-turbos-20250416` — **1383** (±7) → `hunyuan-turbos-20250416`
- #183 `gemini-2.5-flash-lite-preview-09-2025-no-thinking` — **1379** (±3) → `gemini-2.5-flash-lite-preview-09-2025`, `gemini-2.5-flash-lite-preview-09-2025-no-thinking`
- #184 `glm-4.6v` — **1379** (±11) → `glm-4.6v`
- #185 `trinity-large-preview` — **1379** (±4) → `trinity-large-preview`, `trinity-large-preview:free`
- #186 `qwen3-235b-a22b` — **1375** (±5) → `qwen3-235b`, `qwen3-235b-a22b`
- #187 `gemini-2.5-flash-lite-preview-06-17-thinking` — **1375** (±5) → `gemini-2.5-flash-lite-preview-06-17`, `gemini-2.5-flash-lite-preview-06-17-thinking`
- #188 `claude-3-5-sonnet-20241022` — **1374** (±3) → `claude-3-5-sonnet`, `claude-3-5-sonnet-20241022`, `claude-3.5-sonnet`
- #189 `qwen2.5-max` — **1374** (±4) → `qwen2.5-max`
- #190 `glm-4.5-air` — **1373** (±4) → `glm-4.5-air`, `glm-4.5-air:free`
- #191 `claude-3-7-sonnet-20250219` — **1372** (±4) → `claude-3-7-sonnet`, `claude-3-7-sonnet-20250219`, `claude-3.7-sonnet`
- #192 `qwen3-next-80b-a3b-thinking` — **1369** (±6) → `qwen3-next-80b-a3b-thinking`
- #193 `trinity-large-thinking` — **1367** (±5) → `trinity-large-thinking`, `trinity-large-thinking:free`
- #194 `gemma-3-27b-it` — **1365** (±4) → `gemma-3-27b`, `gemma-3-27b-it`, `gemma-3-27b-it:free`, `gemma3:27b`
- #195 `glm-4.7-flash` — **1365** (±6) → `glm-4.7-flash`
- #196 `amazon-nova-experimental-chat-11-10` — **1364** (±4) → `amazon-nova-experimental-chat-11-10`
- #197 `minimax-m1` — **1364** (±4) → `minimax-m1`
- #198 `o3-mini-high` — **1364** (±5) → `o3-mini-high`
- #199 `grok-3-mini-high` — **1363** (±5) → `grok-3-mini-high`
- #200 `nvidia-nemotron-3-super-120b-a12b` — **1361** (±7) → `nemotron-3-super`, `nemotron-3-super-120b-a12b`, `nemotron-3-super-120b-a12b:free`, `nvidia-nemotron-3-super-120b-a12b`
- #201 `gemini-2.0-flash-001` — **1360** (±4) → `gemini-2.0-flash`, `gemini-2.0-flash-001`
- #202 `deepseek-v3` — **1358** (±5) → `deepseek-v3`
- #203 `grok-3-mini-beta` — **1358** (±5) → `grok-3-mini-beta`
- #204 `mistral-small-2506` — **1356** (±5) → `mistral-small-2506`, `mistral-small-3.2`
- #205 `intellect-3` — **1356** (±8) → `intellect-3`
- #206 `command-a-03-2025` — **1354** (±3) → `command-a`, `command-a-03-2025`
- #207 `gemini-2.0-flash-lite-preview-02-05` — **1354** (±4) → `gemini-2.0-flash-lite-preview-02-05`
- #208 `glm-4.5v` — **1352** (±8) → `glm-4.5v`
- #209 `gpt-oss-120b` — **1352** (±4) → `gpt-oss-120b`, `gpt-oss-120b:free`, `gpt-oss:120b`
- #210 `gemini-1.5-pro-002` — **1351** (±3) → `gemini-1.5-pro-002`
- #211 `step-3` — **1349** (±8) → `step-3`
- #212 `hunyuan-turbos-20250226` — **1349** (±12) → `hunyuan-turbos-20250226`
- #213 `nvidia-nemotron-3.5-lightning-30b-a3b-nvfp4` — **1349** (±6) → `nemotron-3.5-lightning`, `nemotron-3.5-lightning:free`, `nvidia-nemotron-3.5-lightning-30b-a3b-nvfp4`
- #214 `amazon-nova-experimental-chat-10-20` — **1348** (±6) → `amazon-nova-experimental-chat-10-20`
- #215 `o3-mini` — **1348** (±4) → `o3-mini`, `o3-mini-2025-01-31`
- #216 `llama-3.1-nemotron-ultra-253b-v1` — **1348** (±12) → `llama-3.1-nemotron-ultra-253b-v1`
- #217 `amazon-nova-experimental-chat-10-09` — **1347** (±11) → `amazon-nova-experimental-chat-10-09`
- #218 `qwen3-32b` — **1347** (±9) → `qwen3-32b`
- #219 `gpt-4o-2024-05-13` — **1346** (±3) → `gpt-4o-2024-05-13`
- #220 `qwen-plus-0125` — **1346** (±8) → `qwen-plus`, `qwen-plus-0125`
- #221 `ling-flash-2.0` — **1344** (±7) → `ling-flash-2.0`
- #222 `minimax-m2` — **1343** (±8) → `minimax-m2`
- #223 `mercury-2` — **1343** (±11) → `mercury-2`
- #224 `claude-3-5-sonnet-20240620` — **1343** (±3) → `claude-3-5-sonnet-20240620`
- #225 `nvidia-llama-3.3-nemotron-super-49b-v1.5` — **1343** (±10) → `llama-3.3-nemotron-super-49b-v1.5`, `nvidia-llama-3.3-nemotron-super-49b-v1.5`
- #226 `glm-4-plus-0111` — **1343** (±8) → `glm-4-plus-0111`
- #227 `gemma-3-12b-it` — **1342** (±10) → `gemma-3-12b`, `gemma-3-12b-it`, `gemma-3-12b-it:free`, `gemma3:12b`
- #228 `hunyuan-turbo-0110` — **1341** (±12) → `hunyuan-turbo-0110`
- #229 `granite-4.2-30b` — **1340** (±10) → `granite-4.2-30b`
- #230 `gpt-5-nano-high` — **1337** (±7) → `gpt-5-nano-high`
- #231 `o1-mini` — **1337** (±4) → `o1-mini`, `o1-mini-2024-09-12`
- #232 `gemini-advanced-0514` — **1336** (±5) → `gemini-advanced-0514`
- #233 `qwq-32b` — **1336** (±4) → `qwen-qwq-32b`, `qwq-32b`
- #234 `grok-2-2024-08-13` — **1336** (±4) → `grok-2`, `grok-2-2024-08-13`
- #235 `gpt-4o-2024-08-06` — **1336** (±4) → `gpt-4o`, `gpt-4o-2024-08-06`
- #236 `llama-3.1-405b-instruct-bf16` — **1335** (±4) → —
- #237 `nova-2-lite` — **1335** (±6) → `nova-2-lite`, `nova-2-lite-v1`
- #238 `step-2-16k-exp-202412` — **1334** (±9) → `step-2-16k-exp-202412`
- #239 `llama-3.1-405b-instruct-fp8` — **1333** (±4) → —
- #240 `olmo-3.1-32b-instruct` — **1329** (±6) → `olmo-3.1-32b-instruct`
- #241 `yi-lightning` — **1328** (±5) → `yi-lightning`
- #242 `llama-3.3-nemotron-49b-super-v1` — **1328** (±12) → —
- #243 `llama-4-maverick-17b-128e-instruct` — **1327** (±4) → `llama-4-maverick`, `llama-4-maverick-17b-128e`, `llama-4-maverick-17b-128e-instruct`
- #244 `qwen3-30b-a3b` — **1327** (±5) → `qwen3-30b-a3b`
- #245 `hunyuan-large-2025-02-10` — **1326** (±10) → —
- #246 `claude-3-5-haiku-20241022` — **1325** (±3) → `claude-3-5-haiku`, `claude-3-5-haiku-20241022`, `claude-3.5-haiku`
- #247 `gpt-4-turbo-2024-04-09` — **1324** (±4) → `gpt-4-turbo`, `gpt-4-turbo-2024-04-09`
- #248 `gemini-1.5-pro-001` — **1324** (±4) → `gemini-1.5-pro`, `gemini-1.5-pro-001`
- #249 `deepseek-v2.5-1210` — **1323** (±8) → `deepseek-v2.5-1210`
- #250 `ring-flash-2.0` — **1323** (±7) → `ring-flash-2.0`
- #251 `gpt-4.1-nano-2025-04-14` — **1322** (±8) → `gpt-4.1-nano`, `gpt-4.1-nano-2025-04-14`
- #252 `claude-3-opus-20240229` — **1322** (±3) → `claude-3-opus`, `claude-3-opus-20240229`
- #253 `molmo-2-8b` — **1322** (±21) → `molmo-2-8b`
- #254 `llama-4-scout-17b-16e-instruct` — **1322** (±5) → `llama-4-scout`, `llama-4-scout-17b-16e`, `llama-4-scout-17b-16e-instruct`
- #255 `step-1o-turbo-202506` — **1320** (±7) → —
- #256 `glm-4-plus` — **1319** (±5) → `glm-4-plus`
- #257 `qwen-max-0919` — **1318** (±6) → —
- #258 `llama-3.3-70b-instruct` — **1318** (±4) → `llama-3.3-70b`, `llama-3.3-70b-instruct`, `llama-3.3-70b-instruct-turbo`, `llama-3.3-70b-instruct:free`, `llama-3.3-70b-versatile`, `llama-v3p3-70b-instruct`, `llama3.3`, `llama3.3:70b`
- #259 `gpt-4o-mini-2024-07-18` — **1318** (±4) → `gpt-4o-mini`, `gpt-4o-mini-2024-07-18`
- #260 `gpt-oss-20b` — **1318** (±6) → `gpt-oss-20b`, `gpt-oss-20b:free`, `gpt-oss:20b`
- #261 `gemma-3n-e4b-it` — **1317** (±5) → `gemma-3n-e4b-it`, `gemma-3n-e4b-it:free`
- #262 `qwen2.5-plus-1127` — **1315** (±6) → —
- #263 `mistral-large-2407` — **1314** (±4) → `mistral-large-2-instruct`, `mistral-large-2407`
- #264 `athene-v2-chat` — **1314** (±5) → —
- #265 `nvidia-nemotron-3-nano-30b-a3b-bf16` — **1314** (±5) → —
- #266 `gpt-4-0125-preview` — **1313** (±4) → `gpt-4-0125-preview`, `gpt-4-turbo-preview`
- #267 `gpt-4-1106-preview` — **1313** (±4) → `gpt-4-1106-preview`
- #268 `hunyuan-standard-2025-02-10` — **1311** (±10) → —
- #269 `gemini-1.5-flash-002` — **1309** (±4) → `gemini-1.5-flash-002`
- #270 `grok-2-mini-2024-08-13` — **1308** (±4) → —
- #271 `deepseek-v2.5` — **1307** (±5) → `deepseek-v2.5`
- #272 `athene-70b-0725` — **1307** (±6) → —
- #273 `olmo-3-32b-think` — **1306** (±8) → `olmo-3-32b-think`
- #274 `mercury` — **1306** (±14) → `mercury`
- #275 `mistral-large-2411` — **1306** (±4) → `mistral-large-2411`
- #276 `magistral-medium-2506` — **1305** (±6) → `magistral-medium-2506`
- #277 `granite-4.1-8b` — **1305** (±10) → `granite-4.1-8b`
- #278 `gemma-3-4b-it` — **1303** (±9) → `gemma-3-4b`, `gemma-3-4b-it`, `gemma-3-4b-it:free`, `gemma3:4b`
- #279 `mistral-small-3.1-24b-instruct-2503` — **1303** (±5) → `mistral-small-3.1-24b-instruct`, `mistral-small-3.1-24b-instruct-2503`
- #280 `qwen2.5-72b-instruct` — **1303** (±4) → `qwen-2.5-72b-instruct`, `qwen2.5-72b`, `qwen2.5-72b-instruct`, `qwen2.5-72b-instruct-turbo`, `qwen2.5:72b`
- #281 `llama-3.1-nemotron-70b-instruct` — **1299** (±8) → `llama-3.1-nemotron-70b-instruct`
- #282 `hunyuan-large-vision` — **1294** (±9) → —
- #283 `llama-3.1-70b-instruct` — **1293** (±4) → `llama-3.1-70b`, `llama-3.1-70b-instruct`, `llama-3.1-70b-instruct-turbo`, `llama-3.1-70b-versatile`, `llama-v3p1-70b-instruct`, `llama3.1:70b`
- #284 `amazon-nova-pro-v1.0` — **1290** (±5) → —
- #285 `gemma-2-27b-it` — **1289** (±3) → `gemma-2-27b-it`, `gemma2:27b`
- #286 `jamba-1.5-large` — **1289** (±7) → `jamba-1.5-large`, `jamba-1.5-large-instruct`
- #287 `granite-4.2-3b` — **1289** (±12) → `granite-4.2-3b`
- #288 `reka-core-20240904` — **1288** (±7) → `reka-core-20240904`
- #289 `gpt-4-0314` — **1288** (±5) → `gpt-4`, `gpt-4-0314`
- #290 `granite-4.2-8b` — **1288** (±11) → `granite-4.2-8b`
- #291 `ibm-granite-h-small` — **1287** (±8) → —
- #292 `llama-3.1-nemotron-51b-instruct` — **1287** (±10) → `llama-3.1-nemotron-51b-instruct`
- #293 `gemini-1.5-flash-001` — **1287** (±5) → `gemini-1.5-flash`, `gemini-1.5-flash-001`
- #294 `olmo-3.1-32b-think` — **1286** (±7) → —
- #295 `llama-3.1-tulu-3-70b` — **1286** (±10) → —
- #296 `claude-3-sonnet-20240229` — **1281** (±4) → `claude-3-sonnet`, `claude-3-sonnet-20240229`
- #297 `gemma-2-9b-it-simpo` — **1280** (±7) → —
- #298 `nemotron-4-340b-instruct` — **1277** (±5) → `nemotron-4-340b-instruct`
- #299 `llama-3-70b-instruct` — **1276** (±4) → `llama-3-70b-instruct`, `llama3-70b-instruct`
- #300 `command-r-plus-08-2024` — **1276** (±7) → `command-r-plus-08-2024`
- #301 `gpt-4-0613` — **1276** (±4) → `gpt-4-0613`
- #302 `mistral-small-24b-instruct-2501` — **1274** (±6) → `mistral-small-24b-instruct`, `mistral-small-24b-instruct-2501`
- #303 `glm-4-0520` — **1273** (±7) → —
- #304 `reka-flash-20240904` — **1272** (±7) → —
- #305 `qwen2.5-coder-32b-instruct` — **1271** (±8) → `qwen-2.5-coder-32b`, `qwen-2.5-coder-32b-instruct`, `qwen2.5-coder-32b`, `qwen2.5-coder-32b-instruct`
- #306 `c4ai-aya-expanse-32b` — **1267** (±5) → `c4ai-aya-expanse-32b`
- #307 `gemma-2-9b-it` — **1267** (±4) → `gemma-2-9b-it`, `gemma2-9b-it`, `gemma2:9b`
- #308 `deepseek-coder-v2` — **1265** (±6) → `deepseek-coder-v2`
- #309 `qwen2-72b-instruct` — **1262** (±5) → `qwen2-72b-instruct`
- #310 `command-r-plus` — **1262** (±4) → `command-r-plus`
- #311 `claude-3-haiku-20240307` — **1262** (±4) → `claude-3-haiku`, `claude-3-haiku-20240307`
- #312 `amazon-nova-lite-v1.0` — **1260** (±5) → —
- #313 `gemini-1.5-flash-8b-001` — **1259** (±4) → `gemini-1.5-flash-8b`, `gemini-1.5-flash-8b-001`
- #314 `phi-4` — **1256** (±5) → `phi-4`, `phi-4-14b`, `phi4`, `phi4:14b`
- #315 `olmo-2-0325-32b-instruct` — **1252** (±11) → `olmo-2-0325-32b-instruct`
- #316 `command-r-08-2024` — **1250** (±7) → `command-r-08-2024`
- #317 `mistral-large-2402` — **1242** (±5) → —
- #318 `amazon-nova-micro-v1.0` — **1241** (±5) → —
- #319 `jamba-1.5-mini` — **1240** (±7) → `jamba-1.5-mini`, `jamba-1.5-mini-instruct`
- #320 `ministral-8b-2410` — **1238** (±9) → `ministral-8b-2410`
- #321 `gemini-pro-dev-api` — **1237** (±7) → —
- #322 `qwen1.5-110b-chat` — **1234** (±6) → —
- #323 `reka-flash-21b-20240226-online` — **1233** (±7) → —
- #324 `qwen1.5-72b-chat` — **1233** (±5) → —
- #325 `hunyuan-standard-256k` — **1233** (±12) → —
- #326 `mixtral-8x22b-instruct-v0.1` — **1230** (±5) → `mixtral-8x22b`, `mixtral-8x22b-instruct`, `mixtral-8x22b-instruct-v0.1`, `mixtral-8x22b-v0.1`
- #327 `command-r` — **1227** (±5) → `command-r`
- #328 `reka-flash-21b-20240226` — **1227** (±6) → —
- #329 `gpt-3.5-turbo-0125` — **1226** (±5) → `gpt-3.5-turbo-0125`
- #330 `llama-3-8b-instruct` — **1224** (±4) → `llama-3-8b-instruct`, `llama3-8b-instruct`
- #331 `gemini-pro` — **1224** (±12) → —
- #332 `c4ai-aya-expanse-8b` — **1223** (±7) → `c4ai-aya-expanse-8b`
- #333 `mistral-medium` — **1223** (±5) → `mistral-medium`
- #334 `llama-3.1-tulu-3-8b` — **1220** (±11) → —
- #335 `zephyr-orpo-141b-A35b-v0.1` — **1213** (±11) → —
- #336 `yi-1.5-34b-chat` — **1213** (±5) → —
- #337 `llama-3.1-8b-instruct` — **1211** (±4) → `llama-3.1-8b`, `llama-3.1-8b-instant`, `llama-3.1-8b-instruct`, `llama-3.1-8b-instruct-turbo`, `llama-v3p1-8b-instruct`, `llama3.1`, `llama3.1-8b`, `llama3.1:8b`
- #338 `granite-3.1-8b-instruct` — **1208** (±11) → `granite-3.1-8b-instruct`
- #339 `gpt-3.5-turbo-1106` — **1204** (±9) → `gpt-3.5-turbo-1106`
- #340 `qwen1.5-32b-chat` — **1204** (±6) → —
- #341 `gemma-2-2b-it` — **1200** (±4) → `gemma-2-2b-it`, `gemma2:2b`
- #342 `phi-3-medium-4k-instruct` — **1198** (±5) → `phi-3-medium-4k-instruct`
- #343 `mixtral-8x7b-instruct-v0.1` — **1197** (±4) → `mixtral-8x7b`, `mixtral-8x7b-instruct`, `mixtral-8x7b-instruct-v0.1`
- #344 `dbrx-instruct-preview` — **1195** (±6) → —
- #345 `qwen1.5-14b-chat` — **1191** (±7) → —
- #346 `internlm2_5-20b-chat` — **1191** (±7) → —
- #347 `deepseek-llm-67b-chat` — **1185** (±11) → —
- #348 `wizardlm-70b` — **1185** (±9) → —
- #349 `yi-34b-chat` — **1184** (±7) → `yi-34b-chat`
- #350 `granite-3.0-8b-instruct` — **1183** (±9) → `granite-3.0-8b-instruct`
- #351 `openchat-3.5` — **1183** (±10) → —
- #352 `openchat-3.5-0106` — **1183** (±8) → —
- #353 `gemma-1.1-7b-it` — **1183** (±6) → —
- #354 `snowflake-arctic-instruct` — **1180** (±6) → —
- #355 `granite-3.1-2b-instruct` — **1179** (±11) → `granite-3.1-2b-instruct`
- #356 `tulu-2-dpo-70b` — **1178** (±10) → —
- #357 `openhermes-2.5-mistral-7b` — **1176** (±10) → —
- #358 `vicuna-33b` — **1173** (±6) → —
- #359 `phi-3-small-8k-instruct` — **1171** (±6) → `phi-3-small-8k-instruct`
- #360 `starling-lm-7b-beta` — **1171** (±7) → —
- #361 `llama-2-70b-chat` — **1171** (±5) → —
- #362 `starling-lm-7b-alpha` — **1167** (±8) → —
- #363 `llama-3.2-3b-instruct` — **1167** (±8) → `llama-3.2-3b`, `llama-3.2-3b-instruct`, `llama-3.2-3b-instruct-turbo`, `llama-3.2-3b-instruct:free`
- #364 `nous-hermes-2-mixtral-8x7b-dpo` — **1164** (±12) → `nous-hermes-2-mixtral-8x7b-dpo`
- #365 `granite-3.0-2b-instruct` — **1157** (±8) → `granite-3.0-2b-instruct`
- #366 `llama2-70b-steerlm-chat` — **1155** (±13) → —
- #367 `qwq-32b-preview` — **1154** (±11) → `qwq-32b-preview`
- #368 `solar-10.7b-instruct-v1.0` — **1152** (±13) → —
- #369 `dolphin-2.2.1-mistral-7b` — **1152** (±15) → —
- #370 `mpt-30b-chat` — **1151** (±12) → —
- #371 `wizardlm-13b` — **1149** (±9) → —
- #372 `mistral-7b-instruct-v0.2` — **1149** (±7) → `mistral-7b-instruct-v0.2`
- #373 `falcon-180b-chat` — **1148** (±17) → —
- #374 `qwen1.5-7b-chat` — **1144** (±10) → —
- #375 `phi-3-mini-4k-instruct-june-2024` — **1143** (±6) → —
- #376 `vicuna-13b` — **1141** (±7) → —
- #377 `llama-2-13b-chat` — **1141** (±7) → —
- #378 `qwen-14b-chat` — **1139** (±11) → —
- #379 `palm-2` — **1139** (±9) → —
- #380 `gemma-7b-it` — **1138** (±9) → —
- #381 `codellama-34b-instruct` — **1137** (±9) → —
- #382 `zephyr-7b-beta` — **1131** (±9) → —
- #383 `phi-3-mini-128k-instruct` — **1130** (±7) → `phi-3-mini-128k-instruct`
- #384 `phi-3-mini-4k-instruct` — **1128** (±6) → `phi-3-mini-4k-instruct`
- #385 `guanaco-33b` — **1127** (±12) → —
- #386 `zephyr-7b-alpha` — **1127** (±16) → —
- #387 `stripedhyena-nous-7b` — **1122** (±11) → —
- #388 `codellama-70b-instruct` — **1119** (±18) → —
- #389 `gemma-1.1-2b-it` — **1117** (±8) → —
- #390 `vicuna-7b` — **1115** (±9) → —
- #391 `smollm2-1.7b-instruct` — **1115** (±14) → —
- #392 `llama-3.2-1b-instruct` — **1111** (±8) → `llama-3.2-1b`, `llama-3.2-1b-instruct`
- #393 `mistral-7b-instruct` — **1110** (±9) → `mistral-7b-instruct`, `mistral-7b-instruct-v0.1`
- #394 `llama-2-7b-chat` — **1108** (±7) → —
- #395 `gemma-2b-it` — **1094** (±11) → —
- #396 `qwen1.5-4b-chat` — **1091** (±9) → —
- #397 `olmo-7b-instruct` — **1074** (±11) → —
- #398 `koala-13b` — **1071** (±10) → —
- #399 `alpaca-13b` — **1070** (±11) → —
- #400 `gpt4all-13b-snoozy` — **1068** (±15) → —
- #401 `mpt-7b-chat` — **1063** (±12) → —
- #402 `chatglm3-6b` — **1056** (±12) → `chatglm3-6b`
- #403 `RWKV-4-Raven-14B` — **1042** (±11) → —
- #404 `chatglm2-6b` — **1025** (±14) → —
- #405 `oasst-pythia-12b` — **1023** (±11) → —
- #406 `chatglm-6b` — **996** (±13) → —
- #407 `fastchat-t5-3b` — **992** (±12) → —
- #408 `dolly-v2-12b` — **982** (±13) → —
- #409 `llama-13b` — **975** (±16) → —
- #410 `stablelm-tuned-alpha-7b` — **953** (±13) → —

# Estimated

573 catalog ids keep a **family / sibling / alias estimate** (or an older attested value that this refresh did not rematch). They are not re-derived on this pass.

Do not treat those numbers as Arena facts. See [matching-policy.md](matching-policy.md).

# Notes

- Runtime lookup strips a leading `provider/` prefix and lowercases the id; catalog keys are already lowercase.
- New board models added to the runtime catalog on this refresh: `gemini-4-argon-high`, `claude-opus-5.5-high` / `claude-opus-5-5-high`, `muse-spark-1.3-max`, `mimo-v2.6-pro`, `mimo-v2.6-flash`, `gpt-6-astra-max`, `gpt-6-sol-max`, `gpt-6-luna-max`, `deepseek-v4.1-flash-max`, `grok-4.7-xhigh`. Their shipping base ids (`gemini-4-argon`, `claude-opus-5-5` / `claude-opus-5.5`, `muse-spark-1.3`, `gpt-6-astra` / `-sol` / `-luna`, `deepseek-v4.1-flash`, `grok-4.7`) are **estimated** from the tier row, not attested.
- Arena renamed `claude-fable-5` → `claude-fable-5-high` (same announcement link, vote history carried). The `-high` score was **not** copied onto `claude-fable-5` / `claude-fable-latest`; those keep their last attested value (1507) as an estimate in the auto-fill block.
- Historical unmatched board rows (older chat models Kai does not ship metadata for) are listed above with `→ —` and were **not** added to `baseEntries`.
- Claude Arena rows that renamed `thinking` → `high` were **not** copied onto existing `-thinking` catalog ids (quality-tier rule).
- `muse-spark-1.2 (xHigh)` updates `muse-spark-1.2-xhigh` only; the base id stays estimated.
- GPT-5.6 shipping ids (`gpt-5.6`, `gpt-5.6-sol`, `gpt-5.6-terra`, `gpt-5.6-luna`) stay estimated from their `-xhigh` siblings; the board still lists only the xHigh tiers.

[^arena-text]: Arena text leaderboard
