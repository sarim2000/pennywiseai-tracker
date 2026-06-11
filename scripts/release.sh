#!/bin/bash

# Local release script that replicates .github/workflows/release.yml
# Usage: ./scripts/release.sh [patch|minor|major] [--yes] [--play] [--no-claude] [--web] [--dry-run]
#
# One-shot headless release (build + Claude notes + tag + push + GitHub release
# + Play Console draft upload):
#   ./scripts/release.sh patch --yes --play

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Global variables for cleanup
CHANGELOG_FILE=""
CHANGELOG_DIR="fastlane/metadata/android/en-US/changelogs"
CHANGES_MADE=false

# Cleanup function for interruptions
cleanup_on_exit() {
    if [ "$CHANGES_MADE" = true ]; then
        echo ""
        echo -e "${YELLOW}⚠️  Script interrupted. Reverting changes...${NC}"
        git checkout -- app/build.gradle.kts 2>/dev/null || true
        if [ -n "$CHANGELOG_FILE" ] && [ -f "$CHANGELOG_FILE" ]; then
            rm -f "$CHANGELOG_FILE"
        fi
        if [ -f "$CHANGELOG_DIR/default.txt" ]; then
            git checkout -- "$CHANGELOG_DIR/default.txt" 2>/dev/null || rm -f "$CHANGELOG_DIR/default.txt"
        fi
        echo -e "${YELLOW}Changes reverted.${NC}"
    fi
}

# Set trap for cleanup on exit
trap cleanup_on_exit EXIT INT TERM

# Parse arguments (order-independent): bump keyword + flags.
#   -y, --yes     auto-confirm every prompt (non-interactive / CI)
#   --play        after building the .aab, upload it to Play Console as a draft
#                 via scripts/upload-play.sh (implies building the bundle)
#   --no-claude   skip Claude release-note generation, use the commit-list format
#   --web         also deploy pennywise-web to Cloudflare
#   --dry-run     print the plan + release notes, change nothing
VERSION_BUMP="patch"
DRY_RUN=""
AUTO_YES=""
DO_PLAY=""
NO_CLAUDE=""
DO_WEB=""
for arg in "$@"; do
    case "$arg" in
        patch|minor|major) VERSION_BUMP="$arg" ;;
        -y|--yes)          AUTO_YES="true" ;;
        --play)            DO_PLAY="true" ;;
        --no-claude)       NO_CLAUDE="true" ;;
        --web)             DO_WEB="true" ;;
        --dry-run)         DRY_RUN="true"; echo -e "${YELLOW}🔍 DRY RUN MODE${NC}" ;;
        *) echo -e "${RED}Unknown argument: $arg${NC}"
           echo "Usage: $0 [patch|minor|major] [--yes] [--play] [--no-claude] [--web] [--dry-run]"
           exit 1 ;;
    esac
done

echo -e "${GREEN}🚀 Starting release (${VERSION_BUMP} bump)${NC}"

# 1. Get current version
CURRENT_VERSION=$(grep "versionName = " app/build.gradle.kts | sed 's/.*"\(.*\)".*/\1/')
CURRENT_CODE=$(grep "versionCode = " app/build.gradle.kts | head -1 | sed 's/[^0-9]*//g')
echo "Current version: $CURRENT_VERSION (code: $CURRENT_CODE)"

# 2. Calculate next version
IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT_VERSION"

if [ "$VERSION_BUMP" = "major" ]; then
    MAJOR=$((MAJOR + 1))
    MINOR=0
    PATCH=0
elif [ "$VERSION_BUMP" = "minor" ]; then
    MINOR=$((MINOR + 1))
    PATCH=0
elif [ "$VERSION_BUMP" = "patch" ]; then
    PATCH=$((PATCH + 1))
fi

NEXT_VERSION="$MAJOR.$MINOR.$PATCH"
NEXT_CODE=$((CURRENT_CODE + 1))
echo "Next version: $NEXT_VERSION (code: $NEXT_CODE)"

# 3. Check if tag exists
TAG_NAME="v$NEXT_VERSION"
if git rev-parse "$TAG_NAME" >/dev/null 2>&1; then
    echo -e "${RED}❌ Tag $TAG_NAME already exists locally${NC}"
    exit 1
fi

# 4. Generate changelog
LAST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "")

