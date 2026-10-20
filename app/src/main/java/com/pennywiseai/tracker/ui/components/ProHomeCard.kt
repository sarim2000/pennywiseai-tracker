package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.yellow_dark
import com.pennywiseai.tracker.ui.theme.yellow_light
import com.pennywiseai.tracker.ui.theme.yellow_on_light

/**
 * The one-time Home card pointing engaged free users to Pro (#945).
 *
 * Uses the existing Pro accent (the Settings Pro row, the avatar ring, the UpgradeSheet
 * tiles) and nothing louder. Play is consumption-only, so no price, link or website here —
 * "See Pro" opens the in-app sheet, which is the one place the website is named.
 */
@Composable
fun ProHomeCard(
    onSeePro: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PennyWiseCardV2(
        modifier = modifier.fillMaxWidth(),
        containerColor = yellow_light,
        contentPadding = Spacing.none,
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, top = Spacing.md, end = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.smd),
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = yellow_dark,
                modifier = Modifier.size(Dimensions.Icon.medium),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    text = stringResource(R.string.pro_home_card_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = yellow_on_light,
                )
                Text(
                    text = stringResource(R.string.pro_home_card_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = yellow_on_light,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = Spacing.xs),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onSeePro,
                colors = ButtonDefaults.textButtonColors(contentColor = yellow_dark),
            ) {
                Text(stringResource(R.string.pro_home_card_action))
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.pro_home_card_dismiss),
                    tint = yellow_dark,
                    modifier = Modifier.size(Dimensions.Icon.small),
                )
            }
        }
    }
}
