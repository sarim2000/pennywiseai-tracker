# PRD: Unified Currency Mode

## Overview

A feature flag that enables users to view all financial data converted to a single display currency, instead of filtering by currency. Includes manual exchange rate management.

## Problem Statement

**Current behavior:**
- Currency dropdown filters transactions (shows only INR OR only AED)
- Budget only tracks transactions in base currency
- Users with multi-currency spending must mentally combine totals
- No visibility into exchange rates being used

**User pain points:**
- "I spent ₹10,000 in India and AED 500 in Dubai - what's my total spending?"
- "My budget shows ₹40,000 but I also spent in USD - am I actually over budget?"
- "What exchange rate is the app using?"

## Solution

### Feature Flag: Unified Currency Mode

A toggle in Settings that changes how currency is handled across the app.

| Aspect | Flag OFF (Default) | Flag ON |
|--------|-------------------|---------|
| Currency dropdown | Filters by currency | Converts all to selected |
| Budget tracking | Base currency only | All currencies (converted) |
| Totals | Per-currency | Aggregated + converted |
| Transaction list | Shows original currency | Shows converted (original as secondary) |

### Exchange Rate Management

A new Settings section allowing users to view and override exchange rates.

---

## User Stories

### US-1: Enable Unified Currency Mode
**As a** user with multi-currency transactions
**I want to** enable unified currency view
**So that** I see all my spending in one currency without mental math

**Acceptance Criteria:**
- Toggle available in Settings > Currency
- When enabled, display currency selector appears
- All monetary values convert to display currency
- Toggle persists across app restarts

### US-2: View Aggregated Budget
**As a** user with unified mode enabled
**I want to** see my budget include all currencies
**So that** I know my true spending against my limit

**Acceptance Criteria:**
- Budget limit stored in base currency
- Spending aggregates transactions from ALL currencies
- Each transaction converted using current exchange rate
- Category breakdown includes all-currency spending
- Progress bar reflects total converted spending

### US-3: View Exchange Rates
**As a** user
**I want to** see what exchange rates are being used
**So that** I understand how my amounts are being converted

