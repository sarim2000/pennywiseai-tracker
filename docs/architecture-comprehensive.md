# PennyWise — Comprehensive Architecture Documentation

## Table of Contents
1. [Project Overview](#1-project-overview)
2. [Multi-Module Structure](#2-multi-module-structure)
3. [Tech Stack](#3-tech-stack)
4. [Architecture Layers](#4-architecture-layers)
5. [Data Flow](#5-data-flow)
6. [Database Architecture](#6-database-architecture)
7. [SMS & Notification Pipeline](#7-sms--notification-pipeline)
8. [Background Processing](#8-background-processing)
9. [Dependency Injection](#9-dependency-injection)
10. [Navigation](#10-navigation)
11. [UI System](#11-ui-system)
12. [AI / On-Device ML](#12-ai--on-device-ml)
13. [Security & Privacy](#13-security--privacy)
14. [Build Variants & Distribution](#14-build-variants--distribution)
15. [Testing Strategy](#15-testing-strategy)
16. [Key Architectural Decisions](#16-key-architectural-decisions)

---

## 1. Project Overview

PennyWise is a minimalist, AI-powered personal finance tracker for Android. Its defining characteristic is **on-device-only processing**: SMS messages and bank notifications are parsed locally using a purpose-built parser library and an on-device LLM, so no financial data ever leaves the device without explicit user consent.

**Current version:** 2.15.60 (versionCode 97)  
**Min SDK:** 26 (Android 8.0) · **Target SDK:** 36  
**Application ID:** `com.pennywiseai.tracker`

---

## 2. Multi-Module Structure

```
CashFlow/
├── app/                    # Android application (UI, ViewModels, DI, Workers)
├── parser-core/            # Multiplatform SMS/transaction parser (JVM + iOS)
├── shared/                 # Multiplatform utilities and seeded data
├── pennywise-web/          # Web companion application
└── iosApp/                 # iOS client (Kotlin Multiplatform / Swift)
```

### Module dependency graph

```
         ┌─────────────┐
         │     app     │  (Android application)
         └──────┬──────┘
                │ depends on
        ┌───────┴────────┐
        │                │
┌───────▼──────┐  ┌──────▼──────┐
│ parser-core  │  │   shared    │
│ (KMP: JVM +  │  │ (KMP: seed  │
│  iOS Arm64)  │  │  data, util)│
└──────────────┘  └─────────────┘
```

### `parser-core` module
- **Purpose:** Parse raw SMS text into structured `ParsedTransaction` objects.
- **Multiplatform targets:** JVM (Android) + iOS Arm64 + iOS Simulator Arm64.
- **Zero Android SDK dependency** — can be unit-tested on JVM without a device or Robolectric.
- Houses 200+ bank-specific parsers covering India, UAE, Saudi Arabia, Kenya, Nepal, Russia, Czech Republic, USA, and more.

### `shared` module
- Multiplatform utilities shared between Android and iOS (default category seeds, common extensions).

---

## 3. Tech Stack

### Language & Runtime
| Concern | Technology |
|---|---|
| Language | Kotlin 2.x |
| JVM target | Java 11 (app module), Java 21 (parser-core) |
| Build system | Gradle with Kotlin DSL (`.gradle.kts`) |
| Android toolchain | compileSdk 37 |

### UI
| Concern | Technology |
|---|---|
| UI framework | Jetpack Compose (BOM-managed) |
| Design system | Material 3 (Material You) |
| Adaptive color | Dynamic Color (Android 12+), custom accent fallback |
| Image loading | Coil (Compose integration) |
| Blur effects | Haze library |
| Charts | Compose Charts library |
| Markdown rendering | Markdown compose library |
| Animations | Shared element transitions, Compose animation APIs |

### Architecture & Async
| Concern | Technology |
|---|---|
| Architecture pattern | MVVM + Clean Architecture |
| State management | StateFlow / MutableStateFlow |
| Async runtime | Kotlin Coroutines + Flow |
| Dependency injection | Hilt (Dagger 2 under the hood, KSP-generated) |

### Persistence
| Concern | Technology |
|---|---|
| Relational database | Room (SQLite), schema version 52 |
| Preferences | Jetpack DataStore (Proto / Preferences) |
| Backup serialization | Gson (backup/restore JSON) + kotlinx.serialization |

### Background Processing
| Concern | Technology |
|---|---|
| Periodic/one-time jobs | WorkManager |
| App Startup | Jetpack App Startup (WorkManager initializer) |
| SMS listening | BroadcastReceiver (`SmsBroadcastReceiver`) |
| Notification listening | `NotificationListenerService` |

### Networking
| Concern | Technology |
|---|---|
| HTTP client | Ktor (Android engine) |
| Serialization | kotlinx.serialization JSON |
| Use | Exchange rates API only (opt-in) |

### On-Device AI / ML
| Concern | Technology |
|---|---|
| Inference runtime | LiteRT-LM (`litertlm-android`) |
| Model | Qwen 2.5 (loaded from device storage) |
| Use cases | Transaction categorization, chat assistant |

### Widgets
| Concern | Technology |
|---|---|
| Home screen widgets | Jetpack Glance (AppWidget + Material 3) |

### Billing & Distribution
| Concern | Technology |
|---|---|
| In-app purchases | Google Play Billing (standard flavor only) |
| In-app updates | Google Play In-App Updates (standard flavor only) |
| In-app reviews | Google Play In-App Reviews (standard flavor only) |
| F-Droid | Stub billing gateway (`FdroidBillingGateway`) |

### PDF Parsing
| Concern | Technology |
|---|---|
| PDF statement parsing | PDFBox Android |
| Parsers | `PdfStatementParser`, `GPayPdfParser`, `PhonePePdfParser` |

### Other Libraries
| Concern | Technology |
|---|---|
| CSV export | OpenCSV |
| Navigation | Navigation Compose (type-safe, Kotlin Serialization routes) |
| Biometric auth | AndroidX Biometric |
| Splash screen | AndroidX Core SplashScreen |
| Color picker | colorpicker-compose |
| Testing | JUnit 4/5, MockK, AndroidX Test, Espresso, WorkManager Testing, Room Testing |

---

## 4. Architecture Layers

PennyWise follows Clean Architecture with three layers. Dependencies only point inward.

```
┌────────────────────────────────────────────┐
│              Presentation Layer             │
│  Compose Screens · ViewModels · UiState     │
│  Widgets · Navigation                        │
└────────────────────┬───────────────────────┘
                     │ calls
┌────────────────────▼───────────────────────┐
│               Domain Layer                  │
│  Use Cases · Business Rules · Domain Models │
│  Repository Interfaces · Services           │
└────────────────────┬───────────────────────┘
                     │ calls
┌────────────────────▼───────────────────────┐
│                Data Layer                   │
│  Repositories · Room DAOs · DataStore       │
│  SMS / Notification data sources            │
│  WorkManager · Mappers · Export/Import      │
└────────────────────────────────────────────┘
```

### 4.1 Presentation Layer

**Package:** `com.pennywiseai.tracker.presentation` / `ui`

#### Screens (feature modules)
| Screen | Key ViewModel |
|---|---|
| Home | `HomeViewModel` |
| Transactions | `TransactionViewModel` / `TransactionDetailViewModel` |
| Categories | `CategoriesViewModel` |
| Accounts / Cards | `AccountsViewModel` / `CardViewModel` |
| Budgets | `BudgetViewModel` |
| Budget Groups | `BudgetGroupsViewModel` |
| Analytics | `AnalyticsViewModel` |
| Subscriptions | `SubscriptionViewModel` |
| Loans | `LoanListViewModel` / `LoanDetailViewModel` |
| Transaction Groups | `TransactionGroupViewModel` |
| Rules | `RulesViewModel` |
| Settings | `SettingsViewModel` |
| Chat (AI) | `ChatViewModel` |
| Onboarding | `OnboardingViewModel` |
| Exchange Rates | `ExchangeRatesViewModel` |
| Statement Import | `StatementImportViewModel` |
| Export | `ExportViewModel` |

#### UI state pattern
Each screen has a dedicated `UiState` data class:
```kotlin
data class HomeUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val monthlyTotal: BigDecimal = BigDecimal.ZERO,
    val isLoading: Boolean = true,
    val error: String? = null
)
```

ViewModels expose a single `StateFlow<UiState>` consumed by the Composable via `collectAsStateWithLifecycle()`.

#### Reusable components (70+)
- **Cards:** `BalanceCard`, `BudgetCard`, `TransactionItem`, `GroupCard`
- **Charts:** `SpendingBarChart`, `CategoryPieChart`, `SpendingHeatmap`
- **Common:** `CustomDateRangePicker`, `FilterChips`, `ProfileDropdown`
- **Effects:** `BlurredVisibility`, `BottomFade`, overscroll animations
- **Skeletons:** `BalanceCardSkeleton`, `TransactionItemSkeleton`

### 4.2 Domain Layer

**Package:** `com.pennywiseai.tracker.domain`

#### Use Cases
| Use Case | Responsibility |
|---|---|
| `AddTransactionUseCase` | Validate + persist a manual transaction |
| `AddSubscriptionUseCase` | Create recurring subscription entry |
| `GetCategoriesUseCase` | Retrieve categories with spending totals |
| `GenerateIncomeAutopayUseCase` | Detect income-side autopay patterns |
| `InitializeRuleTemplatesUseCase` | Seed built-in categorization rules |
| `MarkSubscriptionPaidUseCase` | Record subscription payment |
| `ApplyRulesToPastTransactionsUseCase` | Retroactively apply a new rule |
| `ImportStatementUseCase` | Orchestrate PDF statement import pipeline |

#### Domain services
| Service | Responsibility |
|---|---|
| `RuleEngine` | Evaluate `TransactionRule` conditions against a transaction and apply actions |
| `RuleTemplateService` | Provide pre-built rule templates (e.g., "Swiggy → Food") |
| `LlmService` | Interface to on-device LiteRT-LM for categorization and chat |
| `BiometricAuthManager` | App-lock, fingerprint/face auth lifecycle |
| `CurrencyConversionService` | Convert amounts between currencies using stored exchange rates |

### 4.3 Data Layer

**Package:** `com.pennywiseai.tracker.data`

#### Repositories (20+)
| Repository | Data Source |
|---|---|
| `TransactionRepository` | Room `TransactionDao` |
| `TransactionGroupRepository` | Room `TransactionGroupDao` + `TransactionSplitDao` |
| `SubscriptionRepository` | Room `SubscriptionDao` |
| `CategoryRepository` | Room `CategoryDao` |
| `MerchantMappingRepository` | Room `MerchantMappingDao` |
| `BudgetRepository` | Room `BudgetDao` + `BudgetCategoryDao` |
| `BudgetGroupRepository` | Room `BudgetSnapshotDao` |
| `CardRepository` | Room `CardDao` |
| `AccountBalanceRepository` | Room `AccountBalanceDao` |
| `ProfileRepository` | Room `ProfileDao` |
| `LoanRepository` | Room `LoanDao` |
| `UnrecognizedSmsRepository` | Room `UnrecognizedSmsDao` |
| `RuleRepository` | Room `RuleDao` + `RuleApplicationDao` |
| `ExchangeRateRepository` | Room `ExchangeRateDao` + Ktor API |
| `ChatRepository` | Room `ChatDao` |
| `UserPreferencesRepository` | DataStore |
| `AppLockRepository` | DataStore |
| `AiContextRepository` | DataStore |
| `LlmRepository` | File system (model file) + LiteRT-LM |
| `ModelRepository` | File system (model metadata) |

---

## 5. Data Flow

### 5.1 Automatic SMS Transaction Flow

```
Bank sends SMS
      │
      ▼
SmsBroadcastReceiver.onReceive()
      │ (fire & forget coroutine)
      ▼
SmsScanManager.processSms(smsBody, sender, timestamp)
      │
      ▼
BankParserFactory.getParser(sender)   ← parser-core module
      │
      ▼
BankParser.parse(smsBody, sender, timestamp)
      │ returns ParsedTransaction?
      ▼
SmsTransactionProcessor.process(parsedTransaction)
      │
      ├─► TransactionDeduplication.isDuplicate()
      │         │ (true) → discard
      │         │ (false) → continue
      ▼
ParsedTransactionMapper.toEntity()
      │
      ▼
RuleEngine.evaluate(transactionEntity)
      │ applies matching rules (category, tags, etc.)
      ▼
TransactionRepository.insert(entity)
      │
      ▼
Room Database (single source of truth)
      │
      ▼ (Flow emission)
TransactionViewModel.uiState updated
      │
      ▼
HomeScreen / TransactionListScreen recomposed
```

### 5.2 Notification-Based Flow

```
Bank app posts notification
      │
      ▼
BankNotificationListenerService.onNotificationPosted()
      │ checks BankNotificationConfig whitelist
      ▼
Extract text from notification extras
      │
      ▼
Same pipeline as SMS (SmsScanManager onward)
```

### 5.3 Manual Transaction Flow

```
User fills AddTransactionScreen
      │
      ▼
AddViewModel.save(formData)
      │
      ▼
AddTransactionUseCase.execute(input)
      │ validates, transforms
      ▼
TransactionRepository.insert(entity)
      │
      ▼
Room DB → Flow → UI update
```

### 5.4 AI Categorization Flow

```
Uncategorized transaction
      │
      ▼
LlmService.categorize(transactionDescription)
      │ (runs on IO dispatcher)
      ▼
LiteRT-LM runtime (Qwen 2.5 on-device)
      │ returns category + confidence
      ▼
TransactionRepository.update(category)
      │
      ▼
Room DB → Flow → UI update
```

### 5.5 User Preferences Flow

```
User changes setting (e.g., theme)
      │
      ▼
SettingsViewModel.updateTheme(style)
      │
      ▼
UserPreferencesRepository.setThemeStyle(style)
      │
      ▼
DataStore (encrypted preferences)
      │
      ▼ (Flow emission)
PennyWiseApp observes preferences
      │
      ▼
PennyWiseTheme recomposed with new ColorScheme
```

### 5.6 Periodic SMS Scan Flow

```
WorkManager fires OptimizedSmsReaderWorker
      │ (periodic — foreground service with notification)
      ▼
SmsScanManager.scanSmsInbox(sinceTimestamp)
      │ reads SMS content provider
      ▼
For each unprocessed SMS:
      └─► Same parser pipeline as real-time flow
```

---

## 6. Database Architecture

**Engine:** Room (SQLite)  
**Schema version:** 52  
**Migration strategy:** Auto-migrations for additive changes; manual `Migration` objects for destructive or multi-step changes.

### 6.1 Entity overview (22 tables)

| Entity | Key Columns | Purpose |
|---|---|---|
| `TransactionEntity` | id, amount, merchant, category, date, accountId, type | Core financial record |
| `SubscriptionEntity` | id, merchant, amount, frequency, nextDueDate | Recurring charges |
| `CategoryEntity` | id, name, icon, color, parentId | Hierarchical spending categories |
| `BudgetEntity` | id, name, amount, period, categoryIds | Budget definitions |
| `BudgetCategoryEntity` | budgetId, categoryId, allocatedAmount | Budget↔Category mapping |
| `BudgetMonthSnapshotEntity` | budgetId, month, spent, remaining | Historical budget snapshots |
| `CardEntity` | id, last4, bank, type, accountId | Linked debit/credit cards |
| `AccountBalanceEntity` | id, bankName, balance, lastUpdated | Account balances from SMS |
| `ProfileEntity` | id, name, currency, avatar | User profile (multi-profile support) |
| `LoanEntity` | id, lender, principal, emi, remaining | Loan/EMI tracking |
| `TransactionGroupEntity` | id, name, description | Manual transaction groups |
| `TransactionSplitEntity` | groupId, transactionId, splitAmount | Group split details |
| `RuleEntity` | id, name, conditions, actions, priority | Automation rules |
| `RuleApplicationEntity` | ruleId, transactionId, appliedAt | Rule application audit log |
| `UnrecognizedSmsEntity` | id, body, sender, timestamp | Failed-parse SMS for review |
| `BankNotificationEntity` | id, packageName, title, text, timestamp | Raw notification log |
| `MerchantMappingEntity` | rawName, canonicalName, category | Merchant name normalization |
| `ChatMessage` | id, role, content, timestamp | AI chat history |
| `ExchangeRateEntity` | fromCurrency, toCurrency, rate, fetchedAt | Cached exchange rates |

### 6.2 Type converters

Room cannot store complex types directly. Custom `TypeConverter` classes handle:
- `BigDecimal` ↔ `String` (amounts — avoids floating-point errors)
- `LocalDate` / `LocalDateTime` ↔ `Long` (epoch millis)
- Enum classes ↔ `String`
- `List<String>` ↔ JSON string

### 6.3 Database initialization

On first creation, `DatabaseModule` installs a `RoomDatabase.Callback` that:
1. Seeds default categories (from `shared` module `DefaultCategoryData`).
2. Creates a default profile.
3. Seeds built-in rule templates via `InitializeRuleTemplatesUseCase`.

### 6.4 DAO pattern

Each DAO uses suspend functions for one-shot writes and `Flow<T>` for reactive queries:

```kotlin
@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE date >= :from ORDER BY date DESC")
    fun observeFrom(from: Long): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)
}
```

---

## 7. SMS & Notification Pipeline

### 7.1 parser-core architecture

```
parser-core/src/main/kotlin/com/pennywiseai/parser/core/
├── BankParser.kt                # Abstract base (canHandle, parse)
├── BankParserFactory.kt         # Selects the right parser for a given sender
├── BankParserRegistry.kt        # Ordered list of all registered parsers
├── ParsedTransaction.kt         # Output model
├── TransactionType.kt           # DEBIT / CREDIT enum
├── MandateInfo.kt               # Mandate/recurring payment metadata
├── CompiledPatterns.kt          # Pre-compiled regex patterns (perf)
├── Hashing.kt                   # MD5 fingerprint for deduplication
└── bank/
    ├── indian/                  # ~35 Indian bank parsers
    │   ├── BaseIndianBankParser.kt   # Shared mandate + balance logic
    │   ├── HdfcBankParser.kt
    │   ├── IciciBankParser.kt
    │   └── ...
    ├── uae/                     # UAE bank parsers
    │   ├── UAEBankParser.kt     # Base class with AED/currency handling
    │   ├── LivBankParser.kt
    │   └── ...
    ├── international/           # USA, Russia, Kenya, Nepal, etc.
    │   ├── ChaseParser.kt
    │   ├── MpesaParser.kt
    │   └── ...
    └── ...
```

### 7.2 Parser selection algorithm

`BankParserFactory` iterates the ordered `BankParserRegistry` and calls `canHandle(sender)` on each parser. The first match wins. `canHandle` typically checks the SMS sender ID against a set of known bank sender strings.

### 7.3 Deduplication

`TransactionDeduplication` computes an MD5 fingerprint from `(amount, merchant, timestamp, accountLast4)` and stores it. If the same fingerprint is seen again (e.g., from both SMS and notification), the second occurrence is silently discarded.

### 7.4 Unrecognized SMS

When no parser matches (or parsing returns `null`), the raw SMS is stored in `UnrecognizedSmsEntity` for the user to review and potentially train a rule.

---

## 8. Background Processing

### 8.1 Workers

| Worker | Type | Purpose |
|---|---|---|
| `OptimizedSmsReaderWorker` | Periodic (foreground) | Scans SMS inbox for missed transactions |
| `BankNotificationRetryWorker` | One-time (retry) | Retries failed notification processing |

All workers are Hilt-injected (`@HiltWorker`). WorkManager is initialized via `WorkManagerInitializer` (Jetpack App Startup), avoiding the need for a custom `Application.onCreate()` call.

### 8.2 WorkManager initialization

```
App Startup
    │
    ▼
WorkManagerInitializer (declared in AndroidManifest provider)
    │
    ▼
WorkManager.initialize(context, Configuration.Builder()
    .setWorkerFactory(HiltWorkerFactory)
    .build())
```

### 8.3 Scheduling

`OptimizedSmsReaderWorker` uses `PeriodicWorkRequest` with a `Constraints` object requiring network-free execution (no internet needed). The worker runs as a foreground service with a low-priority notification to survive Doze mode.

---

## 9. Dependency Injection

**Framework:** Hilt 2.x (Dagger 2 under the hood, KSP code generation)

### 9.1 Component hierarchy

```
SingletonComponent (Application lifetime)
      │
      ├─ ViewModelComponent (ViewModel lifetime)
      │       └─ All @HiltViewModel classes
      │
      └─ ServiceComponent (Service lifetime)
              └─ BankNotificationListenerService
```

### 9.2 Hilt modules

| Module | Scope | Provides |
|---|---|---|
| `DatabaseModule` | `@Singleton` | `PennyWiseDatabase`, all 18+ DAOs, seeding callback |
| `ApplicationModule` | `@Singleton` | `@ApplicationScope` CoroutineScope, `ExchangeRateProvider`, `CurrencyConversionService` |
| `LlmModule` | `@Singleton` | `LlmService` (LiteRT-LM wrapper) |
| `RuleModule` | `@Singleton` | `RuleEngine`, `RuleTemplateService` |

### 9.3 Scoping rules

- Repositories: `@Singleton` — one instance app-wide.
- ViewModels: `@ViewModelScoped` (Hilt default via `@HiltViewModel`) — tied to ViewModel lifecycle.
- Use Cases: unscoped — created fresh per injection site (lightweight, stateless).

---

## 10. Navigation

**Library:** Navigation Compose with type-safe routes using Kotlin Serialization.

### 10.1 Route definitions

Routes are declared as `@Serializable` data classes/objects in `PennyWiseDestinations.kt`:

```kotlin
@Serializable object Home
@Serializable object Transactions
@Serializable data class TransactionDetail(val id: String)
@Serializable object Settings
// ...
```

### 10.2 NavHost structure

`PennyWiseNavHost` wraps `SharedTransitionLayout` (for shared element transitions) and defines all composable destinations. Navigation events are triggered by ViewModels via `NavigationCommand` sealed classes to keep screens side-effect-free.

### 10.3 Deep linking

Specific destinations support deep links, enabling external triggers (e.g., notification tap → open transaction detail).

---

## 11. UI System

### 11.1 Theming

```
PennyWiseApp
    └─ PennyWiseTheme (MaterialTheme wrapper)
            ├─ ColorScheme  (dynamic/light/dark + custom accent)
            ├─ Typography   (Material 3 type scale)
            └─ Shapes       (rounded corner scale)
```

**Theme selection priority:**
1. Dynamic color from wallpaper (Android 12+, if enabled)
2. User-selected accent color (custom theme)
3. Brand fallback colors (older Android)

User preferences (theme style, accent, font) are persisted to DataStore and observed app-wide.

### 11.2 Screen scaffold

All screens use `PennyWiseScaffold` which provides:
- Default `TopAppBar` (configurable title, nav icon, actions)
- Edge-to-edge handling (system bar insets)
- `Scaffold` with `contentWindowInsets` for bottom navigation padding
- Optional transparent top bar for immersive screens

### 11.3 Responsive layout

| Width | Navigation component |
|---|---|
| < 600 dp (phones) | `NavigationBar` (bottom) |
| 600–840 dp (tablets) | `NavigationRail` (side) |
| > 840 dp (large tablets) | `NavigationRail` + optional two-pane |

### 11.4 Widgets (Glance)

Three Glance widgets update via their own `WorkerUpdateWorker`:
- `RecentTransactionsWidget` — last N transactions
- `BudgetWidget` — budget progress ring
- `AddTransactionWidget` — quick-add shortcut

Each widget has a dedicated `DataStore` for widget-specific state to avoid coupling to the main database observing mechanism.

---

## 12. AI / On-Device ML

### 12.1 LiteRT-LM (LLM inference)

- **Library:** `litertlm-android`
- **Model:** Qwen 2.5 — downloaded once by the user and stored on device storage.
- **Interface:** `LlmService` in the domain layer; the data layer `LlmModule` provides the concrete `LiteRtLmService` implementation.
- **Threading:** All inference runs on a dedicated IO dispatcher coroutine to avoid blocking the main thread.

### 12.2 Use cases

| Feature | LLM use |
|---|---|
| Auto-categorization | Classify uncategorized transactions by merchant name + description |
| Chat assistant | Conversational Q&A about spending patterns |
| Merchant normalization | Clean raw merchant names from SMS |

### 12.3 Rule engine (non-ML)

`RuleEngine` provides deterministic categorization via user-defined rules that run before LLM inference:

```
Transaction arrives
      │
      ▼
RuleEngine.evaluate() — checks all active RuleEntity rows in priority order
      │
      ├─ MATCH found → apply actions (set category, tag, skip, etc.) → DONE
      │
      └─ NO MATCH → LlmService.categorize() (async)
```

---

## 13. Security & Privacy

### 13.1 Data residency

All financial data is stored exclusively in the on-device SQLite database. The only optional network call is the exchange rate API (`ExchangeRateProvider` via Ktor), which transmits no financial data.

### 13.2 App lock

`BiometricAuthManager` wraps `BiometricPrompt` for fingerprint/face authentication. `AppLockRepository` (DataStore) stores the lock-enabled flag and PIN hash. The lock screen intercepts navigation before any data is shown.

### 13.3 Backup encryption

Backup files are produced by `BackupExporter` as JSON (Gson). Sensitive backups can be encrypted with a user-provided passphrase using `DeviceEncryption` (AES-GCM). The RSA public key for server-side verification (if used) is injected at build time from `local.properties` and never hardcoded in source.

### 13.4 Permissions

| Permission | Reason |
|---|---|
| `RECEIVE_SMS` | Real-time SMS parsing |
| `READ_SMS` | Historical SMS scan |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Bank app notification parsing |
| `USE_BIOMETRIC` / `USE_FINGERPRINT` | App lock |
| `FOREGROUND_SERVICE` | Background SMS scan worker |
| `SCHEDULE_EXACT_ALARM` | Subscription due-date reminders |

All permissions are requested at runtime with clear rationale dialogs. No permission is requested without an explicit user action.

### 13.5 ProGuard / R8

Release builds enable full minification (`isMinifyEnabled = true`, `isShrinkResources = true`) with `proguard-rules.pro` ensuring Room entities and Gson-serialized backup models are kept.

---

## 14. Build Variants & Distribution

### 14.1 Product flavors

| Flavor | Google Play libs | Billing | ABI filters |
|---|---|---|---|
| `standard` | Yes (updates, reviews) | Google Play Billing | all (armeabi-v7a, arm64-v8a, x86, x86_64) |
| `fdroid` | No | `FdroidBillingGateway` stub (Pro always unlocked) | arm64-v8a, armeabi-v7a only |

The F-Droid flavor uses a stub `FdroidBillingGateway` that reports Pro as always active, since F-Droid policy forbids proprietary billing libraries.

### 14.2 Build types

| Build type | App ID suffix | Minification | Signing |
|---|---|---|---|
| `debug` | `.debug` | No | Debug keystore |
| `release` | — | Yes (R8) | Release keystore (from `local.properties`) |

### 14.3 APK splits

Non-bundle, non-F-Droid builds produce per-ABI APKs (armeabi-v7a, arm64-v8a, x86, x86_64) plus a universal APK.

### 14.4 Changelog automation

A Gradle task (`copyChangelog`) copies the FastLane changelog for the current `versionCode` into `generated/assets/changelog/whats_new.txt` before each build, driving the in-app "What's New" dialog.

---

## 15. Testing Strategy

### 15.1 Unit tests (JVM)

- **Parser tests:** Run on JVM using `ParserTestUtils` shared helpers (see `docs/parser-test-standards.md`). No device required.
- **ViewModel tests:** Mock repositories injected via constructor; test StateFlow emissions.
- **Use case tests:** Pure Kotlin, no Android dependencies.
- **Room migration tests:** `MigrationTestHelper` validates each schema migration.
- **Backup schema guard:** `BackupSchemaGuardTest` enforces that all backup-serialized fields have Kotlin default values (prevents restoring old backups from breaking).

### 15.2 Android instrumentation tests

- **Database integration:** Full Room DB tests on Android using `@RunWith(AndroidJUnit4::class)`.
- **WorkManager tests:** `TestListenableWorkerBuilder` verifies worker logic.
- **Compose UI tests:** `ComposeTestRule` for key user flows.

### 15.3 Test tooling

| Tool | Purpose |
|---|---|
| JUnit 4 / JUnit 5 | Unit & integration test runner |
| MockK | Kotlin-idiomatic mocking |
| AndroidX Test | Activity, Fragment, Compose test rules |
| Espresso | Legacy UI interaction (where Compose not applicable) |
| Room Testing | Migration and in-memory DB tests |
| WorkManager Testing | Worker unit tests |

---

## 16. Key Architectural Decisions

### 16.1 Single Activity

One `MainActivity` hosts the entire Compose navigation graph. This avoids Activity transition jank, simplifies back-stack management, and enables shared element transitions across the full app.

### 16.2 parser-core as a standalone module

Decoupling the parser from the Android application module means:
- Pure JVM tests — no Robolectric or emulator needed for parser coverage.
- Parsers are reusable in the iOS client and web companion.
- parser-core can be versioned and published independently.

### 16.3 StateFlow over LiveData

`StateFlow` is lifecycle-aware when collected with `collectAsStateWithLifecycle()` and is natively a Kotlin coroutines primitive — no Android SDK dependency required for testing.

### 16.4 BigDecimal for money

All monetary amounts are stored as `BigDecimal` (serialized to `String` in Room) to avoid floating-point rounding errors on financial calculations.

### 16.5 Room as single source of truth

All state — including transient UI selections that survive process death — is derived from the Room database. Repositories expose `Flow` queries so the UI stays reactive without polling.

### 16.6 Deferred LLM loading

The on-device LLM model file can be large. `LlmRepository` loads the model lazily on first chat/categorization request and holds a weak reference, allowing it to be released under memory pressure.

### 16.7 F-Droid parity via stub

Rather than maintaining two separate codebases, the F-Droid flavor uses Kotlin interfaces (`PurchaseGateway`, `EntitlementGate`) with a stub `FdroidBillingGateway` implementation. Hilt provides the correct implementation at compile time per flavor.

### 16.8 Forward/backward-compatible backups

Every entity field serialized in a backup file has a Kotlin default value. This means:
- Old backups missing new fields restore successfully (field gets default).
- New backups with unknown fields don't break old app versions (Gson `lenient` mode).
- `BackupSchemaGuardTest` enforces this contract in CI so it can never regress.
