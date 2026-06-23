#!/usr/bin/env bash
# Regenerates the supported-banks catalogue (docs/supported-banks.json + the
# README marker block) from the live BankParserFactory registry.
#
# Run this after adding/removing/renaming a bank parser. CI runs the same
# generator in assert mode (parser-core:jvmTest), so a stale catalogue fails
# the build with a pointer back to this script.
set -euo pipefail
cd "$(dirname "$0")/.."

# --rerun-tasks is required: without it Gradle treats the test as UP-TO-DATE on
# repeat runs and skips it, so the generated files would not be rewritten. Only
# this on-demand regen uses it; the CI staleness guard runs the test normally.
UPDATE_SUPPORTED_BANKS=true ./gradlew :parser-core:jvmTest \
  --tests "com.pennywiseai.parser.core.SupportedBanksDocTest" \
  --rerun-tasks

echo "Updated docs/supported-banks.json and the README supported-banks block."
