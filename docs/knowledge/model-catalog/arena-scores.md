---
type: Catalog
title: Arena text Elo scores
description: Attested LMArena / Arena.ai text-leaderboard Elo scores mapped onto Kai catalog ids.
tags: [models, arena, elo, lmarena]
status: stable
resource: https://arena.ai/leaderboard/text
stale_after: 2026-09-12
generated: { by: process:update-model-catalog, at: 2026-08-29T08:47:12Z }
verified: { by: process:desktopTest-ModelCatalog, at: 2026-08-29T08:56:26Z }
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
| Fetched | 2026-08-29 (page date Aug 27, 2026) |
| Models on board | 395 |
| Votes (page) | 7,922,078 |
| Catalog ids receiving an attested score | 495 |
| Catalog ids still estimated | 555 |

# Attested (text arena)

Arena name → Elo → catalog ids that carry this score after the refresh.

- #1 `claude-fable-5` — **1507** (±5) → `claude-fable-5`, `claude-fable-latest`
- #2 `claude-opus-4-6-high` — **1505** (±4) → `claude-opus-4-6-high`, `claude-opus-4.6-high`
- #3 `claude-opus-4-7-high` — **1502** (±4) → `claude-opus-4-7-high`, `claude-opus-4.7-high`
- #4 `muse-spark-1.2 (xHigh)` — **1498** (±10) → `muse-spark-1.2-xhigh`
- #5 `claude-opus-4-6` — **1497** (±3) → `claude-opus-4-6`, `claude-opus-4.6`
- #6 `claude-opus-4-7` — **1494** (±4) → `claude-opus-4-7`, `claude-opus-4.7`
- #7 `claude-opus-5-high` — **1492** (±5) → `claude-opus-5`, `claude-opus-5-high`
- #8 `muse-spark-1.1` — **1490** (±5) → `muse-spark-1.1`
- #9 `gemini-3.7-flash-high` — **1490** (±8) → `gemini-3.7-flash-high`
- #10 `kimi-k3-max` — **1489** (±6) → `kimi-k3-max`
- #11 `muse-spark` — **1488** (±6) → `muse-spark`
- #12 `claude-opus-5-max` — **1488** (±6) → `claude-opus-5-max`
- #13 `gemini-3.1-pro-preview` — **1487** (±3) → `gemini-3.1-pro-preview`
- #14 `gemini-3-pro` — **1486** (±4) → `gemini-3-pro`
- #15 `glm-5.3-max` — **1484** (±8) → `glm-5.3-max`
- #16 `gpt-5.5-high` — **1482** (±4) → `gpt-5.5-high`
- #17 `gpt-5.6-sol-xhigh` — **1482** (±5) → `gpt-5.6-sol-xhigh`
- #18 `claude-opus-4-8-high` — **1481** (±4) → `claude-opus-4-8-high`, `claude-opus-4.8-high`
- #19 `gemini-3.6-flash-high` — **1481** (±5) → `gemini-3.6-flash-high`
- #20 `qwen3.8-max` — **1479** (±6) → `qwen3.8-max`
- #21 `gemini-3.5-flash-high` — **1479** (±4) → `gemini-3.5-flash`, `gemini-3.5-flash-high`
- #22 `gpt-5.5` — **1477** (±4) → `gpt-5.5`
- #23 `gpt-5.4-high` — **1477** (±4) → `gpt-5.4-high`
- #24 `gpt-5.2-chat-latest-20260210` — **1476** (±4) → `gpt-5.2-chat-latest-20260210`
- #25 `grok-4.20-beta1` — **1475** (±5) → `grok-4.20-beta1`
- #26 `gemini-3-flash` — **1474** (±4) → `gemini-3-flash`
- #27 `qwen3.7-max-preview` — **1474** (±10) → `qwen3.7-max-preview`
- #28 `gemini-3.5-flash-medium` — **1473** (±5) → `gemini-3.5-flash-medium`
- #29 `gpt-5.5-instant` — **1473** (±5) → `gpt-5.5-instant`
- #30 `claude-opus-4-5-20251101-high-32k` — **1473** (±4) → `claude-opus-4-5-20251101-high-32k`
- #31 `claude-opus-4-8` — **1473** (±4) → `claude-opus-4-8`, `claude-opus-4.8`
- #32 `claude-sonnet-4-6` — **1472** (±4) → `claude-sonnet-4-6`, `claude-sonnet-4.6`
- #33 `glm-5.2-max` — **1472** (±5) → `glm-5.2`, `glm-5.2-max`, `glm5.2`
- #34 `grok-4.20-beta-0309-reasoning` — **1472** (±4) → `grok-4.20-beta-0309-reasoning`
- #35 `grok-4.20-multi-agent-beta-0309` — **1470** (±4) → `grok-4.20-multi-agent-beta-0309`
- #36 `grok-4.5` — **1470** (±5) → `grok-4.5`
- #37 `claude-opus-4-5-20251101` — **1469** (±3) → `claude-opus-4-5`, `claude-opus-4-5-20251101`, `claude-opus-4.5`
- #38 `glm-5.3-flash` — **1469** (±12) → `glm-5.3-flash`
- #39 `ernie-5.1` — **1468** (±5) → `ernie-5.1`
- #40 `mimo-v2.5-pro` — **1468** (±4) → `mimo-v2.5-pro`
- #41 `glm-5.1` — **1466** (±4) → `glm-5.1`
- #42 `gpt-5.6-terra-xhigh` — **1466** (±5) → `gpt-5.6-terra-xhigh`
- #43 `gpt-5.4` — **1466** (±4) → `gpt-5.4`
- #44 `qwen3.5-max-preview` — **1465** (±5) → `qwen3.5-max-preview`
- #45 `grok-4.1-thinking` — **1465** (±3) → `grok-4.1-thinking`
- #46 `deepseek-v4-pro-high-20260813` — **1462** (±9) → `deepseek-v4-pro-high-20260813`
- #47 `claude-sonnet-5-high` — **1461** (±5) → `claude-sonnet-5`, `claude-sonnet-5-high`
- #48 `grok-4.6-high` — **1461** (±10) → `grok-4-6`, `grok-4-6-high`, `grok-4.6`, `grok-4.6-high`
- #49 `kimi-k2.6` — **1461** (±5) → `kimi-k2-6`, `kimi-k2.6`
- #50 `qwen3.6-max-preview` — **1460** (±8) → `qwen3.6-max-preview`
- #51 `grok-4.1` — **1459** (±3) → `grok-4.1`
- #52 `gemini-3-flash (thinking-minimal)` — **1458** (±3) → `gemini-3-flash-thinking-minimal`
- #53 `glm-5` — **1458** (±4) → `glm-5`, `glm5`
- #54 `deepseek-v4-pro` — **1458** (±4) → `deepseek-v4-pro`, `deepseek-v4-pro:free`
- #55 `gemini-3.5-flash-lite` — **1458** (±5) → `gemini-3.5-flash-lite`
- #56 `qwen3.7-plus` — **1456** (±5) → `qwen3.7-plus`
- #57 `claude-sonnet-4-5-20250929-high-32k` — **1456** (±3) → `claude-sonnet-4-5-20250929-high-32k`
- #58 `dola-seed-2.0-pro` — **1456** (±3) → `dola-seed-2.0-pro`
- #59 `hy3` — **1456** (±8) → `hy3`
- #60 `gpt-5.1-high` — **1455** (±4) → `gpt-5.1-high`
- #61 `deepseek-v4-pro-high-preview` — **1455** (±4) → `deepseek-v4-pro-high-preview`
- #62 `claude-sonnet-4-5-20250929` — **1455** (±3) → `claude-sonnet-4-5`, `claude-sonnet-4-5-20250929`, `claude-sonnet-4.5`
- #63 `gpt-5.6-luna-xhigh` — **1452** (±5) → `gpt-5.6-luna-xhigh`
- #64 `gemma-4-31b` — **1451** (±8) → `gemma-4-31b`, `gemma-4-31b-it`, `gemma-4-31b-it-heretic`, `gemma-4-31b-it:free`, `gemma4:31b`
- #65 `kimi-k2.5-thinking` — **1450** (±3) → `kimi-k2.5-thinking`
- #66 `claude-opus-4-1-20250805-thinking-16k` — **1450** (±3) → `claude-opus-4-1-20250805-thinking-16k`
- #67 `gpt-5.3-chat-latest` — **1449** (±4) → `gpt-5.3-chat-latest`
- #68 `ernie-5.0-preview-1203` — **1449** (±7) → `ernie-5.0-preview-1203`
- #69 `mimo-v2-pro` — **1448** (±5) → `mimo-v2-pro`
- #70 `gpt-5.4-mini-high` — **1448** (±4) → `gpt-5.4-mini-high`
- #71 `claude-opus-4-1-20250805` — **1448** (±3) → `claude-opus-4-1`, `claude-opus-4-1-20250805`, `claude-opus-4.1`
- #72 `ernie-5.0-0110` — **1446** (±4) → `ernie-5.0`, `ernie-5.0-0110`
- #73 `gemini-2.5-pro` — **1446** (±2) → `gemini-2.5-pro`
- #74 `gpt-4.5-preview-2025-02-27` — **1445** (±6) → `gpt-4.5-preview-2025-02-27`
- #75 `qwen3.6-plus` — **1444** (±4) → `qwen3.6-plus`, `qwen3.6-plus-free`
- #76 `chatgpt-4o-latest-20250326` — **1443** (±3) → `chatgpt-4o-latest-20250326`
- #77 `grok-4.3` — **1442** (±4) → `grok-4.3`
- #78 `minimax-m3` — **1442** (±4) → `minimax-m3`
- #79 `glm-4.7` — **1442** (±6) → `glm-4.7`, `glm4.7`, `zai-glm-4.7`
- #80 `qwen3.5-397b-a17b` — **1441** (±3) → `qwen3.5-397b-a17b`, `qwen3.5:397b`
- #81 `inkling` — **1439** (±5) → `inkling`
- #82 `gpt-5.1` — **1439** (±4) → `gpt-5.1`
- #83 `deepseek-v4-flash-high-preview` — **1438** (±4) → `deepseek-v4-flash-high-preview`
- #84 `gemma-4-26b-a4b` — **1438** (±8) → `gemma-4-26b-a4b`, `gemma-4-26b-a4b-it`, `gemma-4-26b-a4b-it:free`
- #85 `gpt-5.2-high` — **1438** (±4) → `gpt-5.2-high`
- #86 `gpt-5.2` — **1436** (±3) → `gpt-5.2`
- #87 `qwen3.8-27b` — **1436** (±9) → `qwen3.8-27b`
- #88 `longcat-flash-chat-2602-exp` — **1436** (±5) → `longcat-flash-chat-2602-exp`
- #89 `deepseek-v4-flash` — **1436** (±4) → `deepseek-v4-flash`, `deepseek-v4-flash-free`, `deepseek-v4-flash:free`
- #90 `qwen3-max-preview` — **1435** (±5) → `qwen3-max-preview`
- #91 `gpt-5-high` — **1434** (±5) → `gpt-5-high`
- #92 `mimo-v2.5` — **1434** (±4) → `mimo-v2.5`, `mimo-v2.5-free`
- #93 `glm-5v-turbo` — **1433** (±7) → `glm-5v-turbo`
- #94 `gemini-3.1-flash-lite-preview` — **1432** (±4) → `gemini-3.1-flash-lite-preview`
- #95 `o3-2025-04-16` — **1431** (±4) → `o3`, `o3-2025-04-16`
- #96 `kimi-k2.5-instant` — **1431** (±7) → `kimi-k2.5-instant`
- #97 `mimo-v2-omni` — **1430** (±6) → `mimo-v2-omni`
- #98 `grok-4-1-fast-reasoning` — **1430** (±3) → `grok-4-1-fast-reasoning`
- #99 `kimi-k2-thinking-turbo` — **1430** (±3) → `kimi-k2-thinking-turbo`
- #100 `mistral-medium-3.5` — **1427** (±7) → `mistral-medium-2604`, `mistral-medium-3-5`, `mistral-medium-3.5`, `mistral-medium-c21211-r0-75`
- #101 `gpt-5-chat` — **1427** (±4) → `gpt-5-chat`
- #102 `nvidia-nemotron-3-ultra-550b-a55b-nvfp4` — **1426** (±7) → `nemotron-3-ultra`, `nemotron-3-ultra-550b-a55b`, `nemotron-3-ultra-550b-a55b:free`, `nemotron-3-ultra-free`, `nvidia-nemotron-3-ultra-550b-a55b-nvfp4`
- #103 `muse-glimmer` — **1426** (±10) → `muse-glimmer`
- #104 `amazon-nova-experimental-chat-26-02-10` — **1426** (±10) → `amazon-nova-experimental-chat-26-02-10`
- #105 `claude-opus-4-20250514-thinking-16k` — **1426** (±4) → `claude-opus-4-20250514-thinking-16k`
- #106 `deepseek-v3.2` — **1425** (±4) → `deepseek-v3-2`, `deepseek-v3.2`
- #107 `deepseek-v3.2-exp-thinking` — **1425** (±7) → `deepseek-v3.2-exp-thinking`
- #108 `glm-4.6` — **1425** (±4) → `glm-4.6`
- #109 `qwen3-max-2025-09-23` — **1424** (±7) → `qwen3-max`, `qwen3-max-2025-09-23`
- #110 `qwen3-235b-a22b-instruct-2507` — **1423** (±3) → `qwen-3-235b-a22b-instruct-2507`, `qwen3-235b-a22b-2507`, `qwen3-235b-a22b-instruct-2507`
- #111 `deepseek-v3.2-thinking` — **1423** (±4) → `deepseek-v3.2-thinking`
- #112 `deepseek-v3.2-exp` — **1422** (±6) → `deepseek-v3.2-exp`
- #113 `deepseek-r1-0528` — **1421** (±6) → `deepseek-r1-0528`
- #114 `ernie-5.0-preview-1022` — **1419** (±9) → `ernie-5.0-preview-1022`
- #115 `grok-4-fast-chat` — **1418** (±8) → `grok-4-fast-chat`
- #116 `kimi-k2-0905-preview` — **1418** (±7) → `kimi-k2-0905-preview`
- #117 `kimi-k2-0711-preview` — **1418** (±5) → `kimi-k2-0711-preview`
- #118 `qwen3.5-122b-a10b` — **1417** (±4) → `qwen3.5-122b-a10b`
- #119 `deepseek-v3.1` — **1417** (±6) → `deepseek-v3-1`, `deepseek-v3.1`, `deepseek-v3.1:671b`
- #120 `deepseek-v3.1-terminus-thinking` — **1417** (±10) → `deepseek-v3.1-terminus-thinking`
- #121 `minimax-m2.7` — **1417** (±4) → `minimax-m2.7`
- #122 `deepseek-v3.1-thinking` — **1416** (±7) → `deepseek-v3.1-thinking`
- #123 `amazon-nova-experimental-chat-26-01-10` — **1415** (±10) → `amazon-nova-experimental-chat-26-01-10`
- #124 `deepseek-v3.1-terminus` — **1415** (±10) → `deepseek-v3.1-terminus`
- #125 `gpt-4.1-2025-04-14` — **1414** (±4) → `gpt-4.1`, `gpt-4.1-2025-04-14`
- #126 `mistral-large-3` — **1414** (±3) → `mistral-large-2512`, `mistral-large-3`
- #127 `qwen3-vl-235b-a22b-instruct` — **1414** (±7) → `qwen3-vl-235b-a22b-instruct`, `qwen3-vl:235b`, `qwen3-vl:235b-instruct`
- #128 `claude-opus-4-20250514` — **1414** (±4) → `claude-opus-4`, `claude-opus-4-20250514`
- #129 `claude-haiku-4-5-20251001` — **1413** (±3) → `claude-haiku-4-5`, `claude-haiku-4-5-20251001`, `claude-haiku-4.5`
- #130 `hunyuan-hy3-preview` — **1413** (±8) → `hunyuan-hy3-preview`, `hy3-preview`, `hy3-preview:free`
- #131 `grok-3-preview-02-24` — **1411** (±4) → `grok-3-preview-02-24`
- #132 `glm-4.5` — **1411** (±5) → `glm-4.5`
- #133 `grok-4-0709` — **1411** (±4) → `grok-4`, `grok-4-0709`
- #134 `gemini-2.5-flash` — **1410** (±2) → `gemini-2.5-flash`
- #135 `mistral-medium-2508` — **1409** (±3) → `mistral-medium-2508`, `mistral-medium-3.1`
- #136 `qwen3.5-27b` — **1408** (±4) → `qwen3.5-27b`
- #137 `Inkling Small` — **1407** (±6) → `inkling-small`
- #138 `grok-4-fast-reasoning` — **1405** (±5) → `grok-4-fast-reasoning`
- #139 `gemini-2.5-flash-preview-09-2025` — **1404** (±4) → `gemini-2.5-flash-preview-09-2025`
- #140 `qwen3-235b-a22b-no-thinking` — **1402** (±5) → `qwen3-235b-a22b-no-thinking`
- #141 `gpt-5.4-nano-high` — **1402** (±4) → `gpt-5.4-nano-high`
- #142 `o1-2024-12-17` — **1402** (±4) → `o1`, `o1-2024-12-17`
- #143 `longcat-flash-chat` — **1401** (±6) → `longcat-flash-chat`
- #144 `claude-sonnet-4-20250514-thinking-32k` — **1401** (±4) → `claude-sonnet-4-20250514-thinking-32k`
- #145 `qwen3-235b-a22b-thinking-2507` — **1400** (±7) → `qwen3-235b-a22b-thinking-2507`
- #146 `qwen3-next-80b-a3b-instruct` — **1399** (±5) → `qwen3-next-80b-a3b-instruct`, `qwen3-next-80b-a3b-instruct:free`, `qwen3-next:80b`
- #147 `deepseek-r1` — **1398** (±5) → `deepseek-r1`, `deepseek-reasoner`
- #148 `qwen3.5-flash` — **1397** (±4) → `qwen3.5-flash`, `qwen3.5-flash-02-23`
- #149 `deepseek-v3-0324` — **1396** (±4) → `deepseek-chat-v3-0324`, `deepseek-v3-0324`
- #150 `hunyuan-vision-1.5-thinking` — **1395** (±12) → `hunyuan-vision-1.5-thinking`
- #151 `qwen3.5-35b-a3b` — **1395** (±4) → `qwen3.5-35b-a3b`
- #152 `qwen3-vl-235b-a22b-thinking` — **1395** (±7) → `qwen3-vl-235b-a22b-thinking`
- #153 `amazon-nova-experimental-chat-12-10` — **1395** (±10) → `amazon-nova-experimental-chat-12-10`
- #154 `step-3.5-flash` — **1394** (±4) → `step-3.5-flash`
- #155 `mimo-v2-flash (non-thinking)` — **1392** (±4) → —
- #156 `o4-mini-2025-04-16` — **1391** (±4) → `o4-mini`, `o4-mini-2025-04-16`
- #157 `minimax-m2.5` — **1390** (±4) → `minimax-m2.5`, `minimax-m2.5-free`, `minimax-m2.5:free`
- #158 `claude-sonnet-4-20250514` — **1390** (±4) → `claude-sonnet-4`, `claude-sonnet-4-20250514`
- #159 `gpt-5-mini-high` — **1389** (±5) → `gpt-5-mini-high`
- #160 `o1-preview` — **1388** (±5) → `o1-preview`, `o1-preview-2024-09-12`
- #161 `claude-3-7-sonnet-20250219-thinking-32k` — **1388** (±4) → `claude-3-7-sonnet-20250219-thinking-32k`
- #162 `mistral-medium-2505` — **1387** (±5) → `mistral-medium-2505`, `mistral-medium-3`, `mistral-medium-3-instruct`
- #163 `hunyuan-t1-20250711` — **1387** (±9) → `hunyuan-t1-20250711`
- #164 `qwen3-coder-480b-a35b-instruct` — **1387** (±5) → `qwen3-coder-480b-a35b`, `qwen3-coder-480b-a35b-instruct`, `qwen3-coder:480b`
- #165 `mimo-v2-flash (thinking)` — **1386** (±6) → `mimo-v2-flash-thinking`
- #166 `minimax-m2.1-preview` — **1384** (±5) → `minimax-m2.1-preview`
- #167 `hunyuan-turbos-20250416` — **1382** (±7) → `hunyuan-turbos-20250416`
- #168 `gpt-4.1-mini-2025-04-14` — **1382** (±4) → `gpt-4.1-mini`, `gpt-4.1-mini-2025-04-14`
- #169 `qwen3-30b-a3b-instruct-2507` — **1382** (±5) → `qwen3-30b-a3b-instruct-2507`
- #170 `gemini-2.5-flash-lite-preview-09-2025-no-thinking` — **1380** (±3) → `gemini-2.5-flash-lite-preview-09-2025`, `gemini-2.5-flash-lite-preview-09-2025-no-thinking`
- #171 `trinity-large-preview` — **1378** (±4) → `trinity-large-preview`, `trinity-large-preview:free`
- #172 `glm-4.6v` — **1378** (±11) → `glm-4.6v`
- #173 `solar-pro4` — **1376** (±12) → `solar-pro-4`, `solar-pro4`
- #174 `qwen3-235b-a22b` — **1375** (±5) → `qwen3-235b`, `qwen3-235b-a22b`
- #175 `gemini-2.5-flash-lite-preview-06-17-thinking` — **1375** (±5) → `gemini-2.5-flash-lite-preview-06-17`, `gemini-2.5-flash-lite-preview-06-17-thinking`
- #176 `qwen2.5-max` — **1374** (±4) → `qwen2.5-max`
- #177 `claude-3-5-sonnet-20241022` — **1374** (±3) → `claude-3-5-sonnet`, `claude-3-5-sonnet-20241022`, `claude-3.5-sonnet`
- #178 `glm-4.5-air` — **1373** (±4) → `glm-4.5-air`, `glm-4.5-air:free`
- #179 `claude-3-7-sonnet-20250219` — **1372** (±4) → `claude-3-7-sonnet`, `claude-3-7-sonnet-20250219`, `claude-3.7-sonnet`
- #180 `trinity-large-thinking` — **1369** (±5) → `trinity-large-thinking`, `trinity-large-thinking:free`
- #181 `qwen3-next-80b-a3b-thinking` — **1369** (±6) → `qwen3-next-80b-a3b-thinking`
- #182 `glm-4.7-flash` — **1366** (±6) → `glm-4.7-flash`
- #183 `amazon-nova-experimental-chat-11-10` — **1365** (±4) → `amazon-nova-experimental-chat-11-10`
- #184 `gemma-3-27b-it` — **1365** (±4) → `gemma-3-27b`, `gemma-3-27b-it`, `gemma-3-27b-it:free`, `gemma3:27b`
- #185 `grok-3-mini-high` — **1364** (±5) → `grok-3-mini-high`
- #186 `minimax-m1` — **1364** (±4) → `minimax-m1`
- #187 `o3-mini-high` — **1363** (±5) → `o3-mini-high`
- #188 `nvidia-nemotron-3-super-120b-a12b` — **1361** (±7) → `nemotron-3-super`, `nemotron-3-super-120b-a12b`, `nemotron-3-super-120b-a12b:free`, `nvidia-nemotron-3-super-120b-a12b`
- #189 `gemini-2.0-flash-001` — **1360** (±4) → `gemini-2.0-flash`, `gemini-2.0-flash-001`
- #190 `deepseek-v3` — **1358** (±5) → `deepseek-v3`
- #191 `grok-3-mini-beta` — **1358** (±5) → `grok-3-mini-beta`
- #192 `mistral-small-2506` — **1357** (±5) → `mistral-small-2506`, `mistral-small-3.2`
- #193 `intellect-3` — **1356** (±8) → `intellect-3`
- #194 `command-a-03-2025` — **1354** (±3) → `command-a`, `command-a-03-2025`
- #195 `gemini-2.0-flash-lite-preview-02-05` — **1353** (±4) → `gemini-2.0-flash-lite-preview-02-05`
- #196 `gpt-oss-120b` — **1352** (±4) → `gpt-oss-120b`, `gpt-oss-120b:free`, `gpt-oss:120b`
- #197 `glm-4.5v` — **1352** (±8) → `glm-4.5v`
- #198 `gemini-1.5-pro-002` — **1351** (±3) → `gemini-1.5-pro-002`
- #199 `amazon-nova-experimental-chat-10-20` — **1350** (±6) → `amazon-nova-experimental-chat-10-20`
- #200 `step-3` — **1350** (±8) → `step-3`
- #201 `hunyuan-turbos-20250226` — **1349** (±12) → `hunyuan-turbos-20250226`
- #202 `nvidia-nemotron-3.5-lightning-30b-a3b-nvfp4` — **1348** (±11) → `nemotron-3.5-lightning`, `nemotron-3.5-lightning:free`, `nvidia-nemotron-3.5-lightning-30b-a3b-nvfp4`
- #203 `o3-mini` — **1348** (±4) → `o3-mini`, `o3-mini-2025-01-31`
- #204 `llama-3.1-nemotron-ultra-253b-v1` — **1347** (±12) → `llama-3.1-nemotron-ultra-253b-v1`
- #205 `qwen3-32b` — **1347** (±9) → `qwen3-32b`
- #206 `amazon-nova-experimental-chat-10-09` — **1347** (±11) → `amazon-nova-experimental-chat-10-09`
- #207 `mercury-2` — **1346** (±11) → `mercury-2`
- #208 `qwen-plus-0125` — **1346** (±8) → `qwen-plus`, `qwen-plus-0125`
- #209 `gpt-4o-2024-05-13` — **1346** (±3) → `gpt-4o-2024-05-13`
- #210 `minimax-m2` — **1345** (±8) → `minimax-m2`
- #211 `ling-flash-2.0` — **1344** (±7) → `ling-flash-2.0`
- #212 `nvidia-llama-3.3-nemotron-super-49b-v1.5` — **1343** (±10) → `llama-3.3-nemotron-super-49b-v1.5`, `nvidia-llama-3.3-nemotron-super-49b-v1.5`
- #213 `claude-3-5-sonnet-20240620` — **1343** (±3) → `claude-3-5-sonnet-20240620`
- #214 `glm-4-plus-0111` — **1343** (±8) → `glm-4-plus-0111`
- #215 `gemma-3-12b-it` — **1342** (±10) → `gemma-3-12b`, `gemma-3-12b-it`, `gemma-3-12b-it:free`, `gemma3:12b`
- #216 `hunyuan-turbo-0110` — **1341** (±12) → `hunyuan-turbo-0110`
- #217 `gpt-5-nano-high` — **1337** (±7) → `gpt-5-nano-high`
- #218 `o1-mini` — **1337** (±4) → `o1-mini`, `o1-mini-2024-09-12`
- #219 `nova-2-lite` — **1336** (±6) → `nova-2-lite`, `nova-2-lite-v1`
- #220 `qwq-32b` — **1336** (±4) → `qwen-qwq-32b`, `qwq-32b`
- #221 `gemini-advanced-0514` — **1336** (±5) → `gemini-advanced-0514`
- #222 `grok-2-2024-08-13` — **1335** (±4) → `grok-2`, `grok-2-2024-08-13`
- #223 `gpt-4o-2024-08-06` — **1335** (±4) → `gpt-4o`, `gpt-4o-2024-08-06`
- #224 `llama-3.1-405b-instruct-bf16` — **1335** (±4) → —
- #225 `step-2-16k-exp-202412` — **1334** (±9) → `step-2-16k-exp-202412`
- #226 `llama-3.1-405b-instruct-fp8` — **1333** (±4) → —
- #227 `olmo-3.1-32b-instruct` — **1330** (±6) → `olmo-3.1-32b-instruct`
- #228 `yi-lightning` — **1328** (±5) → `yi-lightning`
- #229 `molmo-2-8b` — **1328** (±21) → `molmo-2-8b`
- #230 `llama-3.3-nemotron-49b-super-v1` — **1328** (±12) → —
- #231 `qwen3-30b-a3b` — **1327** (±5) → `qwen3-30b-a3b`
- #232 `llama-4-maverick-17b-128e-instruct` — **1327** (±4) → `llama-4-maverick`, `llama-4-maverick-17b-128e`, `llama-4-maverick-17b-128e-instruct`
- #233 `hunyuan-large-2025-02-10` — **1326** (±10) → —
- #234 `gpt-4-turbo-2024-04-09` — **1324** (±4) → `gpt-4-turbo`, `gpt-4-turbo-2024-04-09`
- #235 `claude-3-5-haiku-20241022` — **1324** (±3) → `claude-3-5-haiku`, `claude-3-5-haiku-20241022`, `claude-3.5-haiku`
- #236 `gemini-1.5-pro-001` — **1324** (±4) → `gemini-1.5-pro`, `gemini-1.5-pro-001`
- #237 `deepseek-v2.5-1210` — **1323** (±8) → `deepseek-v2.5-1210`
- #238 `gpt-4.1-nano-2025-04-14` — **1322** (±8) → `gpt-4.1-nano`, `gpt-4.1-nano-2025-04-14`
- #239 `claude-3-opus-20240229` — **1322** (±3) → `claude-3-opus`, `claude-3-opus-20240229`
- #240 `llama-4-scout-17b-16e-instruct` — **1321** (±5) → `llama-4-scout`, `llama-4-scout-17b-16e`, `llama-4-scout-17b-16e-instruct`
- #241 `ring-flash-2.0` — **1321** (±7) → `ring-flash-2.0`
- #242 `step-1o-turbo-202506` — **1320** (±7) → —
- #243 `glm-4-plus` — **1319** (±5) → `glm-4-plus`
- #244 `qwen-max-0919` — **1318** (±6) → —
- #245 `gpt-4o-mini-2024-07-18` — **1318** (±4) → `gpt-4o-mini`, `gpt-4o-mini-2024-07-18`
- #246 `llama-3.3-70b-instruct` — **1317** (±4) → `llama-3.3-70b`, `llama-3.3-70b-instruct`, `llama-3.3-70b-instruct-turbo`, `llama-3.3-70b-instruct:free`, `llama-3.3-70b-versatile`, `llama-v3p3-70b-instruct`, `llama3.3`, `llama3.3:70b`
- #247 `gpt-oss-20b` — **1317** (±6) → `gpt-oss-20b`, `gpt-oss-20b:free`, `gpt-oss:20b`
- #248 `gemma-3n-e4b-it` — **1317** (±5) → `gemma-3n-e4b-it`, `gemma-3n-e4b-it:free`
- #249 `qwen2.5-plus-1127` — **1315** (±6) → —
- #250 `nvidia-nemotron-3-nano-30b-a3b-bf16` — **1314** (±6) → —
- #251 `mistral-large-2407` — **1314** (±4) → `mistral-large-2-instruct`, `mistral-large-2407`
- #252 `athene-v2-chat` — **1314** (±5) → —
- #253 `gpt-4-0125-preview` — **1313** (±4) → `gpt-4-0125-preview`, `gpt-4-turbo-preview`
- #254 `gpt-4-1106-preview` — **1312** (±4) → `gpt-4-1106-preview`
- #255 `hunyuan-standard-2025-02-10` — **1311** (±10) → —
- #256 `gemini-1.5-flash-002` — **1309** (±4) → `gemini-1.5-flash-002`
- #257 `mercury` — **1308** (±14) → `mercury`
- #258 `grok-2-mini-2024-08-13` — **1308** (±4) → —
- #259 `deepseek-v2.5` — **1307** (±5) → `deepseek-v2.5`
- #260 `olmo-3-32b-think` — **1307** (±8) → `olmo-3-32b-think`
- #261 `athene-70b-0725` — **1306** (±6) → —
- #262 `granite-4.1-8b` — **1306** (±10) → `granite-4.1-8b`
- #263 `mistral-large-2411` — **1305** (±4) → `mistral-large-2411`
- #264 `magistral-medium-2506` — **1304** (±6) → `magistral-medium-2506`
- #265 `gemma-3-4b-it` — **1303** (±9) → `gemma-3-4b`, `gemma-3-4b-it`, `gemma-3-4b-it:free`, `gemma3:4b`
- #266 `mistral-small-3.1-24b-instruct-2503` — **1303** (±5) → `mistral-small-3.1-24b-instruct`, `mistral-small-3.1-24b-instruct-2503`
- #267 `qwen2.5-72b-instruct` — **1303** (±4) → `qwen-2.5-72b-instruct`, `qwen2.5-72b`, `qwen2.5-72b-instruct`, `qwen2.5-72b-instruct-turbo`, `qwen2.5:72b`
- #268 `llama-3.1-nemotron-70b-instruct` — **1299** (±8) → `llama-3.1-nemotron-70b-instruct`
- #269 `hunyuan-large-vision` — **1294** (±9) → —
- #270 `llama-3.1-70b-instruct` — **1293** (±4) → `llama-3.1-70b`, `llama-3.1-70b-instruct`, `llama-3.1-70b-instruct-turbo`, `llama-3.1-70b-versatile`, `llama-v3p1-70b-instruct`, `llama3.1:70b`
- #271 `amazon-nova-pro-v1.0` — **1290** (±5) → —
- #272 `gemma-2-27b-it` — **1289** (±3) → `gemma-2-27b-it`, `gemma2:27b`
- #273 `jamba-1.5-large` — **1289** (±7) → `jamba-1.5-large`, `jamba-1.5-large-instruct`
- #274 `reka-core-20240904` — **1288** (±7) → `reka-core-20240904`
- #275 `gpt-4-0314` — **1287** (±5) → `gpt-4`, `gpt-4-0314`
- #276 `gemini-1.5-flash-001` — **1286** (±5) → `gemini-1.5-flash`, `gemini-1.5-flash-001`
- #277 `llama-3.1-nemotron-51b-instruct` — **1286** (±10) → `llama-3.1-nemotron-51b-instruct`
- #278 `llama-3.1-tulu-3-70b` — **1286** (±10) → —
- #279 `olmo-3.1-32b-think` — **1286** (±7) → —
- #280 `ibm-granite-h-small` — **1285** (±8) → —
- #281 `claude-3-sonnet-20240229` — **1281** (±4) → `claude-3-sonnet`, `claude-3-sonnet-20240229`
- #282 `gemma-2-9b-it-simpo` — **1280** (±7) → —
- #283 `nemotron-4-340b-instruct` — **1277** (±5) → `nemotron-4-340b-instruct`
- #284 `llama-3-70b-instruct` — **1276** (±4) → `llama-3-70b-instruct`, `llama3-70b-instruct`
- #285 `command-r-plus-08-2024` — **1276** (±7) → `command-r-plus-08-2024`
- #286 `gpt-4-0613` — **1276** (±4) → `gpt-4-0613`
- #287 `mistral-small-24b-instruct-2501` — **1274** (±6) → `mistral-small-24b-instruct`, `mistral-small-24b-instruct-2501`
- #288 `glm-4-0520` — **1273** (±7) → —
- #289 `reka-flash-20240904` — **1272** (±7) → —
- #290 `qwen2.5-coder-32b-instruct` — **1270** (±8) → `qwen-2.5-coder-32b`, `qwen-2.5-coder-32b-instruct`, `qwen2.5-coder-32b`, `qwen2.5-coder-32b-instruct`
- #291 `c4ai-aya-expanse-32b` — **1267** (±5) → `c4ai-aya-expanse-32b`
- #292 `gemma-2-9b-it` — **1267** (±4) → `gemma-2-9b-it`, `gemma2-9b-it`, `gemma2:9b`
- #293 `deepseek-coder-v2` — **1265** (±6) → `deepseek-coder-v2`
- #294 `qwen2-72b-instruct` — **1261** (±5) → `qwen2-72b-instruct`
- #295 `command-r-plus` — **1261** (±4) → `command-r-plus`
- #296 `claude-3-haiku-20240307` — **1261** (±4) → `claude-3-haiku`, `claude-3-haiku-20240307`
- #297 `amazon-nova-lite-v1.0` — **1260** (±5) → —
- #298 `gemini-1.5-flash-8b-001` — **1259** (±4) → `gemini-1.5-flash-8b`, `gemini-1.5-flash-8b-001`
- #299 `phi-4` — **1256** (±5) → `phi-4`, `phi-4-14b`, `phi4`, `phi4:14b`
- #300 `olmo-2-0325-32b-instruct` — **1251** (±11) → `olmo-2-0325-32b-instruct`
- #301 `command-r-08-2024` — **1250** (±7) → `command-r-08-2024`
- #302 `mistral-large-2402` — **1242** (±5) → —
- #303 `amazon-nova-micro-v1.0` — **1240** (±5) → —
- #304 `jamba-1.5-mini` — **1239** (±7) → `jamba-1.5-mini`, `jamba-1.5-mini-instruct`
- #305 `ministral-8b-2410` — **1237** (±9) → `ministral-8b-2410`
- #306 `gemini-pro-dev-api` — **1236** (±7) → —
- #307 `qwen1.5-110b-chat` — **1234** (±6) → —
- #308 `hunyuan-standard-256k` — **1233** (±12) → —
- #309 `reka-flash-21b-20240226-online` — **1233** (±7) → —
- #310 `qwen1.5-72b-chat` — **1233** (±5) → —
- #311 `mixtral-8x22b-instruct-v0.1` — **1229** (±5) → `mixtral-8x22b`, `mixtral-8x22b-instruct`, `mixtral-8x22b-instruct-v0.1`, `mixtral-8x22b-v0.1`
- #312 `command-r` — **1226** (±5) → `command-r`
- #313 `reka-flash-21b-20240226` — **1226** (±6) → —
- #314 `gpt-3.5-turbo-0125` — **1225** (±5) → `gpt-3.5-turbo-0125`
- #315 `llama-3-8b-instruct` — **1223** (±4) → `llama-3-8b-instruct`, `llama3-8b-instruct`
- #316 `gemini-pro` — **1223** (±12) → —
- #317 `c4ai-aya-expanse-8b` — **1223** (±7) → `c4ai-aya-expanse-8b`
- #318 `mistral-medium` — **1222** (±5) → `mistral-medium`
- #319 `llama-3.1-tulu-3-8b` — **1220** (±11) → —
- #320 `yi-1.5-34b-chat` — **1212** (±5) → —
- #321 `zephyr-orpo-141b-A35b-v0.1` — **1212** (±11) → —
- #322 `llama-3.1-8b-instruct` — **1211** (±4) → `llama-3.1-8b`, `llama-3.1-8b-instant`, `llama-3.1-8b-instruct`, `llama-3.1-8b-instruct-turbo`, `llama-v3p1-8b-instruct`, `llama3.1`, `llama3.1-8b`, `llama3.1:8b`
- #323 `granite-3.1-8b-instruct` — **1208** (±11) → `granite-3.1-8b-instruct`
- #324 `gpt-3.5-turbo-1106` — **1203** (±9) → `gpt-3.5-turbo-1106`
- #325 `qwen1.5-32b-chat` — **1203** (±6) → —
- #326 `gemma-2-2b-it` — **1200** (±4) → `gemma-2-2b-it`, `gemma2:2b`
- #327 `phi-3-medium-4k-instruct` — **1197** (±5) → `phi-3-medium-4k-instruct`
- #328 `mixtral-8x7b-instruct-v0.1` — **1197** (±4) → `mixtral-8x7b`, `mixtral-8x7b-instruct`, `mixtral-8x7b-instruct-v0.1`
- #329 `dbrx-instruct-preview` — **1195** (±6) → —
- #330 `qwen1.5-14b-chat` — **1191** (±7) → —
- #331 `internlm2_5-20b-chat` — **1190** (±7) → —
- #332 `deepseek-llm-67b-chat` — **1184** (±11) → —
- #333 `wizardlm-70b` — **1184** (±9) → —
- #334 `yi-34b-chat` — **1183** (±7) → `yi-34b-chat`
- #335 `granite-3.0-8b-instruct` — **1182** (±9) → `granite-3.0-8b-instruct`
- #336 `openchat-3.5` — **1182** (±10) → —
- #337 `gemma-1.1-7b-it` — **1182** (±6) → —
- #338 `openchat-3.5-0106` — **1182** (±8) → —
- #339 `snowflake-arctic-instruct` — **1179** (±6) → —
- #340 `granite-3.1-2b-instruct` — **1178** (±11) → `granite-3.1-2b-instruct`
- #341 `tulu-2-dpo-70b` — **1177** (±10) → —
- #342 `openhermes-2.5-mistral-7b` — **1175** (±10) → —
- #343 `vicuna-33b` — **1172** (±6) → —
- #344 `phi-3-small-8k-instruct` — **1171** (±6) → `phi-3-small-8k-instruct`
- #345 `starling-lm-7b-beta` — **1170** (±7) → —
- #346 `llama-2-70b-chat` — **1170** (±5) → —
- #347 `starling-lm-7b-alpha` — **1167** (±8) → —
- #348 `llama-3.2-3b-instruct` — **1166** (±8) → `llama-3.2-3b`, `llama-3.2-3b-instruct`, `llama-3.2-3b-instruct-turbo`, `llama-3.2-3b-instruct:free`
- #349 `nous-hermes-2-mixtral-8x7b-dpo` — **1164** (±12) → `nous-hermes-2-mixtral-8x7b-dpo`
- #350 `granite-3.0-2b-instruct` — **1156** (±8) → `granite-3.0-2b-instruct`
- #351 `llama2-70b-steerlm-chat` — **1154** (±13) → —
- #352 `qwq-32b-preview` — **1154** (±11) → `qwq-32b-preview`
- #353 `solar-10.7b-instruct-v1.0` — **1152** (±13) → —
- #354 `dolphin-2.2.1-mistral-7b` — **1152** (±15) → —
- #355 `mpt-30b-chat` — **1150** (±12) → —
- #356 `wizardlm-13b` — **1149** (±9) → —
- #357 `mistral-7b-instruct-v0.2` — **1149** (±7) → `mistral-7b-instruct-v0.2`
- #358 `falcon-180b-chat` — **1147** (±17) → —
- #359 `qwen1.5-7b-chat` — **1143** (±10) → —
- #360 `phi-3-mini-4k-instruct-june-2024` — **1143** (±6) → —
- #361 `vicuna-13b` — **1141** (±7) → —
- #362 `llama-2-13b-chat` — **1141** (±7) → —
- #363 `qwen-14b-chat` — **1139** (±11) → —
- #364 `palm-2` — **1138** (±9) → —
- #365 `gemma-7b-it` — **1137** (±9) → —
- #366 `codellama-34b-instruct` — **1136** (±9) → —
- #367 `zephyr-7b-beta` — **1130** (±9) → —
- #368 `phi-3-mini-128k-instruct` — **1129** (±7) → `phi-3-mini-128k-instruct`
- #369 `phi-3-mini-4k-instruct` — **1128** (±6) → `phi-3-mini-4k-instruct`
- #370 `guanaco-33b` — **1127** (±12) → —
- #371 `zephyr-7b-alpha` — **1126** (±16) → —
- #372 `stripedhyena-nous-7b` — **1121** (±11) → —
- #373 `codellama-70b-instruct` — **1119** (±18) → —
- #374 `gemma-1.1-2b-it` — **1116** (±8) → —
- #375 `vicuna-7b` — **1115** (±9) → —
- #376 `smollm2-1.7b-instruct` — **1114** (±14) → —
- #377 `llama-3.2-1b-instruct` — **1111** (±8) → `llama-3.2-1b`, `llama-3.2-1b-instruct`
- #378 `mistral-7b-instruct` — **1110** (±9) → `mistral-7b-instruct`, `mistral-7b-instruct-v0.1`
- #379 `llama-2-7b-chat` — **1107** (±7) → —
- #380 `gemma-2b-it` — **1093** (±11) → —
- #381 `qwen1.5-4b-chat` — **1090** (±9) → —
- #382 `olmo-7b-instruct` — **1073** (±11) → —
- #383 `koala-13b` — **1070** (±10) → —
- #384 `alpaca-13b` — **1069** (±11) → —
- #385 `gpt4all-13b-snoozy` — **1067** (±15) → —
- #386 `mpt-7b-chat` — **1063** (±12) → —
- #387 `chatglm3-6b` — **1056** (±12) → `chatglm3-6b`
- #388 `RWKV-4-Raven-14B` — **1042** (±11) → —
- #389 `chatglm2-6b` — **1024** (±14) → —
- #390 `oasst-pythia-12b` — **1023** (±11) → —
- #391 `chatglm-6b` — **995** (±13) → —
- #392 `fastchat-t5-3b` — **992** (±12) → —
- #393 `dolly-v2-12b` — **982** (±13) → —
- #394 `llama-13b` — **974** (±16) → —
- #395 `stablelm-tuned-alpha-7b` — **953** (±13) → —

# Estimated

555 catalog ids keep a **family / sibling / alias estimate** (or an older attested value that this refresh did not rematch). They are not re-derived on this pass.

Do not treat those numbers as Arena facts. See [matching-policy.md](matching-policy.md).

# Notes

- Runtime lookup strips a leading `provider/` prefix and lowercases the id; catalog keys are already lowercase.
- New board models added to the runtime catalog on this refresh: `claude-opus-4-6-high` / `claude-opus-4-7-high` / `claude-opus-4-8-high`, `gemini-3.7-flash-high`, `gemini-3.6-flash-high`, `glm-5.3-max` / `glm-5.3-flash`, `deepseek-v4-pro-high-20260813`, `qwen3.8-27b`, `inkling-small`, `grok-3-mini-high`.
- Historical unmatched board rows (older chat models Kai does not ship metadata for) are listed above with `→ —` and were **not** added to `baseEntries`.
- Claude Arena rows that renamed `thinking` → `high` were **not** copied onto existing `-thinking` catalog ids (quality-tier rule). The new `-high` ids were added instead.

[^arena-text]: Arena text leaderboard
