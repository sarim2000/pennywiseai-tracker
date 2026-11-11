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
        val violations = forbiddenImports(inPackage = "$APP.data")
            // Allowed only while this file still lives in `data`: it is moving to
            // `worker`, where importing `billing` is fine. Never add to this.
            .filterNot { (path, import) ->
                path.endsWith(WEBHOOK_SCHEDULER) && import == "$APP.billing.EntitlementGate"
            }
        assertTrue(
            "data must not import billing. Depend on a core.entitlement contract instead:\n" +
                violations.joinToString("\n") { (path, import) -> "  $path: import $import" },
            violations.isEmpty(),
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
        const val WEBHOOK_SCHEDULER = "/com/pennywiseai/tracker/data/webhook/WebhookSyncScheduler.kt"

        val productionFiles: List<KoFileDeclaration> by lazy {
            Konsist.scopeFromProduction(moduleName = "app").files
        }

        fun String.isWithin(prefix: String) = this == prefix || startsWith("$prefix.")

        val KoFileDeclaration.repoPath: String
            get() = projectPath.replace('\\', '/').removePrefix("/")
    }
}
