package com.pennywiseai.tracker.presentation.onboarding

import android.content.Context
import android.net.Uri
import android.telephony.TelephonyManager
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.preferences.UserPreferences
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.ui.components.AvatarHelper
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.CurrencyUtils
import com.pennywiseai.tracker.utils.ProfilePhotos
import com.pennywiseai.tracker.worker.OptimizedSmsReaderWorker
import com.pennywiseai.tracker.worker.SmsScanManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

/**
 * The currency of the first [countries] entry (ISO 3166 region, best guess first) that
 * resolves to a [supported] currency, else the app-wide default base currency.
 */
internal fun detectCurrency(countries: List<String?>, supported: Collection<String>): String =
    countries.firstNotNullOfOrNull { country ->
        // Blank/unknown regions throw; regions without a currency (e.g. AQ) return null.
        runCatching { Currency.getInstance(Locale("", country.orEmpty().uppercase()))?.currencyCode }
            .getOrNull()?.takeIf { it in supported }
    } ?: UserPreferences().baseCurrency

enum class OnBoardingStep {
    WELCOME,
    PROFILE,
    PERMISSIONS,
    BACKGROUND_ACCESS,
    SMS_SCAN,
    ACCOUNT_SETUP
}

data class OnBoardingUiState(
    val currentStep: OnBoardingStep = OnBoardingStep.WELCOME,
    val userName: String = "",
    val selectedAvatarIndex: Int = 0,
    val profileImageUri: Uri? = null,
    val selectedBackgroundColor: Int = 0,
    val selectedCurrency: String = "INR",
    /** True once the user changes the pre-selected currency; a guess left as is stays overridable. */
    val currencyPicked: Boolean = false,
    val smsPermissionGranted: Boolean = false,
    val smsPermissionSkipped: Boolean = false,
    val isScanning: Boolean = false,
    val scanTotal: Int = 0,
    val scanProcessed: Int = 0,
    val scanParsed: Int = 0,
    val scanSaved: Int = 0,
    val scanTimeElapsed: Long = 0L,
    val scanEstimatedRemaining: Long = 0L,
    val scanCompleted: Boolean = false,
    val accounts: List<AccountBalanceEntity> = emptyList(),
    val selectedAccountKey: String? = null,
    val isCompleting: Boolean = false
) {
    /** Back (the on-screen arrow and system Back) returns a step; on the first step it leaves the app. */
    val canGoBack: Boolean get() = currentStep != OnBoardingStep.WELCOME && !isScanning
}

