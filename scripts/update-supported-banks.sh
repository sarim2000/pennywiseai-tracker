#!/usr/bin/env bash
# Regenerates the supported-banks catalogue (docs/supported-banks.json + the
# README marker block) from the live BankParserFactory registry.
#
# Run this after adding/removing/renaming a bank parser. CI runs the same
# generator in assert mode (parser-core:jvmTest), so a stale catalogue fails
# the build with a pointer back to this script.
set -euo pipefail
cd "$(dirname "$0")/.."

UPDATE_SUPPORTED_BANKS=true ./gradlew :parser-core:jvmTest \
  --tests "com.pennywiseai.parser.core.SupportedBanksDocTest" \
  --rerun-tasks

echo "Updated docs/supported-banks.json and the README supported-banks block."
