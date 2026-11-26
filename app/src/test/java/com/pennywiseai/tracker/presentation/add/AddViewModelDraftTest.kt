package com.pennywiseai.tracker.presentation.add

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.preferences.CurrencyPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The quick add sheet's "More details" draft. The base currency is ready at once,
 * as on a device with it cached, so init runs inside the constructor: an Income
 * draft used to read [AddViewModel.categories] before it was set and crash.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddViewModelDraftTest {
    private fun withDraft(type: TransactionType, block: (AddViewModel) -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val currency = mockk<CurrencyPreferences> { every { baseCurrency } returns flowOf("USD") }
            val viewModel = AddViewModel(
                appContext = mockk(relaxed = true),
                addTransactionUseCase = mockk(relaxed = true),
                addSubscriptionUseCase = mockk(relaxed = true),
                getCategoriesUseCase = mockk { every { execute() } returns flowOf(emptyList()) },
                categoryRepository = mockk(relaxed = true),
                accountBalanceRepository = mockk(relaxed = true),
                budgetGroupRepository = mockk(relaxed = true),
                userPreferencesRepository = currency,
                transactionRepository = mockk(relaxed = true),
                merchantMappingRepository = mockk(relaxed = true),
                tagRepository = mockk(relaxed = true),
                receiptManager = mockk(relaxed = true),
                receiptTextRecognizer = mockk(relaxed = true),
                unrecognizedSmsRepository = mockk(relaxed = true),
                learnedSendersStore = mockk(relaxed = true),
                profilePreferences = mockk(relaxed = true),
                mascotMoments = mockk(relaxed = true),
                savedStateHandle = SavedStateHandle(mapOf("type" to type.name, "amount" to "12")),
            )
            try {
                block(viewModel)
            } finally {
                viewModel.viewModelScope.cancel()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `an income draft opens both forms as income`() = withDraft(TransactionType.INCOME) { vm ->
        val transaction = vm.transactionUiState.value
        assertTrue(transaction.draftLoaded)
        assertEquals(TransactionType.INCOME, transaction.transactionType)
        assertEquals("12", transaction.amount)
        assertEquals("USD", transaction.currency)
        val subscription = vm.subscriptionUiState.value
        assertEquals(SubscriptionDirection.INCOME, subscription.direction)
        assertEquals("Income", subscription.category)
        assertEquals("12", subscription.amount)
    }

    @Test
    fun `an expense draft keeps the subscription form on expense`() = withDraft(TransactionType.EXPENSE) { vm ->
        val transaction = vm.transactionUiState.value
        assertTrue(transaction.draftLoaded)
        assertEquals(TransactionType.EXPENSE, transaction.transactionType)
        assertEquals(SubscriptionDirection.EXPENSE, vm.subscriptionUiState.value.direction)
    }
}
