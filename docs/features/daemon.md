# Daemon Mode

**Last verified:** 2026-04-10

Kai's daemon mode keeps the app running in the background on Android so that scheduled tasks, heartbeat checks, and email polling continue to execute even when the app is not in the foreground. On other platforms (desktop, iOS, web), daemon mode is a no-op.

## Concepts

### Daemon Controller

A platform-abstracted interface with `start()` and `stop()` methods. On Android, it manages an Android foreground service. On all other platforms, it does nothing.

### Foreground Service

An Android service that runs with a persistent notification, preventing the system from killing the process. Kai's service uses the `dataSync` foreground service type and `IMPORTANCE_LOW` notification priority to minimize user disruption.

## Service Lifecycle

1. When daemon mode is enabled in settings, the foreground service is started
2. The service creates a notification channel and displays a persistent "Daemon is running" notification
3. A coroutine scope is created and the task scheduler is started within it
4. The service returns `START_STICKY`, so Android restarts it if the system kills it
5. When daemon mode is disabled, the service is stopped and the coroutine scope is cancelled

## Auto-Start

On app launch, if daemon mode was previously enabled (persisted in settings), the service is automatically started. This ensures the daemon survives app restarts without requiring the user to re-enable it.

## Background Work

The daemon's task scheduler polls every 60 seconds and handles three types of background work:

- **Scheduled tasks** — executes due tasks by sending their prompts through the AI pipeline
- **Heartbeat checks** — periodic self-checks during active hours (see heartbeat doc)
- **Email polling** — fetches new emails from configured accounts on a configurable interval



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

A toggle labeled "Daemon Mode" appears in settings only on Android. The description reads: "Keep Kai 9000 running in the background so scheduled tasks execute even when the app is not in the foreground." Toggling it starts or stops the foreground service and persists the preference.

## Key Files

| File | Purpose |
|---|---|
| `composeApp/src/commonMain/.../DaemonController.kt` | Platform-independent interface |
| `composeApp/src/androidMain/.../DaemonController.android.kt` | Android implementation, start/stop/auto-start logic |
| `composeApp/src/androidMain/.../DaemonService.kt` | Android foreground service, notification, coroutine scope |
| `androidApp/src/main/.../KaiApplication.kt` | Auto-start on app launch |
| `androidApp/src/main/AndroidManifest.xml` | Service declaration and permissions |
| `composeApp/src/commonMain/.../data/TaskScheduler.kt` | Background poll loop started by the service |
| `composeApp/src/commonMain/.../data/AppSettings.kt` | Daemon enabled state persistence |
| `composeApp/src/commonMain/.../ui/settings/SettingsScreen.kt` | Daemon mode toggle UI |
