package com.pennywiseai.tracker.ui.components

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.pennywiseai.tracker.ui.theme.Spacing

/**
 * Represents a single feature or change in a version
 */
data class WhatsNewItem(
    val text: String
)

/**
 * Represents a version's changelog
 */
data class WhatsNewVersion(
    val title: String,
    val items: List<WhatsNewItem>
)

/**
 * Reads and parses the changelog from assets/whats_new.txt
 *
 * Expected format:
 * What's New in v2.15.44
 *
 * • Feature one description
 * • Feature two description
 * • Bug fixes and improvements
 */
object WhatsNewContent {

    /**
     * Parse the changelog file from assets
     */
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

    /**
     * Parse changelog text content
     */
    fun parseChangelog(content: String): WhatsNewVersion? {
        val lines = content.trim().lines()
        if (lines.isEmpty()) return null

        // First line is the title (e.g., "What's New in v2.15.44")
        val title = lines.first().trim()

        // Rest are bullet points starting with •, -, or *
        val items = lines.drop(1)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                // Remove bullet point prefix
                val text = line
                    .removePrefix("•")
                    .removePrefix("-")
                    .removePrefix("*")
                    .trim()
                WhatsNewItem(text)
            }
            .filter { it.text.isNotEmpty() }

        return if (items.isNotEmpty()) {
            WhatsNewVersion(title = title, items = items)
        } else {
            null
        }
    }
}

@Composable
fun WhatsNewDialog(
    version: WhatsNewVersion,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = version.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                version.items.forEach { item ->
                    WhatsNewItemRow(item = item)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Got it")
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
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
