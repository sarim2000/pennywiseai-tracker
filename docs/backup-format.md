# Backup & Restore Format

PennyWise exports the full local database + user preferences to a single JSON
file and can restore it later, on the same or a newer app version. This
document is the **contract** for that format: how it stays compatible across
versions, and the rules you must follow when you change anything it touches.

> TL;DR: every backup-serialized field **must have a Kotlin default value**.
> That single rule is what makes old backups restorable forever. A CI test
> (`BackupSchemaGuardTest`) enforces it twice: once for the wrapper models, and
> once against a pinned required-fields baseline for every Room entity — so an
> entity that gains a new non-defaulted column fails CI too.

## Where it lives

| File | Role |
|------|------|
| `data/backup/BackupModels.kt` | The wrapper schema (`PennyWiseBackup`, `DatabaseSnapshot`, `PreferencesSnapshot`, …). All `@Serializable`, all fields defaulted. |
| `data/backup/BackupSerializers.kt` | The `backupJson` instance + custom serializers for `BigDecimal` / `LocalDate` / `LocalDateTime`. The one source of truth for serialization behavior. |
| `data/backup/BackupExporter.kt` | Reads the DB → builds `PennyWiseBackup` → `backupJson.encodeToString`. |
| `data/backup/BackupImporter.kt` | `backupJson.decodeFromString` → inserts into the DB (MERGE or REPLACE_ALL), remapping foreign keys and skipping bad rows. |
| `data/backup/BackupReceipts.kt` | Receipt photos: collect (export), path-safety check + restore (import). |
| `data/database/entity/*.kt` | The Room entities are serialized **directly** (each is `@Serializable`). |
| `test/.../backup/BackupModelsTest.kt` | Round-trip + backward/forward compatibility regression tests. |
| `test/.../backup/BackupSchemaGuardTest.kt` | CI guard: every wrapper-model field has a default. |

## The compatibility contract

The backup is (de)serialized with **kotlinx.serialization**, configured in
`BackupSerializers.kt` to be tolerant in both directions:

- **Backward compatible** (old backup → newer app): a key the older app didn't
  write is simply **missing**. Because every field has a Kotlin default,
  kotlinx fills the default instead of throwing. `coerceInputValues = true`
  also turns a present-but-`null` value on a defaulted field into the default.
- **Forward compatible** (new backup → older app): a key the newer app added is
  **unknown** to the older app. `ignoreUnknownKeys = true` drops it silently.

