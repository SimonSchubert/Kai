# Notifications

**Last verified:** 2026-04-28 (per-app filtering delegated to system Notification Access "Apps" picker)

> Reading notifications is **FOSS-only** and **Android-only**. The Play Store variant of Kai does not declare `BIND_NOTIFICATION_LISTENER_SERVICE` and the feature is invisible there — no settings, no tools, no code path. Play Store's notification-access policies restrict the listener to a narrow set of approved use cases (accessibility, smartwatches, replacement notification UIs), which Kai is not.

Kai on the FOSS Android build can **read** notifications posted by other apps and surface them to the AI, mirroring the SMS feature: per-app opt-in, capped pending queue, heartbeat summary, plus on-demand `check_notifications` / `read_notification` / `search_notifications` tools.

There is no "send" counterpart in v1. Acting on a notification (replying via `RemoteInput`, dismissing) is out of scope for the first cut and would follow the SMS-send draft pattern when added.

## Availability

- **FOSS Android build**: fully available.
- **Play Store Android build**: feature is invisible — `BIND_NOTIFICATION_LISTENER_SERVICE` is not declared in the Play flavor's merged manifest, the runtime support check returns false, the settings section is hidden, and the notification tools are never registered.
- **iOS / desktop / web**: unsupported. iOS does not allow third-party apps to read system notifications at all; desktop and web have no equivalent surface. No-op stubs.

The FOSS gate is purely manifest-based: the `foss` product flavor contributes `androidApp/src/foss/AndroidManifest.xml` declaring the listener service with `BIND_NOTIFICATION_LISTENER_SERVICE`, while the `playStore` flavor does not. At runtime the app queries `PackageManager.getPackageInfo(…, GET_SERVICES)` (or checks the merged-manifest service registration) to decide whether to show the feature.

## Scope

- **Read**: list / read / search notifications posted to the system tray since the listener was bound.
- **Per-app filtering is delegated to the OS.** System Notification Access already exposes an "Apps" picker per listener — if the user unchecks an app there, `onNotificationPosted` is never fired for that package. We don't duplicate that UI in Kai; the in-app toggle is just a master switch for the whole feature.
- **Visible notifications only.** Ongoing/foreground-service notifications (media controls, downloads, navigation) are filtered out — they are sticky UI affordances, not events.
- **No reply, no dismiss, no action invocation in v1.** The listener is read-only.
- **No content from secure / sensitive flag.** Notifications posted with `Notification.VISIBILITY_SECRET` are skipped. Posts marked sensitive by the system (lockscreen-redacted) are captured but flagged so the AI can choose to elide content.

## Opt-in flow

Notification access is granted via system settings, not a runtime permission dialog — there is no `requestPermissions` path for `BIND_NOTIFICATION_LISTENER_SERVICE`.

1. In **Settings → Agent → Notifications → "Read notifications"**, the user flips the toggle on.
2. The app deep-links to **Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS** (or the per-component variant on API 30+) and instructs the user to enable Kai in the list.
3. On return, the app polls `NotificationManager.isNotificationListenerAccessGranted(…)` (API 27+) or `Settings.Secure.getString("enabled_notification_listeners")`. If granted, the toggle stays on; if not, the toggle resets and an inline hint appears.
4. Once granted, Android binds `KaiNotificationListenerService`. From then on every `onNotificationPosted` callback that passes the visibility filters writes a record into the pending queue. The user can refine *which* apps Kai sees from the same system Notification Access screen — the "Apps" picker per listener is the source of truth.
5. On the next heartbeat, the queue snapshot is included in the prompt under `## New Notifications`. After the heartbeat run, exactly that snapshot is removed from the queue — notifications that arrived during the call survive to the next heartbeat.

If the user later revokes notification access from system settings, `onListenerDisconnected` fires, the support check flips to false, and the read tools stop appearing in the AI's available-tools list until access is re-granted.

## Per-app filtering

