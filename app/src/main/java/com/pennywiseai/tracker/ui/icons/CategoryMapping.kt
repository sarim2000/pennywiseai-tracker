package com.pennywiseai.tracker.ui.icons

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.AirplanemodeActive
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Commute
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.LocalGroceryStore
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MovieFilter
import androidx.compose.material.icons.outlined.Payment
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.RemoveCircle
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.SportsMartialArts
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import com.pennywiseai.shared.domain.mapping.SharedCategoryMapping
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.builtinKey

/**
 * Category visual properties (icons, colors) for Android UI.
 * Keyword-based categorization is delegated to SharedCategoryMapping.
 */
object CategoryMapping {

    data class CategoryInfo(
        @StringRes val label: Int,
        val icon: ImageVector,
        val color: Color,
        val fallbackIcon: ImageVector = Icons.Outlined.Category
    )

    /** What the user set on a CategoryEntity: its color and optional emoji (#760). */
    data class UserStyle(
        val colorHex: String,
        val emoji: String?,
        /** Original name of a renamed built-in (#823); null otherwise. */
        val builtinKey: String? = null,
        /** True when [colorHex] is the user's choice rather than a built-in's seed. */
        val customColor: Boolean = true,
        /** A built-in still on its original name — the only kind shown translated. */
        val isBuiltin: Boolean = false
    )

    /**
     * Name → user style, mirrored from the categories table by
     * [com.pennywiseai.tracker.PennyWiseApplication] so the many non-ViewModel
     * icon call sites (BrandIcon, CategoryIcon, IconProvider) can resolve a
     * custom category without plumbing a map through every screen.
     */
    val userStyles = mutableStateMapOf<String, UserStyle>()

    /**
     * Translated name of an un-renamed built-in, or null to show [name] as stored.
     * Storage, rules, budgets and backups all keep the English key; only display
     * changes. A user's own name (a custom category or a renamed built-in, #823)
     * is never translated.
     */
    @StringRes
    fun labelRes(name: String): Int? = labelRes(name, userStyles[name]?.isBuiltin)

    /** [isBuiltin] null = no category row by that name (e.g. the non-seeded "Tax"). */
    @StringRes
    private fun labelRes(name: String, isBuiltin: Boolean?): Int? =
        if (isBuiltin == false) null else builtins[name]?.label ?: labelOnly[name]

    /** Names the app assigns by default but that have no built-in icon or colour of their own. */
    private val labelOnly = mapOf("Subscriptions" to R.string.category_subscriptions)

    /** [labelRes] for non-Compose callers (widgets, workers, notifications). */
    fun label(context: Context, name: String): String =
        labelRes(name)?.let(context::getString) ?: name

    /**
     * [label] decided from rows the caller already loaded. A background process
     * (widget refresh, incoming SMS) may run before [userStyles] is mirrored, and
     * an unmirrored custom "Groceries" would otherwise be shown translated.
     */
    fun label(context: Context, name: String, categories: List<CategoryEntity>): String =
        labelRes(name, categories)?.let(context::getString) ?: name

    @StringRes
    fun labelRes(name: String, categories: List<CategoryEntity>): Int? =
        labelRes(name, categories.firstOrNull { it.name == name }?.let { it.builtinKey == it.name })

    fun emojiFor(name: String): String? = userStyles[name]?.emoji?.takeIf { it.isNotBlank() }

    /**
     * Resolves the display color for a category. Prefers the user's assigned color
     * ([overrideHex], e.g. "#4CAF50" from CategoryEntity), then the built-in palette,
     * then gray — so user-created categories no longer render gray in Analytics (#586).
     */
    fun colorFor(name: String, overrideHex: String? = null): Color {
        // A built-in's stored seed isn't a choice the user made: it follows the palette.
        val seedColor = userStyles[name]?.customColor == false && categories[name] != null
        if (!overrideHex.isNullOrBlank() && !seedColor) {
            runCatching { Color(android.graphics.Color.parseColor(overrideHex)) }
                .getOrNull()?.let { return it }
        }
        val userColor = userStyles[name]?.colorHex?.let { hex -> runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull() }
        if (userColor != null && userStyles[name]?.customColor == true) return userColor
        return categories[name]?.color ?: userColor ?: Color.Gray
    }

