package com.pennywiseai.tracker.data.ledger

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import java.math.BigDecimal

/**
 * The signed sum a manual account's balance is derived from (opening + this), over
 * rows already read: [direct] the account's own live rows, [transfers] the live
 * transfers naming its last 4. Only rows [counts] keeps add in. Direct rows: INCOME +,
 * anything else −. A standalone transfer moves its named legs; a marked pair's leg
 * moves only its own account. Shared by AccountBalanceRepository and MIGRATION_73_74,
 * so both read a row the same way.
 */
fun signedAccountSum(
    bankName: String,
    accountLast4: String,
    direct: List<TransactionEntity>,
    transfers: List<TransactionEntity>,
    counts: (TransactionEntity) -> Boolean
): BigDecimal {
    var sum = direct
        .filter { it.transactionType != TransactionType.TRANSFER && counts(it) }
        .fold(BigDecimal.ZERO) { acc, tx ->
            if (tx.transactionType == TransactionType.INCOME) acc + tx.amount else acc - tx.amount
        }
    for (tx in transfers.filter(counts)) {
        if (tx.isPairedTransferLeg) {
            if (tx.bankName == bankName && tx.accountNumber == accountLast4) {
                when (tx.ledgerType()) {
                    TransactionType.INCOME -> sum += tx.amount
                    TransactionType.EXPENSE -> sum -= tx.amount
                    else -> Unit
                }
            }
            continue
        }
        if (tx.toAccount == accountLast4 && (tx.toBankName == null || tx.toBankName == bankName)) sum += tx.amount
        if (tx.fromAccount == accountLast4 && (tx.fromBankName == null || tx.fromBankName == bankName)) sum -= tx.amount
    }
    return sum
}
