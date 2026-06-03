package com.pennywiseai.tracker.presentation.statement

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.statement.ImportStatementUseCase
import com.pennywiseai.tracker.data.statement.StatementImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

sealed class ImportStatementUiState {
    data object Idle : ImportStatementUiState()
    data object Loading : ImportStatementUiState()
    data class Success(val result: StatementImportResult.Success) : ImportStatementUiState()
    data class Error(val message: String) : ImportStatementUiState()
}

@HiltViewModel
class ImportStatementViewModel @Inject constructor(
    private val importStatementUseCase: ImportStatementUseCase,
    private val preferences: UserPreferencesRepository,
    entitlementGate: EntitlementGate,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImportStatementUiState>(ImportStatementUiState.Idle)
    val uiState: StateFlow<ImportStatementUiState> = _uiState.asStateFlow()

    /**
     * True when the user is allowed to import a statement right now — either
     * Pro (unlimited) or has not yet imported this calendar month. The UI
     * reads this to decide between opening the file picker and triggering
     * the paywall.
     *
     * Calendar-month boundary is computed in the system default zone, which
     * matches how a user thinks about "this month" without needing a
     * stored month-string that's fragile across timezone changes.
     */
    val canImportThisMonth: StateFlow<Boolean> = combine(
        entitlementGate.isProEntitled,
        preferences.lastStatementImportAt,
    ) { pro, lastAtMillis ->
        if (pro) return@combine true
        if (lastAtMillis == null) return@combine true
        val lastDate = Instant.ofEpochMilli(lastAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now(ZoneId.systemDefault())
        lastDate.year != today.year || lastDate.month != today.month
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true,
    )

    fun importStatement(uri: Uri) {
        _uiState.value = ImportStatementUiState.Loading
        viewModelScope.launch {
            when (val result = importStatementUseCase.import(uri)) {
                is StatementImportResult.Success -> {
                    // Record the import so the next attempt this month is
                    // gated; Pro users will still see canImportThisMonth=true
                    // because the combine() short-circuits on entitlement.
                    preferences.markStatementImported(System.currentTimeMillis())
                    _uiState.value = ImportStatementUiState.Success(result)
                }
                is StatementImportResult.Error -> {
                    _uiState.value = ImportStatementUiState.Error(result.message)
                }
            }
        }
    }

    fun resetState() {
        _uiState.value = ImportStatementUiState.Idle
    }
}
