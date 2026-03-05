package com.pennywiseai.shared.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

actual class SharedDatabaseFactory actual constructor() {
    actual fun createDatabase(): SharedDatabase {
        error(
            "SharedDatabaseFactory on Android needs a Context. " +
                "Use createAndroidSharedDatabase(context) instead."
        )
    }
}

fun createAndroidSharedDatabase(context: Context): SharedDatabase =
    Room.databaseBuilder(
        context = context,
        klass = SharedDatabase::class.java,
        name = SharedDatabase.DATABASE_NAME
    )
        .fallbackToDestructiveMigration(dropAllTables = true)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
