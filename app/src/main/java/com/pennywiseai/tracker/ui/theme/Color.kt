package com.pennywiseai.tracker.ui.theme

import androidx.compose.ui.graphics.Color

// ── The Private Ledger ───────────────────────────────────────────────────
// Cool green-grey ink and paper for surfaces, so the page recedes and the figures carry the
// screen. Verdigris is the accent: the colour a copper penny turns with age. It also means
// money coming in; spending stays ink.
val ink_background = Color(0xFF121A17)
val ink_raised = Color(0xFF19221F)
val ink_container = Color(0xFF1E2825)
val ink_high = Color(0xFF25312D)
val ink_highest = Color(0xFF2F3C37)
val ink_rule = Color(0xFF2A3733)
val ink_outline = Color(0xFF74857E)
val ink_text = Color(0xFFE9F0EC)
val ink_textMuted = Color(0xFFB1C0BA)

val paper_background = Color(0xFFF3F6F4)
val paper_raised = Color(0xFFFFFFFF)
val paper_container = Color(0xFFE9EEEB)
val paper_high = Color(0xFFE1E8E4)
val paper_highest = Color(0xFFD6DFDA)
val paper_rule = Color(0xFFD8E0DC)
val paper_outline = Color(0xFF74837D)
val paper_text = Color(0xFF111C18)
val paper_textMuted = Color(0xFF4A5953)

val Verdigris_dark = Color(0xFF6DBFA6)
val Verdigris_dark_secondary = Color(0xFFA9C4BA)
val Verdigris_dark_tertiary = Color(0xFFE3B46B)
val Verdigris_light = Color(0xFF0A6B57)
val Verdigris_light_secondary = Color(0xFF44615A)
val Verdigris_light_tertiary = Color(0xFF8A6414)

// Custom Semantic Colors
val success_light = Color(0xFF0A6B57)
val success_dark = Color(0xFF6DBFA6)
val warning_light = Color(0xFF8F5F00)
val warning_dark = Color(0xFFE3B45F)

// Transaction Type Colors
val income_light = Color(0xFF0A6B57)   // Verdigris
val income_dark = Color(0xFF6DBFA6)
val expense_light = Color(0xFFB23A2A)  // Oxblood: over budget and overdue, not routine spend
val expense_dark = Color(0xFFEC8E7E)
val credit_light = Color(0xFF8A6414)   // Brass
val credit_dark = Color(0xFFD9B56A)
val transfer_light = Color(0xFF3D5A78) // Slate
val transfer_dark = Color(0xFF9DB6CC)
val investment_light = Color(0xFF5F4B8B) // Plum
val investment_dark = Color(0xFFBBA9D8)

// Loan Colors (Amber / Warm)
val loan_light = Color(0xFFFF8F00)           // Amber 800
val loan_dark = Color(0xFFFFCA28)            // Amber 400
val loan_container_light = Color(0xFFFFF8E1) // Amber 50
val loan_container_dark = Color(0xFF3E2723)  // Brown 900

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
val budget_safe_light = income_light
val budget_safe_dark = income_dark

// Yellow/Amber: 50-80% spent (warning)
val budget_warning_light = warning_light
val budget_warning_dark = warning_dark

// Red: over 80% spent (danger)
val budget_danger_light = expense_light
val budget_danger_dark = expense_dark

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

// Rose Pine Dawn (Light) - accent palettes
val Dawn_Rose = Color(0xFFD7827E)
val Dawn_Rose_secondary = Color(0xFFD4A373)
val Dawn_Rose_tertiary = Color(0xFFC49B9B)

val Dawn_Iris = Color(0xFF907AA9)
val Dawn_Iris_secondary = Color(0xFF7E9CB9)
val Dawn_Iris_tertiary = Color(0xFFB4637A)

val Dawn_Pine = Color(0xFF286983)
val Dawn_Pine_secondary = Color(0xFF56949F)
val Dawn_Pine_tertiary = Color(0xFF3B8686)

val Dawn_Gold = Color(0xFFEA9D34)
val Dawn_Gold_secondary = Color(0xFFD7827E)
val Dawn_Gold_tertiary = Color(0xFFC4956A)

val Dawn_Love = Color(0xFFB4637A)
val Dawn_Love_secondary = Color(0xFFD7827E)
val Dawn_Love_tertiary = Color(0xFF907AA9)

val Dawn_Foam = Color(0xFF56949F)
val Dawn_Foam_secondary = Color(0xFF286983)
val Dawn_Foam_tertiary = Color(0xFF6E9A6E)

val Dawn_Muted = Color(0xFF797593)
val Dawn_Muted_secondary = Color(0xFF9893A5)
val Dawn_Muted_tertiary = Color(0xFF6E6A86)

val Dawn_Subtle = Color(0xFF908CAA)
val Dawn_Subtle_secondary = Color(0xFF797593)
val Dawn_Subtle_tertiary = Color(0xFFA39FBF)

val Dawn_Text = Color(0xFF575279)
val Dawn_Text_secondary = Color(0xFF6E6A86)
val Dawn_Text_tertiary = Color(0xFF4A4568)

val Dawn_Highlight = Color(0xFFC4A7E7)
val Dawn_Highlight_secondary = Color(0xFF907AA9)
val Dawn_Highlight_tertiary = Color(0xFFB4A0D1)

