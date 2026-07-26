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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pennywiseai.tracker.data.share.ShareCardConfig
import com.pennywiseai.tracker.presentation.share.ShareCardData

/**
 * The image users share to WhatsApp.
 *
 * Two deliberate departures from the rest of the app's UI:
 *
 * 1. **Fixed palette, not Material You.** Every other surface adapts to the user's
 *    wallpaper. This one must not: it is a brand artifact seen mostly by people who
 *    don't have the app, and a card that looks different on every sender's phone is
 *    not recognisable as anything.
 *
 * 2. **No amounts, anywhere.** Counts and category names only. Spending is the most
 *    private data a person has, and a card that exposed it would never be shared —
 *    which is also why nothing here touches CurrencyFormatter.
 *
 * Which sections appear is the user's choice ([ShareCardConfig]); the preview sheet
 * shows them the result before anything leaves the device.
 */
object ShareCardColors {
    val Ink = Color(0xFF0B0E13)
    val Surface = Color(0xFF151C25)
    val Line = Color(0xFF232C38)
    val Text = Color(0xFFEAF0F6)
    val Muted = Color(0xFF8894A4)
    val Amber = Color(0xFFFFB74D)
}

/** 4:5 — the aspect WhatsApp and Instagram both render without cropping. */
val ShareCardWidth = 360.dp
val ShareCardHeight = 450.dp

const val SHARE_CARD_URL = "pennywise.zynth.dev"

private enum class Section { TRANSACTIONS, CATEGORIES, SUBSCRIPTIONS }

@Composable
fun ShareCard(
    config: ShareCardConfig,
    data: ShareCardData,
    modifier: Modifier = Modifier,
) {
    val sections = buildList {
        if (config.showTransactions) add(Section.TRANSACTIONS)
        if (config.showCategories) add(Section.CATEGORIES)
        if (config.showSubscriptions) add(Section.SUBSCRIPTIONS)
    }

    // The canvas is fixed, so the hero shrinks as sections are added rather than the
    // content growing past the bottom edge. Bounding the height by construction is the
    // fix for having twice pushed the URL — the only element that brings anyone back —
    // off the card.
    val heroSize: TextUnit = when (sections.size) {
        1 -> 84.sp
        2 -> 64.sp
        else -> 52.sp
    }
    val subscriptionRows = when {
        sections.size >= 3 -> 2
        sections.firstOrNull() == Section.SUBSCRIPTIONS -> 4
        else -> 3
    }

    Column(
        modifier = modifier
            .width(ShareCardWidth)
            .height(ShareCardHeight)
            .background(ShareCardColors.Ink)
            .padding(horizontal = 28.dp, vertical = 26.dp),
    ) {
        Text(
            text = "PENNYWISE",
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.2.sp,
                color = ShareCardColors.Muted,
            ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = data.periodLabel,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
                color = ShareCardColors.Text,
            ),
        )

        Spacer(Modifier.height(18.dp))

        sections.forEachIndexed { index, section ->
            if (index > 0) Spacer(Modifier.height(16.dp))
            val isHero = index == 0
            when (section) {
                Section.TRANSACTIONS -> TransactionsSection(data.transactionCount, isHero, heroSize)
                Section.CATEGORIES -> CategoriesSection(data.topCategories, isHero)
                Section.SUBSCRIPTIONS ->
                    SubscriptionsSection(data.subscriptionCount, isHero, heroSize, subscriptionRows)
            }
        }

        // weight() alone collapses to nothing at max content, which is how a previous
        // version ended up with text glued to the footer. The fixed spacer is the floor.
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(14.dp))

        Text(
            text = "Found automatically from my bank SMS.\nThe details never left my phone.",
            style = TextStyle(
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = ShareCardColors.Muted,
            ),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = SHARE_CARD_URL,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = ShareCardColors.Text,
            ),
        )
    }
}

@Composable
private fun HeroNumber(value: String, caption: String, size: TextUnit) {
    // alignByBaseline rather than Alignment.Bottom: a large numeral carries a lot of
    // internal leading, so bottom-aligning leaves the word floating below the digit.
    Row {
        Text(
            text = value,
            modifier = Modifier.alignByBaseline(),
            style = TextStyle(
                fontSize = size,
                lineHeight = size,
                fontWeight = FontWeight.Black,
                letterSpacing = (-3).sp,
                color = ShareCardColors.Amber,
            ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = caption,
            modifier = Modifier.alignByBaseline(),
            style = TextStyle(
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = ShareCardColors.Text,
            ),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = TextStyle(
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
            color = ShareCardColors.Muted,
        ),
    )
}

@Composable
private fun TransactionsSection(count: Int, isHero: Boolean, heroSize: TextUnit) {
    if (isHero) {
        Column {
            HeroNumber(count.toString(), "transactions", heroSize)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "tracked automatically · 0 typed in",
                style = TextStyle(fontSize = 13.sp, color = ShareCardColors.Muted),
            )
        }
    } else {
        Column {
            SectionLabel("TRANSACTIONS")
            Spacer(Modifier.height(3.dp))
            Text(
                text = "$count tracked · 0 typed in",
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = ShareCardColors.Text,
                ),
            )
        }
    }
}

