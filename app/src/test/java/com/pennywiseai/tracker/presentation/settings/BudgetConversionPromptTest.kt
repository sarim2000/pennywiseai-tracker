package com.pennywiseai.tracker.presentation.settings

import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.BudgetGroupRepository
import com.pennywiseai.tracker.widget.WidgetRefresher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/** Settings stays on the back stack, so the question could otherwise wait for the user's return. */
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetConversionPromptTest {

    private val baseCurrency = MutableStateFlow("JOD")
    private val saved = CompletableDeferred<Unit>()
    private val rate = CompletableDeferred<BigDecimal?>()
    private val preferences = mockk<UserPreferencesRepository>(relaxed = true)
    private val budgets = mockk<BudgetGroupRepository>(relaxed = true)
    private val conversion = mockk<CurrencyConversionService>(relaxed = true)
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkObject(WidgetRefresher)
        every { WidgetRefresher.refreshTransactionWidgets(any()) } returns Unit
        every { preferences.baseCurrency } returns baseCurrency
        coEvery { preferences.updateBaseCurrency(any()) } coAnswers {
            saved.await()
            baseCurrency.value = firstArg()
        }
        coEvery { budgets.hasActiveBudgets() } returns true
        coEvery { conversion.getExchangeRate("JOD", "INR", any()) } coAnswers { rate.await() }
        viewModel = SettingsViewModel(
            context = mockk(relaxed = true),
            modelRepository = mockk(relaxed = true),
            userPreferencesRepository = preferences,
            unrecognizedSmsRepository = mockk(relaxed = true),
            transactionRepository = mockk(relaxed = true),
            deleteAllTransactionsUseCase = mockk(relaxed = true),
            accountBalanceRepository = mockk(relaxed = true),
            backupExporter = mockk(relaxed = true),
            backupImporter = mockk(relaxed = true),
            importCsvUseCase = mockk(relaxed = true),
            folderBackupWriter = mockk(relaxed = true),
            scheduledFolderBackupScheduler = mockk(relaxed = true),
            contactsResolver = mockk(relaxed = true),
            modelDownloader = mockk(relaxed = true),
            moneyAlertChecker = mockk(relaxed = true),
            budgetGroupRepository = budgets,
            currencyConversionService = conversion,
            entitlementGate = mockk(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        unmockkObject(WidgetRefresher)
        Dispatchers.resetMain()
    }

    @Test
    fun `staying on Settings gets the question`() {
        viewModel.updateBaseCurrency("INR")
        saved.complete(Unit)
        rate.complete(BigDecimal("133.9"))

        assertEquals(
            BudgetConversionPrompt("JOD", "INR", BigDecimal("133.9")),
            viewModel.budgetConversionPrompt.value
        )
    }

    @Test
    fun `a rate that arrives after leaving asks nothing`() {
        viewModel.updateBaseCurrency("INR")
        saved.complete(Unit)
        viewModel.dropBudgetConversion()
        rate.complete(BigDecimal("133.9"))

        assertNull(viewModel.budgetConversionPrompt.value)
    }

    @Test
    fun `leaving before the save finishes keeps the pick and asks nothing`() {
        viewModel.updateBaseCurrency("INR")
        viewModel.dropBudgetConversion()
        saved.complete(Unit)
        rate.complete(BigDecimal("133.9"))

        assertEquals("INR", baseCurrency.value)
        assertNull(viewModel.budgetConversionPrompt.value)
        coVerify(exactly = 1) { budgets.moveBudgetsToCurrency("INR", null) }
    }

    @Test
    fun `dropping a question on screen converts nothing`() {
        viewModel.updateBaseCurrency("INR")
        saved.complete(Unit)
        rate.complete(BigDecimal("133.9"))
        viewModel.dropBudgetConversion()
        viewModel.resolveBudgetConversion(convert = true)

        assertNull(viewModel.budgetConversionPrompt.value)
        coVerify(exactly = 0) { budgets.moveBudgetsToCurrency(any(), BigDecimal("133.9")) }
    }

    @Test
    fun `a newer pick takes the old question off screen`() {
        viewModel.updateBaseCurrency("INR")
        saved.complete(Unit)
        rate.complete(BigDecimal("133.9"))
        coEvery { conversion.getExchangeRate("INR", "USD", any()) } coAnswers { CompletableDeferred<BigDecimal?>().await() }

        viewModel.updateBaseCurrency("USD")

        assertNull(viewModel.budgetConversionPrompt.value)
    }

    @Test
    fun `converting while a newer pick is saving doesn't apply the old rate`() {
        viewModel.updateBaseCurrency("INR")
        saved.complete(Unit)
        rate.complete(BigDecimal("133.9"))
        val usdSaved = CompletableDeferred<Unit>()
        coEvery { preferences.updateBaseCurrency("USD") } coAnswers {
            usdSaved.await()
            baseCurrency.value = "USD"
        }
        coEvery { conversion.getExchangeRate("INR", "USD", any()) } returns null

        viewModel.updateBaseCurrency("USD")
        viewModel.resolveBudgetConversion(convert = true)
        usdSaved.complete(Unit)

        assertEquals("USD", baseCurrency.value)
        coVerify(exactly = 0) { budgets.moveBudgetsToCurrency(any(), BigDecimal("133.9")) }
        coVerify(exactly = 1) { budgets.moveBudgetsToCurrency("USD", null) }
    }
}
