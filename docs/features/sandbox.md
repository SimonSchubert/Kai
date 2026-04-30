# Linux Sandbox

**Last verified:** 2026-04-30

Kai ships a self-contained Alpine Linux environment on Android so the assistant — and the user, via the in-app Terminal — can run real shell commands. The agent can install packages, write and run scripts, hit the network, and reach external servers over SSH/SFTP/FTP. The sandbox runs the user-space `proot` runtime against an Alpine 3.21 minirootfs extracted into the app's private storage; no root or system access is required.

The sandbox is **Android-only**. iOS, desktop, and web stubs return "not ready" for every operation.

## Concepts

### Per-conversation shell sessions

Each chat conversation gets its own long-running `bash` process. The agent's shell tool routes through the conversation's shell, so working directory, exported environment variables, and any in-shell state carry from one tool call to the next within that chat — the way they do in any normal terminal. State does **not** leak across chats: `cd /tmp` in conversation A leaves conversation B sitting wherever it was. The in-app Terminal tab also has its own dedicated scratch shell, separate from any chat.

`/root` and the rest of the rootfs are still shared on disk across all sessions, so files an agent writes in one chat are visible to every other chat and to the Terminal tab. Only live shell state (cwd, exports, background `&` jobs, ssh-agent connections) is per-session.

So `cd /tmp` followed by `pwd` in the same chat returns `/tmp`. The assistant does not need to chain `cd dir && command` unless it specifically wants the directory change to be one-shot.

A shell is created lazily on first use and lives for the duration of the app process. When a conversation is deleted, its shell is closed. Sandbox reset closes every live shell.

### Terminal tab session picker

The in-app Terminal tab shows a chip row at the top whenever more than one shell is live. Each chip is one session: "Terminal" for the user's scratch shell, plus one chip per chat conversation that has used the shell tool, labelled with the conversation's title. Tapping a chip switches the visible terminal to that session — the running command (if any), output buffer, and input field swap to that shell while everything else keeps running in the background. The picker is in-memory only; chips disappear when the app process dies.

The agent's shell tool, `apk` operations from the Packages tab, and the Terminal scratch session each route to a distinct shell. A long-running `apk install` in the Packages UI no longer blocks the chat tool from running.

### Pre-installed tooling

The first-run install pulls a fixed set of packages: `bash`, `curl`, `wget`, `git`, `jq`, `python3` (with pip), `nodejs`, plus remote-server tooling — `openssh-client` (provides `ssh`/`scp`/`sftp`), `lftp` (FTP and FTPS), and `rsync`. Anything else is one `apk add` away.

`~/.ssh` is part of `/root`, which is bind-mounted to durable app storage, so SSH keys, `known_hosts`, and SSH config survive restarts.

### One-shot escape hatch

The assistant's shell tool accepts a `fresh: true` argument that runs the command in a brand-new short-lived `proot` instead of the persistent shell. State changes in that one-shot shell are discarded when it exits. The persistent session is the default; `fresh` is only there for the rare case where isolation matters.

### Background processes

`background: true` on the shell tool detaches the command into its own short-lived `proot` and returns a `session_id`. Background jobs do not share state with the persistent shell. The companion `manage_process` tool reports status, output, and lets the assistant kill them.

### Cancellation

Hitting **Cancel** in the Terminal — or any cancel signal coming from the chat — sends `SIGINT` to the running command (technically: every direct child of the persistent bash, delivered from a sibling proot, since Kai has no PTY to drive line discipline). If the process ignores `SIGINT`, the cancel escalates to `SIGTERM` then `SIGKILL`. If even that fails, the whole shell is reset; the next command transparently restarts a fresh bash. At most a single command loses session state.

### Self-healing

The shell session can break — the user types `exit`, a command crashes bash, the framing channel desyncs, or a per-call timeout expires with the shell still wedged. In every case the next command lazily starts a new shell. Working directory and exported env are lost in that one event; the system stays usable.

## Behavior

- **First run**: Settings → Tools → Linux Sandbox kicks off a download of the Alpine minirootfs (≈3 MB). After extraction, `apk update` runs against a list of mirrors, then the package set above is installed. The whole flow surfaces progress in the Settings sheet.
- **State across the app**: each chat conversation has its own shell, and the Terminal tab has another. Files in `/root` and the rest of the rootfs are shared between them; live shell state (cwd, exports) is not. The Packages UI uses a separate "system" shell so its operations don't interfere with chats.
- **Network access**: outbound IP works (DNS is configured against `8.8.8.8` / `8.8.4.4`). SSH/SFTP/FTP/HTTP all work; the user's Wi-Fi/mobile-data permission applies as normal.
- **File visibility**: `/root` lives at app-external storage so files the agent produces can be opened with `open_file` via Android's `FileProvider`. The rest of the filesystem (`/etc`, `/usr`, `/var`, etc.) is the Alpine rootfs and lives in app-internal storage.
- **Limits**: each shell call's stdout and stderr are individually capped at 15 000 characters; pipe through `head` / `tail` / `grep` for larger output. The default per-call timeout is 30 s and the maximum is 60 s.

