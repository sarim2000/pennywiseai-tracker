package com.pennywiseai.tracker.di

import com.pennywiseai.tracker.data.database.entity.MerchantMappingEntity
import com.pennywiseai.tracker.data.repository.MerchantMappingRepository
import com.pennywiseai.tracker.presentation.settings.LearnedCategory
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class AppLearnedCategoriesDataTest {
    private val repository = mockk<MerchantMappingRepository>(relaxed = true)
    private val data = AppLearnedCategoriesData(repository)

    private val time = LocalDateTime.of(2026, 3, 1, 10, 30)

    @Test
    fun `projection keeps the repository's order, names and categories`() = runTest {
        every { repository.getAllMappings() } returns flowOf(
            listOf(
                MerchantMappingEntity("Zeta Mart", "Groceries", createdAt = time, updatedAt = time),
                MerchantMappingEntity("alpha cafe", "Food & Dining", createdAt = time, updatedAt = time),
                MerchantMappingEntity("Metro Fuel ", "Transportation", createdAt = time, updatedAt = time),
            )
        )

        assertEquals(
            listOf(
                LearnedCategory("Zeta Mart", "Groceries"),
                LearnedCategory("alpha cafe", "Food & Dining"),
                LearnedCategory("Metro Fuel ", "Transportation"),
            ),
            data.observeLearnedCategories().first(),
        )
    }

    @Test
    fun `an empty repository gives an empty list`() = runTest {
        every { repository.getAllMappings() } returns flowOf(emptyList())
        assertEquals(emptyList<LearnedCategory>(), data.observeLearnedCategories().first())
    }

    @Test
    fun `removal passes the exact merchant name to the repository`() = runTest {
        data.removeLearnedCategory("Metro Fuel ")
        coVerify(exactly = 1) { repository.removeMapping("Metro Fuel ") }
    }
}
