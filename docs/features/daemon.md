# Daemon Mode

**Last verified:** 2026-05-14

Kai's daemon mode keeps the app running in the background on Android so that scheduled tasks, heartbeat checks, and email polling continue to execute even when the app is not in the foreground. On other platforms (desktop, iOS, web), daemon mode is a no-op.

## Concepts

### Daemon Controller

A platform-abstracted interface with `start()` and `stop()` methods. On Android, it manages an Android foreground service. On all other platforms, it does nothing.

### Foreground Service

An Android service that runs with a persistent notification, preventing the system from killing the process. Kai's service uses the `dataSync` foreground service type and `IMPORTANCE_LOW` notification priority to minimize user disruption.

## Service Lifecycle

1. When daemon mode is enabled in settings, the foreground service is started
2. The service creates a notification channel and displays a persistent "Daemon is running" notification
3. The service starts the task scheduler, which owns its own process-lifetime scope
4. The service returns `START_STICKY`, so Android restarts it if the system kills it
5. When daemon mode is disabled, the service stops itself and removes the foreground notification
6. On Android 12+, starting the service can silently fail with a `ForegroundServiceStartNotAllowedException` when the app isn't in the foreground; this is caught and ignored so the daemon can be retried later (e.g. on next app launch)

## Auto-Start

Every time the main activity is brought to the foreground, if daemon mode is enabled in settings, the service is (re)started. This runs on app launch as well as on every subsequent return to the foreground, so the daemon recovers from situations where OEM battery managers or aggressive task killers have terminated the service while the app was in the background.

## Background Work

The daemon's task scheduler polls every 60 seconds and handles four types of background work:

- **Scheduled tasks** — executes due tasks by sending their prompts through the AI pipeline
- **Heartbeat checks** — periodic self-checks during active hours (see heartbeat doc)
- **Email polling** — fetches new emails from configured accounts on a configurable interval
- **SMS polling** — checks for new incoming SMS messages on a configurable interval



## Notification

- **Channel**: "Kai 9000 Background Service" with low importance
- **Content**: "Daemon is running" with a sync icon
- **Tap action**: Opens the app's main screen
- The notification is required by Android for foreground services and cannot be hidden

## Permissions

The app declares two permissions in the Android manifest:

- `FOREGROUND_SERVICE` — required for all foreground services
- `FOREGROUND_SERVICE_DATA_SYNC` — required for the `dataSync` service type

No wake locks or battery optimization exemptions are requested. The service relies on Android's standard process management with `START_STICKY` for restart behavior.

## Settings UI

A toggle labeled "Daemon Mode" appears in the General tab of settings, only on Android. The description reads: "Keep Kai 9000 running in the background so scheduled tasks execute even when the app is not in the foreground." Toggling it starts or stops the foreground service and persists the preference. On Android 13+, turning the toggle on also requests the notification permission, since the foreground service's persistent notification cannot be displayed without it.

## Key Files

| File | Purpose |
|---|---|
| `composeApp/src/commonMain/.../DaemonController.kt` | Platform-independent interface |
| `composeApp/src/androidMain/.../DaemonController.android.kt` | Android implementation, start/stop/auto-start logic |
| `composeApp/src/androidMain/.../DaemonService.kt` | Android foreground service, notification, task scheduler startup |
| `androidApp/src/main/.../MainActivity.kt` | Auto-start (and recovery) on every foreground transition via `onStart` |
| `androidApp/src/main/AndroidManifest.xml` | Service declaration and permissions |
| `composeApp/src/commonMain/.../data/TaskScheduler.kt` | Background poll loop started by the service |
| `composeApp/src/commonMain/.../data/AppSettings.kt` | Daemon enabled state persistence |
| `composeApp/src/commonMain/.../ui/settings/SettingsScreen.kt` | Daemon mode toggle UI |
