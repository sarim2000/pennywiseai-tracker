package com.pennywiseai.tracker.presentation.statement

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.statement.ImportStatementUseCase
import com.pennywiseai.tracker.data.statement.StatementImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ImportStatementUiState {
    data object Idle : ImportStatementUiState()
    data object Loading : ImportStatementUiState()
    data class Success(val result: StatementImportResult.Success) : ImportStatementUiState()
    data class Error(val message: String) : ImportStatementUiState()
}

@HiltViewModel
class ImportStatementViewModel @Inject constructor(
    private val importStatementUseCase: ImportStatementUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImportStatementUiState>(ImportStatementUiState.Idle)
    val uiState: StateFlow<ImportStatementUiState> = _uiState.asStateFlow()

    fun importStatement(uri: Uri) {
        _uiState.value = ImportStatementUiState.Loading
        viewModelScope.launch {
            when (val result = importStatementUseCase.import(uri)) {
                is StatementImportResult.Success -> {
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
