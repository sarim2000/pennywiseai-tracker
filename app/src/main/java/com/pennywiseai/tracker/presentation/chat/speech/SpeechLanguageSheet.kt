package com.pennywiseai.tracker.presentation.chat.speech

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import java.util.Locale

/** Dictation language for the mic buttons. Home and Chat share one store. */
@Stable
class SpeechLanguages(
    val listeningTag: String,
    val badge: String,
    val spokenName: String,
    val menu: SpeechLanguageMenu,
    val known: Boolean,
    val settled: Boolean,
    val downloading: Set<String>,
    val downloadFailed: Boolean,
    val hintSeen: Boolean,
    val refresh: () -> Unit,
    val select: (String) -> Unit,
    val download: (String) -> Unit,
    val dismissHint: () -> Unit,
)

@Composable
fun rememberSpeechLanguages(): SpeechLanguages {
    val context = LocalContext.current
    val store = remember { SpeechLanguageStore.get(context) }
    LaunchedEffect(store) { store.refresh() }
    val saved = store.saved
    val report = store.report
    val userTags = store.userTags
    // Until the recognizer answers, offer the person's own languages so the
    // first tap is not stuck on the app language.
    val menu = if (report.known) {
        speechLanguageMenu(report.installed, report.pending, report.downloadable, userTags, saved)
    } else {
        speechLanguageMenu(userTags, emptyList(), emptyList(), userTags, saved)
    }
    val listening = listeningLanguage(menu, saved, Locale.getDefault().toLanguageTag())
    return SpeechLanguages(
        listeningTag = listening,
        badge = speechLanguageBadge(listening),
        spokenName = speechLanguageUiName(listening),
        menu = menu,
        known = report.known,
        settled = store.settled,
        downloading = store.downloading,
        downloadFailed = store.downloadFailed,
        hintSeen = store.hintSeen,
        refresh = store::refresh,
        select = store::save,
        download = store::download,
        dismissHint = store::markHintSeen,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeechLanguageSheet(
    languages: SpeechLanguages,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { languages.dismissHint() }
    LifecycleResumeEffect(Unit) {
        languages.refresh()
        onPauseOrDispose { }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.lg),
        ) {
            Text(
                text = stringResource(R.string.chat_voice_language_title),
                modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.chat_voice_language_body),
                modifier = Modifier.padding(
                    horizontal = Dimensions.Padding.content,
                    vertical = Spacing.xs,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!languages.settled) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(Spacing.md)
                        .size(Dimensions.Icon.medium)
                        .align(Alignment.CenterHorizontally),
                )
            } else if (languages.menu.ready.isNotEmpty()) {
                SectionLabel(
                    stringResource(
                        if (languages.known) {
                            R.string.chat_voice_language_ready
                        } else {
                            R.string.chat_voice_language_yours
                        },
                    ),
                )
                languages.menu.ready.forEach { choice ->
                    ReadyRow(choice, selected = choice.tag == languages.menu.selected) {
                        languages.select(choice.tag)
                        onDismiss()
                    }
                }
            }
            if (languages.settled && languages.menu.suggestions.isNotEmpty()) {
                SectionLabel(stringResource(R.string.chat_voice_language_suggest))
                languages.menu.suggestions.forEach { choice ->
                    val busy = choice.pending || languageOf(choice.tag) in languages.downloading
                    SuggestionRow(choice, busy = busy) {
                        if (!busy) languages.download(choice.tag)
                    }
                }
            }
            val showUnknown = languages.settled && !languages.known
            val showEmpty = languages.settled &&
                languages.known &&
                languages.menu.ready.isEmpty() &&
                languages.menu.suggestions.isEmpty()
            if (showUnknown || showEmpty || languages.downloadFailed) {
                Text(
                    text = stringResource(
                        when {
                            languages.downloadFailed -> R.string.chat_voice_language
                            showUnknown -> R.string.chat_voice_language_unknown
                            else -> R.string.chat_voice_language_none
                        },
                    ),
                    modifier = Modifier.padding(horizontal = Dimensions.Padding.content, vertical = Spacing.sm),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { context.openSpeechFailureSettings(SpeechFailure.LANGUAGE) },
                    modifier = Modifier.padding(horizontal = Spacing.sm),
                ) {
                    Text(stringResource(R.string.chat_voice_language_settings))
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(
            horizontal = Dimensions.Padding.content,
            vertical = Spacing.xs,
        ),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ReadyRow(
    choice: SpeechLanguageChoice,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val (native, ui) = languageNames(choice.tag)
    ListItem(
        modifier = Modifier.clickable(onClick = onSelect),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            RadioButton(
                selected = selected,
                onClick = null,
                modifier = Modifier.clearAndSetSemantics {},
            )
        },
        headlineContent = { Text(native) },
        supportingContent = ui?.let { name -> { Text(name) } },
    )
}

@Composable
private fun SuggestionRow(
    choice: SpeechLanguageChoice,
    busy: Boolean,
    onDownload: () -> Unit,
) {
    val (native, ui) = languageNames(choice.tag)
    ListItem(
        modifier = Modifier.clickable(enabled = !busy, onClick = onDownload),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(native) },
        supportingContent = ui?.let { name -> { Text(name) } },
        trailingContent = {
            Text(
                text = stringResource(
                    if (busy) R.string.chat_voice_language_downloading else R.string.chat_voice_language_download,
                ),
                color = if (busy) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                style = MaterialTheme.typography.labelLarge,
            )
        },
    )
}

@Composable
private fun languageNames(tag: String): Pair<String, String?> {
    val native = speechLanguageName(tag)
    val ui = speechLanguageUiName(tag)
    val secondary = ui.takeUnless { it.equals(native, ignoreCase = true) }
    return native to secondary
}

/** Small autonym on the mic, hidden from TalkBack. The button describes the language. */
@Composable
fun SpeechLanguageMark(
    tag: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val badge = speechLanguageBadge(tag)
    if (badge.isEmpty()) return
    Text(
        text = badge,
        modifier = modifier.clearAndSetSemantics {},
        color = color,
        fontSize = 9.sp,
        lineHeight = 9.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}

/**
 * Tonal mic that also accepts a long press. A plain icon button cannot, because
 * its own click consumes the gesture. [enabled] only gates the short tap, so a
 * busy composer can still change language.
 */
@Composable
fun DictationMicButton(
    listening: Boolean,
    languageTag: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val colors = IconButtonDefaults.filledTonalIconButtonColors()
    val background = if (enabled) colors.containerColor else colors.disabledContainerColor
    val tint = if (enabled) colors.contentColor else colors.disabledContentColor
    val holdLabel = stringResource(R.string.chat_voice_language_hold)
    val spokenDescription = contentDescription
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .combinedClickable(
                role = Role.Button,
                onClick = { if (enabled) onClick() },
                onLongClick = onLongClick,
                onLongClickLabel = holdLabel,
            )
            .semantics { this.contentDescription = spokenDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(Dimensions.Icon.medium)
                .offset(y = if (!listening && languageTag.isNotBlank()) (-5).dp else 0.dp),
        )
        if (!listening && languageTag.isNotBlank()) {
            SpeechLanguageMark(
                tag = languageTag,
                color = tint,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp),
            )
        }
    }
}
