#!/usr/bin/env bash
# Build the current branch as a locally-installed Flatpak app, completely separate
# from your real io.github.simonschubert.Kai install: app-id io.github.simonschubert.Kai.Dev,
# its own data directory, its own icon/name ("Kai 9000 (Dev Test)"). Safe to re-run any
# time you want to test a fresh commit -- never touches your production install or data.
#
# Mirrors the official release CI's Flatpak build (.github/workflows/release.yml,
# the `flatpak` job) but points the manifest at a freshly-built local tarball instead
# of a pinned GitHub release URL.
#
# Requires:
#   - flatpak, flatpak-builder
#   - org.freedesktop.Sdk//24.08  (one-time: flatpak install --user flathub org.freedesktop.Sdk//24.08)
#   - A JDK 21 *compiler* (not just a JRE) at /usr/lib/jvm/java-21-openjdk-amd64.
#     ProGuard 7.7.0 can't read JDK 25 class files, and this repo's own jvmTarget is 21
#     anyway. If you only have the JRE: sudo apt install openjdk-21-jdk
#   - A few GB of free disk space for the SDK/build/Flatpak export.
#
# Run it, then: flatpak run io.github.simonschubert.Kai.Dev
# To remove it later: scripts/uninstall-dev-flatpak.sh

set -euo pipefail
cd "$(dirname "$0")/.."

APP_ID="io.github.simonschubert.Kai.Dev"
REMOTE_NAME="kai-dev-local"
BUILD_ROOT="${XDG_CACHE_HOME:-$HOME/.cache}/kai-dev-flatpak"
REPO_DIR="$BUILD_ROOT/repo"
MANIFEST_DIR="$BUILD_ROOT/manifest"

JDK21="/usr/lib/jvm/java-21-openjdk-amd64"
if [ ! -x "$JDK21/bin/javac" ]; then
    echo "JDK 21 with a compiler not found at $JDK21." >&2
    echo "Install it with: sudo apt install openjdk-21-jdk" >&2
    echo "(Or edit JDK21= at the top of this script if yours lives elsewhere.)" >&2
    exit 1
fi

if ! command -v flatpak-builder >/dev/null; then
    echo "flatpak-builder not found. Install it with: sudo apt install flatpak-builder" >&2
    exit 1
fi

echo "==> Building release distributable (JDK 21, this takes a while)..."
JAVA_HOME="$JDK21" ./gradlew createReleaseDistributable

DIST_DIR="composeApp/build/compose/binaries/main-release/app"
if [ ! -d "$DIST_DIR/Kai" ]; then
    echo "Expected distributable at $DIST_DIR/Kai, not found." >&2
    exit 1
fi

echo "==> Packaging tarball..."
mkdir -p "$MANIFEST_DIR"
tar czf "$MANIFEST_DIR/Kai-linux-x86_64.tar.gz" -C "$DIST_DIR" Kai
SHA256=$(sha256sum "$MANIFEST_DIR/Kai-linux-x86_64.tar.gz" | cut -d' ' -f1)

echo "==> Preparing dev manifest ($APP_ID)..."
cp flatpak/io.github.simonschubert.Kai.desktop "$MANIFEST_DIR/$APP_ID.desktop"
cp flatpak/io.github.simonschubert.Kai.metainfo.xml "$MANIFEST_DIR/$APP_ID.metainfo.xml"
cp flatpak/io.github.simonschubert.Kai.yaml "$MANIFEST_DIR/$APP_ID.yaml"
cp composeApp/icon.svg composeApp/icon.png "$MANIFEST_DIR/"

sed -i "s/^Icon=io.github.simonschubert.Kai\$/Icon=$APP_ID/" "$MANIFEST_DIR/$APP_ID.desktop"
sed -i "s/^Name=Kai 9000\$/Name=Kai 9000 (Dev Test)/" "$MANIFEST_DIR/$APP_ID.desktop"

sed -i "s|<id>io.github.simonschubert.Kai</id>|<id>$APP_ID</id>|" "$MANIFEST_DIR/$APP_ID.metainfo.xml"
sed -i "s|io.github.simonschubert.Kai.desktop|$APP_ID.desktop|" "$MANIFEST_DIR/$APP_ID.metainfo.xml"

sed -i "s/^app-id: io.github.simonschubert.Kai\$/app-id: $APP_ID/" "$MANIFEST_DIR/$APP_ID.yaml"
sed -i "s|io.github.simonschubert.Kai.desktop|$APP_ID.desktop|g" "$MANIFEST_DIR/$APP_ID.yaml"
sed -i "s|io.github.simonschubert.Kai.metainfo.xml|$APP_ID.metainfo.xml|g" "$MANIFEST_DIR/$APP_ID.yaml"
sed -i "s|hicolor/scalable/apps/io.github.simonschubert.Kai.svg|hicolor/scalable/apps/$APP_ID.svg|" "$MANIFEST_DIR/$APP_ID.yaml"
sed -i "s|hicolor/512x512/apps/io.github.simonschubert.Kai.png|hicolor/512x512/apps/$APP_ID.png|" "$MANIFEST_DIR/$APP_ID.yaml"
sed -i "s|url: https://github.com/SimonSchubert/Kai/releases/download/.*|path: Kai-linux-x86_64.tar.gz|" "$MANIFEST_DIR/$APP_ID.yaml"
sed -i "/dest-filename: Kai-linux-x86_64.tar.gz/d" "$MANIFEST_DIR/$APP_ID.yaml"
sed -i "s|sha256: .*|sha256: $SHA256|" "$MANIFEST_DIR/$APP_ID.yaml"
sed -i "/x-checker-data:/,/url-query:.*/d" "$MANIFEST_DIR/$APP_ID.yaml"
sed -i "s|path: \.\./composeApp/icon\.svg|path: icon.svg|" "$MANIFEST_DIR/$APP_ID.yaml"
sed -i "s|path: \.\./composeApp/icon\.png|path: icon.png|" "$MANIFEST_DIR/$APP_ID.yaml"

echo "==> Running flatpak-builder..."
(
    cd "$MANIFEST_DIR"
    # The final export step has hit a transient "syncfs: Interrupted system
    # call" once in testing (unrelated to the build itself) -- retry once.
    flatpak-builder --user --repo="$REPO_DIR" --force-clean build-dir "$APP_ID.yaml" \
        || flatpak-builder --user --repo="$REPO_DIR" --force-clean build-dir "$APP_ID.yaml"
)

echo "==> Installing/updating $APP_ID..."
# Always point the remote at the current REPO_DIR (stable path across runs,
# so this doesn't need special-casing "remote already exists but stale").
flatpak remote-delete --user --force "$REMOTE_NAME" >/dev/null 2>&1 || true
flatpak remote-add --user --no-gpg-verify "$REMOTE_NAME" "$REPO_DIR"

if flatpak info --user "$APP_ID" >/dev/null 2>&1; then
    flatpak update --user -y "$APP_ID"
else
    flatpak install --user -y "$REMOTE_NAME" "$APP_ID"
fi

echo
echo "Done. Run it with: flatpak run $APP_ID"
