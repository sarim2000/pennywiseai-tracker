#!/bin/bash
#
# Local Google Play uploader via `fastlane supply` — no CI minutes.
# Run it AFTER scripts/release.sh has built the .aab locally.
#
# Modes:
#   ./scripts/upload-play.sh release [--dry-run]   # AAB + release notes (default)
#   ./scripts/upload-play.sh listing [--dry-run]   # screenshots + store text only
#
#   release  -> uploads the built AAB and its release notes
#               (fastlane/metadata/android/en-US/changelogs/<versionCode>.txt).
#               Does NOT touch screenshots/description, so it can't clobber a
#               listing you edit by hand. Leaves the release as a DRAFT to review
#               and roll out in Play Console.
#   listing  -> uploads title/short/full description + phone & tablet
#               screenshots + feature graphic from fastlane/metadata. No binary.
#               Use this to push refreshed screenshots instead of dragging files
#               into Play Console.
#
# Setup (one time):
#   gem install fastlane
#   Put your Play service-account JSON somewhere local and point to it:
#     export PLAY_JSON_KEY=/absolute/path/to/play-store-key.json
#   (default looked-up path: ./play-store-key.json — git-ignored)
#
# Optional env overrides:
#   PLAY_TRACK   (default: production)   PLAY_STATUS (default: draft)

set -e

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'

# Parse args in any order: mode (release|listing) and optional --dry-run.
MODE="release"
DRY=""
for arg in "$@"; do
  case "$arg" in
    release|listing) MODE="$arg" ;;
    --dry-run)       DRY="--validate_only true" ;;
    *) echo "Unknown arg: $arg"; echo "Usage: $0 [release|listing] [--dry-run]"; exit 1 ;;
  esac
done

PACKAGE="com.pennywiseai.tracker"
JSON_KEY="${PLAY_JSON_KEY:-play-store-key.json}"
TRACK="${PLAY_TRACK:-production}"
STATUS="${PLAY_STATUS:-draft}"
META_PATH="fastlane/metadata/android"

# --- preflight ---
if ! command -v fastlane >/dev/null 2>&1; then
  echo -e "${RED}❌ fastlane not found. Install it:  gem install fastlane${NC}"; exit 1
fi
if [ ! -f "$JSON_KEY" ]; then
  echo -e "${RED}❌ Play service-account key not found at: $JSON_KEY${NC}"
  echo -e "${YELLOW}   Set PLAY_JSON_KEY=/path/to/play-store-key.json${NC}"; exit 1
fi

case "$MODE" in
  release)
    VERSION=$(grep 'versionName = ' app/build.gradle.kts | sed 's/.*"\(.*\)".*/\1/')
    AAB="app/build/outputs/bundle/standardRelease/PennyWise-v${VERSION}.aab"
    [ -f "$AAB" ] || AAB=$(ls app/build/outputs/bundle/standardRelease/*.aab 2>/dev/null | head -1)
    if [ -z "$AAB" ] || [ ! -f "$AAB" ]; then
      echo -e "${RED}❌ No .aab found. Build it first (release.sh -> 'Build Play Store Bundle').${NC}"; exit 1
    fi
    echo -e "${GREEN}⬆️  Uploading AAB + release notes${NC}  (track=$TRACK status=$STATUS)"
    echo "   $AAB"
    fastlane supply \
      --package_name "$PACKAGE" \
      --json_key "$JSON_KEY" \
      --aab "$AAB" \
      --track "$TRACK" \
      --release_status "$STATUS" \
      --skip_upload_metadata true \
      --skip_upload_images true \
      --skip_upload_screenshots true \
      $DRY
    echo -e "${GREEN}✅ Done.${NC} ${YELLOW}Left as a DRAFT — review & roll out in Play Console.${NC}"
    ;;
  listing)
    echo -e "${GREEN}⬆️  Uploading store listing (text + screenshots + feature graphic)${NC}"
    fastlane supply \
      --package_name "$PACKAGE" \
      --json_key "$JSON_KEY" \
      --track "$TRACK" \
      --metadata_path "$META_PATH" \
      --skip_upload_aab true \
      --skip_upload_apk true \
      --skip_upload_changelogs true \
      $DRY
    echo -e "${GREEN}✅ Listing pushed.${NC} ${YELLOW}Store-listing changes go to Google review.${NC}"
    ;;
  *)
    echo "Usage: $0 [release|listing] [--dry-run]"; exit 1 ;;
esac