# Generate release notes via the Claude Agent SDK with schema-enforced
# structured output. Runs on the local Claude subscription (same auth as the
# `claude` CLI — no API key). One call returns {summary, highlights[]}; both the
# GitHub notes and the F-Droid/Play changelog are formatted from it below, so
# there is never any stray preamble or markdown fence to strip. Needs node + jq;
# falls back to the commit-list format otherwise (or with --no-claude).
NOTES_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/release-notes"
NOTES_JSON=""
USE_CLAUDE=false

if [ -z "$NO_CLAUDE" ] && [ -n "$LAST_TAG" ] && command -v node &> /dev/null && command -v jq &> /dev/null; then
    PROCEED_AI=true
    if [ -z "$AUTO_YES" ]; then
        echo ""
        read -p "Generate release notes with Claude? (y/n) " -n 1 -r
        echo
        [[ $REPLY =~ ^[Yy]$ ]] || PROCEED_AI=false
    fi

    if [ "$PROCEED_AI" = true ]; then
        echo -e "${YELLOW}🤖 Generating release notes with the Claude Agent SDK...${NC}"

        # Install the generator's dependency on first use.
        if [ ! -d "$NOTES_DIR/node_modules" ]; then
            (cd "$NOTES_DIR" && npm install --silent) || echo -e "${YELLOW}⚠️  npm install failed${NC}"
        fi

        COMMITS=$(git log "$LAST_TAG"..HEAD --pretty=format:"- %s")
        NOTES_JSON=$(printf '%s' "$COMMITS" \
            | RELEASE_VERSION="$NEXT_VERSION" \
              RELEASE_NOTES_MODEL="${RELEASE_NOTES_MODEL:-claude-sonnet-4-6}" \
              node "$NOTES_DIR/index.mjs" 2>/dev/null)

        if [ -n "$NOTES_JSON" ] && printf '%s' "$NOTES_JSON" | jq -e . >/dev/null 2>&1; then
            USE_CLAUDE=true
        else
            echo -e "${YELLOW}⚠️  AI generation failed, falling back to standard format${NC}"
            NOTES_JSON=""
        fi
    fi
fi

# Build the GitHub RELEASE_NOTES.md from the structured notes.
if [ "$USE_CLAUDE" = true ]; then
    SUMMARY=$(printf '%s' "$NOTES_JSON" | jq -r '.summary // ""')
    {
        echo "# Release v$NEXT_VERSION"
        echo ""
        if [ -n "$SUMMARY" ]; then
            echo "$SUMMARY"
            echo ""
        fi
        echo "## What's New"
        printf '%s' "$NOTES_JSON" | jq -r '.highlights[]? | "- \(.)"'
        echo ""
        echo "---"
        echo "### Installation"
        echo "Download the APK below and install it on your Android device."
    } > RELEASE_NOTES.md
    echo -e "${GREEN}✅ AI-powered release notes generated${NC}"
fi

# Fallback to standard changelog if Claude not used or failed
if [ "$USE_CLAUDE" = false ]; then
    echo "# Release v$NEXT_VERSION" > RELEASE_NOTES.md
    echo "" >> RELEASE_NOTES.md

    if [ -n "$LAST_TAG" ]; then
        echo "## Changes since $LAST_TAG" >> RELEASE_NOTES.md
        echo "" >> RELEASE_NOTES.md
        git log $LAST_TAG..HEAD --pretty=format:"- %s (%h)" >> RELEASE_NOTES.md
    else
        echo "## Initial Release" >> RELEASE_NOTES.md
        echo "" >> RELEASE_NOTES.md
        echo "First release of PennyWise" >> RELEASE_NOTES.md
    fi

    echo "" >> RELEASE_NOTES.md
    echo "---" >> RELEASE_NOTES.md
    echo "### Installation" >> RELEASE_NOTES.md
    echo "Download the APK below and install it on your Android device." >> RELEASE_NOTES.md

    echo -e "${GREEN}✅ Changelog generated${NC}"
fi

if [ "$DRY_RUN" = "true" ]; then
    echo -e "${YELLOW}🔍 DRY RUN SUMMARY${NC}"
    echo "=================="
    echo "Current version: $CURRENT_VERSION"
    echo "Next version: $NEXT_VERSION"
    echo "Version code: $NEXT_CODE"
    echo ""
    echo "📝 Release Notes:"
    cat RELEASE_NOTES.md
    exit 0
fi

# 5. Update version (versionName and versionCode)
# Use sed -i '' for macOS, sed -i for Linux
# Note: Use '/val /!' to skip lines containing "val " (avoids modifying copyChangelog task's versionCode)
if [[ "$OSTYPE" == "darwin"* ]]; then
    sed -i '' "s/versionName = \".*\"/versionName = \"$NEXT_VERSION\"/" app/build.gradle.kts
    sed -i '' '/val /!s/versionCode = .*/versionCode = '"$NEXT_CODE"'/' app/build.gradle.kts
