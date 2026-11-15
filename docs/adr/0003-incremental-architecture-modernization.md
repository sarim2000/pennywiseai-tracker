---
status: accepted
---

# Modernize the architecture incrementally, inside :app first

The Android app is one `:app` module of about 500 Kotlin files. Its top-level packages import each other in 23 pairs (`data` ↔ `domain`, `presentation` ↔ `ui`, `data` ↔ `worker`, and others), and feature screens are split between `presentation/` and `ui/screens/` ([architecture-baseline.md](../architecture-baseline.md)). We considered creating `core:*` and `feature:*` Gradle modules up front and moving code into them, and we considered rewriting the largest screens and ViewModels. We chose neither. The work goes in small PRs that do not change behavior, in this order: guardrails, so a forbidden import fails the build before any code moves; then feature-first packages inside `:app`; then Gradle modules, only where the package graph already shows a clean boundary. A module cannot be split off while its package still imports code that would stay behind, and a package move inside one module is cheap to review and to revert.

## Consequences

- Every refactor PR says what it covers, changes no behavior, and passes the Refactor PRs checklist in `.github/pull_request_template.md`: money stays currency-tagged, nothing is summed across currencies, backup fields keep their defaults, migrations run on SQLite 3.18, and both flavors build.
- The package rules live in [architecture.md](../architecture.md#ownership--dependency-rules). Tests that enforce them come before the moves. Existing violations are recorded and reduced over time, not fixed in one sweep. Formatting applies to touched files only.
- A move must not break anything Android or the user's device stores by class name. WorkManager saves each worker's class name with its queued and periodic work. Placed widgets, added Quick Settings tiles, granted notification-listener access and the launcher icon are bound to component names declared in `AndroidManifest.xml` and `res/xml/*_widget_info.xml`. These classes stay where they are, or move only with a plan for existing installs. Room table and column names, DataStore keys and backup field names stay unchanged.
- The first moves are the cheap ones the baseline exposes: non-UI code that lives in screen packages but is used by `data`, `domain`, `worker` or `widget` (for example `presentation.common`'s profile filters and `presentation.accounts.AccountType`), and shared components that import a feature.
- `scripts/arch/package-deps.sh` reprints the package matrix and its cycles, so a PR can show it removed a cycle and added none.

## Deferred

Status as of 2026-11-12. The original reasoning is kept under each item.

- **Feature Gradle modules. Started.** `:feature:exchangerates` and `:feature:learnedcategories` are out, each behind a small port that `:app` binds ([architecture.md](../architecture.md#featureexchangerates)). The rest still wait for their packages to lose their back-edges. Originally deferred because screens import `data` directly (330 imports from `presentation` alone), and `data` imported back into `presentation`, `ui`, `worker`, `receiver` and `widget`. A `:feature:*` module would need `:app` on its classpath, which Gradle cannot do.
- **Convention plugins (`build-logic/`). Started.** The second Android library module brought them in, as planned: `pennywise.android.compose-library` and `pennywise.android.feature` (#1235). `:app`, `:shared`, `:parser-core` and `:baselineprofile` don't use them yet.
- **Detekt. Advisory.** It runs with Kotlin 2.4.21 and AGP 9.5 in report-only mode with a baseline, in `quality-advisory.yml` (#1237). It doesn't fail a build.
- **Kover. Opt-in.** Coverage reports run only with `-Pcoverage` (#1237), with no thresholds. Add targets for money, backup and parser code once those have targeted tests.
- **Renovate. Config merged.** `renovate.json` opens grouped weekly PRs; the coupled toolchain (Kotlin, AGP, Hilt, LiteRT-LM, coroutines) and every major wait for approval (#1217).
- **Compose screenshot tests. Rejected for now** by the maintainer. Revisit only if a screen split needs them.
- **Runtime performance numbers.** Startup and scroll need a physical device ([performance.md](../performance.md)). This decision doesn't depend on them.

## Data and domain stay one layer for now

`data` and `domain` import each other 51 / 136 times, because use cases work on Room entities and concrete repositories. We treat the two as one implementation layer for now and don't try to break that cycle. We also don't extract a `:core:data` module yet. It would pull `utils`, `billing` and the code that schedules workers, refreshes widgets and posts notifications along with it, unless those already sit behind contracts. Round 5 adds those inversions: `utils` becomes a leaf, `data` reaches `billing` through `core.entitlement`, and data reaches workers, widgets and receivers through contracts. Revisit `:core:data` once all of them have landed on `main`.
