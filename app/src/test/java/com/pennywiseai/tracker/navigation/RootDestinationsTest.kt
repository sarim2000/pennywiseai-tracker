package com.pennywiseai.tracker.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Root launches need an entry in PennyWiseNavHost. Plan uses MainScreen's tab
 * stack, with a registered standalone fallback when another root screen is open.
 */
class RootDestinationsTest {

    private val src = File("src/main/java/com/pennywiseai/tracker")

    @Test
    fun `every screen an intent extra opens is registered on the root host`() {
        val activity = File(src, "MainActivity.kt").readText()
        val handler = activity.substringAfter("fun handleWidgetIntent(").substringBefore("\n    }\n")
        val opened = Regex("""navigation\.(\w+)""").findAll(handler).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("GoalDetail", "UnrecognizedSms", "Review", "Insights", "BudgetGroups", "Plan"), opened)

        val app = File(src, "PennyWiseApp.kt").readText()
        assertTrue(app.contains("val target = if (destination is Plan) BudgetGroups() else destination"))
        val rootRoutes = opened - "Plan"

        val host = File(src, "navigation/PennyWiseNavHost.kt").readText()
        val registered = Regex("""entry<(\w+)>""").findAll(host).map { it.groupValues[1] }.toSet()
        assertTrue("Not on the root host: ${rootRoutes - registered}", registered.containsAll(rootRoutes))
    }
}