else
    sed -i "s/versionName = \".*\"/versionName = \"$NEXT_VERSION\"/" app/build.gradle.kts
    sed -i '/val /!s/versionCode = .*/versionCode = '"$NEXT_CODE"'/' app/build.gradle.kts
fi
CHANGES_MADE=true  # Mark that we've made changes
echo -e "${GREEN}✅ Version updated: $NEXT_VERSION (code: $NEXT_CODE)${NC}"

# 5a. Update fastlane changelog
CHANGELOG_FILE="$CHANGELOG_DIR/${NEXT_CODE}.txt"

# Build the F-Droid / Play changelog from the same structured notes.
if [ "$USE_CLAUDE" = true ] && [ -n "$NOTES_JSON" ]; then
    echo -e "${YELLOW}🤖 Writing store changelog...${NC}"
    # Bullets only — no header (F-Droid and Play prepend their own "New in
    # version X"), no preamble, no fences. Keep whole bullet lines up to 500
    # characters (the Play Store changelog limit).
    printf '%s' "$NOTES_JSON" \
        | jq -r '.highlights[]? | "• \(.)"' \
        | awk 'BEGIN{n=0} { add=length($0)+1; if (n+add>500) exit; print; n+=add }' \
        > "$CHANGELOG_FILE"
    if [ -s "$CHANGELOG_FILE" ]; then
        echo -e "${GREEN}✅ Store changelog generated${NC}"
    else
        echo -e "${YELLOW}⚠️  Empty changelog, using standard format${NC}"
        USE_CLAUDE=false
    fi
fi

# Fallback to standard changelog if Claude not used
if [ "$USE_CLAUDE" = false ]; then
    # Generate simple changelog for fastlane
    if [ -n "$LAST_TAG" ]; then
        echo "Version $NEXT_VERSION" > "$CHANGELOG_FILE"
        echo "" >> "$CHANGELOG_FILE"
        # Get features
        FEATURES=$(git log $LAST_TAG..HEAD --pretty=format:"%s" | grep "^feat" 2>/dev/null | sed 's/^feat[:(].*[):] */• /' | head -5)
        FIXES=$(git log $LAST_TAG..HEAD --pretty=format:"%s" | grep "^fix" 2>/dev/null | sed 's/^fix[:(].*[):] */• /' | head -5)

        if [ -n "$FEATURES" ]; then
            echo "New Features:" >> "$CHANGELOG_FILE"
            echo "$FEATURES" >> "$CHANGELOG_FILE"
            echo "" >> "$CHANGELOG_FILE"
        fi

        if [ -n "$FIXES" ]; then
            echo "Bug Fixes:" >> "$CHANGELOG_FILE"
            echo "$FIXES" >> "$CHANGELOG_FILE"
        fi

        # If no conventional commits, just use recent commits
        if [ -z "$FEATURES" ] && [ -z "$FIXES" ]; then
            git log $LAST_TAG..HEAD --pretty=format:"• %s" | head -5 >> "$CHANGELOG_FILE"
        fi
    else
        echo "Initial release" > "$CHANGELOG_FILE"
    fi
fi

# Also update default.txt
cp "$CHANGELOG_FILE" "$CHANGELOG_DIR/default.txt"
echo -e "${GREEN}✅ Fastlane changelog created: $CHANGELOG_FILE${NC}"

# 6. Build APKs
echo -e "${YELLOW}🔨 Building APKs...${NC}"

# Function to revert changes on failure
revert_changes() {
    echo -e "${RED}❌ Build failed! Reverting changes...${NC}"

    CHANGES_MADE=false  # Reset flag so cleanup_on_exit doesn't run again

    # Revert build.gradle.kts
    git checkout -- app/build.gradle.kts

    # Remove fastlane changelog files
    if [ -f "$CHANGELOG_FILE" ]; then
        rm -f "$CHANGELOG_FILE"
    fi
    if [ -f "$CHANGELOG_DIR/default.txt" ]; then
        git checkout -- "$CHANGELOG_DIR/default.txt" 2>/dev/null || rm -f "$CHANGELOG_DIR/default.txt"
    fi

    echo -e "${YELLOW}⚠️  Changes reverted. Please fix the build errors and try again.${NC}"
    exit 1
}

