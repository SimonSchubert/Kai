# Kai Build

**Last verified:** 2026-08-09

Kai Build is an **Android-only** coding environment inside the Kai app. It is a separate *surface* from chat — no shared conversation store, no sandbox tools, no provider/settings coupling — but it is not necessarily a separate *Linux*: when the chat sandbox also runs Debian, both work in the same install.

It is a **single screen with three states** — set up Linux, pick a project, work in that project's terminal.

## Concepts

### Separate product surface

Kai Build is opened from the **empty chat state** — an "Open Kai Build" button next to "Start Interactive UI", shown on Android only. It then takes over the whole screen the same way Interactive UI mode does: no navigation destination, no second launcher icon, and no place in the back stack. A close button in its top bar and the system back gesture both return to the chat, leaving any drafted message intact. Inside a project, the same top-bar button and system back step back to the project list instead. The mode is not persisted across app restarts; it survives rotation only.

### Which Linux it runs in

Kai Build is always Debian: its coding agents are vendor scripts that expect glibc and apt.

Debian has exactly one install on the device, normally under app-private `kai-build/`. Kai Build works in it unconditionally, and never moves. What varies is whether the chat [Linux sandbox](sandbox.md) is pointed at the same install:

- **Shell integration is Debian** (the default) — both use the *same* install. Whichever surface installs it first, the other finds it already there: set Linux up in Settings and Kai Build opens straight on the project list; install from Kai Build's setup screen and the sandbox reports itself ready. Uninstalling from either removes it for both, and both dialogs say so.
- **Shell integration is Alpine** — that runs in a separate Alpine under app-private `linux-sandbox/`, and the two coexist.

The chat sandbox can be switched between the two at any time from Settings, and neither install is touched when it happens — so a Kai Build project terminal is never disturbed by that choice. The sandbox can also copy a home directory across when switching, but never the coding agents: their binaries are built for one distribution's libc, and Kai Build installs them per environment. (A Debian installed by an earlier build that put it under `linux-sandbox/` is found and used where it is, rather than downloaded again.)

Either way `/root` (agent binaries and config) is on the rootfs, and an external-files folder is bind-mounted only to `/root/projects`, so project code stays USB/MTP reachable without putting executables on storage that Android often mounts noexec. That projects folder is the same one in every arrangement, so projects are unaffected by the sandbox's distribution.

### Set up Linux

First run shows a single setup card: a beta notice, a short explanation, a checkbox per coding agent, and one **Install Debian** button. The setup title and description mark the feature as beta. Install downloads a Debian Bookworm rootfs from the Linux Containers image index (architecture-matched: arm64, armhf, amd64, i386), extracts it, configures DNS, then runs `apt-get update` and installs the essentials every project needs — bash, ca-certificates, curl, wget, git, nano, less, unzip, python3, tar and coreutils. Any agents ticked beforehand are installed straight after, so a fresh system arrives ready to use. If a Debian is already installed — because the chat sandbox put one there — this step is skipped entirely and only the ticked agents are installed.

Progress replaces the install button (download percent, extract, configure, base packages, per-agent), with Cancel next to it. Cancelling or failing deletes the partial rootfs so the retry starts clean; a failure message stays on screen. A Debian install is only considered complete once its base packages are in, so an interrupted install can never present itself as ready.

LXC images ship `etc/resolv.conf` as a symlink into systemd-resolved under `/run`, which does not exist under proot. Install replaces that link with a plain file pointing at public DNS (`8.8.8.8` / `8.8.4.4`) so apt and HTTPS work.

Debian’s package manager relies on hardlinks when unpacking packages. On Android those often fail inside the app sandbox, so Kai Build starts proot with hardlink-to-symlink emulation (and the companion lstat fix) before any apt work. Without that, the base-packages step fails with a dpkg subprocess error even though `apt-get update` succeeded.

### Coding agents

Three agents are offered — **Claude Code**, **Grok**, and **OpenCode** — each installed with its vendor script, which is why curl, certificates, tar, and coreutils ship with the base system. Installers write under `/root` on the rootfs (executable); only project folders live on the external bind. Vendor scripts put their binaries in different home folders (`~/.local/bin` for Claude, `~/.grok/bin` for Grok, `~/.opencode/bin` for OpenCode). Proot injects all three into `PATH` for install and detection. Login shells (every project terminal) rebuild `PATH` from system profile files and would otherwise drop those dirs — and vendor installers often skip writing shell-rc `PATH` lines when their bin dir is already present during install. Kai Build therefore writes a small system profile snippet that keeps every agent bin dir on `PATH` for login shells, links found binaries into `~/.local/bin`, and starts an agent session via the resolved absolute path so opening Claude or OpenCode does not depend on whichever installer rewrote `.bashrc`. An agent counts as installed only when its binary is actually reachable afterwards, regardless of what the installer's exit code claimed; agent state is probed from the live environment rather than remembered in a file.

