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
import com.pennywiseai.tracker.data.repository.TransactionRepository
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDateTime
import javax.inject.Inject

class AddTransactionUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
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

        // For manual/cash accounts, pin the opening anchor from the PRE-insert snapshot
        // (balance − Σtxns) so the post-insert recompute reflects this new transaction.
        // Establishing it after the insert would fold the new txn into the opening and
        // the add would silently not move the balance. No-op for SMS accounts.
        if (bankName != null && accountLast4 != null) {
            accountBalanceRepository.ensureManualOpening(bankName, accountLast4)
        }

        // Insert the transaction
        val transactionId = transactionRepository.insertTransaction(transaction)

        // Link tags (create-or-select handled in the repository)
        if (transactionId != -1L && tags.isNotEmpty()) {
            tagRepository.setTagsForTransaction(transactionId, tags)
        }

        // Update account balance if account was selected
        if (transactionId != -1L && bankName != null && accountLast4 != null) {
            updateAccountBalance(
                bankName = bankName,
                accountLast4 = accountLast4,
                amount = amount,
                type = type,
                date = date,
                transactionId = transactionId
            )
        }
        
        // If marked as recurring, create a subscription
        if (isRecurring && transactionId != -1L) {
            val nextPaymentDate = date.toLocalDate().plusMonths(1) // Default to monthly
            
            val subscription = SubscriptionEntity(
                merchantName = merchant,
                amount = amount,
                nextPaymentDate = nextPaymentDate,
                state = SubscriptionState.ACTIVE,
                bankName = "Manual Entry",
                category = category,
                currency = currency,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
            
            subscriptionRepository.insertSubscription(subscription)
        }
    }
    
    private suspend fun updateAccountBalance(
        bankName: String,
        accountLast4: String,
        amount: BigDecimal,
        type: TransactionType,
        date: LocalDateTime,
        transactionId: Long
    ) {
        // Manual/cash accounts derive their balance from their transactions, so a
        // back-dated (or later edited) transaction must be reflected — recompute rather
        // than writing a per-transaction delta snapshot stamped at the txn date (which a
        // back-dated row hides from "latest"). The transaction is already persisted here,
        // so the recompute sum includes it. (#469)
        if (accountBalanceRepository.isManualAccount(bankName, accountLast4)) {
            accountBalanceRepository.recomputeManualBalance(bankName, accountLast4)
            return
        }

        // Get current account balance
        val currentAccount = accountBalanceRepository.getLatestBalance(bankName, accountLast4)

        if (currentAccount != null) {
            // Calculate new balance based on transaction type
            val newBalance = when (type) {
                TransactionType.INCOME -> currentAccount.balance + amount
                TransactionType.EXPENSE, TransactionType.CREDIT -> currentAccount.balance - amount
                TransactionType.TRANSFER -> currentAccount.balance - amount  // Simplified - from account
                TransactionType.INVESTMENT -> currentAccount.balance - amount
            }

            // Insert new balance record
            accountBalanceRepository.insertBalance(
                currentAccount.copy(
                    id = 0,  // Auto-generate new ID
                    balance = newBalance,
                    timestamp = date,
                    transactionId = transactionId,
                    sourceType = "TRANSACTION",
                    smsSource = null
                )
            )
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