# Kai Build

**Last verified:** 2026-08-02

Kai Build is an **Android-only** coding environment inside the Kai app. It is intentionally separate from chat: no shared conversation store, no Alpine sandbox tools, no provider/settings coupling beyond the shared proot native libraries.

It is a **single screen with three states** — set up Linux, pick a project, work in that project's terminal.

## Concepts

### Separate product surface

Kai Build is opened from the **empty chat state** — an "Open Kai Build" button next to "Start Interactive UI", shown on Android only. It then takes over the whole screen the same way Interactive UI mode does: no navigation destination, no second launcher icon, and no place in the back stack. A close button in its top bar and the system back gesture both return to the chat, leaving any drafted message intact. Inside a project, the same top-bar button and system back step back to the project list instead. The mode is not persisted across app restarts; it survives rotation only.

Storage lives under app-private `kai-build/` (rootfs, including `/root` for agent binaries and config) and an external-files folder bind-mounted only to `/root/projects`, so project code stays USB/MTP reachable without putting executables on storage that Android often mounts noexec. Uninstalling Kai Build's Linux does not touch the chat Alpine sandbox, and the reverse is also true.

### Set up Linux

First run shows a single setup card: a beta notice, a short explanation, a checkbox per coding agent, and one **Install Debian** button. The setup title and description mark the feature as beta. Install downloads a Debian Bookworm rootfs from the Linux Containers image index (architecture-matched: arm64, armhf, amd64, i386), extracts it, configures DNS, then runs `apt-get update` and installs the essentials every project needs — bash, ca-certificates, curl, wget, git, nano, less, unzip, and python3. Any agents ticked beforehand are installed straight after, so a fresh system arrives ready to use.

Progress replaces the install button (download percent, extract, configure, base packages, per-agent), with Cancel next to it. Cancelling or failing deletes the partial rootfs so the retry starts clean; a failure message stays on screen. A Debian install is only considered complete once its base packages are in, so an interrupted install can never present itself as ready.

LXC images ship `etc/resolv.conf` as a symlink into systemd-resolved under `/run`, which does not exist under proot. Install replaces that link with a plain file pointing at public DNS (`8.8.8.8` / `8.8.4.4`) so apt and HTTPS work.

Debian’s package manager relies on hardlinks when unpacking packages. On Android those often fail inside the app sandbox, so Kai Build starts proot with hardlink-to-symlink emulation (and the companion lstat fix) before any apt work. Without that, the base-packages step fails with a dpkg subprocess error even though `apt-get update` succeeded.

### Coding agents

Three agents are offered — **Claude Code**, **Grok**, and **OpenCode** — each installed with its vendor script, which is why curl, certificates, tar, and coreutils ship with the base system. Installers write under `/root` on the rootfs (executable); only project folders live on the external bind. Vendor scripts put their binaries in different home folders (`~/.local/bin` for Claude, `~/.grok/bin` for Grok, `~/.opencode/bin` for OpenCode). Proot injects all three into `PATH` for install and detection. Login shells (every project terminal) rebuild `PATH` from system profile files and would otherwise drop those dirs — and vendor installers often skip writing shell-rc `PATH` lines when their bin dir is already present during install. Kai Build therefore writes a small system profile snippet that keeps every agent bin dir on `PATH` for login shells, links found binaries into `~/.local/bin`, and starts an agent session via the resolved absolute path so opening Claude or OpenCode does not depend on whichever installer rewrote `.bashrc`. An agent counts as installed only when its binary is actually reachable afterwards, regardless of what the installer's exit code claimed; agent state is probed from the live environment rather than remembered in a file.

Vendor install pages often show both a Unix `curl | bash` line and a Windows PowerShell line. Only the Unix line is valid inside Kai Build — pasting both into the terminal will run the PowerShell tokens as shell commands and print `irm`/`iex: not found` after the real installer finishes.

Agents can be added after setup from the Debian system card on the project list, which shows the install progress on the setup surface and returns to the projects when it finishes.

### Projects

