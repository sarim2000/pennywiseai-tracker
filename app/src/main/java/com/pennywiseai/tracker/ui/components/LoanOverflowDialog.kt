package com.pennywiseai.tracker.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal

/**
 * Asks what the part of a loan repayment beyond what's owed is: ordinary money
 * ([onCountAsOwn], the default since #831) or a debt the other way
 * ([onFlipDebt]). Dismissing records nothing. [youPaid] is true when the
 * payment went out (repaying money you borrowed), false when it came in.
 * Used by the transaction and loan screens.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoanOverflowDialog(
    personName: String,
    youPaid: Boolean,
    extra: BigDecimal,
    currency: String,
    onCountAsOwn: () -> Unit,
    onFlipDebt: () -> Unit,
    onDismiss: () -> Unit
) {
    val extraText = CurrencyFormatter.formatCurrency(extra, currency)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.loan_overflow_title, extraText)) },
        text = {
            Text(
                stringResource(
                    if (youPaid) R.string.loan_overflow_message_paid else R.string.loan_overflow_message_received,
                    personName,
                    extraText
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onFlipDebt, shapes = ButtonDefaults.shapes()) {
                Text(
                    if (youPaid) {
                        stringResource(R.string.loan_overflow_they_owe, personName)
                    } else {
                        stringResource(R.string.loan_overflow_i_owe, personName)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCountAsOwn, shapes = ButtonDefaults.shapes()) {
                Text(
                    stringResource(if (youPaid) R.string.loan_overflow_own_spend else R.string.loan_overflow_own_income)
                )
            }
        }
    )
}