Per-app filtering is **the OS's job**. Android's system Notification Access screen, when opened on a specific listener, exposes an "Apps" picker that lets the user toggle which apps the listener can read. Kai's settings card includes a "Manage apps" button that deep-links straight there.

This was a deliberate simplification — earlier iterations of this feature shipped a Kai-side "Ignored apps" list, but the OS-level picker covers the same ground without duplicating UI or maintaining a parallel allowlist. A small set of packages is still **hard-blocked** at the listener callback (Kai itself, system UI) to avoid feedback loops, but everything else flows through whatever the system has approved.

## No polling interval

Unlike SMS and email, there is **no `pollIntervalMinutes`**. Notifications are push: the listener service receives `onNotificationPosted` callbacks the moment a notification fires, and writes synchronously to the store. The scheduler does not need a `checkNewNotifications` hook.

The heartbeat still drives the AI summarisation cadence — the pending queue accumulates between heartbeats and gets flushed when the heartbeat runs.

## Retention

The pending queue and the broader notification store are both bounded:

- **Pending queue capacity 100** (FIFO). Older notifications are dropped if the queue fills before a heartbeat consumes it. Identical to SMS.
- **Per-app age cap 24h.** A background sweep on listener bind + after each heartbeat drops records older than 24 hours from the broader store.
- **Per-app record cap 50.** Prevents one chatty app (e.g. a group chat) from monopolising the store. Oldest-first eviction.

Records are persisted in the encrypted app settings store alongside email/SMS pending so they survive process death.

## Captured fields

| Field | Source | Notes |
|---|---|---|
| `id` | `StatusBarNotification.key` | Stable across `post`/`update` for the same notification. Used as the read/search lookup key. |
| `package_name` | `StatusBarNotification.packageName` | Used for ignore-list matching. |
| `app_label` | `PackageManager.getApplicationLabel` | Best-effort; falls back to package name on lookup failure. |
| `title` | `extras["android.title"]` | Trimmed. |
| `text` | `extras["android.bigText"]` ?: `extras["android.text"]` | Big text preferred when present. |
| `subtext` | `extras["android.subText"]` | Optional. |
| `posted_at` | `StatusBarNotification.postTime` | Epoch ms. |
| `is_ongoing` | `Notification.flags & FLAG_ONGOING_EVENT` | Used to filter sticky notifications at capture time. |
| `category` | `Notification.category` | e.g. `msg`, `email`, `alarm`. |
| `preview` | First 200 chars of `text` | Shown in `check_notifications` and the heartbeat prompt. |

`MessagingStyle` notifications (group chats with multiple senders) are flattened: `extras["android.messages"]` is parsed and the preview shows the latest sender + text. Full per-message detail is available via `read_notification`.

## AI Tools

Registered in `getAvailableTools()` on Android only, gated on `isNotificationsSupported && isNotificationsEnabled && hasListenerAccess`:

| Tool | Purpose |
|---|---|
| `check_notifications` | List notifications currently in the heartbeat pending queue. Returns `id`, `package_name`, `app_label`, `title`, `posted_at`, `preview` for each. |
| `read_notification` | Fetch the full body of a single notification by `id` (the `StatusBarNotification.key`). |
| `search_notifications` | Full-text search over `app_label` + `title` + `text`, newest first, capped at 20. Optional `package_name` filter. |

The pattern intentionally mirrors the SMS read triplet so the AI can transfer mental model: "check for new things, read a specific one, or search by text."

## Heartbeat surface

The heartbeat prompt builder gains a `## New Notifications` section, formatted parallel to `## New Emails` / `## New SMS`:

```
## New Notifications
These notifications arrived since the last heartbeat. Summarise briefly; only flag items that genuinely need attention.
- **WhatsApp** — Alice (id: 0|com.whatsapp|...): Hey, are we still on for tonight?
- **Gmail** (id: 0|com.google.android.gm|...): New message from boss@…
```