## Limitations

- **No PTY → fullscreen TUIs do not work.** `vim`, `less`, `nano`, anything ncurses-based, `cbonsai` in animated mode, and any `ssh -t host fullscreen-cmd` will either refuse to start ("inappropriate ioctl for device" / "stdout is not a tty") or spam escape codes that don't render. Use the non-interactive variants: `cat`/redirected editors, `ssh user@host 'remote-cmd'` without `-t`. A proper PTY layer was prototyped and reverted — the build-out tradeoffs (terminal emulator complexity, IME interaction, scrollback) didn't pencil out for v1.
- **Process inspectors (`top`, `htop`) cannot see system-wide processes.** Android's `/proc` mount is `hidepid=2`, so `/proc/<pid>/` for processes owned by other UIDs is not visible. `proot` rewrites paths but can't bypass kernel UID enforcement. There is no fix without root. For workload monitoring inside the sandbox itself, use `ps`, `ps -p $$`, or `cat /proc/self/status`.
- **Subprocess stdout buffering.** `python3` / `node` / etc. fully buffer stdout when stdin is a pipe — output looks "stuck" until the buffer fills or the process exits. Use `python3 -u` or `stdbuf -o0 <cmd>` for interactive testing.
- **App backgrounding can end the session.** When Android kills the app process to reclaim memory, every `proot` (and therefore every bash) dies with it. On the next foreground use shells restart cleanly per conversation, but cwd, exported env, and any open SSH/SFTP connections are gone. There is no foreground service holding sessions alive — the tradeoff for not asking for that permission.
- **Memory cost of multiple sessions.** Each live shell is a `proot+bash` pair (tens of MB resident). Running many concurrent chats with shell-tool usage will accumulate sessions. There is no soft cap yet — closing a conversation drops its shell, sandbox reset drops them all.
- **Cancel without a PTY is best-effort.** A child that ignores `SIGINT`/`SIGTERM` forces a session reset; the user loses session state for that one command.
- **Stray output from backgrounded jobs** (`sleep 60 &` then "Done" later) can attach itself to whatever command is running when the kernel finally reports the exit. Matches normal terminal behavior.
- **iOS / desktop / web**: no sandbox. Calls return "not ready" until those platforms get their own runtime.

## Key Files

| File | Purpose |
| --- | --- |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/sandbox/LinuxSandboxManager.kt` | Owns the rootfs lifecycle, the proot binary path, the package-install list, and the session-keyed map of live persistent shells. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/sandbox/PersistentSandboxShell.kt` | Long-lived bash, sentinel-based command framing, graduated `SIGINT`/`SIGTERM`/`SIGKILL` cancel, self-healing on shell death. One instance per session id. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/SandboxController.kt` | Common surface; `executeCommand{,Streaming}` take a `sessionId`. `SandboxSessions` defines the well-known ids: `DEFAULT`, `SYSTEM`, `TERMINAL`. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/ConversationIdContext.kt` | `ConversationIdElement` coroutine-context element that threads the active conversation id from the chat layer down into tool execution without polluting `Tool.execute(args)`. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/sandbox/ProotExecutor.kt` | Low-level proot invocation — stream readers, stdin pipe, timeout-bounded one-shot execution. Used by the persistent shell, by package install, and by background jobs. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/sandbox/RootfsDownloader.kt` | Downloads Alpine rootfs, extracts the tarball, writes `resolv.conf` and `repositories`. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/SandboxController.android.kt` | Routes `executeCommand` and `executeCommandStreaming` through the persistent shell; one-shot fallbacks live alongside. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/tools/ShellCommandTool.kt` | The `execute_shell_command` tool the assistant calls. Description, `fresh` flag, env/working-dir wrapping. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/tools/ProcessManager.kt` / `ProcessManagerTool.kt` | Background-job lifecycle: detached one-shot proot, in-memory session table, status/kill controls. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/sandbox/SandboxSessionViewModel.kt` | Terminal-tab ViewModel: line buffer, run/cancel state, stream draining. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/settings/TerminalSheet.kt` | Visible terminal UI with command echo, color-coded streams, and an interactive input row. |
| `composeApp/src/iosMain/kotlin/com/inspiredandroid/kai/SandboxController.ios.kt`, `desktopMain/.../SandboxController.jvm.kt`, `wasmJsMain/.../SandboxController.wasmJs.kt` | NoOp stubs for non-Android platforms. |
