package com.pennywiseai.shared.domain.usecase

import com.pennywiseai.shared.data.model.SharedTransaction
import com.pennywiseai.shared.data.model.SharedTransactionType
import com.pennywiseai.shared.data.statement.SharedStatementImportResult
import com.pennywiseai.shared.data.statement.SliceSharedStatementParser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// A synthetic slice statement (layout only; every name, number and reference is made up).
private val STATEMENT = """
    01 Jan '26 - 31 Jan '26
    1/1
    Mr TEST PERSON
    Customer ID 100000000001
    Account SAVINGS
    A/C number 999900005678
    IFSC NESF0000999
    Opening balance Total credits Interest earned Total debits Closing balance
    + + - =
    ₹100 ₹0 ₹0 ₹12.35 ₹87.65
    DATE DETAILS REF NO. AMOUNT BALANCE
    04 Jan '26 UPI-Debit-999900002222-OTHER SHOP-WXYZ0AB1234-shop@upi-123456 2026010412345679 -₹12.35 ₹87.65
    Generated on 31 Jan '26
    Need help? Contact our support team at help@slice.bank.in or +91-0000000000 slice small finance bank
""".trimIndent()

class ImportStatementUseCaseTest {
    private val transactions = FakeTransactions()
    private val useCase = ImportStatementUseCase(transactions, FakeAccounts())

    @Test
    fun aSameDayRowInAnotherCurrencyIsNotADuplicate() = runTest {
        val row = SliceSharedStatementParser().parse(STATEMENT).single()
        assertEquals(1_235L, row.amountMinor) // INR 12.35
        // JOD 1.235 stores the same integer on the same day; it isn't the same money.
        transactions.insert(
            SharedTransaction(
                amountMinor = 1_235L, merchantName = "Test Cafe", category = "Food & Dining",
                transactionType = SharedTransactionType.EXPENSE, occurredAtEpochMillis = row.timestampEpochMillis,
                currency = "JOD"
            )
        )

        val result = assertIs<SharedStatementImportResult.Success>(useCase.importFromText(STATEMENT))

        assertEquals(1, result.imported)
        assertEquals(0, result.skippedDuplicates)
        assertEquals("INR", transactions.rows.last().currency)
    }

    @Test
    fun aSameDayRowInTheSameCurrencyStillIsADuplicate() = runTest {
        val row = SliceSharedStatementParser().parse(STATEMENT).single()
        transactions.insert(
            SharedTransaction(
                amountMinor = 1_235L, merchantName = "Other Shop", category = "Shopping",
                transactionType = SharedTransactionType.EXPENSE, occurredAtEpochMillis = row.timestampEpochMillis,
                currency = "INR"
            )
        )

        val result = assertIs<SharedStatementImportResult.Success>(useCase.importFromText(STATEMENT))

        assertEquals(0, result.imported)
        assertEquals(1, result.skippedDuplicates)
    }
}
