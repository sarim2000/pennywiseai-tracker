package com.pennywiseai.tracker.presentation.add

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.pennywiseai.tracker.MainActivity
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.AppLockRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.di.ApplicationScope
import com.pennywiseai.tracker.domain.security.BiometricAuthManager
import com.pennywiseai.tracker.domain.usecase.AddTransactionUseCase
import com.pennywiseai.tracker.ui.components.CategoryChip
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseTheme
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.widget.WidgetRefresher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.time.LocalDateTime
import javax.inject.Inject
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.icons.CategoryMapping

/**
 * The quick add for what no SMS reports — cash: type the amount, tap a
 * category, Save. A translucent sheet over whatever the user was doing,
 * opened by the widgets' + and the evening recap's "+ Cash"; "More details"
 * hands over to the full form. The merchant is optional and defaults to the
 * category. Saved against a cash account of the selected profile when there
 * is one, so its balance moves: the last used, or the one picked under the
 * title when there are several.
 *
 * Behind the app lock, like opening the app.
 */
@AndroidEntryPoint
class QuickAddActivity : FragmentActivity() {

    @Inject lateinit var addTransaction: AddTransactionUseCase
    @Inject lateinit var categoryRepository: CategoryRepository
    @Inject lateinit var transactionRepository: TransactionRepository
    @Inject lateinit var accountBalanceRepository: AccountBalanceRepository
    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository
    @Inject lateinit var appLockRepository: AppLockRepository
    @Inject lateinit var biometricAuthManager: BiometricAuthManager

