package com.pennywiseai.tracker.data.csv

import android.content.Context
import android.net.Uri
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.withTransaction
import com.pennywiseai.tracker.data.preferences.CurrencyPreferences
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.data.statement.ImportRules
import com.pennywiseai.tracker.data.statement.ImportRulesSource
import com.pennywiseai.tracker.domain.service.RuleEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

private const val ROOM_COMPAT = "com.pennywiseai.tracker.data.database.RoomCompatKt"

/** What a CSV import writes and reports, at the repository boundary. */
class ImportCsvUseCaseTest {
    private val header = "Date,Time,Merchant,Category,Type,Amount,Currency,Bank,Account,Balance After,Description"
    private val file = listOf(
        header,
        "2026-01-15,09:30:00,Coffee Hut,,Expense,250.00,INR,DemoBank,1234,,",
        "2026-01-16,09:30:00,Book Nook,,Expense,90.00,INR,DemoBank,1234,,"
    ).joinToString("\n")

    private val uri = mockk<Uri>()
    private val context = mockk<Context> {
        every { contentResolver.openInputStream(uri) } answers { ByteArrayInputStream(file.toByteArray()) }
    }
    private val transactions = mockk<TransactionRepository>(relaxed = true) {
        coEvery { getExistingHashes(any()) } returns emptyList()
    }
    private val importRules = mockk<ImportRulesSource>(relaxed = true) {
        coEvery { load() } returns ImportRules(emptyMap(), emptyMap(), emptyList(), RuleEngine())
    }
    private val database = mockk<PennyWiseDatabase>()
    private val balances = mockk<AccountBalanceRepository>(relaxed = true)
    private val useCase = ImportCsvUseCase(
        context,
        CsvTransactionImporter(),
        transactions,
        mockk<CurrencyPreferences> { every { baseCurrency } returns flowOf("INR") },
        importRules,
        database,
        balances
    )

    @Before
    fun setUp() {
        mockkStatic(ROOM_COMPAT)
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
    }

    @After
    fun tearDown() = unmockkStatic(ROOM_COMPAT)

    @Test
    fun `a row the database ignores is a duplicate, not an import`() = runBlocking {
        coEvery { transactions.insertTransactions(any()) } returns listOf(7L, -1L)

        val result = useCase.execute(uri) as ImportCsvUseCase.Result.Success

        assertEquals(1, result.imported)
        assertEquals(1, result.skippedDuplicate)
        coVerify { importRules.record(7L, any()) }
    }

    @Test
    fun `the hash lookup and the rules are read inside the write transaction`() = runBlocking {
        coEvery { transactions.insertTransactions(any()) } returns listOf(7L, 8L)

        useCase.execute(uri)

        coVerifyOrder {
            database.withTransaction(any<suspend () -> Any?>())
            transactions.getExistingHashes(any())
            importRules.load()
            transactions.insertTransactions(any())
        }
    }

    @Test
    fun `the accounts of the rows it adds are pinned before and recomputed after`() = runBlocking {
        coEvery { transactions.insertTransactions(any()) } returns listOf(7L, -1L)

        useCase.execute(uri)

        coVerifyOrder {
            database.withTransaction(any<suspend () -> Any?>())
            balances.pinManualOpenings(*anyVararg())
            transactions.insertTransactions(any())
            balances.recomputeManualBalances(match { rows -> rows.map { it.merchantName } == listOf("Coffee Hut") })
        }
    }
}
