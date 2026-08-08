# Linux Sandbox

**Last verified:** 2026-08-09

Kai ships a self-contained Linux environment on Android so the assistant — and the user, via the in-app Terminal — can run real shell commands. The agent can install packages, write and run scripts, hit the network, and reach external servers over SSH/SFTP/FTP. The sandbox runs the user-space `proot` runtime against a rootfs extracted into the app's private storage; no root or system access is required.

The sandbox is **Android-only**. iOS, desktop, and web have no-op stubs — sandbox operations are simply unavailable on those platforms.

## Concepts

### Choosing a distribution

Before the first install, the Settings card offers two:

- **Debian 12** — the recommended default. ~150 MB, apt, glibc, and the same Linux [Kai Build](kai-build.md) runs in.
- **Alpine Linux** — the small option. ~4 MB, apk, musl. Kai Build cannot use it, so picking Alpine means Kai Build installs a Debian of its own alongside.

The choice only decides what a *fresh* install becomes. Once a rootfs exists it records its own distribution, and that recording wins everywhere afterwards — package commands, the tool descriptions sent to the model, the name on the card and in the Terminal header. The picker disappears after install; switching means uninstalling and installing again.

Alpine is capped at 3.22 because 3.23+ ships apk-tools 3, which is incompatible with proot (`execveat`), so package installs would fail. Debian comes from the Linux Containers image index, architecture-matched, and needs proot's hardlink-to-symlink emulation because dpkg unpacks packages using hardlinks that Android refuses inside the app sandbox.

**Sandboxes installed before this choice existed are Alpine and stay Alpine.** They are recognised on sight, keep their `/root`, their SSH keys and their skills, and are never re-downloaded.

### One Linux, or two

When the sandbox is Debian — the default — the chat sandbox and Kai Build are the **same install**. One download, one rootfs, one uninstall: setting up Linux from Settings means opening Kai Build lands straight on the project list, and installing from Kai Build's setup screen leaves Settings reporting the sandbox as ready. Uninstalling from either surface removes it for both, and both confirmation dialogs say so. Project folders survive it.

Choosing Alpine splits them: the chat sandbox gets its Alpine, and Kai Build bootstraps a separate Debian the first time it is opened. Both can exist at once.

Package installs are serialized across the two, because a shared rootfs means the Settings "Install Packages" action and a Kai Build agent install could otherwise reach `dpkg` at the same moment and both fail on its lock.

### Per-conversation shell sessions

Each chat conversation gets its own long-running `bash` process. The agent's shell tool routes through the conversation's shell, so working directory, exported environment variables, and any in-shell state carry from one tool call to the next within that chat — the way they do in any normal terminal. State does **not** leak across chats: `cd /tmp` in conversation A leaves conversation B sitting wherever it was. The in-app Terminal tab also has its own dedicated scratch shell, separate from any chat.

`/root` and the rest of the rootfs are still shared on disk across all sessions, so files an agent writes in one chat are visible to every other chat and to the Terminal tab. Only live shell state (cwd, exports, background `&` jobs, ssh-agent connections) is per-session.

So `cd /tmp` followed by `pwd` in the same chat returns `/tmp`. The assistant does not need to chain `cd dir && command` unless it specifically wants the directory change to be one-shot.

A shell is created lazily on first use and lives for the duration of the app process. When a conversation is deleted, its shell is closed. Sandbox reset closes every live shell.

The bash *process* itself dies with the app — cwd and exported env do not survive. The visible *transcript* of the current chat's session is kept in memory with a hard cap of 500 lines per session, and persisted to the conversation (capped at roughly 10,000 characters of trimmed output — about a screen and a half of scrollback) so re-opening an old chat after a restart still shows the tail of what was on screen, even though running another command starts a fresh shell. Persistence is debounced (~500 ms) so rapid output bursts coalesce into a single write rather than thrashing storage.

### Terminal tab session picker

The in-app Terminal tab shows two chips at the top whenever a chat is open: **Session** for the current chat's shell, and **Temporary** for the user's scratch shell. **Session** sits first and is auto-selected when opening the terminal from a chat, so the user immediately sees what the agent is operating on; the visible terminal also scrolls to the most recent output (including any transcript restored from disk). **Temporary** drops to the standalone scratch shell that isn't tied to any chat — its transcript is in-memory only and clears when the app process dies. With no chat active, only the **Temporary** chip is shown (and the chip row collapses entirely when there's nothing to switch between).

