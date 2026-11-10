package com.pennywiseai.tracker.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.presentation.exchangerates.ExchangeRatesScreen
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter

/** [ExchangeRatesScreen] (in :feature:exchangerates) with the app's top bar and formatters. */
@Composable
fun ExchangeRatesEntry(onNavigateBack: () -> Unit) {
    ExchangeRatesScreen(
        topBar = { title, scrollBehaviorSmall, scrollBehaviorLarge, hazeState, actions ->
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = title,
                hasBackButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accounts_back))
                    }
                },
                actionContent = actions,
                hazeState = hazeState,
            )
        },
        formatDateTime = { it.format(AppDates.dateTimeFormatter()) },
        currencySymbol = CurrencyFormatter::getCurrencySymbol,
        cancelLabel = stringResource(R.string.accounts_action_cancel),
    )
}
