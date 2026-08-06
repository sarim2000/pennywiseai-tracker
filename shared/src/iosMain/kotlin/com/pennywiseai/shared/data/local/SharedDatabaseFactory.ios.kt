package com.pennywiseai.shared.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSHomeDirectory

actual class SharedDatabaseFactory actual constructor() {
    actual fun createDatabase(): SharedDatabase {
        val dbPath = "${NSHomeDirectory()}/Documents/${SharedDatabase.DATABASE_NAME}"

        // No destructive fallback: this is a finance database — a schema bump
        // must ship a Migration or fail loudly in development, never silently
        // wipe the user's data. The schema was born at version 2, so there is
        // no legacy version to migrate from yet; add migrations here as the
        // version grows.
        return Room.databaseBuilder<SharedDatabase>(name = dbPath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
    }
}
