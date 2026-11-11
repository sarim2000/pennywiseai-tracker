#!/usr/bin/env bash
#
# Device performance report: cold start, Transactions scrolling, memory and APK size.
# Runs the Macrobenchmarks on one connected device, then prints a Markdown report.
#
# Usage:
#   ANDROID_SERIAL=<serial> scripts/perf-report.sh           # report only, never fails on numbers
#   ANDROID_SERIAL=<serial> scripts/perf-report.sh --gate    # also fail on docs/perf-thresholds.json
#
# The gate only checks devices listed in the thresholds file (keyed by model). Device
# requirements (API 34+, English, nothing else holding UiAutomation): docs/performance.md.

set -euo pipefail
cd "$(dirname "$0")/.."

: "${ANDROID_SERIAL:?set ANDROID_SERIAL to the device to measure (adb devices)}"

results=baselineprofile/build/outputs/connected_android_test_additional_output
rm -rf "$results"

# The profile-installed tests only; the *WarmupOnly ones answer a different question
# (what a JIT-only install reaches) and double the run time.
tests=com.pennywiseai.baselineprofile.PerformanceBenchmarks#startupProfile
tests+=,com.pennywiseai.baselineprofile.PerformanceBenchmarks#scrollProfile

./gradlew :baselineprofile:connectedStandardBenchmarkReleaseAndroidTest :app:assembleStandardRelease \
  -Pandroid.testInstrumentationRunnerArguments.class="$tests"

gate=()
[[ "${1:-}" == "--gate" ]] && gate=(--gate docs/perf-thresholds.json)

apks=()
for apk in app/build/outputs/apk/standard/release/*{arm64-v8a,universal}*.apk; do
  [[ -f "$apk" ]] && apks+=(--apk "$apk")
done

python3 scripts/perf_report.py --results "$results" "${apks[@]}" "${gate[@]}"