    // The save outlives finish(): the sheet closes as soon as Save is tapped.
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    private val unlocked = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState?.getBoolean(KEY_UNLOCKED) == true) unlocked.value = true else checkLock()
        setContent {
            PennyWiseTheme {
                if (unlocked.value) Host()
            }
        }
    }

    // Kept across a rotation only: an unlocked sheet doesn't outlive leaving it (onStop).
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_UNLOCKED, unlocked.value)
    }

    /**
     * Left after unlocking (Home, another app, the screen off): close. The
     * sheet is singleTask, so one kept in the background came back on the
     * next + without onCreate, past the lock's timeout; a fresh one checks.
     * Not while locked: the PIN fallback covers the sheet mid-check.
     */
    override fun onStop() {
        super.onStop()
        if (unlocked.value && !isChangingConfigurations) finish()
    }

    private fun checkLock() {
        lifecycleScope.launch {
            if (!appLockRepository.shouldLockApp()) {
                unlocked.value = true
                return@launch
            }
            biometricAuthManager.authenticate(
                activity = this@QuickAddActivity,
                onSuccess = {
                    lifecycleScope.launch { appLockRepository.updateAuthTimestamp() }
                    unlocked.value = true
                },
                onError = { finish() },
                onCancel = { finish() }
            )
        }
    }

    private class Setup(val currency: String, val categories: List<CategoryEntity>, val cashAccounts: List<AccountBalanceEntity>)

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Host() {
        var setup by remember { mutableStateOf<Setup?>(null) }
        LaunchedEffect(Unit) {
            val currency = userPreferencesRepository.baseCurrency.first()
            setup = Setup(
                currency = currency,
                categories = rankCategories(
                    categoryRepository.getVisibleExpenseCategories().first(),
                    runCatching { transactionRepository.getCategoriesByUsage() }.getOrDefault(emptyList()),
                    others = BuiltinCategoryNames.current("Others")
                ),
                cashAccounts = cashAccounts(
                    accountBalanceRepository.getAllLatestBalances().first(),
                    currency,
                    userPreferencesRepository.selectedProfileId.first()
                )
            )
        }
        val s = setup ?: return
        ModalBottomSheet(
            onDismissRequest = { finish() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Sheet(s)
        }
    }

    @Composable
    private fun Sheet(setup: Setup) {
        // Saveable: a rotation, the dark theme or a font size change recreates the sheet.
        var amountText by rememberSaveable { mutableStateOf("") }
        var where by rememberSaveable { mutableStateOf("") }
        var category by rememberSaveable { mutableStateOf<String?>(null) }
        var accountKey by rememberSaveable { mutableStateOf(setup.cashAccounts.firstOrNull()?.let(::keyOf)) }
        val account = setup.cashAccounts.firstOrNull { keyOf(it) == accountKey }
        val amount = amountText.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
        val focus = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        val decimalComma = remember {
            DecimalFormatSymbols.getInstance(resources.configuration.locales[0]).decimalSeparator == ','
        }
        LaunchedEffect(Unit) { focus.requestFocus() }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.Padding.dialog)
                .padding(bottom = Spacing.lg)
                .imePadding()
                // Short phones and landscape: scroll rather than run off the sheet.
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.quick_add_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { openFullForm(amountText, where, category, account) }) {
                    Text(stringResource(R.string.quick_add_more_details))
                }
            }
            // Only with a choice to make; one account or none needs no line.
            if (account != null && setup.cashAccounts.size > 1) {
                AccountPicker(account, setup.cashAccounts) { accountKey = keyOf(it) }
            }
            OutlinedTextField(
                value = amountText,
                onValueChange = { typed -> normalizeAmount(typed, decimalComma)?.let { amountText = it } },
                prefix = { Text(CurrencyFormatter.getCurrencySymbol(setup.currency) + " ") },
                textStyle = MaterialTheme.typography.headlineSmall,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
            OutlinedTextField(
                value = where,
                onValueChange = { where = it },
                placeholder = { Text(stringResource(R.string.quick_add_where_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )
            // One scrolling row, most-used first: the usual pick is in view and
            // Save stays above the keyboard.
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                setup.categories.forEach { c ->
                    FilterChip(
                        selected = category == c.name,
                        onClick = {
                            category = c.name
                            // Typing is done once the category is picked; on a short phone the keyboard hides Save.
                            focusManager.clearFocus()
                        },
                        label = { Text(categoryLabel(c.name)) },
                        leadingIcon = { CategoryChip(category = c, showText = false) }
                    )
                }
            }
            Button(
                onClick = { save(amount!!, category!!, where, account, setup.currency) },
                enabled = amount != null && category != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimensions.Component.minTouchTarget)
            ) {
                Text(stringResource(R.string.add_save))
            }
        }
    }

    /** "From: Wallet ▾" — the account the spend comes out of, changed from a short menu. */
    @Composable
    private fun AccountPicker(
        selected: AccountBalanceEntity,
        accounts: List<AccountBalanceEntity>,
        onPick: (AccountBalanceEntity) -> Unit
    ) {
        var open by remember { mutableStateOf(false) }
        Box {
            AssistChip(
                onClick = { open = true },
                label = { Text(stringResource(R.string.quick_add_from, selected.displayLabel)) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                accounts.forEach { a ->
                    DropdownMenuItem(
                        text = { Text(a.displayLabel) },
                        onClick = {
                            onPick(a)
                            open = false
                        }
                    )
                }
            }
        }
    }

    // A second tap lands before finish() takes effect; one sheet saves one row.
    private var saving = false

    private fun save(amount: BigDecimal, category: String, where: String, account: AccountBalanceEntity?, currency: String) {
        if (saving) return
        saving = true
        val app = applicationContext
        appScope.launch {
            val saved = runCatching {
                addTransaction.execute(
                    amount = amount,
                    merchant = where.trim().ifEmpty { category },
                    category = category,
                    type = TransactionType.EXPENSE,
                    date = LocalDateTime.now(),
                    bankName = account?.bankName,
                    accountLast4 = account?.accountLast4,
                    currency = currency
                )
            }.onFailure { Log.e(TAG, "Quick add failed", it) }.isSuccess
            // Apart from the save: a refresh that fails mustn't report a saved
            // expense as failed and have it entered twice.
            if (saved) runCatching { WidgetRefresher.refreshTransactionWidgets(app) }.onFailure { Log.w(TAG, "Widget refresh failed", it) }
            val message = if (saved) {
                app.getString(R.string.quick_add_saved, CurrencyFormatter.formatCurrency(amount, currency), CategoryMapping.label(app, category))
            } else {
                app.getString(R.string.add_error_save_transaction)
            }
            withContext(Dispatchers.Main) { Toast.makeText(app, message, Toast.LENGTH_SHORT).show() }
        }
        finish()
    }

    /** The full form, with what was typed here so nothing is entered twice. */
    private fun openFullForm(amount: String, where: String, category: String?, account: AccountBalanceEntity?) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_ADD_TRANSACTION, true)
                .putExtra(MainActivity.EXTRA_ADD_AMOUNT, amount.ifEmpty { null })
                .putExtra(MainActivity.EXTRA_ADD_MERCHANT, where.trim().ifEmpty { null })
                .putExtra(MainActivity.EXTRA_ADD_CATEGORY, category)
                .putExtra(MainActivity.EXTRA_ADD_BANK, account?.bankName)
                .putExtra(MainActivity.EXTRA_ADD_LAST4, account?.accountLast4)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }

    companion object {
        private const val TAG = "QuickAddActivity"
        private const val KEY_UNLOCKED = "unlocked"
        private const val CASH = "CASH"

        /**
         * What the amount field keeps of a keystroke: digits in any script
         * (an Arabic keyboard types ٣٫٥) as ASCII, "٫" as the decimal point,
         * at most one point. "," is the decimal point only where the locale
         * writes it so (3,5 in German); elsewhere it's a thousands separator
         * and dropped, as the full form does, so "1,250" is 1250, not 1.25.
         * Null rejects the keystroke.
         */
        internal fun normalizeAmount(input: String, decimalComma: Boolean): String? {
            val ascii = input.mapNotNull { c ->
                when {
                    c == '٫' || (c == ',' && decimalComma) -> '.'
                    c == ',' || c == '٬' -> null
                    Character.isDigit(c) -> '0' + Character.digit(c, 10)
                    else -> c
                }
            }.joinToString("")
            return ascii.takeIf { AMOUNT_INPUT.matches(it) }
        }

        private val AMOUNT_INPUT = Regex("""\d*\.?\d*""")

        /**
         * The cash accounts the sheet can charge, last used first: the
         * selected profile's (null: all profiles) in the entry currency.
         * Last used is the latest balance move; every save moves its
         * account's, so a changed pick is the default next time.
         */
        internal fun cashAccounts(
            accounts: List<AccountBalanceEntity>,
            currency: String,
            profileId: Long?
        ): List<AccountBalanceEntity> = accounts
            .filter { it.accountType == CASH && it.currency == currency && (profileId == null || it.profileId == profileId) }
            .sortedByDescending { it.timestamp }

        private fun keyOf(account: AccountBalanceEntity) = "${account.bankName}_${account.accountLast4}"

        /** Most-used first, the rest in their usual order, [others] last: it's the non-answer. */
        internal fun rankCategories(
            categories: List<CategoryEntity>,
            usedMostFirst: List<String>,
            others: String
        ): List<CategoryEntity> {
            val rank = usedMostFirst.withIndex().associate { (i, name) -> name to i }
            return categories.sortedWith(compareBy({ it.name == others }, { rank[it.name] ?: Int.MAX_VALUE }))
        }
    }
}