Every project is a folder under the Linux home's `projects` directory (`/root/projects` inside Debian). The project list is that folder listing, so anything created in the shell shows up. A plus button in the top bar opens a small dialog for the name; creating the folder drops the user straight into its terminal — no other steps. Names are sanitized to a safe folder name.

Above the list, an optional **Open with** row picks what a project opens with: a plain shell (the default) or one of the installed agents. The choice sticks until it is changed, so opening three projects in a row with the same agent takes one tap each.

A **Debian system** card below the list is the home for everything about the Linux install rather than the projects: which Debian it is and for which architecture, how many packages are in it, how much space the system and the project folders take, how much room is left on the device, chips to install the remaining agents, and the uninstall action. The facts are read straight off the rootfs, so the card is current every time the list is shown.

### Terminal sessions

A project can have **several shells open at once** — one running an agent, another for git — switched from a tab strip that replaces the title bar at the top of the terminal. Each tab is an independent session: its own PTY, its own screen contents, its own geometry, and its own draft input line. The plus button next to the tabs starts another session, either a plain shell or an installed agent.

Tabs are labelled by what they run (an agent's product name, or "Shell"), numbered while more than one is open. The selected tab carries a close button. Closing a session ends its shell and everything running inside it; closing the last one steps back to the project list, as does the back button. Leaving a project ends every session it had — nothing keeps running in the background.

An **agent session** runs that agent's CLI first and drops to an ordinary shell in the project folder when the agent exits, so a finished session is still usable rather than dead.

### Project files

Next to the session tabs — pinned, so it is always there — sits **Files**: the same file browser the chat sandbox uses, pointed at Kai Build's Debian instead. It opens on the project's own folder and cannot climb above it; the breadcrumbs start at the project name, because they are the only way up.

It does everything it does in chat: open a text file in the built-in editor and save it back, hand a file to another app on the device, rename, delete, and re-list itself silently whenever the tab becomes visible, so files an agent just wrote show up without a manual refresh. Files are read and written directly rather than through a shell, so none of it depends on a session running.

Switching to Files leaves every session running — coming back finds the terminal exactly as it was, and the system back gesture steps Files → terminal → project list.

### Terminal

A session shows a **cell-grid terminal** sized to the visible viewport: cursor, basic colors, and common ANSI/VT control sequences, backed by an interactive login shell (`bash -l`) in the project folder. When the panel is laid out or the window changes (rotation, IME, split), Kai recomputes columns×rows from the monospace cell size, resizes the buffer, and tells the live PTY the new geometry (`TIOCSWINSZ` + `SIGWINCH`) so fullscreen apps reflow like a desktop terminal. A new session inherits the geometry that was last measured, so it starts at the right size instead of a default 80×24. Clear blanks the screen buffer.

The grid is given as much of the screen as possible: the tab strip is the only chrome above it, and the surface around it is inset by a few pixels rather than a full margin.

The session uses a **pseudo-terminal (PTY)** inside Debian (via Python’s `pty` module) so tools that open `/dev/tty` (Grok, Claude Code, fullscreen TUIs) can start. Output is parsed by a minimal VT emulator into a character grid and rendered in Compose.

### Terminal input

The terminal has **two input modes**, switched from an icon in the input bar. Keyboard mode is the default.

In **keyboard mode** the grid itself is the input surface: tapping it raises the soft keyboard, and every character reaches the shell the instant it is pressed. That is what lets a TUI respond while the user is still typing — typing `/` in an agent CLI pops up its command list, and as-you-type filtering works the way it does on a desktop. There is no text field and nothing to submit. While the soft keyboard is open in this mode the input bar (hint, show-keyboard, mode toggle, clear) is hidden so the cell grid keeps that row; dismiss the keyboard and the bar returns. The special-key row stays visible either way.

In **line mode** a text field returns, and the typed line is sent in one go when the user submits. It exists because keyboard mode gives up autocorrect, swipe typing and word prediction — a fair trade for driving a TUI, a bad one for composing a paragraph-long prompt to a coding agent. The mode is shared by the open tabs and is not remembered across app runs; the half-typed line is per tab, so switching away and back does not lose it. Line mode always keeps the input bar, because that is where the draft is typed.

Both modes share a **key row** above the input bar for keys a phone keyboard does not have: Ctrl, Alt, Shift, Esc, Tab, the four arrows, and Enter. The three modifiers **latch for exactly one press** — tap Ctrl, then C, to interrupt — because there is no physical key to hold down. Tapping a latched modifier again clears it. In keyboard mode the latch also applies to the next character typed on the soft keyboard, so Ctrl and a letter behave as one chord. A modifier reported by a hardware keyboard is merged with whatever is latched.

The full set of caps is wider than a phone screen, so the row scrolls sideways and its order is a ranking: Ctrl, Esc, Tab and the four arrows come first because no soft keyboard offers them at all, and Alt and Shift trail behind them. Enter sits outside that scroll, pinned to the right edge, so the key that ends every command is always in reach. Hairlines mark the groups. The arrows and Enter are drawn as icons rather than printed as terminal characters — as text they came out hairline-thin and at whatever weight the platform's font happened to have. Enter is the row's action key and reads as one: a wider, filled green cap with a bright return arrow on it. Caps dim as a set while no session is running.

Presses are encoded as the byte sequences xterm defines, which is what readline and every TUI decode: Enter as carriage return, Backspace as delete, Shift+Tab as its own sequence rather than a modified Tab, Ctrl-folded characters in the control range, and Alt as an escape prefix. Arrow keys have two encodings and the terminal tracks which one applies — apps that switch into application-cursor mode expect a different form, and sending the wrong one prints stray characters instead of moving the cursor. That mode is read back from the running app and resets when the screen is cleared.

Keyboard mode works by asking the system keyboard for a null input type, which is what makes keyboards deliver raw key presses instead of composing words. Keyboards are inconsistent about honoring that, so committed text and deletion requests are handled as a second path; a keyboard that ignores the hint still delivers whole words rather than nothing. The IME action button is treated as Enter.

The input surface that does this is deliberately kept out of the way rather than laid over the grid — anything covering the grid would hide the terminal contents from screen readers, so the grid keeps its own tap handling and stays readable in both modes. Input written to the shell is queued onto a single background thread, so a keystroke never blocks the UI on a pipe write and the bytes still arrive in the order they were typed.

### Repaint pacing

The PTY hands Kai a block of output as often as the program produces one — under heavy output that is far more often than the display refreshes. Parsing every block into the cell grid happens immediately, so nothing is ever missed, but **painting is coalesced to at most one repaint per frame**. A burst of output collapses into a single redraw instead of one redraw per block, and the last state is always painted, so the final screen a command leaves behind is never the stale one. Everything the repaint needs — snapshotting the grid, scanning it for login codes, handing it to the UI — happens off the main thread, as do geometry changes and clearing the screen. That is what keeps the rest of the app responsive while a command floods the terminal, and what keeps raising the soft keyboard from stalling.

## Behavior

- **Android only** — the entry button is hidden on iOS, desktop, and web, and the environment itself is a no-op there.
- **Network required** — the rootfs download and every agent installer need HTTPS outbound access.
- **Disk** — expect ~150 MB for the base system, more per agent; the project list reports the real figure once Debian is installed.
- **Isolation** — Kai Build does not share its rootfs or home with the chat Linux sandbox.
- **Projects survive uninstall** — removing Linux deletes the Debian system, not the user's project folders.
- **Files are ordinary files** — project folders live in app external storage, so the browser reads and writes them directly, and a file handed to another app opens as itself.

## Limitations

- **Minimal VT set** — cursor move, erase, SGR colors (16 + coarse 256/RGB map), scroll; login URLs are captured via Grok’s `GROK_TEST_OPEN_URL_FILE` / `$BROWSER` hooks (and OSC 8/52 / plain text when present) and shown under the grid for a couple of minutes, then cleared. Same path collapses to one link (so OAuth re-prints do not stack), at most three links, and line-wrapped URLs are rejoined before scanning; no full xterm (mouse, true dual alt-screen buffers, scrollback history).
- **Keyboard mode gives up IME help** — no autocorrect, swipe typing or predictions while keys go straight to the terminal, which is why line mode still exists.
- **Keyboards vary** — the null-input-type hint is a convention, not a guarantee. A keyboard that ignores it delivers whole words on commit rather than single presses, so as-you-type behavior degrades to word-at-a-time on those.
- **No composing region** — dead keys and IME candidate selection are not supported in keyboard mode; text arrives when committed.
- **Key row coverage** — Ctrl/Alt/Shift, Esc, Tab, arrows, Enter. Home/End, Page Up/Down and Delete are encoded and reachable from a hardware keyboard, but have no caps in the row. Function keys are not encoded at all.
- **No bracketed paste** — a submitted line is sent as plain bytes, so an app that would otherwise treat a multi-line paste as one unit still sees it as ordinary input.
- **Full-grid redraw** — a repaint rebuilds the whole grid rather than the changed cells, so a screen that is mostly static still costs a full pass. The per-frame cap is what keeps that affordable; there is no damage tracking.
- **Resize debounce** — geometry updates are lightly debounced and applied off the main thread, so very rapid layout thrash may lag a frame behind the pixels.
- **Cancel is cooperative** — it takes effect while downloading and between install steps, not in the middle of a running `apt-get`.
- **Background** — long installs or agent sessions can be killed if Android reclaims the process (foreground service is planned). Sessions do not survive leaving the project either, on purpose: every shell of a project ends when the user steps back to the list.
- **The file browser stays inside the project** — there is no way to reach the rest of the Debian tree (agent config under the home folder, `/etc`, `/usr`) from it; use a shell for that. It also cannot create files or folders, only work with what is there.
- **Symlinks out of the project** are listed but not followed — opening or deleting one does nothing.
- **Sessions cost memory** — each one is a full proot + PTY + shell, so a phone will not hold many at once.
- **proot constraints** — same class of limits as the chat sandbox (no OpenSSH ControlMaster, no real mounts, some modern syscalls may fail).

## Key Files

| File | Purpose |
| --- | --- |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/KaiBuildController.kt` | Environment interface plus the no-op used off Android. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/build/KaiBuildState.kt` | The single state snapshot the screen renders. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/build/BuildAgents.kt` | The three installable coding agents. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/build/terminal/` | Cell buffer, VT parser, immutable screen snapshot. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/build/terminal/TerminalKeys.kt` | Key set, modifier latches, and the xterm key-to-bytes encoder. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/KaiBuildController.android.kt` | Android implementation backed by the environment manager. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/build/runtime/BuildEnvironmentManager.kt` | Install, agent detection, projects, system facts, and the live sessions. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/build/runtime/DebianRootfsInstaller.kt` | LXC index resolve, download, extract. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/build/runtime/BuildProotExecutor.kt` | proot process launcher for Debian (PTY-wrapped streaming). |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/build/BuildTerminalContent.kt` | Compose cell-grid terminal + input for the active session. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/build/BuildSessionBar.kt` | Back, session tabs, and the new-session menu. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/build/BuildProjectsContent.kt` | Project list, launch-agent row, Debian system card, new-project dialog. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/FileBrowserSource.kt` | The browsable-tree contract shared with the chat sandbox's file browser. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/build/runtime/BuildFileBrowser.kt` | Kai Build's side of it: guest paths to host files, listing, read/write, rename, delete, open-with. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/sandbox/SandboxFileBrowserScreen.kt` | The reused browser UI, rooted at the open project here. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/build/TerminalKeyRow.kt` | The Ctrl/Alt/Shift/Esc/Tab/arrow/Enter row and its latch behavior. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/build/TerminalKeyIcons.kt` | The arrow and Enter glyphs the key row draws. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/build/TerminalKeyboard.kt` | Platform input-surface declaration and whether keyboard mode is available. |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/build/TerminalInputView.kt` | The Android IME plumbing: null input type, key events, committed text. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/build/KaiBuildScreen.kt` | Screen shell and the setup / projects / terminal routing. |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/chat/composables/EmptyState.kt` | Hosts the "Open Kai Build" entry button. |
