package com.pennywiseai.tracker.presentation.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.R
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
    val nameState = rememberTextFieldState(state.name.takeUnless { it == DEFAULT_NAME }.orEmpty())
    var imageUri by remember { mutableStateOf(state.imageUri) }
    var color by remember { mutableIntStateOf(state.backgroundColor) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        if (picked != null) scope.launch { onImportPhoto(picked)?.let { imageUri = it } }
    }
    val name = nameState.text.toString()
    val save = withCommittedText {
        if (nameState.text.isNotBlank()) {
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
            Text(stringResource(R.string.settings_profile_edit), style = MaterialTheme.typography.titleMedium)

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

            Text(stringResource(R.string.settings_profile_picture), style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                AvatarHelper.avatarDrawables.indices.forEach { index ->
                    val uri = "avatar://$index"
                    Choice(
                        selected = imageUri == uri,
                        label = stringResource(R.string.onboarding_avatar_cd, index + 1),
                        onClick = { imageUri = uri },
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
                    if (hasPhoto) {
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

            Text(stringResource(R.string.onboarding_background_color_title), style = MaterialTheme.typography.titleSmall)
            FlowRow(
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
                                .size(SWATCH)
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
                enabled = name.isNotBlank(),
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = Spacing.sm),
            ) { Text(stringResource(R.string.settings_profile_save)) }
        }
    }
}

/** A round option with a ring when it's the current pick. */
@Composable
private fun Choice(selected: Boolean, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .border(RING, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = label
            }
            .padding(RING + 2.dp),
    ) { content() }
}

private const val DEFAULT_NAME = "User"
private val TILE = 48.dp
private val SWATCH = 36.dp
private val RING = 3.dp
