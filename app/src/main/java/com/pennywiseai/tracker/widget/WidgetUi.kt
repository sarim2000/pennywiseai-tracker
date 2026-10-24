package com.pennywiseai.tracker.widget

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.text.TextUtils
import android.os.Build
import android.view.View
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.Typography
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/*
 * What every widget shares, so the seven read as one family: the app's own
 * language and reading direction, the in-app eye, how money reads at a
 * glance, and the few parts (header, + button, progress bar, empty state).
 */

/**
 * This context in the app's own language. A widget is drawn from whichever
 * context its session or worker started with, and that can carry the
 * device's language (English) while the app is set to Arabic, which flipped
 * widget text between the two. Older Androids have no per-app language, so
 * there the device's language is the app's.
 */
fun Context.inAppLanguage(): Context {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    val appLocales = getSystemService(LocaleManager::class.java)?.applicationLocales
    if (appLocales == null || appLocales.isEmpty || resources.configuration.locales == appLocales) return this
    return createConfigurationContext(Configuration(resources.configuration).apply { setLocales(appLocales) })
}

/**
 * Whether the app's language reads the other way from the device's (an
 * Arabic app on an English phone). Only API 33+ can tell them apart, and
 * only it has a per-app language. Resources.getSystem() would not do: it
 * carries the app's own language too.
 */
private fun Context.readsAgainstDevice(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    val device = getSystemService(LocaleManager::class.java)?.systemLocales?.takeUnless { it.isEmpty }?.get(0)
        ?: return false
    return isRtlLayout != (TextUtils.getLayoutDirectionFromLocale(device) == View.LAYOUT_DIRECTION_RTL)
}

/** The app language's first locale, for date formats built outside resources. */
val Context.appLocale: Locale get() = resources.configuration.locales[0]

val Context.isRtlLayout: Boolean
    get() = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL

/**
 * getString with Latin digits. Arabic resources format %d as "٤٠", which then
 * sits beside "JD 40"; the app's amounts are Latin, so the widgets' numbers are too.
 */
fun Context.latinString(@StringRes id: Int, vararg args: Any): String =
    String.format(Locale.US, getString(id), *args)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun userPreferences(): UserPreferencesRepository
}

/**
 * The in-app eye (Home's hide-amounts toggle), read before composing so the
 * first frame is already right, then followed live. Hidden totals stay hidden
 * on the home screen, where anyone near the phone can read them; what the
 * app shows with the eye closed (single transactions, goals) shows here too.
 */
class WidgetPrivacy(val hidden: Flow<Boolean>, val initial: Boolean) {
    companion object {
        suspend fun load(context: Context): WidgetPrivacy = try {
            val flow = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
                .userPreferences().isBalanceHidden
            WidgetPrivacy(flow, flow.first())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            WidgetPrivacy(flowOf(true), true) // can't tell: keep them hidden
        }
    }
}

private val LocalAmountsHidden = staticCompositionLocalOf { false }

/** Whether totals are hidden right now; for a widget that swaps more than the figure. */
@Composable
fun amountsHidden(): Boolean = LocalAmountsHidden.current

private val LocalMirrored = staticCompositionLocalOf { false }

/**
 * Bumped by the app when its language changes. A widget's composition lives
 * on between updates, and an update with unchanged data doesn't recompose
 * its root, so without this it kept drawing the old language.
 */
val WidgetLanguageChanges = MutableStateFlow(0)

/**
 * Every widget's root: theme, the app's language, and the eye.
 *
 * Glance's views take their direction from the launcher's language, not the
 * app's, so an Arabic app on an English phone got Arabic text laid out left
 * to right (and no parent direction can override that). When the two
 * disagree the widget mirrors itself: [WidgetRow] reverses its items and
 * [startAlignment] / [endAlignment] swap edges; [startTextAlign] /
 * [endTextAlign] pin text to the app's edges. Glance also turns start/end
 * padding into left/right by its own guess of the direction, so keep
 * padding symmetric (horizontal = …) and space Row items with Spacers.
 */
