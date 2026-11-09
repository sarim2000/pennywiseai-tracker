package com.pennywiseai.tracker.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pennywiseai.core.designsystem.R

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

/**
 * Every weight the Google Sans Flex families declare: all nine, so any
 * `FontWeight` in the app — the scale, its +100 `*Emphasized` step, the
 * `PennyWiseText` money weights — resolves to its own instance. Compose picks
 * the *closest declared* Font, so a missing 700 would quietly render an
 * emphasized style at 600, identical to its base.
 */
private val GoogleSansFlexWeights: List<FontWeight> = (100..900 step 100).map { FontWeight(it) }

/** ROND (roundness) for the heading family: fully rounded terminals. */
internal const val GOOGLE_SANS_FLEX_HEADING_ROUNDNESS = 100f

/**
 * Google Sans Flex — Material 3 Expressive's typeface — from one variable font
 * (`res/font/google_sans_flex.ttf`, instanced down to its `wght` and `ROND`
 * axes). Each Font pins both axes; they are never animated. Latin only: text in
 * other scripts falls back per glyph to the system font.
 */
private fun googleSansFlex(roundness: Float) = FontFamily(
    GoogleSansFlexWeights.map { weight ->
        Font(
            R.font.google_sans_flex,
            weight,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight.weight),
                FontVariation.Setting("ROND", roundness),
            ),
        )
    }
)

/** Body, label and money text: square terminals (ROND 0), crisp at small sizes and in figures. */
val GoogleSansFlexFamily = googleSansFlex(roundness = 0f)

/** Display, headline and title roles: the rounded, expressive cut. */
val GoogleSansFlexHeadingFamily = googleSansFlex(roundness = GOOGLE_SANS_FLEX_HEADING_ROUNDNESS)

/**
 * The type scale.
 *
 * Two deliberate departures from stock Material 3, both aimed at making
 * hierarchy readable in a dense, number-heavy UI:
 *
 * 1. **Titles and headlines are SemiBold, not Normal/Medium.** Material's
 *    default weights leave a `titleMedium` heading nearly indistinguishable
 *    from the `bodyLarge` text under it — the screens read as one flat wall.
 *    A weight step (600 vs 400) separates them without needing a size step,
 *    which matters because sizes are already tight.
 *
 * 2. **Tracking is tightened at the large end and left wide at the small
 *    end.** Big figures (balances, totals) get negative tracking so digits
 *    group into one number instead of drifting apart; 11–12sp labels keep
 *    Material's generous tracking, which is what makes small text legible.
 *
 * Display, headline and title roles take [headingFamily]; body and label
 * roles take the body family. They differ only for Google Sans Flex (rounded
 * headings, square body); SYSTEM and SN Pro pass one family for both.
 *
 * Roles, so screens pick the same style for the same job:
 * | Role | Use |
 * |---|---|
 * | `displaySmall`+ | The one hero figure on a screen (`PennyWiseText.heroAmount`) |
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
private fun baseTypography(fontFamily: FontFamily, headingFamily: FontFamily): Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = headingFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-1).sp
    ),
    displayMedium = TextStyle(
        fontFamily = headingFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.5).sp
    ),
    displaySmall = TextStyle(
        fontFamily = headingFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = headingFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = headingFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = headingFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.15).sp
    ),
    titleLarge = TextStyle(
        fontFamily = headingFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.1).sp
    ),
    titleMedium = TextStyle(
        fontFamily = headingFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = headingFamily,
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
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp
    ),
    bodySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
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
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * The scale plus M3 Expressive's emphasized styles (`*Emphasized`).
 *
 * Material's stock emphasized styles carry their own font family, so they'd
 * ignore the user's font choice. Derive them from our scale instead: same
 * size, tracking and family, one weight step heavier. Reserve them for hero
 * moments (the Home balance, a screen's large title) — emphasis everywhere is
 * emphasis nowhere.
 *
 * @param headingFamily the family for display, headline and title roles;
 *   defaults to [fontFamily].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun getTypography(
    fontFamily: FontFamily = FontFamily.Default,
    headingFamily: FontFamily = fontFamily,
): Typography {
    val b = baseTypography(fontFamily, headingFamily)
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
