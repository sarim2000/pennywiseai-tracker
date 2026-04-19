#!/usr/bin/env bash
# Regenerate Android launcher mipmaps from the iOS master icon (CLI).
# Requires ImageMagick 7+ (`magick`) or 6.x (`convert`) with WebP support.
#
# Usage (from anywhere):
#   /Users/sarim/dev/personal/pennywiseai-tracker/scripts/generate_android_launcher_icons.sh
#
# Or from repo root:
#   ./scripts/generate_android_launcher_icons.sh
#
# Optional env:
#   SOURCE_PNG   — override master image path (default: iOS 1024 app icon)
#   BG_FUZZ      — fuzz % for making near-white background transparent (default: 22)

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE_PNG="${SOURCE_PNG:-$ROOT/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/icon_1024x1024.png}"
RES="$ROOT/app/src/main/res"
BG_FUZZ="${BG_FUZZ:-22}"
TMPDIR="${TMPDIR:-/tmp}"
WORK="$TMPDIR/pennywise_launcher_icons_$$"
mkdir -p "$WORK"

if command -v magick >/dev/null 2>&1; then
  magick() { command magick "$@"; }
elif command -v convert >/dev/null 2>&1; then
  magick() { command convert "$@"; }
else
  echo "ImageMagick not found. Install on macOS:"
  echo "  brew install imagemagick"
  exit 1
fi

if [[ ! -f "$SOURCE_PNG" ]]; then
  echo "Source image not found: $SOURCE_PNG"
  exit 1
fi

# Master with light background keyed out (adaptive foreground should be transparent outside the logo).
magick "$SOURCE_PNG" -fuzz "${BG_FUZZ}%" -transparent white "$WORK/keyed.png"

write_density() {
  local name="$1" canvas108="$2" canvas48="$3"
  local dir="$RES/mipmap-${name}"
  mkdir -p "$dir"

  local inner108=$(( canvas108 * 66 / 108 ))
  local inner48=$(( canvas48 * 2 / 3 ))
  if (( inner48 < 1 )); then inner48=1; fi

  local fg="$dir/ic_launcher_foreground.webp"
  local leg="$dir/ic_launcher.webp"
  local rnd="$dir/ic_launcher_round.webp"
  local mono="$dir/ic_launcher_monochrome.webp"

  # Adaptive foreground: 108dp square, logo within ~66dp safe zone (centered).
  magick -size "${canvas108}x${canvas108}" xc:none \
    \( "$WORK/keyed.png" -resize "${inner108}x${inner108}>" \) -gravity center -compose over -composite \
    "$fg"

  # Themed monochrome: same alpha as foreground, RGB forced to white (Android tints this layer).
  magick "$fg" -channel RGB -evaluate set 100% +channel "$mono"

  # Legacy square launcher (48dp) on solid background matching values/ic_launcher_background (#FFFFFF).
  magick -size "${canvas48}x${canvas48}" xc:'#FFFFFF' \
    \( "$WORK/keyed.png" -resize "${inner48}x${inner48}>" \) -gravity center -compose over -composite \
    "$leg"

  # Round legacy: circular clip (DstIn with white disc alpha).
  local cx=$(( canvas48 / 2 ))
  local cy=$(( canvas48 / 2 ))
  magick "$leg" \( -size "${canvas48}x${canvas48}" xc:none -fill white -draw "circle ${cx},${cy} ${cx},0" \) \
    -compose DstIn -composite "$rnd"

  echo "Wrote $name → $fg (+ legacy + monochrome)"
}

write_density mdpi 108 48
write_density hdpi 162 72
write_density xhdpi 216 96
write_density xxhdpi 324 144
write_density xxxhdpi 432 192

rm -rf "$WORK"

echo ""
echo "Done. Rebuild and reinstall the app, then toggle Themed icons in Wallpaper & style to verify."
