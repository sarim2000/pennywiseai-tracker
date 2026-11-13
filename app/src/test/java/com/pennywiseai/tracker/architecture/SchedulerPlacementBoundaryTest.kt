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

        val found = dataFiles
            .associate { file -> file.repoPath to file.importNames().filter { import -> FORBIDDEN.any { import.isWithin(it) } } }
            .filterValues { it.isNotEmpty() }
        val violations = found.flatMap { (path, imports) ->
            (imports - PENDING_MONEY_ALERT_IMPORTS[path].orEmpty()).map { "$path: import $it" }
        }
        val stale = PENDING_MONEY_ALERT_IMPORTS.flatMap { (path, imports) ->
            (imports - found[path].orEmpty().toSet()).map { "$path: import $it" }
        }
        assertTrue(
            "data must not schedule work; use the scheduler in $APP.worker instead:\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
        assertTrue(
            "These exceptions no longer match an import (fixed, moved or deleted). Delete them:\n" +
                stale.joinToString("\n"),
            stale.isEmpty(),
        )
    }

    private companion object {
        const val APP = "com.pennywiseai.tracker"

        val SCHEDULERS = listOf("SmsScanManager", "WebhookSyncScheduler")

        val FORBIDDEN = listOf("androidx.work", "$APP.worker")

        /**
         * The money-alert hand-off from SMS processing to the worker is a known
         * data -> worker edge, left for a separate change. Don't add to this list.
         */
        val PENDING_MONEY_ALERT_IMPORTS = mapOf(
            "app/src/main/java/com/pennywiseai/tracker/data/manager/SmsTransactionProcessor.kt" to setOf(
                "$APP.worker.MoneyAlertWorker",
                "$APP.worker.MoneyAlertNotifications",
            ),
        )

        val appFiles: List<KoFileDeclaration> by lazy { Konsist.scopeFromProduction(moduleName = "app").files }

        val KoFileDeclaration.repoPath: String
            get() = projectPath.replace('\\', '/').removePrefix("/")

        fun String.isWithin(prefix: String) = this == prefix || startsWith("$prefix.")

        /** Konsist names `import a.B.*` just `a.B`; keep the `.*` so wildcards still match. */
        fun KoFileDeclaration.importNames() = imports.map { if (it.isWildcard) "${it.name}.*" else it.name }
    }
}
