package com.pennywiseai.tracker.worker.smsscan

import android.util.Log
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.bank.BaseIndianBankParser
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.CardType
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.UnrecognizedSmsEntity
import com.pennywiseai.tracker.data.database.withTransaction
import com.pennywiseai.tracker.data.manager.TransactionDeduplication
import com.pennywiseai.tracker.data.mapper.DeferredBalanceInput
import com.pennywiseai.tracker.data.mapper.atEnqueuedAccount
import com.pennywiseai.tracker.data.mapper.toEntity
import com.pennywiseai.tracker.data.mapper.toEntityType
import com.pennywiseai.tracker.data.notification.BankNotificationConfig
import com.pennywiseai.tracker.data.preferences.BankAccountMergeStore
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.CardRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TagRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.data.repository.UnrecognizedSmsRepository
import com.pennywiseai.tracker.data.repository.mappingFits
import com.pennywiseai.tracker.domain.model.rule.TransactionRule
import com.pennywiseai.tracker.domain.model.rule.tagChanges
import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.domain.service.RuleEngine
import com.pennywiseai.tracker.data.ledger.BalanceCalculator
import com.pennywiseai.tracker.utils.CurrencyFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.toJavaLocalDateTime
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

private const val TAG = "OptimizedSmsReaderWorker"

/** Computed once in doWork, then read by every save for the rest of the scan. */
internal class ScanRunContext(
    /**
     * Merchant-name to custom category, preloaded once at scan start.
     * Previously: 1 DB read per transaction. Now: 1 DB read total, O(1) lookup per transaction.
     */
    val merchantCategories: Map<String, String>,

    /** Category name to its income flag, for [mappingFits]; preloaded with the mappings. */
    val categoryIsIncome: Map<String, Boolean>,

    /**
     * Rules preloaded by transaction type at scan start.
     * Previously: getActiveRulesByType DB call per transaction.
     * Now: one call per TransactionType (4 total), zero DB calls per transaction.
     */
    val rulesByType: Map<TransactionType, List<TransactionRule>>,

    /**
     * Typed accounts as they were before a force resync wiped their rebuildable
     * balance rows, keyed by (bank, last4). A user-chosen type (#792) lives only on
     * those rows, so the rebuild reads it from here and re-tags the account after.
     * Empty unless this run is a force resync.
     */
    val accountsBeforeRebuild: Map<Pair<String, String>, AccountBalanceEntity>,

    /**
     * When this run's force resync wiped the SMS balance rows, or null. Delta rows
     * created before it survived the wipe already holding the SMS effects being put
     * back; rows created after it (an expense added mid-rebuild) don't (#982).
     */
    val rebuildStartedAt: LocalDateTime?,
)

internal enum class SaveOutcome {
    SAVED,
    UPDATED_DUPLICATE,
    SKIPPED_DUPLICATE,
    SKIPPED
}

internal data class DeferredBalanceUpdate(
    // Capture the rule-selected account, then check it again at consumption.
    val input: DeferredBalanceInput,
    val transactionId: Long
)

/**
 * The scan's save stage: everything that writes rows, called from one coroutine.
 *
 * Each entry point takes [BankAccountMergeStore.mutationMutex] itself and the pipeline never
 * holds it. The mutex is not reentrant, and a balance update must be sent outside it so the
 * consumer can drain a full channel.
 */
