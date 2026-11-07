package com.pennywiseai.tracker.benchmark

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pennywiseai.tracker.BuildConfig
import com.pennywiseai.tracker.data.database.dao.AccountBalanceDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import java.math.BigDecimal
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Benchmark variants only (never in release): puts the app past onboarding with
 * synthetic multi-currency data, so generation and benchmarks start on a real Home.
 * Safe to repeat: the row hashes are fixed and inserts ignore conflicts.
 *   adb shell am broadcast -f 32 -n <pkg>/com.pennywiseai.tracker.benchmark.BenchmarkSeedReceiver
 */
@AndroidEntryPoint
class BenchmarkSeedReceiver : BroadcastReceiver() {
    @Inject lateinit var transactions: TransactionDao
    @Inject lateinit var balances: AccountBalanceDao
    @Inject lateinit var preferences: UserPreferencesRepository

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                seed()
                result.resultCode = Activity.RESULT_OK
            } finally {
                result.finish()
            }
        }
    }

    private suspend fun seed() {
        preferences.updateHasCompletedOnboarding(true)
        preferences.updateSkippedSmsPermission(true)
        preferences.markScanTutorialShown()
        preferences.setAppLockEnabled(false)
        // What's New would otherwise cover the journeys after a version bump.
        preferences.setLastSeenAppVersion(BuildConfig.VERSION_NAME)

        val now = LocalDateTime.now()
        transactions.insertTransactions(List(ROWS) { i -> transaction(i, now) })
        ACCOUNTS.forEach { (bank, last4, currency) ->
            balances.insertBalance(
                AccountBalanceEntity(
                    bankName = bank,
                    accountLast4 = last4,
                    balance = BigDecimal("12500.00"),
                    // Stable, so seeding again replaces these rows instead of adding more.
                    timestamp = now.toLocalDate().atStartOfDay(),
                    currency = currency,
                )
            )
        }
    }

    private fun transaction(i: Int, now: LocalDateTime): TransactionEntity {
        val (bank, last4, currency) = ACCOUNTS[i % ACCOUNTS.size]
        val (merchant, category) = MERCHANTS[i % MERCHANTS.size]
        val isIncome = i % 40 == 0
        return TransactionEntity(
            amount = BigDecimal(100 + i * 37 % 9000).movePointLeft(1),
            merchantName = if (isIncome) "Demo Payroll" else merchant,
            category = if (isIncome) "Salary" else category,
            transactionType = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
            // About five a day across the last year, newest first.
            dateTime = now.minusHours(i * 24L * DAYS / ROWS),
            bankName = bank,
            accountNumber = last4,
            transactionHash = "benchmark-seed-$i",
            currency = currency,
        )
    }

    private companion object {
        const val ROWS = 1800
        const val DAYS = 365L
        val ACCOUNTS = listOf(
            Triple("Demo Bank", "0001", "INR"),
            Triple("Demo Bank", "0001", "INR"),
            Triple("Sample Credit Union", "0002", "USD"),
            Triple("Example Bank", "0003", "AED"),
        )
        val MERCHANTS = listOf(
            "Demo Grocer" to "Groceries",
            "Sample Cafe" to "Food & Dining",
            "Example Transit" to "Transportation",
            "Demo Mart" to "Shopping",
            "Sample Cinema" to "Entertainment",
            "Example Power Co" to "Bills & Utilities",
        )
    }
}