Same lifecycle as the SMS pending queue: the snapshot is taken before the heartbeat, only that snapshot is removed afterward, anything that arrived during the call survives.

## Notifications

No notifications-specific push notification. New notifications surface via the existing heartbeat notification path: if the heartbeat produces a non-`HEARTBEAT_OK` response while Kai is backgrounded, the standard heartbeat notification fires and deep-links into the heartbeat conversation.

## Settings UI

The Notifications section appears in **Settings → Agent** only when `isNotificationsSupported` is true (FOSS build). One card with:

- **Read notifications** toggle — enabling deep-links to system notification-access settings; the toggle stays off until access is confirmed on return.
- **"Open notification access"** button — shown when the toggle is on but access has not been granted (or was revoked).
- **Listener status** — "Listener active" / "Listener inactive — check notification access".
- **"Manage apps"** button — deep-links to the same system Notification Access screen so the user can adjust which apps Kai can read.
- **Queued count + Clear queue** — number of notifications sitting in the pending queue waiting for the next heartbeat, with a button to flush them on demand.

There is **no poll interval slider** — the listener is push-driven.

## Key Files

| File | Purpose |
|---|---|
| `androidApp/src/foss/AndroidManifest.xml` | Declares `BIND_NOTIFICATION_LISTENER_SERVICE` and registers `KaiNotificationListenerService` in the FOSS flavor only |
| `composeApp/src/commonMain/.../data/NotificationModels.kt` | `NotificationRecord`, `NotificationSyncState` data classes |
| `composeApp/src/commonMain/.../data/NotificationStore.kt` | Pending queue + broader store + retention sweeps |
| `composeApp/src/commonMain/.../notifications/NotificationReader.kt` | Expect interface for `getById`, `search`, `currentRecords` |
| `composeApp/src/androidMain/.../notifications/NotificationReader.android.kt` | Reads from the in-memory + persisted listener store |
| `composeApp/src/androidMain/.../notifications/KaiNotificationListenerService.kt` | `NotificationListenerService` subclass; visibility/hard-block filter + write to `NotificationStore` |
| `composeApp/src/commonMain/.../tools/NotificationTools.kt` | `check_notifications`, `read_notification`, `search_notifications` tool definitions |
| `composeApp/src/commonMain/.../tools/NotificationListenerController.kt` | Expect interface for "is access granted" + "open settings" |
| `composeApp/src/androidMain/.../tools/NotificationListenerController.android.kt` | `NotificationManager.isNotificationListenerAccessGranted` + `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` deep-link |
| `composeApp/src/androidMain/.../Platform.android.kt` | `isNotificationsSupported` gate + conditional tool registration |
| `composeApp/src/commonMain/.../data/HeartbeatPromptBuilder.kt` | `## New Notifications` section renderer |
| `composeApp/src/commonMain/.../data/TaskScheduler.kt` | Heartbeat snapshot/remove lifecycle for the pending queue (no poll hook — listener is push) |
| `composeApp/src/commonMain/.../ui/settings/HeartbeatSection.kt` | `NotificationsSection` Compose UI with toggle + Manage apps deep-link |

## Future scope (not v1)

- **`reply_notification` tool.** Inspect the captured `Notification.actions` for a `RemoteInput`-bearing reply action; if present, expose a draft tool that mirrors `reply_sms` (banner-gated send). Useful for messaging apps.
- **`dismiss_notification` tool.** Call `NotificationListenerService.cancelNotification(key)`. Low-friction so could ship behind a separate "Allow dismissing" toggle.
- **OTP redaction.** Auto-detect 4–8 digit OTP-style codes in capture and elide them from the heartbeat prompt by default; let the AI request the full body via `read_notification` only when it's clearly a non-sensitive context.
- **Per-channel filtering.** Within an allowed app, let the user pick which `NotificationChannel` IDs to capture (e.g. allow Slack mentions, drop Slack reactions).