/**
 * Ranked by how often a category is used, not by how much was spent in it — summing
 * per-category amounts would break across currencies. The label says "most frequent"
 * because that is what the ranking actually is.
 */
@Composable
private fun CategoriesSection(categories: List<String>, isHero: Boolean) {
    Column {
        SectionLabel("MOST FREQUENT")
        Spacer(Modifier.height(3.dp))
        Text(
            text = categories.takeIf { it.isNotEmpty() }?.joinToString("  ·  ") ?: "—",
            style = TextStyle(
                fontSize = if (isHero) 22.sp else 15.sp,
                lineHeight = if (isHero) 28.sp else 20.sp,
                fontWeight = if (isHero) FontWeight.Bold else FontWeight.Medium,
                color = ShareCardColors.Text,
            ),
        )
    }
}

@Composable
private fun SubscriptionsSection(
    count: Int,
    isHero: Boolean,
    heroSize: TextUnit,
    rows: Int,
) {
    Column {
        if (isHero) {
            HeroNumber(
                count.toString(),
                if (count == 1) "subscription" else "subscriptions",
                heroSize,
            )
        } else {
            SectionLabel("SUBSCRIPTIONS")
            Spacer(Modifier.height(3.dp))
            Text(
                text = "$count found",
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = ShareCardColors.Text,
                ),
            )
        }

        if (count > 0) {
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val visible = count.coerceAtMost(rows)
                // Varied widths so the block reads as a list of different names rather
                // than a loading skeleton.
                val widths = listOf(0.62f, 0.44f, 0.71f, 0.52f)
                repeat(visible) { i -> RedactedRow(widths[i % widths.size]) }
                if (count > rows) {
                    Text(
                        text = "+${count - rows} more",
                        style = TextStyle(fontSize = 11.sp, color = ShareCardColors.Muted),
                    )
                }
            }
        }
    }
}

/**
 * One subscription, structurally intact and completely unreadable: a bar where the
 * merchant name would be, and a masked chip where the amount would be. Borrowed from
 * the masked account tails the app already renders everywhere.
 */
@Composable
private fun RedactedRow(nameWidth: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(nameWidth)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ShareCardColors.Surface),
        )
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ShareCardColors.Line),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "••••",
                style = TextStyle(
                    fontSize = 8.sp,
                    color = ShareCardColors.Muted,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

// --- Previews for the extremes, so clipping shows up here rather than only after a
// --- build-install-navigate-export cycle on a device.

private val previewData = ShareCardData(
    transactionCount = 312,
    topCategories = listOf("Food & Dining", "Shopping", "Transport"),
    subscriptionCount = 6,
    periodLabel = "JULY 2026",
)

@Preview(name = "All three sections", widthDp = 400, heightDp = 500)
@Composable
private fun ShareCardAllPreview() {
    Box(Modifier.background(Color(0xFF303030)).padding(20.dp)) {
        ShareCard(ShareCardConfig(), previewData)
    }
}

@Preview(name = "Transactions only", widthDp = 400, heightDp = 500)
@Composable
private fun ShareCardTransactionsPreview() {
    Box(Modifier.background(Color(0xFF303030)).padding(20.dp)) {
        ShareCard(
            ShareCardConfig(showCategories = false, showSubscriptions = false),
            previewData,
        )
    }
}

@Preview(name = "Subscriptions only", widthDp = 400, heightDp = 500)
@Composable
private fun ShareCardSubscriptionsPreview() {
    Box(Modifier.background(Color(0xFF303030)).padding(20.dp)) {
        ShareCard(
            ShareCardConfig(showTransactions = false, showCategories = false),
            previewData,
        )
    }
}

@Preview(name = "Categories only", widthDp = 400, heightDp = 500)
@Composable
private fun ShareCardCategoriesPreview() {
    Box(Modifier.background(Color(0xFF303030)).padding(20.dp)) {
        ShareCard(
            ShareCardConfig(showTransactions = false, showSubscriptions = false),
            previewData,
        )
    }
}
