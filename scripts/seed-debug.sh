#!/usr/bin/env bash
set -euo pipefail

result="$(adb shell am broadcast -f 32 -n com.pennywiseai.tracker.debug/com.pennywiseai.tracker.debug.DemoSeedReceiver)"
printf '%s\n' "$result"
[[ "$result" == *"Broadcast completed: result=-1"* ]]
