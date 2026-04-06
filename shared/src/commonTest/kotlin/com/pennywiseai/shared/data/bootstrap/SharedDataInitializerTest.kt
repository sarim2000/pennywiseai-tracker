package com.pennywiseai.shared.data.bootstrap

import com.pennywiseai.shared.data.model.SharedCategory
import com.pennywiseai.shared.data.repository.SharedCategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedDataInitializerTest {
    @Test
    fun seedsDefaultsWhenRepositoryIsEmpty() = runTest {
        val repo = FakeCategoryRepository(startCount = 0)
        SharedDataInitializer(repo).seedDefaultCategoriesIfNeeded()

        assertEquals(18, repo.insertedCategoryCount)
    }

    @Test
    fun doesNotSeedWhenCategoriesAlreadyExist() = runTest {
        val repo = FakeCategoryRepository(startCount = 2)
        SharedDataInitializer(repo).seedDefaultCategoriesIfNeeded()

        assertEquals(0, repo.insertedCategoryCount)
    }
}

private class FakeCategoryRepository(
    private var startCount: Int
) : SharedCategoryRepository {
    var insertedCategoryCount: Int = 0
        private set

    private val stored = mutableListOf<SharedCategory>()
    private var nextId = 1L

    override fun observeCategories(): Flow<List<SharedCategory>> = flowOf(emptyList())

    override fun observeCategoriesByIncomeType(isIncome: Boolean): Flow<List<SharedCategory>> =
        flowOf(emptyList())

    override suspend fun getById(id: Long): SharedCategory? = stored.find { it.id == id }

    override suspend fun countCategories(): Int = startCount

    override suspend fun insertCategories(categories: List<SharedCategory>) {
        insertedCategoryCount += categories.size
        startCount += categories.size
    }

    override suspend fun insertCategory(category: SharedCategory): Long {
        val id = nextId++
        stored.add(category.copy(id = id))
        startCount++
        return id
    }

    override suspend fun updateCategory(category: SharedCategory) {
        val index = stored.indexOfFirst { it.id == category.id }
        if (index >= 0) stored[index] = category
    }

    override suspend fun deleteCategory(id: Long): Boolean {
        val removed = stored.removeAll { it.id == id }
        if (removed) startCount--
        return removed
    }
}
