package com.pennywiseai.tracker.data.database

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Migrations run on the phone's own SQLite, which is old on old Android
 * (3.32 on Android 13, 3.18 on Android 8). The JVM's SQLite accepts newer
 * syntax, so a migration test here can't catch it. 2.22.0 shipped
 * `HAVING` without `GROUP BY` (SQLite 3.39+, Android 14+) and every update on
 * Android 13 and older crashed on launch.
 */
class MigrationSqlCompatibilityTest {

    @Test
    fun `no migration uses HAVING without GROUP BY`() {
        val statements = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any()) } answers { statements += firstArg<String>() }
        every { db.execSQL(any(), any()) } answers { statements += firstArg<String>() }

        PennyWiseDatabase.ALL_MIGRATIONS.forEach { it.migrate(db) }

        val offending = statements.filter {
            it.contains("HAVING", ignoreCase = true) && !it.contains("GROUP BY", ignoreCase = true)
        }
        assertTrue("HAVING without GROUP BY needs SQLite 3.39 (Android 14+):\n$offending", offending.isEmpty())
    }
}
