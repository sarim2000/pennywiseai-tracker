package com.pennywiseai.tracker.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pennywiseai.tracker.MainActivity
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.challenges.ChallengeKind
import com.pennywiseai.tracker.domain.challenges.DayStatus
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.Typography
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * 2×2: the running Plan challenge, its days as dots, how many are done and how many are
 * left; its result until the user moves on from it in Plan; or a way to start one.
 * Every tap opens Plan → Challenges.
 */
class ChallengeWidget : GlanceAppWidget() {

    // Exact, so the dots take the width they're given.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = try {
            withTimeoutOrNull(5000L) { ChallengeWidgetDataStore.getData(context).first() } ?: ChallengeWidgetSnapshot()
        } catch (e: Exception) {
            android.util.Log.e("ChallengeWidget", "Failed to load widget data", e)
            ChallengeWidgetSnapshot()
        }
        val privacy = WidgetPrivacy.load(context)
        provideContent {
            // A session still alive when the worker writes recomposes from here.
            val snapshot by remember { ChallengeWidgetDataStore.getData(context) }.collectAsState(initial)
            WidgetRoot(privacy) { Content(snapshot) }
        }
    }

    @Composable
    private fun Content(snapshot: ChallengeWidgetSnapshot) {
        val context = LocalContext.current
        val open = openChallenges(context)
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(WidgetDefaults.padding)
                .clickable(open),
            contentAlignment = Alignment.Center,
        ) {
            when (snapshot.status) {
                ChallengeWidgetStatus.ACTIVE -> Active(snapshot)
                ChallengeWidgetStatus.WON -> WidgetEmptyState(
                    title = context.getString(R.string.challenge_won_title),
                    hint = context.challengeTitle(snapshot),
                )
                ChallengeWidgetStatus.ENDED -> WidgetEmptyState(
                    title = context.challengeTitle(snapshot),
                    hint = context.getString(
                        R.string.widget_challenge_ended,
                        context.latinQuantityString(R.plurals.challenge_lost_title, snapshot.target, snapshot.cleanDays, snapshot.target),
                    ),
                )
                ChallengeWidgetStatus.NONE -> WidgetEmptyState(
                    title = context.getString(R.string.widget_challenge_empty),
                    hint = context.getString(R.string.widget_challenge_empty_hint),
                    button = context.getString(R.string.challenge_start),
                    onButton = open,
                )
            }
        }
    }

    @Composable
    private fun Active(snapshot: ChallengeWidgetSnapshot) {
        val context = LocalContext.current
        val size = LocalSize.current
        // Centred, so it reads the same both ways; the dots run from the app's start edge.
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = context.challengeTitle(snapshot),
                maxLines = if (size.height >= TWO_LINE_TITLE) 2 else 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = Typography.titleSmall.fontSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
            )
            Spacer(GlanceModifier.height(Spacing.sm))
            DayDots(snapshot.days, size.width - WidgetDefaults.padding * 2)
            Spacer(GlanceModifier.height(Spacing.sm))
            Text(
                text = context.latinQuantityString(R.plurals.challenge_days_done, snapshot.target, snapshot.cleanDays, snapshot.target),
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = Typography.bodySmall.fontSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
            )
            Text(
                text = when {
                    snapshot.paused -> context.getString(R.string.widget_challenge_paused)
                    snapshot.daysLeft == 0 -> context.getString(R.string.widget_challenge_settling)
                    else -> context.latinQuantityString(R.plurals.widget_challenge_days_left, snapshot.daysLeft, snapshot.daysLeft)
                },
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = Typography.bodySmall.fontSize,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }

    /**
     * The window's days, first at the app's start edge, as Plan draws them: a check for a
     * clean day, a cross for a missed one, a ring for one still to settle, a faint dot to come.
     * Each dot sits centred in a slot a quarter wider than itself, as big as [width] allows.
     * Slots, not spacers between the dots: Glance draws at most 10 children in a Row, and
     * 7 dots with 6 spacers would lose the last days.
     */
    @Composable
    private fun DayDots(days: List<DayStatus>, width: Dp) {
        if (days.isEmpty()) return
        val dot = (width / (days.size * SLOT)).coerceIn(MIN_DOT, Dimensions.Icon.inline)
        WidgetRow(verticalAlignment = Alignment.CenterVertically) {
            days.forEach { status -> item { Day(status, dot) } }
        }
    }

    @Composable
    private fun Day(status: DayStatus, size: Dp) {
        val colors = GlanceTheme.colors
        val (shape, fill, glyph) = when (status) {
            DayStatus.CLEAN -> Triple(R.drawable.ic_widget_dot, colors.primary, R.drawable.ic_widget_check to colors.onPrimary)
            DayStatus.MISSED -> Triple(R.drawable.ic_widget_dot, colors.surfaceVariant, R.drawable.ic_widget_close to colors.onSurfaceVariant)
            DayStatus.PENDING -> Triple(R.drawable.ic_widget_ring, colors.primary, null)
            DayStatus.FUTURE -> Triple(R.drawable.ic_widget_dot, colors.surfaceVariant, null)
        }
        Box(modifier = GlanceModifier.width(size * SLOT).height(size), contentAlignment = Alignment.Center) {
            DayImage(shape, fill, size)
            glyph?.let { (icon, tint) -> DayImage(icon, tint, size * GLYPH) }
        }
    }

    @Composable
    private fun DayImage(icon: Int, tint: ColorProvider, size: Dp) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = GlanceModifier.size(size),
        )
    }

    private companion object {
        val MIN_DOT = 8.dp
        const val GLYPH = 0.7f

        /** A day's slot is this many dots wide: the dot and a quarter of one around it. */
        const val SLOT = 1.25f

        /** Below this the title keeps to one line, so the dots and both lines still fit. */
        val TWO_LINE_TITLE = 150.dp
    }
}

/** The challenge's name, as Plan titles it: "3 no-spend days", "A week without Food & Dining". */
private fun Context.challengeTitle(snapshot: ChallengeWidgetSnapshot): String = when (snapshot.kind) {
    ChallengeKind.NO_SPEND_DAYS -> latinQuantityString(R.plurals.challenge_no_spend_title, snapshot.target, snapshot.target)
    ChallengeKind.CATEGORY_WEEK -> getString(R.string.challenge_category_week_title, snapshot.categoryLabel.orEmpty())
}

/** getQuantityString with Latin digits, like [latinString]. */
private fun Context.latinQuantityString(@PluralsRes id: Int, quantity: Int, vararg args: Any): String =
    String.format(Locale.US, resources.getQuantityString(id, quantity), *args)

/** Plan → Challenges. The data URI keeps this PendingIntent apart from the other widgets'. */
private fun openChallenges(context: Context): Action = actionStartActivity(
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        data = "pennywise-widget://challenges".toUri()
        putExtra(MainActivity.EXTRA_OPEN_CHALLENGES, true)
    }
)

class ChallengeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChallengeWidget()

    /** Each update period (and the first add) works the days out again, so they move past midnight. */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        ChallengeWidgetUpdateWorker.enqueueOneShot(context)
    }
}
