package com.pennywiseai.tracker.presentation.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Backs the full-page [BalanceHistoryScreen]. Reads the account (bankName +
 * accountLast4) from the navigation route and exposes its balance snapshots,
 * mirroring the load/update/delete logic that used to live in
 * [ManageAccountsViewModel] when balance history was a dialog.
 */
@HiltViewModel
class BalanceHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountBalanceRepository: AccountBalanceRepository
) : ViewModel() {

    val bankName: String = savedStateHandle.get<String>("bankName") ?: ""
    val accountLast4: String = savedStateHandle.get<String>("accountLast4") ?: ""

    private val _history = MutableStateFlow<List<AccountBalanceEntity>>(emptyList())
    val history: StateFlow<List<AccountBalanceEntity>> = _history.asStateFlow()

    init {
        reload()
    }

    private fun reload() {
        viewModelScope.launch {
            _history.value = accountBalanceRepository.getBalanceHistoryForAccount(bankName, accountLast4)
        }
    }

    fun updateBalance(id: Long, newBalance: BigDecimal) {
        viewModelScope.launch {
            accountBalanceRepository.updateBalanceById(id, newBalance)
            reload()
        }
    }

    fun deleteBalance(id: Long) {
        viewModelScope.launch {
            // Never delete the only record — the account's latest balance must survive.
            if (accountBalanceRepository.getBalanceCountForAccount(bankName, accountLast4) > 1) {
                accountBalanceRepository.deleteBalanceById(id)
                reload()
            }
        }
    }
}
