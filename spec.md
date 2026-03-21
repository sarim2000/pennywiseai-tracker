# iOS Support for PennyWise

## Goal
Build a native iOS app (SwiftUI) for PennyWise that shares business logic via Kotlin Multiplatform (KMP), with pixel-similar UI adapted to iOS conventions. All features except AI chat and SMS auto-tracking.

## Scope
### In scope
- iOS app scaffold in `iosApp/` using SwiftUI
- Shared KMP module in `shared/` for cross-platform domain/data logic
- `parser-core` migration from JVM-only to KMP-compatible module
- Build/test parity checks to ensure Android remains stable

### Out of scope (for this initiative)
- AI chat on iOS
- iOS SMS auto-tracking (platform limitation)
- 1:1 visual cloning that violates iOS UX conventions

## Decisions
- **Shared logic**: KMP shared module (parser-core + business logic)
- **iOS UI**: SwiftUI (native, not Compose Multiplatform)
- **Database**: Room KMP for cross-platform persistence
- **DI**: Koin in shared module (Hilt stays Android-only)
- **Date/Time**: kotlinx-datetime (replaces java.time in parser-core)
- **PDF extraction**: expect/actual — PDFBox on Android, PDFKit on iOS
- **Design**: Pixel-similar to Android, adapted to iOS conventions (nav style, sheets, haptics, SF Symbols)

## Architecture

```diagram
{
  "id": "kmp-architecture",
  "type": "diagram",
  "version": 1,
  "createdAt": "2026-03-04T00:00:00Z",
  "createdBy": "agent",
  "grammar": "architecture",
  "model": {
    "nodes": [
      {"id": "ios-app", "label": "iOS App\n(SwiftUI)", "kind": "client"},
      {"id": "android-app", "label": "Android App\n(Compose)", "kind": "client"},
      {"id": "shared", "label": "Shared KMP Module", "kind": "service", "semanticStyle": "highlighted"},
      {"id": "parser", "label": "parser-core\n(KMP)", "kind": "service"},
      {"id": "db", "label": "Room KMP\n(SQLite)", "kind": "database"},
      {"id": "repos", "label": "Repositories\n& Use Cases", "kind": "service"},
      {"id": "ios-platform", "label": "iOS Platform\n(PDFKit, Keychain, etc.)", "kind": "infrastructure"},
      {"id": "android-platform", "label": "Android Platform\n(PDFBox, DataStore, etc.)", "kind": "infrastructure"}
    ],
    "edges": [
      {"id": "e1", "from": "ios-app", "to": "shared", "label": "Swift Export"},
      {"id": "e2", "from": "android-app", "to": "shared", "label": "Kotlin"},
      {"id": "e3", "from": "shared", "to": "parser", "label": "depends on"},
      {"id": "e4", "from": "shared", "to": "db", "label": "Room DAOs"},
      {"id": "e5", "from": "shared", "to": "repos", "label": "business logic"},
      {"id": "e6", "from": "shared", "to": "ios-platform", "label": "expect/actual", "dashed": true},
      {"id": "e7", "from": "shared", "to": "android-platform", "label": "expect/actual", "dashed": true}
    ]
  },
  "baseView": {
    "layout": {"type": "layered", "direction": "TB", "spacing": 120}
  }
}
```

## Phases Overview

| Phase | Focus | Waves |
|-------|-------|-------|
| **Phase 1** | Foundation — KMP setup, parser-core migration, iOS project scaffold | 1-2 |
| **Phase 2** | Core data — Room KMP, repositories, manual transactions | 3-4 |
| **Phase 3** | Categories, budgets, accounts | 5-6 |
| **Phase 4** | PDF import, analytics, charts | 7-8 |
| **Phase 5** | Rules, subscriptions, settings, search | 9-10 |
| **Phase 6** | Theming, onboarding, biometric lock, backup/restore, polish | 11-12 |

---

## Phase 1: Foundation (Waves 1-2)

This is what we'll start with. The rest will be specced in detail as we progress.

### Acceptance Criteria (Phase 1)
- [x] parser-core compiles as KMP module targeting JVM + iOS (arm64, simulatorArm64)
- [x] All 85+ existing parser tests pass on JVM after migration
- [x] `java.time.LocalDateTime` replaced with `kotlinx-datetime` in all parser-core files
- [x] `java.security.MessageDigest` replaced with KMP-compatible solution
- [x] iOS Xcode project exists at `iosApp/` with SwiftUI scaffold
- [x] iOS app builds and runs on simulator showing a "Hello PennyWise" screen
- [x] shared KMP module exists at `shared/` with commonMain, androidMain, iosMain
- [x] Android app still builds and all existing tests pass (no regressions)
- [x] `settings.gradle.kts` includes `:shared` and `:iosApp` modules

### Non-goals (Phase 1)
- No iOS UI screens beyond scaffold
- No database setup yet
- No DI setup yet
- No feature implementation

