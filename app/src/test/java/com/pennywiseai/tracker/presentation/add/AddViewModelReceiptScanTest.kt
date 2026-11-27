package com.pennywiseai.tracker.presentation.add

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.preferences.CurrencyPreferences
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.receipt.ReceiptManager
import com.pennywiseai.tracker.data.receipt.ReceiptTextRecognizer
import com.pennywiseai.tracker.data.repository.MerchantMappingRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.usecase.AddTransactionUseCase
import com.pennywiseai.tracker.ui.UiText
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** "Scan receipt" fills only what the user hasn't entered. Synthetic receipt text. */
@OptIn(ExperimentalCoroutinesApi::class)
class AddViewModelReceiptScanTest {
    private val receipt = listOf("COFFEE HUT", "Date 2026-01-02", "Subtotal 9.00", "Tax 0.45", "TOTAL 9.45")
    private val addTransactionUseCase: AddTransactionUseCase = mockk(relaxed = true)

    private fun withForm(lines: List<String>?, block: (AddViewModel) -> Unit) = withForm({ lines }, block = block)

    /** A form opened from the quick sheet's "More details", which hands on [draft] as route arguments. */
    private fun withDraft(lines: List<String>, draft: Map<String, Any?>, block: (AddViewModel) -> Unit) =
        withForm({ lines }, draft = draft, block = block)

