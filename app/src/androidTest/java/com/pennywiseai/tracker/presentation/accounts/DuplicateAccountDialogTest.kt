package com.pennywiseai.tracker.presentation.accounts

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
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
            dao.retargetTransferLegRefs(ids, "000", "1000", time)
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
            dao.retargetTransferLegRefs(manualIds, "000", "1000", time)
            assertEquals("1000", dao.getTransactionById(manual)?.toAccount)
            assertEquals("3000", dao.getTransactionById(manual)?.fromAccount)
        } finally { database.close() }
    }

    @After fun clearSyntheticPreferences() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("account_prefs_merge_test", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun lightThemeMergePersistsAcrossStoreInstances() = checkDialog(dark = false, merge = true)
    @Test fun darkThemeKeepSeparateDismisses() = checkDialog(dark = true, merge = false)
}
