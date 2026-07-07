# Desktop/Flatpak Sandbox Environment

**Status:** Approved design, ready for implementation planning
**Date:** 2026-06-30

## Background

Kai's Android app sandboxes shell access using `proot` to fake a `chroot` into a downloaded Alpine Linux rootfs, giving the agent a real, isolated Linux environment with its own package manager (`apk`). The desktop/Flatpak build currently has no equivalent — `SandboxController.jvm.kt` is a `NoOpSandboxController` that does nothing.

This was originally requested by the maintainer's user community (GitHub issue [SimonSchubert/Kai#109](https://github.com/SimonSchubert/Kai/issues/109)): *"The Flatpak shell environment feels a little empty. Might be worth it to have an Alpine proot available in there, even though we're already on Linux."* No prior discussion in that issue or elsewhere in the repo found this idea technically infeasible — it simply hadn't been attempted.

## Spike findings (empirically verified, not theoretical)

Before committing to an architecture, we tested the core mechanisms directly against a real installed Flatpak build of Kai, and against minimal throwaway Flatpak apps built for this purpose. All three results below were reproduced with real binaries running inside real, installed Flatpak sandboxes (not simulated or inferred from documentation):

| Mechanism | Result | Why |
|---|---|---|
| `proot` (ptrace-based chroot faking) | **Blocked** | `ptrace(TRACEME)` fails with `Operation not permitted`. Flatpak's seccomp filter unconditionally blocks the `ptrace` syscall family. Confirmed with a real `proot` 5.1.0 binary; `PROOT_NO_SECCOMP=1` (proot's own seccomp-accelerator opt-out) does not help, ruling out proot-side causes. |
| Nested `bwrap` (user/mount namespace-based chroot) | **Blocked** | `bwrap: No permissions to create new namespace`. Fails identically regardless of which namespace flags are requested (`--unshare-user`, `--unshare-pid` alone, etc.) — Flatpak's own sandbox prevents nested unprivileged namespace creation from inside itself, independent of the host's `kernel.unprivileged_userns_clone` sysctl (which is enabled on the test host). |
| `micromamba` (conda-forge package manager, no privileged syscalls) | **Works** | Installed `jq` (+ native deps: `libgomp`, `_openmp_mutex`, `libgcc`, `oniguruma`) from conda-forge and successfully *executed* the installed binary, fully inside a real Flatpak sandbox. Independently re-confirmed by running the same test through Kai's own live, already-installed Flatpak app (v1.7.9, runtime 24.08) via its chat agent's shell tool. |
| `~/.kai` persistence via `--persist=.kai` | **Works** | Relocated the live micromamba install from `/tmp` to `~/.kai/linux-sandbox/micromamba/` through Kai's own chat agent, fully quit and relaunched the Flatpak app, then confirmed in a fresh conversation that both the binary and the installed environment survived and `jq --version` still ran correctly from that path. Confirms the storage location this design relies on is durable across real app restarts, not just within a single process lifetime. |

**Conclusion:** Any approach requiring `ptrace`, `chroot`, `pivot_root`, or namespace creation is a dead end inside Flatpak — this is not configurable via any `finish-args` permission we could find. The viable path is a **relocatable package manager** that installs real, working binaries directly into the existing Flatpak filesystem without faking a separate root.

### Why conda-forge over a "real" Linux distro repo (apt/apk)

Real distro packages (`.deb`, `.apk`) hardcode absolute paths (e.g. ELF interpreter `/lib/ld-musl-x86_64.so.1`) that assume the package is installed at the real `/`. Extracted elsewhere, the kernel's loader can't find the interpreter and the binary simply won't run — there is no chroot available inside Flatpak to make `/` "look like" the alternate root. conda-forge packages are specifically built to avoid this: every binary is `patchelf`-rewritten at install time to use paths relative to an arbitrary install prefix. That relocatability is *why* this approach works where Alpine/Debian packages wouldn't, without us reinventing that relocation engineering ourselves.

