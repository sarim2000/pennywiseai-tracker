package com.pennywiseai.tracker.domain.usecase

import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames
import com.pennywiseai.tracker.data.repository.MerchantMappingRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A category picked from a transaction alert is a lesson, not a one-off: the
 * merchant is remembered so its next SMS is filed on arrival, and its earlier
 * transactions of the same kind still sitting in "Others" move along.
 * Transactions the user already filed elsewhere are left alone, and so are
 * loan-linked and split rows. [undo] reverses all of it.
 *
 * A row the user deliberately set to "Others" can't be told apart from one
 * that landed there, so it moves too; [undo] is the way back.
 */
@Singleton
class LearnCategoryUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val merchantMappingRepository: MerchantMappingRepository
) {
    /** What one pick changed, so [undo] can put it back. */
    data class Learned(
        val transactionId: Long,
        val category: String,
        val previousCategory: String,
        /** The merchant now mapped to [category]; null when nothing was learned. */
        val merchant: String? = null,
        val previousMapping: String? = null,
        /** When this lesson wrote the mapping: [undo] reverts it only while it is still this one. */
        val mappedAt: LocalDateTime? = null,
        /** The earlier rows that moved from "Others" to [category]. */
        val movedIds: List<Long> = emptyList()
    )

    /**
     * Files the transaction under [category] and, when there is a merchant to
     * teach, learns it. Nothing is learned from the parser's placeholder
     * merchant or from a pick of "Others". Null when the transaction is gone.
     */
    suspend operator fun invoke(transactionId: Long, category: String): Learned? {
        val transaction = transactionRepository.getTransactionById(transactionId)
            ?.takeUnless { it.isDeleted } ?: return null
        transactionRepository.updateCategory(transaction.id, category)
        val filed = Learned(transaction.id, category, transaction.category)
        val others = BuiltinCategoryNames.current("Others")
        val merchant = transaction.merchantName
        if (category == others || merchant.isBlank() || merchant == UNKNOWN_MERCHANT) return filed

        val previousMapping = merchantMappingRepository.getCategoryForMerchant(merchant)
        val mappedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)
        merchantMappingRepository.setMapping(merchant, category, mappedAt)
        val ids = transactionRepository.getMovableMerchantRowIds(merchant, others, sameKind(transaction.transactionType))
        transactionRepository.moveCategory(ids, from = others, to = category)
        return filed.copy(merchant = merchant, previousMapping = previousMapping, mappedAt = mappedAt, movedIds = ids)
    }

    /**
     * Puts back what [learned] changed and still owns: the transaction and the
     * moved rows only while they are still in its category, and the mapping
     * only while it is still the one it wrote. So undoing an older alert never
     * erases a newer pick for the same merchant or a later edit.
     */
    suspend fun undo(learned: Learned) {
        transactionRepository.moveCategory(listOf(learned.transactionId), from = learned.category, to = learned.previousCategory)
        val merchant = learned.merchant ?: return
        val current = merchantMappingRepository.getMapping(merchant)
        if (current != null && current.category == learned.category && current.updatedAt == learned.mappedAt) {
            if (learned.previousMapping == null) {
                merchantMappingRepository.removeMapping(merchant)
            } else {
                merchantMappingRepository.setMapping(merchant, learned.previousMapping)
            }
        }
        transactionRepository.moveCategory(learned.movedIds, from = learned.category, to = BuiltinCategoryNames.current("Others"))
    }

    internal companion object {
        /** ParsedTransactionMapper's name for an SMS with no merchant: not one place. */
        const val UNKNOWN_MERCHANT = "Unknown Merchant"

        /**
         * The rows a pick speaks for: money in, purchases (bank or card), or
         * exactly this type for transfers and investments.
         */
        fun sameKind(type: TransactionType): Set<TransactionType> = when (type) {
            TransactionType.EXPENSE, TransactionType.CREDIT -> setOf(TransactionType.EXPENSE, TransactionType.CREDIT)
            else -> setOf(type)
        }
    }
}
