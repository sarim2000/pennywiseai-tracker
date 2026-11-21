package com.pennywiseai.tracker.presentation.add

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.navigation.AddForm
import com.pennywiseai.tracker.ui.components.ConnectedChoiceRow
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing

/** The full Add form: Transaction or Subscription, one switch, one form, one Save. */
@Composable
fun AddScreen(
    viewModel: AddViewModel = hiltViewModel(),
    initialForm: AddForm = AddForm.TRANSACTION,
    onNavigateBack: () -> Unit = {}
) {
    var form by rememberSaveable { mutableStateOf(initialForm) }
    PennyWiseScaffoldForAdd(onNavigateBack) { modifier ->
        Column(modifier) {
            ConnectedChoiceRow(
                options = AddForm.entries,
                selected = form,
                onSelect = { form = it },
                label = {
                    stringResource(if (it == AddForm.TRANSACTION) R.string.add_tab_transaction else R.string.add_tab_subscription)
                },
                modifier = Modifier
                    .padding(horizontal = Dimensions.Padding.content)
                    .padding(top = Spacing.sm)
            )
            when (form) {
                AddForm.TRANSACTION -> TransactionTabContent(viewModel = viewModel, onSave = onNavigateBack)
                AddForm.SUBSCRIPTION -> SubscriptionTabContent(viewModel = viewModel, onSave = onNavigateBack)
            }
        }
    }
}

@Composable
private fun PennyWiseScaffoldForAdd(onNavigateBack: () -> Unit, content: @Composable (Modifier) -> Unit) {
    com.pennywiseai.tracker.ui.components.PennyWiseScaffold(
        title = stringResource(R.string.add_title),
        navigationIcon = {
            IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.add_back))
            }
        }
    ) { padding -> content(Modifier.fillMaxSize().padding(padding)) }
}
