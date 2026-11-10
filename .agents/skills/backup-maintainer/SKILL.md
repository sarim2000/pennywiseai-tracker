---
name: backup-maintainer
description: Keep stored data and backup compatibility intact through any persistence change. Use when adding, changing or removing a Room entity column or table, touching BackupModels.kt or anything in data/backup/, renaming or removing a serialized field or enum value, or bumping SCHEMA_VERSION.
---

# Backup maintainer

Goal: every user can update from any older version without losing rows, and every
backup ever written still restores, in MERGE and REPLACE_ALL. Kotlin defaults alone
don't get you there. Most breakage comes from a table the exporter forgets, an
importer that doesn't remap an id, or a renamed key. The rules live in
`docs/backup-format.md` and `docs/database-migrations.md`; read both first. Paths
below are under `app/src/main/java/com/pennywiseai/tracker/data/`.

Classify the change, then work through every branch that applies.

## (a) New column on an existing entity

- Give it a Kotlin default **and** a matching `@ColumnInfo(defaultValue = ...)`:
  - The Kotlin default fills the field when an older backup lacks the key
    (`BackupSchemaGuardTest` checks it).
  - The SQL default fills existing rows on upgrade, and SQLite rejects
    `ADD COLUMN … NOT NULL` without one. Room's `@Insert` writes every column, so
    app inserts and restores never use it. Keep the two values equal.
  - A nullable column uses `= null` with `defaultValue = "NULL"` (see
    `TransactionEntity.transferPairId`). The migration's `DEFAULT` must match the
    exported schema exactly, or Room fails with "Migration didn't properly handle".
- Mark `BigDecimal`/`LocalDate`/`LocalDateTime` fields `@Contextual`. Make enums
  `@Serializable` and give them a converter in `database/converter/Converters.kt`.
- **Export:** entities serialize as-is, so the column ships. If it holds user text or
  an account identifier, mask it in the `MASKED`/`ANONYMOUS` branches of
  `BackupExporter.createBackup`, or export it on `FULL` only.
- **Import:** if the column holds another row's id, remap it in every `copy(...)` in
  `BackupImporter`. MERGE inserts with `id = 0`; REPLACE_ALL keeps ids but still drops
  links to rows that didn't restore. Reuse `cleanTransaction`, `resolveProfileId`,
  `oldToNewLoanIdMap`, `oldToNewTransactionIdMap` and `categoryIdMap`.
- Reset a value derived from other rows on insert and rebuild it afterwards (see
  `lentSharesTotal`). Never build a MERGE dedup key from a mutable field.

## (b) New table / entity

Decide first whether it belongs in the backup; the `Webhook*` entities deliberately
don't. If it does, do all of these:
1. Make the entity `@Serializable` with fields as in (a), plus a `uid` column for
   MERGE dedup (see `GoalEntity`).
2. Add `@SerialName("snake_case") val x: List<X> = emptyList()` to `DatabaseSnapshot`.
3. Add a `total_x` counter (`= 0`) to `BackupStatistics` and fill it in
   `BackupExporter.createBackup`.
4. **Exporter:** read the table from its DAO, gate it on `ExportPrivacy` (relational
   and user-text tables are `FULL` only) and pass it to `DatabaseSnapshot`.
5. **REPLACE_ALL** (`replaceAllData`): clear the table in the delete block, or confirm
   a cascade does. Insert parents before children, each through `insertEachCounting`.
6. **MERGE** (`mergeData`): dedup on `uid` or an immutable key so a repeat merge
   inserts nothing. Insert with `id = 0` and keep an old→new id map. Remap child
   foreign keys: null an unmapped optional link, and skip and count a row whose
   required parent didn't map.
7. Add the entity's descriptor to `entityDescriptors` in `BackupSchemaGuardTest`. A
   brand-new table's required fields may join `KNOWN_REQUIRED_ENTITY_FIELDS`, since
   every backup that has the table has them, but prefer defaults.

## (c) Backup wrapper model (`backup/BackupModels.kt`)

- Give every field a default and an explicit snake_case `@SerialName`. Add each new
  class to `backupModelDescriptors` in `BackupSchemaGuardTest`.
- Write a preference in `BackupExporter` and apply it in
  `BackupImporter.importPreferences`. Default it to null ("unset") so an old backup
  leaves the current value alone (see `hiddenNavTabs`). Values that identify a person
  are `FULL` only (see `upiId`, `ignoredAccounts`).
- Imports reject any `_format` not starting with `PennyWiseBackup.COMPATIBLE_PREFIX`.
  Never move to `v2`.

## (d) Wire-format change: rename, removal or type change

