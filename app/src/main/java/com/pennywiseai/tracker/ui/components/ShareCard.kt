package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pennywiseai.tracker.data.share.ShareCardConfig
import com.pennywiseai.tracker.data.share.ShareHero
import com.pennywiseai.tracker.presentation.share.ShareCardData

/**
 * The image users send to WhatsApp.
 *
 * Designed at ~260px chat-thumbnail scale and scaled up, not the other way round. The
 * previous version was calibrated against the in-app preview — the one context where this
 * card is never actually consumed — and when downscaled to a chat bubble almost nothing on
 * it was legible, the URL included. Everything here is sized to survive that reduction,
 * which is why there is so little of it.
 *
 * Two constraints carried over deliberately:
 *
 * 1. **Fixed palette, not Material You.** This is seen mostly by people who don't have the
 *    app; a card that looks different on every sender's phone isn't recognisable as
 *    anything.
 * 2. **No amounts, anywhere.** Counts only — which is also why nothing here touches
 *    CurrencyFormatter.
 */
object ShareCardColors {
    val Ink = Color(0xFF0B0E13)
    val Redaction = Color(0xFF1A222C)
    val Text = Color(0xFFEAF0F6)
    val Muted = Color(0xFF8894A4)
    val Amber = Color(0xFFFFB74D)
}

/** 4:5 — the aspect WhatsApp and Instagram both render without cropping. */
val ShareCardWidth = 360.dp
val ShareCardHeight = 450.dp

const val SHARE_CARD_URL = "pennywise.zynth.dev"

@Composable
fun ShareCard(
    config: ShareCardConfig,
    data: ShareCardData,
    modifier: Modifier = Modifier,
) {
    // A "0 subscriptions" card isn't worth sending, so fall back rather than publish a
    // zero the user never asked for.
    val hero = if (config.hero == ShareHero.SUBSCRIPTIONS && data.subscriptionCount == 0) {
        ShareHero.TRANSACTIONS
    } else {
        config.hero
    }

    val value = when (hero) {
        ShareHero.TRANSACTIONS -> data.transactionCount
        ShareHero.SUBSCRIPTIONS -> data.subscriptionCount
    }
    val caption = when (hero) {
        // Four words. The zero is the surprising half — the effort that wasn't spent —
        // and unlike the old three-line supporting text it still reads as a thumbnail.
        ShareHero.TRANSACTIONS -> "tracked. 0 typed."
        ShareHero.SUBSCRIPTIONS ->
            if (value == 1) "subscription I forgot" else "subscriptions I forgot"
    }

    Column(
        modifier = modifier
            .width(ShareCardWidth)
            .height(ShareCardHeight)
            .background(ShareCardColors.Ink)
            .padding(horizontal = 26.dp, vertical = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "PENNYWISE",
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = ShareCardColors.Text,
                ),
            )
            Text(
                text = data.periodLabel,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                    color = ShareCardColors.Muted,
                ),
            )
        }

        Spacer(Modifier.weight(0.9f))

        Text(
            text = value.toString(),
            style = TextStyle(
                // Deliberately enormous: at thumbnail scale this is the only element
                // guaranteed to read, so it carries the whole card.
                fontSize = 140.sp,
                lineHeight = 132.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-8).sp,
                color = ShareCardColors.Amber,
            ),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = caption,
            style = TextStyle(
                fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.4).sp,
                color = ShareCardColors.Text,
            ),
        )

        Spacer(Modifier.weight(1f))

        // The redaction motif, demoted from a labelled section to texture. It was the most
        // distinctive part of the first design and the least legible once stacked with
        // everything else; as a band it still reads as "a list with the values blacked out"
        // at full size and simply as texture when small — degrading rather than vanishing.
        RedactionBand()

        Spacer(Modifier.height(18.dp))

        Text(
            text = "Read from my bank SMS. Nothing left my phone.",
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = ShareCardColors.Muted,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = SHARE_CARD_URL,
            style = TextStyle(
                // The only element that brings anyone back, so it gets a size and contrast
                // that survive the same reduction as the hero instead of muted grey.
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                color = ShareCardColors.Text,
            ),
        )
    }
}

@Composable
private fun RedactionBand() {
    val rows = listOf(
        listOf(0.42f, 0.22f),
        listOf(0.30f, 0.36f),
        listOf(0.52f, 0.18f),
    )
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        rows.forEach { widths ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                widths.forEach { w ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(w)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ShareCardColors.Redaction),
                    )
                }
            }
        }
    }
}

// --- Previews. The thumbnail one is the size that matters: if it fails there it fails in
// --- a chat, however good it looks in the app.

private val previewData = ShareCardData(
    transactionCount = 312,
    topCategories = listOf("Food & Dining", "Shopping", "Transport"),
    subscriptionCount = 4,
    periodLabel = "JULY 2026",
)

@Preview(name = "Transactions", widthDp = 400, heightDp = 500)
@Composable
private fun ShareCardTransactionsPreview() {
    Box(Modifier.background(Color(0xFF303030)).padding(20.dp)) {
        ShareCard(ShareCardConfig(hero = ShareHero.TRANSACTIONS), previewData)
    }
}

@Preview(name = "Subscriptions", widthDp = 400, heightDp = 500)
@Composable
private fun ShareCardSubscriptionsPreview() {
    Box(Modifier.background(Color(0xFF303030)).padding(20.dp)) {
        ShareCard(ShareCardConfig(hero = ShareHero.SUBSCRIPTIONS), previewData)
    }
}

/** Chat-thumbnail scale — the size this card is genuinely read at. */
@Preview(name = "Thumbnail (chat size)", widthDp = 130, heightDp = 165)
@Composable
private fun ShareCardThumbnailPreview() {
    Box(Modifier.background(Color(0xFF303030))) {
        ShareCard(ShareCardConfig(), previewData)
    }
}
