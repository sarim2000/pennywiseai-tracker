# PennyWise Architecture Guide

## Overview
PennyWise follows modern Android architecture guidelines with MVVM pattern, Clean Architecture principles, and Unidirectional Data Flow (UDF).

## Core Architectural Principles

### 1. Separation of Concerns
- UI components (Activities, Fragments, Composables) contain minimal logic
- Business logic resides in ViewModels and Use Cases
- Data operations handled by repositories

### 2. Drive UI from Data Models
- Persistent models survive configuration changes
- Room database as single source of truth
- StateFlow for reactive UI updates

### 3. Single Source of Truth (SSOT)
- All transaction data originates from Room database
- Repositories centralize data mutations
- Immutable data exposed to UI layer

### 4. Unidirectional Data Flow (UDF)
- State flows: Repository → ViewModel → UI
- Events flow: UI → ViewModel → Repository
- Predictable state management with StateFlow

## Architecture Layers

### UI Layer (Presentation)
**Components:**
- Jetpack Compose screens
- ViewModels with StateFlow
- UI state classes

**Responsibilities:**
- Render UI based on state
- Handle user interactions
- Navigate between screens

**Key Classes:**
```kotlin
- presentation/home/HomeScreen.kt
- presentation/home/HomeViewModel.kt
- ui/viewmodel/ThemeViewModel.kt
- UiState data classes
```

Home's figure math is plain functions next to its ViewModel, unit-tested without
it: `HomeFigures.kt` (per-currency breakdown, currency collapse, money in motion,
account conversion), `HomeSpendTrend.kt` (the sparkline) and `HomeBudgetSummary.kt`
(the unified-mode budget card). They take a converter lambda rather than
`CurrencyConversionService`. `HomeViewModel` keeps the flows, caches and UI state.

The Transactions list follows the same split: `TransactionListFilters.kt` (filter,
search, sort and date-group steps) and `TransactionListTotals.kt` (per-currency and
unified totals). `TransactionsViewModel` keeps the triggers, threading and currency
auto-select.

### Domain Layer (Business Logic)
**Components:**
- Use Cases/Interactors
- Domain models
- Business rules

**Responsibilities:**
- Complex business logic
- Data transformation
- Validation rules

**Key Classes:**
```kotlin
- AddTransactionUseCase.kt
- DeleteTransactionUseCase.kt
- ApplyRulesToPastTransactionsUseCase.kt
- MarkSubscriptionPaidUseCase.kt
- domain/service/LlmService.kt (on-device AI interface)
```

### Data Layer (Data Sources)
**Components:**
- Repositories
- Room DAOs
- Data sources (SMS, notification listener, AI)
- Data models

**Responsibilities:**
- Abstract data sources
- Cache management
- Data synchronization

**Key Classes:**
```kotlin
- TransactionRepository.kt
- TransactionDao.kt
- data/manager/SmsTransactionProcessor.kt (orchestrates SMS → parser → database)
- data/repository/LlmRepository.kt (backs LlmService with the on-device model)
- receiver/BankNotificationListenerService.kt (notification-to-SMS parser bridge for whitelisted bank apps)
```

SMS parsing itself lives in the `parser-core` module (pure Kotlin, no Android
dependencies). Parsers register in `BankParserFactory`, which dispatches each
message to every candidate parser by content — see
[adding-bank-parsers.md](adding-bank-parsers.md).

