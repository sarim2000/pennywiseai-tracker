package com.pennywiseai.tracker.ui.icons

import androidx.compose.ui.graphics.Color
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryColorsTest {

    @Test
    fun `uses persisted color for custom category`() {
        val categoriesByName = mapOf(
            "My Custom" to CategoryEntity(
                name = "My Custom",
                color = "#FF5722"
            )
        )

        assertEquals(Color(0xFFFF5722), categoryColorFor("My Custom", categoriesByName))
    }

    @Test
    fun `falls back to CategoryMapping for known system category`() {
        val expected = CategoryMapping.categories["Food & Dining"]!!.color

        assertEquals(expected, categoryColorFor("Food & Dining"))
    }

    @Test
    fun `falls back to Others for unknown category`() {
        val expected = CategoryMapping.categories["Others"]!!.color

        assertEquals(expected, categoryColorFor("Unknown Category"))
    }

    @Test
    fun `prefers persisted color over CategoryMapping for system category`() {
        val categoriesByName = mapOf(
            "Food & Dining" to CategoryEntity(
                name = "Food & Dining",
                color = "#123456"
            )
        )

        assertEquals(Color(0xFF123456), categoryColorFor("Food & Dining", categoriesByName))
    }
}
