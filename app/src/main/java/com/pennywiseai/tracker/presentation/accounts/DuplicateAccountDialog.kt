package com.pennywiseai.tracker.presentation.accounts

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity

@Composable
internal fun DuplicateAccountDialog(
    source: AccountBalanceEntity,
    target: AccountBalanceEntity,
    onMerge: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.duplicate_accounts_title)) },
        text = {
            Text(stringResource(
                R.string.duplicate_accounts_message,
                AccountBalanceEntity.accountLabel(source.bankName, source.accountLast4),
                AccountBalanceEntity.accountLabel(target.bankName, target.accountLast4)
            ))
        },
        confirmButton = {
            TextButton(onClick = onMerge) { Text(stringResource(R.string.duplicate_accounts_merge)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.duplicate_accounts_keep)) }
        }
    )
}