### Verification Plan
```
# All parser tests pass
./gradlew :parser-core:test

# Android app still builds
./gradlew :app:assembleDebug

# Shared module compiles for all targets
./gradlew :shared:build

# iOS framework builds
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

### Rollback Plan
All changes are additive (new modules, new files). Existing Android code stays untouched except parser-core build.gradle migration. Revert by restoring parser-core/build.gradle.kts to JVM plugin.

---

## Phase 1 Tasks

- [x] [Migrate parser-core to KMP module](intent://local/task/d9a9e436-6828-4b47-ba5b-bda1763949b3)

- [x] [Create shared KMP module](intent://local/task/933af8b0-f614-4a3d-bd1a-bea1c463178c)

- [x] [Scaffold iOS Xcode project](intent://local/task/89213103-678f-4323-bf26-68e222684af6)

### Recommended Execution Order (Phase 1)
1. Migrate `parser-core` to KMP targets (JVM + iOS)
2. Replace non-KMP APIs (`java.time`, `MessageDigest`) with multiplatform-compatible implementations
3. Re-run parser test suite on JVM to confirm zero behavior regressions
4. Create `shared/` KMP module with `commonMain`, `androidMain`, `iosMain`
5. Integrate `shared` module into Gradle settings and Android build
6. Scaffold `iosApp/` SwiftUI host app and wire exported shared framework
7. Validate end-to-end build matrix (`app`, `parser-core`, `shared`, iOS framework)

### Risks and Mitigations
- **KMP migration drift in parser behavior**
  - Mitigation: run existing parser tests before/after each migration slice and compare pass count
- **iOS framework export / interop friction**
  - Mitigation: keep first iOS deliverable minimal (`Hello PennyWise`) and defer feature complexity
- **Room KMP maturity/tooling edge cases**
  - Mitigation: defer Room integration to Phase 2 and keep Phase 1 persistence-free

### Definition of Done (Phase 1)
- All Phase 1 acceptance criteria checked
- Build and verification commands run successfully on local machine
- No Android regression in compile/test baseline
- iOS simulator run demonstrated with shared module linked

### Open Questions
- Should Swift export use static or dynamic framework by default for `shared`?
- Do we want iOS-specific CI checks in Phase 1 or start in Phase 2?
- Should we keep parser test execution JVM-only for now, or add selected iOS smoke tests in this phase?

---

## Phase 2: Core Data (Waves 3-4)

Current execution strategy: keep Android data layer stable while building shared Room KMP + shared repositories/use-cases for iOS-first integration.

### Acceptance Criteria (Phase 2) - Current Status
- [x] `shared` contains Room KMP schema for core manual transaction scope (`transactions`, `categories`)
- [x] `shared` provides platform database factory for Android + iOS via expect/actual
- [x] Default categories are seeded idempotently in shared initialization
- [x] Shared repositories for transactions/categories are wired to Room DAOs
- [x] iOS app consumes shared data layer for manual add flow
- [x] iOS app supports manual edit flow wired to shared update use-case
- [ ] Android app migrated to consume shared repositories for this scope (deferred)
- [ ] Accounts/splits/merchant mapping parity in shared (deferred)

### Phase 2 Tasks - Progress
- [x] Wave 3 foundation: Room KMP setup in `shared`
- [x] Wave 3 foundation: minimal core schema + repository wiring
- [x] Wave 4 start: shared manual create/update use-cases with validation
- [x] Wave 4 start: iOS SwiftUI screen wired to shared add/edit
- [ ] Wave 4 next: broaden iOS manual transaction UX beyond scaffold-level interaction
- [ ] Wave 4 next: add shared unit tests for create/update paths (intentionally deferred for now)

### Verification (executed during implementation)
```
./gradlew :shared:build
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
./gradlew :app:assembleDebug
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'id=1FDFB3E9-AEE5-41C9-BCFC-29026BB7E20F' build
```

### Android vs iOS Screen Parity Matrix

| Android screen/route | Android status | iOS status | Gap |
|---|---|---|---|
| Home (`home`) | Full | Partial | No dedicated Home destination/layout parity |
| Transactions list/filter (`transactions?...`) | Full | Partial | No advanced filters/search/sort/navigation route |
| Transaction detail (`TransactionDetail`) | Full | Partial | Not a full screen flow; limited actions |
| Add Transaction (`AddTransaction`) | Full | Partial | No dedicated add screen parity |
| Subscriptions (`subscriptions`) | Full | Partial | No full subscriptions management UX |
| Analytics (`analytics`) | Full | Missing | Entire analytics experience absent |
| Chat (`chat`) | Full | Missing | Out of iOS scope for this initiative |
| Settings (`settings`) | Full | Missing | No settings hub on iOS |
| Appearance (`appearance`) | Full | Missing | Theme/appearance controls absent |
| Categories (`categories`) | Full | Missing | No categories management screen |
| Unrecognized SMS (`unrecognized_sms`) | Full | Missing | No screen/workflow exposed |
| FAQ (`faq`) | Full | Missing | No FAQ/help screen |
| Manage Accounts (`manage_accounts`) | Full | Missing | No accounts management UI |
| Add Account (`add_account`) | Full | Missing | No add-account flow |
| Account Detail (`AccountDetail`) | Full | Missing | No account detail route |
| Rules (`Rules`) | Full | Missing | No rules list UI |
| Create/Edit Rule (`CreateRule`) | Full | Missing | No rules editor |
| Budget Groups (`BudgetGroups`) | Full | Missing | No budget groups screen |
| Budget Group Edit (`BudgetGroupEdit`) | Full | Missing | No budget group editor |
| Monthly Budget Settings (`MonthlyBudgetSettings`) | Full | Missing | No monthly budget settings UI |
| Exchange Rates (`ExchangeRates`) | Full | Missing | No exchange rates screen |
| Import Statement (`import_statement` / `ImportStatement`) | Full | Partial | No dedicated screen parity/workflow depth |
| App Lock (`AppLock`) | Full | Missing | No lock screen flow |
| Onboarding (`OnBoarding`) | Full | Missing | No onboarding flow |