package com.pennywiseai.tracker.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.core.QuickAddIntent
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.voice.VoiceInput

/**
 * The cash button. Cash never texts, so this is the one spend the app can't
 * catch on its own: one tap opens the quick cash sheet. The wide layout adds
 * a mic, on phones that can listen: say the spend, or ask a question.
 */
class AddTransactionWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(ICON, LABELLED, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val privacy = WidgetPrivacy.load(context)
        val voice = VoiceInput.isAvailable(context)
        provideContent {
            WidgetRoot(privacy) {
                Content(voice)
            }
        }
    }

    @Composable
    private fun Content(voice: Boolean) {
        val context = LocalContext.current
        val size = LocalSize.current
        val add = actionRunCallback<OpenAddTransactionAction>()
        // No inner padding: the content is centred, and a 1x1 cell has no room to spare.
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .clickable(add),
            contentAlignment = Alignment.Center
        ) {
            when {
                size.width >= WIDE.width && voice -> WidgetRow(
                    modifier = GlanceModifier.fillMaxWidth().padding(horizontal = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item { AddButton(add, context.getString(R.string.widget_cash_spend), CIRCLE) }
                    item { Spacer(GlanceModifier.width(Spacing.sm)) }
                    // The short label: beside the mic, "Cash spend" in most languages ends in "…".
                    // Glance can't measure text, and the + still says "Cash spend" to TalkBack.
                    item { WidgetTitle(context.getString(R.string.widget_cash_label), GlanceModifier.defaultWeight()) }
                    item { MicButton(actionRunCallback<OpenVoiceQuickAddAction>(), context.getString(R.string.quick_add_voice_title)) }
                }
                size.width >= WIDE.width -> WidgetRow(verticalAlignment = Alignment.CenterVertically) {
                    item { AddButton(add, context.getString(R.string.widget_cash_spend), CIRCLE) }
                    item { Spacer(GlanceModifier.width(Spacing.sm)) }
                    item { WidgetTitle(context.getString(R.string.widget_cash_spend)) }
                }
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AddButton(add, context.getString(R.string.widget_cash_spend), CIRCLE)
                    if (size.height >= LABELLED.height) {
                        Spacer(GlanceModifier.height(Spacing.xxs))
                        WidgetLabel(context.getString(R.string.widget_cash_label), color = GlanceTheme.colors.onSurface)
                    }
                }
            }
        }
    }

    /** Secondary beside the primary +: the same sheet, listening first. */
    @Composable
    private fun MicButton(action: Action, contentDescription: String) {
        Box(
            modifier = GlanceModifier
                .size(Dimensions.Component.minTouchTarget)
                .clickable(action),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = GlanceModifier
                    .size(MIC)
                    .cornerRadius(MIC / 2)
                    .background(GlanceTheme.colors.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_mic),
                    contentDescription = contentDescription,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                    modifier = GlanceModifier.size(MIC / 2)
                )
            }
        }
    }

    private companion object {
        val CIRCLE = Dimensions.Icon.avatarLarge
        val MIC = 40.dp
        val ICON = DpSize(40.dp, 40.dp)
        // Circle (48dp) plus the "Cash" label under it.
        val LABELLED = DpSize(40.dp, 68.dp)
        val WIDE = DpSize(140.dp, 40.dp)
    }
}

/** The widget's mic: the quick add sheet, listening. */
class OpenVoiceQuickAddAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        context.startActivity(QuickAddIntent.voice(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
