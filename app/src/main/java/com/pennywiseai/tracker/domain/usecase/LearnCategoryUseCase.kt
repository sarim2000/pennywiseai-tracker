package com.pennywiseai.tracker.domain.usecase

import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames
import com.pennywiseai.tracker.data.repository.MerchantMappingRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A category picked from a transaction alert is a lesson, not a one-off: the
 * merchant is remembered so its next SMS is filed on arrival, and its earlier
 * transactions of the same kind (money in vs out) still sitting in "Others"
 * move along. Transactions the user already filed elsewhere are left alone.
 */
@Singleton
class LearnCategoryUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val merchantMappingRepository: MerchantMappingRepository
) {
    /**
     * @return how many earlier transactions moved too, or null when nothing was
     * learned: the transaction is gone, the merchant is the parser's placeholder,
     * or the pick is "Others" itself.
     */
    suspend operator fun invoke(transactionId: Long, category: String): Int? {
        val transaction = transactionRepository.getTransactionById(transactionId)
            ?.takeUnless { it.isDeleted } ?: return null
        transactionRepository.updateCategory(transaction.id, category)
        val others = BuiltinCategoryNames.current("Others")
        val merchant = transaction.merchantName
        if (category == others || merchant.isBlank() || merchant == UNKNOWN_MERCHANT) return null
        merchantMappingRepository.setMapping(merchant, category)
        return transactionRepository.recategorizeMerchant(
            merchant,
            isIncome = transaction.transactionType == TransactionType.INCOME,
            from = others,
            to = category
        )
    }

    private companion object {
        /** ParsedTransactionMapper's name for an SMS with no merchant: not one place. */
        const val UNKNOWN_MERCHANT = "Unknown Merchant"
    }
}
