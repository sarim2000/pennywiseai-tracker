package com.pennywiseai.tracker.di

import com.pennywiseai.tracker.data.repository.MerchantMappingRepository
import com.pennywiseai.tracker.presentation.settings.LearnedCategoriesData
import com.pennywiseai.tracker.presentation.settings.LearnedCategory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AppLearnedCategoriesData @Inject constructor(
    private val repository: MerchantMappingRepository
) : LearnedCategoriesData {

    override fun observeLearnedCategories(): Flow<List<LearnedCategory>> =
        repository.getAllMappings().map { mappings ->
            mappings.map { LearnedCategory(merchantName = it.merchantName, category = it.category) }
        }

    override suspend fun removeLearnedCategory(merchantName: String) =
        repository.removeMapping(merchantName)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LearnedCategoriesModule {

    @Binds
    abstract fun bindLearnedCategoriesData(impl: AppLearnedCategoriesData): LearnedCategoriesData
}
