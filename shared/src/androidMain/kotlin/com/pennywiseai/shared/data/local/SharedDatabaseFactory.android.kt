package com.pennywiseai.shared.data.local

actual class SharedDatabaseFactory actual constructor() {
    actual fun createDatabase(): SharedDatabase {
        // Android keeps its own Room database in :app (framework SQLite driver).
        error("SharedDatabaseFactory is iOS-only; the Android app uses PennyWiseDatabase.")
    }
}
