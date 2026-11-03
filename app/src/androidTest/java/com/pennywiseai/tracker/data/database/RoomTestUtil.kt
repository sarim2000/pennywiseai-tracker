package com.pennywiseai.tracker.data.database

import android.app.Instrumentation
import androidx.room3.RoomDatabase
import androidx.room3.testing.MigrationTestHelper
import androidx.room3.useReaderConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver

/** Migration helper on the file [name], with the app's driver (system SQLite). */
fun pennyWiseMigrationHelper(instrumentation: Instrumentation, name: String) = MigrationTestHelper(
    instrumentation, instrumentation.targetContext.getDatabasePath(name), AndroidSQLiteDriver(), PennyWiseDatabase::class
)

/** Every row of [sql], each as its columns' text (null for NULL). */
fun SQLiteConnection.rows(sql: String): List<List<String?>> = prepare(sql).use { s ->
    buildList { while (s.step()) add(List(s.getColumnCount()) { if (s.isNull(it)) null else s.getText(it) }) }
}

/** First column of each row of [sql] as a Long (replaces `openHelper.readableDatabase.query`). */
suspend fun RoomDatabase.longs(sql: String): List<Long> = useReaderConnection { c ->
    c.usePrepared(sql) { s -> buildList { while (s.step()) add(s.getLong(0)) } }
}
