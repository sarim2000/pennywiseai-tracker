# Kosh-inspired Roadmap

Status: **Reviewed draft**. Codex review applied: 3 blockers and 13 should/nit items. Base: `main` @ `968515dab` · Room `SCHEMA_VERSION = 68`. Line refs are as of that commit; re-check them when you start a milestone.

Ideas taken from [Kosh](https://github.com/AIM-IT4/kosh) — a GPL-3.0, offline-first
Android finance tracker — that fill real gaps in PennyWise. Planned jointly by Claude
(Opus 5.5) and Codex (gpt-6.1-sol, high), with every hook checked against PennyWise's code.

> **Licence:** Kosh is GPL-3.0. Reimplement the *ideas* from scratch against PennyWise's
> models. Never copy its code, strings, or assets. The Kosh paths in the appendix are
> for behavioural reference only.

## Goals
- Turn the data PennyWise already holds into **actionable, on-device insights**.
- Make "how much can I spend" **commitment-aware**: card bills, recurring payments and goal reserves.
- Make missing SMS imports **diagnosable** without support tickets.
- Add a few **everyday quality-of-life wins**: a calculator for amounts, a calendar view, and PDF reports.

## Non-goals (explicitly cut)
India-only tax, EPF/NPS and bank interest tables · household Wi-Fi sync · family SMS
forwarding · Monte Carlo goal odds · decoy PIN · Beancount/Ledger export · bank-loan
amortisation (`LoanEntity` models person-to-person IOUs; bank loans would need a new
model) · envelope budgeting (it overlaps budget groups) · iOS, web and `shared` parity
(separate work).

---

## Definition of Done — every PR

- [ ] One concern per PR, and each commit compiles on its own. Conventional title: `feat(insights): …`.
- [ ] **Money:** format with `CurrencyFormatter.formatCurrency(amount, code)`. Never sum across currencies; use `sumByCurrency` and render with `formatByCurrency`. A single total is allowed only in unified mode, via `CurrencyConversionService.convertAmountOrNull`, and a currency with no rate stays its own line (#670).
- [ ] **Privacy:** screen amounts respect `AmountPrivacy`. Notifications, PDFs and logs pass `maskIfHidden = false`.
- [ ] **Scoping:** respect the active profile, ignored/hidden accounts (`IgnoredAccountsStore`), `countCreditCardAsExpense`, transfer pairs (`forTotals()`), and the budget cycle start day (`BudgetCycle`, including the `-1` last-working-day value).
- [ ] **Prefs:** every new DataStore key is placed in exactly one bucket of `SettingsBackup` (`UserPreferencesRepository.kt` ~:259). `SettingsBackupTest` enforces this.
- [ ] **Schema:** follow the **backup-maintainer** skill. Every new column has a Kotlin default and an SQL default, SQL runs on SQLite 3.18, the schema JSON is regenerated, and the migration is added to `ALL_MIGRATIONS`.
- [ ] **UI:** use `PennyWiseScaffold`, existing tokens and cards (`docs/design.md`), localised strings, light and dark themes, RTL, 200% font scale, and accessible labels.
- [ ] **Layers:** rules live in `domain/` as pure Kotlin with unit tests. Loading goes in a repository or use case. Workers never depend on ViewModels.
- [ ] **Verify:** run `./init.sh app`. UI or runtime changes also go through **emulator-verify**. Migrations also need the `Api26_Migration` AVD upgrade check (`docs/database-migrations.md`).

---

## Milestone overview

| # | Milestone | Value | Effort | Depends on | Schema |
|---|---|---|---|---|---|
| M0 | Quick wins: calculator keypad + digest "budget left" | Med | S | — | none |
| M1 | Insights engine + Insights page + monthly score | **High** | M | — | none |
| M2 | SMS import check-up | **High** | M | — | none (prefs) |
| M3 | Emergency runway + all-goal affordability | High | S–M | M1 | none |
| M4 | Card bills + commitment-aware Safe-to-spend | **High** | L | core: — · task 6: M1 · goal reserve: M3 | **Wave A** |
| M5 | Tracked net worth with provenance + trend | Med | M | M4 (wave A) | none |
| M6 | Spending + upcoming-payments calendar | Med | M | M4 (`UpcomingPayments`) | none |
| M7 | Bounded budget rollover | Med | M–L | — | **Wave B** |
| M8 | Monthly/yearly PDF reports | Med | S–M | — | none |
| M9 | PDF receipts, then warranty reminders | Low–Med | M | — | **Wave C** |

Suggested release grouping: **M0+M1** → **M2+M3** → **M4+M5** → **M6+M8** → **M7** → **M9**.
M0, M2, M7 and M8 are independent and can run in parallel worktrees.

---

## M0 — Quick wins

**Goal:** two small, visible improvements with no new data.

### Tasks
1. **Amount calculator: evaluator.** New `domain/model/AmountExpression.kt`, a BigDecimal evaluator for `+ − × ÷` and parentheses with bounded input length. Handle locale decimal separators and Latin digits, and round to the currency's fraction digits. Unit tests cover precedence, ÷0, malformed input and huge numbers.
2. **Amount calculator: UI and wiring.** New `ui/components/AmountCalculatorSheet.kt` (keypad plus live result), opened from a trailing icon on the main amount field in `presentation/add/TransactionTabContent.kt:233` and on `presentation/add/QuickAddActivity.kt:244`. **Apply** goes through each form's existing path: `AddViewModel.updateTransactionAmount` for the main form and `normalizeAmount` (`QuickAddActivity.kt:379`) for Quick Add. **Cancel** leaves the draft untouched. A zero or negative result is rejected in the sheet, before AddViewModel's input filter can silently strip the minus sign. Other amount fields (split, goals, loans) come later, only if users ask.
3. **Digest "budget left".** Extend `worker/DailySummaryWorker.kt` (don't add a second 9 PM notifier) with one segment: "· X left in <budget>". Reuse the **per-budget resolved summaries** (`BudgetGroupRepository.getGroupSpendingAllCurrencies`) rather than the widget's own aggregation. A budget's limit is in its own currency, so show one figure per budget and never repeat that allowance for each spending currency. When some spending can't be converted, mark the figure "incomplete" instead of overstating what's left. Extend `DailySummaryWorkerTest`.

**Acceptance:** `12.5+30×2` applies `72.50` in a 2-decimal currency and `73` in a 0-decimal currency. Cancel leaves the field as it was. The digest shows the budget-left segment only when a LIMIT budget exists, and never shows the same budget twice.

---

## M1 — Insights engine + page

**Goal:** a screen that tells users what needs attention this period, why, and where to fix it. Everything is computed on-device from data PennyWise already has.

**Reuse, don't rebuild.** Codex proposed a new `FinancialFacts` layer. **We decided against it for now.** `domain/review/PeriodReviewLoader.kt` already produces per-currency income, spending, categories (with the previous period) and months, with profile, ignored-account, card and loan scoping. `SpendForecast` (`SpendOutlook.kt:19`), `budgetPace` (`BudgetPace.kt:24`), `MoneyAlertRules`, `SubscriptionCost.monthlyEquivalent` and `RecurringCharge.isPriceChange` cover most of the rest. Only extract a shared loader if a second consumer actually duplicates the code.

### Tasks
0. **Inputs `PeriodReview` doesn't carry.** `PeriodReview` keeps only the top 5 categories, previous-period *spending* but no previous income, and calendar-month points only for yearly reviews. It has no merchants or raw rows. Add a small scoped query, using the same scoping as `PeriodReviewLoader`, that returns the current cycle's expense rows (for medians and merchants) and income and expense for the last 6 cycles. Prefer extending `PeriodReviewLoader` with an optional detail flag over adding a new layer. M8 reuses these inputs.
1. **Rule engine.** New `domain/insights/InsightRules.kt`: pure functions from an input snapshot to `List<Insight>`. Each insight has a stable id (`rule:v1:period:currency:subject`), a severity (`ALERT` / `WARNING` / `GOOD`), typed evidence (`Money` values, percentages, dates — never preformatted strings) and a **domain action id**, e.g. `InsightAction.OpenBudget(id)`. It never holds a `NavKey`: domain code must not import navigation (`docs/architecture.md`). `presentation/insights` maps actions to destinations. First rules:
   - budget on pace to overshoot, with the per-day amount left (`budgetPace`)
   - category spike vs its 3-cycle average (needs ≥2 cycles of history; otherwise suppress)
   - savings rate this cycle (income must be > 0)
   - unusual large transaction (> N× the category median, excluding known recurring charges)
   - subscription price change and the total monthly subscription cost
   - credit-card utilisation (only when `creditLimit` is set and in the same currency)
   - bills due in the next N days (subscriptions; card bills join in M4)

   Unit-test every rule for sparse history, zero values, a mix of currencies and ordering.
2. **Loader and dismissals.** New `domain/insights/InsightsLoader.kt`, which gathers the inputs from the existing sources above. New `data/preferences/InsightPreferences.kt`, modelled on `RecurringSuggestionPreferences.kt`: a `Set<String>` of dismissed ids, keyed per period so a dismissal expires when the period ends. Wire it in `di/PreferencesModule.kt` and classify it **DEVICE_ONLY** in `SettingsBackup`.
3. **Insights screen.** New `presentation/insights/InsightsScreen.kt` and `InsightsViewModel.kt`, with a `NavKey` in `navigation/PennyWiseDestinations.kt` and an `entry<>` in `PennyWiseNavHost.kt`. Layout:
   - a **period summary card**: income, spent, saved and savings rate, plus a 6-cycle bar chart with the current cycle highlighted
   - severity sections: **Needs attention** (error colour), **Heads up** (tertiary or warning), and **Doing well**
   - expandable cards with metric chips (e.g. Spent / Budget / Projected / Per-day left), an action button, dismiss with undo, and "show dismissed"

   With more than one currency, a currency selector filters the whole page. The page never shows a mixed total.
4. **Entry points.** Add an `INSIGHTS` value to `HomeSection` (`data/preferences/HomeSection.kt`) and render a compact "top 2 insights" card in `presentation/home/HomeSections.kt`. Add a link from Analytics. Retarget the Home menu's "Reviews" item, which already uses the `Insights` icon (`HomeSheets.kt:73`), or add a sibling entry next to it. No new bottom-nav tab.
5. **Monthly score.** New `domain/insights/PeriodScore.kt`: a 0–100 **tracking health indicator** built from savings rate, budget adherence and uncategorised share. A tap shows the breakdown of its components. It is per currency and hidden when there isn't enough data. Dismissing an insight doesn't change the score. The wording must never suggest a credit score.
6. **Digest link.** A notification tap on the daily summary opens Insights when there is at least one `ALERT` (wire this through the intent handling in `MainActivity.kt`).

**Acceptance:** results match Analytics and Period Review for the same period (same rows, refunds and transfers). A brand-new user sees an empty state, not wrong insights. Dismissals survive a restart and expire at the end of the period. Nothing reaches the network or the AI model.

---

## M2 — SMS import check-up

**Goal:** one screen that answers "why didn't my transaction show up?".

### Tasks
1. **Persist scan outcomes.** First define **mutually exclusive** per-message outcomes: saved, duplicate, excluded, unrecognised, non-financial or discarded, and special notification. Add the missing counters to `worker/smsscan/ProcessingStats.kt`, which today has no unrecognised, discard or special counters, so that examined = Σ outcomes. Write a scan record with state RUNNING → SUCCEEDED / FAILED / CANCELLED. Write RUNNING before the stats object exists, so a crash or process death shows as "interrupted" rather than success. The last successful scan is still recorded by `persistScanState` (`OptimizedSmsReaderWorker.kt:325`). Store the record as one JSON string in `ScanPreferences` (**DEVICE_ONLY**). Also record a "last live SMS received" timestamp in `SmsBroadcastReceiver.processIncomingSms` (:746). **Never** reuse or reset `LAST_SCAN_TIMESTAMP`, which is the incremental-scan cursor.
2. **Check-up screen.** New `presentation/settings/SmsCheckupScreen.kt` and its ViewModel. It reuses `utils/BackgroundAccess.kt` (`canReadSms`, `canReceiveSms`, `isBatteryUnrestricted`, `trackingProblem`), `BackgroundAccessViewModel` and the notification-listener state from `PermissionViewModel`. Sections:
   - **Permissions**, with fix buttons
   - **Background**, linking to the existing `BackgroundAccess` screen
   - **Last scan** (when, what was counted)
   - **Not imported**: the unrecognised count via `UnrecognizedSmsDao` (filter on `is_deleted`), linking to `UnrecognizedSms`
   - **Rescan** through `SmsScanManager`
3. **Entry points.** Put a top row in `SettingsTrackingPage.kt` (:75 and :117 already link to the two existing screens). Home already shows a `trackingProblem` banner (`HomeScreen.kt:141`). Retarget its action to the check-up screen and keep its snooze behaviour; don't add a second banner.

**Acceptance:** a cancelled or failed scan never shows as successful. The counts add up. Denying a permission shows a fix action, and fixing it updates the screen on resume.

---

## M3 — Emergency runway + all-goal affordability

**Goal:** answer two new questions: "how long would my money last?" and "can I afford all my goals at once?".

### Tasks
1. **Average spend and surplus.** Move the 3-previous-cycle income and expense averaging out of `HomeViewModel` (`previousCycleBreakdownMaps`, :189–197 and :723–760) into a new `domain/spending/CycleAverages.kt`. Home keeps the same output. It returns null, not zero, when there are fewer than 3 complete cycles (the `SpendForecast.of` rule).
2. **Runway.** New `domain/spending/Runway.kt`: liquid balance ÷ average monthly spending, per currency. Liquid means latest balances of `SAVINGS` / `CURRENT` / `CASH` accounts, excluding ignored ones and accounts in other profiles. **Do not** use `AccountBalanceDao.getTotalBalance()`, because it mixes in liabilities and currencies. Flag unanchored accounts (`BalanceCalculator.unanchoredAccounts`) as lower confidence. This becomes an Insights rule ("covers 2.4 months; 6 is a common target") plus a chip on the Insights summary.
3. **Goal affordability.** New `domain/goals/GoalAffordability.kt`: Σ `GoalRules.progress().perMonth` over active goals, per goal currency, compared with the average monthly surplus in the same currency. Leave out archived, reached and missed goals. Show it as a header on the Goals list (`presentation/goals/`) and as an Insights rule.

**Acceptance:** zero spending never shows "infinite" runway. Goals in a currency with no income show "no data", not "unaffordable". Home's figures don't change after the refactor in task 1 (snapshot test).

---

## M4 — Card bills + commitment-aware Safe-to-spend

**Goal:** Safe-to-spend sets aside money that is already committed before payday: card bills, every recurring or subscription occurrence in the window, and the goal savings the user opts into.

### Tasks
1. **Schema wave A** (migration 68→69, using the backup-maintainer skill). Add to `account_balances` / `AccountBalanceEntity`, all nullable with defaults:
   - `card_due_day INTEGER` (1–31)
   - `card_bill_amount TEXT` and `card_bill_as_of INTEGER`: the last statement amount the user entered
   - `anchor_amount TEXT`, `anchor_at INTEGER` and `anchor_source TEXT`: explicit balance-anchor provenance, used by M5. Today's manual rows get refreshed, so their timestamp can't prove when the user entered them.

   Preserve these fields through every account copy and rebuild path: `SmsTransactionProcessor`, `ScanTransactionWriter`, merges, and `deleteRebuildableBalances`. Legacy rows stay null, meaning "unknown"; never fabricate a value.
2. **Card-bill entry and lifecycle.** In `presentation/accounts/AccountDetailScreen.kt` and its ViewModel, credit cards get a "Bill" row with the due day and statement amount, using the existing `statementDay`. A statement day must never be treated as a due date. Automatic extraction from bill SMS is **out of scope**: it is a separate parser-core piece of work.
   - **No silent fallback.** `computeBilledUnbilled` (AccountDetailViewModel:237) adds up purchases across currencies and ignores repayments and refunds, so it must **not** feed reserves. With no entered amount, the bill is *unknown*: it reserves nothing and shows "incomplete" (task 5).
   - **Statement identity.** A bill is identified by (card, statement date), where the statement date comes from `statementDay` and the due date is the next `card_due_day` after it. Entering a new amount for a later statement replaces the old bill.
   - **Payment reconciliation.** Same-currency credits to the card after the statement date (payments and transfer-pair legs, not refunds of purchases) reduce the remaining amount. A bill is retired once the remaining amount is ≤ 0. Partial payments leave the rest reserved.
   - **Overdue.** A bill past its due date that still has an amount remaining **stays reserved**. Today's `billsInWindow` (SpendOutlook:128) drops overdue items, so overdue bills get their own path.
3. **Upcoming payments.** New `domain/spending/UpcomingPayments.kt`. It expands **every** occurrence in a window from active EXPENSE subscriptions (`SubscriptionRepository.advance`) and recurring templates (expose `nextDueDateAfter`, RecurringTransactionRepository:105), plus card bills from task 2.
   - **Matching.** Merchant alone (`RecurringCharge.sameMerchant`) can merge two different bills, so de-duplicate a subscription against a recurring template only when the merchant, currency, amount (within tolerance) and occurrence date all match.
   - **Settlement.** An occurrence counts as settled when a posted transaction matches it within a bounded date window around it. `canPayCycle` has no upper bound, so add one. Each posted payment settles **at most one** occurrence.
   - **Card overlap.** Subscriptions billed to a credit card are already inside that card's statement, so they are not reserved a second time. This case needs its own test. This replaces the next-occurrence-only `SafeToSpend.billsFrom` (the ponytail note at SpendOutlook:135). M6 reuses it.
4. **Safe-to-spend reserves.** Extend `SafeToSpend.calculate` and `HomeViewModel.safeToSpend` (:312–344) to work out spendable minus upcoming payments before the next cycle start (`BudgetCycle.nextCycleStart`) minus the opt-in goal reserve (remaining `perMonth`, pro-rated, using M3's goal scope rule), capped by `safeToSpendCap`. Add a BOOLEANS pref, "Reserve goal savings", which is off by default. Card bills reduce cash; card *purchases* are not subtracted again.
5. **Breakdown sheet.** Extend the `SafeToSpendCard.kt:170` sheet so each line shows what it reserves: balance, then minus each bill with its date, then minus the goal reserve, giving the result. Show an "incomplete" hint when a card has no due day.
6. **Card-bill insight** *(needs M1)*. Add a "<card> bill due in N days" rule to the M1 `InsightRules`.

**Acceptance:** the test set covers:
- weekly items that occur several times in the window
- month-end and leap-year due days
- payday being today
- bills that are overdue and unpaid, partly paid, or fully paid
- a subscription charged to a card
- two different subscriptions from the same merchant
- excluded accounts and both card-expense settings
- a missing FX rate
- an account merge or rescan that keeps the bill fields

The migration passes on `Api26_Migration`.

---

## M5 — Tracked net worth

**Goal:** one honest number per currency, explained line by line.

### Tasks
1. **Calculation.** New `domain/spending/NetWorth.kt`: per currency, liquid assets minus the **full** card outstanding (billed and unbilled) minus bank loans (`accountType = LOAN`). Sign the latest balances directly using `isLiability`, read from `getAllLatestBalances()`. Don't use `BalanceCalculator.signedBalanceEffect`, which signs transaction deltas, not balances. Person-to-person IOUs (`LoanEntity`) appear on a separate line and aren't counted by default. Goals are not assets. Each account carries a staleness flag (the age of its latest row).
2. **Provenance line.** For each account:
   - "Entered X on <date> + N transactions since", when `anchor_*` (wave A) exists.
   - "Reported by your bank on <date> + N since", when the latest anchoring row carries real stated-balance evidence (`statedBalance == true`, or `sourceType` CARD_LINK). The ingestion path doesn't currently emit `SMS_BALANCE`, so don't key on that value.
   - "Estimated from transactions", when the account is unanchored.

   Show no claim where there's no evidence.
3. **Screen and trend.** New `presentation/accounts/NetWorthScreen.kt` and its ViewModel. Show a summary card (assets − cards − loans), sectioned account lists, and a 1M / 3M / 6M / 1Y trend, rendered with `ui/components/BalanceChart.kt`. The trend has one series per currency, built by carrying each account's last balance forward per day. Seed each account with its latest balance **before** the range start (`getBalancesFromDate` omits it). Manual accounts overwrite their current row, so before wave A their history is a **gap**, never a flat line. Entry point: tapping the Home balance card, and a link from Manage Accounts.

**Acceptance:** cards and loans subtract. An unknown opening balance shows as "estimated". The trend has no line that crosses a currency change. A rescan that rebuilds derived rows doesn't crash the chart.

---

## M6 — Spending + upcoming calendar

**Goal:** one month grid of what you spent and what's coming.

### Tasks
1. **Data.** New `domain/spending/SpendingCalendar.kt`, which builds a map of day → (actual spend per currency, planned payments). Actuals use the Analytics row rules (`AnalyticsRows`). Planned payments come from M4's `UpcomingPayments`. Planned amounts never add to actual spend.
2. **UI.** New `presentation/calendar/SpendingCalendarScreen.kt` and its ViewModel, with a month grid that starts the week from `localFirstDayOfWeek` (`PeriodReviewLoader.kt:33`). Intensity is per selected currency, and planned days get a dot or outline marker. Tapping a day opens an agenda sheet: that day's transactions (reuse the `TransactionsWithFilter` NavKey) plus upcoming items, linking to the subscription or account. Entry point: Analytics, next to `SpendingHeatmap`.

**Acceptance:** month and week boundaries are right, RTL works, and a weekly item shows on every matching day. After a payment posts, the planned marker for that day disappears.

---

## M7 — Bounded budget rollover

**Goal:** an opt-in setting per LIMIT budget that carries the last period's under- or overspend into the current one.

**Rule: one-period carry, and it expires.** `carry(n) = clamp(base − spent(n−1), −base, +base)`. The carry comes only from the **previous** period, measured against the **base** limit, never the effective one. Any carry that isn't used expires after one period.

**Why this rule.** Codex's review showed that a replayed chain with a ±1-limit clamp never lets old carry go: with a 100 limit, spending 50 once and then 100 every period keeps a carry of 50 forever. Replaying only the last 12 periods silently resets it to zero. One-period expiry is well-defined, easy to explain ("last week's leftover"), and needs just one window lookback.

**No history table.** We keep Claude's choice, and Codex agrees. Windows are already resolved on read by the top-level `resolveBudgetWindow` (`BudgetWindow.kt:45`). Add a table only if users ask for multi-period accumulation.

### Tasks
1. **Schema wave B** (migration to 70 or the next free number, using the backup-maintainer skill). Add `rollover_enabled INTEGER NOT NULL DEFAULT 0` and `rollover_since INTEGER` (nullable epoch day) to `budgets` / `BudgetEntity`. The first period after `rollover_since` has no carry. Changing the currency or cadence, or `moveBudgetsToCurrency` (BudgetGroupRepository:53), resets `rollover_since`.
2. **Math.** New `domain/spending/BudgetRollover.kt`: `carry(baseLimit, previousSpent: Money?)` → `Money`. It is pure and unit-tested: the cap in both directions, zero limit, the first period after enabling, missed app openings, and anchored February windows.
   - **Currency.** Previous-period spending in another currency is converted at current rates, the same way the live budget does it. If any of it **can't** be converted, the carry is 0 and shows "incomplete". A missing rate must never look like underspending. Because the carry only looks back one period, rate drift stays small.
   - **Windows.** Measure the previous period on its **full cadence window** (resolved by `resolveBudgetWindow`), not the month-clipped `windowsForMonth` (BudgetWindow:126). Test a week that spans two months.
3. **Integration.** Create a single `effectiveLimit = base + carry` function and route **every** consumer of remaining-budget figures through it:
   - `BudgetGroupRepository.buildSummaryFromWindows` (:920, :1021, :1081)
   - `BudgetSummaryProvider` (:347)
   - `HomeBudgetSummary.kt:113`
   - `BudgetWidgetUpdateWorker.kt:158` and `BudgetWidgetData`
   - `BudgetPace` and `MoneyAlertRules`
   - `safeToSpendCap`
   - the M0 digest segment
   - the budget history screens

   Test the routing in unified-currency mode too. Leave legacy `BudgetRepository` alone unless something still reads it.
4. **UI.** Add a "Roll over unspent/overspent" toggle in `BudgetGroupEditScreen.kt`, available only for LIMIT budgets with weekly or monthly periods. Budget cards show "Base X + carried Y = Z". `BudgetHistoryScreen` shows the carry for each period.

**Acceptance:** every consumer agrees on the remaining amount, including Home, alerts, the widget and Insights. TARGET, EXPECTED and CUSTOM budgets can't enable rollover. A backup round-trip keeps the policy. `Api26_Migration` passes.

---

## M8 — Monthly/yearly PDF report

**Goal:** a shareable, archivable summary of a month or year.

### Tasks
1. **Exporter.** New `data/export/PdfReportExporter.kt`, using Android's `PdfDocument` and no new dependency. It renders a `PeriodReview` (from `PeriodReviewLoader`, so totals match the app) as:
   - a header with the period dates and profile
   - a separate section per currency with income, spent and net
   - a categories table with the change vs the previous period
   - top merchants
   - a month bar chart for yearly reports

   It paginates, uses `maskIfHidden = false`, and includes no SMS bodies, account numbers or receipts.
2. **Share.** Add an "Export PDF" action in `PeriodReviewScreen.kt` and `PeriodReviewViewModel.kt`. Write the file to the `exports/` FileProvider cache path and share it with `ACTION_SEND`, following the `ShareCardExporter` pattern (unique temp file, 24h purge). Free for everyone: no Pro gate.

**Acceptance:** PDF totals equal the Review screen. Arabic and RTL text render. A year with empty months still prints. Multi-page output paginates cleanly.

---

## M9 — PDF receipts, then warranty reminders

**Goal:** attach a PDF bill, and get reminded before a warranty ends.

### Tasks
1. **PDF receipts.** `ReceiptPickerSection` (`TransactionTabContent.kt:757`) accepts images only, so add PDF document selection and pending-preview handling there and in the detail screen. In `data/receipt/ReceiptManager.kt`, copy PDF bytes as they are (no Bitmap decode), check the MIME type and magic bytes, and enforce the ≤ 5 MB limit **while copying**, so the check isn't fooled by a reported size. `TransactionDetailReceipt.kt` shows a `PdfRenderer` first-page thumbnail and opens the file with a temporary FileProvider read grant. Keep **one attachment per transaction** (`receipt_path`), because multiple attachments would need a new table.
2. **Backup safety.** `data/backup/BackupReceipts.kt` currently Base64-encodes every receipt in memory (the known out-of-memory note). Before admitting PDFs, add per-file and total size limits on export, and on restore check the encoded length **before** Base64-decoding. Skip oversized files with a warning in the backup result. The wire format doesn't change.
3. **Schema wave C: warranty.** Add `warranty_until INTEGER` (nullable epoch day) to `transactions` / `TransactionEntity`, with a date picker in the transaction detail edit view. Add a new `worker/WarrantyReminderWorker.kt`, modelled on `BillReminderWorker` (KEEP periodic, its own channel), that notifies 30 days and 1 day before the warranty ends. Store a delivery watermark in DEVICE_ONLY prefs.

**Acceptance:** corrupt, encrypted or oversized PDFs are rejected cleanly. Path traversal is rejected (`safeReceiptFile`). Old backups and JPEG receipts restore unchanged. Denied notifications fail silently. `Api26_Migration` passes.

---

## Schema waves (keep migrations few)

| Wave | Milestone | Tables | Notes |
|---|---|---|---|
| A | M4 | `account_balances` | card due day, bill amount + as-of, balance anchor ×3 |
| B | M7 | `budgets` | `rollover_enabled`, `rollover_since` |
| C | M9 | `transactions` | `warranty_until` |

Version numbers are assigned in merge order, starting from 68. Rebase them when the work starts.

## Decisions (resolved)

1. **M1 entry point:** a Home card plus a pushed Insights screen. **No new bottom-nav tab.**
2. **M1 score:** ship the 0–100 indicator with its breakdown one tap away. Hide it when there isn't enough data.
3. **M3 and M4 goal scope:** goals have no `profile_id`, while balances and surplus are profile-scoped. Use the *all-profiles* surplus and balances for **both** goal affordability (M3) and the goal reserve (M4). Hide both figures while a single non-default profile is active.
4. **M4 payday:** use the existing budget cycle start day. No new payday setting.
5. **M4 goal reserve:** off by default.
6. **M7 carry policy:** a one-period carry that expires, capped at ±1 base limit.
7. **Pricing:** **every feature in this roadmap is free.** None of it is gated by `isProEntitled` or the paywall, and existing gates on other features stay as they are.

## UX principles (apply to every milestone)

- **Simple first.** One primary action per card. Put details behind a tap (expand or bottom sheet) and never front-load them.
- **No new settings unless they're unavoidable.** Use the recommended defaults. A toggle needs a reason a real user would flip it.
- **Plain language.** Write "You can spend 1,240 a day until the 25th", not jargon. Lead with the number, then the reason.
- **Honest emptiness.** With too little data, show a friendly empty state that says what unlocks the feature, never a guessed figure.
- **Reuse existing components and tokens** (`docs/design.md`), and match the surrounding screens. Avoid novel visual patterns.
- **Accessible by default.** Minimum 48dp touch targets, content descriptions, RTL, large fonts, light and dark themes.

## Risks
- **Card accounting:** a statement bill vs full outstanding, partial payments, overdue debt, and subscriptions already inside a card bill. M4 task 2 defines the lifecycle; don't start M4 without it.
- **Double counting:** card spend vs card bill, a recurring item vs a subscription, posted vs planned payments. The M4 tests must cover each pairing.
- **Thin history:** new users. Every rule needs a minimum-data guard that hides the result rather than guessing.
- **Provenance loss:** derived balance rows are rebuilt on rescan, so the wave-A fields must survive every rebuild path.
- **Consumer drift (M7):** remaining-budget maths is duplicated in about 5 places. Route all of them through one function, or the figures will disagree.

---

## Appendix — Kosh reference (behaviour only, GPL-3.0)

Root: `github.com/AIM-IT4/kosh`

| Idea | Kosh file |
|---|---|
| Insight rules, severities, routes | `core/finance/.../finance/Insights.kt` |
| Insight card UI | `core/designsystem/.../components/Insight.kt`, `InsightCharts.kt`, `app/.../ui/insights/InsightsScreen.kt` |
| Safe-to-spend with reserves | `app/.../repo/SafeToSpend.kt`, `core/finance/.../Payday.kt` |
| Budget rollover maths | `core/finance/.../BudgetMath.kt` |
| Goal affordability | `core/finance/.../Planning.kt` |
| SMS check-up | `app/.../ui/settings/SmsCheckupScreen.kt` |
| Daily digest text | `app/.../tools/Digest.kt` |
| Keypad | `core/designsystem/.../components/Keypad.kt` |
| Calendar, PDF reports | `app/.../ui/features/ReportsScreens.kt` |
| Receipts, warranties | `app/.../ui/features/TxnExtras.kt` |
| Net worth breakdown | `app/.../repo/InsightRepository.kt`, `ui/wealth/WealthScreen.kt` |
