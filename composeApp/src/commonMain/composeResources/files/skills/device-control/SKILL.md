---
name: device-control
description: Operate the phone UI directly - tap, swipe, type, press keys, launch apps, and read the screen via the accessibility tree. Use for any request that needs touching the screen rather than running a shell command.
---

You are driving the user's Android device through the `android_ui` tool. It works through any of
three backends: the accessibility service (no root needed), Shizuku, or root. If it returns a
privilege error, tell the user to enable one in Settings → Device control instead of retrying
blindly.

## The core loop: look, then act, then verify

Never guess coordinates. Every interaction follows the same three steps:

1. `{"action": "dump"}` — returns the accessibility tree with `bounds="[x1,y1][x2,y2]"` for each
   node. Find the node whose `text` or `content-desc` matches what you want.
2. Act on the node. **Prefer `tap_node`** — it resolves the label live and clicks the control,
   so it still works after a scroll or layout shift:
   `{"action": "tap_node", "target": "Wi-Fi"}`
   Only fall back to coordinates when no label exists:
   `{"action": "tap", "x": 540, "y": 1200}`
3. `{"action": "dump"}` again to confirm the screen changed. If nothing changed, the target moved
   or the element is not clickable — re-read the tree instead of tapping the same spot again.

## Actions

| Action | Parameters | Notes |
| --- | --- | --- |
| `dump` | — | Accessibility tree. Do this before every unfamiliar tap. |
| `tap_node` | `target` | **Preferred.** Tap by visible text, content description or resource id. |
| `tap` | `x`, `y` | Pixel coordinates. Fragile — use only when there is no label. |
| `swipe` | `x`, `y`, `x2`, `y2`, `duration_ms` | Scroll down = swipe from low y to high y. |
| `text` | `text` | Types into the focused field. Tap the field first. |
| `key` | `key` | `HOME`, `BACK`, `ENTER`, `POWER`, `VOLUME_UP`, `VOLUME_DOWN`, or a numeric keycode. |
| `launch` | `package`, `activity` (optional) | e.g. `com.android.settings`. |
| `current_app` | — | Which app is in front. Use it when you lose track. |
| `screenshot` | — | Saves a PNG under `/sdcard` and returns the path. |
| `wait` | `seconds` | For slow screens and animations. |
| `back` / `home` / `recents` | — | Navigation shortcuts. |

## Practical rules

- **Scrolling**: swipe `x` constant, `y` from ~75% of screen height to ~25% for a page down.
  Use `duration_ms` around 300; slower swipes fling further.
- **Text entry**: `input text` cannot type into a field that is not focused. Tap the field,
  `dump` to confirm the keyboard is up, then send `text`.
- **Waits**: after `launch` and after navigation, `wait` 1–2 seconds before `dump`, otherwise you
  read the previous screen.
- **Ambiguity**: when several nodes share a label, prefer the smallest `bounds` — the outer one is
  usually a non-clickable container. `tap_node` picks the first exact match, so make the target
  string specific when the screen repeats a word.
- **Backend**: `dump` reports which backend answered. `accessibility` is the live tree (fastest,
  most accurate); a shell `uiautomator` dump means the service is off, which is a hint to the
  user that enabling it in Settings makes taps more reliable.
- **Reporting**: after finishing, summarise what you did in one or two sentences. Do not dump raw
  trees into the reply; the user sees the tool card separately.

## Common requests

- "Open Settings and turn on Wi-Fi" → `launch com.android.settings` → `dump` → tap the Wi-Fi row →
  `dump` → tap the toggle.
- "Scroll down and screenshot" → `swipe` → `wait` → `screenshot`.
- "Go back to the home screen" → `{"action": "home"}`.

## The floating control window

While you operate the phone, a small window stays on screen showing what you are doing. The user
can watch progress, type a message into it, or press Stop. Two consequences:

- **You are being watched.** Keep status meaningful: `tap_node` with a real label reads better
  than a bare coordinate, and one tool call per step is easier to follow than a burst.
- **Stop is immediate.** If the user stops you, do not resume the same task; wait for a new
  instruction. A queued message typed during a run arrives as the next turn.
