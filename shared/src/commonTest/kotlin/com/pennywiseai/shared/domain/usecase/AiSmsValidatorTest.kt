package com.pennywiseai.shared.domain.usecase

import com.pennywiseai.shared.data.model.SharedTransactionType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// Fake banks, fake numbers: bodies shaped like alerts from banks parser-core doesn't know.
private const val CARD_SPEND =
    "Dear Customer, INR 1,318.00 spent on your Example SFB Credit Card XX4321 at SWIGGY on 22-10-26. Avl limit INR 50,000.00"
private const val OTP =
    "123456 is your OTP for a txn of Rs.318.00 at SWIGGY on card XX4321. Do not share it with anyone. -Example Bank"

private fun reading(
    amount: String = "1318",
    currency: String = "INR",
    type: String = "credit",
    isBankTransaction: Boolean = true,
    accountLast4: String? = "4321"
) = AiSmsReading(isBankTransaction, amount, currency, type, "SWIGGY", accountLast4, "Example SFB")

class AiSmsValidatorTest {
    @Test
    fun acceptsAnAmountPrintedInTheSmsInAnyCommonSpelling() {
        for (spelling in listOf("1318", "1318.00", "1,318", "Rs. 1,318.00", "₹1318", "INR 1318.0")) {
            val parsed = assertNotNull(AiSmsValidator.validate(CARD_SPEND, reading(amount = spelling), null), spelling)
            assertEquals("1318.00", parsed.amount)
        }
        val parsed = AiSmsValidator.validate(CARD_SPEND, reading(), null)!!
        assertEquals("CREDIT", parsed.type)
        assertEquals("INR", parsed.currency)
        assertEquals("4321", parsed.accountLast4)
        assertEquals("Example SFB", parsed.bankName)
    }

    @Test
    fun rejectsAnAmountTheSmsDoesNotShow() {
        assertNull(AiSmsValidator.validate(CARD_SPEND, reading(amount = "1300"), null))
        assertNull(AiSmsValidator.validate(CARD_SPEND, reading(amount = "4321"), null)) // the masked card, not an amount
        assertNull(AiSmsValidator.validate(CARD_SPEND, reading(amount = "0"), null))
        assertNull(AiSmsValidator.validate(CARD_SPEND, reading(amount = "1318.005"), null))
    }

    @Test
    fun rejectsWhatIsNotABankTransaction() {
        assertNull(AiSmsValidator.validate(CARD_SPEND, reading(isBankTransaction = false), null))
        assertNull(AiSmsValidator.validate(OTP, reading(amount = "318"), null))
    }

    @Test
    fun rejectsAnUnsupportedCurrencyOrType() {
        assertNull(AiSmsValidator.validate(CARD_SPEND, reading(currency = "XYZ"), null))
        assertNull(AiSmsValidator.validate(CARD_SPEND, reading(currency = "$"), null))
        assertNull(AiSmsValidator.validate(CARD_SPEND, reading(type = "refund"), null))
        assertEquals("INR", AiSmsValidator.validate(CARD_SPEND, reading(currency = "Rs."), null)?.currency)
    }

    @Test
    fun keepsOnlyAnAccountNumberTheSmsShowsAndPrefersTheKnownBank() {
        assertNull(AiSmsValidator.validate(CARD_SPEND, reading(accountLast4 = "9999"), null)!!.accountLast4)
        assertEquals("Known Bank", AiSmsValidator.validate(CARD_SPEND, reading(), "Known Bank")!!.bankName)
    }

    @Test
    fun aiReadingSavesThroughTheImportPathMarkedAndDeduped() = runTest {
        val transactions = FakeTransactions()
        val useCase = ImportBankSmsUseCase(transactions, FakeAccounts(), FakeMappings(), FakeRules(), FakeCategories())

        val saved = assertIs<BankSmsImportResult.Saved>(useCase.executeAiReading(CARD_SPEND, 1_000L, reading(), null)).transaction
        assertEquals(131_800L, saved.amountMinor)
        assertEquals("INR", saved.currency)
        assertEquals("Swiggy", saved.merchantName)
        assertEquals("Food & Dining", saved.category)
        assertEquals(SharedTransactionType.CREDIT, saved.transactionType)
        assertEquals(AiSmsValidator.AI_NOTE, saved.note)

        assertEquals(BankSmsImportResult.Duplicate, useCase.executeAiReading(CARD_SPEND, 2_000L, reading(), null))
        assertEquals(BankSmsImportResult.Unparsed("Example SFB"), useCase.executeAiReading(OTP, 3_000L, reading(amount = "318"), null))
        assertEquals(1, transactions.rows.size)
    }
}
