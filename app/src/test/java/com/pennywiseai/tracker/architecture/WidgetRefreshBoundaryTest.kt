package com.pennywiseai.tracker.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `data` and `receiver` refresh widgets through `core.TransactionWidgets`, which
 * `widget` implements, and read the app language from `utils.inAppLanguage`.
 * Neither imports `widget` (that made data -> widget and receiver <-> widget
 * cycles), nor reaches `WidgetRefresher` by a fully qualified name.
 */
class WidgetRefreshBoundaryTest {

    @Test
    fun `data imports nothing from widget`() = assertNoWidgetImports(dataFiles, "data")

    @Test
    fun `receiver imports nothing from widget`() = assertNoWidgetImports(receiverFiles, "receiver")

    @Test
    fun `data and receiver do not call WidgetRefresher`() {
        val violations = (dataFiles + receiverFiles).filter { "WidgetRefresher" in it.text }.map { it.path }
        assertTrue(
            "Refresh widgets through core.TransactionWidgets:\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun `TransactionWidgets is implemented once, in widget`() {
        val implementations = appFiles
            .flatMap { it.classes() }
            .filter { declaration -> declaration.parents().any { it.name == "TransactionWidgets" } }
            .map { "${it.packagee?.name}.${it.name}" }
        assertEquals(listOf("$WIDGET.WorkManagerTransactionWidgets"), implementations)
    }

    @Test
    fun `core's TransactionWidgets imports nothing from the app`() {
        val file = appFiles.single { it.packagee?.name == "$APP.core" && it.name == "TransactionWidgets" }
        val appImports = file.imports.map { it.name }.filter { it.startsWith("$APP.") }
        assertTrue("core must stay a leaf: $appImports", appImports.isEmpty())
    }

    private fun assertNoWidgetImports(files: List<KoFileDeclaration>, layer: String) {
        val violations = files.flatMap { file ->
            file.imports.map { it.name }
                .filter { it == WIDGET || it.startsWith("$WIDGET.") }
                .map { "${file.path}: import $it" }
        }
        assertTrue(
            "$layer must not import $WIDGET; use core.TransactionWidgets or utils.inAppLanguage:\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    private companion object {
        const val APP = "com.pennywiseai.tracker"
        const val WIDGET = "$APP.widget"

        val appFiles: List<KoFileDeclaration> by lazy { Konsist.scopeFromProduction(moduleName = "app").files }

        fun filesIn(pkg: String): List<KoFileDeclaration> =
            appFiles.filter { it.packagee?.name?.let { name -> name == pkg || name.startsWith("$pkg.") } == true }
                .also { assertTrue("No $pkg files found. Did the package move?", it.isNotEmpty()) }

        val dataFiles: List<KoFileDeclaration> by lazy { filesIn("$APP.data") }
        val receiverFiles: List<KoFileDeclaration> by lazy { filesIn("$APP.receiver") }
    }
}
