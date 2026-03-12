Perform a release for Kai. Bump type argument: $ARGUMENTS (default: patch).

Follow these steps exactly:

## 1. Read current version

Read `gradle/libs.versions.toml`. Extract `appVersion` (line 3) and `android-versionCode` (line 4).

## 2. Calculate new version

Parse the bump type from the argument ($ARGUMENTS). If empty or not one of patch/minor/major, default to `patch`.

- **patch**: 1.8.2 → 1.8.3
- **minor**: 1.8.2 → 1.9.0
- **major**: 1.8.2 → 2.0.0

Increment `android-versionCode` by 1.

## 3. Generate changelog

Run:
```
git log --pretty=format:"- %s (%h)" v{current}..HEAD --no-merges
```

Filter out lines matching any of:
- `Auto-fix:`
- `[skip ci]`
- Lines that are exactly `- Release` or `- Release v...`
- Documentation-only changes (e.g. "Update docs", "Fix README", "Add feature spec")
- CI/workflow changes (e.g. "Update release.yml", "Fix CI", "Update AUR package", "Update Homebrew formula")

Only include changes that affect the app itself — code, UI, dependencies, build config that impacts the output artifact.

Additionally, collapse fix-up commits into the feature they belong to. If a feature was added and then fixed before this release (i.e. both commits are in the same range), the fix is not a standalone changelog entry — it's part of shipping the feature. Only list the feature itself. Similarly, don't list follow-up tweaks (e.g. "Add success feedback for import") separately if they refine a new feature in the same release.

From the remaining commits, write a **human-readable summary** grouped by category:
- **Features** — new capabilities
- **Fixes** — bug fixes
- **Improvements** — refactors, perf, UX polish, dependency updates

Omit empty categories. Each entry should be a concise one-liner (rewrite commit messages for clarity if needed). Do NOT include commit hashes in the final summary.

## 4. Write CHANGELOG.md

If `CHANGELOG.md` exists, prepend the new section. If not, create it.

Format:
```
## v{new} — {YYYY-MM-DD}

### Features
- ...

### Fixes
- ...

(then a blank line before existing content)
```

## 5. Write Play Store changelog

Write a condensed, user-facing summary to `fastlane/metadata/android/en-US/changelogs/{newVersionCode}.txt`.

- Plain text only — no markdown headers, no bullet markers
- One change per line — each entry on its own line, no blank lines between them
- Must be under 500 characters (Play Store limit)
- Each line should be a concise description, similar in style to existing entries like "Add identity configurations" or "Support additional languages"

## 6. Update gradle/libs.versions.toml

- Set `appVersion` to the new version
- Set `android-versionCode` to the incremented value

## 7. Commit, tag, and push

- Stage `CHANGELOG.md`, `gradle/libs.versions.toml`, and `fastlane/metadata/android/en-US/changelogs/{newVersionCode}.txt`
- Commit with message: `Release v{new}`
- Create annotated tag: `git tag -a v{new} -m "v{new}"`
- Push commit and tag to origin: `git push origin main --follow-tags`

## Important

- Do NOT use `--no-verify` or skip any hooks
- Show the user the changelog summary before committing so they can confirm
- If anything fails, stop and report the error — do not retry destructively
