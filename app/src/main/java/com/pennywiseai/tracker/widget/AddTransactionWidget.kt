package com.pennywiseai.tracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.width
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing

/**
 * The cash button. Cash never texts, so this is the one spend the app can't
 * catch on its own: one tap opens the quick cash sheet.
 */
class AddTransactionWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(ICON, LABELLED, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val privacy = WidgetPrivacy.load(context)
        provideContent {
            WidgetRoot(privacy) {
                Content()
            }
        }
    }

    @Composable
    private fun Content() {
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

    private companion object {
        val CIRCLE = Dimensions.Icon.avatarLarge
        val ICON = DpSize(40.dp, 40.dp)
        // Circle (48dp) plus the "Cash" label under it.
        val LABELLED = DpSize(40.dp, 68.dp)
        val WIDE = DpSize(140.dp, 40.dp)
    }
}
