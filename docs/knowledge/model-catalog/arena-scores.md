---
type: Catalog
title: Arena text Elo scores
description: Attested LMArena / Arena.ai text-leaderboard Elo scores mapped onto Kai catalog ids.
tags: [models, arena, elo, lmarena]
status: stable
resource: https://arena.ai/leaderboard/text
stale_after: 2026-10-16
generated: { by: process:update-model-catalog, at: 2026-10-02T19:46:00Z }
verified: { by: process:desktopTest-ModelCatalog, at: 2026-10-02T19:48:03Z }
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
| Fetched | 2026-10-02 (page date Oct 2, 2026) |
| Models on board | 413 |
| Votes (page) | 8,626,731 |
| Catalog ids receiving an attested score | 514 |
| Catalog ids still estimated | 576 |

# Attested (text arena)

Arena name → Elo → catalog ids that carry this score after the refresh.

- #1 `gemini-4-argon-high` — **1525** (±9) → `gemini-4-argon-high`
- #2 `claude-opus-4-6-high` — **1505** (±3) → `claude-opus-4-6-high`, `claude-opus-4.6-high`
- #3 `claude-fable-5-high` — **1504** (±4) → `claude-fable-5-high`
- #4 `claude-opus-5.5-high` — **1504** (±9) → `claude-opus-5-5-high`, `claude-opus-5.5-high`
- #5 `claude-opus-4-7-high` — **1501** (±4) → `claude-opus-4-7-high`, `claude-opus-4.7-high`
- #6 `claude-fable-5.1-max` — **1501** (±6) → `claude-fable-5-1-max`, `claude-fable-5.1-max`
- #7 `claude-opus-4-6` — **1497** (±3) → `claude-opus-4-6`, `claude-opus-4.6`
- #8 `gemini-3.8-flash-high` — **1495** (±5) → `gemini-3.8-flash-high`
- #9 `muse-spark-1.3-max` — **1494** (±6) → `muse-spark-1.3-max`
- #10 `claude-opus-4-7` — **1494** (±4) → `claude-opus-4-7`, `claude-opus-4.7`
- #11 `muse-spark-1.2 (xHigh)` — **1494** (±9) → `muse-spark-1.2-xhigh`
- #12 `muse-spark-1.1` — **1491** (±4) → `muse-spark-1.1`
- #13 `claude-opus-5-high` — **1490** (±4) → `claude-opus-5`, `claude-opus-5-high`
- #14 `claude-opus-5-max` — **1489** (±5) → `claude-opus-5-max`
- #15 `muse-spark` — **1489** (±6) → `muse-spark`
- #16 `kimi-k3-max` — **1488** (±5) → `kimi-k3-max`
- #17 `gemini-3.7-flash-high` — **1488** (±5) → `gemini-3.7-flash-high`
- #18 `gemini-3.1-pro-preview` — **1487** (±3) → `gemini-3.1-pro-preview`
- #19 `gemini-3-pro` — **1485** (±4) → `gemini-3-pro`
- #20 `gpt-5.6-sol-xhigh` — **1484** (±4) → `gpt-5.6-sol-xhigh`
- #21 `gpt-6.1-sol-max` — **1483** (±11) → `gpt-6.1-sol-max`
- #22 `gemini-3.6-flash-high` — **1483** (±4) → `gemini-3.6-flash-high`
- #23 `qwen3.8-max` — **1482** (±5) → `qwen3.8-max`
- #24 `claude-opus-4-8-high` — **1482** (±4) → `claude-opus-4-8-high`, `claude-opus-4.8-high`
- #25 `gpt-5.5-high` — **1481** (±4) → `gpt-5.5-high`
- #26 `mimo-v2.6-pro` — **1480** (±9) → `mimo-v2.6-pro`
- #27 `glm-5.3-max` — **1478** (±6) → `glm-5.3-max`
- #28 `gemini-3.5-flash-high` — **1477** (±4) → `gemini-3.5-flash`, `gemini-3.5-flash-high`
- #29 `gpt-6-astra-max` — **1477** (±7) → `gpt-6-astra-max`
- #30 `gpt-5.5` — **1477** (±4) → `gpt-5.5`
- #31 `gemini-3.5-flash-medium` — **1476** (±4) → `gemini-3.5-flash-medium`
- #32 `glm-5.2-max` — **1476** (±4) → `glm-5.2`, `glm-5.2-max`, `glm5.2`
- #33 `gpt-5.2-chat-latest-20260210` — **1476** (±4) → `gpt-5.2-chat-latest-20260210`
- #34 `gpt-5.4-high` — **1475** (±4) → `gpt-5.4-high`
- #35 `qwen3.7-max-preview` — **1475** (±10) → `qwen3.7-max-preview`
- #36 `grok-4.20-beta1` — **1475** (±5) → `grok-4.20-beta1`
- #37 `claude-opus-4-8` — **1475** (±4) → `claude-opus-4-8`, `claude-opus-4.8`
- #38 `deepseek-v4.1-flash-max` — **1474** (±7) → `deepseek-v4.1-flash-max`
- #39 `claude-opus-4-5-20251101-high-32k` — **1474** (±4) → `claude-opus-4-5-20251101-high-32k`
- #40 `gpt-5.5-instant` — **1473** (±5) → `gpt-5.5-instant`
- #41 `glm-5.3-flash` — **1473** (±5) → `glm-5.3-flash`
- #42 `gemini-3-flash` — **1473** (±4) → `gemini-3-flash`
- #43 `claude-sonnet-4-6` — **1472** (±4) → `claude-sonnet-4-6`, `claude-sonnet-4.6`
- #44 `grok-4.20-beta-0309-reasoning` — **1472** (±4) → `grok-4.20-beta-0309-reasoning`
- #45 `claude-sonnet-5.5-xhigh` — **1471** (±10) → `claude-sonnet-5-5-xhigh`, `claude-sonnet-5.5-xhigh`
- #46 `grok-4.20-multi-agent-beta-0309` — **1471** (±4) → `grok-4.20-multi-agent-beta-0309`
- #47 `claude-opus-4-5-20251101` — **1470** (±3) → `claude-opus-4-5`, `claude-opus-4-5-20251101`, `claude-opus-4.5`
- #48 `ernie-5.1` — **1468** (±5) → `ernie-5.1`
- #49 `mimo-v2.5-pro` — **1468** (±4) → `mimo-v2.5-pro`
- #50 `grok-4.5` — **1466** (±4) → `grok-4.5`
- #51 `gpt-5.6-terra-xhigh` — **1465** (±4) → `gpt-5.6-terra-xhigh`
- #52 `grok-4.1-thinking` — **1465** (±3) → `grok-4.1-thinking`
- #53 `gpt-5.4` — **1465** (±4) → `gpt-5.4`
- #54 `qwen3.5-max-preview` — **1465** (±5) → `qwen3.5-max-preview`
- #55 `glm-5.1` — **1465** (±4) → `glm-5.1`
- #56 `deepseek-v4-pro-high-20260813` — **1464** (±7) → `deepseek-v4-pro-high-20260813`
- #57 `claude-sonnet-5-high` — **1462** (±4) → `claude-sonnet-5`, `claude-sonnet-5-high`
- #58 `kimi-k2.6` — **1461** (±4) → `kimi-k2-6`, `kimi-k2.6`
- #59 `qwen3.6-max-preview` — **1460** (±8) → `qwen3.6-max-preview`
- #60 `grok-4.1` — **1459** (±3) → `grok-4.1`
- #61 `gemini-3-flash (thinking-minimal)` — **1458** (±3) → `gemini-3-flash-thinking-minimal`
- #62 `deepseek-v4-pro` — **1458** (±4) → `deepseek-v4-pro`, `deepseek-v4-pro:free`
- #63 `glm-5` — **1458** (±4) → `glm-5`, `glm5`
- #64 `gpt-6-sol-max` — **1457** (±7) → `gpt-6-sol-max`
- #65 `claude-sonnet-4-5-20250929-high-32k` — **1457** (±3) → `claude-sonnet-4-5-20250929-high-32k`
- #66 `dola-seed-2.0-pro` — **1456** (±3) → `dola-seed-2.0-pro`
- #67 `hy3` — **1456** (±7) → `hy3`
- #68 `qwen3.7-plus` — **1456** (±4) → `qwen3.7-plus`
- #69 `gpt-5.1-high` — **1456** (±4) → `gpt-5.1-high`
- #70 `claude-sonnet-4-5-20250929` — **1455** (±3) → `claude-sonnet-4-5`, `claude-sonnet-4-5-20250929`, `claude-sonnet-4.5`
- #71 `gemini-3.5-flash-lite` — **1455** (±4) → `gemini-3.5-flash-lite`
- #72 `deepseek-v4-pro-high-preview` — **1455** (±4) → `deepseek-v4-pro-high-preview`
- #73 `grok-4.6-high` — **1454** (±5) → `grok-4-6`, `grok-4-6-high`, `grok-4.6`, `grok-4.6-high`
- #74 `Step 5 Preview` — **1454** (±11) → —
- #75 `gpt-5.6-luna-xhigh` — **1453** (±4) → `gpt-5.6-luna-xhigh`
- #76 `gemma-4-31b` — **1453** (±7) → `gemma-4-31b`, `gemma-4-31b-it`, `gemma-4-31b-it-heretic`, `gemma-4-31b-it:free`, `gemma4:31b`
- #77 `mimo-v2.6-flash` — **1452** (±8) → `mimo-v2.6-flash`
- #78 `kimi-k2.5-thinking` — **1450** (±3) → `kimi-k2.5-thinking`
- #79 `gpt-5.3-chat-latest` — **1450** (±4) → `gpt-5.3-chat-latest`
- #80 `claude-opus-4-1-20250805-thinking-16k` — **1450** (±3) → `claude-opus-4-1-20250805-thinking-16k`
- #81 `ernie-5.0-preview-1203` — **1449** (±7) → `ernie-5.0-preview-1203`
- #82 `claude-opus-4-1-20250805` — **1448** (±3) → `claude-opus-4-1`, `claude-opus-4-1-20250805`, `claude-opus-4.1`
- #83 `mimo-v2-pro` — **1448** (±5) → `mimo-v2-pro`
- #84 `gpt-5.4-mini-high` — **1447** (±4) → `gpt-5.4-mini-high`
- #85 `ernie-5.0-0110` — **1446** (±4) → `ernie-5.0`, `ernie-5.0-0110`
- #86 `gemini-2.5-pro` — **1446** (±2) → `gemini-2.5-pro`
- #87 `gpt-4.5-preview-2025-02-27` — **1445** (±6) → `gpt-4.5-preview-2025-02-27`
- #88 `qwen3.6-plus` — **1443** (±4) → `qwen3.6-plus`, `qwen3.6-plus-free`
- #89 `chatgpt-4o-latest-20250326` — **1443** (±3) → `chatgpt-4o-latest-20250326`
- #90 `gpt-6-luna-max` — **1443** (±7) → `gpt-6-luna-max`
- #91 `grok-4.7-xhigh` — **1442** (±8) → `grok-4.7-xhigh`
- #92 `qwen3.5-397b-a17b` — **1442** (±3) → `qwen3.5-397b-a17b`, `qwen3.5:397b`
- #93 `glm-4.7` — **1441** (±6) → `glm-4.7`, `glm4.7`, `zai-glm-4.7`
- #94 `grok-4.3` — **1441** (±4) → `grok-4.3`
- #95 `inkling` — **1441** (±5) → `inkling`
- #96 `minimax-m3` — **1440** (±4) → `minimax-m3`
- #97 `gpt-5.1` — **1439** (±4) → `gpt-5.1`
- #98 `deepseek-v4-flash-high-preview` — **1438** (±4) → `deepseek-v4-flash-high-preview`
- #99 `qwen3.8-27b` — **1438** (±5) → `qwen3.8-27b`
- #100 `gemma-4-26b-a4b` — **1437** (±7) → `gemma-4-26b-a4b`, `gemma-4-26b-a4b-it`, `gemma-4-26b-a4b-it:free`
- #101 `gpt-5.2-high` — **1437** (±4) → `gpt-5.2-high`
- #102 `longcat-flash-chat-2602-exp` — **1437** (±5) → `longcat-flash-chat-2602-exp`
- #103 `deepseek-v4-flash` — **1436** (±4) → `deepseek-v4-flash`, `deepseek-v4-flash-free`, `deepseek-v4-flash:free`
- #104 `gpt-5.2` — **1436** (±3) → `gpt-5.2`
- #105 `gpt-5-high` — **1435** (±5) → `gpt-5-high`
- #106 `qwen3-max-preview` — **1434** (±5) → `qwen3-max-preview`
- #107 `mimo-v2.5` — **1434** (±4) → `mimo-v2.5`, `mimo-v2.5-free`
- #108 `glm-5v-turbo` — **1433** (±6) → `glm-5v-turbo`
- #109 `gemini-3.1-flash-lite-preview` — **1433** (±4) → `gemini-3.1-flash-lite-preview`
- #110 `o3-2025-04-16` — **1432** (±4) → `o3`, `o3-2025-04-16`
- #111 `mimo-v2-omni` — **1431** (±6) → `mimo-v2-omni`
- #112 `kimi-k2.5-instant` — **1431** (±7) → `kimi-k2.5-instant`
- #113 `grok-4-1-fast-reasoning` — **1430** (±3) → `grok-4-1-fast-reasoning`
- #114 `kimi-k2-thinking-turbo` — **1430** (±3) → `kimi-k2-thinking-turbo`
- #115 `mistral-medium-3.5` — **1427** (±6) → `mistral-medium-2604`, `mistral-medium-3-5`, `mistral-medium-3.5`, `mistral-medium-c21211-r0-75`
- #116 `gpt-5-chat` — **1427** (±4) → `gpt-5-chat`
- #117 `amazon-nova-experimental-chat-26-02-10` — **1427** (±10) → `amazon-nova-experimental-chat-26-02-10`
- #118 `nvidia-nemotron-3-ultra-550b-a55b-nvfp4` — **1426** (±7) → `nemotron-3-ultra`, `nemotron-3-ultra-550b-a55b`, `nemotron-3-ultra-550b-a55b:free`, `nemotron-3-ultra-free`, `nvidia-nemotron-3-ultra-550b-a55b-nvfp4`
- #119 `claude-opus-4-20250514-thinking-16k` — **1426** (±4) → `claude-opus-4-20250514-thinking-16k`
- #120 `muse-glimmer` — **1425** (±10) → `muse-glimmer`
- #121 `deepseek-v3.2-exp-thinking` — **1425** (±7) → `deepseek-v3.2-exp-thinking`
- #122 `deepseek-v3.2` — **1425** (±3) → `deepseek-v3-2`, `deepseek-v3.2`
- #123 `glm-4.6` — **1424** (±4) → `glm-4.6`
- #124 `qwen3-max-2025-09-23` — **1423** (±7) → `qwen3-max`, `qwen3-max-2025-09-23`
- #125 `deepseek-v3.2-thinking` — **1423** (±4) → `deepseek-v3.2-thinking`
- #126 `qwen3-235b-a22b-instruct-2507` — **1422** (±3) → `qwen-3-235b-a22b-instruct-2507`, `qwen3-235b-a22b-2507`, `qwen3-235b-a22b-instruct-2507`
- #127 `deepseek-v3.2-exp` — **1422** (±6) → `deepseek-v3.2-exp`
- #128 `deepseek-r1-0528` — **1422** (±6) → `deepseek-r1-0528`
- #129 `grok-4-fast-chat` — **1419** (±8) → `grok-4-fast-chat`
- #130 `ernie-5.0-preview-1022` — **1419** (±9) → `ernie-5.0-preview-1022`
- #131 `kimi-k2-0905-preview` — **1419** (±7) → `kimi-k2-0905-preview`
- #132 `kimi-k2-0711-preview` — **1418** (±5) → `kimi-k2-0711-preview`
- #133 `deepseek-v3.1-terminus-thinking` — **1417** (±10) → `deepseek-v3.1-terminus-thinking`
- #134 `deepseek-v3.1` — **1417** (±6) → `deepseek-v3-1`, `deepseek-v3.1`, `deepseek-v3.1:671b`
- #135 `qwen3.5-122b-a10b` — **1416** (±4) → `qwen3.5-122b-a10b`
- #136 `deepseek-v3.1-thinking` — **1416** (±7) → `deepseek-v3.1-thinking`
- #137 `minimax-m2.7` — **1415** (±3) → `minimax-m2.7`
- #138 `deepseek-v3.1-terminus` — **1415** (±10) → `deepseek-v3.1-terminus`
- #139 `gpt-4.1-2025-04-14` — **1415** (±4) → `gpt-4.1`, `gpt-4.1-2025-04-14`
- #140 `claude-opus-4-20250514` — **1414** (±4) → `claude-opus-4`, `claude-opus-4-20250514`
- #141 `mistral-large-3` — **1414** (±3) → `mistral-large-2512`, `mistral-large-3`
- #142 `claude-haiku-4-5-20251001` — **1414** (±2) → `claude-haiku-4-5`, `claude-haiku-4-5-20251001`, `claude-haiku-4.5`
- #143 `qwen3-vl-235b-a22b-instruct` — **1413** (±7) → `qwen3-vl-235b-a22b-instruct`, `qwen3-vl:235b`, `qwen3-vl:235b-instruct`
- #144 `amazon-nova-experimental-chat-26-01-10` — **1413** (±10) → `amazon-nova-experimental-chat-26-01-10`
- #145 `grok-3-preview-02-24` — **1411** (±4) → `grok-3-preview-02-24`
- #146 `glm-4.5` — **1411** (±5) → `glm-4.5`
- #147 `grok-4-0709` — **1411** (±4) → `grok-4`, `grok-4-0709`
- #148 `gemini-2.5-flash` — **1409** (±2) → `gemini-2.5-flash`
- #149 `hunyuan-hy3-preview` — **1409** (±7) → `hunyuan-hy3-preview`, `hy3-preview`, `hy3-preview:free`
- #150 `qwen3.5-27b` — **1409** (±4) → `qwen3.5-27b`
- #151 `mistral-medium-2508` — **1408** (±3) → `mistral-medium-2508`, `mistral-medium-3.1`
- #152 `Inkling Small` — **1405** (±5) → `inkling-small`
- #153 `grok-4-fast-reasoning` — **1405** (±5) → `grok-4-fast-reasoning`
- #154 `gemini-2.5-flash-preview-09-2025` — **1403** (±4) → `gemini-2.5-flash-preview-09-2025`
- #155 `qwen3-235b-a22b-no-thinking` — **1403** (±5) → `qwen3-235b-a22b-no-thinking`
- #156 `o1-2024-12-17` — **1402** (±4) → `o1`, `o1-2024-12-17`
- #157 `claude-sonnet-4-20250514-thinking-32k` — **1402** (±4) → `claude-sonnet-4-20250514-thinking-32k`
- #158 `gpt-5.4-nano-high` — **1401** (±4) → `gpt-5.4-nano-high`
- #159 `longcat-flash-chat` — **1401** (±6) → `longcat-flash-chat`
- #160 `qwen3-235b-a22b-thinking-2507` — **1400** (±7) → `qwen3-235b-a22b-thinking-2507`
- #161 `qwen3-next-80b-a3b-instruct` — **1399** (±5) → `qwen3-next-80b-a3b-instruct`, `qwen3-next-80b-a3b-instruct:free`, `qwen3-next:80b`
- #162 `deepseek-r1` — **1398** (±5) → `deepseek-r1`, `deepseek-reasoner`
- #163 `qwen3.5-flash` — **1396** (±4) → `qwen3.5-flash`, `qwen3.5-flash-02-23`
- #164 `deepseek-v3-0324` — **1396** (±4) → `deepseek-chat-v3-0324`, `deepseek-v3-0324`
- #165 `qwen3-vl-235b-a22b-thinking` — **1395** (±7) → `qwen3-vl-235b-a22b-thinking`
- #166 `hunyuan-vision-1.5-thinking` — **1395** (±12) → `hunyuan-vision-1.5-thinking`
- #167 `amazon-nova-experimental-chat-12-10` — **1394** (±9) → `amazon-nova-experimental-chat-12-10`
- #168 `qwen3.5-35b-a3b` — **1394** (±4) → `qwen3.5-35b-a3b`
- #169 `step-3.5-flash` — **1393** (±4) → `step-3.5-flash`
- #170 `mimo-v2-flash (non-thinking)` — **1392** (±3) → —
- #171 `minimax-m2.5` — **1391** (±4) → `minimax-m2.5`, `minimax-m2.5-free`, `minimax-m2.5:free`
- #172 `o4-mini-2025-04-16` — **1391** (±4) → `o4-mini`, `o4-mini-2025-04-16`
- #173 `claude-sonnet-4-20250514` — **1391** (±4) → `claude-sonnet-4`, `claude-sonnet-4-20250514`
- #174 `gpt-5-mini-high` — **1390** (±5) → `gpt-5-mini-high`
- #175 `o1-preview` — **1389** (±5) → `o1-preview`, `o1-preview-2024-09-12`
- #176 `claude-3-7-sonnet-20250219-thinking-32k` — **1388** (±4) → `claude-3-7-sonnet-20250219-thinking-32k`
- #177 `mistral-medium-2505` — **1388** (±5) → `mistral-medium-2505`, `mistral-medium-3`, `mistral-medium-3-instruct`
- #178 `qwen3-coder-480b-a35b-instruct` — **1387** (±5) → `qwen3-coder-480b-a35b`, `qwen3-coder-480b-a35b-instruct`, `qwen3-coder:480b`
- #179 `hunyuan-t1-20250711` — **1387** (±9) → `hunyuan-t1-20250711`
- #180 `mimo-v2-flash (thinking)` — **1387** (±6) → `mimo-v2-flash-thinking`
- #181 `solar-pro4` — **1386** (±6) → `solar-pro-4`, `solar-pro4`
- #182 `minimax-m2.1-preview` — **1385** (±5) → `minimax-m2.1-preview`
- #183 `gpt-4.1-mini-2025-04-14` — **1383** (±4) → `gpt-4.1-mini`, `gpt-4.1-mini-2025-04-14`
- #184 `hunyuan-turbos-20250416` — **1383** (±7) → `hunyuan-turbos-20250416`
- #185 `qwen3-30b-a3b-instruct-2507` — **1383** (±5) → `qwen3-30b-a3b-instruct-2507`
- #186 `gemini-2.5-flash-lite-preview-09-2025-no-thinking` — **1379** (±3) → `gemini-2.5-flash-lite-preview-09-2025`, `gemini-2.5-flash-lite-preview-09-2025-no-thinking`
- #187 `glm-4.6v` — **1379** (±11) → `glm-4.6v`
- #188 `trinity-large-preview` — **1379** (±4) → `trinity-large-preview`, `trinity-large-preview:free`
- #189 `qwen3-235b-a22b` — **1375** (±5) → `qwen3-235b`, `qwen3-235b-a22b`
- #190 `gemini-2.5-flash-lite-preview-06-17-thinking` — **1375** (±5) → `gemini-2.5-flash-lite-preview-06-17`, `gemini-2.5-flash-lite-preview-06-17-thinking`
- #191 `claude-3-5-sonnet-20241022` — **1374** (±3) → `claude-3-5-sonnet`, `claude-3-5-sonnet-20241022`, `claude-3.5-sonnet`
- #192 `qwen2.5-max` — **1374** (±4) → `qwen2.5-max`
- #193 `glm-4.5-air` — **1373** (±4) → `glm-4.5-air`, `glm-4.5-air:free`
- #194 `claude-3-7-sonnet-20250219` — **1373** (±4) → `claude-3-7-sonnet`, `claude-3-7-sonnet-20250219`, `claude-3.7-sonnet`
- #195 `qwen3-next-80b-a3b-thinking` — **1369** (±6) → `qwen3-next-80b-a3b-thinking`
- #196 `trinity-large-thinking` — **1367** (±5) → `trinity-large-thinking`, `trinity-large-thinking:free`
- #197 `gemma-3-27b-it` — **1365** (±4) → `gemma-3-27b`, `gemma-3-27b-it`, `gemma-3-27b-it:free`, `gemma3:27b`
- #198 `glm-4.7-flash` — **1365** (±6) → `glm-4.7-flash`
- #199 `amazon-nova-experimental-chat-11-10` — **1364** (±4) → `amazon-nova-experimental-chat-11-10`
- #200 `minimax-m1` — **1364** (±4) → `minimax-m1`
- #201 `o3-mini-high` — **1364** (±5) → `o3-mini-high`
- #202 `grok-3-mini-high` — **1363** (±5) → `grok-3-mini-high`
- #203 `nvidia-nemotron-3-super-120b-a12b` — **1360** (±7) → `nemotron-3-super`, `nemotron-3-super-120b-a12b`, `nemotron-3-super-120b-a12b:free`, `nvidia-nemotron-3-super-120b-a12b`
- #204 `gemini-2.0-flash-001` — **1360** (±4) → `gemini-2.0-flash`, `gemini-2.0-flash-001`
- #205 `deepseek-v3` — **1358** (±5) → `deepseek-v3`
- #206 `grok-3-mini-beta` — **1358** (±5) → `grok-3-mini-beta`
- #207 `mistral-small-2506` — **1356** (±5) → `mistral-small-2506`, `mistral-small-3.2`
- #208 `intellect-3` — **1356** (±8) → `intellect-3`
- #209 `command-a-03-2025` — **1354** (±3) → `command-a`, `command-a-03-2025`
- #210 `gemini-2.0-flash-lite-preview-02-05` — **1354** (±4) → `gemini-2.0-flash-lite-preview-02-05`
- #211 `glm-4.5v` — **1352** (±8) → `glm-4.5v`
- #212 `gpt-oss-120b` — **1352** (±4) → `gpt-oss-120b`, `gpt-oss-120b:free`, `gpt-oss:120b`
- #213 `gemini-1.5-pro-002` — **1351** (±3) → `gemini-1.5-pro-002`
- #214 `nvidia-nemotron-3.5-lightning-30b-a3b-nvfp4` — **1350** (±6) → `nemotron-3.5-lightning`, `nemotron-3.5-lightning:free`, `nvidia-nemotron-3.5-lightning-30b-a3b-nvfp4`
- #215 `step-3` — **1349** (±8) → `step-3`
- #216 `hunyuan-turbos-20250226` — **1349** (±12) → `hunyuan-turbos-20250226`
- #217 `amazon-nova-experimental-chat-10-20` — **1348** (±6) → `amazon-nova-experimental-chat-10-20`
- #218 `o3-mini` — **1348** (±4) → `o3-mini`, `o3-mini-2025-01-31`
- #219 `llama-3.1-nemotron-ultra-253b-v1` — **1348** (±12) → `llama-3.1-nemotron-ultra-253b-v1`
- #220 `amazon-nova-experimental-chat-10-09` — **1347** (±11) → `amazon-nova-experimental-chat-10-09`
- #221 `qwen3-32b` — **1347** (±9) → `qwen3-32b`
- #222 `gpt-4o-2024-05-13` — **1346** (±3) → `gpt-4o-2024-05-13`
- #223 `qwen-plus-0125` — **1346** (±8) → `qwen-plus`, `qwen-plus-0125`
- #224 `ling-flash-2.0` — **1344** (±7) → `ling-flash-2.0`
- #225 `minimax-m2` — **1343** (±8) → `minimax-m2`
- #226 `mercury-2` — **1343** (±11) → `mercury-2`
- #227 `claude-3-5-sonnet-20240620` — **1343** (±3) → `claude-3-5-sonnet-20240620`
- #228 `nvidia-llama-3.3-nemotron-super-49b-v1.5` — **1343** (±10) → `llama-3.3-nemotron-super-49b-v1.5`, `nvidia-llama-3.3-nemotron-super-49b-v1.5`
- #229 `glm-4-plus-0111` — **1343** (±8) → `glm-4-plus-0111`
- #230 `gemma-3-12b-it` — **1342** (±10) → `gemma-3-12b`, `gemma-3-12b-it`, `gemma-3-12b-it:free`, `gemma3:12b`
- #231 `hunyuan-turbo-0110` — **1341** (±12) → `hunyuan-turbo-0110`
- #232 `granite-4.2-30b` — **1340** (±10) → `granite-4.2-30b`
- #233 `gpt-5-nano-high` — **1338** (±7) → `gpt-5-nano-high`
- #234 `o1-mini` — **1337** (±4) → `o1-mini`, `o1-mini-2024-09-12`
- #235 `gemini-advanced-0514` — **1336** (±5) → `gemini-advanced-0514`
- #236 `qwq-32b` — **1336** (±4) → `qwen-qwq-32b`, `qwq-32b`
- #237 `grok-2-2024-08-13` — **1336** (±4) → `grok-2`, `grok-2-2024-08-13`
- #238 `gpt-4o-2024-08-06` — **1336** (±4) → `gpt-4o`, `gpt-4o-2024-08-06`
- #239 `llama-3.1-405b-instruct-bf16` — **1335** (±4) → —
- #240 `nova-2-lite` — **1335** (±6) → `nova-2-lite`, `nova-2-lite-v1`
- #241 `step-2-16k-exp-202412` — **1334** (±9) → `step-2-16k-exp-202412`
- #242 `llama-3.1-405b-instruct-fp8` — **1334** (±4) → —
- #243 `olmo-3.1-32b-instruct` — **1329** (±6) → `olmo-3.1-32b-instruct`
- #244 `yi-lightning` — **1328** (±5) → `yi-lightning`
- #245 `llama-3.3-nemotron-49b-super-v1` — **1328** (±12) → —
- #246 `llama-4-maverick-17b-128e-instruct` — **1327** (±4) → `llama-4-maverick`, `llama-4-maverick-17b-128e`, `llama-4-maverick-17b-128e-instruct`
- #247 `qwen3-30b-a3b` — **1326** (±5) → `qwen3-30b-a3b`
- #248 `hunyuan-large-2025-02-10` — **1326** (±10) → —
- #249 `claude-3-5-haiku-20241022` — **1325** (±3) → `claude-3-5-haiku`, `claude-3-5-haiku-20241022`, `claude-3.5-haiku`
- #250 `gpt-4-turbo-2024-04-09` — **1324** (±4) → `gpt-4-turbo`, `gpt-4-turbo-2024-04-09`
- #251 `gemini-1.5-pro-001` — **1324** (±4) → `gemini-1.5-pro`, `gemini-1.5-pro-001`
- #252 `deepseek-v2.5-1210` — **1323** (±8) → `deepseek-v2.5-1210`
- #253 `ring-flash-2.0` — **1323** (±7) → `ring-flash-2.0`
- #254 `gpt-4.1-nano-2025-04-14` — **1322** (±8) → `gpt-4.1-nano`, `gpt-4.1-nano-2025-04-14`
- #255 `claude-3-opus-20240229` — **1322** (±3) → `claude-3-opus`, `claude-3-opus-20240229`
- #256 `molmo-2-8b` — **1322** (±21) → `molmo-2-8b`
- #257 `llama-4-scout-17b-16e-instruct` — **1322** (±5) → `llama-4-scout`, `llama-4-scout-17b-16e`, `llama-4-scout-17b-16e-instruct`
- #258 `step-1o-turbo-202506` — **1320** (±7) → —
- #259 `glm-4-plus` — **1319** (±5) → `glm-4-plus`
- #260 `qwen-max-0919` — **1318** (±6) → —
- #261 `llama-3.3-70b-instruct` — **1318** (±4) → `llama-3.3-70b`, `llama-3.3-70b-instruct`, `llama-3.3-70b-instruct-turbo`, `llama-3.3-70b-instruct:free`, `llama-3.3-70b-versatile`, `llama-v3p3-70b-instruct`, `llama3.3`, `llama3.3:70b`
- #262 `gpt-4o-mini-2024-07-18` — **1318** (±4) → `gpt-4o-mini`, `gpt-4o-mini-2024-07-18`
- #263 `gpt-oss-20b` — **1318** (±6) → `gpt-oss-20b`, `gpt-oss-20b:free`, `gpt-oss:20b`
- #264 `gemma-3n-e4b-it` — **1317** (±5) → `gemma-3n-e4b-it`, `gemma-3n-e4b-it:free`
- #265 `qwen2.5-plus-1127` — **1315** (±6) → —
- #266 `mistral-large-2407` — **1314** (±4) → `mistral-large-2-instruct`, `mistral-large-2407`
- #267 `athene-v2-chat` — **1314** (±5) → —
- #268 `nvidia-nemotron-3-nano-30b-a3b-bf16` — **1314** (±5) → —
- #269 `gpt-4-0125-preview` — **1313** (±4) → `gpt-4-0125-preview`, `gpt-4-turbo-preview`
- #270 `gpt-4-1106-preview` — **1313** (±4) → `gpt-4-1106-preview`
- #271 `hunyuan-standard-2025-02-10` — **1311** (±10) → —
- #272 `gemini-1.5-flash-002` — **1309** (±4) → `gemini-1.5-flash-002`
- #273 `grok-2-mini-2024-08-13` — **1308** (±4) → —
- #274 `deepseek-v2.5` — **1307** (±5) → `deepseek-v2.5`
- #275 `athene-70b-0725` — **1307** (±6) → —
- #276 `olmo-3-32b-think` — **1306** (±8) → `olmo-3-32b-think`
- #277 `mistral-large-2411` — **1306** (±4) → `mistral-large-2411`
- #278 `mercury` — **1305** (±14) → `mercury`
- #279 `magistral-medium-2506` — **1305** (±6) → `magistral-medium-2506`
- #280 `granite-4.1-8b` — **1304** (±10) → `granite-4.1-8b`
- #281 `gemma-3-4b-it` — **1303** (±9) → `gemma-3-4b`, `gemma-3-4b-it`, `gemma-3-4b-it:free`, `gemma3:4b`
- #282 `mistral-small-3.1-24b-instruct-2503` — **1303** (±5) → `mistral-small-3.1-24b-instruct`, `mistral-small-3.1-24b-instruct-2503`
- #283 `qwen2.5-72b-instruct` — **1303** (±4) → `qwen-2.5-72b-instruct`, `qwen2.5-72b`, `qwen2.5-72b-instruct`, `qwen2.5-72b-instruct-turbo`, `qwen2.5:72b`
- #284 `llama-3.1-nemotron-70b-instruct` — **1299** (±8) → `llama-3.1-nemotron-70b-instruct`
- #285 `hunyuan-large-vision` — **1294** (±9) → —
- #286 `llama-3.1-70b-instruct` — **1293** (±4) → `llama-3.1-70b`, `llama-3.1-70b-instruct`, `llama-3.1-70b-instruct-turbo`, `llama-3.1-70b-versatile`, `llama-v3p1-70b-instruct`, `llama3.1:70b`
- #287 `amazon-nova-pro-v1.0` — **1290** (±5) → —
- #288 `granite-4.2-3b` — **1290** (±12) → `granite-4.2-3b`
- #289 `gemma-2-27b-it` — **1289** (±3) → `gemma-2-27b-it`, `gemma2:27b`
- #290 `jamba-1.5-large` — **1289** (±7) → `jamba-1.5-large`, `jamba-1.5-large-instruct`
- #291 `granite-4.2-8b` — **1288** (±11) → `granite-4.2-8b`
- #292 `reka-core-20240904` — **1288** (±7) → `reka-core-20240904`
- #293 `gpt-4-0314` — **1288** (±5) → `gpt-4`, `gpt-4-0314`
- #294 `ibm-granite-h-small` — **1287** (±8) → —
- #295 `llama-3.1-nemotron-51b-instruct` — **1287** (±10) → `llama-3.1-nemotron-51b-instruct`
- #296 `gemini-1.5-flash-001` — **1286** (±5) → `gemini-1.5-flash`, `gemini-1.5-flash-001`
- #297 `olmo-3.1-32b-think` — **1286** (±7) → —
- #298 `llama-3.1-tulu-3-70b` — **1286** (±10) → —
- #299 `claude-3-sonnet-20240229` — **1281** (±4) → `claude-3-sonnet`, `claude-3-sonnet-20240229`
- #300 `gemma-2-9b-it-simpo` — **1280** (±7) → —
- #301 `nemotron-4-340b-instruct` — **1277** (±5) → `nemotron-4-340b-instruct`
- #302 `llama-3-70b-instruct` — **1277** (±4) → `llama-3-70b-instruct`, `llama3-70b-instruct`
- #303 `command-r-plus-08-2024` — **1276** (±7) → `command-r-plus-08-2024`
- #304 `gpt-4-0613` — **1276** (±4) → `gpt-4-0613`
- #305 `mistral-small-24b-instruct-2501` — **1274** (±6) → `mistral-small-24b-instruct`, `mistral-small-24b-instruct-2501`
- #306 `glm-4-0520` — **1273** (±7) → —
- #307 `reka-flash-20240904` — **1272** (±7) → —
- #308 `qwen2.5-coder-32b-instruct` — **1271** (±8) → `qwen-2.5-coder-32b`, `qwen-2.5-coder-32b-instruct`, `qwen2.5-coder-32b`, `qwen2.5-coder-32b-instruct`
- #309 `c4ai-aya-expanse-32b` — **1267** (±5) → `c4ai-aya-expanse-32b`
- #310 `gemma-2-9b-it` — **1267** (±4) → `gemma-2-9b-it`, `gemma2-9b-it`, `gemma2:9b`
- #311 `deepseek-coder-v2` — **1265** (±6) → `deepseek-coder-v2`
- #312 `qwen2-72b-instruct` — **1262** (±5) → `qwen2-72b-instruct`
- #313 `command-r-plus` — **1262** (±4) → `command-r-plus`
- #314 `claude-3-haiku-20240307` — **1262** (±4) → `claude-3-haiku`, `claude-3-haiku-20240307`
- #315 `amazon-nova-lite-v1.0` — **1260** (±5) → —
- #316 `gemini-1.5-flash-8b-001` — **1259** (±4) → `gemini-1.5-flash-8b`, `gemini-1.5-flash-8b-001`
- #317 `phi-4` — **1256** (±5) → `phi-4`, `phi-4-14b`, `phi4`, `phi4:14b`
- #318 `olmo-2-0325-32b-instruct` — **1252** (±11) → `olmo-2-0325-32b-instruct`
- #319 `command-r-08-2024` — **1250** (±7) → `command-r-08-2024`
- #320 `mistral-large-2402` — **1242** (±5) → —
- #321 `amazon-nova-micro-v1.0` — **1241** (±5) → —
- #322 `jamba-1.5-mini` — **1240** (±7) → `jamba-1.5-mini`, `jamba-1.5-mini-instruct`
- #323 `ministral-8b-2410` — **1238** (±9) → `ministral-8b-2410`
- #324 `gemini-pro-dev-api` — **1237** (±7) → —
- #325 `qwen1.5-110b-chat` — **1234** (±6) → —
- #326 `reka-flash-21b-20240226-online` — **1233** (±7) → —
- #327 `qwen1.5-72b-chat` — **1233** (±5) → —
- #328 `hunyuan-standard-256k` — **1233** (±12) → —
- #329 `mixtral-8x22b-instruct-v0.1` — **1230** (±5) → `mixtral-8x22b`, `mixtral-8x22b-instruct`, `mixtral-8x22b-instruct-v0.1`, `mixtral-8x22b-v0.1`
- #330 `command-r` — **1227** (±5) → `command-r`
- #331 `reka-flash-21b-20240226` — **1227** (±6) → —
- #332 `gpt-3.5-turbo-0125` — **1226** (±5) → `gpt-3.5-turbo-0125`
- #333 `llama-3-8b-instruct` — **1224** (±4) → `llama-3-8b-instruct`, `llama3-8b-instruct`
- #334 `gemini-pro` — **1224** (±12) → —
- #335 `c4ai-aya-expanse-8b` — **1223** (±7) → `c4ai-aya-expanse-8b`
- #336 `mistral-medium` — **1223** (±5) → `mistral-medium`
- #337 `llama-3.1-tulu-3-8b` — **1220** (±11) → —
- #338 `zephyr-orpo-141b-A35b-v0.1` — **1213** (±11) → —
- #339 `yi-1.5-34b-chat` — **1213** (±5) → —
- #340 `llama-3.1-8b-instruct` — **1211** (±4) → `llama-3.1-8b`, `llama-3.1-8b-instant`, `llama-3.1-8b-instruct`, `llama-3.1-8b-instruct-turbo`, `llama-v3p1-8b-instruct`, `llama3.1`, `llama3.1-8b`, `llama3.1:8b`
- #341 `granite-3.1-8b-instruct` — **1208** (±11) → `granite-3.1-8b-instruct`
- #342 `gpt-3.5-turbo-1106` — **1204** (±9) → `gpt-3.5-turbo-1106`
- #343 `qwen1.5-32b-chat` — **1204** (±6) → —
- #344 `gemma-2-2b-it` — **1200** (±4) → `gemma-2-2b-it`, `gemma2:2b`
- #345 `phi-3-medium-4k-instruct` — **1198** (±5) → `phi-3-medium-4k-instruct`
- #346 `mixtral-8x7b-instruct-v0.1` — **1197** (±4) → `mixtral-8x7b`, `mixtral-8x7b-instruct`, `mixtral-8x7b-instruct-v0.1`
- #347 `dbrx-instruct-preview` — **1195** (±6) → —
- #348 `qwen1.5-14b-chat` — **1191** (±7) → —
- #349 `internlm2_5-20b-chat` — **1191** (±7) → —
- #350 `deepseek-llm-67b-chat` — **1185** (±11) → —
- #351 `wizardlm-70b` — **1185** (±9) → —
- #352 `yi-34b-chat` — **1184** (±7) → `yi-34b-chat`
- #353 `granite-3.0-8b-instruct` — **1183** (±9) → `granite-3.0-8b-instruct`
- #354 `openchat-3.5` — **1183** (±10) → —
- #355 `openchat-3.5-0106` — **1183** (±8) → —
- #356 `gemma-1.1-7b-it` — **1183** (±6) → —
- #357 `snowflake-arctic-instruct` — **1180** (±6) → —
- #358 `granite-3.1-2b-instruct` — **1179** (±11) → `granite-3.1-2b-instruct`
- #359 `tulu-2-dpo-70b` — **1178** (±10) → —
- #360 `openhermes-2.5-mistral-7b` — **1176** (±10) → —
- #361 `vicuna-33b` — **1173** (±6) → —
- #362 `phi-3-small-8k-instruct` — **1171** (±6) → `phi-3-small-8k-instruct`
- #363 `starling-lm-7b-beta` — **1171** (±7) → —
- #364 `llama-2-70b-chat` — **1171** (±5) → —
- #365 `starling-lm-7b-alpha` — **1167** (±8) → —
- #366 `llama-3.2-3b-instruct` — **1167** (±8) → `llama-3.2-3b`, `llama-3.2-3b-instruct`, `llama-3.2-3b-instruct-turbo`, `llama-3.2-3b-instruct:free`
- #367 `nous-hermes-2-mixtral-8x7b-dpo` — **1164** (±12) → `nous-hermes-2-mixtral-8x7b-dpo`
- #368 `granite-3.0-2b-instruct` — **1157** (±8) → `granite-3.0-2b-instruct`
- #369 `llama2-70b-steerlm-chat` — **1155** (±13) → —
- #370 `qwq-32b-preview` — **1154** (±11) → `qwq-32b-preview`
- #371 `solar-10.7b-instruct-v1.0` — **1152** (±13) → —
- #372 `dolphin-2.2.1-mistral-7b` — **1152** (±15) → —
- #373 `mpt-30b-chat` — **1151** (±12) → —
- #374 `wizardlm-13b` — **1149** (±9) → —
- #375 `mistral-7b-instruct-v0.2` — **1149** (±7) → `mistral-7b-instruct-v0.2`
- #376 `falcon-180b-chat` — **1148** (±17) → —
- #377 `qwen1.5-7b-chat` — **1144** (±10) → —
- #378 `phi-3-mini-4k-instruct-june-2024` — **1143** (±6) → —
- #379 `vicuna-13b` — **1142** (±7) → —
- #380 `llama-2-13b-chat` — **1141** (±7) → —
- #381 `qwen-14b-chat` — **1139** (±11) → —
- #382 `palm-2` — **1139** (±9) → —
- #383 `gemma-7b-it` — **1138** (±9) → —
- #384 `codellama-34b-instruct` — **1137** (±9) → —
- #385 `zephyr-7b-beta` — **1131** (±9) → —
- #386 `phi-3-mini-128k-instruct` — **1130** (±7) → `phi-3-mini-128k-instruct`
- #387 `phi-3-mini-4k-instruct` — **1128** (±6) → `phi-3-mini-4k-instruct`
- #388 `guanaco-33b` — **1127** (±12) → —
- #389 `zephyr-7b-alpha` — **1127** (±16) → —
- #390 `stripedhyena-nous-7b` — **1122** (±11) → —
- #391 `codellama-70b-instruct` — **1119** (±18) → —
- #392 `gemma-1.1-2b-it` — **1117** (±8) → —
- #393 `vicuna-7b` — **1115** (±9) → —
- #394 `smollm2-1.7b-instruct` — **1115** (±14) → —
- #395 `llama-3.2-1b-instruct` — **1111** (±8) → `llama-3.2-1b`, `llama-3.2-1b-instruct`
- #396 `mistral-7b-instruct` — **1110** (±9) → `mistral-7b-instruct`, `mistral-7b-instruct-v0.1`
- #397 `llama-2-7b-chat` — **1108** (±7) → —
- #398 `gemma-2b-it` — **1094** (±11) → —
- #399 `qwen1.5-4b-chat` — **1091** (±9) → —
- #400 `olmo-7b-instruct` — **1074** (±11) → —
- #401 `koala-13b` — **1071** (±10) → —
- #402 `alpaca-13b` — **1070** (±11) → —
- #403 `gpt4all-13b-snoozy` — **1068** (±15) → —
- #404 `mpt-7b-chat` — **1063** (±12) → —
- #405 `chatglm3-6b` — **1057** (±12) → `chatglm3-6b`
- #406 `RWKV-4-Raven-14B` — **1042** (±11) → —
- #407 `chatglm2-6b` — **1025** (±14) → —
- #408 `oasst-pythia-12b` — **1023** (±11) → —
- #409 `chatglm-6b` — **996** (±12) → —
- #410 `fastchat-t5-3b` — **992** (±12) → —
- #411 `dolly-v2-12b` — **982** (±13) → —
- #412 `llama-13b` — **975** (±16) → —
- #413 `stablelm-tuned-alpha-7b` — **953** (±13) → —

