package com.pennywiseai.tracker.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.yellow_dark
import com.pennywiseai.tracker.ui.theme.yellow_light

/**
 * The "Support development" UPI tip-jar dialog. Shared so both the Settings
 * entry and contextual F-Droid nudges present an identical ask. F-Droid builds
 * have everything unlocked (no Pro to sell), so this is a donation prompt —
 * never a gate.
 */
@Composable
fun SupportDevelopmentDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val vpa = stringResource(R.string.support_upi_vpa)
    val payeeName = stringResource(R.string.support_payee_name)
    val clipboard = LocalClipboardManager.current
    val copiedMsg = stringResource(R.string.support_copied_toast)
    val noUpiAppMsg = stringResource(R.string.support_no_upi_app)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Favorite, contentDescription = null, tint = yellow_dark) },
        title = { Text(stringResource(R.string.support_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    stringResource(R.string.support_dialog_body),
                    style = MaterialTheme.typography.bodyMedium
                )
                // Show the VPA so a user without a UPI app (or who'd rather pay
                // from their bank app) can copy it manually.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = vpa,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(vpa))
                        Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.support_copy_upi_id)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Only dismiss if a UPI app actually opened; otherwise keep the
                // dialog up so the copy-the-VPA fallback stays on screen.
                if (launchUpiPayment(context, vpa, payeeName, noUpiAppMsg)) onDismiss()
            }) {
                Text(stringResource(R.string.support_pay_via_upi))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.support_close)) }
        }
    )
}

/**
 * A subtle, tappable tonal card used to nudge F-Droid users toward the tip jar
 * at a power-feature moment (the F-Droid analog of the Play paywall). Kept low
 * key on purpose — it never blocks and is frequency-capped by the caller.
 */
@Composable
fun SupportNudgeCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PennyWiseCardV2(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = yellow_light),
        modifier = modifier.fillMaxWidth(),
        contentPadding = Spacing.md,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = null,
                tint = yellow_dark,
                modifier = Modifier.size(Dimensions.Icon.small)
            )
            Text(
                text = stringResource(R.string.support_nudge_body),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = yellow_dark,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = yellow_dark,
                modifier = Modifier.size(Dimensions.Icon.small)
            )
        }
    }
}

/**
 * Opens the user's UPI app pre-filled to pay the developer's [vpa] via the
 * standard `upi://pay` deep link (amount left blank). Falls back to a toast.
 * @return true if a UPI app was launched; false (with a toast) if none exists.
 */
fun launchUpiPayment(
    context: Context,
    vpa: String,
    payeeName: String,
    noUpiAppMessage: String
): Boolean {
    val uri = Uri.Builder()
        .scheme("upi")
        .authority("pay")
        .appendQueryParameter("pa", vpa)
        .appendQueryParameter("pn", payeeName)
        .appendQueryParameter("cu", "INR")
        .appendQueryParameter("tn", "PennyWise support")
        .build()
    val intent = Intent(Intent.ACTION_VIEW, uri)
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, noUpiAppMessage, Toast.LENGTH_LONG).show()
        false
    }
}
