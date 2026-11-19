package com.pennywiseai.tracker.presentation.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.model.ChatLink
import com.pennywiseai.tracker.data.model.PlanLink
import com.pennywiseai.tracker.data.model.ReplyLink
import com.pennywiseai.tracker.data.repository.ChatSuggestion
import com.pennywiseai.tracker.data.repository.ModelState
import com.pennywiseai.tracker.data.repository.QuickAction
import com.pennywiseai.tracker.ui.animations.pwCollapse
import com.pennywiseai.tracker.ui.animations.pwExpand
import com.pennywiseai.tracker.ui.animations.pwFadeIn
import com.pennywiseai.tracker.ui.animations.pwFadeOut
import com.pennywiseai.tracker.ui.animations.rememberAnimationsEnabled
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.mascot.Mascot
import com.pennywiseai.tracker.ui.components.mascot.MascotMood
import com.pennywiseai.tracker.ui.components.mascot.MascotShape
import com.pennywiseai.tracker.ui.components.mascot.REACTION_HOLD_MS
import com.pennywiseai.tracker.ui.components.mascot.typingGaze
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.warning
import com.pennywiseai.tracker.ui.voice.VoiceLanguageChip
import com.pennywiseai.tracker.ui.voice.VoiceMicButton
import com.pennywiseai.tracker.ui.voice.VoiceStatusBar
import com.pennywiseai.tracker.ui.voice.rememberVoiceInput
import com.pennywiseai.tracker.utils.TokenUtils
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit,
    onOpenLink: (ReplyLink) -> Unit = {}
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val modelState by viewModel.modelState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentResponse by viewModel.currentResponse.collectAsStateWithLifecycle()
    val links by viewModel.links.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val pendingAction by viewModel.pendingAction.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val isConfirming by viewModel.isConfirming.collectAsStateWithLifecycle()
    val isDeveloperMode by viewModel.isDeveloperModeEnabled.collectAsStateWithLifecycle()
    val chatStats by viewModel.chatStats.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val downloadedMB by viewModel.downloadedMB.collectAsStateWithLifecycle()
    val totalMB by viewModel.totalMB.collectAsStateWithLifecycle()

    // Stops when another tab replaces Chat or the app goes to the background.
    androidx.lifecycle.compose.LifecycleStartEffect(viewModel) {
        viewModel.onScreenShown()
        onStopOrDispose { viewModel.onScreenHidden() }
    }

    // Saveable: a question carried in from the quick add mic must survive a rotation.
    var inputText by rememberSaveable { mutableStateOf("") }
    val voice = rememberVoiceInput(onText = { inputText = it })
    // A question from the quick add mic lands in the box, not sent, once the box is there:
    // with no model it waits, shown below. READY only: LOADING is also the state Chat
    // starts in before the model is checked, and with no model the box never comes.
    val chatDraft by viewModel.chatDraft.collectAsStateWithLifecycle()
    val composerShown = modelState == ModelState.READY
    LaunchedEffect(chatDraft, composerShown) {
        val draft = chatDraft ?: return@LaunchedEffect
        if (!composerShown) return@LaunchedEffect
        voice.cancel()
        // A message already typed stays: the question goes under it.
        inputText = if (inputText.isBlank()) draft.text else inputText.trimEnd() + "\n" + draft.text
        viewModel.takeChatDraft(draft)
    }
    // The mic stops with the screen; words already heard stay in the box.
    androidx.lifecycle.compose.LifecycleStartEffect(voice) {
        onStopOrDispose {
            voice.cancel()
            voice.menuOpen = false
        }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size, currentResponse) {
        if (messages.isNotEmpty() || currentResponse.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(
                    index = if (currentResponse.isNotEmpty()) messages.size else messages.size - 1
                )
            }
        }
    }

    // "Receipt settled": only an Add that was saved gets the check + Confirm haptic.
    // Keyed on the count, so the haptic lands even when no hold keeps the check up;
    // only for Adds made since this screen came up, not ones from an earlier visit.
    val haptics = LocalHapticFeedback.current
    val addsAtEntry = remember { uiState.adds }
    LaunchedEffect(uiState.adds) {
        if (uiState.adds > addsAtEntry) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }

    // The owl is happy for a beat after the same saved Adds (the reply's mascot, or the
    // hero), and never for one from an earlier visit, a rotation or restored history. Each
    // Add is used up as it arrives, beat or not: without animations it gets none, and
    // turning them back on later never replays it. That change can only end a beat.
    val animationsEnabled = rememberAnimationsEnabled()
    val animate by rememberUpdatedState(animationsEnabled)
    var savedBeat by remember { mutableStateOf(false) }
    var addsHandled by remember { mutableIntStateOf(addsAtEntry) }
    LaunchedEffect(uiState.adds) {
        if (uiState.adds <= addsHandled) return@LaunchedEffect
        addsHandled = uiState.adds
        if (!animate) return@LaunchedEffect
        savedBeat = true
        delay(REACTION_HOLD_MS)
        savedBeat = false
    }
    LaunchedEffect(animationsEnabled) { if (!animationsEnabled) savedBeat = false }

    // The model screen's owl sleeps; the first LOADING after it wakes it, once.
    var modelWasAsleep by remember { mutableStateOf(false) }

    // The tab header: overline + big title + avatar, folding into the small bar once a
    // long conversation is scrolled.
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = stringResource(R.string.nav_chat),
                overline = stringResource(R.string.chat_overline),
                hazeState = hazeState,
                actionContent = {
                    // Quick answers get saved without a model too, so they have to be
                    // clearable without one.
                    ClearChatAction(
                        visible = messages.isNotEmpty() && modelState != ModelState.LOADING,
                        onClear = viewModel::clearChat
                    )
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            when (modelState) {
                ModelState.NOT_DOWNLOADED, ModelState.DOWNLOADING, ModelState.ERROR -> {
                    val isDownloading = modelState == ModelState.DOWNLOADING
                    SideEffect { modelWasAsleep = true }
                    // Show existing messages if any, but disable input
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // If no messages, show the download prompt centered
                        if (messages.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                ModelRequiredPrompt(
                                    isDownloading = isDownloading,
                                    downloadProgress = downloadProgress,
                                    downloadedMB = downloadedMB,
                                    totalMB = totalMB,
                                    onDownload = viewModel::startModelDownload,
                                    onCancel = viewModel::cancelDownload,
                                    onSettings = onNavigateToSettings
                                )
                            }
                        } else {
                            // Show existing messages (read-only)
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .overScrollVertical(),
                                contentPadding = chatListPadding(),
                                verticalArrangement = Arrangement.spacedBy(Spacing.smd),
                                flingBehavior = rememberOverscrollFlingBehavior { listState }
                            ) {
                                items(messages, key = { it.id }) { message ->
                                    ChatMessageItem(
                                        message = message,
                                        modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                                        topic = uiState.replyTopic?.takeIf { it.messageId == message.id },
                                        celebrating = savedBeat,
                                        links = links[message.id].orEmpty(),
                                        onOpenLink = onOpenLink
                                    )
                                }
                            }
                        }

                        chatDraft?.let { draft ->
                            Text(
                                text = stringResource(R.string.chat_draft_waiting, draft.text),
                                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.sm)
                            )
                        }
                        // These need no model, so they stay usable while there's none.
                        QuickActionChips(
                            suggestions = suggestions,
                            enabled = !uiState.isLoading && !isConfirming,
                            onAsk = viewModel::runQuickAction
                        )
                        // With no messages the centred prompt above already holds these controls.
                        if (messages.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            ModelRequiredBanner(
                                isDownloading = isDownloading,
                                downloadProgress = downloadProgress,
                                downloadedMB = downloadedMB,
                                totalMB = totalMB,
                                onDownload = viewModel::startModelDownload,
                                onCancel = viewModel::cancelDownload
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.smd + Dimensions.Component.bottomBarHeight))
                    }
                }

                ModelState.READY, ModelState.LOADING -> {
                    // Show loading overlay when model is loading
                    if (modelState == ModelState.LOADING) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                                modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                            ) {
                                // Straight from the model screen, the sleeping owl wakes up (the
                                // lids open through a blink) and gets to work; otherwise, or with
                                // animations removed, it's already thinking.
                                val wakes = remember { modelWasAsleep && animationsEnabled }
                                var awake by remember { mutableStateOf(!wakes) }
                                LaunchedEffect(Unit) {
                                    modelWasAsleep = false
                                    if (!awake) {
                                        delay(WAKE_DELAY_MS) // asleep on screen first, so the wake reads
                                        awake = true
                                    }
                                }
                                Mascot(
                                    size = Dimensions.Component.chatHeroMascot,
                                    mood = if (awake) MascotMood.Thinking else MascotMood.Asleep
                                )
                                Spacer(modifier = Modifier.height(Spacing.sm))
                                Text(
                                    text = stringResource(R.string.chat_initializing_model),
                                    style = MaterialTheme.typography.titleLargeEmphasized,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = stringResource(R.string.chat_initializing_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Developer info card
                            AnimatedVisibility(
                                visible = isDeveloperMode && messages.isNotEmpty(),
                                enter = pwExpand(),
                                exit = pwCollapse()
                            ) {
                                DeveloperInfoCard(chatStats = chatStats)
                            }

                            // Messages list
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .overScrollVertical(),
                                contentPadding = chatListPadding(),
                                verticalArrangement = Arrangement.spacedBy(Spacing.smd),
                                flingBehavior = rememberOverscrollFlingBehavior { listState },
                                reverseLayout = false
                            ) {
                                // The empty chat's owl, above the welcome. It isn't a chat row, so it
                                // fades as it goes (the first request starting) instead of swapping.
                                if (showsHero(messages, uiState.isLoading)) {
                                    item(key = HERO_KEY) {
                                        val motion = MaterialTheme.motionScheme
                                        ChatHero(
                                            happy = savedBeat,
                                            typedLength = inputText.length,
                                            modifier = Modifier.animateItem(
                                                fadeInSpec = motion.fastEffectsSpec(),
                                                placementSpec = motion.defaultSpatialSpec(),
                                                fadeOutSpec = motion.fastEffectsSpec()
                                            )
                                        )
                                    }
                                }

                                // Rows only slide (placement), never fade: every row swaps in place —
                                // the streamed reply for its saved message, the card for its "Added"
                                // line — and a fading row would draw over or under its replacement.
                                // On device a fade-in on the card also stuck at alpha 0, hiding it.
                                items(messages, key = { it.id }) { message ->
                                    ChatMessageItem(
                                        message = message,
                                        modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                                        topic = uiState.replyTopic?.takeIf { it.messageId == message.id },
                                        celebrating = savedBeat,
                                        links = links[message.id].orEmpty(),
                                        onOpenLink = onOpenLink
                                    )
                                }

                                // A transaction the model proposed — the user confirms it (#170)
                                // A saved card stays drawn (settled) for a beat after it's done.
                                (pendingAction ?: uiState.settledCard)?.let { action ->
                                    item(key = PENDING_KEY) {
                                        if (action is com.pennywiseai.tracker.data.model.PendingChatAction.Add) {
                                            AddReviewCard(
                                                rows = action.rows,
                                                categories = categories,
                                                enabled = !isConfirming,
                                                saved = uiState.cardSaved,
                                                onToggle = viewModel::toggleRow,
                                                onEdit = viewModel::editRow,
                                                onConfirm = viewModel::confirmPendingAction,
                                                onDismiss = viewModel::dismissPendingAction,
                                                modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                                            )
                                        } else {
                                            PendingActionCard(
                                                action = action,
                                                enabled = !isConfirming,
                                                onConfirm = { viewModel.confirmPendingAction() },
                                                onDismiss = { viewModel.dismissPendingAction() },
                                                modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                                            )
                                        }
                                    }
                                }

                                // The reply on its way — typing dots, then the streamed text — in one
                                // keyed slot, so the swap happens in place and the saved reply lands
                                // where it was.
                                if (currentResponse.isNotEmpty() || uiState.isLoading) {
                                    item(key = REPLY_KEY) {
                                        if (currentResponse.isNotEmpty()) {
                                            ChatMessageItem(
                                                message = com.pennywiseai.tracker.data.database.entity.ChatMessage(
                                                    message = currentResponse,
                                                    isUser = false,
                                                    timestamp = System.currentTimeMillis()
                                                ),
                                                modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                                                isStreaming = true
                                            )
                                        } else {
                                            // One status line per request, picked when the wait starts,
                                            // so a tool call's silent few seconds don't look like a hang.
                                            val status = remember(uiState.isLoading) { THINKING_LINES.random() }
                                            TypingIndicator(status = stringResource(status), modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null))
                                        }
                                    }
                                }
                            }

                            // Error message
                            AnimatedVisibility(
                                visible = uiState.error != null,
                                enter = pwExpand(),
                                exit = pwCollapse()
                            ) {
                                ChatErrorCard(
                                    message = uiState.error?.asString().orEmpty(),
                                    onDismiss = viewModel::clearError
                                )
                            }

                            // Suggestions (or the mic's status) over the composer pill.
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = Spacing.smd),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                if (voice.available && (voice.active || voice.note != null)) {
                                    VoiceStatusBar(voice)
                                } else {
                                    QuickActionChips(
                                        suggestions = suggestions,
                                        enabled = !uiState.isLoading && !isConfirming,
                                        onAsk = viewModel::runQuickAction,
                                        leading = { if (voice.available) VoiceLanguageChip(voice) }
                                    )
                                }
                                ChatComposer(
                                    text = inputText,
                                    onTextChange = { inputText = it },
                                    enabled = !uiState.isLoading,
                                    // Words land here as they're heard; editable once the mic stops.
                                    readOnly = voice.active,
                                    canSend = inputText.isNotBlank() && !uiState.isLoading && !isConfirming && !voice.active,
                                    isSending = uiState.isLoading,
                                    onSend = {
                                        viewModel.sendMessage(inputText)
                                        inputText = ""
                                        // Keep keyboard open by requesting focus
                                        focusRequester.requestFocus()
                                    },
                                    focusRequester = focusRequester,
                                    trailing = if (voice.available) {
                                        {
                                            VoiceMicButton(
                                                voice = voice,
                                                // Stop always works; only a new recording waits for the chat.
                                                enabled = voice.active || (!uiState.isLoading && !isConfirming),
                                                onTap = { voice.toggle(inputText) }
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                                )
                            }
                            // The bottom nav hides while the keyboard is up, so its
                            // clearance would just leave a gap above the keyboard.
                            if (!WindowInsets.isImeVisible) {
                                Spacer(modifier = Modifier.height(Dimensions.Component.bottomBarHeight))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The conversation's gutters: the hero sits close under the header, the last row clear of the chips. */
private fun chatListPadding() = PaddingValues(
    start = Dimensions.Padding.content,
    end = Dimensions.Padding.content,
    top = Spacing.sm,
    bottom = Spacing.md
)

/** Clear chat, in the header; it's only there while there's something to clear. */
@Composable
private fun ClearChatAction(visible: Boolean, onClear: () -> Unit) {
    AnimatedVisibility(visible = visible, enter = pwFadeIn(), exit = pwFadeOut()) {
        IconButton(onClick = onClear, shapes = IconButtonDefaults.shapes()) {
            Icon(
                Icons.Default.DeleteSweep,
                contentDescription = stringResource(R.string.chat_clear_chat)
            )
        }
    }
}

/** No model yet: the sleeping owl, why, and the download (or its progress). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModelRequiredPrompt(
    isDownloading: Boolean,
    downloadProgress: Int,
    downloadedMB: Long,
    totalMB: Long,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.padding(horizontal = Dimensions.Padding.dialog)
    ) {
        Mascot(size = Dimensions.Component.chatHeroMascot, mood = MascotMood.Asleep)
        PrivacyNote()
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = stringResource(if (isDownloading) R.string.chat_downloading_model else R.string.chat_model_required_title),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            textAlign = TextAlign.Center
        )
        Text(
            text = if (isDownloading) stringResource(R.string.chat_download_progress, downloadedMB, totalMB) else stringResource(R.string.chat_model_required_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        if (isDownloading) {
            LinearWavyProgressIndicator(
                progress = { downloadProgress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(onClick = onCancel, shapes = ButtonDefaults.shapes()) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.Icon.small)
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(stringResource(R.string.chat_cancel))
            }
        } else {
            Button(onClick = onDownload, shapes = ButtonDefaults.shapes()) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.Icon.small)
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(stringResource(R.string.chat_download_with_size, totalMB))
            }
        }
        TextButton(onClick = onSettings, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.chat_settings))
        }
    }
}

/** Under an existing conversation with no model: download it (or follow the download). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModelRequiredBanner(
    isDownloading: Boolean,
    downloadProgress: Int,
    downloadedMB: Long,
    totalMB: Long,
    onDownload: () -> Unit,
    onCancel: () -> Unit
) {
    PennyWiseCardV2(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.Padding.content),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        if (isDownloading) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.chat_downloading_model),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.chat_download_progress, downloadedMB, totalMB),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.padding(start = Spacing.sm),
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Text(stringResource(R.string.chat_cancel))
                    }
                }
                LinearWavyProgressIndicator(
                    progress = { downloadProgress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.chat_model_required_short),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        // 1.6 GB via the system Download Manager; some phones (Samsung
                        // with Data saver) won't start it on mobile data.
                        text = stringResource(R.string.chat_model_size_hint),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Button(
                    onClick = onDownload,
                    modifier = Modifier.padding(start = Spacing.sm),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.chat_download))
                }
            }
        }
    }
}

/** A failed request, above the composer; dismissable. */
@Composable
private fun ChatErrorCard(message: String, onDismiss: () -> Unit) {
    PennyWiseCardV2(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.Padding.content)
            .padding(bottom = Spacing.sm),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.chat_dismiss),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

/**
 * The big pill the user types in: the text (with the mic at its end) and a filled round
 * send button. It grows to three lines; its corners stay round at every height.
 */
@Composable
private fun ChatComposer(
    text: String,
    onTextChange: (String) -> Unit,
    enabled: Boolean,
    readOnly: Boolean,
    canSend: Boolean,
    isSending: Boolean,
    onSend: () -> Unit,
    focusRequester: FocusRequester,
    trailing: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier
) {
    val clear = Color.Transparent
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(end = Spacing.xs),
            verticalAlignment = Alignment.Bottom
        ) {
            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.chat_input_placeholder)) },
                enabled = enabled,
                readOnly = readOnly,
                trailingIcon = trailing,
                maxLines = 3,
                // Arabic from the mic (or typed) reads right to left.
                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = clear,
                    unfocusedContainerColor = clear,
                    disabledContainerColor = clear,
                    focusedIndicatorColor = clear,
                    unfocusedIndicatorColor = clear,
                    disabledIndicatorColor = clear
                )
            )
            FilledIconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier
                    .padding(vertical = Spacing.xs)
                    .size(Dimensions.Component.minTouchTarget),
                shapes = IconButtonDefaults.shapes()
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Dimensions.Icon.medium),
                        strokeWidth = Spacing.xxs
                    )
                } else {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = stringResource(R.string.chat_send)
                    )
                }
            }
        }
    }
}

