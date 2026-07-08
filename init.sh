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
# Gradle here needs a JDK 21 (matches CI). Find one and verify its major
# version rather than trusting whatever JAVA_HOME happens to point at — a stray
# JDK 17/11 would otherwise fail the build with a cryptic error, and older
# Android Studio JBRs are 17, not 21.

jdk_major() {
  # Print the major version of the JDK at $1 (e.g. "21"), or nothing if unusable.
  local home="$1"
  [[ -n "$home" && -x "$home/bin/java" ]] || return 1
  "$home/bin/java" -version 2>&1 | awk -F'"' '/version/ {print $2; exit}' | cut -d. -f1
}

find_jdk21() {
  local c major
  local -a candidates=()
  # 1. An already-correct JAVA_HOME wins (no surprise switching).
  [[ -n "${JAVA_HOME:-}" ]] && candidates+=("$JAVA_HOME")
  # 2. macOS: ask the OS for a registered JDK 21.
  if [[ -x /usr/libexec/java_home ]]; then
    c="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
    [[ -n "$c" ]] && candidates+=("$c")
  fi
  # 3. Android Studio's bundled JBR (used only if it is actually 21).
  candidates+=("/Applications/Android Studio.app/Contents/jbr/Contents/Home")
  # 4. Common SDKMAN install.
  candidates+=("${SDKMAN_DIR:-$HOME/.sdkman}/candidates/java/current")

  for c in "${candidates[@]}"; do
    major="$(jdk_major "$c" || true)"
    if [[ "$major" == "21" ]]; then
      printf '%s\n' "$c"
      return 0
    fi
  done
  return 1
}

if JDK21="$(find_jdk21)"; then
  if [[ "${JAVA_HOME:-}" != "$JDK21" ]]; then
    echo "→ Using JDK 21 at: $JDK21"
    export JAVA_HOME="$JDK21"
  fi
else
  echo "⚠️  No JDK 21 found. CI builds on JDK 21 and Gradle here needs it too." >&2
  if [[ -n "${JAVA_HOME:-}" ]]; then
    echo "    JAVA_HOME points at a JDK $(jdk_major "$JAVA_HOME" 2>/dev/null || echo '?'), not 21." >&2
  fi
  echo "    Install one (e.g. Temurin 21, or Android Studio's JBR) and either set" >&2
  echo "    JAVA_HOME to it or make it discoverable via /usr/libexec/java_home, then re-run." >&2
  exit 1
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
