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
import androidx.activity.ComponentActivity
import android.view.WindowManager
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
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

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
        val activity = compose.activity
        activity.runOnUiThread {
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
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
            if (merge) assertEquals("1000", BankAccountMergeStore(context).resolveSuffix(bank, "INR", "000"))
            else assertTrue(dismissed)
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
                bankName = "Example Bank", accountNumber = "000", fromAccount = "000")
            val first = dao.insertTransaction(row)
            val second = dao.insertTransaction(row.copy(bankName = "Another Bank", transactionHash = "synthetic-b"))
            val deleted = dao.insertTransaction(row.copy(isDeleted = true, transactionHash = "synthetic-c"))
            assertEquals(2, dao.mergeAccountTransactions("Example Bank", "000", "Example Bank", "1000", time))
            dao.retargetTransferLegRefs("Example Bank", "000", "1000", time)
            assertEquals("1000", dao.getTransactionById(first)?.accountNumber)
            assertEquals("1000", dao.getTransactionById(first)?.fromAccount)
            assertEquals("1000", dao.getTransactionById(deleted)?.accountNumber)
            assertEquals("000", dao.getTransactionById(second)?.accountNumber)
            assertEquals("000", dao.getTransactionById(second)?.fromAccount)
        } finally { database.close() }
    }

    @After fun clearSyntheticPreferences() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("account_prefs_merge_test", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun lightThemeMergePersistsAcrossStoreInstances() = checkDialog(dark = false, merge = true)
    @Test fun darkThemeKeepSeparateDismisses() = checkDialog(dark = true, merge = false)
}