@Composable
fun DeveloperInfoCard(
    chatStats: ChatStats,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val usageHint = remember(chatStats.contextUsagePercent) {
        TokenUtils.getUsageColorHint(chatStats.contextUsagePercent)
    }
    val usageColor = when (usageHint) {
        "critical" -> MaterialTheme.colorScheme.error
        "warning" -> MaterialTheme.colorScheme.warning
        else -> MaterialTheme.colorScheme.primary
    }

    PennyWiseCardV2(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.Padding.content)
            .padding(bottom = Spacing.sm),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        onClick = { isExpanded = !isExpanded }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Code,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.Icon.small),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = pluralStringResource(R.plurals.chat_stats_model_messages, chatStats.messageCount, chatStats.messageCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.chat_stats_tokens, TokenUtils.formatNumber(chatStats.estimatedTokens)),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = usageColor
                )
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(if (isExpanded) R.string.chat_collapse else R.string.chat_expand),
                    modifier = Modifier.size(Dimensions.Icon.small),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = pwExpand(),
            exit = pwCollapse()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Spacer(modifier = Modifier.height(Spacing.xs))

                // Context usage
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.chat_context_usage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.chat_context_percent, chatStats.contextUsagePercent),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = usageColor
                    )
                }

                LinearProgressIndicator(
                    progress = { chatStats.contextUsagePercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Spacing.xs)
                        .clip(CircleShape),
                    color = usageColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    drawStopIndicator = {}
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.chat_stats_tokens_of_max, TokenUtils.formatNumber(chatStats.estimatedTokens), TokenUtils.formatNumber(chatStats.maxTokens)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// Keys for the list's non-message rows; message rows key on their saved id.
private const val HERO_KEY = "chat-hero"
private const val PENDING_KEY = "chat-pending-card"
private const val REPLY_KEY = "chat-reply"

/** How long the model screen's owl is seen asleep before it wakes for LOADING. */
private const val WAKE_DELAY_MS = 400L

/**
 * The hero shows on an empty chat: once the welcome has loaded (so history still loading
 * doesn't flash it), while nothing the user asked is in it and no request is running.
 */
internal fun showsHero(messages: List<com.pennywiseai.tracker.data.database.entity.ChatMessage>, isLoading: Boolean): Boolean =
    !isLoading && messages.isNotEmpty() && messages.none { it.isUser }

/**
 * The empty chat's owl, large, over the privacy promise: a tap pokes it, a drag has it
 * look around, and while the user types it watches the input below. The owl is
 * decorative: TalkBack skips it and reads the note.
 */
@Composable
private fun ChatHero(happy: Boolean, typedLength: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.smd)
    ) {
        Mascot(
            size = Dimensions.Component.chatHeroMascot,
            mood = if (happy) MascotMood.Happy else MascotMood.Calm,
            look = if (typedLength > 0) typingGaze(typedLength) else null,
            interactive = true,
            alive = true
        )
        PrivacyNote()
    }
}