Vendor install pages often show both a Unix `curl | bash` line and a Windows PowerShell line. Only the Unix line is valid inside Kai Build — pasting both into the terminal will run the PowerShell tokens as shell commands and print `irm`/`iex: not found` after the real installer finishes.

Agents can be added after setup from the Debian system card on the project list, which shows the install progress on the setup surface and returns to the projects when it finishes.

### Projects

Every project is a folder under the Linux home's `projects` directory (`/root/projects` inside Debian). The project list is that folder listing, so anything created in the shell shows up. A plus button in the top bar opens a small dialog for the name; creating the folder drops the user straight into its terminal — no other steps. Names are sanitized to a safe folder name. A project with shells still open says how many, because the list is the only place to find them from; opening it goes back to those rather than starting another.

Above the list, an optional **Open with** row picks what a *fresh* project opens with: a plain shell (the default) or one of the installed agents. The choice is a preference rather than a per-visit pick — it is remembered until it is changed, including across app restarts, so somebody who works in Claude Code finds Claude Code selected the next morning. It has no say over a project that still has shells open — that one is resumed as it was left, and the row's choice applies to the next session started from the plus button. A remembered agent that the environment no longer has (Linux was removed and put back without it) falls back to a plain shell rather than opening a session on a missing binary.

A **Debian system** card below the list is the home for everything about the Linux install rather than the projects: which Debian it is and for which architecture, how many packages are in it, how much space the system and the project folders take, how much room is left on the device, chips to install the remaining agents, and the uninstall action. The facts are read straight off the rootfs, so the card is current every time the list is shown.

Opening Kai Build answers "is Linux installed?" from a marker file and shows the project list straight away. Everything that costs real work — probing each agent by running it, and measuring how much disk the system and projects take — happens right after and fills the card in a moment later, so an installed system never shows the setup screen while it is being checked.

### Terminal sessions

A project can have **several shells open at once** — one running an agent, another for git — switched from a tab strip that replaces the title bar at the top of the terminal. Each tab is an independent session: its own PTY, its own screen contents, its own geometry, and its own draft input line. The plus button next to the tabs starts another session, either a plain shell or an installed agent.

