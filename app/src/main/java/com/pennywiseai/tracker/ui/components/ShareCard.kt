package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import com.pennywiseai.tracker.R
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
    val hero = config.effectiveHero(data.subscriptionCount)

    val value = when (hero) {
        ShareHero.TRANSACTIONS -> data.transactionCount
        ShareHero.SUBSCRIPTIONS -> data.subscriptionCount
    }
    val caption = when (hero) {
        // "bank texts" carries the product. Without it the card says "15 — tracked" to
        // someone who has never heard of PennyWise, and the only line explaining what was
        // tracked is the smallest text on the image. The zero stays: the effort that
        // wasn't spent is the surprising half, and it still reads at thumbnail size.
        ShareHero.TRANSACTIONS -> "bank texts tracked. 0 typed."
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // A raster copy of the launcher icon, NOT R.mipmap.ic_launcher: that
                // resolves to an <adaptive-icon> XML on API 26+, and painterResource only
                // accepts VectorDrawables or rasters — it throws at composition time, which
                // a green build will not tell you. The mark itself is the right one: on a
                // card seen mostly by people who don't have the app, what's worth showing is
                // exactly what they'd tap on the Play Store listing, and it matches the
                // website header's treatment.
                Image(
                    painter = painterResource(id = R.drawable.share_card_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(7.dp)),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = "PENNYWISE",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = ShareCardColors.Text,
                    ),
                )
            }
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

        // One row per real category, each bar sized from the length of the name it hides.
        // An earlier version drew a fixed three-row pattern, which looked the same whether
        // you had two categories or forty — decoration imitating data. That's the one thing
        // this card cannot afford: its whole claim is that the structure is real and only
        // the values are withheld. Nothing to hide means no band, rather than a fake one.
        RedactionBand(data.topCategories)

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

/**
 * One redacted row per [categories] entry. The bar is sized from the length of the name it
 * covers, so the block genuinely varies with the user's own data rather than being a
 * decorative constant — the same relationship the app's masked account tails have to the
 * numbers behind them.
 */
@Composable
private fun RedactionBand(categories: List<String>) {
    if (categories.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        categories.take(3).forEach { name ->
            // Map name length onto a readable span. Clamped at both ends so a one-word
            // category still reads as a bar and a long one can't run to the edge.
            val width = (0.26f + name.length * 0.028f).coerceIn(0.26f, 0.62f)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(width)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(ShareCardColors.Redaction),
                )
                Box(
                    modifier = Modifier
                        .width(46.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(ShareCardColors.Redaction),
                )
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
