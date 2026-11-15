package com.pennywiseai.tracker.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `ui` is shared UI: components, app-wide ViewModels and small helpers that
 * features and the app shell build on. It must not reach up into a feature
 * (`presentation`) or into the navigation graph and shell (`navigation`).
 *
 * The shell (`MainScreen`, `PennyWiseBottomNavigation`, `BottomNavItem`) lives in
 * `navigation`, which may import anything. Before it moved there, `ui` imported
 * both packages and each imported `ui` back, so `presentation` ↔ `ui` and
 * `navigation` ↔ `ui` were cycles. This test keeps them from coming back.
 *
 * There is no allowlist: no `ui` file imports either package. Only `import`
 * lines are checked, so don't use a fully qualified name to get around it.
 */
class UiBoundaryTest {

    @Test
    fun `ui does not import presentation or navigation`() {
        val subjects = uiFiles
        assertTrue(
            "ui rule matched no files. Did the package move? Point the rule at it.",
            subjects.isNotEmpty(),
        )
        val violations = subjects
            .associate { file ->
                file.repoPath to file.imports
                    .map { if (it.isWildcard) "${it.name}.*" else it.name }
                    .filter { import -> FORBIDDEN.any { import.isWithin(it) } }
            }
            .filterValues { it.isNotEmpty() }
        assertTrue(
            "ui must not import presentation or navigation. Move the code to the feature or to " +
                "navigation/ (the app shell), or pass the value in as a parameter " +
                "(docs/architecture.md):\n" +
                violations.entries.joinToString("\n") { (path, imports) ->
                    "  $path\n" + imports.joinToString("\n") { "    import $it" }
                },
            violations.isEmpty(),
        )
    }

    @Test
    fun `the app shell lives in navigation`() {
        val shell = setOf("MainScreen.kt", "PennyWiseBottomNavigation.kt", "BottomNavItem.kt")
        val found = productionFiles.filter { it.nameWithExtension in shell }
        assertTrue("No app shell file found. Did it get renamed? Update this test.", found.size == shell.size)
        val misplaced = found.filterNot { it.packagee?.name == "$APP.navigation" }
        assertTrue(
            "The app shell belongs in $APP.navigation:\n" + misplaced.joinToString("\n") { "  ${it.repoPath}" },
            misplaced.isEmpty(),
        )
    }

    private companion object {
        const val APP = "com.pennywiseai.tracker"

        val FORBIDDEN = listOf("$APP.presentation", "$APP.navigation")

        /** `app` plus every `feature/<name>` module, as in [ArchitectureTest]. */
        val MODULES: List<String> by lazy {
            // Gradle runs tests from the module dir; an IDE may run them from the root.
            val root = if (File("settings.gradle.kts").isFile) File(".") else File("..")
            listOf("app") + File(root, "feature").listFiles { f -> File(f, "build.gradle.kts").isFile }
                .orEmpty().map { "feature/${it.name}" }.sorted()
        }

        /** Production sources of every flavor and build type. */
        val productionFiles: List<KoFileDeclaration> by lazy {
            MODULES.flatMap { Konsist.scopeFromProduction(moduleName = it).files }
        }

        val uiFiles: List<KoFileDeclaration> by lazy { productionFiles.filter { it.isInPackage("$APP.ui") } }

        /** `a.b` contains `a.b` itself and everything under `a.b.`, but not `a.bc`. */
        fun String.isWithin(prefix: String) = this == prefix || startsWith("$prefix.")

        fun KoFileDeclaration.isInPackage(pkg: String) = packagee?.name?.isWithin(pkg) == true

        val KoFileDeclaration.repoPath: String
            get() = projectPath.replace('\\', '/').removePrefix("/")
    }
}
