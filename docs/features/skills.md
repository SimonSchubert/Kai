# Skills

**Last verified:** 2026-03-21

The skills system lets the AI create, store, and execute reusable JavaScript scripts. Over time, the agent builds a library of custom capabilities — a "database of skills" — that persist across sessions.

## How It Works

1. The AI writes a JavaScript (ES2023) script for a specific task
2. The script is saved with a name, description, optional readme, and optional data
3. On future requests, the AI can look up and execute saved skills
4. Skills are listed in the system prompt so the AI knows what's available
5. Skills can call other skills via `skill.run(name, input)`

## Skill Structure

Each skill has:
- **name** — unique identifier (e.g. `fetch_weather`)
- **description** — what the skill does
- **script** — JavaScript source code
- **readme** — markdown documentation (usage examples, notes)
- **dataJson** — JSON configuration accessible as `data` variable at runtime

## Execution Engine

- **Android & Desktop**: QuickJS via quickjs-kt (lightweight, ~1.2 MB)
- **iOS**: Built-in JavaScriptCore framework
- **Web**: Not supported (returns error)

## Script Bindings

### Variables
- `input` — always available, contains the value passed via skill_execute (null if not provided)
- `data` — the skill's stored JSON config (null if not set)

### Network
- `fetch(url)` — async GET request, returns `{ok, status, body, json(), text()}`
- `fetch(url, {method, headers, body})` — async request with options

### File System
- `fs.readFile(path)` — read file contents as string
- `fs.writeFile(path, content)` — write string to file (creates parent dirs)
- `fs.exists(path)` — check if file exists (returns boolean)
- `fs.listDir(path)` — list directory contents

### Inter-skill Calls
- `skill.run(name, input?)` — execute another skill and return its output
- Maximum call depth: 5 levels (prevents infinite recursion)

### Platform Support
- **Android & Desktop**: All bindings available
- **iOS**: `input` and `data` available; fetch, fs, skill.run not yet available
- **Web**: Not supported

## AI Tools

| Tool | Purpose |
|------|---------|
| skill_create | Create or update a skill (with optional readme and data) |
| skill_list | List all saved skills with usage stats |
| skill_execute | Run a skill by name with optional input |
| skill_update_data | Update a skill's JSON data without changing the script |
| skill_delete | Remove a saved skill |

## Settings

The feature is disabled by default. The Skills tab in Settings shows all saved skills with expandable cards displaying: documentation, data, input field + run button, execution result, and source code.

## Key Files

| File | Purpose |
|------|---------|
| `data/SkillEntry.kt` | Data model and execution result |
| `data/SkillStore.kt` | Persistent CRUD storage |
| `data/SkillExecutor.kt` | Platform-specific script execution |
| `tools/SkillTools.kt` | Agent-facing tool definitions |