    /**
     * A form whose OCR reads each photo with [read]; [refused] photos fail the receipt check,
     * and [categories] are the ones learned for past merchants.
     */
    private fun withForm(
        read: suspend (Uri) -> List<String>?,
        refused: Set<Uri> = emptySet(),
        draft: Map<String, Any?> = emptyMap(),
        categories: Map<String, String> = emptyMap(),
        block: (AddViewModel) -> Unit
    ) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val recognizer = object : ReceiptTextRecognizer {
                override val isAvailable = true
                override suspend fun recognize(uri: Uri): List<String>? = read(uri)
            }
            val viewModel = AddViewModel(
                appContext = mockk(relaxed = true),
                addTransactionUseCase = addTransactionUseCase,
                addSubscriptionUseCase = mockk(relaxed = true),
                getCategoriesUseCase = mockk { every { execute() } returns flowOf(emptyList()) },
                categoryRepository = mockk(relaxed = true),
                accountBalanceRepository = mockk(relaxed = true),
                budgetGroupRepository = mockk(relaxed = true),
                userPreferencesRepository = mockk<CurrencyPreferences> { every { baseCurrency } returns flowOf("USD") },
                transactionRepository = mockk<TransactionRepository>(relaxed = true) {
                    // Only the profile in view: another profile's spelling isn't offered.
                    coEvery { getMerchantsInProfile(2L) } returns listOf("Coffee Hut Downtown")
                    coEvery { getMerchantsInProfile(null) } returns listOf("Coffee Hut Downtown", "COFFEE HUT")
                },
                merchantMappingRepository = mockk<MerchantMappingRepository>(relaxed = true) {
                    coEvery { getCategoryForMerchant(any()) } answers { categories[firstArg()] }
                },
                tagRepository = mockk(relaxed = true),
                receiptManager = mockk(relaxed = true) {
                    coEvery { checkReceipt(any()) } answers {
                        if (firstArg<Uri>() in refused) ReceiptManager.PdfProblem.UNREADABLE else null
                    }
                },
                receiptTextRecognizer = recognizer,
                unrecognizedSmsRepository = mockk(relaxed = true),
                learnedSendersStore = mockk(relaxed = true),
                profilePreferences = mockk<UserPreferencesRepository>(relaxed = true) {
                    every { selectedProfileId } returns flowOf(2L)
                },
                mascotMoments = mockk(relaxed = true),
                savedStateHandle = SavedStateHandle(draft),
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
    private val otherPhoto: Uri = mockk()

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

    @Test
    fun `a second photo's scan replaces what the first one filled in`() {
        val second = listOf("BOOK NOOK", "Date 2026-01-05", "TOTAL 20.00")
        withForm({ uri -> if (uri == photo) receipt else second }) { vm ->
            vm.scanReceipt(photo)
            vm.scanReceipt(otherPhoto)
            val state = vm.transactionUiState.value
            assertEquals("20.00", state.amount)
            assertEquals("Book Nook", state.merchant)
            assertEquals(LocalDate.of(2026, 1, 5), state.date.toLocalDate())
            assertEquals(otherPhoto, state.receiptUri)
        }
    }

    @Test
    fun `a second photo without a total clears the first one's, but not what the user typed`() {
        withForm({ uri -> if (uri == photo) receipt else listOf("BOOK NOOK") }) { vm ->
            vm.scanReceipt(photo)
            vm.updateTransactionMerchant("Corner Shop")
            vm.scanReceipt(otherPhoto)
            val state = vm.transactionUiState.value
            assertEquals("", state.amount)
            assertEquals("Corner Shop", state.merchant)
        }
    }

    @Test
    fun `a second photo clears the first one's reading as soon as it's attached`() {
        val ocr = CompletableDeferred<List<String>?>()
        withForm({ uri -> if (uri == photo) receipt else ocr.await() }) { vm ->
            vm.scanReceipt(photo)
            vm.scanReceipt(otherPhoto)
            val state = vm.transactionUiState.value
            assertEquals(otherPhoto, state.receiptUri)
            assertTrue(state.scanningReceipt)
            assertEquals("", state.amount)
            assertEquals("", state.merchant)
        }
    }

    @Test
    fun `Save waits while the photo is read`() {
        val ocr = CompletableDeferred<List<String>?>()
        withForm({ ocr.await() }) { vm ->
            vm.updateTransactionAmount("12")
            vm.updateTransactionMerchant("Corner Shop")
            vm.scanReceipt(photo)
            vm.saveTransaction {}
            confirmVerified(addTransactionUseCase)
        }
    }

    @Test
    fun `a second scan puts back the category the first one's shop brought in`() {
        val second = listOf("BOOK NOOK", "TOTAL 20.00")
        withForm({ uri -> if (uri == photo) receipt else second }, categories = mapOf("Coffee Hut Downtown" to "Food & Dining")) { vm ->
            vm.scanReceipt(photo)
            assertEquals("Food & Dining", vm.transactionUiState.value.category)
            vm.scanReceipt(otherPhoto)
            assertEquals("Book Nook", vm.transactionUiState.value.merchant)
            assertEquals("Others", vm.transactionUiState.value.category)
        }
    }

    @Test
    fun `a category the user picked stays through a second scan`() {
        val second = listOf("BOOK NOOK", "TOTAL 20.00")
        withForm({ uri -> if (uri == photo) receipt else second }, categories = mapOf("Coffee Hut Downtown" to "Food & Dining")) { vm ->
            vm.scanReceipt(photo)
            vm.updateTransactionCategory("Groceries")
            vm.scanReceipt(otherPhoto)
            assertEquals("Groceries", vm.transactionUiState.value.category)
        }
    }

    @Test
    fun `a photo that fails its check isn't read into the form`() {
        withForm({ receipt }, refused = setOf(otherPhoto)) { vm ->
            vm.scanReceipt(otherPhoto)
            val state = vm.transactionUiState.value
            assertEquals("", state.amount)
            assertEquals(null, state.receiptUri)
            assertFalse(state.scanningReceipt)
        }
    }

    @Test
    fun `cancelling the check stops the scan too`() {
        val ocr = CompletableDeferred<List<String>?>()
        withForm({ ocr.await() }) { vm ->
            vm.scanReceipt(photo)
            vm.cancelReceiptCheck()
            ocr.complete(receipt)
            val state = vm.transactionUiState.value
            assertEquals("", state.amount)
            assertFalse(state.scanningReceipt)
        }
    }

    @Test
    fun `a field the user empties while the photo is read stays empty`() {
        val ocr = CompletableDeferred<List<String>?>()
        withForm({ ocr.await() }) { vm ->
            vm.updateTransactionMerchant("Corner")
            vm.scanReceipt(photo)
            vm.updateTransactionMerchant("")
            ocr.complete(receipt)
            assertEquals("", vm.transactionUiState.value.merchant)
            assertEquals("9.45", vm.transactionUiState.value.amount)
        }
    }

    @Test
    fun `a currency the user chose isn't switched, and a total in another one is left out`() =
        withForm(listOf("TOTAL ₹450.00")) { vm ->
            vm.updateTransactionCurrency("EUR")
            vm.scanReceipt(photo)
            val state = vm.transactionUiState.value
            assertEquals("EUR", state.currency)
            assertEquals("", state.amount)
            assertEquals(UiText.Res(R.string.add_receipt_scan_other_currency), state.scanMessage)
        }

    @Test
    fun `a shared mark keeps the form's currency, and fills only when it can be the form's`() {
        withForm(listOf("TOTAL $12.00")) { vm ->
            vm.scanReceipt(photo)
            assertEquals("USD", vm.transactionUiState.value.currency)
            assertEquals("12.00", vm.transactionUiState.value.amount)
        }
        withForm(listOf("Net payable Rs. 450.00")) { vm ->
            vm.scanReceipt(photo)
            assertEquals("USD", vm.transactionUiState.value.currency)
            assertEquals("", vm.transactionUiState.value.amount)
        }
    }

    @Test
    fun `a second scan puts back the currency the first one switched`() {
        withForm({ uri -> if (uri == photo) listOf("TOTAL ₹450.00") else listOf("TOTAL 12.00") }) { vm ->
            vm.scanReceipt(photo)
            assertEquals("INR", vm.transactionUiState.value.currency)
            vm.scanReceipt(otherPhoto)
            assertEquals("USD", vm.transactionUiState.value.currency)
            assertEquals("12.00", vm.transactionUiState.value.amount)
        }
    }

    @Test
    fun `an amount typed over a scanned one keeps the scan's currency on the next scan`() {
        withForm({ uri -> if (uri == photo) listOf("TOTAL ₹450.00") else listOf("TOTAL 12.00") }) { vm ->
            vm.scanReceipt(photo)
            vm.updateTransactionAmount("460")
            vm.scanReceipt(otherPhoto)
            val state = vm.transactionUiState.value
            assertEquals("460", state.amount)
            assertEquals("INR", state.currency)
        }
    }

    @Test
    fun `switching away from the scanned currency clears the scanned amount`() =
        withForm(listOf("TOTAL ₹450.00")) { vm ->
            vm.scanReceipt(photo)
            vm.updateTransactionCurrency("EUR")
            val state = vm.transactionUiState.value
            assertEquals("EUR", state.currency)
            assertEquals("", state.amount)
        }

    @Test
    fun `the quick sheet's untouched base currency still lets the receipt's own one in`() =
        withDraft(listOf("TOTAL JOD 12.500"), mapOf("currency" to "USD")) { vm ->
            vm.scanReceipt(photo)
            val state = vm.transactionUiState.value
            assertEquals("JOD", state.currency)
            assertEquals("12.500", state.amount)
        }

    @Test
    fun `another currency picked in the quick sheet is kept, and a total in a third one left out`() =
        withDraft(listOf("TOTAL JOD 12.500"), mapOf("currency" to "EUR")) { vm ->
            vm.scanReceipt(photo)
            val state = vm.transactionUiState.value
            assertEquals("EUR", state.currency)
            assertEquals("", state.amount)
            assertEquals(UiText.Res(R.string.add_receipt_scan_other_currency), state.scanMessage)
        }
}
