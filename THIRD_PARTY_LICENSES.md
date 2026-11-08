# Third-Party Licenses

This project includes the following third-party binaries in `androidApp/src/main/jniLibs/`.
The app shows these notices, the full license texts and a written offer for the source under
Settings › Licenses (`composeApp/src/commonMain/composeResources/files/licenses/THIRD_PARTY_NOTICES.txt`).
Keep the pinned versions there and in `build-proot.sh` in sync.

## PRoot (Termux fork)

- **Files:** `libproot.so`, `libproot-loader.so`, `libproot-loader32.so`
- **Source:** https://github.com/termux/proot/tree/4dba3afbf3a63af89b4d9c1a59bf2bda10f4d10f (built by `build-proot.sh`)
- **License:** GPL-2.0
- **Copyright:** Copyright (C) PRoot developers

PRoot is a user-space implementation of chroot, mount --bind, and binfmt_misc. It is used to run an Alpine Linux environment inside the Android app without requiring root access. PRoot is executed as a separate process and is not linked into the application code.

The full GPL-2.0 license text is available at: https://www.gnu.org/licenses/old-licenses/gpl-2.0.html

## talloc

- **Files:** `libtalloc.so`
- **Source:** https://www.samba.org/ftp/talloc/talloc-2.4.3.tar.gz (version 2.4.3, built by `build-proot.sh`)
- **License:** LGPL-3.0
- **Copyright:** Copyright (C) Andrew Tridgell, Stefan Metzmacher, and contributors

talloc is a hierarchical memory allocator used as a dependency of PRoot. It is dynamically linked.

The full LGPL-3.0 license text is available at: https://www.gnu.org/licenses/lgpl-3.0.html
