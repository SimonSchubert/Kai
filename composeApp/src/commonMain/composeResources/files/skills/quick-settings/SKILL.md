---
name: quick-settings
description: Toggle Wi-Fi, Bluetooth, mobile data, airplane mode, brightness, rotation, and other system switches. Use for "turn on/off" requests about device radios and display.
---

System switches live behind `execute_shell_command` (root/Shizuku) or the Settings UI. Prefer the
shell: it is faster and deterministic. Fall back to `android_ui` when a command is rejected.

## Radio and connectivity

```sh
svc wifi enable          # or: svc wifi disable
svc data enable          # mobile data
svc bluetooth enable     # some builds: cmd bluetooth_manager enable
settings get global airplane_mode_on
settings put global airplane_mode_on 1   # 0 to disable
```

Toggling airplane mode also needs a broadcast on many builds:

```sh
am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true
```

## Display

```sh
settings get system screen_brightness
settings put system screen_brightness 128          # 0..255
settings put system screen_brightness_mode 1       # 1 = auto, 0 = manual
settings put system accelerometer_rotation 0       # 0 = lock rotation
settings put system user_rotation 1                # 0=0°, 1=90°, 2=180°, 3=270°
```

## Do Not Disturb, sound

```sh
settings get global zen_mode
cmd notification set_dnd on                        # or: off / priority
settings get system volume_music
media volume --stream 3 --set 8                    # stream 3 = music
```

## Rules

- **Read before you write.** `settings get` first, so you can report the previous value and restore
  it if the user asks.
- Some toggles need the screen on or the app in the foreground; if a command silently does nothing,
  fall back to `android_ui` (open Quick Settings, tap the tile).
- Airplane mode and mobile data affect connectivity — confirm before disabling on a device the user
  is actively using for a call or download.
- Report the final state, not the command: "Wi-Fi is now on."
