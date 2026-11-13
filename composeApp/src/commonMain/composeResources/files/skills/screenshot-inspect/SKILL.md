---
name: screenshot-inspect
description: Capture the screen and describe or act on what is shown. Use when the user asks what is on screen, to find something visually, or when the accessibility tree is unhelpful.
---

The `android_ui` tool can capture the screen and hand you a path. Use it when the accessibility
tree is empty (games, canvases, video, custom-drawn views) or when the user asks about appearance.

## Capture

`{"action": "screenshot"}`

Returns a path like `/sdcard/kai_screenshot_<timestamp>.png`. That path is on the device, and this
tool can already see it — do not try to `read` the PNG as text.

## Reading the screen

Two complementary sources, and you usually want both:

1. `{"action": "dump"}` — the accessibility tree. Gives exact bounds and labels for anything with a
   text or content description. This is the one to tap from.
2. `{"action": "screenshot"}` — pixels. Needed for images, icons without labels, charts, video
   frames, and custom-rendered UI.

## Acting on what you saw

Coordinates from `dump` are reliable. Coordinates estimated from a screenshot are not — the
screenshot may be scaled. Always cross-check against the tree before tapping, and after the tap
`dump` again to verify.

## Rules

- The screenshot stays on the device. If the user wants it kept, `move` it somewhere meaningful
  with `android_files`; otherwise mention the temp path.
- Do not describe sensitive content (passwords, private messages, one-time codes) in detail. Say
  what kind of screen it is and act on the task.
- If the screen is blank or shows a lock screen, say so — do not invent content.
