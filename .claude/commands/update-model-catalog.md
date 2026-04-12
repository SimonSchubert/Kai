Update the LMArena Elo scores and model catalog in `ModelCatalog.kt`.

## 1. Fetch the latest LMArena leaderboard

Use WebFetch to get the leaderboard data from `https://lmarena.ai/leaderboard`. Parse the page for model names and their Elo scores. If the main page doesn't work, try the API at `https://lmarena.ai/api/v1/leaderboard` or `https://arena.ai/api/v1/leaderboard`.

The leaderboard shows models ranked by Elo rating (higher = better). Extract every model name and its Elo score.

## 2. Read the current catalog

Read `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/ModelCatalog.kt`. This file has two key sections:

- `baseEntries` — the main catalog with `CuratedModelInfo(displayName, contextWindow, releaseDate, parameterCount)` for each model ID
- `arenaScores` — a separate `mapOf(...)` mapping model IDs to Elo ints

## 3. Match arena models to catalog IDs

For each model on the arena leaderboard:
1. Find matching entries in `baseEntries` (by model name/ID)
2. Map each arena model to ALL its catalog aliases (e.g. `claude-opus-4-5` has aliases `claude-opus-4.5`, `claude-opus-4-5-20251101`, etc.)
3. Use the Elo score from the leaderboard

**Matching rules:**
- Arena names may differ slightly from catalog IDs (e.g. arena says `claude-opus-4-5-20251101`, catalog has `claude-opus-4-5`)
- All aliases of a model should get the same score
- Models not in the arena (niche/local models) should get an estimated score based on similar models in their family

## 4. Update the arenaScores map

Replace the entire `arenaScores` map in `ModelCatalog.kt` with updated scores. Keep the existing format:
- Grouped by provider with `// --- Provider ---` comments
- Multiple aliases on the same line when they share a score
- Sorted by provider, then by score descending within provider

## 5. Fill gaps

Every model in `baseEntries` that is a chat model (not filtered by `isChatModel()` in `ModelTransformations.kt`) should have an arena score. For models not on the leaderboard:
- Use the score of the closest sibling model (same family, similar size)
- For fine-tuned variants, use the base model's score
- For `-latest`/`-preview` aliases, use the same score as the base model

## 6. Verify

After updating, check:
- The file still compiles: `./gradlew :composeApp:compileKotlinDesktop`
- Tests still pass: `./gradlew :composeApp:desktopTest`
- Run `./gradlew spotlessApply` to fix formatting

## 7. Check for new models

Look at the arena leaderboard for any new models that are NOT in `baseEntries` yet. If found, add them to `baseEntries` with appropriate metadata (displayName, contextWindow, releaseDate, parameterCount) AND add their arena score.

## Important

- Do NOT remove existing catalog entries
- Do NOT change `baseEntries` metadata unless correcting an error
- Preserve the `-latest` → "(Latest)" and `-preview` → "(Preview)" display name convention
- Show a summary of changes before writing (new models added, scores updated, gaps filled)
