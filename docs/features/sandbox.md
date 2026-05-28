# Linux Sandbox

**Last verified:** 2026-05-24

Kai ships a self-contained Alpine Linux environment on Android so the assistant — and the user, via the in-app Terminal — can run real shell commands. The agent can install packages, write and run scripts, hit the network, and reach external servers over SSH/SFTP/FTP. The sandbox runs the user-space `proot` runtime against an Alpine 3.21 minirootfs extracted into the app's private storage; no root or system access is required.

The sandbox is **Android-only**. iOS, desktop, and web have no-op stubs — sandbox operations are simply unavailable on those platforms.

## Concepts

### Per-conversation shell sessions

Each chat conversation gets its own long-running `bash` process. The agent's shell tool routes through the conversation's shell, so working directory, exported environment variables, and any in-shell state carry from one tool call to the next within that chat — the way they do in any normal terminal. State does **not** leak across chats: `cd /tmp` in conversation A leaves conversation B sitting wherever it was. The in-app Terminal tab also has its own dedicated scratch shell, separate from any chat.

`/root` and the rest of the rootfs are still shared on disk across all sessions, so files an agent writes in one chat are visible to every other chat and to the Terminal tab. Only live shell state (cwd, exports, background `&` jobs, ssh-agent connections) is per-session.

So `cd /tmp` followed by `pwd` in the same chat returns `/tmp`. The assistant does not need to chain `cd dir && command` unless it specifically wants the directory change to be one-shot.

A shell is created lazily on first use and lives for the duration of the app process. When a conversation is deleted, its shell is closed. Sandbox reset closes every live shell.

The bash *process* itself dies with the app — cwd and exported env do not survive. The visible *transcript* of the current chat's session is kept in memory with a hard cap of 500 lines per session, and persisted to the conversation (capped at roughly 10,000 characters of trimmed output — about a screen and a half of scrollback) so re-opening an old chat after a restart still shows the tail of what was on screen, even though running another command starts a fresh shell. Persistence is debounced (~500 ms) so rapid output bursts coalesce into a single write rather than thrashing storage.

### Terminal tab session picker

The in-app Terminal tab shows two chips at the top whenever a chat is open: **Session** for the current chat's shell, and **Temporary** for the user's scratch shell. **Session** sits first and is auto-selected when opening the terminal from a chat, so the user immediately sees what the agent is operating on; the visible terminal also scrolls to the most recent output (including any transcript restored from disk). **Temporary** drops to the standalone scratch shell that isn't tied to any chat — its transcript is in-memory only and clears when the app process dies. With no chat active, only the **Temporary** chip is shown (and the chip row collapses entirely when there's nothing to switch between).

The agent's shell tool, `apk` operations from the Packages tab, and the Terminal scratch session each route to a distinct shell. A long-running `apk install` in the Packages UI no longer blocks the chat tool from running.

### Pre-installed tooling

The first-run install pulls a fixed set of packages: `bash`, `curl`, `wget`, `git`, `jq`, `python3` (with pip), `nodejs`, plus remote-server tooling — `openssh-client` (provides `ssh`/`scp`/`sftp`), `lftp` (FTP and FTPS), and `rsync`. Anything else is one `apk add` away.

`~/.ssh` is part of `/root`, which is bind-mounted to durable app storage, so SSH keys, `known_hosts`, and SSH config survive restarts.

### SSH host configuration

After the package install completes, Kai seeds `~/.ssh/config` with a `Host *` defaults block: server keepalive (`ServerAliveInterval 30`, `ServerAliveCountMax 3`) so long-lived ssh tunnels and sftp sessions don't get killed by NAT timeouts, and `StrictHostKeyChecking accept-new` so the first connection to a host writes its key into `known_hosts` automatically without a `yes/no` prompt this shell can't answer (subsequent connections still reject *changed* keys — sane TOFU). The seeding step is idempotent and only writes if the defaults block is missing.

The agent has a dedicated **Configure SSH Host** tool that registers a named host alias (alias, hostname, optional user/port/identity file) by upserting a `Host` block into `~/.ssh/config`, optionally appending a line to `~/.ssh/known_hosts`. After registration, the agent drives ssh through the regular shell tool — `ssh myalias 'remote-cmd'`, `scp file myalias:`, etc. — with no flags. The tool never writes or uploads private keys; the user has to place those under `~/.ssh` separately. Repeated calls for the same alias replace the prior block in place, so configuration stays clean.

Password-only remotes are reachable too but openssh inside the sandbox can't answer interactive password prompts on its own (no PTY; ssh reads from `/dev/tty`, not stdin). The documented path is `apk add sshpass` once, then invoke as `sshpass -p '<password>' ssh <alias>` (or `-f <password-file>` to keep the password off the command line). The host alias config still applies — sshpass only supplies the password and fakes a PTY internally. Both tool descriptions point the agent at this pattern so it surfaces during normal SSH workflows.

