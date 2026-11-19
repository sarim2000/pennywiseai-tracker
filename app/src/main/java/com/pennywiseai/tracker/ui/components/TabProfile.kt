package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.bestInk

/** The user as a tab header's avatar shows them, and where tapping it goes. */
@Immutable
data class TabProfile(
    val name: String,
    val imageUri: String?,
    val backgroundColor: Int,
    val onClick: () -> Unit,
)

/**
 * Provided by MainScreen around each tab's own first screen, so its header ends in the
 * avatar that opens Settings. Null everywhere else (pushed screens keep their back arrow).
 */
val LocalTabProfile = staticCompositionLocalOf<TabProfile?> { null }

/** The avatar at the end of a tab's header, in an Expressive cookie shape. Draws nothing off a tab root. */
@Composable
fun TabProfileButton(modifier: Modifier = Modifier) {
    val profile = LocalTabProfile.current ?: return
    // The margin sits outside the avatar (inside, it squeezed the picture). With a bar's own
    // 4dp end inset the avatar's edge lines up with the screen's 16dp content margin.
    Box(modifier.padding(start = Spacing.xs, end = Spacing.smd)) {
        ProfileAvatar(
            name = profile.name,
            imageUri = profile.imageUri,
            backgroundColor = profile.backgroundColor,
            size = Dimensions.Icon.avatar,
            shape = MaterialShapes.Cookie9Sided.toShape(),
            modifier = Modifier.clickable(
                onClickLabel = stringResource(R.string.settings_title),
                role = Role.Button,
                onClick = profile.onClick,
            ),
            contentDescription = stringResource(R.string.settings_title),
        )
    }
}

/** The user's picture, chosen avatar, or initials, clipped to [shape]. */
@Composable
fun ProfileAvatar(
    name: String,
    imageUri: String?,
    backgroundColor: Int,
    size: Dp,
    shape: Shape,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    /** The initials' style when there is no picture; a big avatar passes a bigger one. */
    initialsStyle: TextStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
) {
    val fill = if (backgroundColor != 0) Color(backgroundColor) else MaterialTheme.colorScheme.primaryContainer
    // A picked colour has no on* role of its own.
    val ink = if (backgroundColor != 0) fill.bestInk() else MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(fill)
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        val avatarResId = imageUri?.let { AvatarHelper.resolveAvatarDrawable(it) }
        when {
            avatarResId != null -> Image(
                painter = painterResource(id = avatarResId),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            imageUri != null -> AsyncImage(
                model = imageUri,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            else -> {
                val initials = remember(name) {
                    val parts = name.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
                    if (parts.size >= 2) "${parts.first().first()}${parts.last().first()}".uppercase()
                    else name.trim().take(2).uppercase()
                }
                Text(
                    text = initials,
                    style = initialsStyle,
                    color = ink,
                )
            }
        }
    }
}
