package com.pennywiseai.tracker.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.tracker.ui.theme.Spacing

/**
 * F-Droid's half of the tip jar: buying a Pro key on the website. That's the
 * only route open to anyone without UPI, and a key also unlocks the Play build
 * if they ever switch.
 *
 * This lives in the fdroid source set rather than behind a runtime flag so the
 * Play build never packages the URL or the copy at all — Play's Payments policy
 * forbids that build from carrying a purchase link, and an inactive `if` branch
 * still ships the strings.
 *
 * @param onOpened called once the browser has actually been launched.
 */
@Composable
fun SupportWebOption(onOpened: () -> Unit) {
    val context = LocalContext.current
    val noBrowserMsg = stringResource(R.string.support_no_browser)

    Text(
        stringResource(R.string.support_dialog_body_web),
        style = MaterialTheme.typography.bodyMedium
    )
    TextButton(
        onClick = { if (openUrl(context, PRO_PAGE_URL, noBrowserMsg)) onOpened() },
        contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = Spacing.none),
    ) {
        Text(
            text = stringResource(R.string.support_get_pro_web),
            fontWeight = FontWeight.Medium,
        )
    }
}

private val PRO_PAGE_URL = "${Constants.Links.WEB_PARSER_URL}/pro"

/**
 * Opens [url] in the user's browser.
 * @return true if something handled it; false (with a toast) if nothing did.
 */
private fun openUrl(context: Context, url: String, noBrowserMessage: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    true
} catch (e: ActivityNotFoundException) {
    Toast.makeText(context, noBrowserMessage, Toast.LENGTH_LONG).show()
    false
}