- **Property rename:** an entity's JSON key is its Kotlin property name, not its
  `@ColumnInfo` name. After a rename, `ignoreUnknownKeys` drops the old key and the
  field silently takes its default: data lost, no error. Put `@SerialName("oldName")`
  on the renamed property so older and newer apps both read it. Renaming only the
  `@ColumnInfo` changes the DB, not the backup, and needs a migration.
- **Enum constants** are stored by `name` in the DB and the backup. `Converters` uses
  `valueOf`, so an unknown name crashes reading that row. Never rename or delete a
  shipped constant; deprecate it. In a backup, `coerceInputValues` turns an unknown
  name into the field's default only if the field has one. Otherwise the whole file
  fails to decode before any insert, and `insertEachCounting` can't help. A new
  constant is safe for new apps, but older apps reading new backups hit the same rule.
  Flag it if the field is in `KNOWN_REQUIRED_ENTITY_FIELDS`.
- **Type change** (`Long` → `BigDecimal`, `String` → enum): old JSON must still
  decode. Add a new key and migrate from the old one. Never change the representations
  in `backup/BackupSerializers.kt`.
- **Removing a field:** old keys are ignored, which is fine, but an older app restoring
  a new backup gets the default. Ask whether that is harmless.

## Migration (any schema change)

1. Bump `SCHEMA_VERSION` in `database/PennyWiseDatabase.kt`; add a new entity to
   `entities = [...]`.
2. A purely additive change gets `AutoMigration(from = N, to = N + 1)`. Anything else
   gets a `MIGRATION_N_N+1` in `PennyWiseMigrations.kt`, registered in `ALL_MIGRATIONS`
   and nowhere else (`MigrationRegistryTest` catches gaps).
3. Compile. KSP writes
   `app/schemas/com.pennywiseai.tracker.data.database.PennyWiseDatabase/<N+1>.json`.
   Commit it and review its diff like code.
4. The SQL must run on SQLite 3.18 (minSdk 26). Don't use `HAVING` without `GROUP BY`
   (3.39), `RENAME COLUMN` (3.25), `DROP COLUMN` (3.35), UPSERT `ON CONFLICT … DO`
   (3.24), window functions (3.25) or `RETURNING` (3.35). To rename or drop a column,
   rebuild the table (CREATE new, INSERT … SELECT, DROP, `ALTER TABLE … RENAME TO`), or
   use an `AutoMigrationSpec` with `@RenameColumn`/`@DeleteColumn` so Room generates
   the rebuild. Guard re-runs with `db.hasColumn(...)` as `MIGRATION_13_14` does.
   `MigrationSqlCompatibilityTest` only catches `HAVING`.
5. Never add `fallbackToDestructiveMigration()`.
6. Check the upgrade on old Android with the **emulator-verify** skill, following "Old
   Android runs old SQLite" in `docs/database-migrations.md`. On the `Api26_Migration`
   AVD, install the last release, open it, then install this build over it. It passes
   with no `FATAL` in `logcat -b crash` and `PRAGMA user_version` at the new version.

## Tests

- In `app/src/test/java/com/pennywiseai/tracker/data/backup/`, add an
  `oldBackupMissing<Thing>_…` test that decodes a raw JSON string shaped like the
  previous release and asserts the defaults, plus a round-trip test. For a rename, also
  decode the **old** key and assert the value arrives.
- If you touched import remapping, test it the way `BackupImporterGoalMergeTest`
  drives `importGoalsWithMerge`.
- Synthetic data only (HC4): no real SMS text, names, account numbers or UPI IDs.
- While iterating, run
  `./gradlew :app:testStandardDebugUnitTest --tests "com.pennywiseai.tracker.data.backup.*"`.
  When done, run `./init.sh app` (it includes `BackupSchemaGuardTest`,
  `MigrationRegistryTest` and `MigrationSqlCompatibilityTest`).
- On device, restore a backup exported by the last release, with MERGE and then
  REPLACE_ALL. The success message must not mention "rows could not be imported".
- Commit titles look like `feat(backup): … (#NNN)` or `fix(backup): … (#NNN)`.

## Stop and ask the maintainer before you

- remove a default, or add a shipped table's field to `KNOWN_REQUIRED_ENTITY_FIELDS`
- rename or remove a shipped key, enum constant or table, or change a wire representation
- change `CURRENT_FORMAT` or `COMPATIBLE_PREFIX`
- write a migration that deletes or rewrites user rows beyond a backfill
- pick on your own whether a new table is backed up, its privacy tier, or the winner of a MERGE conflict
- call a migration done without the `Api26_Migration` check. If the AVD is unavailable, say so.
