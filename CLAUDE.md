# PennyWise Project Context

## Project Overview
PennyWise is a minimalist, AI-powered expense tracker for Android that automatically extracts transaction data from SMS messages using on-device processing.

## Important Documents
Please reference these documents when working on this project:
- **Architecture**: `/docs/architecture.md` - MVVM + Clean Architecture patterns, layer responsibilities
- **Design System**: `/docs/design.md` - Material 3 theming, colors, typography, components
- **PRD**: `/prd.md` - Product requirements, features, timeline

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

Current version: 2.1.3 (versionCode: 13)

Recent version history:
- 2.1.3: Federal Bank support, Discord community, GitHub issue templates
- 2.1.2: Spotlight tutorial, SBI/Indian Bank support, auto-scan on launch
- 2.0.1: Previous release

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

## Supported Banks (46 parsers)
- Airtel Payments Bank
- **Alinma Bank (Saudi Arabia)** - Arabic SMS support
- American Express (AMEX)
- Axis Bank
- Bank of Baroda
- Bank of India
- Canara Bank
- Central Bank of India
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
- **M-PESA (Kenya)** - Mobile money service
- **Navy Federal Credit Union (USA)** - NFCU
- **NMB Bank / Nabil Bank (Nepal)**
- OneCard
- **Priorbank (Belarus)** - Russian/Belarusian SMS support
- Punjab National Bank (PNB)
- Saraswat Co-operative Bank
- **Siddhartha Bank Limited (Nepal)**
- State Bank of India (SBI)
- Slice
- South Indian Bank
- **Standard Chartered Bank**
- Union Bank
- Utkarsh Bank

When implementing any feature, please ensure it aligns with the architecture patterns and design system defined in the documentation.


# Important
Never use pii in comments, code anywhere

# Test implementation standards for parsers
- Parser tests must use the shared JUnit helpers under `ParserTestUtils`. For
  full guidance (examples, migration checklist), read `docs/parser-test-standards.md`.
