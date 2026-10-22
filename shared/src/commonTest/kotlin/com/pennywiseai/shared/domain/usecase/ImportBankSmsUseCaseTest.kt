package com.pennywiseai.shared.domain.usecase

import com.pennywiseai.shared.data.local.entity.SharedAccountBalanceEntity
import com.pennywiseai.shared.data.local.entity.SharedCardEntity
import com.pennywiseai.shared.data.local.entity.SharedMerchantMappingEntity
import com.pennywiseai.shared.data.local.entity.SharedRuleApplicationEntity
import com.pennywiseai.shared.data.local.entity.SharedRuleEntity
import com.pennywiseai.shared.data.model.SharedCategory
import com.pennywiseai.shared.data.model.SharedTransaction
import com.pennywiseai.shared.data.model.SharedTransactionType
import com.pennywiseai.shared.data.repository.SharedAccountRepository
import com.pennywiseai.shared.data.repository.SharedCategoryRepository
import com.pennywiseai.shared.data.repository.SharedMerchantMappingRepository
import com.pennywiseai.shared.data.repository.SharedRuleRepository
import com.pennywiseai.shared.data.repository.SharedTransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// SMS bodies follow parser-core's test formats; every number in them is fake.
private const val HDFC_UPI =
    "Sent Rs.450.00 From HDFC Bank A/C *1234 To SWIGGY On 22/10/26 Ref 000011112222 Not You? Call 18000000000/SMS BLOCK UPI to 7000000000"
private const val HDFC_NEFT_WITH_BALANCE =
    "Update! INR 1,500.00 deposited in HDFC Bank A/c XX9999 on 22-OCT-26 for NEFT Cr-TEST0000000-ACME TECHNOLOGIES-PERSON NAME-TEST00000000000.Avl bal INR 8,250.50. Cheque deposits in A/C are subject to clearing"
private const val KOTAK_UPI =
    "Sent Rs.15.00 from Kotak Bank AC X1234 to paytmqr000000000000@paytm on 22-10-26.UPI Ref 0000000000. Not you, https://kotak.com/KBANKT/Fraud"
private const val RECEIVED_AT = 1_792_000_000_000L

class ImportBankSmsUseCaseTest {
    private val transactions = FakeTransactions()
    private val accounts = FakeAccounts()
    private val mappings = FakeMappings()
    private val rules = FakeRules()
    private val useCase = ImportBankSmsUseCase(transactions, accounts, mappings, rules, FakeCategories())

    @Test
    fun savesAParsedBankSms() = runTest {
        val result = useCase.execute("AX-HDFCBK-S", HDFC_UPI, RECEIVED_AT)

        val saved = assertIs<BankSmsImportResult.Saved>(result).transaction
        assertEquals(45_000L, saved.amountMinor)
        assertEquals("INR", saved.currency)
        assertEquals("Swiggy", saved.merchantName)
        assertEquals("Food & Dining", saved.category)
        assertEquals(SharedTransactionType.EXPENSE, saved.transactionType)
        assertEquals("HDFC Bank", saved.bankName)
        assertEquals("1234", saved.accountLast4)
        assertEquals(RECEIVED_AT, saved.occurredAtEpochMillis)
        assertEquals(listOf(saved), transactions.rows)
        assertEquals(emptyList(), accounts.balances) // no balance in the SMS, no made-up 0 balance
    }

    @Test
    fun theSameSmsTwiceIsADuplicate() = runTest {
        useCase.execute("AX-HDFCBK-S", HDFC_UPI, RECEIVED_AT)

        // A later delivery of the same text (different timestamp) still dedupes, as on Android.
        assertEquals(BankSmsImportResult.Duplicate, useCase.execute("AX-HDFCBK-S", HDFC_UPI, RECEIVED_AT + 60_000))
        assertEquals(1, transactions.rows.size)
    }

    @Test
    fun theSameSmsWithAndWithoutItsSenderIsADuplicate() = runTest {
        useCase.execute("AX-HDFCBK-S", HDFC_UPI, RECEIVED_AT)

        // One automation passes the sender, another doesn't (it's then guessed from the body).
        assertEquals(BankSmsImportResult.Duplicate, useCase.execute(null, HDFC_UPI, RECEIVED_AT))
        assertEquals(BankSmsImportResult.Duplicate, useCase.execute("HDFCBK", HDFC_UPI, RECEIVED_AT))
        assertEquals(1, transactions.rows.size)
    }

