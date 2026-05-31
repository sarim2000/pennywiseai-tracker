# Issue Tracker — Open Feature/Bug Backlog

Ordered **easiest → hardest** by implementation effort, not priority. Effort is a
rough estimate for this codebase (Compose + Room + Hilt + on-device LLM):
**S** ≈ localized change, **M** ≈ schema + a few screens, **L** ≈ new sub-feature,
**XL** ≈ new subsystem / risky migration.

_Snapshot: 2026-05-31 · 17 actionable + 1 blocked. Tier 1 (S) is empty; next-easiest items are in Tier 2._

---

## Tier 2 — Moderate (M)

| # | Issue | What it takes |
|---|-------|---------------|
| 1 | [#369 Bulk edit transactions](https://github.com/sarim2000/pennywiseai-tracker/issues/369) | Long-press → multi-select mode in the txn list + contextual action bar (delete / change category) + Snackbar undo. Building blocks already exist: shared `QuickCategoryPickerSheet`, per-row update + delete in `TransactionRepository`. Real work is the selection-mode UI state. |
| 2 | [#159 Save GPS coordinates on a transaction](https://github.com/sarim2000/pennywiseai-tracker/issues/159) | Location permission UX (runtime opt-in), new lat/lng columns, capture on manual add, surface on detail. Most of the work is the permission flow + opt-in toggle. |
| 3 | [#374 Sub-categories](https://github.com/sarim2000/pennywiseai-tracker/issues/374) | Category hierarchy: `parentId` on category, nested picker UI, rollups wherever categories are aggregated (reports/budgets). Schema + many touch points. |
| 4 | [#371 Income autopay](https://github.com/sarim2000/pennywiseai-tracker/issues/371) | Extend the autopay/subscription model to the income direction (recurring credits) plus the matching UI. |
| 5 | [#296 Interest accrual / periodic balance update](https://github.com/sarim2000/pennywiseai-tracker/issues/296) | Config (rate, period, compounding) + a scheduled job that nudges an account balance so it reconciles with daily-interest accounts. |

## Tier 3 — Large (L)

| # | Issue | What it takes |
|---|-------|---------------|
| 6 | [#385 Detect & merge self-transfers](https://github.com/sarim2000/pennywiseai-tracker/issues/385) | Heuristic matcher: same amount, debit/credit pair within a short window, account/name overlap → collapse two rows into one transfer event. Needs a confidence threshold + manual un-merge to keep false positives from silently corrupting data. |
| 7 | [#301 Planned spendings ("Plans" under Budgets)](https://github.com/sarim2000/pennywiseai-tracker/issues/301) | New entity: planned item (name, budget, category, target month, optional attached txn) + CRUD + monthly view. |
| 8 | [#170 Natural-language add-transaction via AI](https://github.com/sarim2000/pennywiseai-tracker/issues/170) | Reuse the on-device LLM to turn free text ("lunch 250") into a transaction draft. Prompt + structured-output mapping. |
| 9 | [#279 User-defined parsing rules (regex)](https://github.com/sarim2000/pennywiseai-tracker/issues/279) | Let users write regex rules to catch currently-unparsed SMS; rule storage, a management UI, and wiring into the parse pipeline as a fallback. |
| 10 | [#346 Split a transaction with contacts + UPI link/QR](https://github.com/sarim2000/pennywiseai-tracker/issues/346) | Split-the-bill flow on the detail screen: contact picker, per-person owed amounts, UPI deep link + QR generation, share via SMS. |
| 11 | [#343 Separate loans per person](https://github.com/sarim2000/pennywiseai-tracker/issues/343) | Per-person loan ledgers (lent/borrowed, running balance, settle-up). New data model + screens. |

## Tier 4 — Major (XL)

| # | Issue | What it takes |
|---|-------|---------------|
| 12 | [#368 Account merge](https://github.com/sarim2000/pennywiseai-tracker/issues/368) | Merge two accounts: reassign all transactions/balances, dedup, and a safe (ideally reversible) migration. High blast radius. |
| 13 | [#299 CSV import of historical transactions](https://github.com/sarim2000/pennywiseai-tracker/issues/299) | File picker + flexible column-mapping UI (varies per source app) + dedup against existing txns. |
| 14 | [#351 Google Drive (or any) auto backup](https://github.com/sarim2000/pennywiseai-tracker/issues/351) | Drive/SAF auth, encrypted export/import, scheduling, and a tested restore path. |
| 15 | [#135 Track closing balance & flag discrepancy](https://github.com/sarim2000/pennywiseai-tracker/issues/135) | Reconciliation logic comparing computed balance vs reported/closing balance, surfacing drift. |
| 16 | [#156 Email processing (instead of / alongside SMS)](https://github.com/sarim2000/pennywiseai-tracker/issues/156) | Entirely new ingestion channel: email access/permissions, parsers for email bodies, dedup with SMS. |
| 17 | [#13 Bank statement reconciliation](https://github.com/sarim2000/pennywiseai-tracker/issues/13) | Statement (PDF/CSV) parsing + reconcile against tracked transactions. Hardest — new parsing surface + matching engine. |

---

## Blocked / not actionable

| # | Issue | Why |
|---|-------|-----|
| — | [#305 RBC, Royal Bank](https://github.com/sarim2000/pennywiseai-tracker/issues/305) | Reporter sees **no SMS from any bank** — a device-side read/permission problem (Samsung secure folder), not a missing parser. Needs support triage before any RBC parser would help. |

---

## Recently closed

| Closed | # | Title | Via |
|--------|---|-------|-----|
| 2026-05-31 | #303 | Quick-pick category from txn-alert notification | #393 |
| 2026-05-31 | #384 | Home-screen cash-flow card | #392 |
| 2026-05-31 | #386 | Backup loans/groups round-trip + databaseVersion de-stale | #391 |
| 2026-05-31 | #372 | Edit auto-pay | closed externally |
| 2026-05-31 | #383 | Show description over merchant on transaction rows | #389 |
| 2026-05-31 | #382 | Bank-account widget showed limit instead of outstanding | #388 |
| 2026-05-31 | #367 | Edit & duplicate smart rule | #380 |
| 2026-05-31 | #373 | Smart rule: set "account" field | #380 |
| 2026-05-31 | #370 | Transaction template / copy | #379 |
| 2026-05-29 | #376 | SABB salary credits not captured | #377 |
| 2026-05-29 | #354 | Standard Bank Mozambique parser | #378 |
| 2026-05-29 | #360 | Kotak RCS transaction alerts | #375 |
| 2026-05-29 | #331 | Commercial Bank Qatar | blocked → closed (sender ID never provided) |
