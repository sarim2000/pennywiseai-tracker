package com.pennywiseai.tracker.worker

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.parser.core.bank.BankParserFactory
import com.pennywiseai.parser.core.bank.HDFCBankParser
import com.pennywiseai.parser.core.bank.IndianBankParser
import com.pennywiseai.parser.core.bank.IndusIndBankParser
import com.pennywiseai.parser.core.bank.SBIBankParser
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.tracker.data.mapper.toEntity
import com.pennywiseai.tracker.data.mapper.toEntityType
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.CardRepository
import com.pennywiseai.tracker.data.repository.LlmRepository
import com.pennywiseai.tracker.data.repository.MerchantMappingRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.data.repository.UnrecognizedSmsRepository
import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.domain.service.RuleEngine
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.UnrecognizedSmsEntity
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Worker responsible for reading SMS messages and logging them.
 * This is the first step in our SMS scanning pipeline.
 */
@HiltWorker
class SmsReaderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val transactionRepository: TransactionRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val cardRepository: CardRepository,
    private val llmRepository: LlmRepository,
    private val merchantMappingRepository: MerchantMappingRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val unrecognizedSmsRepository: UnrecognizedSmsRepository,
    private val ruleRepository: RuleRepository,
    private val ruleEngine: RuleEngine
) : CoroutineWorker(appContext, workerParams) {
    
    companion object {
        const val TAG = "SmsReaderWorker"
        const val WORK_NAME = "sms_reader_work"
        
        // SMS Content Provider columns
        private val SMS_PROJECTION = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE,
            Telephony.Sms.BODY,
            Telephony.Sms.TYPE
        )
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting SMS reading and parsing work...")
            
            // Read SMS messages
            val messages = readSmsMessages()
            Log.d(TAG, "Found ${messages.size} SMS messages")
            
            var parsedCount = 0
            var savedCount = 0
            var subscriptionCount = 0
            
            // Process all messages from the scan period
            for (sms in messages) {
                // Skip promotional (-P) and government (-G) messages
                val senderUpper = sms.sender.uppercase()
                if (senderUpper.endsWith("-P") || senderUpper.endsWith("-G")) {
                    Log.d(TAG, "Skipping promotional/government SMS from: ${sms.sender}")
                    continue
                }
                
                // Check if sender is from a known bank
                val parser = BankParserFactory.getParser(sms.sender)
                if (parser != null) {
                    Log.d(TAG, "Processing SMS from ${parser.getBankName()}")
                    
                    // Calculate SMS age for subscription filtering
                    val smsDateTime = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(sms.timestamp),
                        ZoneId.systemDefault()
                    )
                    val thirtyDaysAgo = LocalDateTime.now().minusDays(30)
                    val isRecentMessage = smsDateTime.isAfter(thirtyDaysAgo)
                    
                    // Check if it's a mandate/subscription notification
                    // Only process subscription messages from the last 30 days
                    when (parser) {
                        is SBIBankParser -> {
                            // Check for UPI-Mandate notifications
                            if (parser.isUPIMandateNotification(sms.body)) {
                                if (!isRecentMessage) {
                                    Log.d(TAG, "Skipping old SBI UPI-Mandate from ${smsDateTime.toLocalDate()}")
                                    continue
                                }
                                val upiMandateInfo = parser.parseUPIMandateSubscription(sms.body)
                                if (upiMandateInfo != null) {
                                    try {
                                        val subscriptionId = subscriptionRepository.createOrUpdateFromSBIMandate(
                                            upiMandateInfo,
                                            parser.getBankName(),
                                            sms.body
                                        )
                                        subscriptionCount++
                                        Log.d(TAG, "Created/Updated SBI UPI-Mandate subscription: $subscriptionId for ${upiMandateInfo.merchant}")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error saving SBI UPI-Mandate subscription: ${e.message}")
                                    }
                                }
                                continue // Skip transaction parsing for UPI-Mandate
                            }
                        }
                        is HDFCBankParser -> {
                            // Check for E-Mandate notifications
                            if (parser.isEMandateNotification(sms.body)) {
                                if (!isRecentMessage) {
                                    Log.d(TAG, "Skipping old HDFC E-Mandate from ${smsDateTime.toLocalDate()}")
                                    continue
                                }
                                val eMandateInfo = parser.parseEMandateSubscription(sms.body)
                                if (eMandateInfo != null) {
                                    try {
                                        val subscriptionId = subscriptionRepository.createOrUpdateFromEMandate(
                                            eMandateInfo,
                                            parser.getBankName(),
                                            sms.body
                                        )
                                        subscriptionCount++
                                        Log.d(TAG, "Created/Updated HDFC E-Mandate subscription: $subscriptionId for ${eMandateInfo.merchant}")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error saving HDFC E-Mandate subscription: ${e.message}")
                                    }
                                }
                                continue // Skip transaction parsing for E-Mandate
                            }
                            
                            // Check for Future Debit notifications (like Twitter subscription)
                            if (parser.isFutureDebitNotification(sms.body)) {
                                if (!isRecentMessage) {
                                    Log.d(TAG, "Skipping old HDFC Future Debit from ${smsDateTime.toLocalDate()}")
                                    continue
                                }
                                val futureDebitInfo = parser.parseFutureDebit(sms.body)
                                if (futureDebitInfo != null) {
                                    try {
                                        // Use the same EMandateInfo structure for subscription creation
                                        val subscriptionId = subscriptionRepository.createOrUpdateFromEMandate(
                                            futureDebitInfo,
                                            parser.getBankName(),
                                            sms.body
                                        )
                                        subscriptionCount++
                                        Log.d(TAG, "Created/Updated HDFC future debit subscription: $subscriptionId for ${futureDebitInfo.merchant}")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error saving HDFC future debit subscription: ${e.message}")
                                    }
                                }
                                continue // Skip transaction parsing for future debit
                            }
                            
                            // Check for Balance Update notifications
                            if (parser.isBalanceUpdateNotification(sms.body)) {
                                val balanceUpdateInfo = parser.parseBalanceUpdate(sms.body)
                                if (balanceUpdateInfo != null) {
                                    try {
                                        // Save to account_balances table
                                        accountBalanceRepository.insertBalanceUpdate(
                                            bankName = balanceUpdateInfo.bankName,
                                            accountLast4 = balanceUpdateInfo.accountLast4 ?: "XXXX",
                                            balance = balanceUpdateInfo.balance,
                                            timestamp = balanceUpdateInfo.asOfDate ?: smsDateTime,
                                            currency = parser.getCurrency()
                                        )
                                        Log.d(TAG, "Saved balance update for ${balanceUpdateInfo.bankName}")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error saving balance update: ${e.message}")
                                    }
                                }
                                continue // Skip transaction parsing
                            }
                        }
                        is IndusIndBankParser -> {
                            // Balance-only updates for IndusInd (same flow as HDFC)
                            if (parser.isBalanceUpdateNotification(sms.body)) {
                                val balanceUpdateInfo = parser.parseBalanceUpdate(sms.body)
                                if (balanceUpdateInfo != null) {
                                    try {
                                        accountBalanceRepository.insertBalanceUpdate(
                                            bankName = balanceUpdateInfo.bankName,
                                            accountLast4 = balanceUpdateInfo.accountLast4 ?: "XXXX",
                                            balance = balanceUpdateInfo.balance,
                                            timestamp = balanceUpdateInfo.asOfDate ?: smsDateTime,
                                            currency = parser.getCurrency()
                                        )
                                        Log.d(TAG, "Saved balance update for ${balanceUpdateInfo.bankName}")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error saving IndusInd balance update: ${e.message}")
                                    }
                                }
                                continue // Skip transaction parsing
                            }
                        }
                        is IndianBankParser -> {
                            if (parser.isMandateNotification(sms.body)) {
                                if (!isRecentMessage) {
                                    Log.d(TAG, "Skipping old Indian Bank Mandate from ${smsDateTime.toLocalDate()}")
                                    continue
                                }
                                val mandateInfo = parser.parseMandateSubscription(sms.body)
                                if (mandateInfo != null) {
                                    try {
                                        val subscriptionId = subscriptionRepository.createOrUpdateFromIndianBankMandate(
                                            mandateInfo,
                                            parser.getBankName(),
                                            sms.body
                                        )
                                        subscriptionCount++
                                        Log.d(TAG, "Created/Updated Indian Bank subscription: $subscriptionId for ${mandateInfo.merchant}")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error saving Indian Bank subscription: ${e.message}")
                                    }
                                }
                                continue // Skip transaction parsing for mandate
                            }
                        }
                    }
                    
                    // Parse the transaction
                    val parsedTransaction = parser.parse(sms.body, sms.sender, sms.timestamp)
                    
                    if (parsedTransaction != null) {
                        parsedCount++
                        Log.d(TAG, """
                            Parsed Transaction:
                            Bank: ${parsedTransaction.bankName}
                            Amount: ${parsedTransaction.amount}
                            Type: ${parsedTransaction.type}
                            Merchant: ${parsedTransaction.merchant}
                            Reference: ${parsedTransaction.reference}
                            Account: ${parsedTransaction.accountLast4}
                            Balance: ${parsedTransaction.balance}
                            Credit Limit: ${parsedTransaction.creditLimit}
                            ID: ${parsedTransaction.generateTransactionId()}
                        """.trimIndent())
                        
                        // Convert to entity and save
                        val entity = parsedTransaction.toEntity()

                        // Check if this transaction was previously deleted by the user
                        val existingTransaction = transactionRepository.getTransactionByHash(entity.transactionHash)
                        if (existingTransaction != null) {
                            if (existingTransaction.isDeleted) {
                                Log.d(TAG, "Skipping previously deleted transaction with hash: ${entity.transactionHash}")
                                continue
                            }
                            // Transaction already exists and not deleted - normal deduplication
                            Log.d(TAG, "Transaction already exists: ${entity.transactionHash}")
                            continue
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
                        // Optimize by getting rules specific to this transaction type
                        val activeRules = ruleRepository.getActiveRulesByType(entityWithMapping.transactionType)
                        val (entityWithRules, ruleApplications) = ruleEngine.evaluateRules(
                            entityWithMapping,
                            sms.body,
                            activeRules
                        )

                        if (ruleApplications.isNotEmpty()) {
                            Log.d(TAG, "Applied ${ruleApplications.size} rules to transaction")
                            ruleApplications.forEach { app ->
                                Log.d(TAG, "Applied rule: ${app.ruleName}")
                            }
                        }

                        try {
                            // Check if this transaction matches an active subscription
                            val matchedSubscription = subscriptionRepository.matchTransactionToSubscription(
                                entityWithRules.merchantName,
                                entityWithRules.amount
                            )

                            // If matched to active subscription, update the entity to mark as recurring
                            val finalEntity = if (matchedSubscription != null) {
                                Log.d(TAG, "Transaction matched to active subscription: ${matchedSubscription.merchantName}")
                                // Update next payment date for the subscription
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
                                savedCount++
                                Log.d(TAG, "Saved new transaction with ID: $rowId${if (finalEntity.isRecurring) " (Recurring)" else ""}")

                                com.pennywiseai.tracker.widget.RecentTransactionsWidgetUpdateWorker.enqueueOneShot(applicationContext)

                                // Save rule applications if any rules were applied
                                if (ruleApplications.isNotEmpty()) {
                                    ruleRepository.saveRuleApplications(ruleApplications)
                                    Log.d(TAG, "Saved ${ruleApplications.size} rule applications for transaction: ${finalEntity.id}")
                                }
                                
                                // Only save balance/credit limit information for NEW transactions (not duplicates)
                                // This prevents incorrect balance accumulation from duplicate SMS messages
                                if (parsedTransaction.accountLast4 != null) {
                                
                                // Determine if this transaction is from a card based on the message pattern
                                val isFromCard = parsedTransaction.isFromCard
                                
                                Log.d(TAG, """
                                    Processing transaction:
                                    - Bank: ${parsedTransaction.bankName}
                                    - Number: **${parsedTransaction.accountLast4}
                                    - Is From Card: $isFromCard
                                    - Transaction Type: ${parsedTransaction.type}
                                    - SMS Body (first 200 chars): ${parsedTransaction.smsBody.take(200)}
                                """.trimIndent())
                                
                                // Handle card vs account logic
                                val targetAccountLast4 = if (isFromCard) {
                                    // This is a card transaction
                                    Log.d(TAG, "Transaction identified as CARD transaction")
                                    
                                    var card = parsedTransaction.accountLast4?.let {
                                        cardRepository.getCard(parsedTransaction.bankName, it)
                                    }
                                    
                                    if (card == null) {
                                        // First time seeing this card - create it
                                        // Determine if it's a credit card based on transaction type
                                        val isCredit = (parsedTransaction.type.toEntityType() == TransactionType.CREDIT)
                                        Log.d(TAG, "Creating new card for ${parsedTransaction.bankName}")
                                        
                                        card = parsedTransaction.accountLast4?.let { accountLast4 ->
                                            cardRepository.findOrCreateCard(
                                                cardLast4 = accountLast4,
                                                bankName = parsedTransaction.bankName,
                                                isCredit = isCredit
                                            )
                                        }
                                        
                                        // CRITICAL: Refetch the card to get the actual state (might have been created before)
                                        card = parsedTransaction.accountLast4?.let {
                                            cardRepository.getCard(parsedTransaction.bankName, it)
                                        }!!
                                        Log.d(TAG, "Card created/found successfully")
                                    } else {
                                        Log.d(TAG, "Found existing card")
                                    }
                                    
                                    // Always update card's balance and source (for debugging)
                                    Log.d(TAG, "Updating card balance")
                                    cardRepository.updateCardBalance(
                                        cardId = card.id,
                                        balance = parsedTransaction.balance,  // Can be null
                                        source = parsedTransaction.smsBody.take(200),  // Always save source
                                        date = LocalDateTime.ofInstant(
                                            Instant.ofEpochMilli(parsedTransaction.timestamp),
                                            ZoneId.systemDefault()
                                        )
                                    )
                                    
                                    // For cards, check if we should create balance entry
                                    val result = when {
                                        card.cardType == com.pennywiseai.tracker.data.database.entity.CardType.CREDIT -> {
                                            // Credit cards get balance entries
                                            Log.d(TAG, "CREDIT card - will create balance entry")
                                            parsedTransaction.accountLast4
                                        }
                                        card.cardType == com.pennywiseai.tracker.data.database.entity.CardType.DEBIT && card.accountLast4 != null -> {
                                            // Linked debit card - use the linked account for balance
                                            Log.d(TAG, "DEBIT card linked to account **${card.accountLast4} - will update linked account balance")
                                            card.accountLast4
                                        }
                                        else -> {
                                            // Unlinked debit card - no balance entry
                                            Log.d(TAG, "DEBIT card NOT linked - NO balance entry will be created")
                                            null
                                        }
                                    }
                                    result
                                } else {
                                    // This is a direct account transaction - always create balance entry
                                    Log.d(TAG, "Transaction identified as ACCOUNT transaction - will create balance entry")
                                    parsedTransaction.accountLast4
                                }
                                
                                // Only create balance entry if we have a target account
                                if (targetAccountLast4 != null) {
                                    // Determine if this is a credit card based on transaction type or card info
                                    val isCreditCard = (parsedTransaction.type.toEntityType() == TransactionType.CREDIT) ||
                                        parsedTransaction.accountLast4?.let {
                                            cardRepository.getCard(parsedTransaction.bankName, it)?.cardType
                                        } == 
                                            com.pennywiseai.tracker.data.database.entity.CardType.CREDIT
                                    
                                    // Get the existing account using the target account
                                    val existingAccount = accountBalanceRepository.getLatestBalance(
                                        parsedTransaction.bankName,
                                        targetAccountLast4
                                    )
                                    
                                    // Calculate new balance based on transaction
                                    val newBalance = when {
                                        // For credit cards: spending increases balance (debt), payments decrease it
                                        isCreditCard -> {
                                            val currentBalance = existingAccount?.balance ?: BigDecimal.ZERO
                                            // Credit card transaction (CREDIT type) = spending, add to outstanding
                                            currentBalance + parsedTransaction.amount
                                        }
                                        // Check if this is a payment TO a credit card (reducing debt)
                                        existingAccount?.isCreditCard == true && parsedTransaction.type.toEntityType() == TransactionType.INCOME -> {
                                            val currentBalance = existingAccount.balance ?: BigDecimal.ZERO
                                            // Payment to credit card, reduce outstanding
                                            (currentBalance - parsedTransaction.amount).max(BigDecimal.ZERO)
                                        }
                                        // For regular accounts, use balance from SMS if available
                                        parsedTransaction.balance != null -> {
                                            parsedTransaction.balance!!
                                        }
                                        // Otherwise keep existing or zero
                                        else -> {
                                            existingAccount?.balance ?: BigDecimal.ZERO
                                        }
                                    }
                                    
                                    Log.d(TAG, """
                                        Saving account balance:
                                        - Bank: ${parsedTransaction.bankName}
                                        - Original: **${parsedTransaction.accountLast4}
                                        - Target Account: **$targetAccountLast4
                                        - Is Card Transaction: ${parsedTransaction.isFromCard}
                                        - Is Credit Card: $isCreditCard
                                        - Transaction Amount: ${parsedTransaction.amount}
                                        - Previous Balance: ${existingAccount?.balance}
                                        - New Balance: $newBalance
                                        - Available Limit (from SMS): ${parsedTransaction.creditLimit}
                                    """.trimIndent())
                                    
                                    // Save balance if:
                                    val shouldSaveBalance = when {
                                        parsedTransaction.balance != null -> true  // Always save if SMS had explicit balance
                                        parsedTransaction.creditLimit != null -> true  // Save if credit limit info
                                        newBalance != BigDecimal.ZERO -> true  // Save non-zero balances
                                        existingAccount != null -> true  // Save to update existing account or create new one
                                        else -> true  // Create new account even with 0 balance (first time)
                                    }
                                    
                                    if (shouldSaveBalance) {
                                        // Create the balance entity using the target account
                                        val balanceEntity = AccountBalanceEntity(
                                            bankName = parsedTransaction.bankName,
                                            accountLast4 = targetAccountLast4,
                                            balance = newBalance,
                                            timestamp = finalEntity.dateTime,
                                            transactionId = if (rowId != -1L) rowId else null,
                                            creditLimit = existingAccount?.creditLimit, // Keep existing credit limit
                                            isCreditCard = isCreditCard || (existingAccount?.isCreditCard ?: false),
                                            smsSource = parsedTransaction.smsBody.take(500),  // Store SMS snippet
                                            sourceType = "TRANSACTION",
                                            currency = parsedTransaction.currency
                                        )
                                        
                                        accountBalanceRepository.insertBalance(balanceEntity)
                                        
                                        val logMsg = if (parsedTransaction.creditLimit != null) {
                                            "Saved balance/credit limit (${CurrencyFormatter.formatCurrency(parsedTransaction.creditLimit!!, parsedTransaction.currency)}) for ${parsedTransaction.bankName} **$targetAccountLast4"
                                        } else {
                                            "Saved balance update for ${parsedTransaction.bankName} **$targetAccountLast4"
                                        }
                                        Log.d(TAG, logMsg)
                                    } else {
                                        Log.d(TAG, "Skipped saving balance: ${parsedTransaction.bankName} **$targetAccountLast4")
                                    }
                                } else {
                                    Log.d(TAG, "No balance entry created for unlinked debit card: ${parsedTransaction.bankName} **${parsedTransaction.accountLast4}")
                                }
                            }
                            } else {
                                Log.d(TAG, "Transaction already exists (duplicate), skipping both transaction and balance update: ${entity.transactionHash}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error saving transaction: ${e.message}")
                        }
                    }
                } else {
                    // Check if it's from a potential financial provider (-T or -S suffix)
                    val upperSender = sms.sender.uppercase()
                    if (upperSender.endsWith("-T") || upperSender.endsWith("-S")) {
                        try {
                            // Check if this message already exists (including soft-deleted ones)
                            val alreadyExists = unrecognizedSmsRepository.exists(sms.sender, sms.body)
                            
                            if (!alreadyExists) {
                                // Store unrecognized SMS for later reporting
                                val unrecognizedSms = UnrecognizedSmsEntity(
                                    sender = sms.sender,
                                    smsBody = sms.body,
                                    receivedAt = LocalDateTime.ofInstant(
                                        Instant.ofEpochMilli(sms.timestamp),
                                        ZoneId.systemDefault()
                                    )
                                )
                                unrecognizedSmsRepository.insert(unrecognizedSms)
                                Log.d(TAG, "Stored unrecognized SMS from potential financial provider: ${sms.sender}")
                            } else {
                                Log.d(TAG, "Skipping duplicate unrecognized SMS from: ${sms.sender}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error storing unrecognized SMS: ${e.message}")
                        }
                    }
                }
            }
            
            // Clean up old unrecognized SMS entries
            try {
                unrecognizedSmsRepository.cleanupOldEntries()
                Log.d(TAG, "Cleaned up old unrecognized SMS entries")
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up unrecognized SMS: ${e.message}")
            }
            
            Log.d(TAG, "SMS parsing completed. Parsed: $parsedCount, Saved: $savedCount, Subscriptions: $subscriptionCount")
            
            // Update system prompt with new financial data if any transactions were saved
            if (savedCount > 0) {
                try {
                    llmRepository.updateSystemPrompt()
                    Log.d(TAG, "Updated system prompt with latest financial data")
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating system prompt: ${e.message}")
                }

                // Update budget widget
                com.pennywiseai.tracker.widget.BudgetWidgetUpdateWorker.enqueueOneShot(applicationContext)
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in SMS parsing work", e)
            Result.failure()
        }
    }
    
    /**
     * Reads SMS messages from the device's SMS content provider.
     * Also attempts to read RCS messages from MMS provider.
     */
    private suspend fun readSmsMessages(): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        
        try {
            // Get scan parameters
            val lastScanTimestamp = userPreferencesRepository.getLastScanTimestamp().first() ?: 0L
            val scanMonths = userPreferencesRepository.getSmsScanMonths()
            val scanAllTime = userPreferencesRepository.getSmsScanAllTime()
            val lastScanPeriod = userPreferencesRepository.getLastScanPeriod().first() ?: 0
            val now = System.currentTimeMillis()

            // Determine if we need a full scan
            val needsFullScan = lastScanTimestamp == 0L || scanAllTime || scanMonths > lastScanPeriod
            
            // Calculate scan start time with 3-day buffer for incremental scans
            val scanStartTime = if (needsFullScan) {
                // Full scan - go back N months or all time
                val calendar = java.util.Calendar.getInstance().apply {
                    if (scanAllTime) {
                        // Scan all time - go back 10 years (effectively all SMS)
                        add(java.util.Calendar.YEAR, -10)
                        Log.d(TAG, "Performing full SMS scan for all time")
                    } else {
                        add(java.util.Calendar.MONTH, -scanMonths)
                        Log.d(TAG, "Performing full SMS scan for last $scanMonths months")
                    }
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                calendar.timeInMillis
            } else {
                // Incremental scan with 3-day buffer
                val threeDaysAgo = now - (3 * 24 * 60 * 60 * 1000L)
                val periodLimit = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.MONTH, -scanMonths)
                }.timeInMillis
                
                // Use the most recent of: 3 days ago, last scan, or period limit
                val incrementalStart = maxOf(
                    minOf(lastScanTimestamp, threeDaysAgo),
                    periodLimit  // Never exceed configured period
                )
                
                val daysSinceLastScan = (now - lastScanTimestamp) / (24 * 60 * 60 * 1000L)
                Log.d(TAG, "Performing incremental SMS scan (last scan: $daysSinceLastScan days ago, buffer: 3 days)")
                incrementalStart
            }
            
            // Query SMS inbox from the scan start time
            val cursor = applicationContext.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                SMS_PROJECTION,
                "${Telephony.Sms.TYPE} = ? AND ${Telephony.Sms.DATE} >= ?",
                arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString(), scanStartTime.toString()),
                "${Telephony.Sms.DATE} DESC"
            )
            
            cursor?.use {
                val idIndex = it.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIndex = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val dateIndex = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val bodyIndex = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val typeIndex = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                
                while (it.moveToNext()) {
                    val message = SmsMessage(
                        id = it.getLong(idIndex),
                        sender = it.getString(addressIndex) ?: "",
                        timestamp = it.getLong(dateIndex),
                        body = it.getString(bodyIndex) ?: "",
                        type = it.getInt(typeIndex)
                    )
                    messages.add(message)
                }
            }
            
            // Update scan tracking after successful processing
            userPreferencesRepository.setLastScanTimestamp(System.currentTimeMillis())
            if (needsFullScan) {
                userPreferencesRepository.setLastScanPeriod(scanMonths)
            }
            
            Log.d(TAG, "SMS scan completed. Processed ${messages.size} messages")
            
            // Try to read RCS messages from MMS provider
            try {
                // MMS/RCS uses seconds since epoch, not milliseconds
                val scanStartTimeSeconds = scanStartTime / 1000
                
                val mmsCursor = applicationContext.contentResolver.query(
                    Uri.parse("content://mms"),
                    arrayOf("_id", "thread_id", "date", "tr_id", "m_id"),
                    "date >= ?",
                    arrayOf(scanStartTimeSeconds.toString()),
                    "date DESC"
                )
                
                mmsCursor?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val messageId = cursor.getLong(cursor.getColumnIndexOrThrow("_id"))
                        val date = cursor.getLong(cursor.getColumnIndexOrThrow("date"))
                        val trIdIndex = cursor.getColumnIndex("tr_id")
                        val trId = if (trIdIndex >= 0) cursor.getString(trIdIndex) ?: "" else ""
                        
                        // Check if this is an RCS message (has proto: in tr_id)
                        if (trId.startsWith("proto:")) {
                            // Extract sender from tr_id (it's base64 encoded protobuf)
                            val sender = extractRcsSender(trId)
                            
                            // Get message text from parts
                            var messageText = getRcsMessageText(messageId)
                            
                            // If it's JSON (RCS Rich Card), extract the actual text
                            if (messageText != null && messageText.trim().startsWith("{")) {
                                messageText = extractTextFromRcsJson(messageText)
                            }
                            
                            // Convert to SmsMessage format for processing
                            if (messageText != null && sender != null) {
                                // Only process RCS messages from PNB to avoid unnecessary processing
                                if (sender.uppercase().contains("PUNJAB NATIONAL BANK")) {
                                    Log.d(TAG, "RCS message from PNB (sender: $sender)")
                                    val rcsMessage = SmsMessage(
                                        id = messageId,
                                        sender = sender,
                                        timestamp = date * 1000, // MMS uses seconds, SMS uses milliseconds
                                        body = messageText,
                                        type = Telephony.Sms.MESSAGE_TYPE_INBOX
                                    )
                                    messages.add(rcsMessage)
                                } else {
                                    Log.d(TAG, "Skipping RCS message from non-PNB sender: $sender")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading RCS messages: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying SMS content provider", e)
        }
        
        return messages
    }
    
    /**
     * Extracts sender name from RCS tr_id field
     * The tr_id contains base64 encoded protobuf data with sender info
     */
    private fun extractRcsSender(trId: String): String? {
        return try {
            // Remove "proto:" prefix and decode base64
            val base64Data = trId.removePrefix("proto:")
            val decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
            val decodedString = String(decodedBytes)
            
            // Look for sender patterns in the decoded data
            // Pattern 1: Agent ID like "ask_apollo_9xdchzx9_agent@rbm.goog"
            val agentPattern = Regex("""([a-z_]+)_[a-z0-9]+_agent@rbm\.goog""")
            agentPattern.find(decodedString)?.let { match ->
                // Convert agent ID to readable name (e.g., "ask_apollo" -> "Ask Apollo")
                return match.groupValues[1].split("_").joinToString(" ") { 
                    it.replaceFirstChar { char -> char.uppercase() }
                }
            }
            
            // Pattern 2: Look for actual sender name in the data
            // RCS messages often have the business name directly in the protobuf
            val namePattern = Regex("""[\x12\x1a][\x00-\x20]([A-Za-z][A-Za-z\s]+)""")
            namePattern.find(decodedString)?.let { match ->
                val name = match.groupValues[1].trim()
                if (name.length > 3 && name.length < 50) {
                    return name
                }
            }
            
            // If no pattern matches, return null
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting RCS sender: ${e.message}")
            null
        }
    }
    
    /**
     * Gets the text content of an RCS/MMS message from its parts
     */
    private fun getRcsMessageText(messageId: Long): String? {
        return try {
            // First, let's see what parts exist for this message
            val partsCursor = applicationContext.contentResolver.query(
                Uri.parse("content://mms/part"),
                null, // Get all columns to debug
                "mid = ?",
                arrayOf(messageId.toString()),
                null
            )
            
            partsCursor?.use { cursor ->
                while (cursor.moveToNext()) {
                    val partId = cursor.getLong(cursor.getColumnIndexOrThrow("_id"))
                    val ctIndex = cursor.getColumnIndex("ct")
                    val contentType = if (ctIndex >= 0) cursor.getString(ctIndex) ?: "" else ""
                    
                    // Look for text content
                    if (contentType.startsWith("text/") || contentType == "application/smil") {
                        // Try to get text directly from the text column
                        val textIndex = cursor.getColumnIndex("text")
                        if (textIndex >= 0) {
                            val text = cursor.getString(textIndex)
                            if (!text.isNullOrEmpty()) {
                                return text
                            }
                        }
                        
                        // Try to read from _data path (file storage)
                        val dataIndex = cursor.getColumnIndex("_data")
                        if (dataIndex >= 0) {
                            val dataPath = cursor.getString(dataIndex)
                            if (!dataPath.isNullOrEmpty()) {
                                // Try to read the file
                                try {
                                    val partUri = Uri.parse("content://mms/part/$partId")
                                    val inputStream = applicationContext.contentResolver.openInputStream(partUri)
                                    val text = inputStream?.bufferedReader()?.use { it.readText() }
                                    if (!text.isNullOrEmpty()) {
                                        return text
                                    }
                                } catch (e: Exception) {
                                    // Ignore read errors
                                }
                            }
                        }
                    }
                }
            }
            
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting RCS message text: ${e.message}", e)
            null
        }
    }
    
    /**
     * Extracts text content from RCS JSON (Rich Cards)
     */
    private fun extractTextFromRcsJson(json: String): String? {
        return try {
            val jsonObject = org.json.JSONObject(json)
            val texts = mutableListOf<String>()
            
            // Navigate through the JSON structure to find text
            fun extractTexts(obj: Any?, depth: Int = 0) {
                if (depth > 10) return // Prevent infinite recursion
                
                when (obj) {
                    is org.json.JSONObject -> {
                        // Priority order for text fields
                        val textFields = listOf(
                            "text",           // Plain text message
                            "message",        // Message body
                            "body",           // Body content
                            "title",          // Card title
                            "description",    // Card description
                            "content",        // Content field
                            "caption"         // Media caption
                        )
                        
                        for (field in textFields) {
                            if (obj.has(field)) {
                                val value = obj.getString(field)
                                if (value.isNotEmpty() && !value.startsWith("{")) {
                                    texts.add(value)
                                }
                            }
                        }
                        
                        // Recursively search nested objects
                        obj.keys().forEach { key ->
                            if (key !in listOf("media", "suggestions", "postback", "urlAction")) {
                                try {
                                    extractTexts(obj.get(key), depth + 1)
                                } catch (e: Exception) {
                                    // Skip problematic fields
                                }
                            }
                        }
                    }
                    is org.json.JSONArray -> {
                        for (i in 0 until obj.length()) {
                            extractTexts(obj.get(i), depth + 1)
                        }
                    }
                }
            }
            
            // Check if it's a simple text message (not a rich card)
            if (jsonObject.has("text")) {
                return jsonObject.getString("text")
            }
            
            // Check for message.text structure
            if (jsonObject.has("message")) {
                val message = jsonObject.getJSONObject("message")
                if (message.has("text")) {
                    return message.getString("text")
                }
            }
            
            // Extract from complex structures
            extractTexts(jsonObject)
            
            // Combine all found texts
            if (texts.isNotEmpty()) {
                return texts.distinct().joinToString(" | ")
            }
            
            // If no text found, it might be a media-only message
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing RCS JSON: ${e.message}")
            // Not JSON, return as plain text
            json
        }
    }
    
    /**
     * Data class representing an SMS message.
     */
    private data class SmsMessage(
        val id: Long,
        val sender: String,
        val timestamp: Long,
        val body: String,
        val type: Int
    )
}
