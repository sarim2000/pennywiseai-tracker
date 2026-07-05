# PennyWise Project Context

## Project Overview
PennyWise is a minimalist, AI-powered expense tracker for Android that automatically extracts transaction data from SMS messages using on-device processing.

## Important Documents
Please reference these documents when working on this project:
- **Architecture**: `/docs/architecture.md` - MVVM + Clean Architecture patterns, layer responsibilities
- **Design System**: `/docs/design.md` - Material 3 theming, colors, typography, components
- **PRD**: `/prd.md` - Product requirements, features, timeline
- **Backup & Restore**: `/docs/backup-format.md` - Backup JSON format + the forward/backward compatibility contract. **Read this before changing anything a backup serializes** (entities, `data/backup/`).

## Key Technical Decisions
1. **UI Framework**: Jetpack Compose with Material 3
2. **Architecture**: MVVM with Clean Architecture (UI, Domain, Data layers)
3. **State Management**: Unidirectional Data Flow with StateFlow
4. **DI**: Hilt for dependency injection
5. **Database**: Room for local storage
6. **AI/ML**: MediaPipe LLM (Qwen 2.5) for on-device processing
7. **Background**: WorkManager for SMS scanning

## Design Principles
- **Material You**: Dynamic color from wallpaper (Android 12+)
- **Light/Dark Theme**: Full support with semantic color roles
- **Spacing**: 8dp grid system
- **Typography**: Material 3 type scale
- **Navigation**: NavigationBar for phones, NavigationRail for tablets
- **Edge-to-Edge**: All screens use PennyWiseScaffold with default TopAppBar for consistent system bar handling
- **Consistent UI**: PennyWiseScaffold provides default TopAppBar with options for title, navigation, actions, and transparency

## Code Style Guidelines
- Follow Kotlin coding conventions
- Use meaningful variable names
- Implement proper error handling with sealed classes
- Ensure UI components are reusable and testable
- Always test on both light and dark themes

## Current Phase
Working on Phase 1: Core Foundation (Project setup, Material 3 theming, Room database, Navigation)

## Commands to Run
- Build: `./gradlew build`
- Test: `./gradlew test`
- Lint: `./gradlew lint`

## Versioning Strategy
We follow Semantic Versioning (SemVer) - MAJOR.MINOR.PATCH:
- **MAJOR**: Breaking changes, major UI overhauls, architecture changes
- **MINOR**: New features, significant improvements
- **PATCH**: Bug fixes, minor improvements, performance optimizations

### Releasing — do NOT hand-edit the version or changelog
`scripts/release.sh [patch|minor|major]` owns the entire release and is the
**only** thing that should touch the version. Never manually edit
`versionCode` / `versionName` in `app/build.gradle.kts`, and never hand-write a
`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` file — the script
generates all of that. In one run it:
- bumps `versionName` (per the SemVer keyword) and increments `versionCode`,
- generates the store changelog `<versionCode>.txt` + `default.txt` and the
  GitHub `RELEASE_NOTES.md` from the commits since the last tag (via the Claude
  Agent SDK, falling back to a commit list; `--no-claude` forces the fallback),
- builds/renames/signs the standard + F-Droid APKs and computes SHA256,
- commits `chore(release): bump version to X [skip ci]`, tags `vX`, pushes, and
  cuts the GitHub release with the APKs attached.

Flags: `--yes` (non-interactive), `--play` (upload the `.aab` to Play Console as
a draft), `--web` (also deploy `pennywise-web`), `--dry-run` (print the plan +
notes, change nothing). So for a routine release just merge the PRs, then run
e.g. `./scripts/release.sh patch --yes`. Use `--dry-run` first to preview the
computed version and notes. The current version therefore lives in
`app/build.gradle.kts`, not here.

## Module Structure
The project now uses a multi-module architecture:
- **app**: Main Android application module
- **parser-core**: Standalone bank parser module (no Android dependencies)

## Bank Parser Architecture
Bank parsers are now in the `parser-core` module for reusability across platforms.

### When adding new bank parsers:
1. **Location**: Add to `parser-core/src/main/kotlin/com/pennywiseai/parser/core/bank/`
2. **Base Class**: All bank parsers extend `BankParser` abstract class
   - **Indian Banks**: MUST extend `BaseIndianBankParser` to inherit centralized mandate, subscription, and balance update logic.
   - **UAE Banks**: MUST extend `UAEBankParser` for currency and transaction type handling.
3. **Key Methods**:
   - `getBankName()`: Returns the bank's display name
   - `canHandle(sender: String)`: Checks if parser can handle SMS from sender
   - `parse(smsBody, sender, timestamp)`: Returns `ParsedTransaction` or null
4. **Override Patterns**: Banks typically override:
   - `extractAmount()`: Bank-specific amount patterns
   - `extractMerchant()`: Bank-specific merchant extraction
   - `extractTransactionType()`: If needed for special cases
