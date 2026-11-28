#!/usr/bin/env bash
# Regenerates everything derived from the live BankParserFactory registry:
#
#   SupportedBanksDocTest -> docs/supported-banks.json
#                            README supported-banks block + summary bullet
#                            pennywise-web .../resources/supported-banks.json
#                            the coverage claim in the Play Store long description
#   BankSamplesDocTest    -> pennywise-web .../resources/bank-samples.json
#                            (one real sample SMS per bank + what the parser
#                             extracts, used by the per-bank landing pages)
#
# Run this after adding/removing/renaming a bank parser, or after changing a
# parser's extraction behaviour. CI runs both generators in assert mode
# (parser-core:jvmTest), so anything stale fails the build with a pointer here.
set -euo pipefail
cd "$(dirname "$0")/.."

# --rerun is required: without it Gradle treats jvmTest as UP-TO-DATE on repeat
# runs and skips it, so the generated files would not be rewritten. It reruns only
# this task, so compilation stays incremental. Only this on-demand regen uses it;
# the CI staleness guard runs the tests normally.
UPDATE_SUPPORTED_BANKS=true ./gradlew :parser-core:jvmTest \
  --tests "com.pennywiseai.parser.core.SupportedBanksDocTest" \
  --tests "com.pennywiseai.parser.core.BankSamplesDocTest" \
  --rerun

echo "Updated the supported-banks catalogue, the README block, the Play listing"
echo "coverage claim, and the per-bank parse samples."
