# PennyWise — Agent Guide

PennyWise is a privacy-first, AI-powered expense tracker that extracts
transactions from bank SMS **entirely on-device**. The primary app is Android
(Jetpack Compose + Material 3); the repo also contains a Kotlin Multiplatform
`shared` module, an `iosApp`, and a `pennywise-web` frontend.

**This file is a router, not a manual.** Read the linked topic doc when a task
touches its area. Everything under **Hard Constraints** is non-negotiable.

## Hard Constraints (never violate)
1. **Money is always currency-tagged.** Format with
   `CurrencyFormatter.formatCurrency(amount, currencyCode)` (or the
   `formatBalance()` / `formatAmount()` entity extensions) — never the
   currency-less overload (it defaults to ₹ and is `@Deprecated(ERROR)`, so it
   won't compile).
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
   `docs/backup-format.md` or the **backup-maintainer** subagent before touching
   entities or `data/backup/`.
4. **Never put PII in code, comments, tests, or anywhere.**
5. **Never hand-edit the version or changelogs.** `scripts/release.sh` owns
   `versionCode` / `versionName` and `fastlane/.../changelogs/*` (see Releasing).
6. **Parser tests use the shared `ParserTestUtils` helpers** — see
   `docs/parser-test-standards.md`.
7. **New bank parsers** extend the correct base class (`BaseIndianBankParser` /
   `UAEBankParser` / `BankParser`) and are registered in `BankParserFactory` —
   see `docs/adding-bank-parsers.md`.

## Verification — Definition of Done
Run **`./init.sh`** before claiming a change is done — it runs exactly what CI
(`.github/workflows/test.yml`) gates on, so "green here" means "green in CI".
Scope it to what you touched:

| Change | Command |
|---|---|
| Parser (`parser-core`) | `./init.sh parser` |
| App (Kotlin) | `./init.sh app` |
| Everything | `./init.sh` (default) |
| Runtime / UI | plus an on-device check (use the **emulator-verify** skill) |

`init.sh` sets `JAVA_HOME` to a JDK 21 (Android Studio's JBR by default). Run
the raw `./gradlew` tasks it wraps only if you need finer control. `./gradlew
build` and `lint` are heavier and **not** part of the gate — don't use them to
verify.

## Architecture at a glance
- **Stack:** Kotlin · Jetpack Compose + Material 3 · MVVM + Clean Architecture ·
  StateFlow (unidirectional data flow) · Hilt DI · Room · WorkManager (background
  SMS scanning) · MediaPipe LLM (Qwen 2.5) for on-device AI.
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
| Supported banks | `docs/BANK_SUPPORT.md`, `docs/supported-banks.json` | Which banks/patterns are covered (139 banks, 23 countries) |
| Roadmap / requirements | `docs/planned-features.md`, `docs/prd-unified-currency.md` | Feature scope / intent |

**Subagents:** `backup-maintainer` (backup-serialized changes) ·
`parser-author` (write/fix a parser end-to-end) · `parser-triage` (assess a
fresh `[BANK]` issue).

## Releasing
For a routine release: merge the PRs, then — from an up-to-date `main` with a
clean tree — run `./scripts/release.sh patch|minor|major --yes` (preview first
with `--dry-run`). It commits/tags the current branch and pushes `main`, so the
branch state matters.
`scripts/release.sh` is the single source of truth — it bumps the version,
generates changelogs + release notes, builds/signs the APKs, tags, pushes, and
cuts the GitHub release. Full flag reference in `docs/RELEASE.md`.
