# Performance: Baseline Profiles and Macrobenchmark

`:baselineprofile` holds a profile generator and a Macrobenchmark for two journeys:
cold start to Home, then opening Transactions and scrolling it on All Time. Nothing
here runs during a normal build: `assembleDebug`, `./init.sh` and `scripts/release.sh`
never touch the module or a device.

**An app profile is committed** in `app/src/main/generated/baselineProfiles/`, generated
and measured on a Galaxy S23 (see [Last measurement](#last-measurement)). Release APKs ship
it alongside the rules libraries (Compose and others) bring with them.

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

## Performance report

`scripts/perf-report.sh` runs `startupProfile` and `scrollProfile` on one device, builds the
standard release, and prints a Markdown report: time to initial and full display, frame CPU
time and overrun (P50/P90/P99), memory (`MemoryUsageMetric`: heap and anonymous RSS after
start, peak while scrolling, GPU), and APK and dex size.

```sh
ANDROID_SERIAL=<serial> scripts/perf-report.sh          # report only
ANDROID_SERIAL=<serial> scripts/perf-report.sh --gate   # fail on docs/perf-thresholds.json
```

The gate reads thresholds by device model (`Build.MODEL`), so it only ever applies to a
device it was calibrated on; any other device or emulator is reported, never gated. To add
a device, run the report a few times and set each limit above the slowest run.

CI (`.github/workflows/perf-report.yml`) is report-only, never a required check:
- **APK Size**, on pull requests touching app code or dependencies: builds the unsigned
  standard release and writes arm64 and universal APK and dex sizes to the job summary.
- **Emulator Benchmarks**, weekly and on demand: the same two benchmarks on an API 34
  emulator, with results uploaded as an artifact. Emulator timings drift with the host,
  so compare them only with earlier runs of that job.

## Compose stability

`compose_compiler_config.conf` marks `BigDecimal`, `BigInteger`, `java.time.*` and `Locale`
stable. Entities made of them, like `TransactionEntity`, become stable too, so a row skips
recomposition when Room re-emits an equal row. List only truly immutable types there.
Stability reports: `./gradlew :app:compileStandardReleaseKotlin -PcomposeReports=true`,
written to `app/build/compose-reports/`.

## Leaks and main-thread I/O

Debug builds run LeakCanary, and StrictMode logs disk and network access on the main thread
(`adb logcat | grep StrictMode`). After a UI change: open the screens it touches, rotate,
leave the app, then check `adb logcat -s LeakCanary` for retained objects and
`adb shell dumpsys meminfo <package>` for `Activities: 0` once the app is closed.

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

November 2026, Galaxy S23 (SM-S911U1, Android 17), 1,800 seeded rows. Three benchmark APKs
alternated across two rounds of 10 iterations each (the order was reversed in round two):
A is `main` before the November performance pass, B is that pass without an app profile,
and C is B plus the app profile. The numbers are the ranges of per-round medians.

| | A: main | B: fixes | C: fixes + profile |
|---|---|---|---|
| Time to initial display (ms) | 268 to 440 | 437 | 411 to 432 |
| Time to full display (ms) | 597 to 648 | 569 to 601 | 542 to 544 |
| Scroll frame CPU P50 / P90 (ms) | 10.5 / 16.8 | 10.4 / 16.7 | 10.0 / 16.2 |
| Scroll frame CPU P99 (ms) | 33.3 to 33.5 | 30.1 to 30.8 | 27.1 to 29.1 |
| Frames over deadline while scrolling | 35.6% | 34.1% | 26.2% |
| Java heap after start (MB) | 52 to 54 | 38 to 38.5 | 37.5 |
| Peak anonymous RSS while scrolling (MB) | 93 to 94 | 80 to 82 | 80 |

The profile is what moves time to full display and scroll jank. The fixes are what cut
memory, mainly because Home no longer loads every transaction just to count it. A fresh
install still takes about 420 ms to first display, against about 240 ms once the JIT has
warmed up. The first frame is the cost here: its RenderThread work takes about 170 ms,
compiling shaders and drawing the offscreen layer behind the blurred bottom bar.

Two clean `fdroidRelease` builds with the profile were byte-identical, so F-Droid
reproducibility holds (https://f-droid.org/docs/Reproducible_Builds/).

An earlier emulator run (API 36, busy host) couldn't tell any compilation mode from no
compilation at all, which is why the profile waited for a physical device.