val Dawn_Surface = Color(0xFF6E6A86)
val Dawn_Surface_secondary = Color(0xFF797593)
val Dawn_Surface_tertiary = Color(0xFF575279)

val Dawn_Overlay = Color(0xFF9893A5)
val Dawn_Overlay_secondary = Color(0xFF908CAA)
val Dawn_Overlay_tertiary = Color(0xFF7E7A96)

// Rose Pine Main (Dark) - accent palettes
val RosePine_Rose = Color(0xFFEBBCBA)
val RosePine_Rose_secondary = Color(0xFFF6C177)
val RosePine_Rose_tertiary = Color(0xFFE0AFA0)

val RosePine_Iris = Color(0xFFC4A7E7)
val RosePine_Iris_secondary = Color(0xFF9CCFD8)
val RosePine_Iris_tertiary = Color(0xFFEB6F92)

val RosePine_Pine = Color(0xFF31748F)
val RosePine_Pine_secondary = Color(0xFF9CCFD8)
val RosePine_Pine_tertiary = Color(0xFF569F9F)

val RosePine_Gold = Color(0xFFF6C177)
val RosePine_Gold_secondary = Color(0xFFEBBCBA)
val RosePine_Gold_tertiary = Color(0xFFE0B08A)

val RosePine_Love = Color(0xFFEB6F92)
val RosePine_Love_secondary = Color(0xFFEBBCBA)
val RosePine_Love_tertiary = Color(0xFFC4A7E7)

val RosePine_Foam = Color(0xFF9CCFD8)
val RosePine_Foam_secondary = Color(0xFF31748F)
val RosePine_Foam_tertiary = Color(0xFF8EC9A0)

val RosePine_Muted = Color(0xFF6E6A86)
val RosePine_Muted_secondary = Color(0xFF908CAA)
val RosePine_Muted_tertiary = Color(0xFF56526E)

val RosePine_Subtle = Color(0xFF908CAA)
val RosePine_Subtle_secondary = Color(0xFF6E6A86)
val RosePine_Subtle_tertiary = Color(0xFFABA6C4)

val RosePine_Text = Color(0xFFE0DEF4)
val RosePine_Text_secondary = Color(0xFF908CAA)
val RosePine_Text_tertiary = Color(0xFFC8C6DD)

val RosePine_Highlight = Color(0xFFC4A7E7)
val RosePine_Highlight_secondary = Color(0xFFEB6F92)
val RosePine_Highlight_tertiary = Color(0xFFB4A0D1)

val RosePine_Surface = Color(0xFF403D52)
val RosePine_Surface_secondary = Color(0xFF524F67)
val RosePine_Surface_tertiary = Color(0xFF2A2837)

val RosePine_Overlay = Color(0xFF524F67)
val RosePine_Overlay_secondary = Color(0xFF6E6A86)
val RosePine_Overlay_tertiary = Color(0xFF403D52)

// Settings Icon Background Colors
val amber_light = Color(0xFFFFF8E1)
val amber_dark = Color(0xFFFF8F00)
val orange_light = Color(0xFFFFF3E0)
val orange_dark = Color(0xFFE65100)
val green_light = Color(0xFFE8F5E9)
val green_dark = Color(0xFF2E7D32)
val teal_light = Color(0xFFE0F2F1)
val teal_dark = Color(0xFF00695C)
val blue_light = Color(0xFFE3F2FD)
val blue_dark = Color(0xFF1565C0)
val indigo_light = Color(0xFFE8EAF6)
val indigo_dark = Color(0xFF283593)
val red_light = Color(0xFFFFEBEE)
val red_dark = Color(0xFFC62828)
val pink_light = Color(0xFFFCE4EC)
val pink_dark = Color(0xFFAD1457)
val purple_light = Color(0xFFF3E5F5)
val purple_dark = Color(0xFF6A1B9A)
val cyan_light = Color(0xFFE0F7FA)
val cyan_dark = Color(0xFF00838F)
val yellow_light = Color(0xFFFFF8E1)
val yellow_dark = Color(0xFFF57F17)
/** Body text on a [yellow_light] surface — readable in both themes since the surface never darkens. */
val yellow_on_light = Color(0xFF3A2B00)
/** Pro tonal card in dark theme: a deep gold surface so it sits with the other cards. */
val yellow_surface_dark = Color(0xFF2E2614)
/** Text on [yellow_surface_dark]. */
val yellow_on_dark = Color(0xFFFFE6A8)
/** Accent (icon, action) on [yellow_surface_dark]. */
val yellow_accent_dark = Color(0xFFFFC24A)
val grey_light = Color(0xFFECEFF1)
val grey_dark = Color(0xFF546E7A)

// AMOLED Dark Mode Colors
val amoled_background = Color(0xFF000000)
val amoled_surface = Color(0xFF000000)
val amoled_surfaceVariant = Color(0xFF1E1C19)
val amoled_surfaceContainerLowest = Color(0xFF000000)
val amoled_surfaceContainerLow = Color(0xFF12110F)
val amoled_surfaceContainer = Color(0xFF1A1815)
val amoled_surfaceContainerHigh = Color(0xFF24211D)
val amoled_surfaceContainerHighest = Color(0xFF2E2A25)
val amoled_surfaceBright = Color(0xFF2A2723)
val amoled_surfaceDim = Color(0xFF000000)