The agent's shell tool, package-manager operations from the Packages tab, and the Terminal scratch session each route to a distinct shell. A long-running install in the Packages UI no longer blocks the chat tool from running.

### Pre-installed tooling

Packages split into two tiers, and each distribution names its own.

**Base** — installed as the last step of setup, with no separate action and no way to opt out. The install is not considered finished until they are in, so an interrupted setup can never present itself as ready. On Alpine that is just `bash`: every persistent shell session (the agent's shell tool, the Terminal tab, the Packages tab's own commands) is literally an `exec bash` process, so the sandbox cannot function without it. On Debian it is the wider set every coding project needs — bash, ca-certificates, curl, wget, git, nano, less, unzip, python3, tar and coreutils — because that install doubles as Kai Build's.

**Optional** — `jq`, `nodejs`, a Python package installer, plus remote-server tooling: `openssh-client` (provides `ssh`/`scp`/`sftp`), `lftp` (FTP and FTPS) and `rsync`. Alpine adds `curl`, `wget`, `git` and `python3` here since they are not in its base. These install only when the user taps **Install Packages** in Settings — a deliberate, separate action, never automatic.

Anything beyond the two tiers is one install away via the Packages tab. The tab never offers to uninstall a base package: those rows simply have no uninstall action.

`~/.ssh` lives under `/root`, which is durable app storage either way, so SSH keys, `known_hosts`, and SSH config survive restarts.

### Where `/root` lives

New installs keep `/root` **on the rootfs**, in app-internal storage, which is the only place Android reliably allows executing a file — that is what lets coding agents installed under `~/.local/bin` actually run. Only `/root/projects` is bound in from external app files, so project code stays reachable over USB/MTP.

Files anywhere in the tree are still openable: `open_file` and the in-app browser hand them to other apps through `FileProvider`, which is configured for both storage areas. What this layout gives up is browsing `/root` directly from a desktop file manager.

Sandboxes installed before the two Linux stacks merged keep the old arrangement — the whole of `/root` bound in from external storage — because relocating a user's files behind their back is not worth the tidiness. Their file browser still shows `/root` in the right place; it is grafted onto the listing rather than read out of the rootfs.

### SSH host configuration

After the package install completes, Kai seeds `~/.ssh/config` with a `Host *` defaults block: server keepalive (`ServerAliveInterval 30`, `ServerAliveCountMax 3`) so long-lived ssh tunnels and sftp sessions don't get killed by NAT timeouts, and `StrictHostKeyChecking accept-new` so the first connection to a host writes its key into `known_hosts` automatically without a `yes/no` prompt this shell can't answer (subsequent connections still reject *changed* keys — sane TOFU). The seeding step is idempotent and only writes if the defaults block is missing.

The agent has a dedicated **Configure SSH Host** tool that registers a named host alias (alias, hostname, optional user/port/identity file) by upserting a `Host` block into `~/.ssh/config`, optionally appending a line to `~/.ssh/known_hosts`. After registration, the agent drives ssh through the regular shell tool — `ssh myalias 'remote-cmd'`, `scp file myalias:`, etc. — with no flags. The tool never writes or uploads private keys; the user has to place those under `~/.ssh` separately. Repeated calls for the same alias replace the prior block in place, so configuration stays clean.

Password-only remotes are reachable too but openssh inside the sandbox can't answer interactive password prompts on its own (no PTY; ssh reads from `/dev/tty`, not stdin). The documented path is installing `sshpass` once (`apk add` or `apt-get install`, per the installed distribution), then invoke as `sshpass -p '<password>' ssh <alias>` (or `-f <password-file>` to keep the password off the command line). The host alias config still applies — sshpass only supplies the password and fakes a PTY internally. Both tool descriptions point the agent at this pattern so it surfaces during normal SSH workflows.

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

