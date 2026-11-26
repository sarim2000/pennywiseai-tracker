package com.pennywiseai.tracker.presentation.reimbursements

import com.pennywiseai.tracker.data.database.entity.ReimbursementStatus
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.utils.Money
import com.pennywiseai.tracker.utils.sumByCurrency

/*
 * Reimbursable expenses (#1389): money paid out that someone, often an employer,
 * pays back. A row is PENDING until then and REIMBURSED after, optionally linked
 * to the incoming payment. It's a label: totals count the row as before.
 */

/** What's still waiting to be paid back, one total per currency, never summed across them. */
fun pendingByCurrency(rows: List<TransactionEntity>): Map<String, Money> =
    rows.filter { it.reimbursementStatus == ReimbursementStatus.PENDING }
        .sumByCurrency({ it.currency }, { it.amount })
