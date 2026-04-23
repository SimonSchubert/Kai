# SMS

**Last verified:** 2026-04-23

> SMS is **FOSS-only** and **Android-only**. The Play Store variant of Kai does not declare `READ_SMS` or `SEND_SMS` and the feature is invisible there — no settings, no tools, no code path. Play Store's SMS/Call Log Permissions policy restricts both permissions to default SMS handlers, which Kai is not.

Kai on the FOSS Android build can **read** incoming SMS messages and **draft** outgoing SMS for the user to review and send. Read and Send are independent user opt-ins: the user can let Kai read SMS without also letting it send, and vice versa. Sending is always gated by an explicit user tap in a review banner — the AI never sends directly.

## Availability

- **FOSS Android build**: fully available. Read and Send are separate opt-ins.
- **Play Store Android build**: feature is invisible — neither `READ_SMS` nor `SEND_SMS` is declared in the Play flavor's merged manifest, the runtime support check returns false, the settings section is hidden, and the SMS tools are never registered.
- **iOS / desktop / web**: unsupported. No-op stubs.

The FOSS gate is purely manifest-based: the `foss` product flavor contributes `androidApp/src/foss/AndroidManifest.xml` declaring both `READ_SMS` and `SEND_SMS`, while the `playStore` flavor does not. At runtime the app queries `PackageManager.getPackageInfo(…, GET_PERMISSIONS).requestedPermissions` to decide whether to show the feature.

## Scope

- **Read**: list / read / search inbox SMS.
- **Send** (including replies): AI drafts a message; user must tap Send in the review banner before anything is actually transmitted. The AI cannot bypass the banner — the `send_sms` / `reply_sms` tools only *stage* a draft; the actual `SmsManager.sendTextMessage` call is initiated by the tap handler in the UI.
- **SMS only.** Multimedia/group messages (MMS) are out of scope.
- **Inbox only for reads.** Sent, draft, and outbox rows are filtered out of polls.
- **No contacts lookup.** Senders are shown as raw phone numbers. Adding contact-name resolution would require a separate `READ_CONTACTS` opt-in.

## Opt-in flow

### Read

1. In **Settings → Agent → SMS → "Read incoming SMS"**, the user flips the toggle on.
2. The app requests `READ_SMS` at runtime via the standard Android permission dialog.
3. If the user grants it, the toggle stays on and a seed poll runs: the current maximum inbox `_id` is recorded as the "already seen" high-water mark so existing messages are not dumped into the heartbeat pending queue.
4. From then on, each scheduler tick that satisfies the poll-interval gate calls the SMS poller, which reads all inbox rows with `_id` greater than the recorded high-water mark (capped at 50 per poll) and adds them to a capped FIFO pending queue (max 100 messages).
5. On the next heartbeat, the queue snapshot is included in the prompt under `## New SMS`. After the heartbeat run, exactly that snapshot is removed from the queue — messages that arrived during the call survive to the next heartbeat.

If the user later revokes `READ_SMS` from system settings, the next poll records `lastError = "Permission not granted"` and the read tools stop appearing in the AI's available-tools list until permission is re-granted.

### Send

1. In **Settings → Agent → SMS → "Send SMS on your behalf"**, the user flips the toggle on.
2. The app requests `SEND_SMS` at runtime.
3. If granted, the `send_sms` and `reply_sms` tools become available to the AI.
4. When the AI calls one of those tools, a [SmsDraft](../../composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/SmsModels.kt) is written to the persistent [SmsDraftStore](../../composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/SmsDraftStore.kt) — **no SMS is sent**. The tool result tells the AI the draft is waiting for user review.
5. A [PendingSmsBanner](../../composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/chat/composables/PendingSmsBanner.kt) appears at the top of the chat showing the draft's recipient and body, with **Send** and **Discard** buttons.
6. Tapping **Send** transitions the draft to `SENDING` state, calls `SmsManager.sendTextMessage` (multipart-aware for long bodies), and updates the draft to `SENT` or `FAILED` in place. Tapping **Discard** removes the draft without sending.
7. The draft store caps at 20 drafts to protect against runaway AI behavior.

## Polling interval

Settings UI offers the same preset buckets as email: **Never, 5m, 15m, 30m, 60m**. Default is 15 minutes. Setting it to 0 (Never) disables automatic polling; the user can still hit "Refresh now" in settings to force a one-shot poll.

Rate-limiting mirrors email: the scheduler compares `now - max(lastSync, lastAttempt)` against the interval, so a failing poll backs off at the configured interval instead of retrying every scheduler tick.

## Sync state

A single global sync state tracks:

- `lastSeenId` — the largest inbox `_id` that has been pushed into pending. SMS rowids are globally monotonic in the system SMS database, so a single high-water mark is sufficient even with multiple SIMs.
- `lastSyncEpochMs` — last successful poll.
- `lastAttemptEpochMs` — last attempted poll (success or failure).
- `unreadCount` — number of inbox rows fetched on the last poll that were not marked read by the system UI.
- `lastError` — human-readable error from the last failed attempt, or null.

