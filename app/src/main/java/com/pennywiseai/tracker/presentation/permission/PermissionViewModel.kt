package com.pennywiseai.tracker.presentation.permission

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import com.pennywiseai.tracker.utils.canReadSms
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class PermissionViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionUiState())
    val uiState: StateFlow<PermissionUiState> = _uiState.asStateFlow()

    init {
        refreshPermissions()
    }

    private fun refreshPermissions() {
        val notificationAccess = NotificationManagerCompat
            .getEnabledListenerPackages(context)
            .contains(context.packageName)

        _uiState.update {
            it.copy(
                hasPermission = context.canReadSms(),
                hasNotificationAccess = notificationAccess
            )
        }
    }

    fun refreshNotificationAccess() {
        refreshPermissions()
    }
}

data class PermissionUiState(
    val hasPermission: Boolean = false,
    val hasNotificationAccess: Boolean = false
)
