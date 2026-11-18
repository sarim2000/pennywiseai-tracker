package com.pennywiseai.tracker.presentation.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.domain.spending.NetWorthLoader
import com.pennywiseai.tracker.domain.spending.NetWorthSnapshot
import com.pennywiseai.tracker.domain.spending.NetWorthTotal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** One currency's page: amounts in different currencies never share a figure. */
data class NetWorthUiState(
    /** Currencies with an account, the one with most accounts first. */
    val currencies: List<String>,
    val currency: String?,
    val total: NetWorthTotal?,
    /** The selected currency's accounts. */
    val accounts: List<AccountBalanceEntity>,
) {
    val isEmpty: Boolean get() = currencies.isEmpty()
}

internal fun netWorthUiState(snapshot: NetWorthSnapshot, chosen: String?): NetWorthUiState {
    val currencies = snapshot.totals.map { it.currency }
    val currency = chosen?.takeIf { it in currencies } ?: currencies.firstOrNull()
    return NetWorthUiState(
        currencies = currencies,
        currency = currency,
        total = snapshot.totals.firstOrNull { it.currency == currency },
        accounts = snapshot.accounts.filter { it.currency == currency },
    )
}

@HiltViewModel
class NetWorthViewModel @Inject constructor(
    loader: NetWorthLoader,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Null while loading. */
    val state: StateFlow<NetWorthUiState?> = combine(
        loader.observe(),
        // Starts on the currency it was opened with (Home's), else the one with most accounts.
        savedStateHandle.getStateFlow(KEY_CURRENCY, savedStateHandle.get<String>("currency")),
        ::netWorthUiState,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectCurrency(currency: String) {
        savedStateHandle[KEY_CURRENCY] = currency
    }

    private companion object {
        const val KEY_CURRENCY = "netWorthCurrency"
    }
}