**No connection multiplexing.** openssh's ControlMaster feature is intentionally not enabled. The mux protocol creates its control socket via the `link()` syscall (atomic create-or-fail), and Android's kernel-level `protected_hardlinks` policy plus SELinux for the `untrusted_app` domain refuses `link()` from app processes regardless of file ownership or mode. `proot` can't translate around it because the check enforces against the real Android uid. The verified symptom when ControlMaster was tried: `muxserver_listen: link mux listener … Permission denied` on every ssh invocation. Each ssh call therefore does a full TCP+auth handshake; there is no held-connection optimization in this sandbox. The alias config alone still removes per-call flag repetition, which is the real ergonomics win.

### One-shot escape hatch

The assistant's shell tool accepts a `fresh: true` argument that runs the command in a brand-new short-lived `proot` instead of the persistent shell. State changes in that one-shot shell are discarded when it exits. The persistent session is the default; `fresh` is only there for the rare case where isolation matters.

### Background processes

`background: true` on the shell tool detaches the command into its own short-lived `proot` and returns a `session_id`. Background jobs do not share state with the persistent shell. The companion `manage_process` tool reports status, output, and lets the assistant kill them.

### Cancellation

Hitting **Cancel** in the Terminal — or any cancel signal coming from the chat — sends `SIGINT` to the running command (technically: every direct child of the persistent bash, delivered from a sibling proot, since Kai has no PTY to drive line discipline). If the process ignores `SIGINT`, the cancel escalates to `SIGTERM` then `SIGKILL`. If even that fails, the whole shell is reset; the next command transparently restarts a fresh bash. At most a single command loses session state.

### Self-healing

The shell session can break — the user types `exit`, a command crashes bash, the framing channel desyncs, or a per-call timeout expires with the shell still wedged. In every case the next command lazily starts a new shell. Working directory and exported env are lost in that one event; the system stays usable.

## Behavior

- **Tool availability follows install state**: the assistant's sandbox tools (shell command, `manage_process`, and the SSH-host config tool) are advertised to the model only when the sandbox is actually installed (Ready) *and* the sandbox toggle is on. Before the sandbox is installed they are not sent at all — there is no point offering tools that can only return "not installed," and the enable/disable toggle is itself hidden until install completes. Once installed, the Alpine Linux card's switch is the on/off control.
- **First run**: Settings → Tools → Linux Sandbox kicks off a download of the Alpine minirootfs (a few MB — ~3.5 MB for aarch64, varies by architecture). After extraction, `apk update` runs against a list of mirrors, then the package set above is installed. The whole flow surfaces progress in the Settings sheet.
- **State across the app**: each chat conversation has its own shell, and the Terminal tab has another. Files in `/root` and the rest of the rootfs are shared between them; live shell state (cwd, exports) is not. The Packages UI uses a separate "system" shell so its operations don't interfere with chats.
- **Network access**: outbound IP works (DNS is configured against `8.8.8.8` / `8.8.4.4`). SSH/SFTP/FTP/HTTP all work; the user's Wi-Fi/mobile-data permission applies as normal.
- **File visibility**: `/root` lives at app-external storage so files the agent produces can be opened with `open_file` via Android's `FileProvider`. The rest of the filesystem (`/etc`, `/usr`, `/var`, etc.) is the Alpine rootfs and lives in app-internal storage.
- **Limits**: each shell call's stdout and stderr are individually capped at 15 000 characters; pipe through `head` / `tail` / `grep` for larger output. The default per-call timeout is 30 s and the maximum is 60 s.
- **Active-shell indicator**: each time a chat starts running a shell command via the agent's shell tool, the rounded background of the sandbox icon button in the chat top bar flashes once in the primary color (snap on, ~800ms fade out) so the user can see at a glance that the assistant kicked off a sandbox command.

## Limitations

