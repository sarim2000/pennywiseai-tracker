package com.pennywiseai.tracker.widget

import android.content.Context
import android.content.Intent
import android.icu.text.RelativeDateTimeFormatter
import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
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
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import com.pennywiseai.tracker.MainActivity
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.canReadSms
import com.pennywiseai.tracker.utils.canReceiveSms
import com.pennywiseai.tracker.utils.isBackgroundRestricted
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * "Is PennyWise still reading my bank SMS?" at a glance: what stops tracking, or
 * when it last read one. The SMS check-up's verdict ([trackingCheckup]); a tap opens
 * the check-up, where every fix is. No amounts.
 */
class TrackingCheckupWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(NARROW, WIDE, NARROW_TALL, WIDE_TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Nothing announces a permission or background change, so read them on every update.
        val smsGranted = context.canReadSms() && context.canReceiveSms()
        val backgroundRestricted = context.isBackgroundRestricted()
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val scan = entry.scanPreferences()
        val checkups = combine(
            scan.lastScanRecord,
            scan.lastLiveSmsAt,
            entry.smsScanRunning().isScanRunning(),
        ) { record, lastLiveSmsAt, running ->
            trackingCheckup(smsGranted, backgroundRestricted, record, running, lastLiveSmsAt)
        }
        val initial = withTimeoutOrNull(5000L) { checkups.first() }
        val privacy = WidgetPrivacy.load(context)
        provideContent {
            val checkup by remember { checkups }.collectAsState(initial)
            WidgetRoot(privacy) {
                Content(checkup)
            }
        }
    }

    @Composable
    private fun Content(checkup: TrackingCheckup?) {
        val context = LocalContext.current
        val wide = LocalSize.current.width >= WIDE.width
        // A two-cell row is too narrow for "Background restricted" or "Last SMS read 2 hours
        // ago" on one line; most launchers' rows have the height to wrap them.
        val lines = if (LocalSize.current.height >= NARROW_TALL.height) 2 else 1
        WidgetRow(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(horizontal = WidgetDefaults.padding, vertical = Spacing.xs)
                .clickable(openCheckup(context)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The icon only once there's room: at two narrow cells the words need all of it.
            if (checkup != null && wide) {
                item { StatusIcon(checkup.health) }
                item { Spacer(GlanceModifier.width(Spacing.smd)) }
            }
            item {
                Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = startAlignment()) {
                    if (checkup == null) {
                        WidgetTitle(context.getString(R.string.widget_name_tracking), maxLines = lines)
                    } else {
                        WidgetTitle(context.getString(titleRes(checkup.health)), maxLines = lines)
                        WidgetLabel(detail(context, checkup), maxLines = lines)
                    }
                }
            }
        }
    }

    @Composable
    private fun StatusIcon(health: TrackingHealth) {
        Image(
            provider = ImageProvider(iconRes(health)),
            // The title next to it says the same.
            contentDescription = null,
            colorFilter = ColorFilter.tint(PennyWiseWidgetTheme.trackingColor(health)),
            modifier = GlanceModifier.size(Dimensions.Icon.medium)
        )
    }

    private companion object {
        val NARROW = DpSize(110.dp, 40.dp)
        val WIDE = DpSize(150.dp, 40.dp)

        /** Room for two lines of title and two of detail. */
        val NARROW_TALL = DpSize(NARROW.width, 80.dp)
        val WIDE_TALL = DpSize(WIDE.width, NARROW_TALL.height)
    }
}

private fun titleRes(health: TrackingHealth): Int = when (health) {
    TrackingHealth.SMS_ACCESS_OFF -> R.string.widget_tracking_sms_off
    TrackingHealth.BACKGROUND_RESTRICTED -> R.string.widget_tracking_background_restricted
    TrackingHealth.MESSAGES_MAY_BE_MISSING -> R.string.widget_tracking_may_be_missing
    TrackingHealth.SCANNING -> R.string.widget_tracking_scanning
    TrackingHealth.ON -> R.string.widget_tracking_on
}

@DrawableRes
private fun iconRes(health: TrackingHealth): Int = when (health) {
    TrackingHealth.ON -> R.drawable.ic_widget_check
    TrackingHealth.SCANNING -> R.drawable.ic_widget_sync
    else -> R.drawable.ic_widget_sync_problem
}

/** A problem says the tap fixes it; otherwise, when an SMS was last read. */
private fun detail(context: Context, checkup: TrackingCheckup): String {
    val lastReadAt = checkup.lastReadAt
    return when {
        checkup.health.needsFix -> context.getString(R.string.widget_tracking_tap_to_fix)
        lastReadAt == null -> context.getString(R.string.widget_tracking_nothing_read)
        else -> context.getString(R.string.widget_tracking_last_read, ago(context, lastReadAt, System.currentTimeMillis()))
    }
}

/**
 * "2 hours ago" in the app's language, which DateUtils doesn't follow: it formats in
 * the device's. The context's locale already carries Latin digits for Arabic.
 */
private fun ago(context: Context, millis: Long, now: Long): String {
    val elapsed = (now - millis).coerceAtLeast(0L)
    if (elapsed < DateUtils.MINUTE_IN_MILLIS) return context.getString(R.string.widget_tracking_just_now)
    val (count, unit) = when {
        elapsed < DateUtils.HOUR_IN_MILLIS -> elapsed / DateUtils.MINUTE_IN_MILLIS to RelativeDateTimeFormatter.RelativeUnit.MINUTES
        elapsed < DateUtils.DAY_IN_MILLIS -> elapsed / DateUtils.HOUR_IN_MILLIS to RelativeDateTimeFormatter.RelativeUnit.HOURS
        else -> elapsed / DateUtils.DAY_IN_MILLIS to RelativeDateTimeFormatter.RelativeUnit.DAYS
    }
    return RelativeDateTimeFormatter.getInstance(context.appLocale)
        .format(count.toDouble(), RelativeDateTimeFormatter.Direction.LAST, unit)
}

/** The in-app SMS check-up. The data URI keeps this PendingIntent apart from the other widgets'. */
private fun openCheckup(context: Context): Action = actionStartActivity(
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        data = "pennywise-widget://tracking-checkup".toUri()
        putExtra(MainActivity.EXTRA_OPEN_SMS_CHECKUP, true)
    }
)

class TrackingCheckupWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TrackingCheckupWidget()
}
