package com.pennywiseai.tracker.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `data` builds and cancels notifications through contracts it declares
 * (`ConscienceNotificationActions`, `UnrecognizedSmsAlerts`) that `receiver`
 * implements, reads `BankNotificationConfig` from `data.notification`, and asks
 * `core.AppForegroundState` instead of casting `PennyWiseApplication`. So `data`
 * imports nothing from `receiver` and no root-package class but `R` and
 * `BuildConfig`, and doesn't reach either by a fully qualified name.
 */
class NotificationContractsBoundaryTest {

    @Test
    fun `data imports nothing from receiver`() {
        val violations = dataFiles.flatMap { file ->
            file.imports.map { it.name }
                .filter { it == RECEIVER || it.startsWith("$RECEIVER.") }
                .map { "${file.path}: import $it" }
        }
        assertTrue(
            "data must not import $RECEIVER; declare a contract in data that receiver implements:\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun `data imports no root application class but R and BuildConfig`() {
        val violations = dataFiles.flatMap { file ->
            file.imports.map { it.name }
                .filter { isForbiddenRootClass(it) }
                .map { "${file.path}: import $it" }
        }
        assertTrue(
            "data must not import root classes such as PennyWiseApplication or MainActivity; use a core contract:\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun `data names neither receiver nor a root application class by a fully qualified name`() {
        val violations = dataFiles.flatMap { file ->
            FQN_REFERENCE.findAll(file.text)
                .map { it.value }
                .filter { it.startsWith("$RECEIVER.") || isForbiddenRootClass(it) }
                .map { "${file.path}: $it" }
                .toList()
        }
        assertTrue("Import through a contract instead:\n" + violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun `the data notification contracts are implemented once each, in receiver`() {
        assertEquals(listOf("$RECEIVER.ReceiverConscienceNotificationActions"), implementationsOf("ConscienceNotificationActions"))
        assertEquals(listOf("$RECEIVER.ReceiverUnrecognizedSmsAlerts"), implementationsOf("UnrecognizedSmsAlerts"))
    }

    @Test
    fun `core's AppForegroundState imports nothing from the app`() {
        val file = appFiles.single { it.packagee?.name == "$APP.core" && it.name == "AppForegroundState" }
        val appImports = file.imports.map { it.name }.filter { it.startsWith("$APP.") }
        assertTrue("core must stay a leaf: $appImports", appImports.isEmpty())
    }

    @Test
    fun `the root-class check catches the application and allows R and BuildConfig`() {
        assertTrue(isForbiddenRootClass("$APP.PennyWiseApplication"))
        assertTrue(isForbiddenRootClass("$APP.MainActivity"))
        assertTrue(!isForbiddenRootClass("$APP.R"))
        assertTrue(!isForbiddenRootClass("$APP.BuildConfig"))
        assertTrue(!isForbiddenRootClass("$APP.data.manager.ConscienceManager"))
    }

    private fun implementationsOf(contract: String): List<String> = appFiles
        .flatMap { it.classes() }
        .filter { declaration -> declaration.parents().any { it.name == contract } }
        .map { "${it.packagee?.name}.${it.name}" }

    private companion object {
        const val APP = "com.pennywiseai.tracker"
        const val RECEIVER = "$APP.receiver"
        val ALLOWED_ROOT_CLASSES = setOf("R", "BuildConfig")

        /** `com.pennywiseai.tracker.` followed by a path, as an import or a qualified reference spells it. */
        val FQN_REFERENCE = Regex("""\bcom\.pennywiseai\.tracker(\.\w+)+""")

        /** A class declared directly in the root package: its first segment after the package is capitalized. */
        fun isForbiddenRootClass(name: String): Boolean {
            if (!name.startsWith("$APP.")) return false
            val first = name.removePrefix("$APP.").substringBefore('.')
            return first.firstOrNull()?.isUpperCase() == true && first !in ALLOWED_ROOT_CLASSES
        }

        val appFiles: List<KoFileDeclaration> by lazy { Konsist.scopeFromProduction(moduleName = "app").files }

        val dataFiles: List<KoFileDeclaration> by lazy {
            appFiles.filter { it.packagee?.name?.let { name -> name == "$APP.data" || name.startsWith("$APP.data.") } == true }
                .also { assertTrue("No $APP.data files found. Did the package move?", it.isNotEmpty()) }
        }
    }
}
