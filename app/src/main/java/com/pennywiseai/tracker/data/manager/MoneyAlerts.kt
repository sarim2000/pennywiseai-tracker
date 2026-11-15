package com.pennywiseai.tracker.data.manager

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity

/**
 * The money alerts [SmsTransactionProcessor] raises as a transaction arrives.
 * `worker` implements it (`worker/WorkManagerMoneyAlerts.kt`), so `data` doesn't
 * import a worker or a notification.
 */
interface MoneyAlerts {
    /** A live transaction was saved: queue the budget and manual-balance check. */
    fun onTransactionSaved()

    /** [account]'s reported balance just fell to its low-balance threshold. */
    fun showLowBalance(account: AccountBalanceEntity)
}
