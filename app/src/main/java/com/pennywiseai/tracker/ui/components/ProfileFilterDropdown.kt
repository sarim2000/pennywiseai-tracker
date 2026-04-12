package com.pennywiseai.tracker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.pennywiseai.tracker.data.database.entity.ProfileEntity

@Composable
fun ProfileFilterDropdown(
    expanded: Boolean,
    profiles: List<ProfileEntity>,
    selectedProfileId: Long?,
    onProfileSelected: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        // "All" option
        DropdownMenuItem(
            text = { Text("All Accounts") },
            onClick = {
                onProfileSelected(null)
                onDismiss()
            },
            leadingIcon = {
                Icon(imageVector = Icons.Outlined.AccountBalance, contentDescription = null)
            },
            trailingIcon = if (selectedProfileId == null) {
                { Icon(imageVector = Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            } else null
        )
        profiles.forEach { profile ->
            DropdownMenuItem(
                text = { Text(profile.name) },
                onClick = {
                    onProfileSelected(profile.id)
                    onDismiss()
                },
                leadingIcon = {
                    Icon(imageVector = profileIcon(profile), contentDescription = null)
                },
                trailingIcon = if (selectedProfileId == profile.id) {
                    { Icon(imageVector = Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                } else null
            )
        }
    }
}

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
