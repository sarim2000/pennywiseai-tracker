package com.pennywiseai.tracker.domain.usecase

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TagRepository
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDateTime
import javax.inject.Inject

class AddTransactionUseCase @Inject constructor(
    private val database: PennyWiseDatabase,
    private val subscriptionRepository: SubscriptionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val tagRepository: TagRepository
) {
    suspend fun execute(
        amount: BigDecimal,
        merchant: String,
        category: String,
        type: TransactionType,
        date: LocalDateTime,
        notes: String? = null,
        tags: List<String> = emptyList(),
        isRecurring: Boolean = false,
        bankName: String? = null,
        accountLast4: String? = null,
        currency: String = "INR",
        receiptPath: String? = null,
        budgetCategory: String? = null,
        budgetImpactType: BudgetImpactType? = null
    ) {
        // Generate a unique hash for manual transactions
        val transactionHash = generateManualTransactionHash(
            amount = amount,
            merchant = merchant,
            date = date
        )
        
        // Create the transaction entity
        val transaction = TransactionEntity(
            amount = amount,
            merchantName = merchant,
            category = category,
            transactionType = type,
            dateTime = date,
            description = notes,
            smsBody = null, // null indicates manual entry
            bankName = bankName ?: "Manual Entry",
            smsSender = null, // null indicates manual entry
            accountNumber = accountLast4,
            balanceAfter = null,
            transactionHash = transactionHash,
            isRecurring = isRecurring,
            currency = currency,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            receiptPath = receiptPath,
            budgetCategory = budgetCategory,
            budgetImpactType = budgetImpactType
        )

        // Insert the transaction, its tags, and any recurring subscription as a
        // single atomic unit: if tag-linking (or the subscription insert) fails
        // after the row is written, the whole add rolls back rather than leaving a
        // persisted-but-untagged transaction the user might re-save as a duplicate.
        // Room's withTransaction is reentrant, so insertTransactionWithBalance and
        // setTagsForTransaction (which each open their own) join this outer one.
        database.withTransaction {
            // For manual/cash accounts the helper pins the opening anchor from the
            // PRE-insert snapshot before inserting, so the recompute includes this
            // new transaction. No-op balance side for SMS / no-account adds.
            val transactionId = accountBalanceRepository.insertTransactionWithBalance(
                transaction = transaction,
                bankName = bankName,
                accountLast4 = accountLast4
            )

            // Link tags (create-or-select handled in the repository)
            if (transactionId != -1L && tags.isNotEmpty()) {
                tagRepository.setTagsForTransaction(transactionId, tags)
            }

            // If marked as recurring, create a subscription
            if (isRecurring && transactionId != -1L) {
                val nextPaymentDate = date.toLocalDate().plusMonths(1) // Default to monthly

                val subscription = SubscriptionEntity(
                    merchantName = merchant,
                    amount = amount,
                    nextPaymentDate = nextPaymentDate,
                    state = SubscriptionState.ACTIVE,
                    // Carry the funding account onto the auto-created subscription so
                    // marking it paid later moves that account's balance — otherwise
                    // this whole class of subscriptions silently skips the #570 fix.
                    bankName = bankName ?: "Manual Entry",
                    accountLast4 = accountLast4,
                    category = category,
                    currency = currency,
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )

                subscriptionRepository.insertSubscription(subscription)
            }
        }
    }
    
    /**
     * Manual account-to-account TRANSFER (#622). Records a single TRANSFER
     * transaction — its `fromAccount`/`toAccount` last4s drive the two-leg
     * balance move in [AccountBalanceRepository.insertTransferWithBalance] — and
     * links any tags. Cross-currency transfers are rejected upstream in the
     * ViewModel (we never sum across currencies); this path assumes both legs
     * share [currency].
     */
    suspend fun executeTransfer(
        amount: BigDecimal,
        date: LocalDateTime,
        notes: String? = null,
        tags: List<String> = emptyList(),
        currency: String = "INR",
        fromBankName: String,
        fromLast4: String,
        toBankName: String,
        toLast4: String
    ) {
        // Unique hash incorporating both legs so two distinct transfers (or a
        // transfer and its reverse) never collide on the unique hash index.
        val transactionHash = generateTransferHash(
            amount = amount,
            fromLast4 = fromLast4,
            toLast4 = toLast4,
            date = date
        )

        val transaction = TransactionEntity(
            amount = amount,
            merchantName = "Transfer",
            category = "Others",
            transactionType = TransactionType.TRANSFER,
            dateTime = date,
            description = notes,
            smsBody = null, // null indicates manual entry
            bankName = fromBankName,
            smsSender = null,
            accountNumber = fromLast4,
            balanceAfter = null,
            transactionHash = transactionHash,
            currency = currency,
            fromAccount = fromLast4,
            toAccount = toLast4,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        // Insert + both-leg balance move + tag-linking as one atomic unit.
        // Room's withTransaction is reentrant, so the repository's own
        // withTransaction and setTagsForTransaction join this outer one.
        database.withTransaction {
            val transactionId = accountBalanceRepository.insertTransferWithBalance(
                transaction = transaction,
                fromBankName = fromBankName,
                fromLast4 = fromLast4,
                toBankName = toBankName,
                toLast4 = toLast4
            )

            if (transactionId != -1L && tags.isNotEmpty()) {
                tagRepository.setTagsForTransaction(transactionId, tags)
            }
        }
    }

    private fun generateManualTransactionHash(
        amount: BigDecimal,
        merchant: String,
        date: LocalDateTime
    ): String {
        // Create a unique hash for manual transactions
        // Format: MANUAL_<amount>_<merchant>_<datetime>
        val data = "MANUAL_${amount}_${merchant}_${date}"

        return MessageDigest.getInstance("MD5")
            .digest(data.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun generateTransferHash(
        amount: BigDecimal,
        fromLast4: String,
        toLast4: String,
        date: LocalDateTime
    ): String {
        // Format: TRANSFER_<amount>_<from>_<to>_<datetime>
        val data = "TRANSFER_${amount}_${fromLast4}_${toLast4}_${date}"

        return MessageDigest.getInstance("MD5")
            .digest(data.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}