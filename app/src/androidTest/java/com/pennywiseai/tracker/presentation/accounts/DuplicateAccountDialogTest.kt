package com.pennywiseai.tracker.presentation.accounts

import android.content.Context
import android.content.ContextWrapper
import com.pennywiseai.tracker.data.mapper.withTransferAccount
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import kotlinx.coroutines.runBlocking
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.assertIsDisplayed
import com.pennywiseai.tracker.debug.UiVerificationActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.preferences.BankAccountMergeStore
import java.math.BigDecimal
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.After

class DuplicateAccountDialogTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @get:Rule val helper = MigrationTestHelper(instrumentation, PennyWiseDatabase::class.java,
        emptyList(), FrameworkSQLiteOpenHelperFactory())
    @get:Rule val compose = createAndroidComposeRule<UiVerificationActivity>()

    private fun checkDialog(dark: Boolean, merge: Boolean) {
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("${name}_merge_test", mode)
        }
        val bank = "Synthetic Test Bank"
        val source = AccountBalanceEntity(bankName = bank, accountLast4 = "000",
            balance = BigDecimal.ZERO, timestamp = LocalDateTime.of(2026, 1, 1, 0, 0))
        val target = source.copy(accountLast4 = "1000")
        var dismissed = false
        compose.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                DuplicateAccountDialog(source, target,
                    onMerge = { BankAccountMergeStore(context).remember(bank, "INR", "000", "1000") },
                    onDismiss = { dismissed = true })
            }
        }
        compose.onNodeWithText("Possible duplicate accounts").assertIsDisplayed()
        compose.onNodeWithText(if (merge) "Merge and remember" else "Keep separate").performClick()
        compose.runOnIdle {
            if (merge) {
                val store = BankAccountMergeStore(context)
                assertEquals("1000", store.resolveSuffix(bank, "INR", "000"))
                store.onMerge(target, target.copy(accountLast4 = "2000"))
                assertEquals("2000", BankAccountMergeStore(context).resolveSuffix(bank, "INR", "000"))
                store.onMerge(target.copy(accountLast4 = "2000"), target.copy(accountLast4 = "1999"))
                assertEquals("000", store.resolveSuffix(bank, "INR", "000"))
                store.remember(bank, "INR", "000", "1000")
                store.forgetAccount(bank, "1000")
                assertEquals("000", store.resolveSuffix(bank, "INR", "000"))
            } else assertTrue(dismissed)
        }
    }

    @Test fun mergingAnAliasDoesNotRetargetAnotherBanksTransfer() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, PennyWiseDatabase::class.java).build()
        try {
            val dao = database.transactionDao()
            val time = LocalDateTime.of(2026, 1, 1, 0, 0)
            val row = TransactionEntity(amount = BigDecimal.ONE, merchantName = "Example Shop", category = "Others",
                transactionType = TransactionType.TRANSFER, dateTime = time, transactionHash = "synthetic-a",
                bankName = "Example Bank", accountNumber = "000", fromAccount = "000", toAccount = "2000",
                reference = "000000000001")
            val first = dao.insertTransaction(row)
            val paired = dao.insertTransaction(row.copy(bankName = "Another Bank", accountNumber = "2000",
                transactionHash = "synthetic-paired", dateTime = time.plusMinutes(1)))
            val second = dao.insertTransaction(row.copy(bankName = "Another Bank", accountNumber = "2000",
                transactionHash = "synthetic-b", reference = "000000000002"))
            val otherCurrency = dao.insertTransaction(row.copy(bankName = "Another Bank", accountNumber = "2000",
                transactionHash = "synthetic-usd", currency = "USD"))
            val differentLegs = dao.insertTransaction(row.copy(bankName = "Another Bank", accountNumber = "3000",
                transactionHash = "synthetic-legs", toAccount = "3000"))
            val distant = dao.insertTransaction(row.copy(bankName = "Another Bank", accountNumber = "2000",
                transactionHash = "synthetic-distant", dateTime = time.plusDays(1)))
            val deleted = dao.insertTransaction(row.copy(isDeleted = true, transactionHash = "synthetic-c"))
            val ids = dao.getAccountTransferLegRefIds("Example Bank", "000")
            assertEquals(setOf(first, paired, deleted), ids.toSet())
            dao.retargetTransferLegRefs(ids, "Example Bank", "Example Bank", "000", "1000", time)
            assertEquals(2, dao.mergeAccountTransactions("Example Bank", "000", "Example Bank", "1000", time))
            assertEquals("1000", dao.getTransactionById(first)?.accountNumber)
            assertEquals("1000", dao.getTransactionById(first)?.fromAccount)
            assertEquals("1000", dao.getTransactionById(paired)?.fromAccount)
            assertEquals("2000", dao.getTransactionById(paired)?.accountNumber)
            assertEquals("1000", dao.getTransactionById(deleted)?.accountNumber)
            for (unrelated in listOf(second, otherCurrency, differentLegs, distant)) {
                assertEquals("000", dao.getTransactionById(unrelated)?.fromAccount)
            }
            val manual = dao.insertTransaction(row.copy(bankName = "Another Bank", accountNumber = "3000",
                fromAccount = "3000", toAccount = "000", reference = null, transactionHash = "synthetic-manual"))
            database.accountBalanceDao().insertBalance(AccountBalanceEntity(bankName = "Example Bank",
                accountLast4 = "000", balance = BigDecimal.ZERO, timestamp = time, transactionId = manual))
            val manualIds = dao.getAccountTransferLegRefIds("Example Bank", "000")
            assertEquals(listOf(manual), manualIds)
            dao.retargetTransferLegRefs(manualIds, "Example Bank", "Example Bank", "000", "1000", time)
            assertEquals("1000", dao.getTransactionById(manual)?.toAccount)
            assertEquals("3000", dao.getTransactionById(manual)?.fromAccount)
        } finally { database.close() }
    }

    @Test fun incomingManualTransferKeepsItsBankLinkAcrossMerges() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, PennyWiseDatabase::class.java).build()
        try {
            val balances = AccountBalanceRepository(db.accountBalanceDao(), db.transactionDao(), db)
            val time = LocalDateTime.of(2026, 1, 1, 0, 0)
            for (bank in listOf("Example Bank", "Another Bank")) {
                balances.seedManualAccount(AccountBalanceEntity(bankName = bank, accountLast4 = "000",
                    balance = BigDecimal.TEN, timestamp = time), BigDecimal.TEN)
            }
            val transfer = TransactionEntity(amount = BigDecimal.ONE, merchantName = "Example transfer", category = "Transfer",
                transactionType = TransactionType.TRANSFER, dateTime = time, transactionHash = "synthetic-manual-destination",
                bankName = "Another Bank", accountNumber = "000", fromAccount = "000", toAccount = "000")
            val id = balances.insertTransferWithBalance(transfer, "Another Bank", "000", "Example Bank", "000")
            assertEquals(BigDecimal("11"), balances.getLatestBalance("Example Bank", "000")?.balance)
            assertEquals(BigDecimal("9"), balances.getLatestBalance("Another Bank", "000")?.balance)
            val dao = db.transactionDao()
            assertEquals("Example Bank", dao.getTransactionById(id)?.toBankName)
            // Manual OPENING/MANUAL rows do not contain transaction IDs.
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM account_balances WHERE transaction_id IS NOT NULL").use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
            }
            val ids = dao.getAccountTransferLegRefIds("Example Bank", "000")
            assertEquals(listOf(id), ids)
            dao.retargetTransferLegRefs(ids, "Example Bank", "Target Bank", "000", "1000", time)
            val updated = dao.getTransactionById(id)!!
            assertEquals("1000", updated.toAccount)
            assertEquals("Target Bank", updated.toBankName)
            assertEquals("000", updated.fromAccount)
            assertEquals("Another Bank", updated.fromBankName)
            balances.seedManualAccount(AccountBalanceEntity(bankName = "Target Bank", accountLast4 = "1000",
                balance = BigDecimal.ZERO, timestamp = time), BigDecimal.ZERO)
            balances.recomputeManualBalance("Target Bank", "1000")
            assertEquals(BigDecimal.ONE, balances.getLatestBalance("Target Bank", "1000")?.balance)
        } finally { db.close() }
    }

    @Test fun editingTransferDestinationUpdatesBothManualBalances() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, PennyWiseDatabase::class.java).build()
        try {
            val balances = AccountBalanceRepository(db.accountBalanceDao(), db.transactionDao(), db)
            val time = LocalDateTime.of(2026, 1, 1, 0, 0)
            for ((bank, suffix) in listOf("Source Bank" to "3000", "Example Bank" to "1000", "Another Bank" to "2000")) {
                balances.seedManualAccount(AccountBalanceEntity(bankName = bank, accountLast4 = suffix,
                    balance = BigDecimal.TEN, timestamp = time), BigDecimal.TEN)
            }
            val tx = TransactionEntity(amount = BigDecimal.ONE, merchantName = "Example transfer", category = "Transfer",
                transactionType = TransactionType.TRANSFER, dateTime = time, transactionHash = "synthetic-edited-transfer",
                bankName = "Source Bank", accountNumber = "3000", fromAccount = "3000", toAccount = "1000")
            val id = balances.insertTransferWithBalance(tx, "Source Bank", "3000", "Example Bank", "1000")
            val original = db.transactionDao().getTransactionById(id)!!
            val edited = original.withTransferAccount("2000", "Another Bank", incoming = true)
            db.transactionDao().updateTransaction(edited)
            balances.applyTransactionBalanceShift(original, edited)
            assertEquals(BigDecimal.TEN, balances.getLatestBalance("Example Bank", "1000")?.balance)
            assertEquals(BigDecimal("11"), balances.getLatestBalance("Another Bank", "2000")?.balance)
            assertEquals(BigDecimal("9"), balances.getLatestBalance("Source Bank", "3000")?.balance)
            db.transactionDao().updateTransaction(edited.copy(isDeleted = true))
            balances.applyDeleteBalanceShift(edited)
            assertEquals(BigDecimal.TEN, balances.getLatestBalance("Another Bank", "2000")?.balance)
            assertEquals(BigDecimal.TEN, balances.getLatestBalance("Source Bank", "3000")?.balance)
        } finally { db.close() }
    }

    @Test fun renamingManualDestinationPreservesTransferUntilDeletion() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, PennyWiseDatabase::class.java).build()
        try {
            val balances = AccountBalanceRepository(db.accountBalanceDao(), db.transactionDao(), db)
            val time = LocalDateTime.of(2026, 1, 1, 0, 0)
            for ((bank, suffix) in listOf("Source Bank" to "3000", "Example Bank" to "1000")) {
                balances.seedManualAccount(AccountBalanceEntity(bankName = bank, accountLast4 = suffix,
                    balance = BigDecimal.TEN, timestamp = time), BigDecimal.TEN)
            }
            val tx = TransactionEntity(amount = BigDecimal.ONE, merchantName = "Example transfer", category = "Transfer",
                transactionType = TransactionType.TRANSFER, dateTime = time, transactionHash = "synthetic-renamed-transfer",
                bankName = "Source Bank", accountNumber = "3000", fromAccount = "3000", toAccount = "1000")
            val id = balances.insertTransferWithBalance(tx, "Source Bank", "3000", "Example Bank", "1000")
            balances.updateAccountBankName("Example Bank", "1000", "Renamed Bank")
            val renamed = db.transactionDao().getTransactionById(id)!!
            assertEquals("Renamed Bank", renamed.toBankName)
            assertEquals("Source Bank", renamed.fromBankName)
            assertNull(balances.getLatestBalance("Example Bank", "1000"))
            balances.updateManualBalanceAndCurrency("Renamed Bank", "1000", "INR", BigDecimal("11"))
            balances.recomputeManualBalance("Renamed Bank", "1000")
            assertEquals(BigDecimal("11"), balances.getLatestBalance("Renamed Bank", "1000")?.balance)
            db.transactionDao().updateTransaction(renamed.copy(isDeleted = true))
            balances.applyDeleteBalanceShift(renamed)
            assertEquals(BigDecimal.TEN, balances.getLatestBalance("Renamed Bank", "1000")?.balance)
        } finally { db.close() }
    }

    @Test fun upgradeBackfillsOnlyUnambiguousTransferBanks() {
        val name = "synthetic-transfer-bank-migration"
        try {
            helper.createDatabase(name, 63).apply {
                execSQL("""INSERT INTO account_balances (bank_name, account_last4, balance, timestamp, created_at)
                    VALUES ('Example Bank', '1000', '0', '2026-01-01T00:00:00', '2026-01-01T00:00:00'),
                           ('Another Bank', '2000', '0', '2026-01-01T00:00:00', '2026-01-01T00:00:00'),
                           ('Third Bank', '2000', '0', '2026-01-01T00:00:00', '2026-01-01T00:00:00')""")
                for ((id, suffix) in listOf(1 to "1000", 2 to "2000")) {
                    execSQL("""INSERT INTO transactions (id, amount, merchant_name, category, transaction_type, date_time,
                        transaction_hash, is_recurring, created_at, updated_at, bank_name, account_number, from_account, to_account)
                        VALUES ($id, '1', 'Example transfer', 'Transfer', 'TRANSFER', '2026-01-01T00:00:00',
                        'synthetic-$id', 0, '2026-01-01T00:00:00', '2026-01-01T00:00:00', 'Source Bank', '3000', '3000', '$suffix')""")
                }
                close()
            }
            helper.runMigrationsAndValidate(name, 64, true, PennyWiseDatabase.MIGRATION_63_64).use { db ->
                db.query("SELECT from_bank_name, to_bank_name FROM transactions ORDER BY id").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("Source Bank", it.getString(0)); assertEquals("Example Bank", it.getString(1))
                    assertTrue(it.moveToNext())
                    assertEquals("Source Bank", it.getString(0)); assertTrue(it.isNull(1))
                }
            }
        } finally { instrumentation.targetContext.deleteDatabase(name) }
    }

    @After fun clearSyntheticPreferences() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("account_prefs_merge_test", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun lightThemeMergePersistsAcrossStoreInstances() = checkDialog(dark = false, merge = true)
    @Test fun darkThemeKeepSeparateDismisses() = checkDialog(dark = true, merge = false)
}
