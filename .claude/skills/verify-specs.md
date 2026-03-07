# Verify Specs

Verify that the codebase conforms with the feature specs in `docs/features/`.

## User-invocable

- `/verify-specs` — Run spec verification for all feature docs
- `/verify-specs <feature>` — Run spec verification for a specific feature (e.g. `/verify-specs multi-service`)

## Instructions

For each feature doc in `docs/features/`:

1. **Read the spec** — Parse the feature doc and extract every behavioral claim (e.g. "retries up to 3 times", "shows only chat-oriented models", "debounce of 800 ms").

2. **Locate the key files** — Use the "Key Files" table at the bottom of each spec to find the relevant source files. If files listed don't exist, flag them.

3. **Verify each claim against the code** — For every behavioral claim in the spec, search the key files (and related files if needed) for the corresponding implementation. Check that:
   - The behavior described actually exists in code
   - Numeric values match (retry counts, delays, timeouts, etc.)
   - Lists of items are complete (e.g. all statuses, all filtered prefixes, all services)
   - Conditional logic matches the described conditions

4. **Report results** — Output a summary in this format:

   ```
   ## <Feature Name> — <PASS | ISSUES FOUND>

   **Spec:** docs/features/<name>.md
   **Last verified date in doc:** <date>

   ### Claims verified (N/N)
   - [x] <claim> — confirmed in <file>:<line>
   - [ ] <claim> — MISMATCH: spec says X, code says Y
   - [ ] <claim> — NOT FOUND: could not locate implementation

   ### Missing from spec
   - <any significant product-visible behavior found in code but not in the spec>

   ### Stale references
   - <any files listed in Key Files that don't exist or have moved>
   ```

5. **Update the "Last verified" date** — If all claims pass, update the date in the spec header to today's date. If issues are found, do NOT update the date — leave it for the user to decide.

## Scope

- Only verify product-visible behavior, not implementation details
- Don't flag internal patterns (error handling strategy, class hierarchy, etc.) unless they contradict a spec claim
- If a spec claim is ambiguous, note it but don't count it as a failure
- When checking numeric values, allow for minor constant naming differences (e.g. `DELAY_MS = 800` matches "800 ms debounce")