    /**
     * [color] as a slice or icon on a dark [surface], where a category's own colour may not
     * show: Transportation's black is invisible on a dark card. Display only, so stored
     * colours stay as they are. Black becomes slate on every surface, since it reads as a
     * hole in a chart on light ones too; light surfaces keep every other colour unchanged.
     */
    fun visibleOn(rawColor: Color, surface: Color): Color {
        val color = if (rawColor.luminance() < NEAR_BLACK_LUMINANCE) SLATE else rawColor
        val surfaceLuminance = surface.luminance()
        fun contrast(c: Color): Float {
            val luminance = c.luminance()
            return (maxOf(luminance, surfaceLuminance) + 0.05f) / (minOf(luminance, surfaceLuminance) + 0.05f)
        }
        if (surfaceLuminance >= 0.5f || contrast(color) >= MIN_MARK_CONTRAST) return color

        // A neutral has no hue to keep, so it ends near white, like a black logo on a dark
        // background. Stopping at the minimum would land it on the grey of "Others".
        val chroma = maxOf(color.red, color.green, color.blue) - minOf(color.red, color.green, color.blue)
        val target = if (chroma < NEUTRAL_MAX_CHROMA) NEUTRAL_MARK_CONTRAST else MIN_MARK_CONTRAST
        return generateSequence(0.05f) { it + 0.05f }
            .takeWhile { it <= 1f }
            .map { lerp(color, Color.White, it) }
            .firstOrNull { contrast(it) >= target }
            ?: Color.White
    }

    private val SLATE = Color(0xFF37474F)
    private const val NEAR_BLACK_LUMINANCE = 0.01f
    private const val MIN_MARK_CONTRAST = 3f
    private const val NEUTRAL_MARK_CONTRAST = 8f
    private const val NEUTRAL_MAX_CHROMA = 0.1f

    /**
     * Built-in icon/colour by name. A renamed built-in (#823) is found through its
     * original name, carried in [userStyles], so every `categories[name]` call site
     * keeps working after a rename.
     */
    val categories: Map<String, CategoryInfo> by lazy {
        object : Map<String, CategoryInfo> by builtins {
            // Identity first: a built-in renamed to another's freed name keeps its own icon.
            override fun get(key: String): CategoryInfo? =
                userStyles[key]?.builtinKey?.let { builtins[it] } ?: builtins[key]
            override fun containsKey(key: String): Boolean = get(key) != null
        }
    }