This is why the historic bug (issue #414) happened and why it's now fixed:
the old code used **Gson**, which constructs objects with
`Unsafe.allocateInstance` and **ignores Kotlin default values entirely**. An
older backup missing a newer non-null column (e.g. `subscription.billingCycle`,
`subscription.direction`) deserialized that field as `null` under a non-null
type, and the insert then crashed — aborting the whole restore. kotlinx honors
the defaults, so the same backup now restores cleanly.

### Wire-format details (do not change without a version bump)

The custom serializers emit **byte-identical** output to the legacy Gson
adapters, so backups remain readable across the Gson→kotlinx switch in both
directions:

| Type | JSON representation |
|------|---------------------|
| `BigDecimal` | plain decimal string, `toPlainString()` (e.g. `"499.00"`) |
| `LocalDateTime` | `ISO_LOCAL_DATE_TIME` string (e.g. `"2024-01-02T10:15:30"`) |
| `LocalDate` | `ISO_LOCAL_DATE` string (e.g. `"2024-01-02"`) |
| enums | the constant **name** (e.g. `"EXPENSE"`) |
| entity field keys | the Kotlin **property name** (camelCase, e.g. `merchantName`) |
| wrapper-model keys | explicit `@SerialName` (snake_case, e.g. `merchant_mappings`) |

Format string lives in `PennyWiseBackup.CURRENT_FORMAT` (`"PennyWise Backup v1.3"`).
Imports accept any `PennyWiseBackup.COMPATIBLE_PREFIX` (`"PennyWise Backup v1"`).

## Receipt photos (v1.3, #839)

Receipt images are embedded in the same JSON file, so there is still exactly
one file format and every v1.x app can read it (older apps ignore the key):

```json
{
  "_format": "PennyWise Backup v1.3",
  "metadata": { "statistics": { "total_receipts": 1 } },
  "database": { "transactions": [ { "receiptPath": "receipts/receipt_1727.jpg" } ] },
  "receipts": [
    { "path": "receipts/receipt_1727.jpg", "data_base64": "/9j/4AAQ..." }
  ]
}
```

- `path` is the transaction's `receiptPath` (relative to `filesDir`);
  `data_base64` is the file's bytes, standard Base64. Both defaulted to `""`.
- **Export** includes receipts only when the caller opts in and only for `FULL`
  exports. Settings has one toggle per path: *Include receipts* for
  manual export (pref `export_include_receipts`, default **on**) and for
  Automatic Folder Backup (pref `folder_backup_include_receipts`, default
  **off**: it runs daily). Missing/unreadable files are skipped and logged.
  A receipt is a JPEG photo or a PDF (`receipts/receipt_<ms>_<n>.pdf`, copied as
  is). Photos always go in, with no size cap, as before PDFs. A PDF over 5 MB
  (`ReceiptFiles.MAX_RECEIPT_BYTES`), or one that would take the backup past
  50 MB of PDFs (`BackupReceipts.MAX_TOTAL_BYTES`), is left out, and the export
  message (or, for the daily folder backup, its "Last backup" line) says how many.
- **Import** treats `path` as untrusted: only `receipts/<name>` that resolves
  (canonically) directly inside `filesDir/receipts` is written. Absolute paths,
  `..`, nested dirs and backslashes are rejected. A receipt is written only if a
  transaction in the DB after the import points at that path (so a merge
  restores exactly the photos of the rows it kept), and an existing file is
  never overwritten. Runs after the DB transaction commits. A PDF over 5 MB is
  skipped: by its `data_base64` length before decoding, then by its decoded
  size. The import message counts it only when no file is already at that path
  (a merge onto the same phone keeps the copy it has). Photos restore at any size.
- A transaction whose `receiptPath` file is missing shows "Receipt photo not
  included in this backup" on its detail screen.

## Savings goals (#914)

Two tables, both in `DatabaseSnapshot` with defaulted lists, plus defaulted
`total_goals` / `total_goal_contributions` counters. Older backups lack the
keys and decode to empty lists; older apps ignore them.

```json
"goals": [ { "id": 4, "uid": "…", "name": "Insurance", "targetAmount": "24000.00",
             "currency": "INR", "dueDate": "2027-03-31", "alreadySaved": "5000", … } ],
"goal_contributions": [ { "id": 8, "uid": "…", "goalId": 4, "amount": "-41.50",
             "originalAmount": "-0.50", "originalCurrency": "USD", "transactionId": 3, … } ]
```

- `amount` is signed, in the goal's currency (negative = Withdrawal).
  `originalAmount`/`originalCurrency` keep the pre-conversion amount; for a
  transaction-linked row it is the share of that transaction.
- **Export:** `FULL` only, like loans and recurring templates (names/notes are
  user text; contributions point at transaction ids).
- **REPLACE_ALL:** goals are cleared (cascading contributions) and re-inserted
  with their ids, goals before contributions. A contribution whose
  `transactionId` didn't restore keeps its money but loses the link.
- **MERGE:** goals dedup on `uid` (a local goal with the same uid wins);
  contributions dedup on their `uid`, so a repeat merge is idempotent while
  contributions made on another device still arrive. `goalId` is remapped to
  the local goal; `transactionId` goes through the same old→local map as tag
  cross-refs (new rows and hash duplicates), and an unmapped link is cleared.
  Each touched transaction's linked shares are then capped to its amount.
  If a uid-matched local goal now has a different currency (an empty goal can
  change currency), that goal's backup contributions are skipped and counted —
  amounts are never converted implicitly.

## Split bills (#346)

One table, `loan_shares`, in `DatabaseSnapshot` as a defaulted list, plus a
defaulted `total_loan_shares` counter, the nullable `lentSharesTotal` on each
transaction, and `preferences.app.upi_id`. Older backups lack the keys and
decode to no shares; older apps ignore them.

```json
"loan_shares": [ { "id": 7, "transactionId": 3, "loanId": 5, "amount": "333.33", "createdAt": "…" } ]
```

- A share is one person's part of a bill: money owed to you on loan `loanId`,
  in the bill's currency. `lentSharesTotal` is the sum of a bill's shares.
- **Export:** `FULL` only, like loans. `upi_id` too.
- **Export** includes only shares whose bill is exported. A soft-deleted
  bill isn't exported, and its debt stays on the loan's principal, as it does
  for a loan-linked bill.
- **Import (both strategies):** every transaction is inserted with
  `lentSharesTotal = null`. A share is restored only onto a loan that this
  import inserted, because that loan's principal was sized with the share.
  Its bill can be new or an existing one with the same hash, but the bill
  must still be an unlinked expense in the loan's currency, have room left in
  its amount, not already be split with that person, and be under the
  five-person limit. Any other share is skipped and counted. Loans are never
  changed on import, so a skipped share's debt stays on its restored loan
  and a restore can't erase money someone owes. Each touched bill's total is
  rebuilt from its rows afterwards.
- **MERGE:** a matched local loan wins as it is, like a local goal with the
  same uid. That makes a repeat merge a no-op, and the unique
  `(transaction_id, loan_id)` index backs this up.
- `upi_id` fills in a missing one and never replaces one set on this device.

## Reimbursable expenses (#1389)

Two nullable columns on each transaction, no new table. Older backups lack
the keys and restore as not reimbursable; older apps ignore them.

```json
"transactions": [ { "id": 3, "reimbursementStatus": "REIMBURSED", "reimbursedById": 9, … } ]
```

- `reimbursementStatus` is `PENDING`, `REIMBURSED` or absent (not
  reimbursable). An unknown name decodes to absent rather than failing the file.
- `reimbursedById` is the id of the incoming payment that paid it back, or
  absent when the user marked it reimbursed without one. Several expenses can
  point at one payment.
- **Import (both strategies):** every transaction is inserted without the
  link, then each row this import inserted is pointed at its payment through
  the old→local id map once all rows are in (`reimbursementLinks`). A payment
  that didn't restore leaves the row reimbursed, unlinked. In a MERGE the
  payment may be a local hash duplicate; a matched local expense keeps its own
  state.

## Resilience

Beyond schema tolerance, the importer skips and **counts** any single row that
fails to insert (`insertEachCounting`) instead of letting it abort the whole
restore. This is applied to **every** entity insert loop in both `MERGE` and
`REPLACE_ALL` (and the merge helpers); batch snapshot inserts are wrapped in a
table-level `try/catch`. The skipped count surfaces to the user ("N rows could
not be imported"). This guards against genuinely corrupt rows or constraint
violations — schema drift is already handled upstream by the defaults.

The one deliberate exception is `importProfilesAndBuildMap`: its inserts build
the profile id-remap map that transactions/balances depend on, so it stays
strict (a profile failure should surface, not be silently swallowed).

## Encrypted backups

*Export Data (Encrypted)* (Settings → Data) wraps the same JSON in a
passphrase envelope (`data/backup/BackupCrypto.kt`). The file keeps the
`.pennywisebackup` extension; the JSON inside is unchanged, so everything
above still applies once it's decrypted. That passphrase is never stored.

| Bytes | Field |
|-------|-------|
| 5 | magic `PWENC` (ASCII) |
| 1 | envelope version, `1` |
| 1 | KDF id, `1` = PBKDF2WithHmacSHA256, 256-bit key |
| 4 | iterations, big-endian int (310 000 when written; readers accept 100 000–2 000 000 and reject the rest before deriving) |
| 16 | random salt |
| 8 | random nonce prefix |

Then frames until the one marked last:

| Bytes | Field |
|-------|-------|
| 1 | `isLast`: `1` on the final frame, else `0` |
| 4 | ciphertext length, big-endian int (16 to 64 KiB + 16) |
| n | AES/GCM/NoPadding ciphertext + 128-bit tag of up to 64 KiB plaintext |

- Nonce = nonce prefix ‖ frame index (4 bytes, big-endian, from 0).
- AAD = the 35 header bytes ‖ frame index ‖ `isLast`. A changed header,
  reordered or dropped frames, a moved `isLast` flag, a missing last frame or
  bytes after it all fail.
- The writer always ends with a frame marked last (empty for an empty input).
- **Export** streams the JSON straight into the encryptor, frame by frame, so
  no plain copy is written. A write that fails leaves no last frame.
- **Import** sniffs the first 5 bytes. A plain backup takes the usual path,
  unless a passphrase was given: then the file must be encrypted. An encrypted
  one asks for the passphrase, is decrypted in full to a temp file in
  `cacheDir/backup_restore/` and parsed **before the database is touched**, so a
  wrong passphrase (the first frame fails) or a damaged file changes nothing.
  The temp file is deleted whatever happens, and that folder is swept at the
  start of every import and export (`DecryptedBackupFiles`). A decrypted backup
  that won't parse gives a fixed error: parser messages quote the plaintext.
- A new KDF or cipher needs a new envelope version; readers reject versions
  they don't know.
- **Automatic folder backups** (*Encrypt Automatic Backups*) use the same
  envelope. The switch and the passphrase are kept on the device in their
  own Preferences DataStore (`data/backup/FolderBackupPassphrase.kt`), which
  no backup file or Android backup carries; the passphrase is sealed with an
  Android Keystore key under a fresh alias each time it's entered, stored
  beside it. A backup is written plain only when that store reads "off": a
  read error, or a passphrase the Keystore can't unlock (opening never
  creates a key), writes nothing, the worker retries later, and Settings
  asks for the passphrase again. Nothing is marked on disk when an unlock
  fails. The switch can be turned on before automatic backups, and turning
  them on waits for a passphrase still being saved, so the first file is
  already encrypted. One lock in `worker/FolderBackupRunner.kt` keeps backups
  and encryption changes from overlapping. Restore them like any encrypted
  backup.

## How to change the format safely

### Adding a column to an existing entity
1. Add the column to the entity **with a Kotlin default** and a Room
   `defaultValue` (Room migration as usual). The default is mandatory — without
   it, older backups that lack the key become un-importable.
   ```kotlin
   @ColumnInfo(name = "new_flag", defaultValue = "0")
   val newFlag: Boolean = false   // <-- the default is load-bearing
   ```
2. If the field type is `BigDecimal` / `LocalDate` / `LocalDateTime`, mark it
   `@Contextual`. If it's an enum, make the enum `@Serializable`.
3. Done — no importer change needed. Add an assertion to the round-trip test if
   it carries meaningful data.

### Adding a whole new entity / table to the backup
1. Make the entity `@Serializable` (+ `@Contextual` on special-typed fields,
   `@Serializable` on its enums).
2. Add a `List<NewEntity> = emptyList()` field to `DatabaseSnapshot` (with a
   `@SerialName`).
3. Populate it in `BackupExporter` and insert it in `BackupImporter`
   (mind foreign-key ordering — parents before children).
4. Add a stat counter to `BackupStatistics` (defaulted to `0`).

### Adding a field to a wrapper model
Always give it a default. `BackupSchemaGuardTest` fails the build otherwise.

### Things you must NOT do
- ❌ Remove a default from any backup-serialized field.
- ❌ Rename an entity property without keeping the old JSON key (a rename
  silently drops that data from older backups — they still carry the old key).
  If you must rename, add `@SerialName("oldKey")` to preserve it.
- ❌ Change a `BigDecimal`/date/enum's wire representation.
- ❌ Add a non-defaulted field to a wrapper model.

## Testing

`BackupModelsTest` covers round-trip, the loans/groups regression (#386), the
#414 backward-compat regression (`oldBackupMissingNewerFields_*`), forward-compat
(`newerBackupWithUnknownKeys_isIgnored`), missing sections, null coercion, and
savings goals (`oldBackupMissingGoals_*`, `goals_roundTrip*`).

`BackupSchemaGuardTest` enforces the compatibility invariant on two layers:
- **wrapper models** — every field must have a default; and
- **entities** — the set of *required* (non-defaulted) fields is pinned to a
  baseline (`KNOWN_REQUIRED_ENTITY_FIELDS`). Adding a new non-defaulted field to
  any backed-up entity — the exact #414 footgun — grows that set and fails the
  build, telling you to add a default (or, rarely, update the baseline for a
  field guaranteed present in every existing backup).

Run them:
```bash
./gradlew :app:testStandardDebugUnitTest --tests "com.pennywiseai.tracker.data.backup.*"
```

When you touch the backup format, **add a regression test that decodes an
older-shaped JSON fixture** (a raw string missing your new key) and asserts the
default is applied — that's the only thing that protects real users' old files.

## Taught message templates (#279)

`preferences.app.taught_templates` is a defaulted list of `TaughtTemplate` (FULL
exports only: its text names the bank and whatever a message always says). Fields
and enums travel by name. A template this version can't fully understand stays in
the list, and in the next export, but reads nothing. Restore is additive by `id`.

## Settings (`preferences.settings`)

Every portable setting travels in one defaulted, nullable `SettingsSnapshot`,
keyed by its on-device DataStore name in typed maps (`booleans`, `ints`,
`longs`, `strings`, `string_sets`), plus a few settings kept elsewhere:
`webhook_interval_hours`, `app_language` (BCP 47 tags, `""` = follow the
device; Android 13+), and FULL-only `tracked_notification_apps` and
`learned_senders`.

```json
"settings": {
  "booleans": { "is_amoled_mode": true, "dark_theme_enabled": null },
  "strings": { "theme_style": "BRANDED", "user_name": "…" },
  "string_sets": { "hidden_nav_tabs": ["chat"] },
  "app_language": "ar"
}
```

- **Which keys:** `UserPreferencesRepository.SettingsBackup`. Every key in
  `PreferencesKeys` is in exactly one bucket: a typed allowlist (backed up),
  `IN_LEGACY_SECTIONS` (still carried by `theme`/`sms`/`developer`/`app` with
  their own merge rules), or `DEVICE_ONLY` (nudge/"seen" flags, delivery
  watermarks, caches, one-shot migrations, App Lock, onboarding, the folder
  backup's SAF grant). `SettingsBackupTest` fails on a new unclassified key.
- **Privacy:** `SettingsBackup.FULL_ONLY` (name, AI prompt, account keys,
  statement mappings) is left out of MASKED/ANONYMOUS exports. A profile
  photo is a file on the old phone, so only a built-in `avatar://` choice
  travels.
- **Restore:** applied after the older sections, so the backup's value wins.
  A present key is written, a present `null` resets it to the default, a
  missing key is left alone (so a masked export never wipes the FULL-only
  ones). Only allowlisted keys of the right type are read. `selected_profile_id`
  goes through the import's profile-id map (unmapped = all profiles). Settings
  then re-syncs the daily/weekly recap workers and redraws the widgets, and the
  app language is applied last.
- **Not backed up:** webhook profiles, logs and cursors (headers can hold
  credentials, and a restore would start posting data), AI relay pairing,
  voice-input language/consent, GPU fallback, widget caches.
