package com.pennywiseai.tracker.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.pennywiseai.tracker.MainActivity
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.domain.spending.BudgetAlertTier
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.bidiIsolate
import com.pennywiseai.tracker.widget.inAppLanguage
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The budget and low-balance notifications, on one channel of their own. Tags
 * keep their per-budget and per-account ids apart from transaction ids, so a
 * newer alert for the same budget or account replaces the older one. Figures
 * stay off the lock screen, like the other alerts (#734).
 */
object MoneyAlertNotifications {
    private const val CHANNEL_ID = "money_alerts"
    private const val BUDGET_TAG = "budget_alert"
    private const val LOW_BALANCE_TAG = "low_balance"

    fun showBudgetAlert(context: Context, spending: BudgetGroupSpending, tier: BudgetAlertTier, currency: String) {
        val res = context.inAppLanguage()
        val name = res.bidiIsolate(spending.group.budget.name)
        fun money(amount: BigDecimal) = CurrencyFormatter.formatCurrency(amount, currency, maskIfHidden = false)
        val (title, text) = when (tier) {
            BudgetAlertTier.WARNING -> res.getString(
                R.string.budget_alert_warning_title,
                name,
                spending.totalActual.multiply(BigDecimal(100)).divide(spending.totalBudget, 0, RoundingMode.DOWN).toInt()
            ) to res.getString(
                R.string.budget_alert_warning_text,
                money(spending.remaining),
                money(spending.dailyAllowance)
            )
            BudgetAlertTier.OVER -> res.getString(R.string.budget_alert_over_title, name) to res.getString(
                R.string.budget_alert_over_text,
                money(spending.remaining.abs()),
                money(spending.totalBudget)
            )
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // The data URI keeps this PendingIntent apart from the budget widget's.
            data = "pennywise-alert://budgets".toUri()
            putExtra(MainActivity.EXTRA_OPEN_BUDGETS, true)
        }
        post(context, BUDGET_TAG, spending.group.budget.id.hashCode(), title, text, intent)
    }

    /** Opens Home, where the account's card already shows red. */
    fun showLowBalance(context: Context, account: AccountBalanceEntity) {
        val res = context.inAppLanguage()
        val currency = CurrencyFormatter.resolveAccountCurrency(account.sourceType, account.currency, account.bankName)
        val threshold = account.lowBalanceThreshold ?: return
        // The figure leads: in the title a long bank name would cut it off.
        val title = res.getString(
            R.string.low_balance_alert_title,
            CurrencyFormatter.formatCurrency(account.balance, currency, maskIfHidden = false)
        )
        val text = res.getString(
            R.string.low_balance_alert_text,
            res.bidiIsolate(
                account.alias?.takeIf { it.isNotBlank() }
                    ?: AccountBalanceEntity.accountLabel(account.bankName, account.accountLast4)
            ),
            CurrencyFormatter.formatCurrency(threshold, currency, maskIfHidden = false)
        )
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        post(context, LOW_BALANCE_TAG, "${account.bankName}|${account.accountLast4}".hashCode(), title, text, intent)
    }

    private fun post(context: Context, tag: String, id: Int, title: String, text: String, intent: Intent) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val res = context.inAppLanguage()
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                res.getString(R.string.money_alert_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = res.getString(R.string.money_alert_channel_description) }
        )
        manager.notify(
            tag,
            id,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        tag.hashCode(),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()
        )
    }
}