5. **Registration**: Add new parser to `BankParserFactory.parsers` list in parser-core
6. **Return Type**: Use `ParsedTransaction` from parser-core
7. **Imports for parser-core**:
   - `com.pennywiseai.parser.core.TransactionType`
   - `com.pennywiseai.parser.core.ParsedTransaction`
   - `java.math.BigDecimal` for amounts

### Integration in main app:
- Use `com.pennywiseai.tracker.data.mapper.toEntity()` to convert ParsedTransaction to TransactionEntity
- The mapper handles type conversions between modules

## Supported Banks (55 parsers)
- Airtel Payments Bank
- **Al Rajhi Bank (Saudi Arabia)** - Arabic SMS support
- **Alinma Bank (Saudi Arabia)** - Arabic SMS support
- **Altana Federal Credit Union (USA)**
- American Express (AMEX)
- Axis Bank
- Bank of Baroda
- Bank of India
- Canara Bank
- Central Bank of India
- **Chase Bank (USA)**
- City Union Bank
- DBS Bank
- Federal Bank
- HDFC Bank
- HSBC Bank
- **Huntington Bank (USA)**
- ICICI Bank
- IDBI Bank
- IDFC First Bank
- Indian Bank
- Indian Overseas Bank
- India Post Payments Bank (IPPB)
- Jio Payments Bank
- JioPay
- Jammu & Kashmir Bank
- Jupiter Bank
- Juspay
- Karnataka Bank
- Kerala Gramin Bank
- Kotak Bank
- LazyPay
- **Liv Bank (UAE)** - Digital bank
- Mashreq Bank
- **mBank CZ (Czech Republic)** - Czech SMS support
- **M-PESA (Kenya)** - Mobile money service
- **Navy Federal Credit Union (USA)** - NFCU
- **NMB Bank / Nabil Bank (Nepal)**
- OneCard
- **Priorbank (Belarus)** - Russian/Belarusian SMS support
- Punjab National Bank (PNB)
- Punjab & Sind Bank (PSB)
- Saraswat Co-operative Bank
- **Saudi National Bank / Al Ahli (SNB-AlAhli, Saudi Arabia)** - Arabic SMS support
- **Siddhartha Bank Limited (Nepal)**
- State Bank of India (SBI)
- Slice
- South Indian Bank
- **Standard Chartered Bank**
- **STC Bank (Saudi Arabia)**
- **T-Bank / Tinkoff (Russia)** - Russian SMS support
- Union Bank
- Utkarsh Bank

When implementing any feature, please ensure it aligns with the architecture patterns and design system defined in the documentation.


## Backup & Restore
Backups serialize Room entities directly to JSON via **kotlinx.serialization**
(`data/backup/`). The format is forward- and backward-compatible by design.

**The one rule:** every backup-serialized field (entity columns + the wrapper
models in `BackupModels.kt`) **must have a Kotlin default value**. Missing keys
in an older backup fall back to defaults; unknown keys from a newer backup are
ignored. Dropping a default re-introduces the "can't restore old backup" bug
(#414). `BackupSchemaGuardTest` enforces this in CI.

- When adding an entity column: give it a Kotlin default; `@Contextual` if it's
  `BigDecimal`/`LocalDate`/`LocalDateTime`; `@Serializable` if it's an enum.
- When in doubt, use the **backup-maintainer** subagent / **backup-format**
  skill, and read `/docs/backup-format.md`.

# Money & Currency Formatting
Every amount in this app carries a currency (transactions, balances, loans,
subscriptions can each be in a different one). Two rules keep totals honest:

1. **Always format money with its currency.** Call
   `CurrencyFormatter.formatCurrency(amount, currencyCode)` — never the
   currency-less overload (it defaults to INR/₹ and is `@Deprecated(ERROR)`, so
   it won't compile). For an account/entity, prefer the `formatBalance()` /
   `formatAmount()` extensions, which already pass the row's currency.

2. **Never sum amounts across currencies.** "₹500 + $10" is not "510" of
   anything. To total a list that can hold more than one currency, use
   `Iterable<T>.sumByCurrency({ it.currency }, { it.amount })` (in
   `utils/MoneyTotals.kt`) → `Map<currency, Money>`, and render it with
   `CurrencyFormatter.formatByCurrency(...)` (single figure for a uniform set,
   `₹1,250 · $600` for a mixed one). The `Money` type's `+` throws on a currency
   mismatch, so a blind fold blows up loudly instead of silently mislabeling.
   The only safe single-figure total is one **filtered/converted to one
   currency first** (see `LoansViewModel` / unified-mode conversion).

`CurrencyFormatterTest` guards these. When a screen shows a wrong currency
symbol or a nonsensical mixed total, it's almost always a violation of one of
the two rules above.

# Important
Never use pii in comments, code anywhere

# Test implementation standards for parsers
- Parser tests must use the shared JUnit helpers under `ParserTestUtils`. For
  full guidance (examples, migration checklist), read `docs/parser-test-standards.md`.
