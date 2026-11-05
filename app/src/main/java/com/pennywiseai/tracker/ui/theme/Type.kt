package com.pennywiseai.tracker.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pennywiseai.tracker.R

val SNProFontFamily = FontFamily(
    Font(R.font.sn_pro_regular, FontWeight.Normal),
    Font(R.font.sn_pro_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.sn_pro_medium, FontWeight.Medium),
    Font(R.font.sn_pro_medium_italic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.sn_pro_semibold, FontWeight.SemiBold),
    Font(R.font.sn_pro_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.sn_pro_bold, FontWeight.Bold),
    Font(R.font.sn_pro_bold_italic, FontWeight.Bold, FontStyle.Italic),
    Font(R.font.sn_pro_extrabold, FontWeight.ExtraBold),
    Font(R.font.sn_pro_extrabold_italic, FontWeight.ExtraBold, FontStyle.Italic),
    Font(R.font.sn_pro_black, FontWeight.Black),
    Font(R.font.sn_pro_black_italic, FontWeight.Black, FontStyle.Italic),
    Font(R.font.sn_pro_light, FontWeight.Light),
    Font(R.font.sn_pro_light_italic, FontWeight.Light, FontStyle.Italic),
    Font(R.font.sn_pro_extralight, FontWeight.ExtraLight),
    Font(R.font.sn_pro_extralight_italic, FontWeight.ExtraLight, FontStyle.Italic),
)

/** Newsreader cut for large sizes: the hero figure and big totals. */
val LedgerDisplayFamily = FontFamily(
    Font(R.font.newsreader_display_regular, FontWeight.Normal),
    Font(R.font.newsreader_display_medium, FontWeight.Medium),
)

/** Newsreader cut for reading sizes: screen titles, section titles and every row amount. */
val LedgerFigureFamily = FontFamily(
    Font(R.font.newsreader_text_regular, FontWeight.Normal),
    Font(R.font.newsreader_text_medium, FontWeight.Medium),
    Font(R.font.newsreader_text_semibold, FontWeight.SemiBold),
)

/** Figtree: the labels, merchants and all running UI text beside the serif figures. */
val LedgerTextFamily = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
)

/**
 * The type scale.
 *
 * Two voices: a serif (Newsreader) for figures and titles, the things you read for
 * their value, and a plain sans (Figtree) for labels and running text. That split
 * is the app's signature, and it does the hierarchy work a weight step did before.
 * Tracking tightens at the large end, so digits group into one number.
 *
 * Roles, so screens pick the same style for the same job:
 * | Role | Use |
 * |---|---|
 * | `displaySmall`+ | Hero balance on the Home balance card |
 * | `headlineMedium`/`Small` | Screen-level totals, dialog titles |
 * | `titleLarge` | Screen title in a top app bar |
 * | `titleMedium` | Card heading |
 * | `titleSmall` | Section header, grouped-list heading |
 * | `bodyLarge` | Primary row text (merchant, setting name) |
 * | `bodyMedium` | Supporting row text, descriptions, paragraph copy |
 * | `bodySmall` | Metadata: timestamps, "3 of 12", footnotes |
 * | `labelLarge` | Buttons, prominent inline actions |
 * | `labelMedium`/`Small` | Chips, badges, axis ticks, overlines |
 */
private fun baseTypography(
    fontFamily: FontFamily,
    // A serif has the weight of its contrast: at 600 it goes blunt. With one family
    // (System, SN Pro) these stay SemiBold so headings still step up from the body.
    displayFamily: FontFamily?,
    figureFamily: FontFamily?,
): Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = displayFamily ?: fontFamily,
        fontWeight = if (displayFamily != null) FontWeight.Normal else FontWeight.SemiBold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-1).sp
    ),
    displayMedium = TextStyle(
        fontFamily = displayFamily ?: fontFamily,
        fontWeight = if (displayFamily != null) FontWeight.Normal else FontWeight.SemiBold,
        fontSize = 42.sp,
        lineHeight = 50.sp,
        letterSpacing = (-0.8).sp
    ),
    displaySmall = TextStyle(
        fontFamily = displayFamily ?: fontFamily,
        fontWeight = if (displayFamily != null) FontWeight.Normal else FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = displayFamily ?: fontFamily,
        fontWeight = if (displayFamily != null) FontWeight.Normal else FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = displayFamily ?: fontFamily,
        fontWeight = if (displayFamily != null) FontWeight.Normal else FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = figureFamily ?: fontFamily,
        fontWeight = if (figureFamily != null) FontWeight.Medium else FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = figureFamily ?: fontFamily,
        fontWeight = if (figureFamily != null) FontWeight.Medium else FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.1).sp
    ),
    titleMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        // Material's 0.5sp here is tuned for Roboto at reading sizes; on a
        // geometric sans in a list row it reads as loose and costs width that
        // long merchant names need.
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    labelLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    )
)

/**
 * The scale plus M3 Expressive's emphasized styles (`*Emphasized`).
 *
 * Material's stock emphasized styles carry their own font family, so they'd
 * ignore the user's font choice. Derive them from our scale instead: same
 * size and tracking, one weight step heavier. Reserve them for hero moments
 * (the Home balance, a screen's large title) — emphasis everywhere is emphasis
 * nowhere.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun getTypography(
    fontFamily: FontFamily = FontFamily.Default,
    displayFamily: FontFamily? = null,
    figureFamily: FontFamily? = null,
): Typography {
    val b = baseTypography(fontFamily, displayFamily, figureFamily)
    // 400 → 500, 600 → 700: Material's own emphasized step.
    fun TextStyle.emphasized() =
        copy(fontWeight = FontWeight(((fontWeight ?: FontWeight.Normal).weight + 100).coerceAtMost(900)))
    return Typography(
        displayLarge = b.displayLarge, displayMedium = b.displayMedium, displaySmall = b.displaySmall,
        headlineLarge = b.headlineLarge, headlineMedium = b.headlineMedium, headlineSmall = b.headlineSmall,
        titleLarge = b.titleLarge, titleMedium = b.titleMedium, titleSmall = b.titleSmall,
        bodyLarge = b.bodyLarge, bodyMedium = b.bodyMedium, bodySmall = b.bodySmall,
        labelLarge = b.labelLarge, labelMedium = b.labelMedium, labelSmall = b.labelSmall,
        displayLargeEmphasized = b.displayLarge.emphasized(),
        displayMediumEmphasized = b.displayMedium.emphasized(),
        displaySmallEmphasized = b.displaySmall.emphasized(),
        headlineLargeEmphasized = b.headlineLarge.emphasized(),
        headlineMediumEmphasized = b.headlineMedium.emphasized(),
        headlineSmallEmphasized = b.headlineSmall.emphasized(),
        titleLargeEmphasized = b.titleLarge.emphasized(),
        titleMediumEmphasized = b.titleMedium.emphasized(),
        titleSmallEmphasized = b.titleSmall.emphasized(),
        bodyLargeEmphasized = b.bodyLarge.emphasized(),
        bodyMediumEmphasized = b.bodyMedium.emphasized(),
        bodySmallEmphasized = b.bodySmall.emphasized(),
        labelLargeEmphasized = b.labelLarge.emphasized(),
        labelMediumEmphasized = b.labelMedium.emphasized(),
        labelSmallEmphasized = b.labelSmall.emphasized(),
    )
}

val Typography = getTypography()