## Module Structure
```
app/
├── src/main/java/com/pennywiseai/tracker/
│   ├── presentation/            # One package per feature: screens + ViewModels
│   │   │                        #   (home, accounts, add, transactions, analytics,
│   │   │                        #   chat, budgetgroups, categories, subscriptions,
│   │   │                        #   loans, groups, rules, settings (+ aiaccess/,
│   │   │                        #   webhooks/), onboarding, unrecognized, applock,
│   │   │                        #   permission, paywall, share, statement,
│   │   │                        #   exchangerates, common; also a navigation/
│   │   │                        #   subpackage for feature routes)
│   ├── ui/                      # App shell (MainScreen) + shared components
│   │   ├── components/          # Reusable Compose components
│   │   └── viewmodel/           # App-wide ViewModels (main, theme, spotlight)
│   ├── navigation/              # Navigation graph
│   ├── domain/                  # Business logic
│   │   ├── model/
│   │   ├── usecase/
│   │   ├── service/             # LlmService and friends
│   │   └── repository/          # Repository interfaces
│   ├── data/                    # Data Layer
│   │   ├── database/            # Room database, DAOs, entities
│   │   ├── repository/          # Repository implementations
│   │   ├── manager/             # SmsTransactionProcessor, sync managers
│   │   ├── preferences/         # "user_preferences" DataStore (UserPreferencesRepository, focused *Preferences contracts), small per-feature stores
│   │   ├── currency/ backup/ contacts/
│   │   └── mapper/
│   ├── di/                      # Hilt modules (DatabaseModule, etc.)
│   ├── receiver/                # SMS / notification entry points
│   ├── worker/                  # WorkManager jobs (SMS scan retry, scheduled backups)
│   │   └── smsscan/             # The SMS scan's stages: source, classifier, pipeline, writer, cleanup
│   ├── widget/                  # Home-screen widgets
│   ├── billing/                 # Play Billing + FreeTierLimits
│   ├── backup/ core/ initializer/ utils/
└── build.gradle.kts             # Flavors: standard, fdroid
core/designsystem/               # PennyWiseTheme, ui.theme tokens, fonts, LocalBlurEffects,
│                                #   overscroll effect, appearance enums (same packages as before the move)
feature/exchangerates/           # Exchange-rates screen + ViewModel (package presentation.exchangerates)
feature/learnedcategories/       # Learned-categories screen + ViewModel (package presentation.settings)
build-logic/                     # Convention plugins: pennywise.android.compose-library / .feature
parser-core/                     # Bank SMS parsers (Kotlin Multiplatform)
shared/                          # KMP code shared with iOS
iosApp/                          # Swift iOS app
pennywise-web/                   # Web deployment (Cloudflare Worker + server/)
```

## Ownership & dependency rules

