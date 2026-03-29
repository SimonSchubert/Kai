#!/usr/bin/env bash
#
# build-proot.sh — Cross-compile proot and talloc from source for Android
#
# Usage: ./build-proot.sh [--clean]
#
# Requirements:
#   - Android NDK (set ANDROID_NDK_HOME, or auto-detected from sdk.dir in local.properties)
#   - Python 3 (for talloc's WAF build system)
#   - git, curl, make
#
# Output: .so files in androidApp/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86_64}/
#

set -euo pipefail

# ── Configuration ──────────────────────────────────────────────────────────────
PROOT_REPO="https://github.com/termux/proot.git"
PROOT_COMMIT="4dba3afbf3a63af89b4d9c1a59bf2bda10f4d10f"
TALLOC_VERSION="2.4.3"
TALLOC_URL="https://www.samba.org/ftp/talloc/talloc-${TALLOC_VERSION}.tar.gz"
MIN_API=26
ABIS="arm64-v8a armeabi-v7a x86_64"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="${SCRIPT_DIR}/.build-native"
OUTPUT_DIR="${SCRIPT_DIR}/androidApp/src/main/jniLibs"

# ── Helpers ────────────────────────────────────────────────────────────────────
ndk_triple() {
    case "$1" in
        arm64-v8a)   echo "aarch64-linux-android" ;;
        armeabi-v7a) echo "armv7a-linux-androideabi" ;;
        x86_64)      echo "x86_64-linux-android" ;;
        x86)         echo "i686-linux-android" ;;
    esac
}

# Returns the 32-bit companion ABI (for building loader32). Empty if N/A.
loader32_abi() {
    case "$1" in
        arm64-v8a) echo "armeabi-v7a" ;;
        x86_64)    echo "x86" ;;
        *)         echo "" ;;
    esac
}

nproc_portable() {
    nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4
}

find_ndk() {
    for var in "${ANDROID_NDK_HOME:-}" "${ANDROID_NDK_ROOT:-}"; do
        [[ -n "$var" && -d "$var" ]] && { echo "$var"; return; }
    done
    # Try local.properties
    if [[ -f "$SCRIPT_DIR/local.properties" ]]; then
        local sdk_dir
        sdk_dir=$(grep '^sdk.dir=' "$SCRIPT_DIR/local.properties" 2>/dev/null | cut -d= -f2-)
        if [[ -n "${sdk_dir:-}" ]]; then
            local found
            found=$(ls -d "$sdk_dir/ndk/"* 2>/dev/null | sort -V | tail -1) || true
            [[ -n "${found:-}" ]] && { echo "$found"; return; }
        fi
    fi
    # Common SDK locations
    for base in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
        if [[ -n "$base" && -d "$base/ndk" ]]; then
            local found
            found=$(ls -d "$base/ndk/"* 2>/dev/null | sort -V | tail -1) || true
            [[ -n "${found:-}" ]] && { echo "$found"; return; }
        fi
    done
    echo "ERROR: Android NDK not found. Set ANDROID_NDK_HOME." >&2
    exit 1
}

# ── Handle --clean ─────────────────────────────────────────────────────────────
if [[ "${1:-}" == "--clean" ]]; then
    echo "Cleaning build directory..."
    rm -rf "$BUILD_DIR"
    echo "Done."
    exit 0
fi

# ── Setup ──────────────────────────────────────────────────────────────────────
NDK="$(find_ndk)"
echo "Using NDK: $NDK"

HOST_TAG=""
for tag in "$(uname -s | tr 'A-Z' 'a-z')-$(uname -m)" "darwin-x86_64" "linux-x86_64"; do
    if [[ -d "$NDK/toolchains/llvm/prebuilt/$tag" ]]; then
        HOST_TAG="$tag"; break
    fi
done
[[ -z "$HOST_TAG" ]] && { echo "ERROR: Cannot determine NDK host platform"; exit 1; }

TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin"
JOBS="$(nproc_portable)"
echo "Host: $HOST_TAG, parallel jobs: $JOBS"

# ── Download sources ───────────────────────────────────────────────────────────
mkdir -p "$BUILD_DIR"

if [[ ! -d "$BUILD_DIR/proot" ]]; then
    echo "Cloning proot..."
    git clone --quiet "$PROOT_REPO" "$BUILD_DIR/proot"
fi
echo "Checking out proot at $PROOT_COMMIT..."
git -C "$BUILD_DIR/proot" checkout -q "$PROOT_COMMIT"

