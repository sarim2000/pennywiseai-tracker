package com.pennywiseai.tracker.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * MainActivity's widget and notification extras push their screen onto the root
 * back stack, so each one needs an entry in PennyWiseNavHost. A key registered
 * only in MainScreen's tab stacks crashed at launch ("Unknown screen").
 */
class RootDestinationsTest {

    private val src = File("src/main/java/com/pennywiseai/tracker")

    @Test
    fun `every screen an intent extra opens is registered on the root host`() {
        val activity = File(src, "MainActivity.kt").readText()
        val handler = activity.substringAfter("fun handleWidgetIntent(").substringBefore("\n    }\n")
        val opened = Regex("""navigation\.(\w+)""").findAll(handler).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("GoalDetail", "UnrecognizedSms", "Review", "BudgetGroups"), opened)

        val host = File(src, "navigation/PennyWiseNavHost.kt").readText()
        val registered = Regex("""entry<(\w+)>""").findAll(host).map { it.groupValues[1] }.toSet()
        assertTrue("Not on the root host: ${opened - registered}", registered.containsAll(opened))
    }
}
