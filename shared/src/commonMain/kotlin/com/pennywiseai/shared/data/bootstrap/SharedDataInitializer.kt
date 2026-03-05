package com.pennywiseai.shared.data.bootstrap

import com.pennywiseai.shared.data.repository.SharedCategoryRepository
import com.pennywiseai.shared.data.util.currentTimeMillis

class SharedDataInitializer(
    private val categoryRepository: SharedCategoryRepository
) {
    suspend fun seedDefaultCategoriesIfNeeded() {
        if (categoryRepository.countCategories() > 0) return
        categoryRepository.insertCategories(
            DefaultSharedCategories.create(
                nowEpochMillis = currentTimeMillis()
            )
        )
    }
}
