package com.pennywiseai.tracker.presentation.share

import android.content.res.Resources
import android.graphics.Bitmap
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.share.SHARE_CARD_URL
import com.pennywiseai.tracker.data.share.ShareLinkError
import com.pennywiseai.tracker.data.share.ShareLinkExpiry
import com.pennywiseai.tracker.ui.components.ConnectedChoiceRow
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.AmountPrivacy
import kotlinx.coroutines.launch

/**
 * The last step before a card leaves the phone as a link.
 *
 * It shows the link-preview image exactly as it will be uploaded (the very pixels are
 * recorded from this preview), says in one line who can see it, and takes the expiry.
 * Nothing is sent until the button here is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShareLinkSheet(
    content: ShareCardContent,
    theme: ShareTheme,
    revealed: Boolean,
    onReveal: () -> Unit,
    state: PublishState,
    onPublish: (ShareLinkExpiry, Bitmap) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val ogLayer = rememberGraphicsLayer()
    var expiry by rememberSaveable { mutableStateOf(ShareLinkExpiry.DEFAULT) }
    val publishing = state == PublishState.Publishing

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensions.Padding.content)
                .navigationBarsPadding()
                .padding(bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(stringResource(R.string.share_link_sheet_title), style = MaterialTheme.typography.titleLarge)
            LinkImagePreview(content, theme, revealed, onReveal, Modifier.drawWithContent {
                // Recorded at the artboard's full 1200×630, from the pixels on screen.
                ogLayer.record { this@drawWithContent.drawContent() }
                drawLayer(ogLayer)
            })
            Text(
                text = stringResource(R.string.share_link_sheet_body, SHARE_CARD_URL),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionHeaderV2(title = stringResource(R.string.share_link_expiry), topSpacing = Spacing.none)
            ConnectedChoiceRow(
                options = ShareLinkExpiry.entries,
                selected = expiry,
                onSelect = { expiry = it },
                label = { expiryLabel(it) },
            )
            if (state is PublishState.Failed) {
                Text(
                    text = shareLinkErrorMessage(LocalResources.current, state.error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                enabled = !publishing,
                onClick = {
                    scope.launch { onPublish(expiry, ogLayer.toImageBitmap().asAndroidBitmap()) }
                },
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes(),
            ) {
                if (publishing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Dimensions.Icon.small),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                } else {
                    Icon(Icons.Default.Link, contentDescription = null)
                }
                Text(stringResource(R.string.share_link_create), modifier = Modifier.padding(start = Spacing.sm))
            }
        }
    }
}

/** The landscape card at the sheet's width, drawn at the density that makes it 1200×630 px. */
@Composable
private fun LinkImagePreview(
    content: ShareCardContent,
    theme: ShareTheme,
    revealed: Boolean,
    onReveal: () -> Unit,
    recordModifier: Modifier,
) {
    val description = stringResource(R.string.share_link_preview_description)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val scale = with(LocalDensity.current) { maxWidth.toPx() } / OG_WIDTH
        Box(
            Modifier
                .size(maxWidth, maxWidth * OG_HEIGHT / OG_WIDTH)
                .clip(MaterialTheme.shapes.medium)
                .border(Dimensions.Component.hairline, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            // Fixed density and font scale: the image is the same on every phone.
            CompositionLocalProvider(LocalDensity provides Density(OG_WIDTH / ShareCardLandscapeWidth.value, 1f)) {
                ShareCardLandscape(content, theme, Modifier.scaleTo(scale).then(recordModifier))
            }
            if (AmountPrivacy.hidden && !revealed) HiddenAmountsCover(onReveal)
        }
    }
}

@Composable
private fun expiryLabel(expiry: ShareLinkExpiry): String = when (val days = expiry.days) {
    null -> stringResource(R.string.share_link_expiry_never)
    else -> pluralStringResource(R.plurals.share_link_expiry_days, days, days)
}

/** What went wrong with a link, in words for the user. */
internal fun shareLinkErrorMessage(resources: Resources, error: ShareLinkError): String = when (error) {
    ShareLinkError.Network -> resources.getString(R.string.share_link_error_network, SHARE_CARD_URL)
    ShareLinkError.RateLimited -> resources.getString(R.string.share_link_error_rate_limited)
    ShareLinkError.TooLarge -> resources.getString(R.string.share_link_error_too_large)
    is ShareLinkError.Rejected -> resources.getString(R.string.share_link_error_rejected, error.reason)
    ShareLinkError.Forbidden -> resources.getString(R.string.shared_links_delete_forbidden)
    is ShareLinkError.Server -> resources.getString(R.string.share_link_error_server, error.status)
}
