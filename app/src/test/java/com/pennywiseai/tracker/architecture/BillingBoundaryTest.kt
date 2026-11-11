package com.pennywiseai.tracker.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `data` and `core` sit below `billing`: `billing.license.LicenseManager` reads
 * `data.preferences`, so a `data -> billing` import closes a package cycle. Data
 * code reaches billing through the `core.entitlement` contracts instead
 * (`ProEntitlement`, `LicenseRestorer`, `FreeTierLimits`), bound in
 * `di/EntitlementContractsModule`.
 *
 * Checks `import` lines of the production sources in every flavor, like
 * [ArchitectureTest]. A fully qualified name in code isn't caught, so don't
 * use one to get around the rule.
 */
class BillingBoundaryTest {

    @Test
    fun `data does not import billing`() {
        val found = forbiddenImports(inPackage = "$APP.data")
        val violations = found.filterNot { it in ALLOWLIST }
        val stale = ALLOWLIST.filterNot { it in found }
        assertTrue(
            "data must not import billing. Depend on a core.entitlement contract instead:\n" +
                violations.joinToString("\n") { (path, import) -> "  $path: import $import" },
            violations.isEmpty(),
        )
        assertTrue(
            "Allowlist entries that no longer violate (fixed, moved or deleted). Delete them:\n" +
                stale.joinToString("\n") { (path, import) -> "  $path: import $import" },
            stale.isEmpty(),
        )
    }

    @Test
    fun `core does not import billing`() {
        val violations = forbiddenImports(inPackage = "$APP.core")
        assertTrue(
            "core must not import billing; the entitlement contracts have to stay below it:\n" +
                violations.joinToString("\n") { (path, import) -> "  $path: import $import" },
            violations.isEmpty(),
        )
    }

    @Test
    fun `the entitlement contracts are scanned`() {
        val contracts = productionFiles.filter { it.packagee?.name == "$APP.core.entitlement" }.map { it.name }
        assertTrue(
            "core.entitlement contracts not found ($contracts). Did the package move? Point this test at it.",
            contracts.containsAll(listOf("ProEntitlement", "LicenseRestorer", "FreeTierLimits")),
        )
    }

    private fun forbiddenImports(inPackage: String): List<Pair<String, String>> {
        val subjects = productionFiles.filter { it.packagee?.name?.isWithin(inPackage) == true }
        assertTrue("No file in $inPackage. Did the package move?", subjects.isNotEmpty())
        return subjects.flatMap { file ->
            file.imports
                .map { if (it.isWildcard) "${it.name}.*" else it.name }
                .filter { it.isWithin("$APP.billing") }
                .map { file.repoPath to it }
        }
    }

    private companion object {
        const val APP = "com.pennywiseai.tracker"

        /**
         * File path to an import it may keep. Only shrinks: the scheduler is moving
         * to `worker`, where importing `billing` is fine, and this entry goes with it.
         */
        val ALLOWLIST = listOf(
            "app/src/main/java/com/pennywiseai/tracker/data/webhook/WebhookSyncScheduler.kt" to
                "$APP.billing.EntitlementGate",
        )

        val productionFiles: List<KoFileDeclaration> by lazy {
            Konsist.scopeFromProduction(moduleName = "app").files
        }

        fun String.isWithin(prefix: String) = this == prefix || startsWith("$prefix.")

        val KoFileDeclaration.repoPath: String
            get() = projectPath.replace('\\', '/').removePrefix("/")
    }
}
