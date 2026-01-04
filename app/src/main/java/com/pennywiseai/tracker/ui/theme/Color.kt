package com.pennywiseai.tracker.ui.theme

import androidx.compose.ui.graphics.Color

// Light Theme Colors - Blue Owl Theme
val md_theme_light_primary = Color(0xFF1565C0)  // Darker blue for better contrast
val md_theme_light_onPrimary = Color(0xFFFFFFFF)  // White on blue - good contrast
val md_theme_light_primaryContainer = Color(0xFFD1E4FF)  // Light blue container
val md_theme_light_onPrimaryContainer = Color(0xFF001D36)  // Very dark blue for text
val md_theme_light_secondary = Color(0xFFF57C00)  // Darker orange for better contrast
val md_theme_light_onSecondary = Color(0xFFFFFFFF)  // White on orange - better than black
val md_theme_light_secondaryContainer = Color(0xFFFFE0B2)  // Light gold
val md_theme_light_onSecondaryContainer = Color(0xFF2C1600)  // Very dark brown for text
val md_theme_light_tertiary = Color(0xFF0277BD)  // Darker cyan blue for contrast
val md_theme_light_onTertiary = Color(0xFFFFFFFF)  // White on tertiary
val md_theme_light_tertiaryContainer = Color(0xFFCAE6FF)  // Light cyan container
val md_theme_light_onTertiaryContainer = Color(0xFF001E30)  // Very dark blue for text
val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onErrorContainer = Color(0xFF410002)
val md_theme_light_outline = Color(0xFF79747E)
val md_theme_light_background = Color(0xFFFFFFFF)  // Pure white background
val md_theme_light_onBackground = Color(0xFF1C1B1F)
val md_theme_light_surface = Color(0xFFFAFAFA)  // Very light gray for cards
val md_theme_light_onSurface = Color(0xFF1C1B1F)
val md_theme_light_surfaceVariant = Color(0xFFF5F5F5)  // Light gray for secondary cards
val md_theme_light_onSurfaceVariant = Color(0xFF49454F)
val md_theme_light_inverseSurface = Color(0xFF313033)
val md_theme_light_inverseOnSurface = Color(0xFFF4EFF4)
val md_theme_light_inversePrimary = Color(0xFF90CAF9)  // Light blue
val md_theme_light_surfaceTint = Color(0xFF1565C0)  // Updated primary blue
val md_theme_light_outlineVariant = Color(0xFFCAC4D0)
val md_theme_light_scrim = Color(0xFF000000)

// Dark Theme Colors - Blue Owl Theme
val md_theme_dark_primary = Color(0xFF90CAF9)  // Light blue for dark mode
val md_theme_dark_onPrimary = Color(0xFF003258)  // Dark blue on light blue
val md_theme_dark_primaryContainer = Color(0xFF1E3A5F)  // Slightly lighter blue container for better contrast
val md_theme_dark_onPrimaryContainer = Color(0xFFD1E4FF)  // Light blue on dark
val md_theme_dark_secondary = Color(0xFFFFB74D)  // Light orange for dark mode
val md_theme_dark_onSecondary = Color(0xFF4A2800)  // Dark brown on orange
val md_theme_dark_secondaryContainer = Color(0xFF5D3200)  // Slightly lighter orange container
val md_theme_dark_onSecondaryContainer = Color(0xFFFFDDB3)  // Light orange on dark
val md_theme_dark_tertiary = Color(0xFF5DD4FC)  // Bright cyan for dark mode
val md_theme_dark_onTertiary = Color(0xFF00344F)  // Dark blue on cyan
val md_theme_dark_tertiaryContainer = Color(0xFF004C6F)  // Dark cyan container
val md_theme_dark_onTertiaryContainer = Color(0xFFCAE6FF)  // Light cyan on dark
val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
val md_theme_dark_outline = Color(0xFF938F99)
val md_theme_dark_background = Color(0xFF000000)  // AMOLED black background
val md_theme_dark_onBackground = Color(0xFFE6E1E5)
val md_theme_dark_surface = Color(0xFF1E1E1E)  // Card surface
val md_theme_dark_onSurface = Color(0xFFE6E1E5)
val md_theme_dark_surfaceVariant = Color(0xFF252525)  // Secondary card surface
val md_theme_dark_onSurfaceVariant = Color(0xFFCAC4D0)
val md_theme_dark_inverseSurface = Color(0xFFE6E1E5)
val md_theme_dark_inverseOnSurface = Color(0xFF313033)
val md_theme_dark_inversePrimary = Color(0xFF1565C0)  // Medium blue
val md_theme_dark_surfaceTint = Color(0xFF90CAF9)  // Light blue
val md_theme_dark_outlineVariant = Color(0xFF49454F)
val md_theme_dark_scrim = Color(0xFF000000)

