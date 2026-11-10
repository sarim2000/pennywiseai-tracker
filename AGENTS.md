# PennyWise — Agent Guide

PennyWise is a privacy-first, AI-powered expense tracker that extracts
transactions from bank SMS **entirely on-device**. The primary app is Android
(Jetpack Compose + Material 3); the repo also contains a Kotlin Multiplatform
`shared` module, an `iosApp`, and a `pennywise-web` frontend.

**This file is a router, not a manual.** Read the linked topic doc when a task
touches its area. Everything under **Hard Constraints** is non-negotiable.

**Apply judgment to feature/bug requests — an issue is input, not a spec.** When
an issue lists several asks, evaluate each on its own merit: ship the clear wins,
and leave out (with a short note on the issue/PR saying why) the parts that
reshape a core figure/flow or aren't clearly worth it. Don't implement everything
an issue lists by default — bias to the smallest change that solves the real
complaint.

## Hard Constraints (never violate)
1. **Money is always currency-tagged.** Format with
   `CurrencyFormatter.formatCurrency(amount, currencyCode)` (or the
   `formatBalance()` / `formatAmount()` entity extensions) — never the
   currency-less overload (it defaults to ₹ and is `@Deprecated(ERROR)`, so it
   won't compile). It returns `••••` while the user hides amounts
   (`AmountPrivacy`); anything that leaves the screen or is stored
   (notifications, AI prompts/chat text, logs, widgets) passes
   `maskIfHidden = false`.
2. **Never sum amounts across currencies.** "₹500 + $10" is not 510 of anything.
   Total a possibly-mixed list with `Iterable.sumByCurrency({ it.currency },
   { it.amount })` (`utils/MoneyTotals.kt`) → `Map<currency, Money>`, and render
   with `CurrencyFormatter.formatByCurrency(...)`. `Money.+` throws on a currency
   mismatch. A single-figure total is only valid after filtering/converting to
   one currency. (Both rules guarded by `CurrencyFormatterTest`.)
3. **Every backup-serialized field must have a Kotlin default value** — Room
   entity columns *and* the wrapper models in `BackupModels.kt`. Dropping a
   default re-introduces the "can't restore old backup" bug (#414);
   `BackupSchemaGuardTest` enforces this. Use `@Contextual` for
   `BigDecimal`/`LocalDate`/`LocalDateTime`, `@Serializable` for enums. See
   `docs/backup-format.md` or the **backup-maintainer** skill before touching
   entities or `data/backup/`.
4. **Never put PII in code, comments, tests, or anywhere.**
5. **Never hand-edit the version or changelogs.** `scripts/release.sh` owns
   `versionCode` / `versionName` and `fastlane/.../changelogs/*` (see Releasing).
6. **Parser tests use the shared `ParserTestUtils` helpers** — see
   `docs/parser-test-standards.md`.
7. **New bank parsers** extend the correct base class (`BaseIndianBankParser` /
   `UAEBankParser` / `BankParser`) and are registered in `BankParserFactory` —
   see `docs/adding-bank-parsers.md`.
8. **Migration SQL must run on the oldest SQLite we support.** minSdk 26 ships
   SQLite 3.18; Android 13 ships 3.32. The JVM and our new-Android emulators
   are newer and accept syntax old phones reject: 2.22.0's `HAVING` without
   `GROUP BY` (3.39+) crashed every launch on Android ≤13. Before merging or
   releasing a migration, run the update path on the `Api26_Migration` AVD —
   see `docs/database-migrations.md`.

## Verification — Definition of Done
Run **`./init.sh`** before claiming a change is done — it runs the same
format/compile/test gate CI applies (CI's App Compile job additionally assembles a
debug APK, so a change that compiles but breaks assembly will still surface
there). Scope it to what you touched:

| Change | Command |
|---|---|
| Formatting only | `./init.sh format` (every target runs it first) |
| Parser (`parser-core`) | `./init.sh parser` |
| App (Kotlin) | `./init.sh app` |
| Everything | `./init.sh` (default) |
| Runtime / UI | plus an on-device check (use the **emulator-verify** skill) |
| DB migration | plus the old-Android update check in `docs/database-migrations.md` |

`init.sh` sets `JAVA_HOME` to a JDK 21 (it tries `JAVA_HOME`, macOS `java_home`,
Android Studio's JBR, SDKMAN, then a Gradle-downloaded JDK). Run the raw
`./gradlew` tasks it wraps only if you need finer control. `./gradlew build` and
`lint` are heavier and **not** part of the gate — don't use them to verify.

A format failure means `./gradlew spotlessApply`, then review and commit the
diff. It checks whole files changed since `origin/main`, so touching a legacy
file means formatting all of it. A lint error marked "no autocorrection" (e.g. a
comment inside the import list) needs a manual edit first. Opt-in pre-push hook
with the same check: `git config --local core.hooksPath .githooks` (this
replaces `.git/hooks`, so move any existing hooks there first).

## Architecture at a glance
- **Stack:** Kotlin · Jetpack Compose + Material 3 · MVVM + Clean Architecture ·
  StateFlow (unidirectional data flow) · Hilt DI · Room · WorkManager (background
  SMS scanning) · Google AI Edge LiteRT-LM (Qwen 2.5) for on-device AI.
- **Modules:** `app` (Android) · `parser-core` (pure-Kotlin bank SMS parsers, no
  Android deps) · `shared` (KMP) · `iosApp` · `pennywise-web`.
- **UI:** Material You dynamic color (Android 12+), full light/dark with semantic
  color roles, 8dp grid, Material 3 type scale. All screens use
  `PennyWiseScaffold` for consistent system-bar / TopAppBar handling. Always
  check both light and dark themes.
- **Versioning:** SemVer (MAJOR breaking / MINOR features / PATCH fixes). The
  live version is in `app/build.gradle.kts`.

## Topic Docs — read when the task touches the area
| Area | Doc | Read when |
|---|---|---|
| Architecture / layers | `docs/architecture.md` | Changing layer responsibilities, DI, data flow |
| Design system | `docs/design.md` | Any UI work — colors, typography, components |
| State management | `docs/state-management.md` | ViewModels, StateFlow, UI state |
| Backup & Restore | `docs/backup-format.md` | Touching backed-up entities or `data/backup/` |
| Adding a bank parser | `docs/adding-bank-parsers.md` | New/changed parser in `parser-core` |
| Parser tests | `docs/parser-test-standards.md` | Writing parser tests |
| DB migrations | `docs/database-migrations.md` | Room schema changes |
| Performance | `docs/performance.md` | Baseline Profiles, Macrobenchmark, startup or scroll timing |
| Supported banks | `docs/BANK_SUPPORT.md`, `docs/supported-banks.json` | Which banks/patterns are covered (170 banks, 26 countries) |
| AI access (MCP relay, dashboard) | `docs/ai-access.md`, `relay/README.md`, `docs/adr/0002-*` | Touching `aiaccess/`, the relay or the dashboard |
| Roadmap / requirements | `docs/planned-features.md`, `docs/prd-unified-currency.md` | Feature scope / intent |

**Skills** (in `.agents/skills/`, shared by every agent; `.claude/skills/` only
forwards to them): `backup-maintainer` (any persistence change) ·
`parser-author` (write/fix a parser end-to-end) · `parser-triage` (assess a
fresh `[BANK]` issue) · `emulator-verify` (on-device check).

## Scope & commits
- Stay on the task and match the surrounding code. Report unrelated problems
  instead of fixing them in the same change.
- One concern per commit, and each commit compiles on its own. Conventional
  titles in the style of the history, e.g. `fix(parser): …`, `feat(backup): …`.

## Releasing
For a routine release: merge the PRs, then — from an up-to-date `main` with a
clean tree — run `./scripts/release.sh patch|minor|major --yes` (preview first
with `--dry-run`). It commits/tags the current branch and pushes `main`, so the
branch state matters.
`scripts/release.sh` is the single source of truth — it bumps the version,
generates changelogs + release notes, builds the APKs (standard flavor signed;
the F-Droid APK stays unsigned for IzzyOnDroid), tags, pushes, and cuts the
GitHub release. Full flag reference in `docs/RELEASE.md`.
`--web` needs Docker (OrbStack) running and `bun install` done in
`pennywise-web/` — a fresh worktree has no `node_modules`.
