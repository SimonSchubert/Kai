---
name: clipboard-notifications
description: Read and post notifications, and inspect the clipboard, using privileged shell access. Use for "what was that notification", "copy this", or "send me a notification" requests.
---

## Notifications

List what is currently posted:

```sh
dumpsys notification --noredact | grep -E 'pkg=|android.title=|android.text=' | head -40
```

Post a simple notification (the `send_notification` tool is cleaner when available):

```sh
cmd notification post -S bigtext -t 'Title' 'tag' 'Body text'
```

Dismiss all notifications from one app:

```sh
cmd notification cancel_all com.example.app
```

## Clipboard

```sh
cmd clipboard get            # Android 13+; may require foreground
```

Writing the clipboard from the shell is unreliable across versions. Prefer `android_ui`: focus the
target field and use the `text` action, which types the content directly.

## Rules

- Notification text often contains one-time codes, private messages, and account details. Summarise
  ("a bank notification arrived") and only quote content the user explicitly asked for.
- `--noredact` is needed to see real text, but it exposes sensitive data — use it only to answer the
  question at hand.
- If a command is unsupported on this Android version, say so rather than returning an empty result
  as if nothing were posted.