/** "Runs on this phone · nothing is uploaded": a small tonal pill with a lock. */
@Composable
private fun PrivacyNote(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = Dimensions.Component.statusChipHeight)
                .padding(horizontal = Spacing.smd, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(Dimensions.Icon.small)
            )
            Text(
                text = stringResource(R.string.chat_privacy_note),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}

private val THINKING_LINES = listOf(
    R.string.chat_thinking_reading,
    R.string.chat_thinking_working,
    R.string.chat_thinking_checking,
    R.string.chat_thinking_crunching,
    R.string.chat_thinking_one_moment
)

/** The wait for a reply: the owl thinks in its avatar spot, the status in a reply bubble. */
@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    status: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = REPLY_END_INSET),
        horizontalArrangement = Arrangement.Start
    ) {
        // The owl thinks while the reply is on its way; with animations removed it
        // holds a still thinking pose.
        Mascot(size = Dimensions.Icon.large, mood = MascotMood.Thinking)
        Spacer(modifier = Modifier.width(Spacing.sm))
        if (status != null) {
            ChatBubble(isUser = false, modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** How far a user bubble stays from the start edge, and a reply from the end: about 80% wide at most. */
private val USER_START_INSET = Spacing.xxxl
private val REPLY_END_INSET = Spacing.lg

/**
 * A message bubble: big rounded corners, with the one by the speaker tighter. The user's
 * own are primary; replies sit on surfaceContainer beside the owl.
 */
@Composable
private fun ChatBubble(
    isUser: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shapes = MaterialTheme.shapes
    val tail = shapes.extraSmall.topStart
    Surface(
        modifier = modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        shape = if (isUser) shapes.extraLarge.copy(topEnd = tail) else shapes.extraLarge.copy(topStart = tail),
        color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.smd),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            content = content
        )
    }
}

/**
 * One chat message. Every reply has the owl at its start; the latest one's owl takes the
 * [topic]'s shape, with "Saved" by the time for a confirmed Add.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatMessageItem(
    message: com.pennywiseai.tracker.data.database.entity.ChatMessage,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    topic: ReplyTopic? = null,
    celebrating: Boolean = false,
    links: List<ReplyLink> = emptyList(),
    onOpenLink: (ReplyLink) -> Unit = {}
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val isUser = message.isUser

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (isUser) USER_START_INSET else Spacing.none,
                end = if (isUser) Spacing.none else REPLY_END_INSET
            ),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            ReplyAvatar(topic = topic, celebrating = celebrating)
            Spacer(modifier = Modifier.width(Spacing.sm))
        }
        ChatBubble(isUser = isUser, modifier = Modifier.weight(1f, fill = false)) {
            Text(
                // Amounts are baked into the stored text; mask them on screen only.
                text = com.pennywiseai.tracker.utils.CurrencyFormatter.maskAmountsIn(message.message),
                // Direction from the text itself: an English reply in the Arabic UI was laid
                // out right to left, so ".Hi! I'm PennyWise AI" put the full stop first (#1057).
                style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content)
            )

            // The transactions behind the figures above, for this session only.
            if (links.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    links.forEach { link -> ChatLinkChip(link, onClick = { onOpenLink(link) }) }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                if (isStreaming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Dimensions.Icon.tiny),
                        strokeWidth = Spacing.xxs,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = timeFormat.format(Date(message.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser)
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = Dimensions.Alpha.subtitle)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (topic?.saved == true) SavedBadge()
            }
        }
    }
}

@Composable
private fun ChatLinkChip(link: ReplyLink, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.onSecondaryContainer
    AssistChip(
        onClick = onClick,
        // A long category wraps to two lines: room above and below so they don't touch the border.
        label = { Text(link.label, modifier = Modifier.padding(vertical = Spacing.xs)) },
        leadingIcon = {
            Icon(
                when (link) {
                    is ChatLink -> Icons.AutoMirrored.Filled.ReceiptLong
                    is PlanLink -> if (link.plan == PlanLink.Plan.BUDGETS) Icons.Default.Savings else Icons.Default.Event
                },
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize)
            )
        },
        shape = MaterialTheme.shapes.medium,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            labelColor = color,
            leadingIconContentColor = color
        ),
        border = null
    )
}

/**
 * The owl at a reply's start. The latest reply's owl turns into the shape of what it
 * answered and shows its mood; it starts as the plain calm owl, so the shape and a happy
 * reply's hop arrive as a change and play. Outside the beat after a save it is calm, and
 * once the reply stops being the latest it turns back into the owl.
 */
@Composable
private fun ReplyAvatar(topic: ReplyTopic?, celebrating: Boolean) {
    // The figure stays while the shape fades back to the owl.
    var shown by remember { mutableStateOf(topic) }
    if (topic != null && topic != shown) shown = topic
    val targetShape = topic?.shape ?: MascotShape.Owl
    val targetMood = when {
        topic == null -> MascotMood.Calm
        topic.mood == MascotMood.Happy && !celebrating -> MascotMood.Calm
        else -> topic.mood
    }
    var shape by remember { mutableStateOf(MascotShape.Owl) }
    var mood by remember { mutableStateOf(MascotMood.Calm) }
    LaunchedEffect(targetShape) { shape = targetShape }
    LaunchedEffect(targetMood) { mood = targetMood }
    Mascot(size = Dimensions.Icon.large, shape = shape, mood = mood, value = shown?.value ?: 0f)
}

/** "Saved" by a confirmed Add's time. Silent to TalkBack: the reply's own text says the same. */
@Composable
private fun SavedBadge() {
    val text = stringResource(R.string.chat_badge_saved)
    val appear = remember { MutableTransitionState(false) }.apply { targetState = true }
    AnimatedVisibility(visibleState = appear, enter = pwFadeIn()) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            maxLines = 1,
            modifier = Modifier
                .clearAndSetSemantics {}
                .background(MaterialTheme.colorScheme.inverseSurface, CircleShape)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
        )
    }
}

