package com.pennywiseai.tracker.presentation.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.RecurringFrequency
import com.pennywiseai.tracker.data.database.entity.RecurringTransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.RecurringTransactionRepository
import com.pennywiseai.tracker.domain.usecase.GetCategoriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class RecurringTransactionsViewModel @Inject constructor(
    private val repository: RecurringTransactionRepository,
    getCategoriesUseCase: GetCategoriesUseCase,
    userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val templates: StateFlow<List<RecurringTransactionEntity>> =
        repository.getAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> =
        getCategoriesUseCase.execute()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val baseCurrency: StateFlow<String> =
        userPreferencesRepository.baseCurrency
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "INR")

    /**
     * Insert a new template ([id] == 0) or update an existing one. The next due
     * date is resolved from the chosen cadence + day-of-month / day-of-week so
     * the first materialisation lands on the user's intended day.
     */
    fun save(form: RecurringFormState) {
        viewModelScope.launch {
            val amount = form.amount.toBigDecimalOrNull() ?: return@launch
            val nextDue = resolveNextDueDate(form)
            if (form.id == 0L) {
                repository.insert(
                    RecurringTransactionEntity(
                        merchantName = form.merchantName.trim(),
                        amount = amount,
                        currency = form.currency,
                        category = form.category,
                        transactionType = form.transactionType,
                        frequency = form.frequency,
                        dayOfMonth = form.dayOfMonth,
                        dayOfWeek = form.dayOfWeek,
                        nextDueDate = nextDue,
                        isActive = form.isActive,
                        note = form.note.trim().ifBlank { null }
                    )
                )
            } else {
                val existing = repository.getById(form.id) ?: return@launch
                repository.update(
                    existing.copy(
                        merchantName = form.merchantName.trim(),
                        amount = amount,
                        currency = form.currency,
                        category = form.category,
                        transactionType = form.transactionType,
                        frequency = form.frequency,
                        dayOfMonth = form.dayOfMonth,
                        dayOfWeek = form.dayOfWeek,
                        nextDueDate = nextDue,
                        isActive = form.isActive,
                        note = form.note.trim().ifBlank { null },
                        updatedAt = LocalDateTime.now()
                    )
                )
            }
        }
    }

    fun setActive(template: RecurringTransactionEntity, active: Boolean) {
        viewModelScope.launch {
            repository.update(template.copy(isActive = active))
        }
    }

    fun delete(template: RecurringTransactionEntity) {
        viewModelScope.launch { repository.delete(template) }
    }

    /**
     * Pick the first upcoming date matching the cadence + selected day. Today
     * counts as valid so a template created on its due day still fires today.
     */
    private fun resolveNextDueDate(form: RecurringFormState): LocalDate {
        val today = LocalDate.now()
        return when (form.frequency) {
            RecurringFrequency.DAILY -> today
            RecurringFrequency.WEEKLY -> {
                val target = form.dayOfWeek ?: today.dayOfWeek.value
                var d = today
                while (d.dayOfWeek.value != target) d = d.plusDays(1)
                d
            }
            RecurringFrequency.MONTHLY, RecurringFrequency.YEARLY -> {
                val dom = (form.dayOfMonth ?: today.dayOfMonth)
                    .coerceIn(1, today.lengthOfMonth())
                val candidate = today.withDayOfMonth(dom)
                if (candidate.isBefore(today)) form.frequency.advance(candidate) else candidate
            }
        }
    }
}

/**
 * Editable form state for the add/edit sheet. Kept in the presentation layer so
 * the ViewModel only deals in validated primitives.
 */
data class RecurringFormState(
    val id: Long = 0L,
    val merchantName: String = "",
    val amount: String = "",
    val currency: String = "INR",
    val category: String = "Others",
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
    val dayOfMonth: Int? = null,
    val dayOfWeek: Int? = null,
    val isActive: Boolean = true,
    val note: String = ""
) {
    val isValid: Boolean
        get() = merchantName.isNotBlank() &&
            (amount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true) &&
            category.isNotBlank()

    companion object {
        fun from(template: RecurringTransactionEntity): RecurringFormState = RecurringFormState(
            id = template.id,
            merchantName = template.merchantName,
            amount = template.amount.stripTrailingZeros().toPlainString(),
            currency = template.currency,
            category = template.category,
            transactionType = template.transactionType,
            frequency = template.frequency,
            dayOfMonth = template.dayOfMonth,
            dayOfWeek = template.dayOfWeek,
            isActive = template.isActive,
            note = template.note.orEmpty()
        )
    }
}