    private val builtins = mapOf(
        "Food & Dining" to CategoryInfo(
            label = R.string.category_food_dining,
            icon = Icons.Outlined.Restaurant,
            color = Color(0xFFC8703C),
            fallbackIcon = Icons.Outlined.Fastfood
        ),
        "Groceries" to CategoryInfo(
            label = R.string.category_groceries,
            icon = Icons.Outlined.ShoppingCart,
            color = Color(0xFF5F9148),
            fallbackIcon = Icons.Outlined.LocalGroceryStore
        ),
        "Transportation" to CategoryInfo(
            label = R.string.category_transportation,
            icon = Icons.Outlined.DirectionsCar,
            color = Color(0xFF64788C),
            fallbackIcon = Icons.Outlined.Commute
        ),
        "Shopping" to CategoryInfo(
            label = R.string.category_shopping,
            icon = Icons.Outlined.ShoppingBag,
            color = Color(0xFFB8902F),
            fallbackIcon = Icons.Outlined.Store
        ),
        "Bills & Utilities" to CategoryInfo(
            label = R.string.category_bills_utilities,
            icon = Icons.Outlined.Receipt,
            color = Color(0xFF3D8FA3),
            fallbackIcon = Icons.Outlined.Payment
        ),
        "Entertainment" to CategoryInfo(
            label = R.string.category_entertainment,
            icon = Icons.Outlined.MovieFilter,
            color = Color(0xFFB9524F),
            fallbackIcon = Icons.Outlined.PlayCircle
        ),
        "Healthcare" to CategoryInfo(
            label = R.string.category_healthcare,
            icon = Icons.Outlined.LocalHospital,
            color = Color(0xFF3F9A83),
            fallbackIcon = Icons.Outlined.HealthAndSafety
        ),
        "Investments" to CategoryInfo(
            label = R.string.category_investments,
            icon = Icons.AutoMirrored.Outlined.TrendingUp,
            color = Color(0xFF7459AD),
            fallbackIcon = Icons.AutoMirrored.Outlined.ShowChart
        ),
        "Banking" to CategoryInfo(
            label = R.string.category_banking,
            icon = Icons.Outlined.AccountBalance,
            color = Color(0xFF3F6FA3),
            fallbackIcon = Icons.Outlined.AccountBalanceWallet
        ),
        "Personal Care" to CategoryInfo(
            label = R.string.category_personal_care,
            icon = Icons.Outlined.Face,
            color = Color(0xFFA85B8C),
            fallbackIcon = Icons.Outlined.Spa
        ),
        "Education" to CategoryInfo(
            label = R.string.category_education,
            icon = Icons.Outlined.School,
            color = Color(0xFF8D6BB8),
            fallbackIcon = Icons.Outlined.Book
        ),
        "Mobile" to CategoryInfo(
            label = R.string.category_mobile,
            icon = Icons.Outlined.Smartphone,
            color = Color(0xFF4F5FA8),
            fallbackIcon = Icons.Outlined.PhoneAndroid
        ),
        "Fitness" to CategoryInfo(
            label = R.string.category_fitness,
            icon = Icons.Outlined.FitnessCenter,
            color = Color(0xFFC75D7E),
            fallbackIcon = Icons.Outlined.SportsMartialArts
        ),
        "Insurance" to CategoryInfo(
            label = R.string.category_insurance,
            icon = Icons.Outlined.Shield,
            color = Color(0xFF4A80B5),
            fallbackIcon = Icons.Outlined.Security
        ),
        "Tax" to CategoryInfo(
            label = R.string.category_tax,
            icon = Icons.Outlined.AccountBalanceWallet,
            color = Color(0xFF8A6A50),
            fallbackIcon = Icons.Outlined.Receipt
        ),
        "Bank Charges" to CategoryInfo(
            label = R.string.category_bank_charges,
            icon = Icons.Outlined.MoneyOff,
            color = Color(0xFF8C8578),
            fallbackIcon = Icons.Outlined.RemoveCircle
        ),
        "Credit Card Payment" to CategoryInfo(
            label = R.string.category_credit_card_payment,
            icon = Icons.Outlined.CreditCard,
            color = Color(0xFF5C7FB0),
            fallbackIcon = Icons.Outlined.Payment
        ),
        "Salary" to CategoryInfo(
            label = R.string.category_salary,
            icon = Icons.Outlined.Payments,
            color = Color(0xFF2E8F6F),
            fallbackIcon = Icons.Outlined.AttachMoney
        ),
        "Income" to CategoryInfo(
            label = R.string.category_income,
            icon = Icons.Outlined.AddCircle,
            color = Color(0xFF2E8F6F),
            fallbackIcon = Icons.AutoMirrored.Outlined.TrendingUp
        ),
        "Travel" to CategoryInfo(
            label = R.string.category_travel,
            icon = Icons.Outlined.Flight,
            color = Color(0xFF2F9EB0),
            fallbackIcon = Icons.Outlined.AirplanemodeActive
        ),
        "Others" to CategoryInfo(
            label = R.string.category_others,
            icon = Icons.Outlined.Category,
            color = Color(0xFF8A8375),
            fallbackIcon = Icons.Outlined.MoreHoriz
        )
    )

