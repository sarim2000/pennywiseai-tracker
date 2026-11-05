package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.shape.CircleShape
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.icons.categoryDisplayColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.icons.BrandIcons
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.icons.IconProvider
import com.pennywiseai.tracker.ui.icons.IconResource
import com.pennywiseai.tracker.ui.icons.isValidCategoryOverride
import com.pennywiseai.tracker.ui.icons.categoryLabel

/**
 * Displays a brand icon with intelligent fallback
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BrandIcon(
    merchantName: String,
    category: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    showBackground: Boolean = true
) {
    val iconResource = IconProvider.getTransactionIcon(merchantName, category)

    // Category glyphs get a soft tint of their colour, the same as Analytics and the
    // category list. Solid discs were the loudest thing in a row, ahead of the merchant
    // and the amount. Real brand logos keep their brand colour.
    val glyphTint = (iconResource as? IconResource.VectorIcon)?.tint
        ?.let { CategoryMapping.visibleOn(it, MaterialTheme.colorScheme.surfaceContainerLow) }
    val backgroundColor = if (glyphTint != null) {
        glyphTint.copy(alpha = CATEGORY_AVATAR_ALPHA)
    } else if (category.isValidCategoryOverride()
        && iconResource !is IconResource.DrawableResource
    ) {
        when (iconResource) {
            is IconResource.VectorIcon -> iconResource.tint
            is IconResource.Emoji -> iconResource.tint
            is IconResource.DrawableResource -> MaterialTheme.colorScheme.surfaceVariant
        }
    } else {
        val brandColor = BrandIcons.getBrandColor(merchantName)
        brandColor?.let { Color(it.toColorInt()) }
            ?: MaterialTheme.colorScheme.surfaceVariant
    }
    
    Box(
        modifier = modifier
            .size(size)
            .then(
                if (showBackground) {
                    Modifier
                        .clip(CircleShape)
                        .background(backgroundColor)
                        .padding(8.dp)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        when (iconResource) {
            is IconResource.DrawableResource -> {
                // Brand logo from drawable
                Image(
                    painter = painterResource(id = iconResource.resId),
                    contentDescription = merchantName,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is IconResource.VectorIcon -> {
                // Category icon fallback
                Icon(
                    imageVector = iconResource.icon,
                    contentDescription = merchantName,
                    tint = glyphTint ?: iconResource.tint,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is IconResource.Emoji -> EmojiGlyph(iconResource.emoji, size * 0.5f)
        }
    }
}

/** How strongly a category avatar's disc takes its glyph's colour. */
private const val CATEGORY_AVATAR_ALPHA = 0.14f

/** A category's icon in its standard tinted avatar: one treatment across the app. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CategoryAvatar(category: String, modifier: Modifier = Modifier, size: Dp = Dimensions.Icon.list) {
    val tint = categoryDisplayColor(category)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = CATEGORY_AVATAR_ALPHA)),
        contentAlignment = Alignment.Center
    ) {
        CategoryIcon(category = category, size = size * 0.55f, tint = tint)
    }
}

/**
 * Letter avatar for merchants without icons
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LetterAvatar(
    merchantName: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val letter = merchantName.firstOrNull()?.uppercase() ?: "?"
    val backgroundColor = BrandIcons.getBrandColor(merchantName)?.let { 
        Color(it.toColorInt()) 
    } ?: generateColorFromString(merchantName)
    
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * Category icon with consistent styling
 */
@Composable
fun CategoryIcon(
    category: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color? = null
) {
    CategoryMapping.emojiFor(category)?.let { emoji ->
        Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
            EmojiGlyph(emoji, size * 0.8f)
        }
        return
    }
    val categoryInfo = CategoryMapping.categories[category]
        ?: CategoryMapping.categories["Others"]!!

    Icon(
        imageVector = categoryInfo.icon,
        contentDescription = categoryLabel(category),
        tint = tint ?: categoryDisplayColor(category),
        modifier = modifier.size(size)
    )
}

/** An emoji drawn at a font size that fills [box] like a glyph would (#760). */
@Composable
fun EmojiGlyph(emoji: String, box: Dp) {
    val fontSize = with(LocalDensity.current) { box.toSp() }
    Text(
        text = emoji,
        fontSize = fontSize,
        lineHeight = fontSize,
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.Center
    )
}

/**
 * Utility function to generate consistent colors from strings
 */
private fun generateColorFromString(str: String): Color {
    val colors = listOf(
        Color(0xFF6750A4), // Material Purple
        Color(0xFF0061A4), // Material Blue
        Color(0xFF006D40), // Material Green
        Color(0xFFB3261E), // Material Red
        Color(0xFF9A4521), // Material Orange
        Color(0xFF6D4C41), // Material Brown
        Color(0xFF455A64), // Material Blue Grey
        Color(0xFF5E35B1), // Deep Purple
        Color(0xFF43A047), // Green
        Color(0xFFE53935), // Red
    )
    
    val hash = str.hashCode()
    return colors[Math.abs(hash) % colors.size]
}

/**
 * Extension to convert hex string to Color Int
 */
private fun String.toColorInt(): Int {
    // Remove # if present and parse hex
    val hex = this.removePrefix("#")
    return android.graphics.Color.parseColor("#$hex")
}