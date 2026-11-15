package com.pennywiseai.tracker.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `data` raises money alerts through its own `data.manager.MoneyAlerts`
 * contract, which `worker` implements. `data` imports nothing from `worker`
 * (docs/architecture.md), and doesn't reach the alert worker or its
 * notifications by a fully qualified name either.
 */
class MoneyAlertsBoundaryTest {

    @Test
    fun `data imports nothing from worker`() {
        val violations = dataFiles.flatMap { file ->
            file.imports.map { it.name }
                .filter { it == WORKER || it.startsWith("$WORKER.") }
                .map { "${file.path}: import $it" }
        }
        assertTrue(
            "data must not import $WORKER; add a contract in data and implement it in worker:\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun `data does not call the money-alert worker or notifications`() {
        val violations = dataFiles.flatMap { file ->
            FORBIDDEN_CALLS.filter { it in file.text }.map { "${file.path}: $it" }
        }
        assertTrue(
            "data must raise money alerts through MoneyAlerts:\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun `MoneyAlerts is implemented once, in worker`() {
        val implementations = appFiles
            .flatMap { it.classes() }
            .filter { declaration -> declaration.parents().any { it.name == "MoneyAlerts" } }
            .map { "${it.packagee?.name}.${it.name}" }
        assertEquals(listOf("$WORKER.WorkManagerMoneyAlerts"), implementations)
    }

    private companion object {
        const val APP = "com.pennywiseai.tracker"
        const val WORKER = "$APP.worker"

        val FORBIDDEN_CALLS = listOf("MoneyAlertWorker.enqueue", "MoneyAlertNotifications.show")

        val appFiles: List<KoFileDeclaration> by lazy { Konsist.scopeFromProduction(moduleName = "app").files }

        val dataFiles: List<KoFileDeclaration> by lazy {
            appFiles.filter { it.packagee?.name?.let { name -> name == "$APP.data" || name.startsWith("$APP.data.") } == true }
                .also { assertTrue("No data files found. Did the package move?", it.isNotEmpty()) }
        }
    }
}