- **Tool availability follows install state**: the assistant's sandbox tools (shell command, `manage_process`, and the SSH-host config tool) are advertised to the model only when the sandbox is actually installed (Ready) *and* the sandbox toggle is on. Before the sandbox is installed they are not sent at all — there is no point offering tools that can only return "not installed," and the enable/disable toggle is itself hidden until install completes. Once installed, the switch on the distribution's card is the on/off control.
- **First run**: Settings → Tools → Linux Sandbox picks a distribution and downloads its rootfs — a few MB for Alpine, around 150 MB for Debian, varying by architecture. After extraction the package index is refreshed (Alpine walks its mirror list rewriting `repositories` until one answers; Debian has a single index) and the base packages install automatically — no further action needed. The sandbox reaches Ready at that point, with the optional package set not yet installed; the Settings card shows a separate **Install Packages** button for those until the user taps it. The whole flow surfaces progress in the Settings sheet.
- **State across the app**: each chat conversation has its own shell, and the Terminal tab has another. Files in `/root` and the rest of the rootfs are shared between them; live shell state (cwd, exports) is not. The Packages UI uses a separate "system" shell so its operations don't interfere with chats.
- **Network access**: outbound IP works (DNS is configured against `8.8.8.8` / `8.8.4.4`). SSH/SFTP/FTP/HTTP all work; the user's Wi-Fi/mobile-data permission applies as normal.
- **File visibility**: the whole filesystem, `/root` included, is the rootfs in app-internal storage; `/root/projects` and `/tmp` are bound in from elsewhere. Files the agent produces can be opened with `open_file` via Android's `FileProvider`, which is configured for both areas.
- **Tapping a file opens it here unless its name says otherwise**: the browser hands a file to another app only for the extensions nothing good comes of showing as text — images, video, audio, documents, archives, compiled binaries, databases, fonts. Everything else opens in the built-in editor, including files with no extension at all (`id_rsa`, `Makefile`, `known_hosts`) and extensions no list would ever cover (`.pub`, `.service`, `.rules`), which is most of what a Linux tree is made of. The editor then decides from the bytes rather than the name: valid UTF-8 becomes an editable buffer, anything else gets the "not text" card with its two ways out. Guessing "text" wrong costs one tap; guessing "binary" wrong used to mean the file could not be read in the app at all.
- **Open as text, on any file**: the row menu offers it next to "Open with app", so an extension the browser routes elsewhere — or one Android grabs — can still be read here. It goes through the same byte check, so a real binary lands on the "not text" card rather than pretending to be a document.
- **Handing a file to another app picks the right one**: whether the file is opened with the row menu or handed over by the agent, the app offered is chosen from the extension. Android's own extension table is corrected where it does not fit a Linux sandbox — most visibly for `.apk`, which it does not know at all, so tapping one now offers to install it (FOSS builds only; see [tools.md](tools.md)). Extensions Android has never heard of fall back to plain text when they look like source or config, which is most of what the sandbox produces.
- **Installed skills live here**: skills (see [skills.md](skills.md)) are stored as folders under `~/skills/<id>/` in the sandbox home — the sandbox is their single source of truth, which is why the Skills UI is Android-only and requires the sandbox to be installed first.
- **Tapping a sandbox-file link in chat**: when the model links to a sandbox file (e.g. a `file:///root/out.gif` markdown link), tapping it opens the file through the same `FileProvider` path rather than the platform URL handler. Handing a raw `file://` URI to Android's `startActivity` throws `FileUriExposedException`, so a global URI handler intercepts `file:` and absolute-path links and routes them to `open_file`; all other links (http/https/…) pass through unchanged.
- **Limits**: each shell call's stdout and stderr are individually capped at 15 000 characters; pipe through `head` / `tail` / `grep` for larger output. The default per-call timeout is 30 s and the maximum is 60 s.
- **Active-shell indicator**: each time a chat starts running a shell command via the agent's shell tool, the rounded background of the sandbox icon button in the chat top bar flashes once in the primary color (snap on, ~800ms fade out) so the user can see at a glance that the assistant kicked off a sandbox command.
- **Inline sandbox view**: the chat top-bar terminal icon toggles an inline sandbox surface (Android only) with sub-tabs — **Terminal** (interactive shell, default), **Files** (browse `/root`, open via FileProvider or a built-in text editor with Save), and **Packages** (search/install/uninstall/upgrade through the installed distribution's package manager; base packages cannot be uninstalled). Package search re-ranks hits so name matches come first: exact name, name prefix (typing `fast` surfaces `fastfetch` before packages that only mention "fast" in a description), then hyphen/underscore segment prefixes, then name contains, then description-only hits.
- **The file browser is shared with Kai Build**: the same browser — listing, breadcrumbs, editor, rename/delete, open-with — is pointed at whichever Linux environment the surface belongs to. Both browse their environment from the filesystem root and differ only in where they open: the home folder here, the open project in Kai Build. The two never share a position or an open file.
- **Bringing a file in from the phone**: the Files tab has an import action that copies a file picked from device storage into the directory currently on screen. Before this the only route into the sandbox was the agent's shell. The copy is streamed rather than buffered, so importing something large does not risk the app's memory, and an existing name is never overwritten — the incoming file is suffixed (`report-1.pdf`) instead. The action is offered only while the listing is showing, since it targets the visible directory; a busy indicator replaces it for the duration and the listing picks up the new file when it lands.
- **Why a file won't open in the editor**: the editor distinguishes the reasons rather than lumping them together. A file past the editor's size cap (512 KB) says so and reports its size, offering only to hand it to another app — a truncated buffer is not something that can be edited back safely. A file that is not valid UTF-8 can be force-opened, which decodes it with replacement characters for viewing and marks the buffer read-only; saving is withheld there, because writing those replacement characters back would destroy the file's real bytes. Anything the sandbox cannot read at all says that plainly. A NUL byte counts as "not text" before a decode is even attempted — it is legal UTF-8 and effectively never appears in real text.
- **The file browser refreshes itself**: the agent writes to the sandbox through the shell, so the browser cannot assume its listing is still current. Every time the Files tab becomes visible — selecting the sub-tab, opening the sandbox surface, or returning to the app from the background — the directory the user left off in is listed again, and re-entering a directory visited earlier lists it fresh rather than reusing what was shown before. Leaving the tab does not send the browser home: it stays where it was, and only the first open (or a surface that changed which folder it opens on) goes to the starting directory. The refresh is silent: entries stay on screen while it runs, and when the directory turns out to be unchanged nothing is redrawn, so the list never jumps or loses its scroll position. A file open in the built-in editor is also re-read, but only while the buffer has no unsaved edits — pending edits always win, which prevents a later Save from overwriting what the agent wrote.

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
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/sandbox/LinuxSandboxManager.kt` | Owns the sandbox's install marker (distro + where `/root` lives), the optional-package install, and the session-keyed map of live persistent shells. Seeds new per-chat shells from the conversation's persisted transcript and pipes transcript snapshots back to `ConversationStorage` after each command. `refreshInstallState()` is how it learns Kai Build installed the shared Debian. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/sandbox/SessionShell.kt` | Per-session facade over `PersistentSandboxShell`. Carries the live in-memory transcript, accepts an `initialLines` seed for restart restoration, and fires an `onChange` callback after each command so the manager can persist the tail. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/ConversationStorage.kt` | Conversation persistence. `updateShellTranscript(id, lines)` trims to ~10,000 chars total and writes the tail back into the conversation JSON. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/sandbox/PersistentSandboxShell.kt` | Long-lived bash, sentinel-based command framing, graduated `SIGINT`/`SIGTERM`/`SIGKILL` cancel, self-healing on shell death. One instance per session id. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/SandboxController.kt` | Common surface; `executeCommand{,Streaming}` take a `sessionId`. `SandboxSessions` defines the well-known ids: `DEFAULT`, `SYSTEM`, `TERMINAL`. `SandboxStatus.distro` carries the installed distribution to every UI and tool. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/sandbox/SandboxPackagesViewModel.kt` / `SandboxPackagesScreen.kt` | Packages tab. Every command and every parser comes from the installed distro's `PackageManagerSpec`; the UI is the same either way. Gates uninstall against the distro's base packages. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/FileBrowserSource.kt` | The browsable-tree contract both Linux environments implement, so the Files UI is not tied to this sandbox. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/sandbox/SandboxFileBrowserViewModel.kt` | Files tab state: directory listing, navigation, the built-in text editor, and rename/delete. Owns the refresh-on-visible behavior — re-lists silently, holds the directory the user left off in, skips the state update when nothing changed so the list keeps its scroll, drops a listing that resolves after the user navigated away, and re-reads the open file only while its buffer is clean. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/sandbox/SandboxFileBrowserScreen.kt` | Files tab UI, rooted wherever the caller asks: breadcrumb path bar (which never offers a step above that root), keyed entry list, per-entry rename/delete/open menu, and the editor pane. Triggers the refresh whenever the browser resumes. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/SandboxUriHandler.kt` | `UriHandler` provided over `LocalUriHandler` in `App.kt`; routes `file:`/absolute-path links to `SandboxController.openFile`, delegates the rest to the platform handler. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/ConversationIdContext.kt` | `ConversationIdElement` coroutine-context element that threads the active conversation id from the chat layer down into tool execution without polluting `Tool.execute(args)`. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/sandbox/ProotExecutor.kt` | The chat sandbox's line-oriented view of a rootfs, over the shared `ProotLauncher`. Returns the map shape the shell tool and background jobs consume. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/linux/LinuxDistro.kt` | The two distributions, their base/optional/protected package sets, and which package manager each uses. Also the default (Debian) and what a marker-less install counts as (Alpine). |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/linux/PackageManagerSpec.kt`, `ApkPackageManager.kt`, `AptPackageManager.kt` | Commands and output parsers per package manager — install/remove/search/list/upgrade, plus what counts as an error and how many packages an upgrade replaced. Pure and unit-tested. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/linux/LinuxPaths.kt` | Storage layout for one install: rootfs, tmp, projects bind, proot and libtalloc, and the install marker (including adopting the legacy layouts). |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/linux/LinuxInstaller.kt` | Download → extract → configure → base packages, for either distribution, cancellable and leaving no partial rootfs. Owns the process-wide package lock the two features share. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/linux/DistroSpec.kt` | Per-distribution facts: architecture names, rootfs URLs (Alpine mirror list, Debian LXC index), post-extract fixes, and the proot flags and environment each needs. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/linux/RootfsDownloader.kt` / `TarExtractor.kt` | One download path with mirror fallback, one tar reader handling both `.tar.gz` and `.tar.xz`. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/linux/ProotLauncher.kt` | proot argv, binds and environment, plus the shared `ProotHandle` and one-shot `execute`. Both this sandbox and Kai Build's PTY executor start their processes here. |
| `composeApp/src/jvmShared/kotlin/com/inspiredandroid/kai/linux/GuestPath.kt` | Guest path → host file, following the binds, for both file browsers. Rejects traversal and anything resolving outside its root. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/SandboxController.android.kt` | Routes `executeCommand` and `executeCommandStreaming` through the persistent shell; one-shot fallbacks live alongside. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/tools/ShellCommandTool.kt` | The `execute_shell_command` tool the assistant calls. Description, `fresh` flag, env/working-dir wrapping. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/tools/SshConfigureHostTool.kt` | The `ssh_configure_host` tool. Validates inputs, calls the config manager, returns an example invocation for the LLM. |
| `composeApp/src/jvmShared/kotlin/com/inspiredandroid/kai/sandbox/SshConfigManager.kt` | Pure-JVM writer for `~/.ssh/config` and `~/.ssh/known_hosts`. Owns the `# kai:<marker>:start/end` blocks (defaults + per-host) for idempotent upsert, file-mode lockdown, and the relative-to-`~/.ssh` identity-file resolution. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/tools/ProcessManager.kt` / `ProcessManagerTool.kt` | Background-job lifecycle: detached one-shot proot, in-memory session table, status/kill controls. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/sandbox/SandboxSessionViewModel.kt` | Terminal-tab ViewModel: line buffer, run/cancel state, stream draining. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/settings/TerminalSheet.kt` | Visible terminal UI with command echo, color-coded streams, and an interactive input row. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/SandboxController.kt` (`NoOpSandboxController`) | The single no-op every non-Android target returns; iOS, desktop and wasm each supply only a one-line factory. |
