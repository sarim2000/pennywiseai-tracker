package com.pennywiseai.tracker.data.database

import androidx.room3.RoomDatabase
import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteConnection

/**
 * Room 2's `withTransaction`, on Room 3's [withWriteTransaction] (an IMMEDIATE
 * transaction; a nested call becomes a savepoint inside the outer one).
 */
// ponytail: keeps the Room 2 name so ~50 call sites and their `return@withTransaction` labels stay untouched.
suspend fun <R> RoomDatabase.withTransaction(block: suspend () -> R): R =
    withWriteTransaction { block() }

/** `SupportSQLiteDatabase.execSQL(sql, bindArgs)` for a driver connection. Args: String, Int or Long. */
internal fun SQLiteConnection.execSQL(sql: String, args: Array<Any>) {
    prepare(sql).use { stmt ->
        args.forEachIndexed { i, arg ->
            when (arg) {
                is String -> stmt.bindText(i + 1, arg)
                is Int -> stmt.bindLong(i + 1, arg.toLong())
                is Long -> stmt.bindLong(i + 1, arg)
                else -> error("Unsupported bind arg type: ${arg::class}")
            }
        }
        stmt.step()
    }
}

/** True when [table] has a column named [column] (`PRAGMA table_info`). */
internal fun SQLiteConnection.hasColumn(table: String, column: String): Boolean =
    prepare("PRAGMA table_info($table)").use { stmt ->
        val nameIndex = stmt.getColumnNames().indexOf("name")
        while (stmt.step()) {
            if (nameIndex != -1 && stmt.getText(nameIndex) == column) return@use true
        }
        false
    }
