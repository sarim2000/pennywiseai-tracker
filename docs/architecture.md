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
│   ├── presentation/            # Feature screens + ViewModels (home, accounts,
│   │   │                        #   add, transactions, budgetgroups, categories,
│   │   │                        #   subscriptions, loans, groups, paywall, share,
│   │   │                        #   statement, exchangerates, common; also a
│   │   │                        #   navigation/ subpackage for feature routes)
│   ├── ui/                      # Shared components, theme, ViewModels
│   │   ├── components/          # Reusable Compose components
│   │   ├── theme/               # Design system tokens
│   │   ├── viewmodel/           # App-level ViewModels (theme, app lock, ...)
│   │   └── screens/             # analytics, chat, onboarding, rules,
│   │                            #   settings, unrecognized
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
│   │   ├── preferences/         # DataStore-backed settings
│   │   ├── currency/ backup/ contacts/
│   │   └── mapper/
│   ├── di/                      # Hilt modules (DatabaseModule, etc.)
│   ├── receiver/                # SMS / notification entry points
│   ├── worker/                  # WorkManager jobs (SMS scan retry, scheduled backups)
│   ├── widget/                  # Home-screen widgets
│   ├── billing/                 # Play Billing + FreeTierLimits
│   ├── backup/ core/ initializer/ utils/
└── build.gradle.kts             # Flavors: standard, fdroid
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

| Package (`com.pennywiseai.tracker.…`) | Owns | Must not import |
|---|---|---|
| `presentation/<feature>`, `ui/screens/<feature>` | A feature's screens, ViewModel and UI state, kept together | Another feature's screens or internal components |
| `ui/components`, `ui/theme`, `ui/icons`, `ui/animations`, `ui/effects` | Components and design tokens used by more than one feature | `presentation`, `ui/screens`, `worker` |
| `domain` | Use cases, business rules, models, service interfaces | `presentation`, `ui`, Compose, Activities or other Android UI |
| `data` | Room (`database`), repositories, DataStore (`preferences`), backup, SMS processing (`manager`) | `presentation`, `ui` |
| `utils`, `core` | Small helpers used across layers: money, currency, dates | `presentation`, `ui`, Android entry points |
| `worker`, `receiver`, `widget`, `tile`, `aiaccess` | Android entry points | Feature screen internals. Use `domain`/`data` instead |
| `di`, `navigation`, root (`MainActivity`, `PennyWiseApp`, `PennyWiseApplication`) | Wiring and the composition root | (may import anything) |

1. **No new cycles.** If two packages import each other, cut the edge that points
   from the lower layer to the higher one.
2. **Non-UI code doesn't live in screen packages.** If `data`, `domain`, a worker
   or a widget needs it, move it into `domain` (or `utils` for a small pure
   helper) instead of importing the screen package. Examples today are the
   profile filters in `presentation/common` and `presentation/accounts/AccountType`.
3. **Shared UI is feature-agnostic.** A component in `ui/components` takes plain
   parameters. It doesn't import a feature's state types. A component only one
   feature uses lives in that feature's package.
4. **Domain has no UI or Android UI dependencies.** Use cases may use Room
   entities and repositories from `data`, which is the current pattern. Introduce
   a separate domain model only when a feature needs one.
5. **Money has one definition.** Use `utils/Money.kt`, `MoneyTotals.kt` and
   `CurrencyFormatter` (see the hard constraints in `CLAUDE.md`). Check `:shared`
   before adding a model, so it isn't defined twice.
6. **Moves keep persisted names.** WorkManager stores worker class names, and
   widgets, tiles, the notification listener and the launcher activity are bound
   by their component names. Room table and column names, DataStore keys and
   backup field names must not change during a refactor either.
7. **Gradle modules.** `:parser-core` stays pure Kotlin. `:shared` `commonMain`
   has no Android API. A new module is split off only when its package has no
   back-edges into `:app`. It depends on other modules with `implementation`, and
   uses `api` only for a contract that is actually public.

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