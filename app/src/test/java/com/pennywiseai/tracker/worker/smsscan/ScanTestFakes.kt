package com.pennywiseai.tracker.worker.smsscan

import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.UnrecognizedSmsEntity
import com.pennywiseai.tracker.data.database.withTransaction
import com.pennywiseai.tracker.data.preferences.BankAccountMergeStore
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.CardRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TagRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.data.repository.UnrecognizedSmsRepository
import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.domain.service.RuleEngine
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.channels.SendChannel
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

internal const val ROOM_COMPAT = "com.pennywiseai.tracker.data.database.RoomCompatKt"

private const val SCAN_T0 = 1_760_000_000_000L

/** A debit in the format parser-core's HDFC samples use; [n] keeps each message distinct. */
internal fun hdfcDebit(n: Int) = ScannedSms(
    id = n.toLong(),
    sender = "VM-HDFCBK-S",
    timestamp = SCAN_T0 + n * 60_000L,
    body = "Rs.${100 + n}.00 debited from A/c XX1234 on 20-Oct-25 to merchant@upi (UPI Ref No ${123456789000L + n})",
    type = 1
)

internal fun scannedSms(sender: String, body: String, id: Long = 1L, timestamp: Long = SCAN_T0) =
    ScannedSms(id = id, sender = sender, timestamp = timestamp, body = body, type = 1)

internal fun transactionRow(
    id: Long,
    amount: String = "500.00",
    smsBody: String? = "Rs.500.00 debited from A/c XX1234",
    smsSender: String? = "VM-HDFCBK-S",
) = TransactionEntity(
    id = id,
    amount = BigDecimal(amount),
    merchantName = "Test Shop",
    category = "Others",
    transactionType = TransactionType.EXPENSE,
    dateTime = LocalDateTime.of(2025, 10, 20, 10, 0),
    smsBody = smsBody,
    smsSender = smsSender,
    bankName = "HDFC Bank",
    accountNumber = "1234",
    transactionHash = "hash-$id",
)

/**
 * The transactions table as a scan touches it: an auto-increment id and a UNIQUE hash.
 * Synchronized, because the balance consumer reads while the saver writes.
 */
internal class FakeTransactionStore {
    private val lock = Any()
    private val rows = LinkedHashMap<Long, TransactionEntity>()
    private var nextId = 1L

    /** Runs before a row is added, as a failing or interrupted write would. */
    @Volatile var beforeInsert: suspend (TransactionEntity) -> Unit = {}

    val all: List<TransactionEntity> get() = synchronized(lock) { rows.values.toList() }

    fun byId(id: Long): TransactionEntity? = synchronized(lock) { rows[id] }

    fun byHash(hash: String): TransactionEntity? = synchronized(lock) {
        rows.values.firstOrNull { it.transactionHash == hash }
    }

    fun bySms(body: String, sender: String?): TransactionEntity? = synchronized(lock) {
        rows.values.firstOrNull { it.smsBody == body && it.smsSender == sender }
    }

    fun near(amount: BigDecimal, start: LocalDateTime, end: LocalDateTime): List<TransactionEntity> = synchronized(lock) {
        rows.values.filter { it.amount.compareTo(amount) == 0 && !it.dateTime.isBefore(start) && !it.dateTime.isAfter(end) }
    }

    suspend fun insert(row: TransactionEntity): Long {
        beforeInsert(row)
        return synchronized(lock) {
            if (rows.values.any { it.transactionHash == row.transactionHash }) {
                -1L
            } else {
                val id = nextId++
                rows[id] = row.copy(id = id)
                id
            }
        }
    }

    /** Rewrites every stored row, to set up what an earlier scan or the user left behind. */
    fun updateAll(change: (TransactionEntity) -> TransactionEntity) = synchronized(lock) {
        rows.replaceAll { _, row -> change(row) }
    }
}

internal class ListSmsSource(private val messages: List<ScannedSms>) : SmsMessageSource {
    val sent = AtomicInteger(0)

    override suspend fun streamTo(feed: SendChannel<ScannedSms>, scanStartMillis: Long) {
        for (sms in messages) {
            feed.send(sms)
            sent.incrementAndGet()
        }
    }
}

/**
 * The real classifier and writer over fakes. Mocks the RoomCompat extension, so a test
 * class using this must call `unmockkStatic(ROOM_COMPAT)` in its @After. Use one per test:
 * a second harness resets the static mock under the first.
 */
