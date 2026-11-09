# Architecture baseline

The starting point for the refactor in [ADR-0003](adr/0003-incremental-architecture-modernization.md),
recorded in November 2026 at commit `74248cd5`. Later refactor PRs compare against
these numbers. They should remove the cycles and back-edges listed here without
adding new ones, and should not slow the build without saying why.

Re-check the package graph with:

```bash
scripts/arch/package-deps.sh                            # top-level matrix + cycles
scripts/arch/package-deps.sh 2 | sed -n '/^Cycles/,$p'  # one level deeper, cycles only
```

## Size

`:app` has 505 production Kotlin files (495 in `main`, 5 each in `standard` and
`fdroid`), about 116,400 lines, and 171 unit-test files, about 23,100 lines. The repo
has 1,200 `.kt` files in total, 499 of them in `:parser-core` and `:shared`.

| Package | Files | | Package | Files |
|---|---:|---|---|---:|
| `data` | 164 | | `billing` | 12 |
| `ui` | 124 | | `aiaccess` | 8 |
| `presentation` | 79 | | `di`, `receiver` | 6 each |
| `domain` | 39 | | `(root)`, `backup`, `core` | 3 each |
| `widget` | 25 | | `navigation` | 2 |
| `utils` | 19 | | `initializer`, `tile` | 1 each |
| `worker` | 10 | | | |

`:app` uses the other modules lightly: 29 files import `:parser-core`
(`com.pennywiseai.parser.core`), and 6 import `:shared`, only for
`SharedCategoryMapping` and `DefaultCategoryData`. `:shared` has its own entities,
DAOs and repositories for iOS. Check them before adding any model to a future core
module, so money and transaction types don't get a second definition.

## Package dependency map

Counts are `import com.pennywiseai.tracker.…` lines in production sources. Read the
table row by row: the row package imports the column package that many times.
Fully qualified names in code are not counted, so treat the numbers as a trend.

```text
Imports between :app packages, depth 1 (row imports column)
                       1    2    3    4    5    6    7    8    9   10   11   12   13   14   15   16   17
 1 (root)              .    .    .    .    .    5    .    .    .    5    .    1    .    5    4    1    .
 2 aiaccess            2    .    .    2    .   19    .    5    .    .    .    .    .    .    3    .    .
 3 backup              .    .    .    .    .    .    .    .    .    .    .    .    .    .    .    .    1
 4 billing             .    .    .    .    .    3    3    .    .    .    .    .    .    .    .    .    .
 5 core                .    .    .    .    .    .    .    .    .    .    .    .    .    .    .    .    .
 6 data                1    .    .    4    7    .    1   47    .    .    1    4    .    4   23    3    4
 7 di                  .    .    .    8    .   34    .    2    .    .    .    .    .    .    .    .    .
 8 domain              .    .    .    .    .  106    1    .    .    .    3    .    .    .    5    .    .
 9 initializer         .    .    .    .    .    .    .    .    .    .    .    .    .    .    .    .    .
10 navigation          .    .    .    .    .    .    .    .    .    .    .    .    .    7    .    .    .
11 presentation        1    .    .   10    4  330    2   58    .    .    .    .    .  351   85    6    1
12 receiver            2    .    .    .    .   32    1    8    .    .    .    .    .    6    5    3    2
13 tile                1    .    .    .    .    .    .    .    .    .    .    .    .    .    .    .    .
14 ui                  .    5    2    5    8  135    .   21    .   57   35    4    .    .   71    .    2
15 utils               .    .    .    .    1   15    .    .    .    .    .    .    .    .    .    .    .
16 widget              6    .    .    .    .   36    .    4    .    .   14    2    .   30    9    .    .
17 worker              4    .    1    .    1   49    .    7    .    .    8    3    .    .   15    2    .
```

**Cycles.** 23 pairs of top-level packages import each other. Only `core`,
`initializer` and `tile` sit outside the tangle. At depth 2 the script finds 37
pairs. The ones that matter most:

| Pair | Imports (a→b / b→a) | What it means |
|---|---:|---|
| `presentation` ↔ `ui` | 351 / 35 | Feature screens live in two trees, `presentation/` and `ui/screens/` |
| `data` ↔ `domain` | 47 / 106 | Use cases work on Room entities and concrete repositories |
| `data` ↔ `presentation`, `ui` | 1 / 330, 4 / 135 | Mostly the expected direction. The back-edges are listed below |
| `data` ↔ `worker`, `receiver`, `widget` | 4 / 49, 4 / 32, 3 / 36 | `data.manager` schedules workers and builds notification intents, and preferences refresh widgets |
| `navigation` ↔ `ui` | 7 / 57 | Route definitions and screens reference each other |
| `presentation.home` ↔ `ui.components` | 31 / 3 | A shared card imports Home's safe-to-spend types |

