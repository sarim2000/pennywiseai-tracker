package com.pennywiseai.tracker.presentation.split

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.LoanRepository
import com.pennywiseai.tracker.domain.loan.SplitError
import com.pennywiseai.tracker.domain.loan.SplitPerson
import com.pennywiseai.tracker.domain.loan.SplitRules
import com.pennywiseai.tracker.domain.loan.SplitShare
import com.pennywiseai.tracker.domain.loan.UpiRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

enum class SplitMode { EQUAL, CUSTOM }

/** One person in the split sheet. [amount] is the typed share (custom amounts only). */
data class FormPerson(val name: String, val amount: String = "")

/** The open split sheet. [yourPart] is typed in custom amounts only. */
data class SplitForm(
    val people: List<FormPerson> = emptyList(),
    val mode: SplitMode = SplitMode.EQUAL,
    val yourPart: String = "",
    val saving: Boolean = false,
    val failure: String? = null,
    /** Set when a save tried to change the share of someone whose loan is settled. */
    val settledPerson: String? = null,
)

/** The sheet's figures: each person's share, your part, what's unassigned, and why it can't save yet. */
data class SplitPreview(
    val shares: List<BigDecimal>,
    val yours: BigDecimal,
    val remainder: BigDecimal,
    val error: SplitError?,
)

fun SplitForm.preview(total: BigDecimal, currency: String): SplitPreview {
    val shares = when (mode) {
        SplitMode.EQUAL -> SplitRules.equalShares(total, people.size, currency)
        SplitMode.CUSTOM -> people.map { it.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO }
    }
    val named = people.zip(shares) { p, a -> SplitShare(p.name, a) }
    return when (mode) {
        SplitMode.EQUAL -> SplitPreview(shares, SplitRules.yourPart(total, shares), BigDecimal.ZERO, SplitRules.validate(total, named))
        SplitMode.CUSTOM -> {
            val yours = yourPart.toBigDecimalOrNull() ?: BigDecimal.ZERO
            SplitPreview(shares, yours, SplitRules.remainder(total, yours, shares), SplitRules.validateCustom(total, yours, named))
        }
    }
}

/**
 * Split a bill (#346) on the transaction screen: the "Split with" rows, the
 * split sheet and the UPI ID used to request each share.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SplitBillViewModel @Inject constructor(
    private val loanRepository: LoanRepository,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    private val transaction = MutableStateFlow<TransactionEntity?>(null)

    fun setTransaction(txn: TransactionEntity?) { transaction.value = txn }

    val split: StateFlow<List<SplitPerson>> = transaction.map { it?.id }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else loanRepository.observeSplit(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Names you've already lent to or borrowed from (open loans, like "Mark as loan"). */
    val suggestions: StateFlow<List<String>> = loanRepository.getRecentPersonNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val upiId: StateFlow<String?> = preferences.upiId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Your name for the UPI request, unless it's still the default. */
    val payeeName: StateFlow<String?> = preferences.userPreferences
        .map { prefs -> prefs.userName.trim().takeIf { it.isNotEmpty() && it != DEFAULT_USER_NAME } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _form = MutableStateFlow<SplitForm?>(null)
    val form: StateFlow<SplitForm?> = _form.asStateFlow()

    private val _changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits after the split is saved or removed, so the screen reloads the transaction. */
    val changed: SharedFlow<Unit> = _changed.asSharedFlow()

    /** Opens the sheet, filled in with the saved split when there is one. */
    fun openSheet() {
        val txn = transaction.value ?: return
        val saved = split.value.mapNotNull { p -> p.loan?.let { FormPerson(it.personName, p.share.amount.toPlainString()) to p.share.amount } }
        val equal = SplitRules.equalShares(txn.amount, saved.size, txn.currency)
        val isEqual = saved.map { it.second }.zip(equal).all { (a, b) -> a.compareTo(b) == 0 }
        _form.value = SplitForm(
            people = saved.map { it.first },
            mode = if (isEqual) SplitMode.EQUAL else SplitMode.CUSTOM,
            yourPart = SplitRules.yourPart(txn.amount, saved.map { it.second }).toPlainString(),
        )
    }

    fun closeSheet() { _form.value = null }

    /** Adds [raw] unless it's blank, already in the split, or the split is full. */
    fun addPerson(raw: String): Boolean {
        val name = raw.trim()
        val form = _form.value ?: return false
        if (name.isEmpty() || form.people.size >= SplitRules.MAX_PEOPLE ||
            form.people.any { it.name.equals(name, ignoreCase = true) }
        ) return false
        _form.value = form.copy(people = form.people + FormPerson(name), failure = null, settledPerson = null)
        return true
    }

    fun removePerson(index: Int) = _form.update { f -> f?.copy(people = f.people.filterIndexed { i, _ -> i != index }, failure = null, settledPerson = null) }

    /** Switching to custom amounts starts from the equal split, so only the changes need typing. */
    fun setMode(mode: SplitMode) {
        val txn = transaction.value ?: return
        _form.update { f ->
            if (f == null || f.mode == mode) return@update f
            if (mode == SplitMode.CUSTOM) {
                val p = f.preview(txn.amount, txn.currency)
                f.copy(
                    mode = mode,
                    people = f.people.zip(p.shares) { person, a -> person.copy(amount = a.toPlainString()) },
                    yourPart = p.yours.toPlainString(),
                )
            } else f.copy(mode = mode)
        }
    }

    fun setShare(index: Int, input: String) = _form.update { f ->
        f?.copy(people = f.people.mapIndexed { i, p -> if (i == index) p.copy(amount = input) else p }, failure = null, settledPerson = null)
    }

    fun setYourPart(input: String) = _form.update { it?.copy(yourPart = input, failure = null, settledPerson = null) }

    fun save() {
        val txn = transaction.value ?: return
        val form = _form.value ?: return
        if (form.saving) return
        val preview = form.preview(txn.amount, txn.currency)
        if (preview.error != null) return
        _form.value = form.copy(saving = true, failure = null, settledPerson = null)
        viewModelScope.launch {
            runCatching {
                loanRepository.saveSplit(txn.id, form.people.zip(preview.shares) { p, a -> SplitShare(p.name, a) })
            }.onSuccess {
                _form.value = null
                _changed.tryEmit(Unit)
            }.onFailure { e ->
                _form.update {
                    if (e is com.pennywiseai.tracker.domain.loan.SettledShareException) it?.copy(saving = false, settledPerson = e.personName)
                    else it?.copy(saving = false, failure = e.message.orEmpty())
                }
            }
        }
    }

    /** Undoes the whole split from the open sheet; on failure the sheet stays open with the error. */
    fun removeSplit() {
        val txn = transaction.value ?: return
        val form = _form.value ?: return
        if (form.saving) return
        _form.value = form.copy(saving = true, failure = null, settledPerson = null)
        viewModelScope.launch {
            runCatching { loanRepository.saveSplit(txn.id, emptyList()) }
                .onSuccess {
                    _form.value = null
                    _changed.tryEmit(Unit)
                }
                .onFailure { e -> _form.update { it?.copy(saving = false, failure = e.message.orEmpty()) } }
        }
    }

    /** Saves [id] as your UPI ID; false (and nothing saved) when it isn't name@handle. */
    fun saveUpiId(id: String): Boolean {
        if (!UpiRequest.isValidUpiId(id)) return false
        viewModelScope.launch { preferences.setUpiId(id) }
        return true
    }

    private companion object {
        // UserPreferences.userName's placeholder when no name was entered.
        const val DEFAULT_USER_NAME = "User"
    }
}
