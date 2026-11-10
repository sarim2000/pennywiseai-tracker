package com.pennywiseai.tracker.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Package-boundary guardrails for `:app` and the `:feature:*` modules. The rules
 * are the ones in docs/architecture.md ("Ownership & dependency rules"), checked
 * on the production sources of every flavor and build type.
 *
 * A rule forbids some imports in some files. Imports that already broke a rule
 * when it was added are listed in its allowlist, per file and per exact import,
 * so an allowlisted file can't quietly gain a second back-edge. **An allowlist
 * may only shrink:** fix the import and delete the entry. An entry that no
 * longer matches an import (fixed, moved or deleted) fails the test too, so a
 * removed back-edge can't come back unnoticed.
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
        allowlist = emptyMap(),
    ).check()

    @Test
    fun `data does not import presentation or ui`() = Rule(
        name = "data",
        appliesTo = { it.isInPackage("$APP.data") },
        forbidden = listOf("$APP.presentation", "$APP.ui"),
        allowlist = emptyMap(),
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
            "$APP.aiaccess",
        ),
        allowlist = emptyMap(),
    ).check()

    @Test
    fun `ViewModels do not use DAOs or Room directly`() = Rule(
        name = "ViewModel",
        appliesTo = { it.declaresViewModel() },
        forbidden = DATABASE_ACCESS,
        allowlist = emptyMap(),
    ).check()

    @Test
    fun `composables do not use DAOs or Room directly`() = Rule(
        name = "@Composable",
        appliesTo = { it.declaresComposable() },
        forbidden = DATABASE_ACCESS,
        allowlist = emptyMap(),
    ).check()

    // One rule per package, so each fails on its own if its package moves, and
    // every package's report is shown, not just the first failing one.

    @Test
    fun `Android entry points do not import screen code`() = checkAll(
        ENTRY_POINTS.map { pkg ->
            Rule(
                name = pkg,
                appliesTo = { it.isInPackage("$APP.$pkg") },
                forbidden = listOf("$APP.presentation"),
                allowlist = ENTRY_POINT_ALLOWLIST.filterKeys { it.isUnderPackage(pkg) },
            )
        },
    )

    @Test
    fun `shared UI does not import features or workers`() = checkAll(
        SHARED_UI.map { pkg ->
            Rule(
                name = pkg,
                appliesTo = { it.isInPackage("$APP.$pkg") },
                forbidden = listOf("$APP.presentation", "$APP.worker"),
                allowlist = SHARED_UI_ALLOWLIST.filterKeys { it.isUnderPackage(pkg) },
            )
        },
    )

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

    @Test
    fun `feature modules are scanned`() {
        assertTrue(
            "No :feature:exchangerates file found. Did MODULES stop discovering feature/*?",
            productionFiles.any { it.repoPath.startsWith("feature/exchangerates/src/main/") },
        )
        assertTrue(
            "No :feature:exchangerates test file found. Did MODULES stop discovering feature/*?",
            testFiles.any { it.repoPath.startsWith("feature/exchangerates/src/test/") },
        )
    }

    private class Rule(
        val name: String,
        val appliesTo: (KoFileDeclaration) -> Boolean,
        val forbidden: List<String>,
        /** File path → the exact imports it may keep. */
        val allowlist: Map<String, Set<String>>,
    ) {
        fun check() = checkAll(listOf(this))

        /** What's wrong, or "" when the rule holds. */
        fun report(): String {
            val subjects = productionFiles.filter(appliesTo)
            if (subjects.isEmpty()) return "$name rule matched no files. Did the package move? Point the rule at it.\n"

            val violations = subjects
                .associate { file ->
                    // Konsist names `import a.B.*` just `a.B`. Keep the `.*`, or a wildcard
                    // would pass as the exact import an allowlist entry names.
                    file.repoPath to file.imports.map { if (it.isWildcard) "${it.name}.*" else it.name }.filter { import ->
                        forbidden.any { import.isWithin(it) }
                    }
                }
                .filterValues { it.isNotEmpty() }

            val added = violations
                .mapValues { (path, imports) -> imports - allowlist[path].orEmpty() }
                .filterValues { it.isNotEmpty() }
            val stale = allowlist.flatMap { (path, imports) ->
                (imports - violations[path].orEmpty().toSet()).map { "$path: import $it" }
            }

            return buildString {
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
        }
    }

    private companion object {
        const val APP = "com.pennywiseai.tracker"
        const val MAIN = "app/src/main/java/com/pennywiseai/tracker"

        /** Android entry points. They may start a screen's Activity but not use its code. */
        val ENTRY_POINTS = listOf("worker", "widget", "receiver", "tile", "aiaccess")

        val ENTRY_POINT_ALLOWLIST = mapOf(
            // Launch targets for PendingIntents: an Activity is a component, not screen code.
            "$MAIN/worker/DailySummaryWorker.kt" to setOf("$APP.presentation.add.QuickAddActivity"),
            "$MAIN/widget/RecentTransactionsWidget.kt" to setOf("$APP.presentation.add.QuickAddActivity"),
            // Goal status text, built from string resources
            "$MAIN/widget/GoalWidgetData.kt" to setOf(
                "$APP.presentation.goals.shortLine",
                "$APP.presentation.goals.statusLine",
            ),
            "$MAIN/widget/GoalWidgetConfigActivity.kt" to setOf(
                "$APP.presentation.goals.GoalRing",
                "$APP.presentation.goals.statusLine",
            ),
            // The unrecognized-SMS suggestion model, which also builds an Add draft
            "$MAIN/receiver/SmsBroadcastReceiver.kt" to setOf("$APP.presentation.unrecognized.Suggestion"),
        )

        /** Feature-agnostic UI shared by every screen (docs/architecture.md rule 3). */
        val SHARED_UI = listOf("ui.components", "ui.icons", "ui.animations", "ui.effects")

        val SHARED_UI_ALLOWLIST = mapOf(
            "$MAIN/ui/components/ShareCard.kt" to setOf("$APP.presentation.share.ShareCardData"),
            "$MAIN/ui/components/SmsParsingProgressDialog.kt" to setOf("$APP.worker.OptimizedSmsReaderWorker"),
        )

        fun checkAll(rules: List<Rule>) {
            val report = rules.joinToString("") { it.report() }
            assertTrue(report, report.isEmpty())
        }

        /** Whether a repo path is in [pkg] or below it, e.g. `ui.components` (and `ui.components.cards`). */
        fun String.isUnderPackage(pkg: String) = "/tracker/${pkg.replace('.', '/')}/" in this

        /** Room itself, the database class and the DAOs. Entities are plain models and allowed. */
        val DATABASE_ACCESS = listOf(
            "$APP.data.database.dao",
            "$APP.data.database.PennyWiseDatabase",
            "androidx.room3",
        )

        val VIEW_MODEL_BASES = setOf("ViewModel", "AndroidViewModel")

        /** The only ViewModels that may live in `ui.viewmodel`. Every other one belongs to a feature. */
        val APP_WIDE_VIEW_MODELS = setOf("MainViewModel", "ThemeViewModel", "SpotlightViewModel")

        /**
         * `app` plus every `feature/<name>` module (Konsist names a nested module by its
         * path). Feature modules keep `com.pennywiseai.tracker.*` packages, so every rule
         * applies to them unchanged.
         */
        val MODULES: List<String> by lazy {
            // Gradle runs tests from the module dir; an IDE may run them from the root.
            val root = if (File("settings.gradle.kts").isFile) File(".") else File("..")
            listOf("app") + File(root, "feature").listFiles { f -> File(f, "build.gradle.kts").isFile }
                .orEmpty().map { "feature/${it.name}" }.sorted()
        }

        /** Parsed once and shared by every rule: parsing dominates the runtime. */
        val productionFiles: List<KoFileDeclaration> by lazy {
            MODULES.flatMap { Konsist.scopeFromProduction(moduleName = it).files }
        }

        /** Unit and instrumented test sources, for the rules that check where files live. */
        val testFiles: List<KoFileDeclaration> by lazy {
            MODULES.flatMap { Konsist.scopeFromTest(moduleName = it).files }
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