@Composable
fun WidgetRoot(privacy: WidgetPrivacy, content: @Composable () -> Unit) {
    // Read so a session still alive when the language changes recomposes in the new one.
    WidgetLanguageChanges.collectAsState().value
    val context = LocalContext.current.inAppLanguage()
    val hidden by privacy.hidden.collectAsState(privacy.initial)
    CompositionLocalProvider(
        LocalContext provides context,
        LocalAmountsHidden provides hidden,
        LocalMirrored provides context.readsAgainstDevice()
    ) {
        GlanceTheme(
            colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GlanceTheme.colors else PennyWiseWidgetTheme.colors,
            content = content
        )
    }
}

/** The app's start edge, as the launcher lays it out. */
@Composable
fun startAlignment(): Alignment.Horizontal = if (LocalMirrored.current) Alignment.End else Alignment.Start

/** The app's end edge, as the launcher lays it out. */
@Composable
fun endAlignment(): Alignment.Horizontal = if (LocalMirrored.current) Alignment.Start else Alignment.End

/**
 * Text aligned to the app's start edge; set it on any Text wider than its
 * words. A TextView reads Start/End against each line's own direction (an
 * Arabic line's start is its right, a Latin amount's its left), so this is
 * the absolute side, which Glance sets as plain gravity from Android 12.
 * Before 12 there's no per-app language, so Start/End already agree.
 */
@Composable
fun startTextAlign(): TextAlign = when {
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> TextAlign.Start
    LocalContext.current.isRtlLayout -> TextAlign.Right
    else -> TextAlign.Left
}

/** Text aligned to the app's end edge (amounts at the row's end); see [startTextAlign]. */
@Composable
fun endTextAlign(): TextAlign = when {
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> TextAlign.End
    LocalContext.current.isRtlLayout -> TextAlign.Left
    else -> TextAlign.Right
}

/**
 * [text] laid out in the app's direction. A TextView takes a line's
 * direction from its first letter, so an Arabic line opening with an amount
 * ("JD 45/شهر · …") was read left to right and came out in the wrong order.
 * A leading direction mark settles it.
 */
@Composable
fun inAppDirection(text: String): String =
    (if (LocalContext.current.isRtlLayout) "\u200F" else "\u200E") + text

/** Collects a [WidgetRow]'s items in the app's reading order. */
class WidgetRowItems {
    internal val items = mutableListOf<@Composable RowScope.() -> Unit>()
    fun item(content: @Composable RowScope.() -> Unit) {
        items += content
    }
}

/**
 * A Row whose items run from the app's start edge: first item on the right
 * in Arabic, on the left in English, whatever the launcher's language.
 * Use it for every Row whose items aren't symmetric.
 */
@Composable
fun WidgetRow(
    modifier: GlanceModifier = GlanceModifier,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    items: WidgetRowItems.() -> Unit
) {
    val list = WidgetRowItems().apply(items).items
    Row(modifier = modifier, verticalAlignment = verticalAlignment) {
        (if (LocalMirrored.current) list.asReversed() else list).forEach { it() }
    }
}

const val HIDDEN_AMOUNT = "••••"

/**
 * A total at a glance: whole units ("JD 241", not "JD241.198"), or
 * [HIDDEN_AMOUNT] while the eye is closed.
 */
@Composable
fun totalText(amount: BigDecimal, currency: String): String =
    if (amountsHidden()) HIDDEN_AMOUNT
    else widgetMoney(LocalContext.current, wholeUnits(amount), currency)

/** [amount] as [totalText] shows it; decide "over" or "left" on this, not the exact figure. */
fun wholeUnits(amount: BigDecimal): BigDecimal = amount.setScale(0, RoundingMode.HALF_UP)

/**
 * One transaction's exact amount ("JD 3.2"). Like the app's transaction list,
 * single rows stay visible while the eye hides the totals.
 */
@Composable
fun exactText(amount: BigDecimal, currency: String): String =
    widgetMoney(LocalContext.current, amount, currency)

/**
 * [CurrencyFormatter.formatCurrency] for a widget: a space after a lettered
 * symbol ("JD 241"), the sign kept on the left, and isolated as left-to-right
 * when the app reads right to left so it sits correctly inside Arabic text.
 * The formatter decides that from the process locale, which in a widget
 * isn't necessarily the app's.
 */