if [[ ! -d "$BUILD_DIR/talloc-${TALLOC_VERSION}" ]]; then
    echo "Downloading talloc ${TALLOC_VERSION}..."
    curl -sL "$TALLOC_URL" -o "$BUILD_DIR/talloc.tar.gz"
    tar xzf "$BUILD_DIR/talloc.tar.gz" -C "$BUILD_DIR"
    rm "$BUILD_DIR/talloc.tar.gz"
fi

# ── Build talloc for an ABI ────────────────────────────────────────────────────
build_talloc() {
    local abi=$1
    local triple
    triple="$(ndk_triple "$abi")"
    local cc="${TOOLCHAIN}/${triple}${MIN_API}-clang"
    local prefix="$BUILD_DIR/sysroot/$abi"

    if [[ -f "$prefix/lib/libtalloc.so" ]]; then
        echo "[talloc/$abi] already built, skipping"
        return
    fi

    echo "[talloc/$abi] building..."
    local build_dir="$BUILD_DIR/build-talloc-$abi"
    rm -rf "$build_dir"
    cp -r "$BUILD_DIR/talloc-${TALLOC_VERSION}" "$build_dir"
    mkdir -p "$prefix"

    # WAF cross-compilation needs pre-filled answers (can't run test programs)
    cat > "$build_dir/cross-answers.txt" << 'EOF'
Checking uname sysname type: "Linux"
Checking uname machine type: "dontcare"
Checking uname release type: "dontcare"
Checking uname version type: "dontcare"
Checking simple C program: OK
building library support: OK
Checking for large file support: OK
Checking for -D_FILE_OFFSET_BITS=64: OK
Checking for WORDS_BIGENDIAN: OK
Checking for C99 vsnprintf: OK
Checking for HAVE_SECURE_MKSTEMP: OK
rpath library support: OK
-Wl,--version-script support: FAIL
Checking correct behavior of strtoll: OK
Checking correct behavior of strptime: OK
Checking for HAVE_IFACE_GETIFADDRS: OK
Checking for HAVE_IFACE_IFCONF: OK
Checking for HAVE_IFACE_IFREQ: OK
Checking getconf LFS_CFLAGS: OK
Checking for large file support without additional flags: OK
Checking for working strptime: OK
Checking for HAVE_SHARED_MMAP: OK
Checking for HAVE_MREMAP: OK
Checking for HAVE_INCOHERENT_MMAP: OK
Checking getconf large file support flags work: OK
EOF

    (
        cd "$build_dir"
        CC="$cc" \
        AR="${TOOLCHAIN}/llvm-ar" \
        RANLIB="${TOOLCHAIN}/llvm-ranlib" \
        STRIP="${TOOLCHAIN}/llvm-strip" \
        ./configure \
            --prefix="$prefix" \
            --disable-rpath \
            --disable-python \
            --cross-compile \
            --cross-answers=cross-answers.txt \
            > /dev/null
        make -j"$JOBS" > /dev/null
        make install > /dev/null
    )
    echo "[talloc/$abi] done"
}

