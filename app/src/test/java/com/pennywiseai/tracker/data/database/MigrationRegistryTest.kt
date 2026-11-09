package com.pennywiseai.tracker.data.database

import androidx.room3.RoomDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An old database opens only if Room can chain migrations from its version to
 * [SCHEMA_VERSION]. A migration that was written but never registered leaves a
 * gap that compiles and passes every other JVM test, then crashes those users
 * on launch (the Hilt builder once missed `MIGRATION_47_48` this way).
 */
class MigrationRegistryTest {

    // Built as Room builds it: manual migrations first, and an auto-migration
    // only where no manual one covers the same hop.
    private val container = RoomDatabase.MigrationContainer().apply {
        addMigrations(PennyWiseMigrations.ALL_MIGRATIONS.toList())
        PennyWiseDatabase_Impl().createAutoMigrations(emptyMap())
            .filterNot { contains(it.startVersion, it.endVersion) }
            .forEach { addMigration(it) }
    }

    @Test
    fun `every version from 1 upgrades to SCHEMA_VERSION`() {
        val stranded = (1 until SCHEMA_VERSION).filter { container.findMigrationPath(it, SCHEMA_VERSION) == null }

        assertEquals("versions with no path to $SCHEMA_VERSION", emptyList<Int>(), stranded)
    }

    @Test
    fun `ALL_MIGRATIONS registers each hop once and stays within the schema`() {
        val hops = PennyWiseMigrations.ALL_MIGRATIONS.map { it.startVersion to it.endVersion }

        assertEquals("duplicate hops in ALL_MIGRATIONS", hops.distinct(), hops)
        assertTrue("hops outside 1..$SCHEMA_VERSION: $hops", hops.all { (start, end) -> start in 1 until end && end <= SCHEMA_VERSION })
    }
}
