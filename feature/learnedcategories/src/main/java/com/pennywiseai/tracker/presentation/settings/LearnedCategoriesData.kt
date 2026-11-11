package com.pennywiseai.tracker.presentation.settings

import kotlinx.coroutines.flow.Flow

/** What the learned-categories screen needs from the app's data layer. Implemented in `di/`. */
interface LearnedCategoriesData {
    /** Every learned merchant → category pair, in the repository's order. */
    fun observeLearnedCategories(): Flow<List<LearnedCategory>>

    /** Forgets the pair for exactly this merchant name. */
    suspend fun removeLearnedCategory(merchantName: String)
}

/** One learned pair as the screen shows it. */
data class LearnedCategory(
    val merchantName: String,
    val category: String
)
