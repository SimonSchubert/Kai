#!/usr/bin/env bash
# Convert PNGs in site/img/ to WebP for smaller payloads and better Lighthouse scores.
# Source PNGs are removed after successful conversion so the repo doesn't grow a duplicate
# tree. Social-preview images stay as PNG because some OG / Twitter Card scrapers still
# don't handle WebP reliably.
#
# Requires cwebp (brew install webp | apt-get install webp). Idempotent: re-running is
# a no-op when no PNG is present.

set -euo pipefail

cd "$(dirname "$0")/.."

EXCLUDE=("og-preview.png" "preview.png")

is_excluded() {
    local name="$1"
    for e in "${EXCLUDE[@]}"; do
        [[ "$name" == "$e" ]] && return 0
    done
    return 1
}

if ! command -v cwebp >/dev/null; then
    echo "cwebp not found. Install it with: brew install webp  (macOS) or apt-get install webp (Linux)" >&2
    exit 1
fi

shopt -s nullglob
converted=0
for png in site/img/*.png; do
    name=$(basename "$png")
    if is_excluded "$name"; then
        continue
    fi
    webp="${png%.png}.webp"
    cwebp -q 90 -metadata none -quiet "$png" -o "$webp"
    rm "$png"
    echo "Converted $name -> $(basename "$webp")"
    converted=$((converted + 1))
done

echo "Done. Converted $converted image(s)."