These rules set the direction for new code and for moves under
[ADR-0003](adr/0003-incremental-architecture-modernization.md). The code doesn't
follow all of them yet. [architecture-baseline.md](architecture-baseline.md)
lists the existing cycles and back-edges, and `scripts/arch/package-deps.sh`
reprints them. A PR may remove a violation but must not add one.
[`PackageEdgeRatchetTest`](#package-edge-ratchet) enforces that for the package
graph as a whole.

| Package (`com.pennywiseai.tracker.…`) | Owns | Must not import |
|---|---|---|
| `presentation/<feature>` | A feature's screens, ViewModel and UI state, kept together | Another feature's screens or internal components |
| `ui/components`, `ui/theme`, `ui/icons`, `ui/animations`, `ui/effects` | Components and design tokens used by more than one feature | `presentation`, `worker` |
| `ui/viewmodel`, `ui` root (`MainScreen`) | App-wide ViewModels (main, theme, spotlight) and the app shell | (a feature's ViewModel goes in its `presentation/<feature>`) |
| `domain` | Use cases, business rules, models, service interfaces | `presentation`, `ui`, Compose, Activities or other Android UI |
| `data` | Room (`database`), repositories, DataStore (`preferences`), backup, SMS processing (`manager`) | `presentation`, `ui` |
| `utils`, `core` | Small helpers used across layers: money, currency, dates | `presentation`, `ui`, Android entry points |
| `worker`, `receiver`, `widget`, `tile`, `aiaccess` | Android entry points | Feature screen internals. Use `domain`/`data` instead |
| `di`, `navigation`, root (`MainActivity`, `PennyWiseApp`, `PennyWiseApplication`) | Wiring and the composition root | (may import anything) |

1. **No new cycles.** If two packages import each other, cut the edge that points
   from the lower layer to the higher one.
2. **Non-UI code doesn't live in screen packages.** If `data`, `domain`, a worker
   or a widget needs it, move it into `domain` (or `utils` for a small pure
   helper) instead of importing the screen package. The profile filters
   (`domain/model/ProfileFilters.kt`) and `AccountType` (`domain/model`) moved
   down this way; their labels and icons stay in `presentation/accounts/AccountTypeUi.kt`.
3. **Shared UI is feature-agnostic.** A component in `ui/components` takes plain
   parameters. It doesn't import a feature's state types. A component only one
   feature uses lives in that feature's package.
4. **Domain has no UI or Android UI dependencies.** Use cases may use Room
   entities and repositories from `data`, which is the current pattern. Introduce
   a separate domain model only when a feature needs one.
5. **Money has one definition.** Use `utils/Money.kt`, `MoneyTotals.kt` and
   `CurrencyFormatter` (see the hard constraints in `AGENTS.md`). Check `:shared`
   before adding a model, so it isn't defined twice.
6. **Moves keep persisted names.** WorkManager stores worker class names, and
   widgets, tiles, the notification listener and the launcher activity are bound
   by their component names. Room table and column names, DataStore keys and
   backup field names must not change during a refactor either.
7. **Gradle modules.** `:parser-core` stays pure Kotlin. `:shared` `commonMain`
   has no Android API. A new module is split off only when its package has no
   back-edges into `:app`. It depends on other modules with `implementation`, and
   uses `api` only for a contract that is actually public.

### `:core:designsystem`

Holds `PennyWiseTheme`, the `ui.theme` tokens, the 17 fonts, `LocalBlurEffects`, the
overscroll effect (`ui.effects.overScrollVertical` and friends) and the appearance enums (`AccentColor`, `AppFont`, `ThemeStyle`). `:app` depends on it,
and it depends on no project module, so the compiler keeps the theme free of
`data`, `domain` and feature code.

- **Packages are unchanged**, so no caller's imports changed. The enums keep
  `data.preferences` because moving them to `ui.theme` would make
  `UserPreferencesRepository` import a `ui` package. They are stored by name, so
  entries can't be renamed. If a `:core:datastore` is ever extracted, move them
  down rather than making it depend on this module.
- **No string resources here.** Crowdin and `StringPlaceholderParityTest` cover
  `app/src/main/res` and the feature modules' `res`, not this module's.
- **`ui/components` stays in `:app`.** About 14 of its files are generic, but `:app`
  is the only module that renders Compose. The rest of the package needs
  `R.string`, money formatting, Haze, Coil or feature types, so moving the generic
  ones would split `ui.components` across two modules and serve no caller. Move what
  a second Compose module needs in the PR that adds it.

### `:feature:exchangerates`

The first feature module: `ExchangeRatesScreen`, its ViewModel and the
`ExchangeRatesData` port. It depends on `:core:designsystem` only. `:app` binds the
port (`di/ExchangeRatesModule.kt`) and calls the screen through
`navigation/ExchangeRatesEntry.kt`, which passes in what stays in `:app`: the
`CustomTitleTopAppBar` (as a `topBar` slot, with the back button), `AppDates` and
`CurrencyFormatter`, and the shared `accounts_*` strings, resolved.

- **The package stays `com.pennywiseai.tracker.presentation.exchangerates`**, so the
  Hilt ViewModel key and every rule below apply as before. Its `R` is
  `com.pennywiseai.feature.exchangerates.R`.
- **Strings live in the module** (`res/values*/strings_exchangerates.xml`). Crowdin
  needs one `files` entry per feature module in `crowdin.yml`.
- Hilt and KSP run here for the ViewModel, applied by `pennywise.android.feature`
  ([`build-logic`](#build-logic)). Room, KSP schemas and the baseline profile
  stay in `:app`.

### `:feature:learnedcategories`

The second feature module, built the same way: `LearnedCategoriesScreen`, its
ViewModel and the `LearnedCategoriesData` port (backed by
`MerchantMappingRepository` in `di/LearnedCategoriesModule.kt`).
`navigation/LearnedCategoriesEntry.kt` passes in `PennyWiseScaffold` with the back
button, `GroupedRow` and `categoryLabel`. The package stays
`com.pennywiseai.tracker.presentation.settings`; its `R` is
`com.pennywiseai.feature.learnedcategories.R`, which the Settings row in `:app`
also reads for the screen's title and subtitle.

### `build-logic`

The included build `build-logic` (shares `gradle/libs.versions.toml`) holds two
precompiled script plugins.
`pennywise.android.compose-library` sets what the Android library modules share
with `:app`: compileSdk 37, minSdk 26, Compose, Java/Kotlin target 17, the
Material 3 Expressive opt-in and `isReturnDefaultValues` for unit tests.
`pennywise.android.feature` adds KSP, the Hilt plugin and the Hilt compiler.
A module still declares its namespace and every dependency it compiles against.
`:app`, `:shared`, `:parser-core` and `:baselineprofile` don't use them; change
`:app`'s copy of these settings together with the plugin.

### Enforced by `ArchitectureTest`

`app/src/test/.../architecture/ArchitectureTest.kt` (Konsist) checks the
`import` lines of the production sources in every flavor, in `:app` and every
`feature/*` module. It runs with the unit
tests, so `./init.sh app` and CI fail on a new violation:

| Files | Must not import |
|---|---|
| `domain` | `presentation`, `ui`, Compose, Activity/Fragment, Room |
| `data` | `presentation`, `ui` |
| `utils`, `core` | `presentation`, `ui`, `worker`, `receiver`, `widget`, `tile`, `aiaccess` |
| `worker`, `widget`, `receiver`, `tile`, `aiaccess` (one rule each) | `presentation` (launch an Activity by class name through a `core` intent contract such as `core/QuickAddIntent`; don't import it) |
| `ui.components`, `ui.icons`, `ui.animations`, `ui.effects` | `presentation`, `worker` |
| ViewModels, files that declare a `@Composable` | DAOs, `PennyWiseDatabase`, Room |

`ui.theme` needs no rule: `:core:designsystem` can't see `:app`'s code.

Two more rules check where files live rather than what they import: no file in
any source set may be in the retired `ui.screens` package (by path or by
`package` line), and `ui.viewmodel` may hold only `MainViewModel`,
`ThemeViewModel` and `SpotlightViewModel`.

Imports that already broke a rule are in that rule's allowlist, keyed by file
and exact import, so an allowlisted file can't gain a new back-edge. An
allowlist only shrinks. Don't add to it: move the code to the right layer. When
you fix an import, delete its entry. The test also fails on an entry that no
longer matches an import, so a fixed import can't come back. A fully qualified name in code isn't checked,
so don't use one to get around a rule.

### Package-edge ratchet

`app/src/test/.../architecture/PackageEdgeRatchetTest.kt` lists every directed
edge between top-level `com.pennywiseai.tracker` packages (`data -> worker`,
`navigation -> ui`, …) in the sources `scripts/arch/package-deps.sh` reads:
`:app`'s main and flavor sources and the `feature/*` and `core/*` modules. It
counts `import` lines and fully qualified names in code, but not comments,
strings, `R` or `BuildConfig`. It fails when an edge is missing from
`config/architecture/package-edges.txt`, and runs with the unit tests, so
`./init.sh app` catches it.

- **A new edge fails the test.** Move the code to the layer the rules above
  allow. Add an edge to the file only when the dependency points the right way
  (for example a wiring package such as `di` or `navigation` reaching a new
  package), and say why in the PR.
- **A removed edge is printed, not failed on**, as "no longer exist" in the test
  output (`app/build/test-results/`). Delete those lines in the same PR or a
  later one.
- The file was seeded with the union of `main` and the merged round-5 PRs, so it
  lists a few edges `main` doesn't have yet; they show as stale until those PRs
  land.

## Data Flow Example
```
User opens app
    ↓
HomeScreen observes HomeViewModel.uiState
    ↓
HomeViewModel collects flows from TransactionRepository
    ↓
Repository queries TransactionDao
    ↓
Data flows back up as StateFlow
    ↓
UI recomposes with new state
```

## Key Technologies

### Dependency Injection
- **Hilt** for compile-time DI
- Scoped components (@Singleton, @ViewModelScoped)
- Module-based provision

**Preferences.** App settings live in one DataStore file, `user_preferences`, behind
`UserPreferencesRepository`. `di/PreferencesModule` binds its focused contracts
(`CurrencyPreferences`, `BudgetPreferences`, `ScanPreferences`, `PrivacyPreferences`,
`NotificationPreferences`, `BackupPreferences`, `BillingPreferences`,
`RecurringSuggestionPreferences`) to that one singleton. Inject the narrowest contract a
class needs; the repository type is for classes that span several groups or read the
`UserPreferences` snapshot. A new setting goes on the matching contract. Key names, value
types, defaults and the migration are pinned by `UserPreferencesKeysGoldenTest` and
`UserPreferencesStorageGoldenTest`: a rename there resets the setting on every install, and
splitting the file needs an explicitly versioned migration.

### Asynchronous Programming
- **Kotlin Coroutines** for async operations
- **Flow** for reactive streams
- **StateFlow** for UI state

### State Management
```kotlin
data class HomeUiState(
    val transactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class HomeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
}
```

The real `HomeUiState` in `presentation/home/HomeViewModel.kt` carries much more
(filters, balances, budget summaries); this trimmed version shows the shape.
[state-management.md](state-management.md) has the full patterns.

### Navigation
- **Navigation 3** (`NavDisplay`) with `@Serializable` `NavKey` back stacks; MainScreen keeps one back stack per bottom tab
- Single Activity architecture
- Deep linking support

## Testing Strategy

### Unit Tests
- ViewModels with mock repositories
- Use cases with mock data sources
- Repository logic testing

### UI Tests
- Compose testing framework
- Screenshot tests
- Navigation tests

### Integration Tests
- Room database migrations
- SMS parsing accuracy
- AI categorization

## Static analysis and coverage

Both are advisory: neither is in `./init.sh` nor in the required **Check** job.
`quality-advisory.yml` runs them in CI with `continue-on-error` and uploads the
reports as artifacts. New Detekt findings leave the job green but add a warning
annotation and list the findings in the job summary.

- **Detekt**: `./gradlew detekt` checks the production sources of `:app` (main,
  standard, fdroid), `core/*`, `feature/*` and `parser-core`. It parses syntax
  only (no type resolution, no formatting rules), on top of the default rule
  set; overrides go in `config/detekt/detekt.yml`. Findings that predate it are in
  `config/detekt/baseline.xml`, so only new ones show. Report:
  `build/reports/detekt/`. Regenerate the baseline with `./gradlew detektBaseline`
  only to drop fixed findings, not to accept new ones.
- **Kover**: only with `-Pcoverage` (or `-Pcoverage=true`; `=false` leaves it
  off), so normal builds stay uninstrumented. It
  writes per-module reports from the debug unit tests, with no aggregation or
  thresholds: `./gradlew -Pcoverage :app:koverHtmlReportStandardDebug` (or
  `koverXmlReportStandardDebug`) for `:app`, and `koverHtmlReportDebug` /
  `koverXmlReportDebug` for modules using the `build-logic` convention plugins.
  Reports go to `<module>/build/reports/kover/`. Generated Hilt and Compose
  classes are included in the totals.

## Performance Considerations

### Memory Management
- LazyColumn for large lists
- Image loading with Coil
- Proper coroutine scope management

### Database Optimization
- Indexed queries
- Batch operations
- Background processing with WorkManager

### UI Performance
- Recomposition optimization
- State hoisting
- Derivable state calculations

## Security & Privacy

### Data Protection
- Transaction parsing and AI processing run on-device
- No network calls without consent
- Encrypted preferences with DataStore

### Permissions
- Runtime permission requests
- Minimal permission scope
- Clear permission rationale

## Best Practices

### Code Organization
- Feature-based packaging
- Clear layer boundaries
- Interface-based dependencies

### Error Handling
- Sealed classes for results
- Graceful degradation
- User-friendly error messages

### Code Style
- Kotlin coding conventions
- Consistent naming patterns
- Immutable data structures
- Formatting is ktlint via Spotless (rules in `.editorconfig`), checked only on files changed since `origin/main`: run `./gradlew spotlessCheck`, fix with `./gradlew spotlessApply`.
- `-PspotlessRatchetFrom=<ref>` compares against another ref (a stacked PR's base); an empty value checks every file.
- `./init.sh` (every target) and the opt-in `.githooks/pre-push` hook run it; `check`/`build` don't. Don't reformat files you aren't changing.
- Its CI job is advisory: it can't fail the workflow.
- Dependency updates: Renovate (`renovate.json`) opens grouped PRs weekly; the coupled Kotlin/AGP/Hilt/LiteRT-LM/coroutines toolchain and all majors wait for approval on its Dependency Dashboard issue.
- Unused or mis-scoped dependencies: `./gradlew buildHealth --max-workers=2` (Dependency Analysis plugin, report only). Its known false positives are listed in [architecture-baseline.md](architecture-baseline.md#dependencies).
- Vulnerable dependencies: Dependency Review fails a PR that adds a dependency with a known high/critical advisory. Neither check is required for merge yet.
  - Gradle: `dependency-graph.yml` resolves the graph on every PR and `dependency-graph-submit.yml` submits it after validating it, which lets fork PRs work too. Its `review` job then sets the **Dependency Review (Gradle)** commit status. A PR that changes no Gradle dependency input (`*.gradle(.kts)`, `gradle/*.versions.toml`, `gradle.properties`, `build-logic/`, the wrapper properties, `dependency-graph.yml`) gets a success status as soon as its graph is submitted, with no wait. Otherwise the status stays pending until GitHub shows the PR's snapshot, usually 13 to 35 minutes, then reports the review. After 50 minutes without a snapshot it turns to error ("not reviewed"); re-run that job from the status's Details link. A newer push or a closed PR ends the wait early.
  - Everything GitHub reads itself (npm manifests, GitHub Actions workflows): `dependency-review.yml` runs the **Dependency Review (non-Gradle)** job on every PR. It needs no snapshot, so it finishes in seconds. Each check reports only its own ecosystems: Gradle (`maven`) advisories fail only the Gradle status.

## Migration & Evolution

### Database Migrations
- Room auto-migrations
- Fallback strategies
- Data integrity checks

### Feature Flags
- Gradual rollout support
- A/B testing capability
- Remote configuration ready

## Monitoring & Analytics

### Performance Monitoring
- App startup time
- Frame rendering metrics
- Memory usage tracking

### Error Tracking
- Crash reporting (opt-in)
- Non-fatal error logging
- User feedback integration