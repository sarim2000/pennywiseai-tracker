package com.pennywiseai.tracker.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WorkManager schedulers live in `worker`, beside the workers they enqueue
 * (docs/architecture.md). `data` doesn't build or enqueue work requests, so it
 * has no reason to import WorkManager or a worker class.
 *
 * Only `import` lines are checked, like [ArchitectureTest].
 */
class SchedulerPlacementBoundaryTest {

    @Test
    fun `schedulers live in the worker package`() {
        val found = SCHEDULERS.associateWith { name ->
            appFiles.flatMap { file -> file.classes().filter { it.name == name } }
                .map { it.packagee?.name }
        }
        SCHEDULERS.forEach { name ->
            assertEquals("$name should be declared once, in $APP.worker", listOf("$APP.worker"), found.getValue(name))
        }
    }

    @Test
    fun `data does not import WorkManager or workers`() {
        val dataFiles = appFiles.filter { it.packagee?.name?.isWithin("$APP.data") == true }
        assertTrue("No data files found. Did the package move?", dataFiles.isNotEmpty())

        val violations = dataFiles.flatMap { file ->
            file.importNames()
                .filter { import -> FORBIDDEN.any { import.isWithin(it) } }
                .map { "${file.path}: import $it" }
        }
        assertTrue(
            "data must not schedule work; use the scheduler in $APP.worker instead:\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    private companion object {
        const val APP = "com.pennywiseai.tracker"

        val SCHEDULERS = listOf("SmsScanManager", "WebhookSyncScheduler")

        val FORBIDDEN = listOf("androidx.work", "$APP.worker")

        val appFiles: List<KoFileDeclaration> by lazy { Konsist.scopeFromProduction(moduleName = "app").files }

        fun String.isWithin(prefix: String) = this == prefix || startsWith("$prefix.")

        /** Konsist names `import a.B.*` just `a.B`; keep the `.*` so wildcards still match. */
        fun KoFileDeclaration.importNames() = imports.map { if (it.isWildcard) "${it.name}.*" else it.name }
    }
}
