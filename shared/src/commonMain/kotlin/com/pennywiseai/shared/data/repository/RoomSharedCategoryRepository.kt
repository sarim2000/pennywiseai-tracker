package com.pennywiseai.shared.data.repository

import com.pennywiseai.shared.data.local.dao.SharedCategoryDao
import com.pennywiseai.shared.data.model.SharedCategory
import com.pennywiseai.shared.data.repository.mapper.toDomain
import com.pennywiseai.shared.data.repository.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSharedCategoryRepository(
    private val dao: SharedCategoryDao
) : SharedCategoryRepository {
    override fun observeCategories(): Flow<List<SharedCategory>> =
        dao.observeCategories().map { list -> list.map { it.toDomain() } }

    override fun observeCategoriesByIncomeType(isIncome: Boolean): Flow<List<SharedCategory>> =
        dao.observeCategoriesByIncomeType(isIncome).map { list -> list.map { it.toDomain() } }

    override suspend fun countCategories(): Int =
        dao.countCategories()

    override suspend fun insertCategories(categories: List<SharedCategory>) {
        dao.insertAll(categories.map { it.toEntity() })
    }
}