### Back-edges

These are places where a lower layer imports a higher one. The rules in
[architecture.md](architecture.md#ownership--dependency-rules) forbid new ones.

| Edge | Imports | Where |
|---|---:|---|
| `data` → `presentation` | 1 | `data/repository/AccountBalanceRepository.kt` imports `presentation.accounts.AccountType` |
| `data` → `ui` | 4 | `data/statement/ImportStatementUseCase.kt` and `data/webhook/WebhookValidation.kt` import `ui.UiText`. `data/repository/SubscriptionRepository.kt` imports `ui.icons.CategoryMapping`. `data/share/ShareCardExporter.kt` imports `ui.components.SHARE_CARD_URL` |
| `domain` → `presentation` | 3 | `domain/conscience/ConscienceEligibility.kt` imports `presentation.common.{buildProfileAccountKeys, filterTransactionsByProfile}`. `domain/model/AccountTypeExtensions.kt` imports `presentation.accounts.AccountType` |
| `domain` → Compose / Android UI | 2 files | `domain/model/AccountTypeExtensions.kt` (Compose icons, `stringResource`) and `domain/security/BiometricAuthManager.kt` (`FragmentActivity`) |
| `domain` → `data` | 106 | 55 imports of `data.database.entity`, 27 of `data.repository`, 16 of `data.database`. This is the codebase's normal pattern, not a stray import. `domain/repository/` holds a single interface, `RuleRepository` |
| `domain` → `di` | 1 | `domain/usecase/RecurringSuggestionsUseCase.kt` imports the `ApplicationScope` qualifier |
| `ui.components` → `presentation` | 9 in 5 files | `ReviewPromptBanner` (`presentation.review`), `cards/SafeToSpendCard` (`presentation.home`), `PennyWiseBottomNavigation` (`presentation.navigation.BottomNavItem`), `TransactionTypeFilterUi` (`presentation.common`), `ShareCard` (`presentation.share`) |
| `ui.components` → `ui.screens`, `worker` | 2, 1 | `CategoryBreakdownCard` and `TagBreakdownCard` import `ui.screens.analytics` data classes. `SmsParsingProgressDialog` imports `worker.OptimizedSmsReaderWorker` |
| `ui.theme` → `data` | 3 | `ui/theme/Theme.kt` imports the `AccentColor`, `AppFont` and `ThemeStyle` enums from `data.preferences` |
| `utils` → `data` | 15 | `utils` is not a leaf package. `BalanceCalculator`, `BalanceDiscrepancy`, `EntityExtensions`, `TransactionFilters` and `TransferPairs` use Room entities, and `CurrencyFormatter` and `AppDates` use preference enums. `data` imports `utils` 23 times in return. No `utils` → `presentation` or `ui` edge exists |
| `worker`, `widget` → `presentation` | 8, 14 | The worker imports `presentation.review` (`PeriodReviewLoader` and helpers), `presentation.common` profile filters and `presentation.add.QuickAddActivity`. The widgets import `presentation.goals` helpers (`GoalTone`, `GoalRing`, `statusLine` and others), the same profile filters and `QuickAddActivity` |

**Pattern.** Most back-edges point at non-UI code that lives in a screen package
and is used elsewhere: the profile filters in `presentation.common` (used by
`domain`, `worker` and `widget`), `presentation.accounts.AccountType` (used by
`data` and `domain`), and the period-review loader in `presentation.review` (used
by `worker`). Moving these down into `domain` is the cheapest boundary fix.

## Coupling hotspots

"Injected" counts `@Inject` / `@AssistedInject` constructor parameters.
"Packages" counts the distinct internal packages a file imports, and "Imports"
counts its distinct internal import lines. Fifteen files take eight or more
injected collaborators.

| File | Injected | Packages | Imports | Lines |
|---|---:|---:|---:|---:|
| `presentation/home/HomeViewModel.kt` | 20 | 12 | 49 | 2,117 |
| `ui/screens/settings/SettingsViewModel.kt` | 18 | 11 | 26 | 936 |
| `worker/OptimizedSmsReaderWorker.kt` | 17 (15 + 2 assisted) | 12 | 26 | 1,504 |
| `presentation/transactions/TransactionDetailViewModel.kt` | 17 | 13 | 39 | 1,454 |
| `presentation/transactions/TransactionsViewModel.kt` | 16 | 10 | 37 | 1,793 |
| `presentation/add/AddViewModel.kt` | 15 | 8 | 20 | 898 |
| `data/manager/SmsTransactionProcessor.kt` | 11 | 12 | 26 | 509 |
| `aiaccess/AiTools.kt` | 11 | 8 | 16 | 221 |
| `ui/screens/analytics/AnalyticsViewModel.kt` | 10 | 9 | 29 | 981 |
| `data/repository/LlmRepository.kt` | 10 | 7 | 20 | 726 |

Composables and Android entry points with the widest reach, which take no
constructor injection:

| File | Packages | Imports | Lines | Note |
|---|---:|---:|---:|---|
| `ui/screens/settings/SettingsPages.kt` | 15 | 67 | 1,700 | |
| `presentation/transactions/TransactionDetailScreen.kt` | 14 | 46 | 2,769 | |
| `presentation/add/QuickAddActivity.kt` | 14 | 23 | 419 | 7 `@Inject` fields |
| `ui/MainScreen.kt` | 13 | 63 | 626 | |
| `presentation/home/HomeScreen.kt` | 13 | 48 | 1,821 | |
| `ui/screens/settings/AppearanceScreen.kt` | 8 | 94 | 988 | Most imports are theme tokens and icons |
| `data/database/PennyWiseDatabase.kt` | 4 | 60 | 939 | Entities, DAOs and migrations, as expected for a Room database |

### Largest files

| File | Size | Lines |
|---|---:|---:|
| `presentation/accounts/ManageAccountsScreen.kt` | 123 KB | 2,643 |
| `presentation/transactions/TransactionDetailScreen.kt` | 122 KB | 2,769 |
| `presentation/home/HomeViewModel.kt` | 106 KB | 2,117 |
| `presentation/home/HomeScreen.kt` | 92 KB | 1,821 |
| `presentation/transactions/TransactionsViewModel.kt` | 80 KB | 1,793 |
| `ui/screens/settings/SettingsPages.kt` | 77 KB | 1,700 |
| `presentation/transactions/TransactionsScreen.kt` | 76 KB | 1,579 |
| `worker/OptimizedSmsReaderWorker.kt` | 73 KB | 1,504 |
| `ui/screens/rules/CreateRuleScreen.kt` | 71 KB | 1,438 |
| `presentation/transactions/TransactionDetailViewModel.kt` | 66 KB | 1,454 |
| `data/preferences/UserPreferencesRepository.kt` | 63 KB | 1,478 |
| `data/repository/BudgetGroupRepository.kt` | 58 KB | 1,233 |

Size alone doesn't justify splitting a file. A split is worth doing when the
pieces have separate owners or separate tests.

## Build baseline

**Machine.** Apple M3 (8 cores), 24 GB RAM, macOS 27.2, about 12 GB of free disk.
Gradle 9.8.2 launched with Homebrew OpenJDK 21.0.13; the daemon JVM is picked by
`gradle/gradle-daemon-jvm.properties` (toolchain 21). AGP 9.5.0, Kotlin 2.4.21,
KSP 2.3.13. The user-level `~/.gradle/gradle.properties` sets
`org.gradle.workers.max=4` and `-Xmx6144m`, overriding the project's 4 GB. The
local build cache and configuration cache were both off; CI passes `--build-cache`.
The Gradle daemon was already warm, and the worktree started with no build
directories.

| Measurement | How | Result |
|---|---|---|
| Clean compile | `./gradlew clean`, then `./gradlew :app:compileStandardDebugKotlin` (19 tasks: `:parser-core`, `:shared`, `:app` KSP + Kotlin) | 58 s, 49 s, 36 s over 3 runs. Times fell as the Kotlin daemon and file cache warmed |
| Incremental compile | Append a comment line to `utils/ShakeDetector.kt` (no ABI change), re-run the same task | 3 s in all 3 runs (KSP + Kotlin for `:app`) |
| `./init.sh app` | Compile, then `:app:testStandardDebugUnitTest` | Passed: 1,353 tests in 172 classes, 0 failures, 0 skipped. The test task took 32 s after compiling |
| Debug APK | `./gradlew :app:assembleStandardDebug` (53 s after the compile above) | ABI splits: universal 111,007,919 B (105.9 MiB), arm64-v8a 80,444,410 B (76.7 MiB), x86_64 84,497,782 B (80.6 MiB), armeabi-v7a 57,709,550 B (55.0 MiB), x86 57,595,398 B (54.9 MiB) |

A single run can swing by 20 s, so compare medians of at least three runs on the
same machine.

**Runtime (startup, scrolling, memory) is not measured here.** Those numbers need a
physical device. Use the Macrobenchmark procedure in [performance.md](performance.md),
whose last emulator measurement was inconclusive.
