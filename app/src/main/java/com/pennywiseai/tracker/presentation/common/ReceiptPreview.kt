package com.pennywiseai.tracker.presentation.common

import android.content.Context
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import coil3.compose.AsyncImage
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.receipt.ReceiptManager
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A receipt thumbnail: the photo, or a PDF's first page. A PDF that can't be drawn
 * (still loading, or a provider that won't seek) shows a plain PDF tile instead.
 */
@Composable
fun ReceiptPreview(
    uri: Uri,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // A content Uri's type is a provider call, so it's asked off the main thread; a stored
    // `file:` receipt is known from its name straight away.
    val type = remember(uri) {
        mutableStateOf(if (uri.scheme == "file") ReceiptManager.isPdf(context, uri) else null)
    }
    LaunchedEffect(uri) {
        if (type.value == null) type.value = withContext(Dispatchers.IO) { ReceiptManager.isPdf(context, uri) }
    }
    val isPdf = type.value
    if (isPdf == null) {
        Box(
            modifier = modifier
                .heightIn(min = 96.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        return
    }
    if (!isPdf) {
        AsyncImage(
            model = uri,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
        return
    }
    // Keyed on the file, as `type` is: produceState keeps its value across keys, so a new
    // PDF showed the previous one's page until it rendered.
    val page = remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        page.value = withContext(Dispatchers.IO) { renderFirstPage(context, uri) }
    }
    val firstPage = page.value
    if (firstPage != null) {
        Box(modifier = modifier) {
            Image(
                bitmap = firstPage,
                contentDescription = contentDescription,
                // A PDF page is white paper in either theme.
                modifier = Modifier.fillMaxWidth().background(Color.White),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter
            )
            // Marks the page as a document, which opens in a PDF app rather than zooming here.
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.sm),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = stringResource(R.string.receipt_pdf_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    } else {
        Column(
            modifier = modifier
                .heightIn(min = 96.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .semantics { contentDescription?.let { this.contentDescription = it } },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterVertically)
        ) {
            Icon(
                Icons.Default.PictureAsPdf,
                contentDescription = null,
                modifier = Modifier.size(Dimensions.Icon.large),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.receipt_pdf_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val THUMBNAIL_WIDTH_PX = 720

/**
 * The first page drawn at [THUMBNAIL_WIDTH_PX] wide (and at most twice as tall), or null
 * when the PDF won't open.
 */
private fun renderFirstPage(context: Context, uri: Uri): ImageBitmap? = try {
    // The renderer closes the descriptor too; closing it twice is harmless.
    context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
        PdfRenderer(fd).use { renderer ->
            renderer.openPage(0).use { page ->
                val height = (THUMBNAIL_WIDTH_PX.toLong() * page.height / page.width.coerceAtLeast(1))
                    .toInt().coerceIn(1, THUMBNAIL_WIDTH_PX * 2)
                val bitmap = createBitmap(THUMBNAIL_WIDTH_PX, height)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                // Scale by width only, so a page taller than the cap keeps its proportions
                // and its bottom is cropped rather than squashed into the bitmap.
                val scale = THUMBNAIL_WIDTH_PX.toFloat() / page.width.coerceAtLeast(1)
                val transform = Matrix().apply { setScale(scale, scale) }
                page.render(bitmap, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap.asImageBitmap()
            }
        }
    }
} catch (_: Exception) {
    null
} catch (_: OutOfMemoryError) {
    null
}
