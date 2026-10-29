package com.pennywiseai.tracker.ui.screens.onboarding

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
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.nameForBuiltin
import com.pennywiseai.tracker.data.manager.SmsScanManager
import com.pennywiseai.tracker.data.preferences.UserPreferences
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.ui.components.AvatarHelper
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.AppLockRepository
import com.pennywiseai.tracker.data.repository.BudgetGroupRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.security.BiometricAuthManager
import com.pennywiseai.tracker.domain.usecase.AddSubscriptionUseCase
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.CurrencyUtils
import com.pennywiseai.tracker.worker.OptimizedSmsReaderWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.LocalDateTime
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
    SMS_SCAN,
    ACCOUNT_SETUP,
    QUICK_SETUP
}

/**
 * A subscription offered on the quick-setup step. [existingId] is set for one already
 * tracked (e.g. from a mandate SMS); [fromScan] is false for one the user typed in.
 */
data class SubscriptionChoice(
    val name: String,
    val amount: BigDecimal,
    val currency: String,
    val category: String,
    val nextPaymentDate: LocalDate,
    val selected: Boolean,
    val existingId: Long? = null,
    val fromScan: Boolean = true
)

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
    val isCompleting: Boolean = false,
    // Quick setup
    val quickSetupLoaded: Boolean = false,
    val paydayDay: Int = 1,
    val paydayDetected: Boolean = false,
    val subscriptions: List<SubscriptionChoice> = emptyList(),
    val usesCash: Boolean = false,
    val cashBalance: String = "",
    val monthlyBudget: String = "",
    val appLockEnabled: Boolean = false,
    val appLockAvailable: Boolean = false
) {
    /**
     * The base currency onboarding saves — also what quick-setup amounts are typed in.
     * An explicit pick wins; otherwise the pre-selection is a SIM/locale guess and the
     * main account's currency is real data. SMS-tracked accounts fall back to the bank
     * parser's currency (their stored value may be the INR default even for a non-INR bank).
     */
    val baseCurrency: String
        get() = if (currencyPicked) selectedCurrency else accounts
            .firstOrNull { "${it.bankName}_${it.accountLast4}" == selectedAccountKey }
            ?.let {
                CurrencyFormatter.resolveAccountCurrency(
                    sourceType = it.sourceType,
                    storedCurrency = it.currency,
                    bankName = it.bankName
                )
            } ?: selectedCurrency
}