# ── Build proot for an ABI ─────────────────────────────────────────────────────
build_proot() {
    local abi=$1
    local triple
    triple="$(ndk_triple "$abi")"
    local cc="${TOOLCHAIN}/${triple}${MIN_API}-clang"
    local sysroot="$BUILD_DIR/sysroot/$abi"
    local build_dir="$BUILD_DIR/build-proot-$abi"
    local out_dir="$OUTPUT_DIR/$abi"

    echo "[proot/$abi] building..."
    rm -rf "$build_dir"
    cp -r "$BUILD_DIR/proot" "$build_dir"

    # Remove HAS_LOADER_32BIT from arch.h — we build the 32-bit loader
    # separately with the correct 32-bit NDK toolchain (NDK clang doesn't
    # support -m32 like GCC does).
    sed -i.bak '/#define HAS_LOADER_32BIT/d' "$build_dir/src/arch.h"

    local loader_dir="$build_dir/loader-out"
    mkdir -p "$loader_dir" "$out_dir"

    # Provide readelf (macOS doesn't have it; NDK provides llvm-readelf)
    local tmp_bin="$BUILD_DIR/bin"
    mkdir -p "$tmp_bin"
    ln -sf "${TOOLCHAIN}/llvm-readelf" "$tmp_bin/readelf"

    (
        cd "$build_dir"
        # Pass flags via environment so the Makefile's += can append its own
        # defaults (-I., -DPROOT_UNBUNDLE_LOADER, -ltalloc, etc.)
        export CC="$cc"
        export LD="$cc"
        export STRIP="${TOOLCHAIN}/llvm-strip"
        export OBJCOPY="${TOOLCHAIN}/llvm-objcopy"
        export OBJDUMP="${TOOLCHAIN}/llvm-objdump"
        export CFLAGS="-DARG_MAX=131072 -I${sysroot}/include -Wno-error=implicit-function-declaration -Wno-error=int-conversion"
        export LDFLAGS="-L${sysroot}/lib"
        export PATH="$tmp_bin:$PATH"
        make -C src \
            PROOT_UNBUNDLE_LOADER="$loader_dir" \
            -j"$JOBS"
    )

    # Copy outputs (named lib*.so so Android extracts them from the APK)
    cp "$build_dir/src/proot" "$out_dir/libproot.so"
    cp "$build_dir/src/loader/loader" "$out_dir/libproot-loader.so"
    cp "$sysroot/lib/libtalloc.so" "$out_dir/libtalloc.so"

    "${TOOLCHAIN}/llvm-strip" "$out_dir/libproot.so"
    "${TOOLCHAIN}/llvm-strip" "$out_dir/libtalloc.so"

    echo "[proot/$abi] done -> $out_dir"
}

# ── Build 32-bit loader separately ─────────────────────────────────────────────
build_loader32() {
    local abi=$1
    local abi32
    abi32="$(loader32_abi "$abi")"
    [[ -z "$abi32" ]] && return

    local triple32
    triple32="$(ndk_triple "$abi32")"
    local cc32="${TOOLCHAIN}/${triple32}${MIN_API}-clang"
    local src_dir="$BUILD_DIR/build-proot-$abi/src"
    local out_dir="$OUTPUT_DIR/$abi"

    echo "[loader32/$abi] building with $abi32 toolchain..."

    # Extract LOADER_ADDRESS for the 32-bit arch via the C preprocessor
    local loader_addr
    loader_addr=$("$cc32" -dM -E -x c "$src_dir/arch.h" 2>/dev/null \
        | grep 'LOADER_ADDRESS' | awk '{print $3}')

    if [[ -z "$loader_addr" ]]; then
        echo "[loader32/$abi] WARNING: could not determine LOADER_ADDRESS, skipping"
        return
    fi
    echo "[loader32/$abi] LOADER_ADDRESS=$loader_addr"

    # The loader is a tiny static/nostdlib/freestanding ELF.
    # Compile loader.c and assembly.S, link at the fixed address.
    "$cc32" \
        -DLOADER_ADDRESS="$loader_addr" \
        -I"$src_dir" \
        -Wall -Wextra -O2 \
        -fPIC -ffreestanding \
        -c "$src_dir/loader/loader.c" \
        -o "$BUILD_DIR/loader32-$abi.o"

    "$cc32" \
        -I"$src_dir" \
        -c "$src_dir/loader/assembly.S" \
        -o "$BUILD_DIR/assembly32-$abi.o"

    # -N (--omagic) prevents lld from padding the file to reach the high
    # virtual address — without it the 32-bit ELF can be gigabytes.
    "$cc32" \
        -static -nostdlib \
        -Wl,-N \
        -Wl,-Ttext="$loader_addr" \
        -o "$out_dir/libproot-loader32.so" \
        "$BUILD_DIR/loader32-$abi.o" \
        "$BUILD_DIR/assembly32-$abi.o"

    "${TOOLCHAIN}/llvm-strip" "$out_dir/libproot-loader32.so"
    echo "[loader32/$abi] done -> $out_dir/libproot-loader32.so"
}

# ── Main ───────────────────────────────────────────────────────────────────────
echo ""
echo "Building proot + talloc from source for: $ABIS"
echo "Output: $OUTPUT_DIR"
echo ""

for abi in $ABIS; do
    build_talloc "$abi"
    build_proot "$abi"
    build_loader32 "$abi"
    echo ""
done

echo "All builds complete!"
echo ""
echo "Binaries installed to:"
for abi in $ABIS; do
    echo "  $OUTPUT_DIR/$abi/"
    ls -lh "$OUTPUT_DIR/$abi/"*.so | awk '{print "    " $NF " (" $5 ")"}'
done
