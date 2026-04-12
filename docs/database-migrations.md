# Database Migrations Guide

## Overview
This guide explains how to handle database migrations in PennyWise using Room's migration features.

## Migration Types

### 1. Automatic Migrations
Room can automatically generate migrations for simple schema changes:
- Adding new columns with default values
- Adding new tables
- Removing columns (with caution)

```kotlin
@Database(
    version = 2,
    autoMigrations = [
        AutoMigration(from = 1, to = 2)
    ]
)
```

### 2. Automatic Migrations with Spec
For ambiguous changes, provide additional information:

```kotlin
@Database(
    version = 2,
    autoMigrations = [
        AutoMigration(
            from = 1, 
            to = 2, 
            spec = PennyWiseDatabase.Migration1To2::class
        )
    ]
)

// Inside PennyWiseDatabase
@RenameColumn(
    tableName = "transactions",
    fromColumnName = "merchant_name",
    toColumnName = "vendor_name"
)
class Migration1To2 : AutoMigrationSpec
```

### 3. Manual Migrations
For complex schema changes:

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create new table
        db.execSQL("""
            CREATE TABLE categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                icon TEXT,
                color TEXT
            )
        """)
        
        // Add foreign key
        db.execSQL("""
            ALTER TABLE transactions 
            ADD COLUMN category_id INTEGER 
            REFERENCES categories(id)
        """)
    }
}
```

## Common Migration Scenarios

### Adding a Column
```kotlin
// Auto-migration works if column has default value
@ColumnInfo(name = "notes", defaultValue = "")
val notes: String = ""
```

### Renaming a Column
```kotlin
@RenameColumn(
    tableName = "transactions",
    fromColumnName = "amount",
    toColumnName = "transaction_amount"
)
class MigrationSpec : AutoMigrationSpec
```

### Adding an Index
```kotlin
override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("""
        CREATE INDEX index_transactions_date 
        ON transactions(date_time)
    """)
}
```

### Data Transformation
```kotlin
override fun migrate(db: SupportSQLiteDatabase) {
    // Add new column
    db.execSQL("ALTER TABLE transactions ADD COLUMN amount_cents INTEGER")
    
    // Migrate data
    db.execSQL("""
        UPDATE transactions 
        SET amount_cents = CAST(amount * 100 AS INTEGER)
    """)
    
    // Drop old column (requires table recreation in SQLite)
}
```

## Best Practices

### 1. Always Test Migrations
```kotlin
@Test
fun testMigration1To2() {
    val db = Room.databaseBuilder(
        context,
        PennyWiseDatabase::class.java,
        "test-db"
    ).addMigrations(MIGRATION_1_2).build()
    
    // Verify migration
    val cursor = db.query("SELECT * FROM sqlite_master WHERE type='table'")
    // Assert expected schema
}
```

### 2. Export Schema
Enable schema export in build.gradle:
```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

### 3. Version Control Schema Files
- Commit generated schema JSON files
- Review changes in pull requests
- Use for migration testing

### 4. Fallback Strategies

#### Development
```kotlin
.fallbackToDestructiveMigration()  // Recreates database
```

#### Production
```kotlin
.fallbackToDestructiveMigrationFrom(1, 2)  // Only for specific versions
.fallbackToDestructiveMigrationOnDowngrade()  // For downgrades only
```

## Migration Checklist

- [ ] Increment database version
- [ ] Write migration (auto or manual)
- [ ] Test migration with real data
- [ ] Test fresh install
- [ ] Update schema documentation
- [ ] Consider data backup strategy
- [ ] Plan rollback procedure

## Example: Adding Transaction Tags

### Step 1: Update Entity
```kotlin
@Entity(tableName = "transactions")
data class TransactionEntity(
    // ... existing fields ...
    
    @ColumnInfo(name = "tags")
    val tags: List<String>? = null  // New field
)
```

### Step 2: Add Converter
```kotlin
@TypeConverter
fun fromStringList(value: List<String>?): String? {
    return value?.joinToString(",")
}

@TypeConverter
fun toStringList(value: String?): List<String>? {
    return value?.split(",")?.filter { it.isNotEmpty() }
}
```

### Step 3: Create Migration
```kotlin
@Database(
    version = 2,
    autoMigrations = [
        AutoMigration(from = 1, to = 2)
    ]
)
```

### Step 4: Update Module
```kotlin
.addMigrations(/* add if manual */)
```

## Troubleshooting

### "Cannot find the schema file"
- Enable `exportSchema = true`
- Check schema location in build.gradle

### "Migration didn't properly handle"
- Room detected schema mismatch
- Compare expected vs actual schema
- Write manual migration if needed

### "Cannot add a NOT NULL column"
- Provide default value
- Or make column nullable first, then migrate data

## Migration History

### Version 36 → 37
- Added `is_business` column (INTEGER, default 0) to `account_balances` table
- Allows marking bank accounts as business or personal
- AutoMigration (no spec needed — simple column addition with default)

### Version 37 → 38
- Added `is_business` column (nullable INTEGER) to `transactions` table
- Supports three-state logic: `null` = inherit from account, `1` = business, `0` = personal
- AutoMigration (no spec needed — nullable column addition)
