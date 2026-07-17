#!/usr/bin/env bash
# Remove the io.github.simonschubert.Kai.Dev app and local build cache created by
# scripts/build-dev-flatpak.sh. Does not touch your real io.github.simonschubert.Kai
# install or its data.

set -euo pipefail

APP_ID="io.github.simonschubert.Kai.Dev"
REMOTE_NAME="kai-dev-local"
BUILD_ROOT="${XDG_CACHE_HOME:-$HOME/.cache}/kai-dev-flatpak"

flatpak uninstall --user -y "$APP_ID" 2>&1 || echo "($APP_ID was not installed)"
flatpak remote-delete --user --force "$REMOTE_NAME" >/dev/null 2>&1 || true

if [ -d "$BUILD_ROOT" ]; then
    if command -v trash >/dev/null; then
        trash "$BUILD_ROOT"
    else
        echo "trash-cli not found; leaving build cache in place: $BUILD_ROOT" >&2
        echo "(install trash-cli, or remove it yourself, to reclaim the space)" >&2
    fi
fi

echo "Done."
