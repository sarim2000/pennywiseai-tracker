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

    override fun observeCategories(): Flow<List<SharedCategory>> = flowOf(emptyList())

    override fun observeCategoriesByIncomeType(isIncome: Boolean): Flow<List<SharedCategory>> =
        flowOf(emptyList())

    override suspend fun countCategories(): Int = startCount

    override suspend fun insertCategories(categories: List<SharedCategory>) {
        insertedCategoryCount += categories.size
        startCount += categories.size
    }
}