    @Test
    fun aDeletedTransactionIsNotBroughtBack() = runTest {
        val saved = assertIs<BankSmsImportResult.Saved>(useCase.execute("AX-HDFCBK-S", HDFC_UPI, RECEIVED_AT))
        transactions.rows[0] = saved.transaction.copy(isDeleted = true)

        assertEquals(BankSmsImportResult.Duplicate, useCase.execute("AX-HDFCBK-S", HDFC_UPI, RECEIVED_AT))
    }

    @Test
    fun aPersonalMessageIsNotATransaction() = runTest {
        assertEquals(BankSmsImportResult.NotATransaction, useCase.execute("+910000000000", "See you at 6?", RECEIVED_AT))
        assertEquals(BankSmsImportResult.NotATransaction, useCase.execute(null, "See you at 6?", RECEIVED_AT))
        assertEquals(emptyList(), transactions.rows)
    }

    @Test
    fun anOtpFromAKnownBankIsUnparsed() = runTest {
        val otp = "000000 is the OTP for your HDFC Bank transaction. Valid for 5 mins. Do not share it with anyone."

        assertEquals(BankSmsImportResult.Unparsed("HDFC Bank"), useCase.execute("AX-HDFCBK-S", otp, RECEIVED_AT))
        assertEquals(emptyList(), transactions.rows)
    }

    @Test
    fun theSenderIsInferredFromTheBodyWhenShortcutsOmitsIt() = runTest {
        val saved = assertIs<BankSmsImportResult.Saved>(useCase.execute(null, KOTAK_UPI, RECEIVED_AT)).transaction

        assertEquals("Kotak Bank", saved.bankName)
        assertEquals(1_500L, saved.amountMinor)
    }

    @Test
    fun theReportedBalanceUpdatesTheAccount() = runTest {
        val saved = assertIs<BankSmsImportResult.Saved>(
            useCase.execute("CP-HDFCBK-S", HDFC_NEFT_WITH_BALANCE, RECEIVED_AT)
        ).transaction

        assertEquals(SharedTransactionType.INCOME, saved.transactionType)
        assertEquals(150_000L, saved.amountMinor)
        val balance = accounts.balances.single()
        assertEquals("HDFC Bank", balance.bankName)
        assertEquals("9999", balance.accountLast4)
        assertEquals(825_050L, balance.balanceMinor)
        assertEquals("INR", balance.currency)
        assertEquals(saved.id, balance.transactionId)
    }

    @Test
    fun anSmsWithoutABalanceLeavesAccountsAlone() = runTest {
        accounts.balances += balanceRow(balanceMinor = 99_00L)

        useCase.execute("AX-HDFCBK-S", HDFC_UPI, RECEIVED_AT)

        assertEquals(listOf(99_00L), accounts.balances.map { it.balanceMinor })
    }

    @Test
    fun aMerchantMappingOverridesTheDefaultCategory() = runTest {
        mappings.rows["Swiggy"] = "Groceries"

        val saved = assertIs<BankSmsImportResult.Saved>(useCase.execute("AX-HDFCBK-S", HDFC_UPI, RECEIVED_AT))

        assertEquals("Groceries", saved.transaction.category)
    }

    @Test
    fun anIncomeCategoryMappingIsNotAppliedToASpend() = runTest {
        mappings.rows["Swiggy"] = "Salary"

        val saved = assertIs<BankSmsImportResult.Saved>(useCase.execute("AX-HDFCBK-S", HDFC_UPI, RECEIVED_AT))

        assertEquals("Food & Dining", saved.transaction.category)
    }

    @Test
    fun aSmartRuleWinsOverTheMapping() = runTest {
        mappings.rows["Swiggy"] = "Groceries"
        rules.rows += SharedRuleEntity(
            id = "r1", name = "Work lunches", priority = 1,
            conditionsJson = """{"type":"merchant_contains","value":"swig"}""",
            actionsJson = """{"category":"Travel","transactionType":"TRANSFER"}""",
            isActive = true, isSystemTemplate = false, createdAtEpochMillis = 0L, updatedAtEpochMillis = 0L
        )

        val saved = assertIs<BankSmsImportResult.Saved>(useCase.execute("AX-HDFCBK-S", HDFC_UPI, RECEIVED_AT)).transaction

        assertEquals("Travel", saved.category)
        assertEquals(SharedTransactionType.TRANSFER, saved.transactionType)
    }

    @Test
    fun plainAmountsBecomeMinorUnits() {
        assertEquals(45_000L, ImportBankSmsUseCase.toMinor("450.00"))
        assertEquals(-12_345L, ImportBankSmsUseCase.toMinor("-123.45"))
        assertEquals(50L, ImportBankSmsUseCase.toMinor("0.50"))
    }
}