# Try to build with error handling
if ! ./gradlew clean; then
    revert_changes
fi

# Build parser-core module first to ensure it compiles
echo -e "${YELLOW}🔧 Building parser-core module...${NC}"
if ! ./gradlew :parser-core:build; then
    revert_changes
fi
echo -e "${GREEN}✅ Parser-core module built${NC}"

if ! ./gradlew assembleStandardRelease; then
    revert_changes
fi

if ! ./gradlew assembleFdroidRelease; then
    revert_changes
fi

echo -e "${GREEN}✅ APKs built${NC}"

# 7. Rename APKs (matching GitHub Actions)
STANDARD_PATH="app/build/outputs/apk/standard/release"
FDROID_PATH="app/build/outputs/apk/fdroid/release"

# Rename universal APK
if [ -f "$STANDARD_PATH/app-standard-universal-release.apk" ]; then
    mv "$STANDARD_PATH/app-standard-universal-release.apk" \
       "$STANDARD_PATH/PennyWise-v${NEXT_VERSION}-universal.apk"
fi

# Rename architecture-specific APKs
for arch in armeabi-v7a arm64-v8a x86 x86_64; do
    if [ -f "$STANDARD_PATH/app-standard-${arch}-release.apk" ]; then
        mv "$STANDARD_PATH/app-standard-${arch}-release.apk" \
           "$STANDARD_PATH/PennyWise-v${NEXT_VERSION}-${arch}.apk"
    fi
done

# Rename F-Droid APK
if [ -f "$FDROID_PATH/app-fdroid-release-unsigned.apk" ]; then
    mv "$FDROID_PATH/app-fdroid-release-unsigned.apk" \
       "$FDROID_PATH/PennyWise-fdroid-v${NEXT_VERSION}.apk"
fi

echo -e "${GREEN}✅ APKs renamed${NC}"

# 8. Calculate SHA256
ORIGINAL_DIR="$PWD"
cd "$STANDARD_PATH"
for apk in PennyWise-v${NEXT_VERSION}*.apk; do
    if [ -f "$apk" ]; then
        sha256sum "$apk" > "${apk}.sha256"
    fi
done

cd "$ORIGINAL_DIR/$FDROID_PATH"
if [ -f "PennyWise-fdroid-v${NEXT_VERSION}.apk" ]; then
    sha256sum "PennyWise-fdroid-v${NEXT_VERSION}.apk" > "PennyWise-fdroid-v${NEXT_VERSION}.apk.sha256"
fi
cd "$ORIGINAL_DIR"

echo -e "${GREEN}✅ SHA256 calculated${NC}"

# 9. Commit and tag
if [ -f "app/build.gradle.kts" ]; then
    git add app/build.gradle.kts
else
    echo -e "${RED}Error: app/build.gradle.kts not found${NC}"
    exit 1
fi

if [ -f "$CHANGELOG_FILE" ]; then
    git add "$CHANGELOG_FILE"
fi

if [ -f "$CHANGELOG_DIR/default.txt" ]; then
    git add "$CHANGELOG_DIR/default.txt"
fi

git commit -m "chore(release): bump version to $NEXT_VERSION [skip ci]"
git tag -a "v$NEXT_VERSION" -m "Release v$NEXT_VERSION"
CHANGES_MADE=false  # Changes are now committed, no need to revert
echo -e "${GREEN}✅ Commit and tag created${NC}"

# 10. Push to GitHub
if [ -n "$AUTO_YES" ]; then
    REPLY=y
else
    echo ""
    read -p "Push to GitHub? (y/n) " -n 1 -r
    echo
fi
if [[ $REPLY =~ ^[Yy]$ ]]; then
    git push origin main
    git push origin "v$NEXT_VERSION"
    echo -e "${GREEN}✅ Pushed to GitHub${NC}"
    
    # Create GitHub release with gh CLI
    if command -v gh &> /dev/null; then
        echo "Creating GitHub release..."
        gh release create "v$NEXT_VERSION" \
            --title "Release v$NEXT_VERSION" \
            --notes-file RELEASE_NOTES.md \
            "$STANDARD_PATH/PennyWise-v${NEXT_VERSION}-universal.apk" \
            "$STANDARD_PATH/PennyWise-v${NEXT_VERSION}-universal.apk.sha256" \
            "$STANDARD_PATH/PennyWise-v${NEXT_VERSION}-arm64-v8a.apk" \
            "$STANDARD_PATH/PennyWise-v${NEXT_VERSION}-arm64-v8a.apk.sha256" \
            "$STANDARD_PATH/PennyWise-v${NEXT_VERSION}-armeabi-v7a.apk" \
            "$STANDARD_PATH/PennyWise-v${NEXT_VERSION}-armeabi-v7a.apk.sha256" \
            "$FDROID_PATH/PennyWise-fdroid-v${NEXT_VERSION}.apk" \
            "$FDROID_PATH/PennyWise-fdroid-v${NEXT_VERSION}.apk.sha256"
        echo -e "${GREEN}✅ GitHub release created${NC}"
    else
        echo -e "${YELLOW}gh CLI not found. Create release manually at:${NC}"
        echo "https://github.com/sarim2000/pennywiseai-tracker/releases/new?tag=v$NEXT_VERSION"
    fi
