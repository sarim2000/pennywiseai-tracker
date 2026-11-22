package com.pennywiseai.tracker.ui.components

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

    val backgroundColor = if (category.isValidCategoryOverride()
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
                        .clip(MaterialShapes.Cookie9Sided.toShape())
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
                    tint = if (showBackground) Color.White else iconResource.tint,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is IconResource.Emoji -> EmojiGlyph(iconResource.emoji, size * 0.5f)
        }
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
        tint = tint ?: CategoryMapping.colorFor(category),
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
 * Extension to convert hex string to Color Int
 */
private fun String.toColorInt(): Int {
    // Remove # if present and parse hex
    val hex = this.removePrefix("#")
    return android.graphics.Color.parseColor("#$hex")
}