@HiltViewModel
class OnBoardingViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val smsScanManager: SmsScanManager,
    private val accountBalanceRepository: AccountBalanceRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val currencies = CurrencyUtils.sortCurrencies(CurrencyUtils.getAllSupportedCurrencies())

    private val _uiState = MutableStateFlow(OnBoardingUiState(selectedCurrency = detectCurrency()))
    val uiState: StateFlow<OnBoardingUiState> = _uiState.asStateFlow()

    val avatarDrawables = AvatarHelper.avatarDrawables

    val backgroundColors = AvatarHelper.backgroundColors

    fun updateUserName(name: String) {
        _uiState.update { it.copy(userName = name) }
    }

    fun selectAvatar(index: Int) {
        _uiState.update { it.copy(selectedAvatarIndex = index, profileImageUri = null) }
    }

    fun selectProfileImage(uri: Uri) {
        viewModelScope.launch {
            // A photo that can't be used keeps the previous pick; the import says why.
            val savedUri = ProfilePhotos.import(context, uri) ?: return@launch
            _uiState.update { it.copy(profileImageUri = savedUri.toUri(), selectedAvatarIndex = -1) }
        }
    }

    fun selectBackgroundColor(colorIndex: Int) {
        _uiState.update { it.copy(selectedBackgroundColor = colorIndex) }
    }

    fun selectCurrency(currency: String) {
        _uiState.update { it.copy(selectedCurrency = currency, currencyPicked = true) }
    }

    /** Pre-selects the SIM country's currency, then the network country's, then the device locale's. */
    private fun detectCurrency(): String {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        return detectCurrency(
            countries = listOf(telephony?.simCountryIso, telephony?.networkCountryIso, Locale.getDefault().country),
            supported = currencies
        )
    }

    fun onSmsPermissionResult(granted: Boolean) {
        _uiState.update {
            it.copy(
                smsPermissionGranted = granted,
                smsPermissionSkipped = !granted
            )
        }
        // Granted from the dialog or app settings: move on rather than wait for Continue.
        if (granted && _uiState.value.currentStep == OnBoardingStep.PERMISSIONS) goToNextStep()
    }

    fun skipSmsPermission() {
        _uiState.update { it.copy(smsPermissionSkipped = true) }
    }

    fun navigateToStep(step: OnBoardingStep) {
        _uiState.update { it.copy(currentStep = step) }
    }

    fun goToNextStep() {
        val currentStep = _uiState.value.currentStep
        val nextStep = when (currentStep) {
            OnBoardingStep.WELCOME -> OnBoardingStep.PROFILE
            OnBoardingStep.PROFILE -> OnBoardingStep.PERMISSIONS
            OnBoardingStep.PERMISSIONS -> {
                if (_uiState.value.smsPermissionGranted) {
                    OnBoardingStep.BACKGROUND_ACCESS
                } else {
                    // Skip the SMS-only steps if permissions not granted
                    OnBoardingStep.ACCOUNT_SETUP
                }
            }
            OnBoardingStep.BACKGROUND_ACCESS -> OnBoardingStep.SMS_SCAN
            OnBoardingStep.SMS_SCAN -> OnBoardingStep.ACCOUNT_SETUP
            OnBoardingStep.ACCOUNT_SETUP -> OnBoardingStep.ACCOUNT_SETUP // Already last
        }
        _uiState.update { it.copy(currentStep = nextStep) }
    }

    fun goToPreviousStep() {
        val currentStep = _uiState.value.currentStep
        val previousStep = when (currentStep) {
            OnBoardingStep.WELCOME -> OnBoardingStep.WELCOME
            OnBoardingStep.PROFILE -> OnBoardingStep.WELCOME
            OnBoardingStep.PERMISSIONS -> OnBoardingStep.PROFILE
            OnBoardingStep.BACKGROUND_ACCESS -> OnBoardingStep.PERMISSIONS
            OnBoardingStep.SMS_SCAN -> OnBoardingStep.BACKGROUND_ACCESS
            OnBoardingStep.ACCOUNT_SETUP -> {
                if (_uiState.value.smsPermissionGranted) {
                    OnBoardingStep.SMS_SCAN
                } else {
                    OnBoardingStep.PERMISSIONS
                }
            }
        }
        _uiState.update { it.copy(currentStep = previousStep) }
    }

    fun startSmsScan() {
        smsScanManager.startSmsLoggingScan()
        _uiState.update { it.copy(isScanning = true, scanCompleted = false) }
        observeScanProgress()
    }

    private fun observeScanProgress() {
        val workManager = WorkManager.getInstance(context)
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkLiveData(OptimizedSmsReaderWorker.WORK_NAME)
                .asFlow()
                .collect { workInfos ->
                    val workInfo = workInfos.firstOrNull() ?: return@collect

                    val progress = workInfo.progress
                    val total = progress.getInt(OptimizedSmsReaderWorker.PROGRESS_TOTAL, 0)
                    val processed = progress.getInt(OptimizedSmsReaderWorker.PROGRESS_PROCESSED, 0)
                    val parsed = progress.getInt(OptimizedSmsReaderWorker.PROGRESS_PARSED, 0)
                    val saved = progress.getInt(OptimizedSmsReaderWorker.PROGRESS_SAVED, 0)
                    val elapsed = progress.getLong(OptimizedSmsReaderWorker.PROGRESS_TIME_ELAPSED, 0L)
                    val remaining = progress.getLong(OptimizedSmsReaderWorker.PROGRESS_ESTIMATED_TIME_REMAINING, 0L)

                    when (workInfo.state) {
                        WorkInfo.State.RUNNING -> {
                            _uiState.update {
                                it.copy(
                                    isScanning = true,
                                    scanTotal = total,
                                    scanProcessed = processed,
                                    scanParsed = parsed,
                                    scanSaved = saved,
                                    scanTimeElapsed = elapsed,
                                    scanEstimatedRemaining = remaining
                                )
                            }
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            // `progress` is already empty once the work has
                            // finished, so fall back to the last counts seen
                            // while it ran — never to zero.
                            _uiState.update { it.completedWith(workInfo.outputData) }
                            loadAccounts()
                        }
                        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                            _uiState.update {
                                it.copy(isScanning = false, scanCompleted = true)
                            }
                            loadAccounts()
                        }
                        else -> { /* ENQUEUED, BLOCKED */ }
                    }
                }
        }
    }

    fun loadAccounts() {
        viewModelScope.launch {
            val accounts = accountBalanceRepository.getAllLatestBalances().first()
                .filter { !it.isLiability && it.balance != BigDecimal.ZERO }
            _uiState.update { state ->
                state.copy(
                    accounts = accounts,
                    selectedAccountKey = if (accounts.size == 1) {
                        "${accounts[0].bankName}_${accounts[0].accountLast4}"
                    } else {
                        state.selectedAccountKey
                    }
                )
            }
        }
    }

    fun selectAccount(accountKey: String) {
        _uiState.update { it.copy(selectedAccountKey = accountKey) }
    }

    fun completeOnboarding(onComplete: () -> Unit) {
        _uiState.update { it.copy(isCompleting = true) }
        viewModelScope.launch {
            val state = _uiState.value
            userPreferencesRepository.updateUserName(state.userName.trim())
            if (state.profileImageUri != null) {
                userPreferencesRepository.updateProfileImageUri(state.profileImageUri.toString())
            } else if (state.selectedAvatarIndex in avatarDrawables.indices) {
                userPreferencesRepository.updateProfileImageUri("avatar://${state.selectedAvatarIndex}")
            }
            if (state.selectedBackgroundColor >= 0 && state.selectedBackgroundColor < backgroundColors.size) {
                userPreferencesRepository.updateProfileBackgroundColor(backgroundColors[state.selectedBackgroundColor])
            }
            if (state.selectedAccountKey != null) {
                userPreferencesRepository.updateMainAccountKey(state.selectedAccountKey)
            }
            if (state.currencyPicked) {
                // An explicit pick: the main account doesn't override it.
                userPreferencesRepository.updateBaseCurrency(state.selectedCurrency)
            } else {
                // The pre-selection is a SIM/locale guess; the main account's currency is
                // real data. SMS-tracked accounts fall back to the bank parser's currency
                // (their stored value may be the INR default even for a non-INR bank).
                val mainAccount = state.accounts.firstOrNull {
                    "${it.bankName}_${it.accountLast4}" == state.selectedAccountKey
                }
                val currency = mainAccount?.let {
                    CurrencyFormatter.resolveAccountCurrency(
                        sourceType = it.sourceType,
                        storedCurrency = it.currency,
                        bankName = it.bankName
                    )
                } ?: state.selectedCurrency
                // Won't override a currency the user explicitly picked in Settings.
                userPreferencesRepository.applyMainAccountCurrency(currency)
            }
            // A widget added before onboarding finished holds the ₹ default.
            com.pennywiseai.tracker.widget.WidgetRefresher.refreshTransactionWidgets(context)
            // Onboarding already scanned: Home's "tap here to scan" hint would teach a step done.
            if (state.scanCompleted) userPreferencesRepository.markScanTutorialShown()
            userPreferencesRepository.updateHasCompletedOnboarding(true, freshInstall = true)
            _uiState.update { it.copy(isCompleting = false) }
            onComplete()
        }
    }
}

/**
 * The state once the scan has finished. Reads the worker's output, falling back
 * to the last counts seen while it ran — `progress` is already empty by then, so
 * falling back to 0 would report "No transactions found" after a full import.
 */
internal fun OnBoardingUiState.completedWith(out: Data): OnBoardingUiState = copy(
    isScanning = false,
    scanCompleted = true,
    scanTotal = out.getInt(OptimizedSmsReaderWorker.PROGRESS_TOTAL, scanTotal),
    scanProcessed = out.getInt(OptimizedSmsReaderWorker.PROGRESS_PROCESSED, scanProcessed),
    scanParsed = out.getInt(OptimizedSmsReaderWorker.PROGRESS_PARSED, scanParsed),
    scanSaved = out.getInt(OptimizedSmsReaderWorker.PROGRESS_SAVED, scanSaved)
)