/** Confirm card for a delete or update the AI proposed (#170). Nothing happens without the tap. */
@Composable
private fun PendingActionCard(
    action: com.pennywiseai.tracker.data.model.PendingChatAction,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val own = { t: com.pennywiseai.tracker.data.database.entity.TransactionEntity -> com.pennywiseai.tracker.utils.CurrencyFormatter.formatCurrency(t.amount, t.currency) }
    val (title, headline, detail, button) = when (action) {
        // AddReviewCard shows these.
        is com.pennywiseai.tracker.data.model.PendingChatAction.Add -> return
        is com.pennywiseai.tracker.data.model.PendingChatAction.Delete -> {
            val t = action.transaction
            listOf(
                stringResource(R.string.chat_action_delete_title),
                "${own(t)} · ${t.merchantName}",
                "${categoryLabel(t.category)} · ${t.dateTime.toLocalDate()}",
                stringResource(R.string.chat_action_delete)
            )
        }
        is com.pennywiseai.tracker.data.model.PendingChatAction.Update -> {
            val t = action.transaction
            val changes = listOfNotNull(
                action.newMerchant?.let { stringResource(R.string.chat_action_change_merchant, it) },
                action.newCategory?.let { stringResource(R.string.chat_action_change_category, categoryLabel(it)) }
            ).joinToString(", ")
            listOf(
                stringResource(R.string.chat_action_update_title),
                "${own(t)} · ${t.merchantName}",
                "${categoryLabel(t.category)} · ${t.dateTime.toLocalDate()}\n$changes",
                stringResource(R.string.chat_action_update)
            )
        }
    }
    val isDelete = action is com.pennywiseai.tracker.data.model.PendingChatAction.Delete
    PennyWiseCardV2(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (isDelete) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        val fg = if (isDelete) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = fg)
            Text(text = headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = fg)
            Text(text = detail, style = MaterialTheme.typography.bodySmall, color = fg)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = onDismiss, enabled = enabled, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.chat_cancel)) }
                Button(
                    onClick = onConfirm,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    colors = if (isDelete) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
                    shapes = ButtonDefaults.shapes()
                ) { Text(button) }
            }
        }
    }
}

/**
 * M8: one-tap prompts the app handles itself, no model involved: only those that apply now
 * ([ChatViewModel.suggestions]). [onAsk] gets the question as the chip says it. Tonal chips
 * with no outline, in one scrolling row over the composer.
 */
@Composable
private fun QuickActionChips(
    suggestions: List<ChatSuggestion>,
    enabled: Boolean,
    onAsk: (QuickAction, String) -> Unit,
    leading: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Dimensions.Padding.content),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        suggestions.forEach { suggestion ->
            val label = if (suggestion.overdue > 0) {
                pluralStringResource(R.plurals.chat_quick_bills_overdue, suggestion.overdue, suggestion.overdue)
            } else {
                suggestion.action.text(LocalContext.current)
            }
            SuggestionChip(
                onClick = { onAsk(suggestion.action, label) },
                label = { Text(label) },
                enabled = enabled,
                shape = MaterialTheme.shapes.medium,
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                border = null
            )
        }
    }
}
