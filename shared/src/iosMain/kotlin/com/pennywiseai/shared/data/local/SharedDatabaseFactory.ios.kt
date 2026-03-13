package com.pennywiseai.shared.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSHomeDirectory

actual class SharedDatabaseFactory actual constructor() {
    actual fun createDatabase(): SharedDatabase {
        val dbPath = "${NSHomeDirectory()}/Documents/${SharedDatabase.DATABASE_NAME}"

        return Room.databaseBuilder<SharedDatabase>(name = dbPath)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
    }
}
