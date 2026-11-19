package com.pennywiseai.tracker.presentation.add

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.manager.ChatDraftInbox
import com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.ModelRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.usecase.AddTransactionUseCase
import com.pennywiseai.tracker.navigation.BottomNavItem
import com.pennywiseai.tracker.widget.WidgetRefresher
import com.pennywiseai.tracker.worker.MoneyAlertWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

/** What the quick add sheet holds; also what "More details" hands to the full form. */
data class QuickAddDraft(
    val amount: String,
    val merchant: String,
    val category: String?,
    val type: TransactionType,
    val currency: String,
    val account: AccountBalanceEntity?,
    /** Null is now. */
    val day: LocalDate?
) {
    val dateTime: LocalDateTime? get() = day?.atTime(LocalTime.now())
}

/**
 * The quick add sheet's data and its one write, shared by the in-app sheet and
 * [QuickAddActivity] (widgets, the evening recap). Stateless: each sheet loads its own
 * [Setup] and owns its [SaveGate], so a new sheet always starts fresh.
 */
@HiltViewModel
class QuickAddViewModel @Inject constructor(
    @ApplicationContext private val app: Context,
    private val addTransaction: AddTransactionUseCase,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val modelRepository: ModelRepository,
    private val chatDrafts: ChatDraftInbox
) : ViewModel() {

    class Setup(
        val baseCurrency: String,
        val expenseCategories: List<CategoryEntity>,
        val incomeCategories: List<CategoryEntity>,
        /** Category names by use, most first: the picker's "Most used". */
        val mostUsed: List<String>,
        val balances: List<AccountBalanceEntity>,
        val profileId: Long?,
        /** False when the user hid the Chat tab: a question then has nowhere to go. */
        val chatOpen: Boolean,
        /** The chat model is downloaded: Chat can read what this can't, not just keep it. */
        val modelReady: Boolean,
    )

    suspend fun loadSetup(): Setup {
        val usage = runCatching { transactionRepository.getCategoriesByUsage() }.getOrDefault(emptyList())
        val others = BuiltinCategoryNames.current("Others")
        return Setup(
            baseCurrency = userPreferencesRepository.baseCurrency.first(),
            expenseCategories = QuickAddActivity.rankCategories(categoryRepository.getVisibleExpenseCategories().first(), usage, others),
            incomeCategories = QuickAddActivity.rankCategories(categoryRepository.getVisibleIncomeCategories().first(), usage, others),
            mostUsed = usage,
            balances = accountBalanceRepository.getAllLatestBalances().first(),
            profileId = userPreferencesRepository.selectedProfileId.first(),
            chatOpen = BottomNavItem.Chat.route !in userPreferencesRepository.userPreferences.first().hiddenNavTabs,
            modelReady = withContext(Dispatchers.IO) { modelRepository.isModelDownloaded() }
        )
    }

    /** The row's id, or -1 when the database refused it. The merchant defaults to the category. */
    suspend fun insert(draft: QuickAddDraft, amount: BigDecimal, category: String): Long = addTransaction.execute(
        amount = amount,
        merchant = draft.merchant.trim().ifEmpty { category },
        category = category,
        type = draft.type,
        date = draft.dateTime ?: LocalDateTime.now(),
        bankName = draft.account?.bankName,
        accountLast4 = draft.account?.accountLast4,
        currency = draft.currency
    )

    /** After a confirmed save, apart from it: a refresh that fails mustn't make a saved row look failed. */
    fun afterSave() {
        runCatching {
            WidgetRefresher.refreshTransactionWidgets(app)
            MoneyAlertWorker.enqueue(app)
        }.onFailure { Log.w(TAG, "Widget refresh failed", it) }
    }

    /** A question said to the in-app mic: into Chat's box (not sent); the host shows Chat. */
    fun askInChat(text: String) = chatDrafts.put(text)

    private companion object {
        const val TAG = "QuickAddViewModel"
    }
}
