package com.pennywiseai.tracker.domain.usecase

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TagRepository
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDateTime
import javax.inject.Inject

class AddTransactionUseCase @Inject constructor(
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

        // Insert the transaction and reflect it on the selected account's balance
        // atomically. For manual/cash accounts the helper pins the opening anchor
        // from the PRE-insert snapshot before inserting, so the recompute includes
        // this new transaction. No-op balance side for SMS / no-account adds.
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
}