internal class ScanHarness(
    scanContext: ScanRunContext = ScanRunContext(
        merchantCategories = emptyMap(),
        categoryIsIncome = emptyMap(),
        rulesByType = emptyMap(),
        accountsBeforeRebuild = emptyMap(),
        rebuildStartedAt = null,
    ),
) {
    val store = FakeTransactionStore()
    val balanceTimes: MutableList<LocalDateTime> = CopyOnWriteArrayList()
    val builtBalances: MutableList<AccountBalanceEntity> = CopyOnWriteArrayList()
    val filedUnrecognized: MutableList<List<UnrecognizedSmsEntity>> = CopyOnWriteArrayList()
    val widgetRefreshes = AtomicInteger(0)

    val transactions = mockk<TransactionRepository>(relaxed = true)
    val balances = mockk<AccountBalanceRepository>(relaxed = true)
    val unrecognized = mockk<UnrecognizedSmsRepository>(relaxed = true)

    private val subscriptions = mockk<SubscriptionRepository>(relaxed = true)
    private val cards = mockk<CardRepository>(relaxed = true)
    private val rules = mockk<RuleRepository>(relaxed = true)
    private val tags = mockk<TagRepository>(relaxed = true)
    private val ignored = mockk<IgnoredAccountsStore>(relaxed = true)
    private val merges = mockk<BankAccountMergeStore>()
    private val database = mockk<PennyWiseDatabase>()

    init {
        mockkStatic(ROOM_COMPAT)
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
        every { merges.resolve(any()) } answers { firstArg() }
        every { merges.resolveSuffix(any(), any(), any()) } answers { thirdArg() }

        // A relaxed mock answers a nullable lookup with a mock, not null.
        coEvery { subscriptions.matchTransactionToSubscription(any()) } returns null
        coEvery { cards.getCard(any(), any()) } returns null
        coEvery { balances.getLatestBalance(any(), any()) } returns null

        coEvery { transactions.getTransactionByHash(any()) } answers { store.byHash(firstArg()) }
        coEvery { transactions.getAnyBySms(any(), any()) } answers { store.bySms(firstArg(), secondArg()) }
        coEvery { transactions.getAnyBySmsAround(any(), any(), any(), any()) } returns null
        coEvery { transactions.getTransactionById(any()) } answers { store.byId(firstArg()) }
        coEvery { transactions.findPotentialDuplicates(any()) } returns emptyList()
        coEvery { transactions.getTransactionByAmountAndDateIncludingDeleted(any(), any(), any()) } answers {
            store.near(firstArg(), secondArg(), thirdArg())
        }
        coEvery { transactions.insertTransaction(any()) } coAnswers { store.insert(firstArg()) }

        coEvery { balances.insertSmsTransactionBalance(any(), any(), any(), any(), any()) } answers {
            balanceTimes += thirdArg<LocalDateTime>()
            arg<(BigDecimal?) -> AccountBalanceEntity>(4).invoke(null).also { builtBalances += it }
        }
        coEvery { unrecognized.insertAll(any()) } answers { filedUnrecognized += firstArg<List<UnrecognizedSmsEntity>>() }
    }

    val writer = ScanTransactionWriter(
        transactionRepository = transactions,
        subscriptionRepository = subscriptions,
        accountBalanceRepository = balances,
        cardRepository = cards,
        ruleRepository = rules,
        ruleEngine = RuleEngine(),
        tagRepository = tags,
        unrecognizedSmsRepository = unrecognized,
        ignoredAccountsStore = ignored,
        database = database,
        bankAccountMerges = merges,
        scanContext = scanContext,
    )

    val classifier = SmsScanClassifier(
        recentSinceMillis = 0L,
        taughtTemplates = lazyOf(emptyMap()),
        subscriptionRepository = subscriptions,
        saveBalanceNotification = writer::saveBalanceNotification,
    )

    fun pipeline(
        source: SmsMessageSource,
        classify: (ScannedSms) -> ParseResult = classifier::classify,
        isTaught: (ScannedSms) -> Boolean = classifier::matchesTaughtTemplate,
        parserConcurrency: Int = 3,
        onProgress: suspend (ProcessingStats) -> Unit = {},
    ) = SmsScanPipeline(
        source = source,
        classify = classify,
        isTaught = isTaught,
        writer = writer,
        parserConcurrency = parserConcurrency,
        onProgress = onProgress,
        onTransactionsChanged = { widgetRefreshes.incrementAndGet() },
    )

    suspend fun scan(
        messages: List<ScannedSms>,
        classify: (ScannedSms) -> ParseResult = classifier::classify,
        isTaught: (ScannedSms) -> Boolean = classifier::matchesTaughtTemplate,
        onProgress: suspend (ProcessingStats) -> Unit = {},
    ): ProcessingStats {
        val stats = ProcessingStats(total = messages.size)
        pipeline(ListSmsSource(messages), classify, isTaught, onProgress = onProgress).run(0L, stats)
        return stats
    }
}
