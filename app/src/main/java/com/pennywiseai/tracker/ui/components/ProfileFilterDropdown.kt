package com.pennywiseai.tracker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.domain.model.seededNameRes

@Composable
fun ProfileFilterDropdown(
    expanded: Boolean,
    profiles: List<ProfileEntity>,
    selectedProfileId: Long?,
    onProfileSelected: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        shape = MaterialTheme.shapes.large,
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        // "All" option
        SelectableDropdownMenuItem(
            selected = selectedProfileId == null,
            text = { Text(stringResource(R.string.profile_filter_all_accounts)) },
            onClick = {
                onProfileSelected(null)
                onDismiss()
            },
            shapes = MenuDefaults.itemShape(0, profiles.size + 1),
            colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
            leadingIcon = {
                Icon(imageVector = Icons.Outlined.AccountBalance, contentDescription = null)
            },
            selectedLeadingIcon = { Icon(imageVector = Icons.Filled.Check, contentDescription = null) }
        )
        profiles.forEachIndexed { index, profile ->
            SelectableDropdownMenuItem(
                selected = selectedProfileId == profile.id,
                text = { Text(profile.displayName()) },
                onClick = {
                    onProfileSelected(profile.id)
                    onDismiss()
                },
                shapes = MenuDefaults.itemShape(index + 1, profiles.size + 1),
                colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                leadingIcon = {
                    Icon(imageVector = profileIcon(profile), contentDescription = null)
                },
                selectedLeadingIcon = { Icon(imageVector = Icons.Filled.Check, contentDescription = null) }
            )
        }
    }
}

/** This profile's name as the user should see it. Edit fields keep [ProfileEntity.name]. */
@Composable
fun ProfileEntity.displayName(): String = seededNameRes()?.let { stringResource(it) } ?: name

fun profileIcon(profile: ProfileEntity): ImageVector = when (profile.id) {
    ProfileEntity.PERSONAL_ID -> Icons.Outlined.Person
    ProfileEntity.BUSINESS_ID -> Icons.Outlined.Work
    else -> Icons.Outlined.AccountBalance
}

fun profileFilterIcon(profiles: List<ProfileEntity>, selectedProfileId: Long?): ImageVector {
    if (selectedProfileId == null) return Icons.Outlined.AccountBalance
    val profile = profiles.find { it.id == selectedProfileId }
    return if (profile != null) profileIcon(profile) else Icons.Outlined.AccountBalance
}
