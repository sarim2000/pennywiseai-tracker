package com.pennywiseai.tracker.worker

import android.content.Context
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.manager.MoneyAlerts
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** [MoneyAlerts] backed by [MoneyAlertWorker] and [MoneyAlertNotifications]. */
class WorkManagerMoneyAlerts @Inject constructor(
    @ApplicationContext private val context: Context
) : MoneyAlerts {

    override fun onTransactionSaved() = MoneyAlertWorker.enqueue(context)

    override fun showLowBalance(account: AccountBalanceEntity) =
        MoneyAlertNotifications.showLowBalance(context, account)
}
