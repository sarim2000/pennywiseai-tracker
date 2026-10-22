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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
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
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * The quick add for what no SMS reports — cash: type the amount, tap a
 * category, Save. A translucent sheet over whatever the user was doing,
 * opened by the widgets' + and the evening recap's "+ Cash"; "More details"
 * hands over to the full form. The merchant is optional and defaults to the
 * category. Saved against the user's cash account when there is one, so its
 * balance moves.
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_UNLOCKED, unlocked.value)
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

    private class Setup(val currency: String, val categories: List<CategoryEntity>, val cashAccount: AccountBalanceEntity?)

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
                // ponytail: the first cash account in the base currency; "More details" picks another.
                cashAccount = accountBalanceRepository.getAllLatestBalances().first()
                    .firstOrNull { it.accountType == CASH && it.currency == currency }
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
        var amountText by remember { mutableStateOf("") }
        var where by remember { mutableStateOf("") }
        var category by remember { mutableStateOf<String?>(null) }
        val amount = amountText.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
        val focus = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
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
                TextButton(onClick = ::openFullForm) { Text(stringResource(R.string.quick_add_more_details)) }
            }
            OutlinedTextField(
                value = amountText,
                onValueChange = { typed -> normalizeAmount(typed)?.let { amountText = it } },
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
                        label = { Text(c.name) },
                        leadingIcon = { CategoryChip(category = c, showText = false) }
                    )
                }
            }
            Button(
                onClick = { save(amount!!, category!!, where, setup) },
                enabled = amount != null && category != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimensions.Component.minTouchTarget)
            ) {
                Text(stringResource(R.string.add_save))
            }
        }
    }

    // A second tap lands before finish() takes effect; one sheet saves one row.
    private var saving = false

    private fun save(amount: BigDecimal, category: String, where: String, setup: Setup) {
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
                    bankName = setup.cashAccount?.bankName,
                    accountLast4 = setup.cashAccount?.accountLast4,
                    currency = setup.currency
                )
                WidgetRefresher.refreshTransactionWidgets(app)
            }.onFailure { Log.e(TAG, "Quick add failed", it) }.isSuccess
            val message = if (saved) {
                app.getString(R.string.quick_add_saved, CurrencyFormatter.formatCurrency(amount, setup.currency), category)
            } else {
                app.getString(R.string.add_error_save_transaction)
            }
            withContext(Dispatchers.Main) { Toast.makeText(app, message, Toast.LENGTH_SHORT).show() }
        }
        finish()
    }

    private fun openFullForm() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_ADD_TRANSACTION, true)
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
         * (an Arabic keyboard types ٣٫٥) as ASCII, "," or "٫" as the decimal
         * point, at most one point. Null rejects the keystroke.
         */
        internal fun normalizeAmount(input: String): String? {
            val ascii = input.map { c ->
                when {
                    c == ',' || c == '٫' -> '.'
                    Character.isDigit(c) -> '0' + Character.digit(c, 10)
                    else -> c
                }
            }.joinToString("")
            return ascii.takeIf { AMOUNT_INPUT.matches(it) }
        }

        private val AMOUNT_INPUT = Regex("""\d*\.?\d*""")

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