fi

# 11. Build Play Store Bundle (and optionally upload to Play Console)
# --play implies building the bundle (we need the .aab to upload).
if [ -n "$AUTO_YES" ] || [ -n "$DO_PLAY" ]; then
    REPLY=y
else
    echo ""
    read -p "Build Play Store Bundle (.aab)? (y/n) " -n 1 -r
    echo
fi
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}🔨 Building App Bundle for Play Store...${NC}"
    ./gradlew bundleStandardRelease
    
    # Rename AAB file
    AAB_PATH="app/build/outputs/bundle/standardRelease"
    if [ -f "$AAB_PATH/app-standard-release.aab" ]; then
        mv "$AAB_PATH/app-standard-release.aab" \
           "$AAB_PATH/PennyWise-v${NEXT_VERSION}.aab"
        echo -e "${GREEN}✅ App Bundle created: $AAB_PATH/PennyWise-v${NEXT_VERSION}.aab${NC}"
        
        # Show file size
        SIZE=$(du -h "$AAB_PATH/PennyWise-v${NEXT_VERSION}.aab" | cut -f1)
        echo "Size: $SIZE"
        
        if [ -n "$DO_PLAY" ]; then
            # Direct upload to Play Console (draft) via the existing fastlane
            # wrapper. It reads the same .aab + the fastlane changelog and uses
            # secrets/play-store-key.json. Left as a DRAFT to roll out by hand.
            echo ""
            echo -e "${YELLOW}⬆️  Uploading .aab to Play Console (draft) via upload-play.sh...${NC}"
            if "$ORIGINAL_DIR/scripts/upload-play.sh" release; then
                echo -e "${GREEN}✅ Uploaded to Play Console as a DRAFT — review & roll out in Play Console.${NC}"
            else
                echo -e "${RED}❌ Play upload failed.${NC} Retry manually: ./scripts/upload-play.sh release"
            fi
        else
            echo ""
            echo -e "${YELLOW}📱 Play Store Upload Instructions:${NC}"
            echo "1. Go to https://play.google.com/console"
            echo "2. Select PennyWise app"
            echo "3. Go to Release > Production (or Testing)"
            echo "4. Create new release"
            echo "5. Upload: $AAB_PATH/PennyWise-v${NEXT_VERSION}.aab"
            echo "6. Add release notes from RELEASE_NOTES.md"
        fi
    fi
fi

# 12. Deploy webapp (optional). Only with explicit --web; plain --yes skips it
# so a routine app release never redeploys the site by surprise.
if [ -n "$DO_WEB" ]; then
    REPLY=y
elif [ -n "$AUTO_YES" ]; then
    REPLY=n
else
    echo ""
    read -p "Deploy webapp (pennywise-web) to Cloudflare? (y/n) " -n 1 -r
    echo
fi
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}🌐 Deploying webapp to Cloudflare...${NC}"

    WEBAPP_DIR="$ORIGINAL_DIR/pennywise-web"

    if [ -d "$WEBAPP_DIR" ]; then
        if [ -f "$WEBAPP_DIR/deploy.sh" ]; then
            cd "$WEBAPP_DIR"
            if bash deploy.sh; then
                echo -e "${GREEN}✅ Webapp deployed successfully${NC}"
            else
                echo -e "${RED}❌ Webapp deployment failed${NC}"
                echo -e "${YELLOW}You can retry manually: cd pennywise-web && ./deploy.sh${NC}"
            fi
            cd "$ORIGINAL_DIR"
        else
            echo -e "${RED}❌ deploy.sh not found in pennywise-web${NC}"
        fi
    else
        echo -e "${RED}❌ pennywise-web directory not found${NC}"
    fi
fi

echo ""
echo -e "${GREEN}✨ Release $NEXT_VERSION complete!${NC}"