**Acceptance Criteria:**
- New "Exchange Rates" section in Settings
- Shows all currency pairs in use (derived from user's transactions)
- Displays current rate, last updated time
- Shows rate source ("ExchangeRate-API" or "Custom")

### US-4: Override Exchange Rate
**As a** user
**I want to** set a custom exchange rate
**So that** conversions match my actual bank rate

**Acceptance Criteria:**
- Tap rate to edit with number input
- Custom rates marked with indicator
- Custom rates persist and don't get overwritten by API refresh
- "Reset to Auto" option to revert to API rate
- "Reset All" to clear all custom rates

### US-5: Refresh Exchange Rates
**As a** user
**I want to** manually refresh exchange rates
**So that** I get the latest rates on demand

**Acceptance Criteria:**
- Refresh button in Exchange Rates section
- Shows loading indicator during fetch
- Updates "Last updated" timestamp on success
- Shows error message on failure (with retry option)
- Only refreshes non-custom rates

### US-6: See Original Currency Context
**As a** user viewing converted amounts
**I want to** see the original currency
**So that** I don't lose context of where I spent

**Acceptance Criteria:**
- Transaction list shows: "₹1,850" with "(AED 80)" below or beside
- Transaction detail shows both original and converted
- Budget category rows show converted total

---

## Technical Design

### Data Model Changes

**UserPreferences (DataStore):**
```kotlin
UNIFIED_CURRENCY_MODE: Boolean = false
DISPLAY_CURRENCY: String = "INR"
```

**ExchangeRateEntity (Room) - Add field:**
```kotlin
isCustomRate: Boolean = false
```

### API Changes

**UserPreferencesRepository:**
```kotlin
val unifiedCurrencyMode: Flow<Boolean>
val displayCurrency: Flow<String>
suspend fun setUnifiedCurrencyMode(enabled: Boolean)
suspend fun setDisplayCurrency(currency: String)
```

**CurrencyConversionService:**
```kotlin
suspend fun setCustomRate(from: String, to: String, rate: BigDecimal)
suspend fun clearCustomRate(from: String, to: String)
suspend fun clearAllCustomRates()
suspend fun getActiveRates(): List<ExchangeRateEntity>  // Only pairs user has transactions in
```

**MonthlyBudgetRepository:**
```kotlin
// New method - fetches all currencies, caller handles conversion
fun getMonthSpendingAllCurrencies(year: Int, month: Int): Flow<MonthlyBudgetSpendingRaw>
```

### Screen Changes

**Settings Screen:**
- Add "Currency" section
- "Unified Currency Mode" toggle with description
- "Display Currency" dropdown (only visible when unified mode ON)
- "Exchange Rates" navigation item

**New: Exchange Rates Screen:**
- List of active currency pairs
- Each row: FROM → TO, rate, edit button, source indicator
- Pull-to-refresh
- "Reset All to Auto" button

**Home Screen:**
- When unified mode ON: currency selector changes display currency (not filter)
- Totals aggregate all currencies, converted to display currency

**Monthly Budget Screen:**
- When unified mode ON:
  - Add display currency selector
  - Fetch all-currency spending
  - Convert amounts using CurrencyConversionService
  - Show category totals as converted sums

**Transactions Screen:**
- When unified mode ON:
  - Show all transactions regardless of currency
  - Display converted amount prominently
  - Show original amount as secondary text

### Conversion Logic

```kotlin
// In ViewModel when unified mode is ON
val allTransactions = repository.getAllCurrencyTransactions(startDate, endDate)
val convertedTotal = allTransactions.sumOf { tx ->
    currencyConversionService.convertAmount(
        amount = tx.amount,
        fromCurrency = tx.currency,
        toCurrency = displayCurrency
    )
}
```

---

## UI Mockups

### Settings - Currency Section
```
┌─────────────────────────────────────┐
│  Currency                           │
├─────────────────────────────────────┤
│  Base Currency                  INR │
│  Used for budget limits             │
├─────────────────────────────────────┤
│  Unified Currency Mode          [○] │
│  Convert all transactions to        │
│  display currency                   │
├─────────────────────────────────────┤
│  Display Currency               INR │  ← Only when unified ON
│  All amounts shown in this          │
│  currency                           │
├─────────────────────────────────────┤
│  Exchange Rates                   > │
│  View and customize rates           │
└─────────────────────────────────────┘
```

### Exchange Rates Screen
```
┌─────────────────────────────────────┐
│  ← Exchange Rates              ↻    │
├─────────────────────────────────────┤
│  Last updated: Jan 25, 2026 8:00 AM │
├─────────────────────────────────────┤
│  USD → INR                          │
│  ₹83.2500                    ✏️ API │
├─────────────────────────────────────┤
│  AED → INR                          │
│  ₹22.6700                ✏️ Custom │
├─────────────────────────────────────┤
│  USD → AED                          │
│  د.إ3.6725                   ✏️ API │
├─────────────────────────────────────┤
│  ⓘ Custom rates won't be updated   │
│    automatically                    │
│                                     │
│  [ Reset All to Auto ]              │
└─────────────────────────────────────┘
```

### Budget with Unified Mode ON
```
┌─────────────────────────────────────┐
│  Monthly Budget                     │
│  Display: [INR ▼]                   │
├─────────────────────────────────────┤
│  ₹42,500 / ₹50,000                  │
│  ████████████████░░░░  85%          │
│  ₹7,500 left · ₹1,500/day           │
├─────────────────────────────────────┤
│  Category Breakdown                 │
├─────────────────────────────────────┤
│  Food                    ₹15,200    │
│  (includes AED 120 converted)       │
├─────────────────────────────────────┤
│  Shopping                 ₹8,000    │
├─────────────────────────────────────┤
│  Travel                  ₹12,300    │
│  (includes USD 45 converted)        │
└─────────────────────────────────────┘
```

### Transaction Row with Unified Mode ON
```
┌─────────────────────────────────────┐
│  🍔 McDonald's                      │
│  Food · Today                       │
│                            -₹1,850  │
│                         (AED 80.00) │
└─────────────────────────────────────┘
```

---

## Implementation Phases

### Phase 1: Foundation (Feature Flag + Preferences)
1. Add `unifiedCurrencyMode` and `displayCurrency` to UserPreferences
2. Add Settings UI for toggle and currency selector
3. No behavioral changes yet - just the preference

### Phase 2: Exchange Rate Management
1. Add `isCustomRate` field to ExchangeRateEntity
2. Create Exchange Rates screen (view only first)
3. Add manual rate override functionality
4. Add reset to auto functionality

### Phase 3: Budget Conversion
1. Add `getMonthSpendingAllCurrencies` to repository
2. Update MonthlyBudgetViewModel to check flag and convert
3. Add currency selector to MonthlyBudgetScreen
4. Show converted totals with original currency hints

### Phase 4: Home & Transactions
1. Update HomeViewModel conversion logic
2. Update TransactionsScreen to show all currencies when flag ON
3. Add secondary currency display to transaction rows
4. Update Analytics if applicable

---

## Edge Cases

1. **No exchange rate available**: Show original currency, indicate "rate unavailable"
2. **Rate fetch fails**: Use cached rate, show "as of [date]"
3. **User has only one currency**: Hide unified mode toggle (not needed)
4. **Custom rate set to 0**: Validate input, minimum 0.0001
5. **Display currency has no transactions**: Still allow selection, show ₹0

---

## Success Metrics

- Users enabling unified mode (feature adoption)
- Reduction in currency-switch actions on home screen
- Users setting custom rates (power user engagement)
- Budget accuracy (users staying within converted budget)

---

## Open Questions

1. Should we show conversion rate inline on transactions? (e.g., "@ ₹22.67/AED")
2. Historical rates - should conversions use rate at transaction time or current rate?
3. Should widget support unified mode?

---

## Dependencies

- Existing: CurrencyConversionService, ExchangeRateProvider, Room database
- No new external dependencies required
