# Database Migrations Guide

PennyWise's Room database stands at version 58 (`SCHEMA_VERSION`, 23 entities). Every migration, auto or manual, is defined in one file: `app/src/main/java/com/pennywiseai/tracker/data/database/PennyWiseDatabase.kt`. Start there when you touch the schema; this doc explains how that file is organized and which rules keep upgrades safe.

## The one rule: ALL_MIGRATIONS

Manual migrations register in a single shared array at the bottom of `PennyWiseDatabase.kt`:

```kotlin
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_12_14,
    MIGRATION_13_14,
    // ... through MIGRATION_57_58
)
```

Two builders consume that array: the Hilt provider in `di/DatabaseModule.kt`, and the `getInstance(context)` fallback builder kept for non-Hilt callers. Register a migration once and both paths get it. The rule exists because of a real crash: the Hilt builder once carried its own list, missed `MIGRATION_47_48`, and v47 installs crashed on boot after updating to v48. Never pass a hand-picked list to `.addMigrations()` anywhere else.

## How a version bump ships here

### Route 1: auto-migration

Purely additive changes need no migration code. Bump `SCHEMA_VERSION`, add `AutoMigration(from = N, to = N + 1)` to the `@Database` annotation, build once so KSP exports the schema JSON, commit it. Recent examples: v55→56 created the `merchant_aliases` table and v56→57 added a nullable `account_last4` column to subscriptions.

When Room can't infer your intent, give the auto-migration a spec class next to the database class:

- `Migration4To5` removes `chat_messages.sessionId` through `@DeleteColumn.Entries`
- `Migration35To36` drops the `category_budget_limits` table through `@DeleteTable.Entries`
- `Migration7To8` seeds the default categories in `onPostMigrate` (this is where the categories feature shipped)
- `Migration10To11` backfills `account_balances` from historical transaction rows
- `Migration27To28` derives `account_type` from the old `is_credit_card` flag
- `Migration43To44` inserts the Personal and Business profiles

### Route 2: manual migration

Reach for `Migration(from, to)` when data moves in ways Room can't generate: nullability flips, column guards on databases that may have drifted, backfills. The file holds 19 registered ones, `MIGRATION_12_14` through `MIGRATION_57_58`. Read two or three before writing your own — `MIGRATION_13_14` shows the house pattern of checking `PRAGMA table_info` before altering.

Two quirks worth knowing:

- `MIGRATION_1_2` sits in the file as a live but unregistered example. v1→2 runs as an auto-migration.
- Some manual migrations skip intermediate versions (`MIGRATION_12_14`) so devices several releases behind can jump directly.

## Adding a migration: checklist

1. Bump `SCHEMA_VERSION` at the top of `PennyWiseDatabase.kt`. It's a top-level constant so non-Room code like `BackupExporter` can stamp backups with the same number.
2. Make your entity changes.
3. Additive change: add an `AutoMigration` entry (plus spec if ambiguous). Anything else: write the `Migration` object **and** register it in `ALL_MIGRATIONS`.
4. Build once. KSP exports `app/schemas/com.pennywiseai.tracker.data.database.PennyWiseDatabase/<version>.json` automatically (the `room.schemaLocation` arg lives in `app/build.gradle.kts`). Commit the JSON.
5. Test the upgrade against a real pre-change database if you touched existing rows.
6. Verify a fresh install still works — migrations only run on existing database files.
7. Run the update path on old Android (below). Not optional: the JVM tests can't catch it.

## Old Android runs old SQLite

Migrations run on the phone's own SQLite, not the app's. minSdk 26 (Android 8) ships SQLite 3.18, Android 13 ships 3.32.2, and only Android 14+ has 3.39+. The JVM's SQLite and our usual emulators (API 36+) are newer, so syntax that works in every test can still crash old phones. 2.22.0 shipped `HAVING` without `GROUP BY` (needs 3.39) in `MIGRATION_63_64`: every launch on Android ≤13 crashed, and Room rolled the migration back so it crashed again next launch (#922).

Avoid features newer than 3.18 in migration SQL — e.g. `HAVING` without `GROUP BY` (3.39), `RENAME COLUMN` (3.25), `DROP COLUMN` (3.35), `UPSERT` (3.24), window functions (3.25), `RETURNING` (3.35). `MigrationSqlCompatibilityTest` guards the `HAVING` case only.

**Check before merging/releasing a migration**, on the `Api26_Migration` AVD (Android 8.0, SQLite 3.18.2 — our minSdk, so the oldest SQLite we run on). `Api33_Migration` (Android 13, 3.32.2) is an optional extra.

```bash
~/Library/Android/sdk/emulator/emulator -avd Api26_Migration -port 5562 -no-snapshot-save -no-boot-anim
```

1. Quick SQL check: `adb -s emulator-5562 shell "sqlite3 :memory: '<your SQL>'"` (create the tables it touches first).
2. Real upgrade: install the last released build (`git worktree add` at its tag, `:app:assembleFdroidDebug`) with `adb -s emulator-5562 install -r <apk>`, open it once, then `adb -s emulator-5562 install -r` the new build and launch it.
3. Pass = no `FATAL` in `adb -s emulator-5562 logcat -b crash`, and this prints the new `SCHEMA_VERSION`:
   ```bash
   adb -s emulator-5562 shell "run-as com.pennywiseai.tracker.debug sqlite3 databases/pennywise_database 'PRAGMA user_version'"
   ```

If the AVD is missing: `sdkmanager "system-images;android-26;google_apis;arm64-v8a"` then `avdmanager create avd -n Api26_Migration -k "system-images;android-26;google_apis;arm64-v8a" -d pixel_2`.

## No destructive fallback

Neither builder calls `fallbackToDestructiveMigration()`. A missing migration fails loudly during development instead of silently wiping a user's transaction history in production. That's deliberate; don't add one "temporarily".

## Schema files are contracts

`app/schemas/` holds one generated JSON per shipped version. Review these diffs in PRs as carefully as code: they define exactly what Room expects on disk after each hop, and every future device will migrate through them. One known gap: `48.json` was never committed (history runs 47 → 49). Nothing migrates against it, but don't treat the gap as precedent.

## Troubleshooting

**"Migration didn't properly handle"** — Room compared the migrated schema against the exported JSON and found drift. Usually a missing default value or an index your SQL forgot to create. Diff your post-migration schema against the target version's JSON.

**"Cannot find the schema file"** — confirm `exportSchema = true` on the `@Database` annotation and the `ksp { arg("room.schemaLocation", ...) }` block in `app/build.gradle.kts`.

**"Cannot add a NOT NULL column"** — add it nullable first, backfill with `UPDATE`, then rebuild the table with the constraint if you truly need it.
