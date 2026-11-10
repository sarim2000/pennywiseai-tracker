package com.pennywiseai.tracker.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Package-boundary guardrails for `:app`. The rules are the ones in
 * docs/architecture.md ("Ownership & dependency rules"), checked on the
 * production sources of every flavor and build type.
 *
 * A rule forbids some imports in some files. Files that already broke a rule
 * when it was added are listed in its allowlist. **An allowlist may only
 * shrink:** fix the import and delete the entry. An entry whose file no longer
 * violates (fixed, moved or deleted) fails the test too, so a removed
 * back-edge can't come back unnoticed.
 *
 * Only `import` lines are checked. A fully qualified name written in code
 * isn't caught, so don't use one to get around a rule.
 *
 * The last two rules check where files live instead: the retired `ui.screens`
 * package stays empty in every source set, and `ui.viewmodel` keeps only the
 * app-wide ViewModels. A feature's screens and ViewModels live in
 * `presentation/<feature>`.
 */
class ArchitectureTest {

    @Test
    fun `domain does not import UI, Compose, Activities or Room`() = Rule(
        name = "domain",
        appliesTo = { it.isInPackage("$APP.domain") },
        forbidden = listOf(
            "$APP.presentation",
            "$APP.ui",
            "androidx.compose",
            "androidx.activity",
            "androidx.fragment",
            "android.app.Activity",
            "androidx.room3",
        ),
        allowlist = setOf(
            // presentation.common profile filters
            "app/src/main/java/com/pennywiseai/tracker/domain/conscience/ConscienceEligibility.kt",
            // Compose icons/stringResource, presentation.accounts.AccountType
            "app/src/main/java/com/pennywiseai/tracker/domain/model/AccountTypeExtensions.kt",
            // FragmentActivity for the biometric prompt
            "app/src/main/java/com/pennywiseai/tracker/domain/security/BiometricAuthManager.kt",
        ),
    ).check()

    @Test
    fun `data does not import presentation`() = Rule(
        name = "data",
        appliesTo = { it.isInPackage("$APP.data") },
        forbidden = listOf("$APP.presentation"),
        allowlist = setOf(
            // presentation.accounts.AccountType
            "app/src/main/java/com/pennywiseai/tracker/data/repository/AccountBalanceRepository.kt",
        ),
    ).check()

    @Test
    fun `utils and core do not import UI or Android entry points`() = Rule(
        name = "utils/core",
        appliesTo = { it.isInPackage("$APP.utils") || it.isInPackage("$APP.core") },
        forbidden = listOf(
            "$APP.presentation",
            "$APP.ui",
            "$APP.worker",
            "$APP.receiver",
            "$APP.widget",
            "$APP.tile",
        ),
        allowlist = emptySet(),
    ).check()

    @Test
    fun `ViewModels do not use DAOs or Room directly`() = Rule(
        name = "ViewModel",
        appliesTo = { it.declaresViewModel() },
        forbidden = DATABASE_ACCESS,
        allowlist = setOf(
            // TransactionSplitDao
            "app/src/main/java/com/pennywiseai/tracker/presentation/budgetgroups/BudgetGroupEditViewModel.kt",
            "app/src/main/java/com/pennywiseai/tracker/presentation/transactions/TransactionsViewModel.kt",
        ),
    ).check()

    @Test
    fun `composables do not use DAOs or Room directly`() = Rule(
        name = "@Composable",
        appliesTo = { it.declaresComposable() },
        forbidden = DATABASE_ACCESS,
        allowlist = emptySet(),
    ).check()

    @Test
    fun `no Kotlin file lives in the retired ui screens package`() {
        // Checks the package line as well as the path: Kotlin doesn't require the
        // two to match, and a rebase can carry a file into presentation/ with its
        // old ui.screens package line.
        val retired = (productionFiles + testFiles).filter { file ->
            file.isInPackage("$APP.ui.screens") || "/com/pennywiseai/tracker/ui/screens/" in file.repoPath
        }
        assertTrue(
            "ui.screens is retired. Move these files to presentation/<feature> and fix their package line:\n" +
                retired.joinToString("\n") { "  ${it.repoPath}" },
            retired.isEmpty(),
        )
    }