private fun balanceRow(balanceMinor: Long) = SharedAccountBalanceEntity(
    bankName = "HDFC Bank", accountLast4 = "1234", timestampEpochMillis = 0L,
    balanceMinor = balanceMinor, currency = "INR", createdAtEpochMillis = 0L
)

private class FakeTransactions : SharedTransactionRepository {
    val rows = mutableListOf<SharedTransaction>()
    override fun observeTransactions(): Flow<List<SharedTransaction>> = flowOf(rows.toList())
    override suspend fun getById(id: Long) = rows.firstOrNull { it.id == id }
    override suspend fun getByHash(hash: String) = rows.firstOrNull { it.transactionHash == hash }
    override suspend fun getByReference(reference: String) = rows.firstOrNull { it.reference == reference }
    override suspend fun getByAmountAndDate(amountMinor: Long, startEpochMillis: Long, endEpochMillis: Long) =
        rows.filter { it.amountMinor == amountMinor && it.occurredAtEpochMillis in startEpochMillis..endEpochMillis }
    override suspend fun insert(transaction: SharedTransaction): Long {
        val id = rows.size + 1L
        rows += transaction.copy(id = id)
        return id
    }
    override suspend fun insertAll(transactions: List<SharedTransaction>) { transactions.forEach { insert(it) } }
    override suspend fun update(transaction: SharedTransaction) {}
    override suspend fun softDelete(id: Long, updatedAtEpochMillis: Long) {}
}

private class FakeAccounts : SharedAccountRepository {
    val balances = mutableListOf<SharedAccountBalanceEntity>()
    override fun observeBalances(): Flow<List<SharedAccountBalanceEntity>> = flowOf(balances.toList())
    override fun observeCards(): Flow<List<SharedCardEntity>> = flowOf(emptyList())
    override suspend fun insertBalance(balance: SharedAccountBalanceEntity): Long {
        balances += balance
        return balances.size.toLong()
    }
    override suspend fun upsertCard(card: SharedCardEntity) = 0L
    override suspend fun getLatestBalance(bankName: String, accountLast4: String) =
        balances.lastOrNull { it.bankName == bankName && it.accountLast4 == accountLast4 }
    override suspend fun getDistinctAccounts() = balances.toList()
    override suspend fun deleteBalanceById(id: Long) {}
    override suspend fun deleteByAccount(bankName: String, accountLast4: String) {}
    override suspend fun deleteCardById(id: Long) {}
    override suspend fun renameAccount(oldBankName: String, newBankName: String, accountLast4: String) {}
}

private class FakeMappings : SharedMerchantMappingRepository {
    val rows = mutableMapOf<String, String>()
    override fun observeAll(): Flow<List<SharedMerchantMappingEntity>> = flowOf(emptyList())
    override suspend fun getByMerchant(merchantName: String) =
        rows[merchantName]?.let { SharedMerchantMappingEntity(merchantName, it, 0L, 0L) }
    override suspend fun upsert(mapping: SharedMerchantMappingEntity) {}
}

private class FakeRules : SharedRuleRepository {
    val rows = mutableListOf<SharedRuleEntity>()
    override fun observeRules(): Flow<List<SharedRuleEntity>> = flowOf(rows.toList())
    override fun observeRuleApplications(): Flow<List<SharedRuleApplicationEntity>> = flowOf(emptyList())
    override suspend fun upsertRule(rule: SharedRuleEntity) {}
    override suspend fun deleteRuleById(ruleId: String) {}
    override suspend fun addApplication(application: SharedRuleApplicationEntity) {}
}

private class FakeCategories : SharedCategoryRepository {
    private val categories = listOf("Food & Dining" to false, "Groceries" to false, "Travel" to false, "Salary" to true)
        .map { (name, isIncome) -> SharedCategory(name = name, colorHex = "#000000", isIncome = isIncome, createdAtEpochMillis = 0L, updatedAtEpochMillis = 0L) }
    override fun observeCategories(): Flow<List<SharedCategory>> = flowOf(categories)
    override fun observeCategoriesByIncomeType(isIncome: Boolean) = flowOf(categories.filter { it.isIncome == isIncome })
    override suspend fun getById(id: Long): SharedCategory? = null
    override suspend fun countCategories() = categories.size
    override suspend fun insertCategories(categories: List<SharedCategory>) {}
    override suspend fun insertCategory(category: SharedCategory) = 0L
    override suspend fun updateCategory(category: SharedCategory) {}
    override suspend fun deleteCategory(id: Long) = false
}
