# Webhooks

Webhook creation, configuration, manual sync, synthetic tests, and automatic
delivery require Pro. F-Droid builds have this access unlocked. Free users can
see the Settings entry and upgrade prompt. Scheduling requires Pro. Existing
schedule timing is retained while entitlement loads and the initial Play check
is pending. Once initialization finishes without Pro, the periodic job is cancelled.
Failed Play checks retain cached access, following the app's existing offline policy.
Queued work cannot send data
without entitlement.

Open Settings → Developer → Webhooks to send selected financial data to an
endpoint you control. This is an opt-in exception to PennyWise's on-device data
processing. Remote endpoints require HTTPS. HTTP is supported only for
`localhost` and `127.0.0.1`, for local receivers.

Create a profile with a name, URL, currency, selected data types, date range,
and optional authentication headers. Header values are hidden in the editor;
profile cards show only the endpoint host. Enable automatic delivery per
profile. The sync interval is shared by enabled profiles and can be set from
1 to 24 hours. Android can delay background delivery to save battery, and
jobs wait for a network connection. Disabling every profile cancels periodic
work. Disabled profiles can still receive an explicit synthetic test.

Use Test to send synthetic data for the selected types with the saved headers.
Tests do not read financial repositories or advance sync cursors. Use Sync to
send real data for an enabled profile. Delivery history shows HTTP status,
failures, timestamps, and the source of each attempt. History retains the latest
100 delivery records across all profiles, with up to 50 displayed per profile.

## Receiver contract

Requests are JSON POSTs using schema version `1.0`. Monetary amounts are decimal
strings, tagged with currency. A profile exports only its selected currency.
The envelope contains `schema_version`, `generated_at`, `app`, `profile`,
`request`, `batch`, and selected `summary`, `transactions`, `budgets`, `accounts`,
and `subscriptions` sections. Each batch contains at most 250 records per
transaction, budget, account, or subscription section. Requests larger than
1 MiB are split further by their encoded UTF-8 size. If one record or the
envelope metadata alone exceeds 1 MiB, delivery fails locally without
retrying or advancing cursors.
All batches in a run share `batch.id`, with one-based `batch.index` and
`batch.count`. Summary appears only in the first batch. For selected budget,
account, and subscription snapshots, accumulate each section across every
batch of a run and replace the stored snapshot only once all batches arrive.
Deduplicate repeated batches by their ID and index. An empty selected section
across a completed run clears that snapshot; an incomplete run must not replace it.

For transaction upserts, key records by `profile.id` and transaction `id`.
For deletes, remove that record. A deletion contains only its stable ID,
`action: "delete"`, and `updated_at`. Receivers must be idempotent: retrying a
partially completed run can deliver a transaction more than once, and an
inclusive cursor boundary can repeat a row. Retry runs can have a new batch ID.
Do not use batch IDs as the sole key for transaction deduplication.

The app retains IDs from successful transaction batches, including batches in a
partially failed run. If a delivered transaction changes currency, its former
currency profile receives a deletion containing no financial details from the
new currency. Transactions that were never delivered to that profile are not
included in these removals. Changing a profile's currency at the same endpoint
retains delivery tracking so old-currency rows can be removed. Changing the
endpoint clears tracking; reconcile the former receiver separately.

Any HTTP 2xx status is success; the response body has no required shape.
Network failures, HTTP 429, and server errors are retried up to three times
per delivery, followed by at most three WorkManager retries. Other HTTP errors
are recorded without immediate retry. Automatic sync will try again at the
next interval. Cursors advance only after every batch succeeds. Tests never
advance cursors.

Generic 302/303 redirects are failures and never acknowledge delivery through
a bodyless GET. Google Apps Script is the narrow exception: an HTTPS deployed
`script.google.com/macros/s/<deployment>/exec` POST may redirect to the HTTPS
`script.googleusercontent.com/macros/echo` response URL via GET. This follows
the [documented Content Service response flow](https://developers.google.com/apps-script/guides/content#redirects).
Login redirects and further GET redirects cannot acknowledge delivery.
301/307/308 preserve the POST body only within the same origin. Custom headers
are stripped from cross-origin redirects. HTTPS cannot redirect to HTTP, and
redirect chains are limited to five hops. Delivery error messages do not
include URLs, response bodies, or header values.

## Data semantics

- Since last success exports transactions by modification time, including edits
  to old transactions and soft-delete tombstones. The first run exports all
  retained transactions in the selected currency.
- If the device clock moves behind a saved cursor, the next run replays retained
  records through the new current time. Only after all batches succeed does it
  reset the cursor to that time, so edits made after the clock change are included.
- Other ranges export non-deleted transactions by their exact transaction
  timestamps. Changing the endpoint, currency, or range resets incremental
  cursors so the new configuration can receive its own initial export.
- Summary income and expense respect analytics exclusions and the credit-card
  expense preference. Loan-linked transactions are excluded from totals.
  Expense category amounts use transaction splits.
  Transfers and investments are excluded from the expense summary.
- Budgets use current active budget windows and PennyWise's existing spending
  calculation, including currency, category, split, and refund rules.
- Accounts and subscriptions are current snapshots, independent of the
  transaction range. Receivers should replace their selected snapshot sections
  after a complete run, even when those sections contain no records.

Webhook profiles and header credentials are stored in the app's local database
and are not included in PennyWise's JSON backups. Recreate them after a restore.
Hard deletion, delete-all, and SMS reparsing do not retain tombstones for removed
rows; reconcile the receiver separately after those operations. Daily clock-time
schedules are not provided; scheduling uses WorkManager intervals.
