# Credits: Eta

Kai's on-device agent tools were extended after studying [Eta](https://github.com/Mangi-11/Eta)
by 蛮吉 (Mangi-11), an Android system-level assistant released under the **GNU General Public
License, version 3** (Copyright © 2026 蛮吉 Mangi-11).

This file records exactly what was taken, because the two projects do not share a license: Kai is
**Apache-2.0**, Eta is **GPL-3.0**.

## What was taken: design, not code

**No source code from Eta is included in Kai.** Every line in the files listed below was written
against the Android platform APIs Kai already uses (`AccessibilityService`, `MediaStore`,
`UsageStatsManager`, `ClipboardManager`, `am`/`uiautomator` via Kai's own `PrivilegedShell`), and
none of it is a translation of an Eta source file.

What *was* taken is the design — the decisions about which actions a phone agent should offer,
what a good result looks like, and which claims a tool is not allowed to make:

| Design decision borrowed | Where it shows up in Kai |
| --- | --- |
| Long press is a first-class GUI action, separate from tap, because it opens the app's second layer (context menus, multi-select, drag) | `composeApp/src/commonMain/kotlin/com/kainext/duo/tools/uitree/UiAction.kt` (`LongPress`), `.../tools/uitree/UiActionExecutor.kt`, `.../tools/uitree/UiTreeShellFallback.kt` |
| Screenshots are passed at native resolution — never downscaled or re-encoded before the model sees them | `.../tools/DeviceControlTools.kt` (`AndroidUiTool.screenshot`) |
| Every action returns the UI state it produced, so the model does not have to re-read the tree to know what happened | `UiActionResult.sliceAfter` plus `toToolMap(includeNodes)` in `DeviceControlTools.kt` |
| "Launched" is not the same as "on screen": report the foreground package only after observing it, and say plainly when it was not observed | `AndroidUiTool` actions `launch`, `foreground`, `wait_for_window` |
| A write is reported as done only after reading it back; if the read-back disagrees, say so instead of claiming success | `.../tools/ContextTools.kt` (`clipboard_write` → `verified`) |
| Personal context is a set of small read-only actions grouped by data source, each stating the window it covers | `ContextTools.kt` actions `clipboard_read`, `photos_recent`, `apps_recent`, `device_state` |
| A missing permission and an empty result look identical, so tools must distinguish them | `photos_recent` / `apps_recent` `note` fields |

The grouping into one `android_context` tool, the parameter names, the result keys, the constants
(limits, windows, byte caps) and all Kai-specific behaviour (label-first node resolution, the
accessibility → shell → `uiautomator` fallback chain, DEX-free JSON results) are Kai's own.

## Why this is not a GPL-3.0 derivative work

Ideas, behaviour and API shape are not copyrightable expression; a program that is written
independently against the same platform APIs is an independent work. Kai therefore remains under
Apache-2.0 and can keep shipping under it, while Eta keeps its own GPL-3.0 terms.

If you intend to *copy* code from Eta into Kai, that changes: GPL-3.0 code cannot be relicensed
under Apache-2.0, so importing it would require Kai (at least the combined work) to be distributed
under GPL-3.0. This project deliberately did not do that — hence the rewrite rather than a port.

## Where this notice is shown

Eta is listed as a design reference in the notices the app displays under **Settings › Licenses**:

- `composeApp/src/commonMain/composeResources/files/licenses/THIRD_PARTY_NOTICES.txt`
  (section "Design references", which also includes the full GPL-3.0 text)
- `THIRD_PARTY_LICENSES.md`

Both state that no Eta code is bundled, so the "written offer for source" that applies to the
bundled GPL/LGPL binaries (PRoot, talloc) does not apply here.

## Reference

- Project: Eta — https://github.com/Mangi-11/Eta
- Author: 蛮吉 (Mangi-11)
- License: GPL-3.0 — https://www.gnu.org/licenses/gpl-3.0.html
- Studied for: device-control action set, result shape and verification rules, personal-context
  grouping. Consulted as documentation (README) and for behaviour; not as a code source.
