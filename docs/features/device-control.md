# Device Control (Privileged Shell, /sdcard, UI Automation)

**Last verified:** 2026-10-29

The Android build can grant the AI root or Shizuku (adb shell) privileges to run commands, read and write shared storage, and drive the phone's user interface. Every capability is off by default and gated behind its own switch.

## Concepts

### Privileged Backend

Two ways to obtain elevated privileges:

- **Root** — spawns `su -c <command>`. Works on rooted devices with a standard `su` binary; no extra app required.
- **Shizuku** — connects to the Shizuku app's binder and runs commands through `Shizuku.newProcess`, which gives adb shell (uid 2000) privileges after the user grants access. Requires the Shizuku app to be installed and running.

**Auto** mode prefers root, then Shizuku, then an unprivileged `sh` child of the app process — the last one is only useful for app-private paths and cannot read `/sdcard` under scoped storage.

### Capabilities

| Switch | Tool exposed to the AI | What it does |
|---|---|---|
| Shell commands | `android_shell` | Runs arbitrary shell commands (`am`, `pm`, `settings`, `dumpsys`, `input`, `getprop`, …) with a timeout and output cap. |
| /sdcard file access | `android_files` | `list`, `read`, `write`, `append`, `delete`, `move`, `mkdir`, `stat` under `/sdcard`, `/storage`, `/mnt/media_rw`. Path traversal (`/../`) is rejected. |
| Phone UI automation | `android_ui` | `dump` (condensed accessibility tree with node bounds and tap centers), `tap`, `swipe`, `text`, `key`, `launch`, `current_app`, `screenshot`, `wait`, `back`, `home`, `recents`. |

Files are read/written through the privileged shell rather than the app process, so `android_files` works even when the app itself lacks `MANAGE_EXTERNAL_STORAGE`.

## Behavior

- Settings → Device shows the master toggle, the privilege-source chips (Auto/Root/Shizuku), a live status row, and one switch per capability.
- Toggling the master on probes for root and Shizuku and reports which backend would be used, plus a "Grant Shizuku access" action when Shizuku is running without permission.
- Tool availability is decided by the master and per-capability switches; the three tools are not individually listed in the Tools tab, matching how sandbox tools are gated.
- UI automation is off by default because it can operate the whole phone; a model with the tool enabled can tap through any app.
- The manifest declares `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` (legacy), and `MANAGE_EXTERNAL_STORAGE` for app-side consumers, and registers the `ShizukuProvider` bridge.

## Key Files

| File | Purpose |
|---|---|
| `composeApp/src/commonMain/.../DeviceControl.kt` | `DeviceControlManager` expect declaration and status model |
| `composeApp/src/androidMain/.../DeviceControl.android.kt` | Probes root/Shizuku, reports the effective backend |
| `composeApp/src/androidMain/.../tools/PrivilegedShell.kt` | Root/Shizuku process spawning, timeout, output capture, shell quoting |
| `composeApp/src/androidMain/.../tools/DeviceControlTools.kt` | `android_shell`, `android_files`, `android_ui` |
| `composeApp/src/androidMain/.../Platform.android.kt` | Injects the tools into the agent tool set behind the toggles |
| `composeApp/src/commonMain/.../data/AppSettings.kt` | Master/per-capability toggles and privilege mode |
| `composeApp/src/commonMain/.../data/DataRepository.kt` | Probe/permission/toggle surface |
| `composeApp/src/commonMain/.../ui/settings/DeviceControlSettings.kt` | Settings tab UI |
| `androidApp/src/main/AndroidManifest.xml` | Storage permissions and Shizuku provider |