internal class ScanTransactionWriter(
    private val transactionRepository: TransactionRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val cardRepository: CardRepository,
    private val ruleRepository: RuleRepository,
    private val ruleEngine: RuleEngine,
    private val tagRepository: TagRepository,
    private val unrecognizedSmsRepository: UnrecognizedSmsRepository,
    private val ignoredAccountsStore: IgnoredAccountsStore,
    private val database: PennyWiseDatabase,
    private val bankAccountMerges: BankAccountMergeStore,
    private val scanContext: ScanRunContext,
) {
    /** Cached at class level — eliminates a system call on every SMS timestamp conversion. */
    private val systemZone: ZoneId = ZoneId.systemDefault()

    private fun Long.toLocalDateTime(): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(this), systemZone)

    suspend fun saveBalanceNotification(
        info: BaseIndianBankParser.BaseBalanceUpdateInfo,
        currency: String,
        sms: ScannedSms
    ) = BankAccountMergeStore.mutationMutex.withLock {
        val suffix = info.accountLast4?.let { bankAccountMerges.resolveSuffix(info.bankName, currency, it) }
        // Balance-only notifications bypass saveTransaction, so apply the same account gates here.
        if (!ignoredAccountsStore.isIgnored(info.bankName, suffix)) {
            accountBalanceRepository.insertBalanceUpdate(
                bankName = info.bankName,
                accountLast4 = suffix ?: "XXXX",
                balance = info.balance,
                timestamp = info.asOfDate?.toJavaLocalDateTime() ?: sms.timestamp.toLocalDateTime(),
                currency = currency
            )
        }
    }

    // ─── Stage 3b: Regular transactions ──────────────────────────────────────

    suspend fun saveTransaction(
        parsedInput: ParsedTransaction,
        sms: ScannedSms,
        stats: ProcessingStats,
        balanceUpdates: SendChannel<DeferredBalanceUpdate>
    ): SaveOutcome = try {
        var pendingBalance: DeferredBalanceUpdate? = null
        val outcome = BankAccountMergeStore.mutationMutex.withLock {
            coroutineScope {
                val parsed = bankAccountMerges.resolve(parsedInput)
                val entity = parsed.toEntity()

                // Same gate as the live receiver, including the linked account
                // behind a debit card (#826), so a rescan can't re-import what the
                // user excluded.
                val linkedAccountLast4 = if (parsed.isFromCard) {
                    parsed.accountLast4?.let {
                        cardRepository.getCard(parsed.bankName, it)?.accountLast4
                    }
                } else null
                if (ignoredAccountsStore.isIgnored(
                        entity.bankName,
                        entity.accountNumber,
                        linkedAccountLast4
                    )
                ) {
                    return@coroutineScope SaveOutcome.SKIPPED
                }

                val hashDeferred = async { transactionRepository.getTransactionByHash(entity.transactionHash) }

                val customCategory = scanContext.merchantCategories[entity.merchantName]
                    ?.takeIf { mappingFits(scanContext.categoryIsIncome[it], entity.transactionType) }
                val mapped = if (customCategory != null) entity.copy(category = customCategory) else entity

                val activeRules = scanContext.rulesByType[mapped.transactionType] ?: emptyList()
                val isBlocked = ruleEngine.shouldBlockTransaction(mapped, sms.body, activeRules) != null

                if (hashDeferred.await() != null) return@coroutineScope SaveOutcome.SKIPPED

                // Same charge already booked from the bank's app notification. Only
                // banks that can arrive by notification pay for the lookup.
                if (entity.bankName in BankNotificationConfig.notificationBankNames) {
                    // Deleted rows count too: a charge the user deleted after
                    // its notification booked it must not return via the SMS.
                    val nearby = transactionRepository.getTransactionByAmountAndDateIncludingDeleted(
                        entity.amount,
                        entity.dateTime.minusMinutes(2),
                        entity.dateTime.plusMinutes(2)
                    )
                    if (TransactionDeduplication.isBookedByOtherChannel(
                            entity, nearby, BankNotificationConfig.notificationAliases
                        )
                    ) {
                        return@coroutineScope SaveOutcome.SKIPPED
                    }
                }

                // The raw SMS is the stable identity: the hash includes the parsed
                // amount, so it shifts whenever a parser fix changes that amount.
                //  - a soft-deleted row means the user deleted it — don't resurrect (#703)
                //  - an active row means it's already booked — don't insert it twice,
                //    and repair an amount an older parser got wrong (#886)
                entity.smsBody?.let { body ->
                    transactionRepository.getAnyBySms(body, entity.smsSender)?.let { stored ->
                        if (!stored.isDeleted && TransactionDeduplication.isParseArtifact(stored, entity)) {
                            transactionRepository.correctParsedAmount(stored, entity)
                        }
                        return@coroutineScope SaveOutcome.SKIPPED
                    }
                }

                if (isBlocked) {
                    stats.blocked.incrementAndGet()
                    return@coroutineScope SaveOutcome.SKIPPED
                }

                val (withRules, ruleApps) = ruleEngine.evaluateRules(mapped, sms.body, activeRules)

                val matchedSub = subscriptionRepository.matchTransactionToSubscription(withRules)
                // The schedule advances only once the row commits (below) — a skipped
                // duplicate or failed insert must leave next_payment_date alone.
                val finalEntity = if (matchedSub != null) withRules.copy(isRecurring = true) else withRules

                val duplicate = transactionRepository.findPotentialDuplicates(finalEntity).firstOrNull()
                if (duplicate != null) {
                    if (TransactionDeduplication.shouldReplaceWithIncoming(duplicate, finalEntity)) {
                        val replacement = finalEntity.copy(
                            id = duplicate.id,
                            transactionHash = duplicate.transactionHash,
                            isRecurring = duplicate.isRecurring || finalEntity.isRecurring,
                            createdAt = duplicate.createdAt,
                            // Loan and split-bill (#346) links belong to the row, not the SMS.
                            loanId = duplicate.loanId,
                            loanContribution = duplicate.loanContribution,
                            lentSharesTotal = duplicate.lentSharesTotal
                        )
                        transactionRepository.updateTransaction(replacement)
                        accountBalanceRepository.removeSmsTransactionBalances(duplicate.id, scanContext.rebuildStartedAt)
                        replaceRuleApplications(duplicate.id, ruleApps)
                        pendingBalance = parsedInput.atEnqueuedAccount(parsed, replacement)?.let {
                            DeferredBalanceUpdate(it, duplicate.id)
                        }
                        return@coroutineScope SaveOutcome.UPDATED_DUPLICATE
                    }
                    return@coroutineScope SaveOutcome.SKIPPED_DUPLICATE
                }

                // One transaction: a charge that commits without its schedule advance
                // would leave the bill due forever — a re-scan skips the row by hash.
                val rowId = database.withTransaction {
                    transactionRepository.insertTransaction(finalEntity).also { id ->
                        if (id != -1L) matchedSub?.let { subscriptionRepository.recordCharge(it, finalEntity.dateTime.toLocalDate()) }
                    }
                }
                if (rowId == -1L) return@coroutineScope SaveOutcome.SKIPPED

                saveRuleApplications(rowId, ruleApps)
                pendingBalance = parsedInput.atEnqueuedAccount(parsed, finalEntity)?.let {
                    DeferredBalanceUpdate(it, rowId)
                }
                SaveOutcome.SAVED
            }
        }
        // Send outside the mutation lock so the balance consumer can drain a full channel.
        pendingBalance?.let { balanceUpdates.send(it) }
        outcome
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.e(TAG, "Error saving transaction: ${e.message}")
        SaveOutcome.SKIPPED
    }

    private suspend fun replaceRuleApplications(
        transactionId: Long,
        ruleApps: List<com.pennywiseai.tracker.domain.model.rule.RuleApplication>
    ) {
        ruleRepository.deleteRuleApplicationsForTransaction(transactionId.toString())
        saveRuleApplications(transactionId, ruleApps)
    }

    private suspend fun saveRuleApplications(
        transactionId: Long,
        ruleApps: List<com.pennywiseai.tracker.domain.model.rule.RuleApplication>
    ) {
        if (ruleApps.isEmpty()) return
        ruleRepository.saveRuleApplications(
            ruleApps.map { it.copy(transactionId = transactionId.toString()) }
        )
        // ADD_TAG / REMOVE_TAG live in the tag table, not on the row (#748).
        val (add, remove) = ruleApps.tagChanges()
        if (add.isNotEmpty() || remove.isNotEmpty()) tagRepository.applyTagChanges(transactionId, add, remove)
    }

    // ─── Balance update ───────────────────────────────────────────────────────

    /** Takes the mutation lock itself; the pipeline's balance consumer never holds it. */
    suspend fun applyBalanceUpdate(update: DeferredBalanceUpdate) = BankAccountMergeStore.mutationMutex.withLock {
        processBalanceUpdate(update.input, update.transactionId)
    }

    private suspend fun processBalanceUpdate(
        input: DeferredBalanceInput,
        rowId: Long
    ) {
        // The transaction may have moved again since enqueue, including a merge
        // that invalidated its original short-mask mapping. Follow the saved row.
        val current = transactionRepository.getTransactionById(rowId) ?: return
        val parsed = input.forSavedTransaction(bankAccountMerges.resolve(input.account), current) ?: return
        val accountLast4 = parsed.accountLast4 ?: run {
            // Mobile-money wallets (eMola, M-Pesa Mozambique) have no per-account
            // number — the whole wallet is one account. Derive a service-level row
            // from the running balance so a full re-scan also creates it, matching
            // SmsTransactionProcessor.
            if (parsed.isMobileWallet && parsed.balance != null) {
                upsertWalletBalance(parsed, current, rowId)
            }
            return
        }

        val card = if (parsed.isFromCard) {
            (cardRepository.getCard(parsed.bankName, accountLast4)
                ?: run {
                    cardRepository.findOrCreateCard(
                        accountLast4, parsed.bankName,
                        isCredit = parsed.type.toEntityType() == TransactionType.CREDIT
                    )
                    cardRepository.getCard(parsed.bankName, accountLast4)
                }
                )?.also { c ->
                cardRepository.updateCardBalance(
                    cardId  = c.id,
                    balance = parsed.balance,
                    source  = parsed.smsBody.take(200),
                    date    = parsed.timestamp.toLocalDateTime()
                )
            }
        } else null

        val targetAccount = when {
            card == null                                                 -> accountLast4
            card.cardType == CardType.CREDIT                             -> accountLast4
            card.cardType == CardType.DEBIT && card.accountLast4 != null -> card.accountLast4
            else                                                         -> return
        }

        val isCreditCard = card?.cardType == CardType.CREDIT ||
            parsed.type.toEntityType() == TransactionType.CREDIT

        val existing = accountBalanceRepository.getLatestBalance(parsed.bankName, targetAccount)

        // A force resync may have wiped this account's rows; until a typed row exists
        // again, its type comes from the snapshot taken before the rebuild (#792). A
        // typed row wins, so a type the user sets while the resync runs is kept.
        // Balance math still starts from the rebuilt history (`existing`), never from
        // the pre-resync balance.
        val typed = existing?.takeIf { it.accountType != null }
            ?: scanContext.accountsBeforeRebuild[parsed.bankName to targetAccount]
            ?: existing
        val resolvedIsCreditCard = BalanceCalculator.resolvedIsCreditCard(isCreditCard, typed)

        // The account keeps its own currency; a foreign-currency spend isn't
        // added to it (see BalanceCalculator.balanceAfter).
        val accountCurrency = existing?.currency
            ?: parsed.accountCurrency
            ?: parsed.currency
        // Moves from the balance just before this SMS in time, not the newest
        // row: the scan's parallel parsers save out of order (#982).
        val saved = accountBalanceRepository.insertSmsTransactionBalance(
            parsed.bankName, targetAccount, current.dateTime, scanContext.rebuildStartedAt
        ) { before ->
            val newBalance = BalanceCalculator.balanceAfter(
                explicitBalance = parsed.balance,
                // A loan account's balance is owed too: a payment lowers it (#792).
                isCreditCard = resolvedIsCreditCard || typed?.isLoan == true,
                transactionType = parsed.type.toEntityType(),
                transactionAmount = parsed.amount,
                transactionCurrency = parsed.currency,
                accountCurrency = accountCurrency,
                currentBalance = before
            )

            // Credit-card SMS report the AVAILABLE limit, not the total. The UI derives
            // available = creditLimit − balance, so store total = available + outstanding
            // (the post-transaction balance). Falls back to the existing stored limit when
            // the SMS carries no limit. (#486)
            val resolvedCreditLimit = if (resolvedIsCreditCard && parsed.creditLimit != null) {
                parsed.creditLimit!! + newBalance
            } else {
                existing?.creditLimit
            }

            AccountBalanceEntity(
                bankName      = parsed.bankName,
                accountLast4  = targetAccount,
                balance       = newBalance,
                timestamp     = current.dateTime,
                transactionId = if (rowId != -1L) rowId else null,
                creditLimit   = resolvedCreditLimit,
                isCreditCard  = resolvedIsCreditCard,
                smsSource     = parsed.smsBody.take(500),
                sourceType    = "TRANSACTION",
                accountType   = BalanceCalculator.preservedAccountType(typed),
                currency      = accountCurrency,
                profileId     = existing?.profileId ?: ProfileEntity.PERSONAL_ID,
                alias         = existing?.alias,
                lowBalanceThreshold = existing?.lowBalanceThreshold
            )
        }

        val logMsg = if (parsed.creditLimit != null) {
            "Saved balance/limit (${CurrencyFormatter.formatCurrency(parsed.creditLimit!!, parsed.currency, maskIfHidden = false)})"
        } else {
            "Saved balance (${CurrencyFormatter.formatCurrency(saved.balance, saved.currency, maskIfHidden = false)})"
        }
        Log.i(TAG, logMsg)
    }

    /**
     * Derives a single service-level account row for a mobile-money wallet
     * (eMola, M-Pesa Mozambique). No per-account number, never a card/credit —
     * keyed on bankName + [AccountBalanceEntity.WALLET_ACCOUNT_MARKER], balance
     * taken straight from the SMS. Mirrors SmsTransactionProcessor.upsertWalletBalance.
     */
    private suspend fun upsertWalletBalance(
        parsed: ParsedTransaction,
        entity: com.pennywiseai.tracker.data.database.entity.TransactionEntity,
        rowId: Long
    ) {
        val balance = parsed.balance ?: return
        val existing = accountBalanceRepository.getLatestBalance(
            parsed.bankName,
            AccountBalanceEntity.WALLET_ACCOUNT_MARKER
        )
        val balanceEntity = AccountBalanceEntity(
            bankName      = parsed.bankName,
            accountLast4  = AccountBalanceEntity.WALLET_ACCOUNT_MARKER,
            balance       = balance,
            timestamp     = entity.dateTime,
            transactionId = if (rowId != -1L) rowId else null,
            creditLimit   = null,
            isCreditCard  = false,
            smsSource     = parsed.smsBody.take(500),
            sourceType    = "TRANSACTION",
            accountType   = BalanceCalculator.preservedAccountType(existing),
            // The balance is in the account's currency, which can differ from the spend's.
            currency      = parsed.accountCurrency ?: parsed.currency,
            profileId     = existing?.profileId ?: ProfileEntity.PERSONAL_ID,
            alias         = existing?.alias,
            lowBalanceThreshold = existing?.lowBalanceThreshold
        )
        accountBalanceRepository.insertBalance(balanceEntity)
        Log.i(TAG, "Saved wallet balance for ${parsed.bankName}")
    }

    // ─── Unrecognized SMS batch ────────────────────────────────────────────────

    suspend fun flushUnrecognizedBatch(batch: ArrayList<ScannedSms>, isTaught: (ScannedSms) -> Boolean) {
        fun ScannedSms.toRow() = UnrecognizedSmsEntity(
            sender     = sender,
            smsBody    = body,
            receivedAt = timestamp.toLocalDateTime()
        )
        val (taught, others) = batch.partition(isTaught)
        try {
            unrecognizedSmsRepository.insertAll(others.map { it.toRow() })
            // A taught sender's text can repeat: the same words later are another payment, offered
            // again unless that very SMS is already booked, as on every rescan after confirming it.
            taught.forEach { sms ->
                val booked = transactionRepository.getAnyBySmsAround(
                    sms.body, sms.sender, sms.timestamp.toLocalDateTime(), UnrecognizedSmsRepository.REDELIVERY_WINDOW
                )
                if (booked == null) unrecognizedSmsRepository.insertOrRevive(sms.toRow())
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error storing unrecognized SMS batch: ${e.message}")
        }
        batch.clear()
    }
}
