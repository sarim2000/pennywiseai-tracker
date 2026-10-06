package com.pennywiseai.tracker.ui.components

import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pennywiseai.tracker.BuildConfig
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.Icons
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing

/**
 * Represents a single feature or change in a version
 */
data class WhatsNewItem(
    val text: String
)

/** A release's changelog: the bullet points from the Play "What's new" text. */
data class WhatsNewVersion(
    val items: List<WhatsNewItem>
)

/**
 * Reads the changelog the build copies into assets/whats_new.txt — the same
 * file Play shows as "What's new" (fastlane changelogs/<versionCode>.txt).
 *
 * Those files are bullets only, no title line:
 * • Feature one description
 * • Feature two description
 *
 * The dialog builds its own title from the app version. An older-style first
 * line that isn't a bullet ("What's New in v2.15.44") is skipped.
 */
object WhatsNewContent {

    fun parseFromAssets(context: Context): WhatsNewVersion? {
        return try {
            val content = context.assets.open("whats_new.txt")
                .bufferedReader()
                .use { it.readText() }

            parseChangelog(content)
        } catch (e: Exception) {
            null
        }
    }

    private val BULLETS = listOf("•", "-", "*")

    fun parseChangelog(content: String): WhatsNewVersion? {
        val lines = content.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
        val body = if (lines.firstOrNull()?.let { first -> BULLETS.none { first.startsWith(it) } } == true) {
            lines.drop(1) // legacy title line
        } else {
            lines
        }
        val items = body
            .map { line -> WhatsNewItem(BULLETS.fold(line) { acc, b -> acc.removePrefix(b) }.trim()) }
            .filter { it.text.isNotEmpty() }
        return if (items.isNotEmpty()) WhatsNewVersion(items) else null
    }
}

@Composable
fun WhatsNewDialog(
    version: WhatsNewVersion,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.whats_new_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = stringResource(R.string.whats_new_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                version.items.forEach { item ->
                    WhatsNewItemRow(item = item)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.whats_new_got_it))
            }
        }
    )
}

@Composable
private fun WhatsNewItemRow(item: WhatsNewItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = Spacing.xxs).size(Dimensions.Icon.small)
        )
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
