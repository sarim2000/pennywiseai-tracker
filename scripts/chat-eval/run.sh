#!/bin/bash
# Usage: ./run.sh <model.litertlm> <shape> [key=value...]   (see src/main/kotlin/Eval.kt)
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
launcher="$here/build/install/chat-eval/bin/chat-eval"
"$here/../../gradlew" -p "$here" -q installDist
cd "$here"
exec "$launcher" "$@"
