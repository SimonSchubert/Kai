---
name: media-control
description: Control music and video playback, volume streams, and media sessions on the device. Use for play/pause/next/volume requests.
---

Media control goes through `execute_shell_command` (media keys) or `android_ui` (in-app controls).

## Media keys

```sh
input keyevent 85      # KEYCODE_MEDIA_PLAY_PAUSE
input keyevent 87      # KEYCODE_MEDIA_NEXT
input keyevent 88      # KEYCODE_MEDIA_PREVIOUS
input keyevent 86      # KEYCODE_MEDIA_STOP
```

These act on whatever holds the active media session, so check first:

```sh
dumpsys media_session | grep -E 'package=|state=PlaybackState' | head -10
```

## Volume

```sh
media volume --stream 3 --get           # 3 = music, 5 = notifications, 2 = ring
media volume --stream 3 --set 10
media volume --stream 3 --adj raise
input keyevent 24                        # volume up
input keyevent 25                        # volume down
```

## Rules

- Confirm the target session before sending keys; otherwise you pause an audiobook when the user
  meant the video.
- If `dumpsys media_session` shows nothing, no app is playing — say so instead of sending keys.
- Volume changes are audible and disruptive. When the user says "quieter", change one step and
  report the new level rather than jumping to a guess.
