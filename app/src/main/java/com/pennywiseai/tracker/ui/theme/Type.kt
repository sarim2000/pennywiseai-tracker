package com.pennywiseai.tracker.ui.theme

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
fun getTypography(fontFamily: FontFamily = FontFamily.Default): Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-1).sp
    ),
    displayMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.5).sp
    ),
    displaySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.15).sp
    ),
    titleLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
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

val Typography = getTypography()
