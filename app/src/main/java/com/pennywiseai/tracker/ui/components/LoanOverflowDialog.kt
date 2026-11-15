package com.pennywiseai.tracker.presentation.loans

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal

/**
 * A payment that overshoots what's owed on a loan, waiting for the user to say
 * what the extra is. [direction] is the loan's direction before the payment.
 */
data class LoanOverflowPrompt(
    val personName: String,
    val direction: LoanDirection,
    val extra: BigDecimal,
    val currency: String
)

/**
 * Asks what the part of a repayment beyond what's owed is: ordinary money
 * ([onCountAsOwn], the default since #831) or a debt the other way
 * ([onFlipDebt]). Dismissing records nothing.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoanOverflowDialog(
    prompt: LoanOverflowPrompt,
    onCountAsOwn: () -> Unit,
    onFlipDebt: () -> Unit,
    onDismiss: () -> Unit
) {
    val extra = CurrencyFormatter.formatCurrency(prompt.extra, prompt.currency)
    // A borrowed loan is repaid by money you pay out; a lent one by money you receive.
    val youPaid = prompt.direction == LoanDirection.BORROWED
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.loan_overflow_title, extra)) },
        text = {
            Text(
                stringResource(
                    if (youPaid) R.string.loan_overflow_message_paid else R.string.loan_overflow_message_received,
                    prompt.personName,
                    extra
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onFlipDebt, shapes = ButtonDefaults.shapes()) {
                Text(
                    if (youPaid) {
                        stringResource(R.string.loan_overflow_they_owe, prompt.personName)
                    } else {
                        stringResource(R.string.loan_overflow_i_owe, prompt.personName)
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
