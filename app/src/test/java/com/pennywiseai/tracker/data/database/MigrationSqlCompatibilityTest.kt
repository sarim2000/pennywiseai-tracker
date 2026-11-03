package com.pennywiseai.tracker.data.database

import androidx.sqlite.SQLiteConnection
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
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
        // Every statement a migration runs goes through prepare() on the driver connection.
        val db = mockk<SQLiteConnection>(relaxed = true)
        every { db.prepare(any()) } answers { statements += firstArg<String>(); mockk(relaxed = true) }

        runBlocking { PennyWiseDatabase.ALL_MIGRATIONS.forEach { it.migrate(db) } }

        assertTrue("no migration SQL captured", statements.size > 50)
        val offending = statements.filter {
            it.contains("HAVING", ignoreCase = true) && !it.contains("GROUP BY", ignoreCase = true)
        }
        assertTrue("HAVING without GROUP BY needs SQLite 3.39 (Android 14+):\n$offending", offending.isEmpty())
    }
}
