---
name: app-control
description: List, launch, force-stop, install, uninstall, and inspect Android apps by package name. Use when the user names an app rather than a screen action.
---

You manage installed apps through `execute_shell_command` (root/Shizuku) and `android_ui` for
launching. Work by package name — display names are ambiguous and localized.

## Finding the package name

Never guess. Resolve the display name to a package first:

```sh
pm list packages -3 | sed 's/package://'          # third-party apps only
pm list packages | grep -i <keyword>              # search all, case-insensitive
```

If the keyword is the app's visible name and nothing matches, the name may be localized or the
app may be a system component. Ask the user, or `android_ui` `dump` the launcher and read the
`package` from the node.

## Launching

Prefer the UI tool — it handles launcher categories correctly:

`{"action": "launch", "package": "com.example.app"}`

Fallback with an explicit activity when the app has no launcher icon:

```sh
monkey -p com.example.app -c android.intent.category.LAUNCHER 1
```

## Inspecting and controlling

```sh
dumpsys package com.example.app | grep -E 'versionName|firstInstallTime|lastUpdateTime'
pm path com.example.app                          # APK location(s)
am force-stop com.example.app                    # kill it
pm clear com.example.app                         # WIPE all app data — always confirm first
dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity'
```

## Installing and uninstalling

```sh
pm install -r /sdcard/Download/app.apk           # upgrade in place
pm uninstall com.example.app                     # user app
pm uninstall --user 0 com.example.app            # remove for this user (system apps)
```

`--user 0` disables rather than deletes a system app, and it is recoverable with
`pm install-existing`. Say so when you use it.

## Rules

- `pm clear` destroys user data with no undo. Confirm before running it.
- Uninstalling a system app can break the device. Only do it when asked explicitly.
- After `force-stop`, the app is gone from recents — tell the user rather than silently relaunching.
- Report the package name you acted on, so the user can verify it was the right app.