## Pending queue

- Capacity 100 (FIFO). Older messages are dropped if the queue fills before a heartbeat consumes it.
- Persisted in the encrypted app settings store alongside email pending.
- Stores `id`, `address` (phone number), `date`, and a truncated `preview` (≤200 chars). The full body is fetched on demand by the `read_sms` tool — this keeps the encrypted-prefs JSON small and avoids duplicating message bodies the system already stores.

## AI Tools

Registered in `getAvailableTools()` on Android only. Read and send tools are independently gated.

**Read tools** (gated on `isSmsSupported && isSmsEnabled && hasReadPermission`):

| Tool | Purpose |
|---|---|
| `check_sms` | List messages currently in the heartbeat pending queue. Returns `id`, `from`, `date`, `preview`, `is_read` for each. |
| `read_sms` | Fetch the full body of a single SMS by `id`. |
| `search_sms` | Full-text search over inbox `address` + `body`, newest first, capped at 20. |

**Send tools** (gated on `isSmsSupported && isSmsSendEnabled && hasSendPermission`). Both *stage* drafts — neither sends directly:

| Tool | Purpose |
|---|---|
| `send_sms` | Draft an outgoing SMS to a phone number. Returns a `draft_id`. |
| `reply_sms` | Draft a reply to a received SMS by its `id` — sender looked up from the inbox. Returns a `draft_id`. |

## Notifications

No SMS-specific push notification. New SMS surface via the existing heartbeat notification path: if the heartbeat produces a non-`HEARTBEAT_OK` response while the app is backgrounded, the standard heartbeat notification fires and deep-links into the heartbeat conversation.

## Settings UI

The SMS section appears in **Settings → Agent** only when `isSmsSupported` is true (FOSS build). It contains two independent sub-toggles in one card:

**Read incoming SMS** (requires `READ_SMS`):

- Toggle — enabling it triggers the runtime permission request. If permission is denied, the toggle stays off.
- "Grant permission" button — shown when the toggle is on but permission has not been granted (or was revoked).
- Poll interval slider — Never / 5m / 15m / 30m / 60m.
- Queued count — number of SMS sitting in the pending queue waiting for the next heartbeat.
- Last poll status — "Last successful poll 5m ago" or "Last poll failed 1h ago" (with the error surfaced in the error color).
- Refresh icon — forces a one-shot poll (shows a spinner while polling).

**Send SMS on your behalf** (requires `SEND_SMS`):

- Toggle — enabling it triggers the runtime permission request. Gates the `send_sms` / `reply_sms` tool registration.
- "Grant permission" button — shown when the toggle is on but permission has not been granted (or was revoked).

## Key Files

| File | Purpose |
|---|---|
| `androidApp/src/foss/AndroidManifest.xml` | Declares `READ_SMS` and `SEND_SMS` in the FOSS flavor only |
| `composeApp/src/commonMain/.../data/SmsModels.kt` | `SmsMessage`, `SmsSyncState`, `SmsDraft`, `SmsDraftStatus` data classes |
| `composeApp/src/commonMain/.../data/SmsStore.kt` | Pending inbox queue + sync state persistence |
| `composeApp/src/commonMain/.../data/SmsDraftStore.kt` | Outgoing-draft persistence with status transitions |
| `composeApp/src/commonMain/.../sms/SmsReader.kt` | Expect interface for inbox queries |
| `composeApp/src/androidMain/.../sms/SmsReader.android.kt` | ContentResolver impl against `Telephony.Sms.CONTENT_URI` |
| `composeApp/src/commonMain/.../sms/SmsSender.kt` | Expect interface for outgoing SMS send |
| `composeApp/src/androidMain/.../sms/SmsSender.android.kt` | `SmsManager.sendTextMessage` (multipart-aware) |
| `composeApp/src/commonMain/.../sms/SmsPoller.kt` | Fetch-since-lastSeenId + pending write |
| `composeApp/src/commonMain/.../tools/SmsTools.kt` | `check_sms`, `read_sms`, `search_sms`, `send_sms`, `reply_sms` tool definitions |
| `composeApp/src/commonMain/.../tools/SmsPermissionController.kt` | `READ_SMS` runtime permission |
| `composeApp/src/commonMain/.../tools/SmsSendPermissionController.kt` | `SEND_SMS` runtime permission |
| `composeApp/src/androidMain/.../Platform.android.kt` | `isSmsSupported` gate + conditional tool registration (split read/send) |
| `composeApp/src/commonMain/.../data/TaskScheduler.kt` | `checkNewSms` poll hook + heartbeat snapshot/remove lifecycle |
| `composeApp/src/commonMain/.../data/HeartbeatPromptBuilder.kt` | `## New SMS` section renderer |
| `composeApp/src/commonMain/.../ui/settings/HeartbeatSection.kt` | `SmsSection` Compose UI with read + send sub-toggles |
| `composeApp/src/commonMain/.../ui/chat/composables/PendingSmsBanner.kt` | Top-of-chat review banner with Send/Discard buttons |