    @Test
    fun `ui viewmodel holds only app-wide ViewModels`() {
        val subjects = productionFiles.filter { it.isInPackage("$APP.ui.viewmodel") }
        assertTrue(
            "ui.viewmodel rule matched no files. Did the package move? Point the rule at it.",
            subjects.isNotEmpty(),
        )
        val featureViewModels = subjects
            .flatMap { file -> file.classes().filter { it.isViewModel() }.map { file.repoPath to it.name } }
            .filter { (_, name) -> name !in APP_WIDE_VIEW_MODELS }
        assertTrue(
            "ui.viewmodel is for app-wide ViewModels ($APP_WIDE_VIEW_MODELS). Move a feature's " +
                "ViewModel to presentation/<feature>, next to its screen:\n" +
                featureViewModels.joinToString("\n") { (path, name) -> "  $name in $path" },
            featureViewModels.isEmpty(),
        )
    }

    private class Rule(
        val name: String,
        val appliesTo: (KoFileDeclaration) -> Boolean,
        val forbidden: List<String>,
        val allowlist: Set<String>,
    ) {
        fun check() {
            val subjects = productionFiles.filter(appliesTo)
            assertTrue(
                "$name rule matched no files. Did the package move? Point the rule at it.",
                subjects.isNotEmpty(),
            )

            val violations = subjects
                .associate { file ->
                    file.repoPath to file.imports.map { it.name }.filter { import ->
                        forbidden.any { import.isWithin(it) }
                    }
                }
                .filterValues { it.isNotEmpty() }

            val added = violations.filterKeys { it !in allowlist }
            val stale = allowlist - violations.keys

            val report = buildString {
                if (added.isNotEmpty()) {
                    appendLine("$name: forbidden imports. Move the code to the right layer (docs/architecture.md):")
                    added.forEach { (path, imports) ->
                        appendLine("  $path")
                        imports.forEach { appendLine("    import $it") }
                    }
                }
                if (stale.isNotEmpty()) {
                    appendLine("$name: allowlist entries that no longer violate (fixed, moved or deleted). Delete them:")
                    stale.forEach { appendLine("  $it") }
                }
            }
            assertTrue(report, report.isEmpty())
        }
    }

    private companion object {
        const val APP = "com.pennywiseai.tracker"

        /** Room itself, the database class and the DAOs. Entities are plain models and allowed. */
        val DATABASE_ACCESS = listOf(
            "$APP.data.database.dao",
            "$APP.data.database.PennyWiseDatabase",
            "androidx.room3",
        )

        val VIEW_MODEL_BASES = setOf("ViewModel", "AndroidViewModel")

        /** The only ViewModels that may live in `ui.viewmodel`. Every other one belongs to a feature. */
        val APP_WIDE_VIEW_MODELS = setOf("MainViewModel", "ThemeViewModel", "SpotlightViewModel")

        /** Parsed once and shared by every rule: parsing dominates the runtime. */
        val productionFiles: List<KoFileDeclaration> by lazy {
            Konsist.scopeFromProduction(moduleName = "app").files
        }

        /** Unit and instrumented test sources, for the rules that check where files live. */
        val testFiles: List<KoFileDeclaration> by lazy {
            Konsist.scopeFromTest(moduleName = "app").files
        }

        /** `a.b` contains `a.b` itself and everything under `a.b.`, but not `a.bc`. */
        fun String.isWithin(prefix: String) = this == prefix || startsWith("$prefix.")

        fun KoFileDeclaration.isInPackage(pkg: String) = packagee?.name?.isWithin(pkg) == true

        val KoFileDeclaration.repoPath: String
            get() = projectPath.replace('\\', '/').removePrefix("/")

        fun KoClassDeclaration.isViewModel() =
            name.endsWith("ViewModel") || parents().any { it.name in VIEW_MODEL_BASES }

        fun KoFileDeclaration.declaresViewModel() = classes().any { it.isViewModel() }

        fun KoFileDeclaration.declaresComposable() =
            functions(includeNested = true, includeLocal = true).any { fn ->
                fn.annotations.any { it.name == "Composable" }
            }
    }
}