# Estimated

576 catalog ids keep a **family / sibling / alias estimate** (or an older attested value that this refresh did not rematch). They are not re-derived on this pass.

Do not treat those numbers as Arena facts. See [matching-policy.md](matching-policy.md).

# Notes

- Runtime lookup strips a leading `provider/` prefix and lowercases the id; catalog keys are already lowercase.
- New board models added to the runtime catalog on this refresh: `gpt-6.1-sol-max`, `claude-sonnet-5.5-xhigh` / `claude-sonnet-5-5-xhigh`. Their shipping base ids (`gpt-6.1-sol`, `claude-sonnet-5.5` / `claude-sonnet-5-5`) are **estimated** from the tier row, not attested. `gpt-6.1-sol*` context window uses the GPT-6 family default (1,050,000) because neither the board nor the announcement lists one.
- `Step 5 Preview` (StepFun, board key `step-5-preview-agent`) has no API id, context window or pricing on the board and was **not** added to `baseEntries`.
- Arena renamed `claude-fable-5` → `claude-fable-5-high` (same announcement link, vote history carried). The `-high` score was **not** copied onto `claude-fable-5` / `claude-fable-latest`; those keep their last attested value (1507) as an estimate in the auto-fill block.
- Historical unmatched board rows (older chat models Kai does not ship metadata for) are listed above with `→ —` and were **not** added to `baseEntries`.
- Claude Arena rows that renamed `thinking` → `high` were **not** copied onto existing `-thinking` catalog ids (quality-tier rule).
- `muse-spark-1.2 (xHigh)` updates `muse-spark-1.2-xhigh` only; the base id stays estimated.
- GPT-5.6 shipping ids (`gpt-5.6`, `gpt-5.6-sol`, `gpt-5.6-terra`, `gpt-5.6-luna`) stay estimated from their `-xhigh` siblings; the board still lists only the xHigh tiers.

[^arena-text]: Arena text leaderboard
