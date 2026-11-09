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

- **Feature Gradle modules.** Screens import `data` directly (330 imports from `presentation` alone), and `data` imports back into `presentation`, `ui`, `worker`, `receiver` and `widget`. A `:feature:*` module would need `:app` on its classpath, which Gradle cannot do. The data layer, or at least the contracts features use, has to come out of `:app` first. Revisit once a pilot feature package has no back-edges.
- **Convention plugins (`build-logic/`).** There is one Android application module, one Android test module (`:baselineprofile`) and two Kotlin Multiplatform modules configured differently. A convention plugin earns its keep when several modules share a configuration. Today it would move build code around without removing any duplication. Add it in the PR that creates the second module of a kind.
- **Detekt.** Its compatibility with Kotlin 2.4.21 and AGP 9.5 is not yet verified. If a release works with this toolchain, pilot it in report-only mode with a baseline.
- **Compose screenshot tests.** No screenshot tool has been checked against AGP 9.5, and no screen is being split yet. Add tests with the first PR that splits a large screen, for the states that PR touches.
- **Renovate.** Automated update PRs are only safe once CI gates are trustworthy, and Kotlin, AGP, Room, Hilt, KSP, WorkManager, billing and LiteRT bumps need a person to review them anyway. Revisit after the guardrails land.
- **Kover.** A coverage number with no tests on the critical paths invites vanity targets. Add reporting for money, backup and parser code once those have targeted tests.
- **Runtime performance numbers.** Startup and scroll need a physical device ([performance.md](../performance.md)). This decision doesn't depend on them.