// Custom Semantic Colors
val success_light = Color(0xFF2E7D32)  // Medium green for good contrast on white
val success_dark = Color(0xFF81C784)  // Lighter green for better contrast on dark backgrounds
val warning_light = Color(0xFFE65100)  // Darker orange for better contrast
val warning_dark = Color(0xFFFFB74D)  // Lighter orange for dark mode

// Transaction Type Colors
// Using distinct colors that work well on both light and dark backgrounds
val income_light = Color(0xFF2E7D32)  // Medium green - good on white
val income_dark = Color(0xFF81C784)   // Light green - readable on dark backgrounds
val expense_light = Color(0xFFD32F2F) // Medium red - good on white  
val expense_dark = Color(0xFFEF5350)  // Light red - readable on dark backgrounds
val credit_light = Color(0xFFE65100)  // Dark orange for credit card - good on white
val credit_dark = Color(0xFFFFB74D)   // Light orange for credit card - readable on dark
val transfer_light = Color(0xFF6A1B9A) // Purple for transfers - good on white
val transfer_dark = Color(0xFFBA68C8)  // Light purple for transfers - readable on dark
val investment_light = Color(0xFF00695C) // Teal for investments - good on white
val investment_dark = Color(0xFF4DB6AC)  // Light teal for investments - readable on dark

// Category Colors (matching the owl theme)
val category_food = Color(0xFFFF7043)  // Coral
val category_transport = Color(0xFF5C6BC0)  // Indigo
val category_shopping = Color(0xFFAB47BC)  // Purple
val category_bills = Color(0xFF42A5F5)  // Light Blue
val category_entertainment = Color(0xFFEC407A)  // Pink
val category_health = Color(0xFF26A69A)  // Teal
val category_other = Color(0xFF78909C)  // Blue Grey

// Budget Progress Colors
// Green: under 50% spent (safe)
val budget_safe_light = Color(0xFF2E7D32)   // Medium green - good contrast on white
val budget_safe_dark = Color(0xFF81C784)    // Light green - readable on dark

// Yellow/Amber: 50-80% spent (warning)
val budget_warning_light = Color(0xFFFF8F00)  // Amber - good contrast on white
val budget_warning_dark = Color(0xFFFFCA28)   // Light amber - readable on dark

// Red: over 80% spent (danger)
val budget_danger_light = Color(0xFFD32F2F)   // Medium red - good contrast on white
val budget_danger_dark = Color(0xFFEF5350)    // Light red - readable on dark

// Budget Card Colors (for different budget cards)
val budget_card_colors = listOf(
    Color(0xFF1565C0),  // Blue (default)
    Color(0xFF2E7D32),  // Green
    Color(0xFFF57C00),  // Orange
    Color(0xFF7B1FA2),  // Purple
    Color(0xFFD32F2F),  // Red
    Color(0xFF00838F),  // Cyan
    Color(0xFFC2185B),  // Pink
    Color(0xFF455A64)   // Blue Grey
)
