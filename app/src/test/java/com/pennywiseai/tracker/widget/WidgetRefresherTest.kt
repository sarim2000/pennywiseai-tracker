package com.pennywiseai.tracker.widget

import androidx.glance.appwidget.GlanceAppWidget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class WidgetRefresherTest {

    @Test
    fun `a redraw reaches every widget the launcher has placed, past one that fails`() = runBlocking {
        val placed = mapOf(
            GoalWidgetReceiver::class.java to intArrayOf(41, 42),
            BudgetWidgetReceiver::class.java to intArrayOf(7)
        )
        val redrawn = mutableListOf<Pair<Class<out GlanceAppWidget>, Int>>()

        WidgetRefresher.redrawEach(WidgetRefresher.WIDGET_RECEIVERS, { placed[it] ?: IntArray(0) }) { widget, id ->
            if (id == 41) error("gone")
            redrawn += widget.javaClass to id
        }

        assertEquals(listOf(BudgetWidget::class.java to 7, GoalWidget::class.java to 42), redrawn)
    }

    @Test
    fun `every widget in the manifest is redrawn`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val declared = Regex("""<receiver\s+android:name="\.widget\.(\w+)"[^>]*>(?:(?!</?receiver).)*android\.appwidget\.provider""", RegexOption.DOT_MATCHES_ALL)
            .findAll(manifest).map { it.groupValues[1] }.toSet()

        assertEquals(declared, WidgetRefresher.WIDGET_RECEIVERS.map { it.simpleName }.toSet())
    }
}
