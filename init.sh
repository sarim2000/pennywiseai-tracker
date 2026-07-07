#!/usr/bin/env bash
#
# init.sh — the verification gate for coding agents (and humans).
#
# Runs exactly what CI (.github/workflows/test.yml) gates on, so "green here"
# means "green in CI". Prefer this over `./gradlew build` / `lint`, which are
# heavier and are NOT part of the gate.
#
# Usage:
#   ./init.sh            # verify everything (default)
#   ./init.sh parser     # parser-core JVM tests only  (fast; use for parser work)
#   ./init.sh app        # app compile + unit tests only
#   ./init.sh all        # same as no argument
#
# Exit code is non-zero if any step fails (fail-fast).

set -euo pipefail

cd "$(dirname "$0")"

# --- JDK 21 -----------------------------------------------------------------
# Gradle here needs a JDK 21. If JAVA_HOME isn't set, fall back to Android
# Studio's bundled JBR (the common local setup on macOS).
if [[ -z "${JAVA_HOME:-}" ]]; then
  ASTUDIO_JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  if [[ -d "$ASTUDIO_JBR" ]]; then
    export JAVA_HOME="$ASTUDIO_JBR"
    echo "→ JAVA_HOME not set; using Android Studio JBR: $JAVA_HOME"
  else
    echo "⚠️  JAVA_HOME is not set and Android Studio JBR was not found."
    echo "    Set JAVA_HOME to a JDK 21 and re-run."
    exit 1
  fi
fi

TARGET="${1:-all}"

run() {
  echo ""
  echo "▶ $*"
  "$@"
}

verify_parser() {
  run ./gradlew :parser-core:test
}

verify_app() {
  run ./gradlew :app:compileStandardDebugKotlin
  run ./gradlew :app:testStandardDebugUnitTest
}

case "$TARGET" in
  parser) verify_parser ;;
  app)    verify_app ;;
  all)    verify_parser; verify_app ;;
  *)
    echo "Unknown target: '$TARGET' (expected: parser | app | all)" >&2
    exit 2
    ;;
esac

echo ""
echo "✅ Verification passed ($TARGET)."
echo "   Note: runtime/UI changes still need an on-device check (emulator-verify skill)."
