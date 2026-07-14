package com.pennywiseai.tracker.data.manager

import android.content.Context
import android.util.Log
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.bank.BankParserFactory
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.CardType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.mapper.toEntity
import com.pennywiseai.tracker.data.mapper.toEntityType
import com.pennywiseai.tracker.utils.BalanceCalculator
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.CardRepository
import com.pennywiseai.tracker.data.repository.MerchantMappingRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.domain.service.RuleEngine
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared processor for SMS transactions. Used by both SmsBroadcastReceiver
 * and OptimizedSmsReaderWorker to ensure consistent transaction processing.
 */
@Singleton
class SmsTransactionProcessor @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val transactionRepository: TransactionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val cardRepository: CardRepository,
    private val merchantMappingRepository: MerchantMappingRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val ruleRepository: RuleRepository,
    private val ruleEngine: RuleEngine
) {
    companion object {
        private const val TAG = "SmsTransactionProcessor"
    }

    /**
     * Result of processing an SMS message
     */
    data class ProcessingResult(
        val success: Boolean,
        val transactionId: Long? = null,
        val reason: String? = null
    )

    /**
     * Parses and saves a transaction from an SMS message.
     *
     * @param sender SMS sender address
     * @param body SMS body text
     * @param timestamp SMS timestamp in milliseconds
     * @return ProcessingResult indicating success/failure and transaction ID
     */
    suspend fun processAndSaveTransaction(
        sender: String,
        body: String,
        timestamp: Long
    ): ProcessingResult {
        try {
            // Some senders are shared by multiple parsers (e.g. M-Pesa
            // Kenya/Tanzania/Mozambique all use "M-Pesa"), so try every parser
            // that handles this sender and use the first whose content parses.
            val parsers = BankParserFactory.getParsers(sender)
            if (parsers.isEmpty()) {
                return ProcessingResult(false, reason = "No parser found for sender: $sender")
            }

            // Parse the SMS
            val parsedTransaction = parsers.firstNotNullOfOrNull { it.parse(body, sender, timestamp) }
            if (parsedTransaction == null) {
                return ProcessingResult(false, reason = "Could not parse transaction from SMS")
            }

            Log.d(TAG, "Parsed transaction: ${parsedTransaction.amount} from ${parsedTransaction.bankName}")

            // Save the transaction
            return saveParsedTransaction(parsedTransaction, body)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing SMS", e)
            return ProcessingResult(false, reason = e.message)
        }
    }

    /**
     * Saves a parsed transaction to the database with all necessary processing:
     * - Duplicate detection
     * - Merchant mapping
     * - Rule application
     * - Subscription matching
     * - Balance updates
     */
    suspend fun saveParsedTransaction(
        parsedTransaction: ParsedTransaction,
        smsBody: String
    ): ProcessingResult {
        return try {
            // Convert to entity
            val entity = parsedTransaction.toEntity()

            // Check if this transaction was previously deleted by the user
            val existingTransaction = transactionRepository.getTransactionByHash(entity.transactionHash)
            if (existingTransaction != null) {
                if (existingTransaction.isDeleted) {
                    Log.d(TAG, "Skipping previously deleted transaction with hash: ${entity.transactionHash}")
                    return ProcessingResult(false, reason = "Transaction was previously deleted")
                }
                // Transaction already exists and not deleted - normal deduplication
                Log.d(TAG, "Transaction already exists: ${entity.transactionHash}")
                return ProcessingResult(false, reason = "Duplicate transaction")
            }

            // Check for custom merchant mapping
            val customCategory = merchantMappingRepository.getCategoryForMerchant(entity.merchantName)
            val entityWithMapping = if (customCategory != null) {
                Log.d(TAG, "Found custom category mapping: ${entity.merchantName} -> $customCategory")
                entity.copy(category = customCategory)
            } else {
                entity
            }

            // Apply rule engine to the transaction
            val activeRules = ruleRepository.getActiveRulesByType(entityWithMapping.transactionType)

            // Check if this transaction should be blocked
            val blockingRule = ruleEngine.shouldBlockTransaction(
                entityWithMapping,
                smsBody,
                activeRules
            )

            if (blockingRule != null) {
                Log.d(TAG, "Transaction blocked by rule: ${blockingRule.name}")
                return ProcessingResult(false, reason = "Blocked by rule: ${blockingRule.name}")
            }

            val (entityWithRules, ruleApplications) = ruleEngine.evaluateRules(
                entityWithMapping,
                smsBody,
                activeRules
            )

            if (ruleApplications.isNotEmpty()) {
                Log.d(TAG, "Applied ${ruleApplications.size} rules to transaction")
            }

            // Check if this transaction matches an active subscription
            val matchedSubscription = subscriptionRepository.matchTransactionToSubscription(
                entityWithRules.merchantName,
                entityWithRules.amount
            )

            val finalEntity = if (matchedSubscription != null) {
                Log.d(TAG, "Transaction matched to active subscription: ${matchedSubscription.merchantName}")
                subscriptionRepository.updateNextPaymentDateAfterCharge(
                    matchedSubscription.id,
                    entityWithRules.dateTime.toLocalDate()
                )
                entityWithRules.copy(isRecurring = true)
            } else {
                entityWithRules
            }

            val rowId = transactionRepository.insertTransaction(finalEntity)
            if (rowId != -1L) {
                Log.d(TAG, "Saved new transaction with ID: $rowId${if (finalEntity.isRecurring) " (Recurring)" else ""}")

                // Save rule applications if any rules were applied
                if (ruleApplications.isNotEmpty()) {
                    ruleRepository.saveRuleApplications(ruleApplications)
                }

                // Process balance updates
                processBalanceUpdate(parsedTransaction, finalEntity, rowId)

                // Trigger widget refresh for recent transactions
                com.pennywiseai.tracker.widget.RecentTransactionsWidgetUpdateWorker.enqueueOneShot(appContext)

                return ProcessingResult(true, transactionId = rowId)
            } else {
                Log.d(TAG, "Transaction already exists (duplicate): ${entity.transactionHash}")
                return ProcessingResult(false, reason = "Duplicate transaction")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving transaction: ${e.message}")
            return ProcessingResult(false, reason = e.message)
        }
    }

    private suspend fun processBalanceUpdate(
        parsedTransaction: ParsedTransaction,
        entity: TransactionEntity,
        rowId: Long
    ) {
        if (parsedTransaction.accountLast4 == null) {
            // Mobile-money wallets (eMola, M-Pesa Mozambique) have no per-account
            // number — the whole wallet is one account. Derive a single service-level
            // account row from the running balance the SMS carries, so the wallet shows
            // up as an account and counts toward the total balance.
            if (parsedTransaction.isMobileWallet && parsedTransaction.balance != null) {
                upsertWalletBalance(parsedTransaction, entity, rowId)
            }
            return
        }

        val isFromCard = parsedTransaction.isFromCard

        val targetAccountLast4: String? = if (isFromCard) {
            var card = parsedTransaction.accountLast4?.let {
                cardRepository.getCard(parsedTransaction.bankName, it)
            }

            if (card == null) {
                val isCredit = (parsedTransaction.type.toEntityType() == TransactionType.CREDIT)
                parsedTransaction.accountLast4?.let { accountLast4 ->
                    cardRepository.findOrCreateCard(
                        cardLast4 = accountLast4,
                        bankName = parsedTransaction.bankName,
                        isCredit = isCredit
                    )
                }
                card = parsedTransaction.accountLast4?.let {
                    cardRepository.getCard(parsedTransaction.bankName, it)
                }
            }

            if (card == null) {
                Log.w(TAG, "Could not create/find card for ${parsedTransaction.bankName}")
                null
            } else {
                // Update card's balance
                cardRepository.updateCardBalance(
                    cardId = card.id,
                    balance = parsedTransaction.balance,
                    source = parsedTransaction.smsBody.take(200),
                    date = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(parsedTransaction.timestamp),
                        ZoneId.systemDefault()
                    )
                )

                when {
                    card.cardType == CardType.CREDIT -> parsedTransaction.accountLast4
                    card.cardType == CardType.DEBIT && card.accountLast4 != null -> card.accountLast4
                    else -> parsedTransaction.accountLast4
                }
            }
        } else {
            parsedTransaction.accountLast4
        }

        if (targetAccountLast4 != null) {
            val isCreditCard = (parsedTransaction.type.toEntityType() == TransactionType.CREDIT) ||
                    parsedTransaction.accountLast4?.let {
                        cardRepository.getCard(parsedTransaction.bankName, it)?.cardType
                    } == CardType.CREDIT

            val existingAccount = accountBalanceRepository.getLatestBalance(
                parsedTransaction.bankName,
                targetAccountLast4
            )

            val resolvedIsCreditCard = isCreditCard || (existingAccount?.isCreditCard ?: false)

            val newBalance = BalanceCalculator.calculateNewBalance(
                explicitBalance = parsedTransaction.balance,
                isCreditCard = resolvedIsCreditCard,
                transactionType = parsedTransaction.type.toEntityType(),
                transactionAmount = parsedTransaction.amount,
                currentBalance = existingAccount?.balance
            )

            // Credit-card SMS report the AVAILABLE limit, not the total. The UI derives
            // available = creditLimit − balance, so store total = available + outstanding
            // (the post-transaction balance) to keep that math correct. Falls back to the
            // existing stored limit when the SMS carries no limit. (#486)
            val resolvedCreditLimit = if (resolvedIsCreditCard && parsedTransaction.creditLimit != null) {
                parsedTransaction.creditLimit!! + newBalance
            } else {
                existingAccount?.creditLimit
            }

            val balanceEntity = AccountBalanceEntity(
                bankName = parsedTransaction.bankName,
                accountLast4 = targetAccountLast4,
                balance = newBalance,
                timestamp = entity.dateTime,
                transactionId = if (rowId != -1L) rowId else null,
                creditLimit = resolvedCreditLimit,
                isCreditCard = resolvedIsCreditCard,
                smsSource = parsedTransaction.smsBody.take(500),
                sourceType = "TRANSACTION",
                currency = parsedTransaction.currency,
                profileId = existingAccount?.profileId ?: ProfileEntity.PERSONAL_ID,
                alias = existingAccount?.alias,
                lowBalanceThreshold = existingAccount?.lowBalanceThreshold
            )

            accountBalanceRepository.insertBalance(balanceEntity)
            Log.d(TAG, "Saved balance update for ${parsedTransaction.bankName} **$targetAccountLast4")
        }
    }

    /**
     * Derives a single service-level account row for a mobile-money wallet
     * (eMola, M-Pesa Mozambique). These SMS carry the running balance but no
     * per-account number, so the account is keyed on bankName with the
     * [AccountBalanceEntity.WALLET_ACCOUNT_MARKER] sentinel and the balance is
     * taken straight from the SMS (never a card, never credit).
     */
    private suspend fun upsertWalletBalance(
        parsedTransaction: ParsedTransaction,
        entity: TransactionEntity,
        rowId: Long
    ) {
        val balance = parsedTransaction.balance ?: return
        val existingAccount = accountBalanceRepository.getLatestBalance(
            parsedTransaction.bankName,
            AccountBalanceEntity.WALLET_ACCOUNT_MARKER
        )

        val balanceEntity = AccountBalanceEntity(
            bankName = parsedTransaction.bankName,
            accountLast4 = AccountBalanceEntity.WALLET_ACCOUNT_MARKER,
            balance = balance,
            timestamp = entity.dateTime,
            transactionId = if (rowId != -1L) rowId else null,
            creditLimit = null,
            isCreditCard = false,
            smsSource = parsedTransaction.smsBody.take(500),
            sourceType = "TRANSACTION",
            currency = parsedTransaction.currency,
            profileId = existingAccount?.profileId ?: ProfileEntity.PERSONAL_ID,
            alias = existingAccount?.alias,
            lowBalanceThreshold = existingAccount?.lowBalanceThreshold
        )

        accountBalanceRepository.insertBalance(balanceEntity)
        Log.d(TAG, "Saved wallet balance for ${parsedTransaction.bankName}")
    }
}