    /**
     * Get category for a merchant name.
     * Delegates to SharedCategoryMapping (single source of truth).
     */
    fun getCategory(merchantName: String): String {
        return SharedCategoryMapping.getCategory(merchantName)
    }
}

/** A category name as the user should read it: built-ins in the app's language. */
@Composable
fun categoryLabel(name: String): String =
    CategoryMapping.labelRes(name)?.let { stringResource(it) } ?: name

/** [CategoryMapping.colorFor], kept visible on the current theme's card surface. */
@Composable
@ReadOnlyComposable
fun categoryDisplayColor(name: String, overrideHex: String? = null): Color =
    CategoryMapping.visibleOn(
        CategoryMapping.colorFor(name, overrideHex),
        MaterialTheme.colorScheme.surfaceContainerLow
    )

/**
 * Icon provider with fallback mechanism
 */
object IconProvider {

    /**
     * Get icon for a merchant with fallback logic
     * 1. Try to get brand-specific icon
     * 2. If not found, use category icon
     * 3. If category not found, use default icon
     */
    fun getIconForMerchant(merchantName: String): IconResource {
        BrandIcons.getIconResource(merchantName)?.let { iconRes ->
            return IconResource.DrawableResource(iconRes)
        }

        val category = CategoryMapping.getCategory(merchantName)
        CategoryMapping.emojiFor(category)?.let { return IconResource.Emoji(it, CategoryMapping.colorFor(category)) }
        val categoryInfo = CategoryMapping.categories[category]
            ?: CategoryMapping.categories["Others"]!!

        return IconResource.VectorIcon(
            icon = categoryInfo.icon,
            tint = categoryInfo.color
        )
    }

    /**
     * Get category info including icon and color
     */
    fun getCategoryInfo(merchantName: String): CategoryMapping.CategoryInfo {
        val category = CategoryMapping.getCategory(merchantName)
        return CategoryMapping.categories[category]
            ?: CategoryMapping.categories["Others"]!!
    }

    /**
     * Get icon for a transaction, using an explicitly set category when available.
     * 1. Try brand-specific icon
     * 2. If not found, use provided category (if valid)
     * 3. Otherwise, derive category from merchant name
     * 4. If still not found, use default icon
     */
    fun getTransactionIcon(merchantName: String, category: String?): IconResource {
        // A user's explicit category emoji outranks the auto-detected brand logo (#760).
        if (category.isValidCategoryOverride()) {
            CategoryMapping.emojiFor(category!!)?.let {
                return IconResource.Emoji(it, CategoryMapping.colorFor(category))
            }
        }
        BrandIcons.getIconResource(merchantName)?.let { iconRes ->
            return IconResource.DrawableResource(iconRes)
        }

        val effectiveCategory = if (category.isValidCategoryOverride()) category!!
            else CategoryMapping.getCategory(merchantName)

        CategoryMapping.emojiFor(effectiveCategory)?.let {
            return IconResource.Emoji(it, CategoryMapping.colorFor(effectiveCategory))
        }
        val categoryInfo = CategoryMapping.categories[effectiveCategory]
            ?: CategoryMapping.categories["Others"]!!

        return IconResource.VectorIcon(
            icon = categoryInfo.icon,
            // A user category keeps its own color; unknown names fall back to Others'.
            tint = if (effectiveCategory in CategoryMapping.userStyles) CategoryMapping.colorFor(effectiveCategory)
                else categoryInfo.color
        )
    }
}

/**
 * Sealed class for different icon types
 */
sealed class IconResource {
    data class DrawableResource(val resId: Int) : IconResource()
    data class VectorIcon(val icon: ImageVector, val tint: Color) : IconResource()
    /** A user-picked emoji for a custom category (#760); [tint] is the category color. */
    data class Emoji(val emoji: String, val tint: Color) : IconResource()
}

/**
 * Returns true if this category string represents a valid override
 * (non-null, non-blank, not the "Uncategorized" sentinel).
 */
internal fun String?.isValidCategoryOverride(): Boolean =
    !this.isNullOrBlank() && !this.equals("Uncategorized", ignoreCase = true)
