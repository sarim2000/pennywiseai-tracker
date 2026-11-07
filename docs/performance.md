# Performance: Baseline Profiles and Macrobenchmark

`:baselineprofile` holds a profile generator and a Macrobenchmark for two journeys:
cold start to Home, then opening Transactions and scrolling it on All Time. Nothing
here runs during a normal build: `assembleDebug`, `./init.sh` and `scripts/release.sh`
never touch the module or a device.

**No app profile is committed today.** Release APKs ship only the rules libraries
(Compose and others) bring with them. See [Last measurement](#last-measurement) for why.

## Variants and seed data

The `androidx.baselineprofile` plugin adds two profileable build types, both with the
applicationId `com.pennywiseai.tracker.benchmark`, so they install beside any real build:

- `nonMinifiedRelease` generates profiles.
- `benchmarkRelease` is minified like release and is what gets measured.

Only those two compile `app/src/benchmarkSeed/`: a receiver that skips onboarding and
writes 1,800 synthetic rows in INR, USD and AED. It is guarded by the `DUMP` permission,
and the tests call it through `am broadcast`. `release` has neither the seed nor
`isProfileable`.

## Prerequisites

- An API 34+ device or emulator. Below 34, resetting compilation reinstalls the app,
  which wipes the seed, so the journeys fail.
- At least 4 GB of RAM and a few GB free on `/data`.
- Device language set to English. The journeys find the period filters by their English labels.
- Set `ANDROID_SERIAL`. Connected tasks otherwise run on every attached device.
- Nothing else may hold UiAutomation (agent-device's snapshot helper does). Android allows
  one per system, and tests fail with `UiAutomationService ... already registered`.

## Regenerate the profile

```sh
ANDROID_SERIAL=<serial> ./gradlew :app:generateBaselineProfile
```

Runs both flavors and merges them into `app/src/main/generated/baselineProfiles/`
(`baseline-prof.txt`, `startup-prof.txt`). `startup-prof.txt` also makes R8 move startup
classes into `classes.dex`, with no further configuration.

Regenerate when Home, Transactions or the startup path change substantially, or after a
Compose, Kotlin or AGP upgrade, and re-measure before committing the result.

## Run the benchmarks

Use a physical device. On an emulator, add both of these, or the tests are skipped:
`-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR`
`-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.skipBenchmarksOnEmulator=false`

```sh
ANDROID_SERIAL=<serial> ./gradlew :baselineprofile:connectedStandardBenchmarkReleaseAndroidTest
```

Results land in `baselineprofile/build/outputs/connected_android_test_additional_output/`.

## Comparing profiles: use two APKs, not two modes

The question worth asking is library rules alone versus library rules plus ours.
No `CompilationMode` answers it, because `Require` and `Disable` differ in whether
*any* baseline profile is installed:

- `Partial(Require)` installs the APK's whole `baseline.prof`: library rules plus ours.
- `Partial(Disable)` installs none of it, library rules included, and its constructor
  throws unless `warmupIterations > 0`. The `*WarmupOnly` tests use it to show what a
  JIT-only install reaches after a few launches.

So build `benchmarkRelease` with and without `app/src/main/generated/`, run the
`*Profile` tests on each under `Partial(Require)`, and alternate the APKs across rounds
so drift doesn't favor one of them.

## Last measurement

November 2026, API 36 emulator on a busy host, 4 rounds per APK of 10 iterations
(some rounds kept 9). Per-round medians:

| | Library rules only | Library + ours | No compilation (control) |
|---|---|---|---|
| Time to initial display (ms) | 716 to 1,256 | 718 to 1,486 | 647 |
| Time to full display (ms) | 1,844 to 3,993 | 2,334 to 4,517 | 1,941 |
| Scroll frame CPU P50 (ms) | 142 to 193 | 143 to 185 | 143 |

Inconclusive, not evidence against profiles. The control, with no AOT compilation at
all, matched both, so this rig cannot detect compilation differences. Only a physical
device can settle this, so the profile stays out until one shows a gain.

With the profile the fdroid APK grew by 246 KB (0.55%) and `classes*.dex` by 543 KB.
Clean `fdroidRelease` builds were byte-identical with and without it, on this machine.
F-Droid documents `baseline.prof` as sometimes non-deterministic, so re-check when a
profile is added: https://f-droid.org/docs/Reproducible_Builds/