@HiltViewModel
class OnBoardingViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val smsScanManager: SmsScanManager,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val transactionRepository: TransactionRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val addSubscriptionUseCase: AddSubscriptionUseCase,
    private val categoryRepository: CategoryRepository,
    private val budgetGroupRepository: BudgetGroupRepository,
    private val appLockRepository: AppLockRepository,
    private val biometricAuthManager: BiometricAuthManager,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val currencies = CurrencyUtils.sortCurrencies(CurrencyUtils.getAllSupportedCurrencies())

    private val _uiState = MutableStateFlow(OnBoardingUiState(selectedCurrency = detectCurrency()))
    val uiState: StateFlow<OnBoardingUiState> = _uiState.asStateFlow()

    val avatarDrawables = AvatarHelper.avatarDrawables

    val backgroundColors = listOf(
        0xFFDC8A78.toInt(), // Rosewater
        0xFFDD7878.toInt(), // Flamingo
        0xFFEA76CB.toInt(), // Pink
        0xFF8839EF.toInt(), // Mauve
        0xFFD20F39.toInt(), // Red
        0xFFFE640B.toInt(), // Peach
        0xFFDF8E1D.toInt(), // Yellow
        0xFF40A02B.toInt(), // Green
        0xFF179299.toInt(), // Teal
        0xFF04A5E5.toInt(), // Sky
        0xFF209FB5.toInt(), // Sapphire
        0xFF1E66F5.toInt(), // Blue
        0xFF7287FD.toInt(), // Lavender
        0xFF6C6F85.toInt(), // Subtext0
        0xFF8C8FA1.toInt(), // Overlay1
        0xFFACB0BE.toInt()  // Overlay2
    )

    fun updateUserName(name: String) {
        _uiState.update { it.copy(userName = name) }
    }

    fun selectAvatar(index: Int) {
        _uiState.update { it.copy(selectedAvatarIndex = index, profileImageUri = null) }
    }

    fun selectProfileImage(uri: Uri) {
        viewModelScope.launch {
            val savedUri = saveImageToInternalStorage(uri)
            _uiState.update { it.copy(profileImageUri = savedUri ?: uri, selectedAvatarIndex = -1) }
        }
    }

    private suspend fun saveImageToInternalStorage(sourceUri: Uri): Uri? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return@withContext null
            val file = File(context.filesDir, "profile_image.jpg")
            file.outputStream().use { output -> inputStream.use { input -> input.copyTo(output) } }
            file.toUri()
        } catch (_: Exception) {
            null
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
                    OnBoardingStep.SMS_SCAN
                } else {
                    // Skip scan step if permissions not granted
                    OnBoardingStep.ACCOUNT_SETUP
                }
            }
            OnBoardingStep.SMS_SCAN -> OnBoardingStep.ACCOUNT_SETUP
            OnBoardingStep.ACCOUNT_SETUP -> OnBoardingStep.QUICK_SETUP
            OnBoardingStep.QUICK_SETUP -> OnBoardingStep.QUICK_SETUP // Already last
        }
        if (nextStep == OnBoardingStep.QUICK_SETUP && !_uiState.value.quickSetupLoaded) loadQuickSetup()
        _uiState.update { it.copy(currentStep = nextStep) }
    }

    fun goToPreviousStep() {
        val currentStep = _uiState.value.currentStep
        val previousStep = when (currentStep) {
            OnBoardingStep.WELCOME -> OnBoardingStep.WELCOME
            OnBoardingStep.PROFILE -> OnBoardingStep.WELCOME
            OnBoardingStep.PERMISSIONS -> OnBoardingStep.PROFILE
            OnBoardingStep.SMS_SCAN -> OnBoardingStep.PERMISSIONS
            OnBoardingStep.ACCOUNT_SETUP -> {
                if (_uiState.value.smsPermissionGranted) {
                    OnBoardingStep.SMS_SCAN
                } else {
                    OnBoardingStep.PERMISSIONS
                }
            }
            OnBoardingStep.QUICK_SETUP -> OnBoardingStep.ACCOUNT_SETUP
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

    /** Pre-fills the quick-setup step from what the scan found. */
    private fun loadQuickSetup() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val transactions = transactionRepository.getTransactionsBetweenDatesList(
                today.minusDays(QUICK_SETUP_HISTORY_DAYS).atStartOfDay(),
                LocalDateTime.now()
            )
            val salary = categoryRepository.getAllCategories().first().nameForBuiltin("Salary")
            val payday = detectPayday(transactions, salary)
            val tracked = subscriptionRepository.getActiveSubscriptions().first()
            val trackedChoices = tracked.map {
                SubscriptionChoice(
                    name = it.merchantName,
                    amount = it.amount,
                    currency = it.currency,
                    category = it.category.orEmpty(),
                    nextPaymentDate = it.nextPaymentDate ?: today,
                    selected = true,
                    existingId = it.id
                )
            }
            val detected = detectRecurring(
                transactions,
                knownNames = tracked.map { it.merchantName.trim().lowercase() }.toSet(),
                today = today
            ).map {
                SubscriptionChoice(it.name, it.amount, it.currency, it.category, it.nextPaymentDate, selected = false)
            }
            val cycleDay = userPreferencesRepository.getBudgetCycleStartDay()
            _uiState.update {
                it.copy(
                    quickSetupLoaded = true,
                    paydayDay = payday ?: cycleDay,
                    paydayDetected = payday != null,
                    subscriptions = trackedChoices + detected,
                    appLockAvailable = biometricAuthManager.isBiometricAvailable()
                )
            }
        }
    }

    fun setPayday(day: Int) {
        _uiState.update { it.copy(paydayDay = day, paydayDetected = false) }
    }

    fun toggleSubscription(index: Int) {
        _uiState.update { state ->
            state.copy(subscriptions = state.subscriptions.mapIndexed { i, c ->
                if (i == index) c.copy(selected = !c.selected) else c
            })
        }
    }

    /** A subscription the user typed in: billed monthly on [day] of the month, in [OnBoardingUiState.baseCurrency]. */
    fun addSubscription(name: String, category: String, amount: BigDecimal, day: Int) {
        val today = LocalDate.now()
        val thisMonth = today.withDayOfMonth(day.coerceIn(1, today.lengthOfMonth()))
        val next = if (thisMonth.isBefore(today)) {
            today.plusMonths(1).let { it.withDayOfMonth(day.coerceIn(1, it.lengthOfMonth())) }
        } else thisMonth
        _uiState.update { state ->
            state.copy(subscriptions = state.subscriptions.filterNot { it.name == name && it.existingId == null } +
                SubscriptionChoice(name, amount, state.baseCurrency, category, next, selected = true, fromScan = false))
        }
    }

    fun setUsesCash(uses: Boolean) {
        _uiState.update { it.copy(usesCash = uses) }
    }

    fun updateCashBalance(value: String) {
        _uiState.update { it.copy(cashBalance = value.filterAmount()) }
    }

    fun updateMonthlyBudget(value: String) {
        _uiState.update { it.copy(monthlyBudget = value.filterAmount()) }
    }

    fun setAppLock(enabled: Boolean) {
        _uiState.update { it.copy(appLockEnabled = enabled) }
    }

    /** Saves the quick-setup answers; anything left blank or unticked is skipped. */
    private suspend fun applyQuickSetup(state: OnBoardingUiState) {
        // Before the budget: a new monthly budget starts on the cycle day.
        userPreferencesRepository.updateBudgetCycleStartDay(state.paydayDay)
        val currency = state.baseCurrency
        val categories = categoryRepository.getAllCategories().first()
        state.subscriptions.forEach { choice ->
            when {
                choice.existingId != null && !choice.selected -> subscriptionRepository.hideSubscription(choice.existingId)
                choice.existingId == null && choice.selected -> addSubscriptionUseCase.execute(
                    merchantName = choice.name,
                    amount = choice.amount,
                    nextPaymentDate = choice.nextPaymentDate,
                    billingCycle = "Monthly",
                    category = categories.nameForBuiltin(choice.category),
                    currency = choice.currency
                )
            }
        }
        if (state.usesCash) {
            val cashName = context.getString(R.string.onboarding_cash_account_name)
            if (accountBalanceRepository.getLatestBalance(cashName, CASH) == null) {
                val opening = state.cashBalance.toBigDecimalOrNull() ?: BigDecimal.ZERO
                accountBalanceRepository.seedManualAccount(
                    AccountBalanceEntity(
                        bankName = cashName,
                        accountLast4 = CASH,
                        balance = opening,
                        timestamp = LocalDateTime.now(),
                        accountType = CASH,
                        currency = currency,
                        sourceType = "MANUAL"
                    ),
                    opening
                )
            }
        }
        state.monthlyBudget.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }?.let { limit ->
            budgetGroupRepository.createGroup(
                name = context.getString(R.string.onboarding_budget_name),
                groupType = BudgetGroupType.LIMIT,
                color = BUDGET_COLOR,
                currency = currency,
                limitAmount = limit
            )
        }
        if (state.appLockEnabled && state.appLockAvailable) appLockRepository.setAppLockEnabled(true)
    }

    /** [withQuickSetup] false (Skip) finishes without saving the quick-setup answers. */
    fun completeOnboarding(onComplete: () -> Unit, withQuickSetup: Boolean = true) {
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
                // Won't override a currency the user explicitly picked in Settings.
                userPreferencesRepository.applyMainAccountCurrency(state.baseCurrency)
            }
            if (withQuickSetup && state.currentStep == OnBoardingStep.QUICK_SETUP) applyQuickSetup(state)
            userPreferencesRepository.updateHasCompletedOnboarding(true)
            _uiState.update { it.copy(isCompleting = false) }
            onComplete()
        }
    }

}

/**
 * Digits and one decimal point — what an amount field accepts. A comma is the decimal
 * point where the locale writes 7,99 (as Quick Add reads it), else a grouping separator.
 */
internal fun String.filterAmount(
    decimalComma: Boolean = DecimalFormatSymbols.getInstance().decimalSeparator == ','
): String {
    val digits = (if (decimalComma) replace(',', '.') else this).filter { it.isDigit() || it == '.' }
    val dot = digits.indexOf('.')
    return if (dot < 0) digits else digits.substring(0, dot + 1) + digits.substring(dot + 1).replace(".", "")
}

private const val QUICK_SETUP_HISTORY_DAYS = 120L
/** Last-4 placeholder and account type of the cash account, as Manage Accounts creates it. */
private const val CASH = "CASH"
private const val BUDGET_COLOR = "#1565C0"

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
