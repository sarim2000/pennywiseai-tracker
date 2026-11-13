package com.pennywiseai.tracker.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `utils` and `core` are leaf packages: the small money, currency and date
 * helpers every layer uses. They must not import `data` or `domain`, or the
 * layers that import them get a cycle back (docs/architecture.md).
 *
 * Helpers that work on Room entities live in `data` instead (`data.ledger`,
 * and the entity formatting extensions in `data.database.entity`). Enums the
 * helpers need from preferences live in `core` (`DateFormatStyle`,
 * `NumberFormatStyle`).
 *
 * Only `import` lines are checked, like in [ArchitectureTest].
 */
class UtilsLeafBoundaryTest {

    @Test
    fun `utils and core do not import data or domain`() {
        val subjects = productionFiles.filter { it.isInPackage("$APP.utils") || it.isInPackage("$APP.core") }
        assertTrue(
            "utils/core rule matched no files. Did the package move? Point the rule at it.",
            subjects.any { it.isInPackage("$APP.utils") } && subjects.any { it.isInPackage("$APP.core") },
        )

        val violations = subjects.flatMap { file ->
            file.imports
                .map { if (it.isWildcard) "${it.name}.*" else it.name }
                .filter { import -> FORBIDDEN.any { import.isWithin(it) } }
                .map { "${file.repoPath}: import $it" }
        }
        assertTrue(
            "utils/core must not import data or domain. Move the helper into data (or the type into core):\n" +
                violations.joinToString("\n") { "  $it" },
            violations.isEmpty(),
        )
    }

    private companion object {
        const val APP = "com.pennywiseai.tracker"

        val FORBIDDEN = listOf("$APP.data", "$APP.domain")

        val productionFiles: List<KoFileDeclaration> by lazy {
            Konsist.scopeFromProduction(moduleName = "app").files
        }

        /** `a.b` contains `a.b` itself and everything under `a.b.`, but not `a.bc`. */
        fun String.isWithin(prefix: String) = this == prefix || startsWith("$prefix.")

        fun KoFileDeclaration.isInPackage(pkg: String) = packagee?.name?.isWithin(pkg) == true

        val KoFileDeclaration.repoPath: String
            get() = projectPath.replace('\\', '/').removePrefix("/")
    }
}
