package com.pennywiseai.tracker.domain.usecase

import com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames
import com.pennywiseai.tracker.data.repository.MerchantMappingRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A category picked from a transaction alert is a lesson, not a one-off: the
 * merchant is remembered so its next SMS is filed on arrival, and its earlier
 * transactions still sitting in "Others" move along. Merchant-wide, like the
 * detail screen's "apply to all from this merchant"; transactions the user
 * already filed elsewhere are left alone.
 */
@Singleton
class LearnCategoryUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val merchantMappingRepository: MerchantMappingRepository
) {
    /** @return how many earlier transactions from the merchant moved too. */
    suspend operator fun invoke(transactionId: Long, category: String): Int {
        val transaction = transactionRepository.getTransactionById(transactionId) ?: return 0
        transactionRepository.updateCategory(transaction.id, category)
        val merchant = transaction.merchantName.takeIf { it.isNotBlank() } ?: return 0
        merchantMappingRepository.setMapping(merchant, category)
        return transactionRepository.recategorizeMerchant(
            merchant,
            from = BuiltinCategoryNames.current("Others"),
            to = category
        )
    }
}