fun widgetMoney(context: Context, amount: BigDecimal, currency: String): String {
    val symbol = CurrencyFormatter.getCurrencySymbol(currency)
    var text = CurrencyFormatter.formatCurrency(amount.abs(), currency).trim('⁦', '⁩')
    if (symbol.lastOrNull()?.isLetter() == true && text.startsWith(symbol) && text.length > symbol.length) {
        text = "$symbol ${text.removePrefix(symbol)}"
    }
    if (amount.signum() < 0) text = "-$text"
    return if (context.isRtlLayout) "⁦$text⁩" else text
}

object WidgetDefaults {
    /** Inside the widget's frame, all sides. */
    val padding = Spacing.md
    /** The + circle; it sits in a [Dimensions.Component.minTouchTarget] target. */
    val addButton = Dimensions.Icon.list
    val barHeight = Spacing.xs + Spacing.xxs
}

/** Section title: the widget's name, as in its header. */
@Composable
fun WidgetTitle(text: String, modifier: GlanceModifier = GlanceModifier) {
    Text(
        text = inAppDirection(text),
        maxLines = 1,
        modifier = modifier,
        style = TextStyle(
            color = GlanceTheme.colors.onSurface,
            fontSize = Typography.titleSmall.fontSize,
            fontWeight = FontWeight.Medium,
            textAlign = startTextAlign()
        )
    )
}

/** Small supporting text: a figure's label, a row's meta line. */
@Composable
fun WidgetLabel(text: String, modifier: GlanceModifier = GlanceModifier, color: ColorProvider? = null) {
    Text(
        text = inAppDirection(text),
        maxLines = 1,
        modifier = modifier,
        style = TextStyle(
            color = color ?: GlanceTheme.colors.onSurfaceVariant,
            fontSize = Typography.bodySmall.fontSize,
            textAlign = startTextAlign()
        )
    )
}

/** A 48dp header row: [title] and, when given, a + at the end. */
@Composable
fun WidgetHeader(title: String, add: Action? = null, addDescription: String = "") {
    WidgetRow(
        modifier = GlanceModifier.fillMaxWidth().height(Dimensions.Component.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item { WidgetTitle(title, GlanceModifier.defaultWeight()) }
        if (add != null) item { AddButton(add, addDescription) }
    }
}

/** The family's +: a filled circle in a 48dp touch target. */
@Composable
fun AddButton(action: Action, contentDescription: String, size: Dp = WidgetDefaults.addButton) {
    Box(
        modifier = GlanceModifier
            .size(maxOf(size, Dimensions.Component.minTouchTarget))
            .clickable(action),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = GlanceModifier
                .size(size)
                .cornerRadius(size / 2)
                .background(GlanceTheme.colors.primary),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_add),
                contentDescription = contentDescription,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
                modifier = GlanceModifier.size(size / 2)
            )
        }
    }
}

/** The family's thin progress bar, on the surface-variant track. */
@Composable
fun WidgetProgressBar(fraction: Float, color: ColorProvider, modifier: GlanceModifier = GlanceModifier) {
    LinearProgressIndicator(
        progress = fraction.coerceIn(0f, 1f),
        modifier = modifier.fillMaxWidth().height(WidgetDefaults.barHeight).cornerRadius(WidgetDefaults.barHeight / 2),
        color = color,
        backgroundColor = GlanceTheme.colors.surfaceVariant
    )
}

/**
 * Nothing to show yet: what's missing, what to do, and (when it helps) a
 * button that does it, so an empty widget is never a dead end.
 */
@Composable
fun WidgetEmptyState(title: String, hint: String, button: String? = null, onButton: Action? = null) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            maxLines = 2,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = Typography.titleSmall.fontSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        )
        Spacer(GlanceModifier.height(Spacing.xxs))
        Text(
            text = hint,
            maxLines = 2,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = Typography.bodySmall.fontSize,
                textAlign = TextAlign.Center
            )
        )
        if (button != null && onButton != null) {
            Spacer(GlanceModifier.height(Spacing.smd))
            Box(
                modifier = GlanceModifier
                    .height(Dimensions.Component.minTouchTarget)
                    .clickable(onButton),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = button,
                    maxLines = 1,
                    modifier = GlanceModifier
                        .cornerRadius(Spacing.lg)
                        .background(GlanceTheme.colors.primary)
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimary,
                        fontSize = Typography.labelLarge.fontSize,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}