This means desktop's environment is conceptually different from Android's: not a separate "fake root" Linux distro, but a curated set of relocated dev-tool binaries (Python, Node.js, git, curl, jq, ripgrep, etc. — conda-forge has 30,000+ packages, covering every tool Android's install flow currently installs) layered directly into the existing Flatpak filesystem. No systemd, no desktop apps, no kernel modules — a dev-tools shelf, not a distro. The Android side is unaffected and keeps proot + Alpine exactly as it works today.

## Scope

- **In scope:** desktop/Flatpak sandbox environment using micromamba + conda-forge, matching Android's setup UX (install rootfs-equivalent → install packages, same two-step flow)
- **Also works for, as an interim measure:** the non-Flatpak desktop build (plain Linux binary/tarball). This implementation doesn't branch on Flatpak-vs-not, so non-Flatpak desktop gets micromamba too for now, purely as a side effect of there being one code path. See Roadmap below — non-Flatpak desktop can do meaningfully better than this, but that's deliberately sequenced as follow-up work, not bundled into this pass.
- **Out of scope:** changing anything about the Android implementation; a Debian-environment option (raised as a possible future idea in GH issue #109, not pursued here); non-Flatpak bwrap+Alpine parity (see Roadmap)

## Architecture

### Storage location

`~/.kai/linux-sandbox/micromamba/`, reusing the existing `getAppFilesDirectory()` helper (`Platform.jvm.kt`), which already resolves to `~/.kai`. Layout: `micromamba/bin/micromamba` (the downloaded binary) and `micromamba/root/` (the `MAMBA_ROOT_PREFIX` — installed packages and the default environment live here). The Flatpak manifest already has `--persist=.kai` in `finish-args`, so this is durable across app updates with **no manifest changes needed for persistence**.

Inside the Flatpak sandbox, `getAppFilesDirectory()` resolves to `$HOME/.kai`, which Flatpak transparently maps to `~/.var/app/io.github.simonschubert.Kai/.kai/` on the real host filesystem via the `--persist=.kai` bind-mount. On the non-Flatpak desktop build (unsandboxed), the same code resolves directly to literal `~/.kai` with no indirection.

### Components

| File | Action |
|---|---|
| `DesktopLinuxSandboxManager.kt` (new, `desktopMain`) | Downloads the micromamba binary (~10MB static binary, one file, no compilation) to `~/.kai/linux-sandbox/`. Manages package installs via `micromamba install -y -p <prefix> <package>` |
| `SandboxController.jvm.kt` | Replace `NoOpSandboxController` with a `DesktopSandboxController` that drives `DesktopLinuxSandboxManager` — mirrors the shape of `AndroidSandboxController` (most of its logic — file listing, read/write, delete/rename — is already platform-agnostic `java.io.File` code with no Android dependency, so it ports with minimal changes) |
| Persistent shell session (new, `desktopMain`) | Simpler than Android's `PersistentSandboxShell`: no chroot boundary to maintain, just a shell with `PATH`/`MAMBA_ROOT_PREFIX` env vars pointed at the installed environment |
| `DesktopSandboxModule.kt` (new) | Koin wiring for `DesktopLinuxSandboxManager`, following the same pattern as Android's `SandboxModule.kt` |

Android's `LinuxSandboxManager`, `ProotExecutor`, `RootfsDownloader`, `PersistentSandboxShell`, `SessionShell` stay exactly where they are (`androidMain`) — unlike the originally-proposed design, there's no shared proot logic to move to `jvmShared`, since desktop doesn't use proot at all.

### Flatpak manifest

No new build modules needed (no proot/talloc compilation). The existing `--share=network` permission (already present) is sufficient for micromamba to download itself and packages.

### UI

Desktop's settings screen uses distinct framing from Android's "Sandbox" terminology — labeled "Dev Tools" — to accurately reflect that this installs dev tools into the existing environment rather than a separate Alpine root. The underlying `SandboxController` interface and method names are unchanged; this is purely a UI copy difference. Setup flow matches Android: an "Install" step (downloads micromamba) followed by an "Install packages" step, using the existing `SandboxStatus`/`SandboxState` state machine shape.

Default package set, mirroring Android's `LinuxSandboxManager.installPackages()` list, mapped to conda-forge package names: `git`, `curl`, `wget`, `jq`, `python` (includes `pip`), `nodejs`, `openssh`, `lftp`, `rsync`. (`bash` isn't needed as a separate install — conda-forge environments run under the host's existing shell.)

## Error handling

- micromamba binary download fails → retry/error surfacing analogous to `RootfsDownloader`'s existing mirror-retry pattern
- `micromamba install <pkg>` fails (package not on conda-forge, network issue) → surfaced to the user/agent the same way `apk add` failures are surfaced today
- No ptrace/namespace-related risk category — eliminated by design, not mitigated

## Testing

- The core mechanism risk was retired via direct empirical spikes (see Spike findings above), run twice independently: once via scripted throwaway Flatpak apps, once via Kai's own live installed app and chat agent
- Unit tests for the new desktop manager's path resolution and download logic
- End-to-end manual verification via `/verify` once implementation exists: install flow, package install, run commands through the terminal UI

## Roadmap: non-Flatpak desktop Alpine parity (deliberately deferred)

Standalone `bwrap` (i.e. **not** nested inside Flatpak's own sandbox) gives the non-Flatpak desktop build genuine Android-level parity — real Alpine, real `apk`, real network — and is arguably a cleaner mechanism than Android's own proot (real kernel namespace isolation instead of ptrace tracing). This was empirically confirmed, not just theorized:

- Downloaded a real Alpine minirootfs (same version/URL pattern as Android's `RootfsDownloader`)
- `bwrap --unshare-user --unshare-pid --unshare-uts --unshare-cgroup --uid 0 --gid 0 --bind $ROOTFS / --dev /dev --proc /proc --bind /sys /sys -- /bin/sh` dropped into a real chroot: `whoami` → `root`, `/etc/os-release` confirmed genuine Alpine
- `apk update && apk add --no-cache jq` succeeded against the real Alpine mirrors, and the installed `jq` binary executed correctly

This works because the failure modes documented above (blocked `ptrace`, blocked nested namespace creation) are specifically about a sandbox *nested inside Flatpak's own bwrap sandbox*. A non-Flatpak process has no outer sandbox to be nested inside — it's just a normal Linux process calling `bwrap` the same way Flatpak itself already does on every desktop this app runs on.

**Why this is deferred rather than built now:** sequencing decision — get Flatpak micromamba shipped and solid first; only take on the added complexity of a second, better desktop mechanism once that's proven in practice.

**Sketch of the eventual work** (not a committed design — to be brainstormed properly when picked up):
- Reuse `RootfsDownloader` as-is (already pure JVM, already in the right shape — see Components table above)
- New `bwrap`-based executor, structurally parallel to `ProotExecutor` but using bind-mount/chroot flags instead of ptrace
- Flatpak-environment detection (e.g. `FLATPAK_ID` env var or presence of `/.flatpak-info`) in `SandboxController.jvm.kt`, branching to the bwrap-based manager when not running inside Flatpak, falling back to the micromamba manager from this spec when running inside Flatpak
- Whether bundling a `bwrap` binary is needed or whether requiring a system-installed one (near-universal on modern desktop Linux, since Flatpak itself depends on it) is acceptable, is an open question for that future design pass
