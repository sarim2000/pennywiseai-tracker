package com.pennywiseai.tracker.presentation.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.preferences.DEFAULT_USER_NAME
import com.pennywiseai.tracker.ui.components.AvatarHelper
import com.pennywiseai.tracker.ui.components.ProfileAvatar
import com.pennywiseai.tracker.ui.components.RoundedField
import com.pennywiseai.tracker.ui.text.withCommittedText
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.bestInk
import kotlinx.coroutines.launch

/**
 * Change the name and avatar picked in onboarding. Edits stay in the sheet until Save,
 * and the big preview shows them as the headers will.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EditProfileSheet(
    state: SettingsHubState,
    onImportPhoto: suspend (Uri) -> String?,
    onSave: (name: String, imageUri: String?, backgroundColor: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    // "User" is the stored stand-in for no name; start the field empty rather than on it.
    val nameState = rememberTextFieldState(state.name.takeUnless { it == DEFAULT_USER_NAME }.orEmpty())
    // Saveable, so rotation or the process dying behind the photo picker keeps the draft.
    var imageUri by rememberSaveable { mutableStateOf(state.imageUri) }
    var color by rememberSaveable { mutableIntStateOf(state.backgroundColor) }
    // The photo being imported. Save waits for it. A later avatar choice bumps the token;
    // clearing pendingPhoto only schedules recomposition and does not stop this import.
    var pendingPhoto by rememberSaveable { mutableStateOf<Uri?>(null) }
    var selectionToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(selectionToken) {
        val picked = pendingPhoto ?: return@LaunchedEffect
        val startedToken = selectionToken
        val imported = onImportPhoto(picked)
        val next = applyPhotoImport(
            draft = PhotoImportDraft(
                imageUri = imageUri,
                importPending = pendingPhoto != null,
                selectionToken = selectionToken,
            ),
            startedToken = startedToken,
            importedUri = imported,
        )
        imageUri = next.imageUri
        if (!next.importPending) pendingPhoto = null
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        if (picked != null) {
            selectionToken += 1
            pendingPhoto = picked
        }
    }
    val name = nameState.text.toString()
    val canSave = name.isNotBlank() && pendingPhoto == null
    val save = withCommittedText {
        if (nameState.text.isNotBlank() && pendingPhoto == null) {
            onSave(nameState.text.toString(), imageUri, color)
            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensions.Padding.content)
                .padding(bottom = Spacing.lg)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(R.string.settings_profile_edit),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ProfileAvatar(
                    name = name,
                    imageUri = imageUri,
                    backgroundColor = color,
                    size = Dimensions.Icon.extraLarge,
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    initialsStyle = MaterialTheme.typography.headlineLargeEmphasized,
                )
            }

            OutlinedTextField(
                state = nameState,
                shape = RoundedField.shape,
                label = { Text(stringResource(R.string.onboarding_name_label)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                onKeyboardAction = { save() },
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel(stringResource(R.string.settings_profile_picture))
            FlowRow(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                AvatarHelper.avatarDrawables.indices.forEach { index ->
                    val uri = "avatar://$index"
                    Choice(
                        selected = imageUri == uri,
                        label = stringResource(R.string.onboarding_avatar_cd, index + 1),
                        onClick = {
                            selectionToken += 1
                            imageUri = uri
                            pendingPhoto = null
                        },
                    ) {
                        ProfileAvatar(name = name, imageUri = uri, backgroundColor = color, size = TILE, shape = CircleShape)
                    }
                }
                val hasPhoto = imageUri != null && AvatarHelper.resolveAvatarDrawable(imageUri!!) == null
                Choice(
                    selected = hasPhoto,
                    label = stringResource(R.string.settings_profile_photo),
                    onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                ) {
                    if (pendingPhoto != null) {
                        Box(Modifier.size(TILE), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(Dimensions.Component.progressIndicatorSize))
                        }
                    } else if (hasPhoto) {
                        ProfileAvatar(name = name, imageUri = imageUri, backgroundColor = color, size = TILE, shape = CircleShape)
                    } else {
                        Box(
                            Modifier
                                .size(TILE)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            SectionLabel(stringResource(R.string.onboarding_background_color_title))
            FlowRow(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                AvatarHelper.backgroundColors.forEachIndexed { index, colorInt ->
                    val swatch = Color(colorInt)
                    Choice(
                        selected = color == colorInt,
                        label = stringResource(R.string.settings_profile_color_cd, index + 1),
                        onClick = { color = colorInt },
                    ) {
                        Box(
                            Modifier
                                .size(TILE)
                                .background(swatch, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (color == colorInt) {
                                Icon(
                                    Icons.Default.Check,
                                    // The tile already reads as selected.
                                    contentDescription = null,
                                    tint = swatch.bestInk(),
                                    modifier = Modifier.size(Dimensions.Icon.inline),
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = save,
                enabled = canSave,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = Spacing.sm),
            ) { Text(stringResource(R.string.settings_profile_save)) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
}

/** A round option with a ring when it's the current pick. */
@Composable
private fun Choice(selected: Boolean, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .border(RING, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(RING + Spacing.xxs),
    ) { content() }
}

/** Every option, avatar or colour, is a full 48dp target inside its ring. */
private val TILE = Dimensions.Icon.avatarLarge
private val RING = Dimensions.Component.avatarRingWidth

/** Avatar draft a finished photo import may change, and the choice generation it must still match. */
internal data class PhotoImportDraft(
    val imageUri: String?,
    val importPending: Boolean,
    val selectionToken: Int,
)

/**
 * Writes [importedUri] and clears the pending import only while [startedToken] is still
 * [draft]'s selection. A preset or a newer photo bumps that token; this result is dropped
 * so it cannot overwrite the later choice or clear the newer import.
 */
internal fun applyPhotoImport(
    draft: PhotoImportDraft,
    startedToken: Int,
    importedUri: String?,
): PhotoImportDraft {
    if (startedToken != draft.selectionToken) return draft
    return draft.copy(imageUri = importedUri ?: draft.imageUri, importPending = false)
}
