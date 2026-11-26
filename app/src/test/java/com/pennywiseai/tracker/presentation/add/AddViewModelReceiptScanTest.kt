package com.pennywiseai.tracker.presentation.add

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.preferences.CurrencyPreferences
import com.pennywiseai.tracker.data.receipt.ReceiptTextRecognizer
import com.pennywiseai.tracker.data.repository.MerchantMappingRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.ui.UiText
import io.mockk.coEvery
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
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

/** "Scan receipt" fills only what the user hasn't entered. Synthetic receipt text. */
@OptIn(ExperimentalCoroutinesApi::class)
class AddViewModelReceiptScanTest {
    private val receipt = listOf("COFFEE HUT", "Date 2026-01-02", "Subtotal 9.00", "Tax 0.45", "TOTAL 9.45")

    private fun withForm(lines: List<String>?, block: (AddViewModel) -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val recognizer = object : ReceiptTextRecognizer {
                override val isAvailable = true
                override suspend fun recognize(uri: Uri): List<String>? = lines
            }
            val viewModel = AddViewModel(
                appContext = mockk(relaxed = true),
                addTransactionUseCase = mockk(relaxed = true),
                addSubscriptionUseCase = mockk(relaxed = true),
                getCategoriesUseCase = mockk { every { execute() } returns flowOf(emptyList()) },
                categoryRepository = mockk(relaxed = true),
                accountBalanceRepository = mockk(relaxed = true),
                budgetGroupRepository = mockk(relaxed = true),
                userPreferencesRepository = mockk<CurrencyPreferences> { every { baseCurrency } returns flowOf("USD") },
                transactionRepository = mockk<TransactionRepository>(relaxed = true) {
                    every { getAllMerchants() } returns flowOf(listOf("Coffee Hut Downtown"))
                },
                merchantMappingRepository = mockk<MerchantMappingRepository>(relaxed = true) {
                    coEvery { getCategoryForMerchant(any()) } returns null
                },
                tagRepository = mockk(relaxed = true),
                receiptManager = mockk(relaxed = true) { coEvery { checkReceipt(any()) } returns null },
                receiptTextRecognizer = recognizer,
                unrecognizedSmsRepository = mockk(relaxed = true),
                learnedSendersStore = mockk(relaxed = true),
                profilePreferences = mockk(relaxed = true),
                mascotMoments = mockk(relaxed = true),
                savedStateHandle = SavedStateHandle(),
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

    private val photo: Uri = mockk()

    @Test
    fun `a blank form is filled from the receipt and the photo attached`() = withForm(receipt) { vm ->
        vm.scanReceipt(photo)
        val state = vm.transactionUiState.value
        assertEquals("9.45", state.amount)
        assertEquals("Coffee Hut Downtown", state.merchant)
        assertEquals(LocalDate.of(2026, 1, 2), state.date.toLocalDate())
        assertEquals(photo, state.receiptUri)
        assertFalse(state.scanningReceipt)
        assertEquals(UiText.Res(R.string.add_receipt_scan_filled), state.scanMessage)
    }

    @Test
    fun `what the user entered is kept`() = withForm(receipt) { vm ->
        vm.updateTransactionAmount("12")
        vm.updateTransactionMerchant("Corner Shop")
        vm.updateTransactionTime(9, 30)
        val date = vm.transactionUiState.value.date
        vm.scanReceipt(photo)
        val state = vm.transactionUiState.value
        assertEquals("12", state.amount)
        assertEquals("Corner Shop", state.merchant)
        assertEquals(date, state.date)
        assertEquals(UiText.Res(R.string.add_receipt_scan_kept), state.scanMessage)
    }

    @Test
    fun `an unreadable photo leaves the form alone`() = withForm(null) { vm ->
        vm.scanReceipt(photo)
        val state = vm.transactionUiState.value
        assertEquals("", state.amount)
        assertEquals("", state.merchant)
        assertEquals(photo, state.receiptUri)
        assertEquals(UiText.Res(R.string.add_receipt_scan_failed), state.scanMessage)
    }

    @Test
    fun `a receipt in another currency switches an unlinked form to it`() = withForm(listOf("TOTAL ₹450.00")) { vm ->
        vm.scanReceipt(photo)
        val state = vm.transactionUiState.value
        assertEquals("INR", state.currency)
        assertEquals("450.00", state.amount)
    }
}
