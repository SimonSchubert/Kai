# Optimize Compose

Scan Compose UI code for performance issues and fix them. Checks stability annotations, recomposition triggers, and Compose best practices against this project's established patterns.

## User-invocable

- `/optimize-compose` — Scan recently changed Compose files (based on `git diff --name-only HEAD~5`)
- `/optimize-compose all` — Scan all Compose files in the project
- `/optimize-compose <file-or-dir>` — Scan a specific file or directory

## Instructions

### 1. Determine target files

If `$ARGUMENTS` is `all`, find all `.kt` files under `composeApp/src/commonMain/` that contain Compose-related code.

If `$ARGUMENTS` is a file or directory path, use that.

Otherwise (no argument), run:

```
git diff --name-only HEAD~5
```

In all cases, filter to only `.kt` files under `composeApp/src/` that contain Compose-related code (files importing from `androidx.compose`, or files whose name ends in `UiState`, `Actions`, `Screen`, or `ViewModel`).

If no matching files are found, report that and stop.

### 2. Check stability annotations

For every `data class` used as a composable parameter or contained in a UI state object, verify:

- The class has `@Immutable` annotation (from `androidx.compose.runtime.Immutable`)
- All `List<>` properties use `ImmutableList<>` from `kotlinx.collections.immutable` with `persistentListOf()` defaults
- All `Set<>` properties use `ImmutableSet<>` and all `Map<>` properties use `ImmutableMap<>`
- Nested data classes referenced from state also have `@Immutable`
- Enum classes used in state are fine as-is (they are inherently stable)

Action holder classes (data classes containing only lambda fields, like `ChatActions`) must have `@Immutable`.

### 3. Check for recomposition issues

In each `@Composable` function, check:

- **Missing `remember`**: Computations, object allocations, or list transformations inside composition that don't depend on changing inputs must be wrapped in `remember {}` or `remember(key) {}`
- **Missing `derivedStateOf`**: State reads used only for derived boolean/computed values (e.g. "is list scrolled to bottom") should use `derivedStateOf` inside `remember`
- **Unstable lambda parameters**: Lambdas passed to child composables should come from a remembered/stable source (like an `@Immutable` actions class) or be wrapped in `remember`. Inline lambdas that capture ViewModel methods are acceptable only if the ViewModel reference is stable
- **LaunchedEffect / DisposableEffect keys**: Every `LaunchedEffect` and `DisposableEffect` must have explicit keys matching the values they depend on. `LaunchedEffect(Unit)` is acceptable only for one-time effects
- **Allocations in composition**: No `listOf()`, `mapOf()`, `Pair()`, `Triple()`, `object : ...` expressions, or `buildList {}` directly in composition scope without `remember`

### 4. Check LazyList best practices

For every `LazyColumn`, `LazyRow`, or `LazyVerticalGrid`:

- `items()` calls use a `key` parameter (typically `key = { it.id }`)
- Item composables do not capture the entire list or parent state — only the individual item data
- `contentType` parameter is used when the list contains mixed item types (e.g. user messages vs bot messages vs loading indicators)

### 5. Push frequent changes to the drawing layer

Animated or frequently changing values should not trigger recomposition or relayout. Check for:

- **`Modifier.alpha()` / `Modifier.scale()` / `Modifier.rotate()`** driven by animated state: replace with `Modifier.graphicsLayer { alpha = ...; scaleX = ...; scaleY = ... }` which executes only in the draw phase, skipping composition and layout entirely
- **`Modifier.offset(x, y)` with animated values**: replace with the lambda variant `Modifier.offset { IntOffset(...) }` which skips relayout
- These only matter when the value changes frequently (animations, scroll-driven effects). Static values are fine as-is

### 6. Check ViewModel state patterns

For every ViewModel:

- State is exposed as `StateFlow` using `stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = ...)`
- No `MutableState` or `mutableStateOf` is used in the ViewModel
- State updates use `.update {}` or `.value = ...` on `MutableStateFlow`, not by directly mutating properties
- Lists emitted from the ViewModel use `.toImmutableList()` before being placed in the UI state

### 7. Report and fix

For each issue found, output a summary:

```
## Compose Performance — <N issues found | CLEAN>

### Stability issues
- [ ] <file>:<line> — <description>

### Recomposition issues
- [ ] <file>:<line> — <description>

### LazyList issues
- [ ] <file>:<line> — <description>

### Drawing layer issues
- [ ] <file>:<line> — <description>

### ViewModel issues
- [ ] <file>:<line> — <description>
```

Omit empty categories. After reporting, fix all issues automatically:

- Add missing `@Immutable` annotations (with the `androidx.compose.runtime.Immutable` import)
- Replace `List<>` with `ImmutableList<>` and `emptyList()` with `persistentListOf()` (adding imports for `kotlinx.collections.immutable.ImmutableList` and `kotlinx.collections.immutable.persistentListOf`)
- Wrap unremembered allocations in `remember {}`
- Add missing `key` parameters to `items()` calls
- Add `contentType` to mixed-type LazyColumn items when applicable
- Replace `Modifier.alpha()`/`.scale()`/`.rotate()` with `Modifier.graphicsLayer {}` when driven by animated values

Do NOT change code that is already correct. Do NOT refactor unrelated code.

## Scope

- Only scan Kotlin files containing Compose UI code (imports from `androidx.compose.*`) or state/ViewModel files used by Compose screens
- Do not flag platform-specific `expect`/`actual` declarations unless they directly affect Compose stability
- Do not flag test files
- When a `data class` is only used outside of Compose (e.g. network DTOs, database entities), do not require `@Immutable` on it
- `@Immutable` is preferred over `@Stable` in this project — only suggest `@Stable` if the class has intentionally mutable properties observed by snapshot state
- Ignore third-party library types (e.g. `PlatformFile`, `TextToSpeechInstance`) — their stability is outside our control
