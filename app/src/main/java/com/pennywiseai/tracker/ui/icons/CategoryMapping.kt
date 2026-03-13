package com.pennywiseai.tracker.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Commute
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.Store
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.pennywiseai.shared.domain.mapping.SharedCategoryMapping

/**
 * Category visual properties (icons, colors) for Android UI.
 * Keyword-based categorization is delegated to SharedCategoryMapping.
 */
object CategoryMapping {

    data class CategoryInfo(
        val displayName: String,
        val icon: ImageVector,
        val color: Color,
        val fallbackIcon: ImageVector = Icons.Default.Category
    )

    val categories = mapOf(
        "Food & Dining" to CategoryInfo(
            displayName = "Food & Dining",
            icon = Icons.Default.Restaurant,
            color = Color(0xFFFC8019),
            fallbackIcon = Icons.Default.Fastfood
        ),
        "Groceries" to CategoryInfo(
            displayName = "Groceries",
            icon = Icons.Default.ShoppingCart,
            color = Color(0xFF5AC85A),
            fallbackIcon = Icons.Default.LocalGroceryStore
        ),
        "Transportation" to CategoryInfo(
            displayName = "Transportation",
            icon = Icons.Default.DirectionsCar,
            color = Color(0xFF000000),
            fallbackIcon = Icons.Default.Commute
        ),
        "Shopping" to CategoryInfo(
            displayName = "Shopping",
            icon = Icons.Default.ShoppingBag,
            color = Color(0xFFFF9900),
            fallbackIcon = Icons.Default.Store
        ),
        "Bills & Utilities" to CategoryInfo(
            displayName = "Bills & Utilities",
            icon = Icons.Default.Receipt,
            color = Color(0xFF4CAF50),
            fallbackIcon = Icons.Default.Payment
        ),
        "Entertainment" to CategoryInfo(
            displayName = "Entertainment",
            icon = Icons.Default.MovieFilter,
            color = Color(0xFFE50914),
            fallbackIcon = Icons.Default.PlayCircle
        ),
        "Healthcare" to CategoryInfo(
            displayName = "Healthcare",
            icon = Icons.Default.LocalHospital,
            color = Color(0xFF10847E),
            fallbackIcon = Icons.Default.HealthAndSafety
        ),
        "Investments" to CategoryInfo(
            displayName = "Investments",
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            color = Color(0xFF00D09C),
            fallbackIcon = Icons.AutoMirrored.Filled.ShowChart
        ),
        "Banking" to CategoryInfo(
            displayName = "Banking",
            icon = Icons.Default.AccountBalance,
            color = Color(0xFF004C8F),
            fallbackIcon = Icons.Default.AccountBalanceWallet
        ),
        "Personal Care" to CategoryInfo(
            displayName = "Personal Care",
            icon = Icons.Default.Face,
            color = Color(0xFF6A4C93),
            fallbackIcon = Icons.Default.Spa
        ),
        "Education" to CategoryInfo(
            displayName = "Education",
            icon = Icons.Default.School,
            color = Color(0xFF673AB7),
            fallbackIcon = Icons.Default.Book
        ),
        "Mobile" to CategoryInfo(
            displayName = "Mobile & Recharge",
            icon = Icons.Default.Smartphone,
            color = Color(0xFF2A3890),
            fallbackIcon = Icons.Default.PhoneAndroid
        ),
        "Fitness" to CategoryInfo(
            displayName = "Fitness",
            icon = Icons.Default.FitnessCenter,
            color = Color(0xFFFF3278),
            fallbackIcon = Icons.Default.SportsMartialArts
        ),
        "Insurance" to CategoryInfo(
            displayName = "Insurance",
            icon = Icons.Default.Shield,
            color = Color(0xFF0066CC),
            fallbackIcon = Icons.Default.Security
        ),
        "Tax" to CategoryInfo(
            displayName = "Tax",
            icon = Icons.Default.AccountBalanceWallet,
            color = Color(0xFF795548),
            fallbackIcon = Icons.Default.Receipt
        ),
        "Bank Charges" to CategoryInfo(
            displayName = "Bank Charges",
            icon = Icons.Default.MoneyOff,
            color = Color(0xFF9E9E9E),
            fallbackIcon = Icons.Default.RemoveCircle
        ),
        "Credit Card Payment" to CategoryInfo(
            displayName = "Credit Card Payment",
            icon = Icons.Default.CreditCard,
            color = Color(0xFF1976D2),
            fallbackIcon = Icons.Default.Payment
        ),
        "Salary" to CategoryInfo(
            displayName = "Salary",
            icon = Icons.Default.Payments,
            color = Color(0xFF4CAF50),
            fallbackIcon = Icons.Default.AttachMoney
        ),
        "Income" to CategoryInfo(
            displayName = "Other Income",
            icon = Icons.Default.AddCircle,
            color = Color(0xFF4CAF50),
            fallbackIcon = Icons.AutoMirrored.Filled.TrendingUp
        ),
        "Travel" to CategoryInfo(
            displayName = "Travel",
            icon = Icons.Default.Flight,
            color = Color(0xFF00BCD4),
            fallbackIcon = Icons.Default.AirplanemodeActive
        ),
        "Others" to CategoryInfo(
            displayName = "Others",
            icon = Icons.Default.Category,
            color = Color(0xFF757575),
            fallbackIcon = Icons.Default.MoreHoriz
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
}

/**
 * Sealed class for different icon types
 */
sealed class IconResource {
    data class DrawableResource(val resId: Int) : IconResource()
    data class VectorIcon(val icon: ImageVector, val tint: Color) : IconResource()
}
