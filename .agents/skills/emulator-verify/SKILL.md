---
name: emulator-verify
description: Prove a PennyWise Android change works on an emulator by building the debug app, seeding synthetic SMS transactions, driving the affected flow in light and dark, and reporting PASS or BLOCKED with screenshots and crash logs. Use after any runtime or UI change, and route Room schema/migration changes to the API 26 upgrade check.
---

# Verify on an emulator

This is the "on-device check" row of the Verification table in [AGENTS.md](../../../AGENTS.md).
Before you start, write down three things: what changed, the flow to exercise
(entry point, steps, expected result), and whether the Room schema or
`data/database/` changed.

## Decide the route

- **Schema/migration change:** run "Old Android runs old SQLite" in
  [docs/database-migrations.md](../../../docs/database-migrations.md) on the
  `Api26_Migration` AVD. A fresh install or a screenshot doesn't prove a
  migration. You need data written by the old build, a real `install -r`
  upgrade, and checks on that data afterwards: rows still there, values
  correct, `PRAGMA user_version` equal to the new `SCHEMA_VERSION`. If there's
  UI too, also do the steps below.
- **Runtime/UI change:** follow the steps below.
- **Parser-only change:** `./init.sh parser` is the gate. Use the probe (step 4)
  only to see how a parsed row renders.

## 1. Gate and build

```bash
./init.sh app
./gradlew :app:assembleStandardDebug   # with JAVA_HOME = the JDK 21 init.sh reported
ls app/build/outputs/apk/standard/debug/
```

Build `standard`, not `fdroid`. fdroid's `abiFilters` are ARM-only, so it won't
install on an x86_64 emulator. CI's upgrade test uses `standard` for the same
reason. Install `app-standard-universal-debug.apk`, which works on any ABI. Debug
package: `com.pennywiseai.tracker.debug`. Launcher: `com.pennywiseai.tracker.MainActivity`.
A release install (`com.pennywiseai.tracker`) is a separate app. Leave it alone.

## 2. Pick the device

```bash
adb devices -l
SERIAL=emulator-5554   # an emulator from the list; a physical phone only if the user asked
```

Pass `-s "$SERIAL"` on every adb call. If the harness provides device tools
(a device panel or MCP tools, the `agent-device` CLI), drive the UI with them.
Use adb for install, logs and shell. If no emulator is running, start one
(`~/Library/Android/sdk/emulator/emulator -list-avds`, then `-avd <name>`) and
record that you started it.

## 3. Install and launch

```bash
T0=$(adb -s "$SERIAL" shell date '+%m-%d %H:%M:%S.000')
adb -s "$SERIAL" install -r app/build/outputs/apk/standard/debug/app-standard-universal-debug.apk
adb -s "$SERIAL" shell am start -n com.pennywiseai.tracker.debug/com.pennywiseai.tracker.MainActivity
```

`INSTALL_FAILED_UPDATE_INCOMPATIBLE` means the existing `.debug` install was
signed with a different debug key. Don't change signing. Uninstall only on an
emulator you started. Otherwise stop with BLOCKED and ask. On a fresh install,
finish onboarding with Skip and by declining SMS. The probe doesn't need the
SMS permission.

## 4. Seed synthetic data

Never use real SMS or personal data. The debug-only `SmsProbeReceiver`
(`app/src/debug/`) runs a made-up SMS through the real parse → save → alert
path. Launch the app once first, then:

```bash
PROBE=com.pennywiseai.tracker.debug/com.pennywiseai.tracker.debug.SmsProbeReceiver
adb -s "$SERIAL" shell "am broadcast -n $PROBE --es sender CP-HDFCBK-S --es body 'Rs.500.00 debited from A/c XX1234 on 20-Oct-25 to merchant@upi (UPI Ref No 123456789012)'"
adb -s "$SERIAL" shell "am broadcast -n $PROBE --es sender ENBD --es body 'AED 500.00 debited from A/C xxxx1234 on 24-Dec-25. Avl Bal is AED 15,234.50'"
```

Seed two currencies so totals exercise Hard Constraint 2. For other shapes,
copy synthetic cases from `parser-core/src/test`. Change the amount and
reference for each extra row, because duplicates are dropped. `--el timestamp
<epoch ms>` backdates a row, and the receiver's KDoc lists the other extras.
`scripts/randomziedata.py` and `verify_anon_smsxml.py` only anonymize parser
test XML. They don't seed the app.

## 5. Drive the flow, in light and dark

Walk the flow from your inputs with the harness tool. Fall back to adb:

```bash
adb -s "$SERIAL" shell uiautomator dump /sdcard/ui.xml && adb -s "$SERIAL" exec-out cat /sdcard/ui.xml
adb -s "$SERIAL" shell input tap <x> <y>   # centre of the node's bounds
adb -s "$SERIAL" shell input text '<ascii>'; adb -s "$SERIAL" shell input keyevent KEYCODE_BACK
```

Repeat the flow in both themes. Switch with Settings → Appearance → Light / Dark,
and note the starting value first. `cmd uimode night yes|no` works only while
Appearance is System. Read the current value with `cmd uimode night` and
restore it afterwards. Check against the accessibility checklist in [docs/design.md](../../../docs/design.md).

## 6. Evidence

Save screenshots to the harness scratchpad, or to gitignored `build/emulator-verify/`:

```bash
mkdir -p build/emulator-verify
adb -s "$SERIAL" exec-out screencap -p > build/emulator-verify/<flow>-<step>-<light|dark>.png
adb -s "$SERIAL" logcat -d -b crash -T "$T0"
adb -s "$SERIAL" logcat -d -T "$T0" --pid="$(adb -s "$SERIAL" shell pidof com.pennywiseai.tracker.debug)" | tail -n 200
```

Any `FATAL EXCEPTION` from the app since `$T0` is a FAIL. Look at every
screenshot before you report PASS.

## Safety

- Never run `adb kill-server` or any other global ADB reset. Never kill
  processes by name pattern.
- Never clear data, uninstall, or `logcat -c` on a device you didn't start.
  Don't touch signing configs or keystores.
- Restore Appearance and night mode. Stop (`adb -s "$SERIAL" emu kill`) only
  emulators you started, and only once the user is done iterating.

## Stop with BLOCKED (don't improvise) when

- The gate or build fails for reasons outside the change.
- No emulator is available or can be started.
- The install fails on signing.
- The flow needs real SMS, an account, a license or a network you don't have.
- The UI is unreachable after two different attempts.
- A migration needs `Api26_Migration` and it's missing.

## Report

```
emulator-verify: PASS | FAIL | BLOCKED
Change: <one line>   Flow: <steps>
Device: <serial>, API <n>, <abi>   APK: <path>
Checked: <what you saw, light + dark>   Data: <seeded rows>
Evidence: <screenshot paths>   Crash buffer since T0: <clean | excerpt>
Blocked by / not covered: <reason or "none">
```