Tabs are labelled by what they run (an agent's product name, or "Shell"), numbered while more than one is open. The selected tab carries a close button. Closing a session ends its shell and everything running inside it; closing the last one steps back to the project list, as does the back button.

**Stepping out of a project leaves its shells running.** A build, a test run or an agent mid-task keeps going while the user reads something else or works in another project, and coming back finds the tabs exactly as they were — same scrollback, same tab in front. Closing a session is the user's decision, made from its tab; nothing else ends one. The exception is the backstop: a project nobody has reopened for an hour has its shells closed, because each one is a proot process with a PTY and a shell behind it and a project left overnight is not one anybody is still waiting on. Reopening the project at any point during that hour calls it off.

A background session still reads and parses everything its program writes, but it does not repaint the app while nobody is looking at it — its screen is brought up to date the moment its project is opened again.

An **agent session** runs that agent's CLI first and drops to an ordinary shell in the project folder when the agent exits, so a finished session is still usable rather than dead.

### Project files

Next to the session tabs — pinned, so it is always there — sits **Files**: the same file browser the chat sandbox uses, pointed at Kai Build's Debian instead. It opens on the project's own folder, and navigates the same way it does in chat: the breadcrumbs run all the way up to the filesystem root, so an agent's config under the home folder, `/etc`, or anything else in Debian is a crumb away rather than shell-only. Where it starts is the only difference between the two.

It does everything it does in chat: open a file in the built-in editor and save it back — including the extension-less and unfamiliar-extension files a project is full of, since what counts as text is decided from the bytes rather than the name — hand a file to another app on the device, rename, delete, and re-list itself silently whenever the tab becomes visible, so files an agent just wrote show up without a manual refresh. Files are read and written directly rather than through a shell, so none of it depends on a session running.

Switching to Files leaves every session running — coming back finds the terminal exactly as it was, and Files finds the directory it was left in, wherever in Debian that was. The system back gesture steps Files → terminal → project list.

### Terminal

A session shows a **cell-grid terminal** sized to the visible viewport: cursor, basic colors, and common ANSI/VT control sequences, backed by an interactive login shell (`bash -l`) in the project folder. When the panel is laid out or the window changes (rotation, IME, split), Kai recomputes columns×rows from the monospace cell size, resizes the buffer, and tells the live PTY the new geometry (`TIOCSWINSZ` + `SIGWINCH`) so fullscreen apps reflow like a desktop terminal. A new session inherits the geometry that was last measured, so it starts at the right size instead of a default 80×24. There is no app-side clear button: `clear` and Ctrl-L do it from inside the shell, which keeps what is on screen and what the running program thinks is on screen the same thing.

The grid is given as much of the screen as possible: the tab strip is the only chrome above it, and the panel runs edge to edge sideways — the grid keeps a small inset of its own, so a second margin around it would only cost columns.

The session uses a **pseudo-terminal (PTY)** inside Debian (via Python’s `pty` module) so tools that open `/dev/tty` (Grok, Claude Code, fullscreen TUIs) can start. Output is parsed by a minimal VT emulator into a character grid and rendered in Compose.

### Terminal input

The terminal has **two input modes**, switched from a key in the key row. Keyboard mode is the default.

In **keyboard mode** the grid itself is the input surface: tapping it raises the soft keyboard, and every character reaches the shell the instant it is pressed. A tab raises the keyboard by itself the first time it is shown, since a session is started to be typed at; switching back to it later does not, because that is usually to read what an agent wrote rather than to answer it. That is what lets a TUI respond while the user is still typing — typing `/` in an agent CLI pops up its command list, and as-you-type filtering works the way it does on a desktop. There is no text field and nothing to submit. While the soft keyboard is open in this mode the input bar (hint, show-keyboard) is hidden so the cell grid keeps that row; dismiss the keyboard and the bar returns. The key row stays visible either way.

In **line mode** a text field returns, and the typed line is sent in one go when the user submits. It exists because keyboard mode gives up autocorrect, swipe typing and word prediction — a fair trade for driving a TUI, a bad one for composing a paragraph-long prompt to a coding agent. The mode is shared by the open tabs and is not remembered across app runs; the half-typed line is per tab, so switching away and back does not lose it. Line mode always keeps the input bar, because that is where the draft is typed.

The switch lives in the key row rather than the input bar precisely because the bar is gone at the moment it is wanted most — mid-sentence, keyboard up, realising the thought needs composing rather than typing straight at the shell. Switching to line mode while the keyboard is open hands the caret to the text field, so typing simply carries on there.

Both modes share a **key row** above the input bar for keys a phone keyboard does not have: Ctrl, Alt, Shift, Esc, Tab, the four arrows, and Enter. The three modifiers **latch for exactly one press** — tap Ctrl, then C, to interrupt — because there is no physical key to hold down. Tapping a latched modifier again clears it. In keyboard mode the latch also applies to the next character typed on the soft keyboard, so Ctrl and a letter behave as one chord. A modifier reported by a hardware keyboard is merged with whatever is latched.

The full set of caps is wider than a phone screen, so the row scrolls sideways and its order is a ranking: Ctrl, Esc, Tab and the four arrows come first because no soft keyboard offers them at all, and Alt and Shift trail behind them. Enter and the input-mode key sit outside that scroll, pinned to the right edge, so the key that ends every command and the one that changes how typing works are always in reach. The mode key is the one cap that still works once a session has ended — it changes the app, not the shell. Hairlines mark the groups. The arrows and Enter are drawn as icons rather than printed as terminal characters — as text they came out hairline-thin and at whatever weight the platform's font happened to have. Enter is the row's action key and reads as one: a wider, filled green cap with a bright return arrow on it. Caps dim as a set while no session is running.

Presses are encoded as the byte sequences xterm defines, which is what readline and every TUI decode: Enter as carriage return, Backspace as delete, Shift+Tab as its own sequence rather than a modified Tab, Ctrl-folded characters in the control range, and Alt as an escape prefix. Arrow keys have two encodings and the terminal tracks which one applies — apps that switch into application-cursor mode expect a different form, and sending the wrong one prints stray characters instead of moving the cursor. That mode is read back from the running app and resets when the screen is cleared.

Keyboard mode works by asking the system keyboard for a null input type, which is what makes keyboards deliver raw key presses instead of composing words. Keyboards are inconsistent about honoring that, so committed text and deletion requests are handled as a second path; a keyboard that ignores the hint still delivers whole words rather than nothing. The IME action button is treated as Enter.

An app can also ask to hear about **the mouse**, which is what makes parts of a TUI clickable on a desktop — and, once the terminal answers, on a phone. While an app is asking, a tap on the grid is a click on the character under the finger, and a vertical drag is the scroll wheel, one notch per row of cells crossed, so a list inside the TUI scrolls by dragging it. Content follows the finger, the way every other list on the device does. Sideways movement is ignored, since a TUI has nothing to scroll sideways and reading it as a drag would only make taps harder to land.

Handing taps to the app costs the shortcut that otherwise raises the keyboard by tapping the grid, so while an app is asking for mouse events the keyboard button in the input bar is the way to bring the keyboard up. Nothing changes at an ordinary shell prompt, which asks for none of this: a tap there still raises the keyboard. Whatever an app turned on is forgotten when the screen is fully reset, so a stale mode cannot outlive the program that wanted it.

The input surface that does this is deliberately kept out of the way rather than laid over the grid — anything covering the grid would hide the terminal contents from screen readers, so the grid keeps its own tap handling and stays readable in both modes. Input written to the shell is queued onto a single background thread, so a keystroke never blocks the UI on a pipe write and the bytes still arrive in the order they were typed.

### Repaint pacing

The PTY hands Kai a block of output as often as the program produces one — under heavy output that is far more often than the display refreshes. Parsing every block into the cell grid happens immediately, so nothing is ever missed, but **painting is coalesced to at most one repaint per frame**. A burst of output collapses into a single redraw instead of one redraw per block, and the last state is always painted, so the final screen a command leaves behind is never the stale one. Everything the repaint needs — snapshotting the grid, scanning it for login codes, handing it to the UI — happens off the main thread, as do geometry changes. That is what keeps the rest of the app responsive while a command floods the terminal, and what keeps raising the soft keyboard from stalling.

## Behavior

- **Android only** — the entry button is hidden on iOS, desktop, and web, and the environment itself is a no-op there.
- **Network required** — the rootfs download and every agent installer need HTTPS outbound access.
- **Disk** — expect ~150 MB for the base system, more per agent; the project list reports the real figure once Debian is installed.
- **Shared or separate** — Kai Build shares its rootfs with the chat sandbox when that sandbox is Debian, and has its own when it is Alpine. Project folders are the same either way.
- **Projects survive uninstall** — removing Linux deletes the Debian system, not the user's project folders. When the system is shared, removing it from either surface removes it for both.
- **Files are ordinary files** — project folders live in app external storage, so the browser reads and writes them directly, and a file handed to another app opens as itself.

## Limitations

- **Minimal VT set** — cursor move, erase, SGR colors (16 + coarse 256/RGB map), scroll; login URLs are captured via Grok’s `GROK_TEST_OPEN_URL_FILE` / `$BROWSER` hooks (and OSC 8/52 / plain text when present) and shown under the grid for a couple of minutes, then cleared. Same path collapses to one link (so OAuth re-prints do not stack), at most three links, and line-wrapped URLs are rejoined before scanning; no full xterm (true dual alt-screen buffers, scrollback history).
- **Mouse reporting is clicks and wheel only** — no drag-select or hover, so an app that asks to follow the pointer while it moves is answered as if it had only asked about clicks; no right button; no modifier keys on a click. A very wide grid falls back to reports an app can still read only if it asked for the modern coordinate form, which every agent TUI does.
- **Keyboard mode gives up IME help** — no autocorrect, swipe typing or predictions while keys go straight to the terminal, which is why line mode still exists.
- **Keyboards vary** — the null-input-type hint is a convention, not a guarantee. A keyboard that ignores it delivers whole words on commit rather than single presses, so as-you-type behavior degrades to word-at-a-time on those.
- **No composing region** — dead keys and IME candidate selection are not supported in keyboard mode; text arrives when committed.
- **Key row coverage** — Ctrl/Alt/Shift, Esc, Tab, arrows, Enter. Home/End, Page Up/Down and Delete are encoded and reachable from a hardware keyboard, but have no caps in the row. Function keys are not encoded at all.
- **No bracketed paste** — a submitted line is sent as plain bytes, so an app that would otherwise treat a multi-line paste as one unit still sees it as ordinary input.
- **Full-grid redraw** — a repaint rebuilds the whole grid rather than the changed cells, so a screen that is mostly static still costs a full pass. The per-frame cap is what keeps that affordable; there is no damage tracking.
- **Resize debounce** — geometry updates are lightly debounced and applied off the main thread, so very rapid layout thrash may lag a frame behind the pixels.
- **Cancel is cooperative** — it takes effect while downloading and between install steps, not in the middle of a running `apt-get`.
- **Background** — long installs or agent sessions can be killed if Android reclaims the process (foreground service is planned). Sessions survive leaving a project but not the app being killed, and a project left alone for an hour has its shells closed for it.
- **The file browser cannot create** — it renames, deletes, edits and imports, but only works with entries that are already there; new files and folders come from a shell.
- **Nothing is off limits in the browser** — it reaches the whole Debian tree, including system files a stray delete would break. Only the tree's own roots (the rootfs, the projects folder, `/tmp`) are protected from rename and delete.
- **Symlinks out of their root** are listed but not followed — a link that resolves outside the rootfs, the projects folder or `/tmp` does nothing when opened or deleted.
- **Sessions cost memory** — each one is a full proot + PTY + shell, so a phone will not hold many at once. They now accumulate across projects until closed or reaped, so the count on the project list is worth reading.
- **proot constraints** — same class of limits as the chat sandbox (no OpenSSH ControlMaster, no real mounts, some modern syscalls may fail).

## Key Files

| File | Purpose |
| --- | --- |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/KaiBuildController.kt` | Environment interface plus the no-op used off Android. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/build/KaiBuildState.kt` | The single state snapshot the screen renders. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/build/BuildAgents.kt` | The three installable coding agents. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/build/terminal/` | Cell buffer, VT parser, immutable screen snapshot. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/build/terminal/TerminalKeys.kt` | Key set, modifier latches, and the xterm key-to-bytes encoder. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/build/terminal/TerminalMouse.kt` | Which mouse events an app asked for, and the touch-to-report encoder. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/KaiBuildController.android.kt` | Android implementation. Resolves Debian's one install directory and wires the two-way notifications with the chat sandbox, checked at fire time because the sandbox can be pointed at that install and away from it while the app runs. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/linux/LinuxInstalls.kt` | Which install directory a distribution lives in. Both surfaces resolve against it, which is what makes "Kai Build's Debian" and "a Debian shell integration" the same rootfs. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/build/runtime/BuildEnvironmentManager.kt` | Agent installs, agent detection, projects, system facts, and the live sessions. Delegates the rootfs itself to the shared installer, and reports install/uninstall back so a shared sandbox notices. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/linux/LinuxInstaller.kt` | Shared download → extract → configure → base packages, used by both surfaces. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/linux/DistroSpec.kt` | `DebianSpec`: LXC index resolve, architecture names, dpkg fixes, and the `--link2symlink`/`-L` flags apt needs on Android. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/build/runtime/BuildProotExecutor.kt` | The python PTY bridge and raw byte streaming; the proot invocation itself comes from the shared `ProotLauncher`. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/build/BuildTerminalContent.kt` | Compose cell-grid terminal + input for the active session. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/build/BuildSessionBar.kt` | Back, session tabs, and the new-session menu. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/build/BuildProjectsContent.kt` | Project list, launch-agent row, Debian system card, new-project dialog. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/FileBrowserSource.kt` | The browsable-tree contract shared with the chat sandbox's file browser. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/build/runtime/BuildFileBrowser.kt` | Kai Build's side of it: listing, read/write, rename, delete, open-with, over the shared `GuestFileMap`. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/sandbox/SandboxFileBrowserScreen.kt` | The reused browser UI, rooted at the open project here. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/build/TerminalKeyRow.kt` | The Ctrl/Alt/Shift/Esc/Tab/arrow/Enter row and its latch behavior. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/build/TerminalKeyIcons.kt` | The arrow and Enter glyphs the key row draws. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/build/TerminalKeyboard.kt` | Platform input-surface declaration and whether keyboard mode is available. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/build/TerminalInputView.kt` | The Android IME plumbing: null input type, key events, committed text. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/build/KaiBuildScreen.kt` | Screen shell and the setup / projects / terminal routing. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/build/KaiBuildViewModel.kt` | Screen state, and the remembered "Open with" agent — read back at startup and checked against what is installed. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/chat/composables/EmptyState.kt` | Hosts the "Open Kai Build" entry button. |
