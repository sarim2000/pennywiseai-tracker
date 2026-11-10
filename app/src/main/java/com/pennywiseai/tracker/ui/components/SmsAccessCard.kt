package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing

/**
 * Top-of-Home card while SMS can't be read: nothing gets tracked without it, so it sits
 * above the fold rather than at the bottom of the list. Same layout as [PaydaySuggestionCard].
 */
@Composable
fun SmsAccessCard(
    access: SmsAccess,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSecondaryContainer
    PennyWiseCardV2(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentPadding = Spacing.none,
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, top = Spacing.md, end = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.smd),
        ) {
            Icon(
                imageVector = Icons.Default.MailOutline,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(Dimensions.Icon.medium),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    text = stringResource(R.string.sms_access_card_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = ink,
                )
                Text(
                    text = stringResource(
                        if (access.blocked) R.string.sms_access_blocked_hint
                        else R.string.sms_access_card_body
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = Spacing.xs),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = access.request, shapes = ButtonDefaults.shapes()) {
                Text(
                    stringResource(
                        if (access.blocked) R.string.sms_access_open_settings
                        else R.string.sms_access_allow
                    )
                )
            }
        }
    }
}
