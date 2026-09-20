package com.pennywiseai.tracker.presentation.paywall

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing

/**
 * Paste-a-key dialog. Copy is intentionally silent about where keys are
 * sold (Play anti-steering); the website and the key email do that job.
 *
 * When the key is already active elsewhere and this build has a move
 * endpoint, the dialog asks for the purchase email as ownership proof and
 * offers "Move to this device".
 */
@Composable
internal fun LicenseKeyDialog(
    isActivating: Boolean,
    error: String?,
    canMove: Boolean,
    onActivate: (String) -> Unit,
    onMoveHere: (key: String, email: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var key by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    val canSubmit = key.isNotBlank() && !isActivating
    val canMoveNow = canSubmit && email.contains('@')

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Activate license key") },
        text = {
            Column {
                Text(
                    text = "Paste the key from your purchase email. Pro activates on this device right away.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.md))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isActivating,
                    // Dodo keys are lowercase UUIDs; don't shout-case the keyboard.
                    placeholder = { Text("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx") },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        imeAction = if (canMove) ImeAction.Next else ImeAction.Done,
                    ),
                )
                if (canMove) {
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isActivating,
                        label = { Text("Purchase email") },
                        supportingText = { Text("Confirms you own the key before it leaves the other device.") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done,
                        ),
                    )
                }
            }
        },
        confirmButton = {
            if (isActivating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimensions.Component.progressIndicatorSize),
                    strokeWidth = Spacing.xxs,
                )
            } else if (canMove) {
                TextButton(onClick = { onMoveHere(key, email) }, enabled = canMoveNow) { Text("Move to this device") }
            } else {
                TextButton(onClick = { onActivate(key) }, enabled = canSubmit) { Text("Activate") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isActivating) { Text("Cancel") }
        },
    )
}
