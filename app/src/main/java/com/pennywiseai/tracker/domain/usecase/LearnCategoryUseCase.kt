package com.pennywiseai.tracker.domain.usecase

import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames
import com.pennywiseai.tracker.data.repository.MerchantMappingRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A category picked from a transaction alert is a lesson, not a one-off: the
 * merchant is remembered so its next SMS is filed on arrival, and its earlier
 * transactions of the same kind still sitting in "Others" move along.
 * Transactions the user already filed elsewhere are left alone, and so are
 * loan-linked and split rows. [undo] reverses the latest lesson.
 *
 * A parser placeholder ("UPI Payment", "ATM Withdrawal") names a channel, not
 * a place: picking for one files only that row.
 *
 * A row the user deliberately set to "Others" can't be told apart from one
 * that landed there, so it moves too; [undo] is the way back.
 */
@Singleton
class LearnCategoryUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val merchantMappingRepository: MerchantMappingRepository,
    private val database: PennyWiseDatabase
) {
    // Two quick picks (or a pick and an undo) for one merchant must not
    // interleave their read-then-write of the mapping.
    private val lock = Mutex()

    /** What one pick changed, so [undo] can put it back. */
    data class Learned(
        val transactionId: Long,
        val category: String,
        val previousCategory: String,
        /** The merchant now mapped to [category]; null when nothing was learned. */
        val merchant: String? = null,
        val previousMapping: String? = null,
        /** When this lesson wrote the mapping: [undo] acts only while it is still this one. */
        val mappedAt: LocalDateTime? = null,
        /** The earlier rows that moved from "Others" to [category]. */
        val movedIds: List<Long> = emptyList()
    )

    /**
     * Files the transaction under [category] and, when there is a merchant to
     * teach, learns it. Nothing is learned from a placeholder merchant or from
     * a pick of "Others". Null when the transaction is gone.
     */
    suspend operator fun invoke(transactionId: Long, category: String): Learned? = lock.withLock {
        database.withTransaction {
            val transaction = transactionRepository.getTransactionById(transactionId)
                ?.takeUnless { it.isDeleted } ?: return@withTransaction null
            transactionRepository.updateCategory(transaction.id, category)
            val filed = Learned(transaction.id, category, transaction.category)
            val others = BuiltinCategoryNames.current("Others")
            val merchant = transaction.merchantName
            if (category == others || isPlaceholderMerchant(merchant)) return@withTransaction filed

            val previousMapping = merchantMappingRepository.getCategoryForMerchant(merchant)
            val mappedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)
            merchantMappingRepository.setMapping(merchant, category, mappedAt)
            val ids = transactionRepository.getMovableMerchantRowIds(merchant, others, sameKind(transaction.transactionType))
            transactionRepository.moveCategory(ids, from = others, to = category)
            filed.copy(merchant = merchant, previousMapping = previousMapping, mappedAt = mappedAt, movedIds = ids)
        }
    }

    /**
     * Reverses [learned] if it is still the merchant's latest lesson (the
     * stored mapping is the one it wrote); otherwise does nothing, so undoing
     * an older alert can neither erase a newer pick nor bring an undone one
     * back. Rows go back only while they are still in its category, so a later
     * edit stands. Returns whether anything was undone.
     */
    suspend fun undo(learned: Learned): Boolean = lock.withLock {
        database.withTransaction {
            val merchant = learned.merchant
            if (merchant != null) {
                val current = merchantMappingRepository.getMapping(merchant)
                if (current == null || current.category != learned.category || current.updatedAt != learned.mappedAt) {
                    return@withTransaction false
                }
                if (learned.previousMapping == null) {
                    merchantMappingRepository.removeMapping(merchant)
                } else {
                    merchantMappingRepository.setMapping(merchant, learned.previousMapping)
                }
                transactionRepository.moveCategory(learned.movedIds, from = learned.category, to = BuiltinCategoryNames.current("Others"))
            }
            transactionRepository.moveCategory(listOf(learned.transactionId), from = learned.category, to = learned.previousCategory)
            true
        }
    }

    internal companion object {
        /**
         * Names parsers give when an SMS has no merchant: the channel, not a
         * place ("UPI Payment", "ATM - MG Road", "NEFT Transfer"). parser-core
         * has no shared constant for these, so they are matched by shape.
         */
        private val PLACEHOLDER_NAMES = setOf("unknown merchant", "transfer", "transaction alert")
        private val ATM = Regex("""\batm\b""")
        private val CHANNEL = Regex(
            """(upi|neft|imps|rtgs|ibft|raast|tips|npsb|visa|pos|fund|bank|account|cash|wallet|mobile money|mobile banking)""" +
                """( (transfer|credit|debit|transaction|payment|withdrawal|deposit|load))?|transfer via .*"""
        )

        fun isPlaceholderMerchant(name: String): Boolean {
            val n = name.trim().lowercase()
            return n.isEmpty() || n in PLACEHOLDER_NAMES || ATM.containsMatchIn(n) || CHANNEL.matches(n)
        }

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
