#!/usr/bin/env bash
set -euo pipefail

package=com.pennywiseai.tracker.debug
result="$(adb shell am broadcast -f 32 -n "$package/com.pennywiseai.tracker.debug.DemoSeedReceiver")"
printf '%s\n' "$result"
[[ "$result" == *"Broadcast completed: result=-1"* ]]

# An already-open app keeps its onboarding back stack, so restart it onto Home.
if [[ "$result" == *"Demo data seeded"* ]]; then
    adb shell am force-stop "$package"
    adb shell am start -W -n "$package/com.pennywiseai.tracker.MainActivity" >/dev/null
fi