- **No PTY → fullscreen TUIs do not work.** `vim`, `less`, `nano`, anything ncurses-based, `cbonsai` in animated mode, and any `ssh -t host fullscreen-cmd` will either refuse to start ("inappropriate ioctl for device" / "stdout is not a tty") or spam escape codes that don't render. Use the non-interactive variants: `cat`/redirected editors, `ssh user@host 'remote-cmd'` without `-t`. A proper PTY layer was prototyped and reverted — the build-out tradeoffs (terminal emulator complexity, IME interaction, scrollback) didn't pencil out for v1.
- **Process inspectors (`top`, `htop`) cannot see system-wide processes.** Android's `/proc` mount is `hidepid=2`, so `/proc/<pid>/` for processes owned by other UIDs is not visible. `proot` rewrites paths but can't bypass kernel UID enforcement. There is no fix without root. For workload monitoring inside the sandbox itself, use `ps`, `ps -p $$`, or `cat /proc/self/status`.
- **Subprocess stdout buffering.** `python3` / `node` / etc. fully buffer stdout when stdin is a pipe — output looks "stuck" until the buffer fills or the process exits. Use `python3 -u` or `stdbuf -o0 <cmd>` for interactive testing.
- **App backgrounding can end the session.** When Android kills the app process to reclaim memory, every `proot` (and therefore every bash) dies with it. On the next foreground use shells restart cleanly per conversation, but cwd, exported env, and any open SSH/SFTP connections are gone. The visible transcript of each chat's shell is persisted (trimmed tail) so the user still sees what was on screen, but live shell *state* is not. There is no foreground service holding sessions alive — the tradeoff for not asking for that permission.
- **Memory cost of multiple sessions.** Each live shell is a `proot+bash` pair (tens of MB resident). Running many concurrent chats with shell-tool usage will accumulate sessions. There is no soft cap yet — closing a conversation drops its shell, sandbox reset drops them all.
- **Cancel without a PTY is best-effort.** A child that ignores `SIGINT`/`SIGTERM` forces a session reset; the user loses session state for that one command.
- **Stray output from backgrounded jobs** (`sleep 60 &` then "Done" later) can attach itself to whatever command is running when the kernel finally reports the exit. Matches normal terminal behavior.
- **iOS / desktop / web**: no sandbox. Stubs are no-ops — calls return empty results (or are simply unsupported) until those platforms get their own runtime.

## Key Files

| File | Purpose |
| --- | --- |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/sandbox/LinuxSandboxManager.kt` | Owns the rootfs lifecycle, the proot binary path, the package-install list, and the session-keyed map of live persistent shells. Seeds new per-chat shells from the conversation's persisted transcript and pipes transcript snapshots back to `ConversationStorage` after each command. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/sandbox/SessionShell.kt` | Per-session facade over `PersistentSandboxShell`. Carries the live in-memory transcript, accepts an `initialLines` seed for restart restoration, and fires an `onChange` callback after each command so the manager can persist the tail. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/ConversationStorage.kt` | Conversation persistence. `updateShellTranscript(id, lines)` trims to ~10,000 chars total and writes the tail back into the conversation JSON. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/sandbox/PersistentSandboxShell.kt` | Long-lived bash, sentinel-based command framing, graduated `SIGINT`/`SIGTERM`/`SIGKILL` cancel, self-healing on shell death. One instance per session id. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/SandboxController.kt` | Common surface; `executeCommand{,Streaming}` take a `sessionId`. `SandboxSessions` defines the well-known ids: `DEFAULT`, `SYSTEM`, `TERMINAL`. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/ConversationIdContext.kt` | `ConversationIdElement` coroutine-context element that threads the active conversation id from the chat layer down into tool execution without polluting `Tool.execute(args)`. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/sandbox/ProotExecutor.kt` | Low-level proot invocation — stream readers, stdin pipe, timeout-bounded one-shot execution. Used by the persistent shell, by package install, and by background jobs. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/sandbox/RootfsDownloader.kt` | Downloads Alpine rootfs, extracts the tarball, writes `resolv.conf` and `repositories`. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/SandboxController.android.kt` | Routes `executeCommand` and `executeCommandStreaming` through the persistent shell; one-shot fallbacks live alongside. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/tools/ShellCommandTool.kt` | The `execute_shell_command` tool the assistant calls. Description, `fresh` flag, env/working-dir wrapping. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/tools/SshConfigureHostTool.kt` | The `ssh_configure_host` tool. Validates inputs, calls the config manager, returns an example invocation for the LLM. |
| `composeApp/src/jvmShared/kotlin/com/inspiredandroid/kai/sandbox/SshConfigManager.kt` | Pure-JVM writer for `~/.ssh/config` and `~/.ssh/known_hosts`. Owns the `# kai:<marker>:start/end` blocks (defaults + per-host) for idempotent upsert, file-mode lockdown, and the relative-to-`~/.ssh` identity-file resolution. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/tools/ProcessManager.kt` / `ProcessManagerTool.kt` | Background-job lifecycle: detached one-shot proot, in-memory session table, status/kill controls. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/sandbox/SandboxSessionViewModel.kt` | Terminal-tab ViewModel: line buffer, run/cancel state, stream draining. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/settings/TerminalSheet.kt` | Visible terminal UI with command echo, color-coded streams, and an interactive input row. |
| `composeApp/src/iosMain/kotlin/com/inspiredandroid/kai/SandboxController.ios.kt`, `desktopMain/.../SandboxController.jvm.kt`, `wasmJsMain/.../SandboxController.wasmJs.kt` | NoOp stubs for non-Android platforms. |
