package com.pennywiseai.tracker.presentation.add

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.pennywiseai.tracker.MainActivity
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.core.ApplicationScope
import com.pennywiseai.tracker.core.QuickAddIntent
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames
import com.pennywiseai.tracker.data.model.VoiceEntry
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.AppLockRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.ModelRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.usecase.AddTransactionUseCase
import com.pennywiseai.tracker.navigation.BottomNavItem
import com.pennywiseai.tracker.ui.components.CategoryChip
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.security.BiometricAuthManager
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.money
import com.pennywiseai.tracker.ui.viewmodel.PennyWiseUserTheme
import com.pennywiseai.tracker.ui.voice.VoiceMicButton
import com.pennywiseai.tracker.ui.voice.VoiceStatusBar
import com.pennywiseai.tracker.ui.voice.rememberVoiceInput
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.withLatinDigits
import com.pennywiseai.tracker.widget.WidgetRefresher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

/**
 * The quick add for what no SMS reports — cash: type the amount, tap a
 * category, Save. A translucent sheet over whatever the user was doing,
 * opened by the widgets' + and the evening recap's "+ Cash"; "More details"
 * hands over to the full form. The merchant is optional and defaults to the
 * category. Saved against a cash account of the selected profile when there
 * is one, so its balance moves: the last used, or the one picked under the
 * title when there are several.
 *
 * Opened by a mic (Home, the cash widget: [QuickAddIntent.voice]) it listens
 * first. One spend or income said ([VoiceEntry]) fills the sheet, type and
 * currency included, for the user to check and save; anything else (a
 * question) opens Chat with it in the box, not sent.
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
    @Inject lateinit var modelRepository: ModelRepository
    @Inject lateinit var biometricAuthManager: BiometricAuthManager

    // The save outlives finish(): the sheet closes as soon as Save is tapped.
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    private val unlocked = mutableStateOf(false)

    /** Opened by a mic: listen first. State, since a later tap can bring this same sheet back (onNewIntent). */
    private var voiceMode by mutableStateOf(false)

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(newBase.withLatinDigits())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState?.getBoolean(KEY_UNLOCKED) == true) unlocked.value = true else checkLock()
        voiceMode = savedInstanceState?.getBoolean(KEY_VOICE) ?: intent.getBooleanExtra(QuickAddIntent.EXTRA_VOICE, false)
        setContent {
            PennyWiseUserTheme {
                // A new mode starts the sheet over: listening, or plain typing.
                if (unlocked.value) key(voiceMode) { Host() }
            }
        }
    }

    /**
     * singleTask: a sheet left behind the lock prompt (only an unlocked one closes on
     * onStop) comes back for the next tap, which may be the other button: the mic's
     * sheet listens, the +'s doesn't.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        voiceMode = intent.getBooleanExtra(QuickAddIntent.EXTRA_VOICE, false)
    }

    // Kept across a rotation only: an unlocked sheet doesn't outlive leaving it (onStop).
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_UNLOCKED, unlocked.value)
        outState.putBoolean(KEY_VOICE, voiceMode)
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

    /** Income categories only for a voice sheet: the plain one adds spends. */
    private class Setup(
        val baseCurrency: String,
        val expenseCategories: List<CategoryEntity>,
        val incomeCategories: List<CategoryEntity>,
        val balances: List<AccountBalanceEntity>,
        val profileId: Long?,
        /** False when the user hid the Chat tab: a question then has nowhere to go. */
        val chatOpen: Boolean,
        /** The chat model is downloaded: Chat can read what this can't, not just keep it. */
        val modelReady: Boolean,
    )

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Host() {
        var setup by remember { mutableStateOf<Setup?>(null) }
        LaunchedEffect(Unit) {
            val usage = runCatching { transactionRepository.getCategoriesByUsage() }.getOrDefault(emptyList())
            val others = BuiltinCategoryNames.current("Others")
            setup = Setup(
                baseCurrency = userPreferencesRepository.baseCurrency.first(),
                expenseCategories = rankCategories(categoryRepository.getVisibleExpenseCategories().first(), usage, others),
                incomeCategories = if (voiceMode) {
                    rankCategories(categoryRepository.getVisibleIncomeCategories().first(), usage, others)
                } else {
                    emptyList()
                },
                balances = accountBalanceRepository.getAllLatestBalances().first(),
                profileId = userPreferencesRepository.selectedProfileId.first(),
                chatOpen = BottomNavItem.Chat.route !in userPreferencesRepository.userPreferences.first().hiddenNavTabs,
                modelReady = withContext(Dispatchers.IO) { modelRepository.isModelDownloaded() }
            )
        }
        val s = setup ?: return
        // Saveable: a rotation after the words were read shows the sheet, not the mic again.
        var listening by rememberSaveable { mutableStateOf(voiceMode) }
        // Only read as the sheet first fills; its own saveable fields carry it from there.
        var heard by remember { mutableStateOf<VoiceEntry?>(null) }
        ModalBottomSheet(
            onDismissRequest = { finish() },
            sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
        ) {
            if (listening) {
                VoicePane(
                    setup = s,
                    onEntry = {
                        heard = it
                        listening = false
                    },
                    onType = { listening = false }
                )
            } else {
                Sheet(s, heard)
            }
        }
    }

    /** Listening: the words as they're heard, then routed once when the user stops talking. */
    @Composable
    private fun VoicePane(setup: Setup, onEntry: (VoiceEntry) -> Unit, onType: () -> Unit) {
        var transcript by rememberSaveable { mutableStateOf("") }
        var unclear by rememberSaveable { mutableStateOf(false) }
        // A question, with the Chat tab hidden: not the user's mistake, so a different line.
        var chatHidden by rememberSaveable { mutableStateOf(false) }
        // Starts by itself once; a rotation doesn't start it again.
        var started by rememberSaveable { mutableStateOf(false) }
        // Words from a recording still going: only these are routed after a rotation, not
        // ones already routed or left by a recording that failed.
        var midRecording by rememberSaveable { mutableStateOf(false) }
        val categories = setup.expenseCategories + setup.incomeCategories
        fun route(text: String) {
            midRecording = false
            when (val route = voiceRoute(text, categories, setup.baseCurrency, setup.chatOpen, setup.modelReady) { CategoryMapping.label(this, it) }) {
                is VoiceRoute.Fill -> onEntry(route.entry)
                VoiceRoute.Chat -> openChat(text)
                VoiceRoute.Unclear -> unclear = true
                VoiceRoute.ChatHidden -> chatHidden = true
            }
        }
        val voice = rememberVoiceInput(
            onText = {
                transcript = it
                unclear = false
                chatHidden = false
                midRecording = true
            },
            onDone = ::route
        )
        LaunchedEffect(voice) {
            snapshotFlow { voice.note }.collect { if (it?.isError == true) midRecording = false }
        }
        LaunchedEffect(Unit) {
            // A widget drawn before the phone lost its recognizer: type instead.
            if (!voice.available) return@LaunchedEffect onType()
            // A rotation stopped the mic mid-sentence: use the words it kept, as if the user stopped.
            if (started && midRecording && transcript.isNotEmpty()) return@LaunchedEffect route(transcript)
            if (!started) {
                // Until the recognizer says which languages it has, Arabic on Google's (online
                // only) would start on-device and fail, or skip the consent question.
                withTimeoutOrNull(LANGUAGES_WAIT_MS) { snapshotFlow { voice.ready }.first { it } }
                started = true
                if (!voice.active) voice.toggle("")
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.Padding.dialog)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.quick_add_voice_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        voice.cancel()
                        onType()
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.quick_add_voice_type))
                }
            }
            Text(
                text = transcript.ifEmpty { stringResource(R.string.quick_add_voice_hint) },
                // Arabic said reads right to left.
                style = (if (transcript.isEmpty()) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.headlineSmall)
                    .copy(textDirection = TextDirection.Content),
                color = if (transcript.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
            if (unclear || chatHidden) {
                Text(
                    text = stringResource(if (chatHidden) R.string.quick_add_voice_chat_hidden else R.string.quick_add_voice_unclear),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                VoiceStatusBar(voice, Modifier.weight(1f))
                VoiceMicButton(
                    voice = voice,
                    enabled = true,
                    onTap = {
                        unclear = false
                        chatHidden = false
                        // A new recording: the last one's words are done with.
                        if (!voice.active) midRecording = false
                        voice.toggle("")
                    }
                )
            }
        }
    }

    @Composable
    private fun Sheet(setup: Setup, heard: VoiceEntry?) {
        // Saveable: a rotation, the dark theme or a font size change recreates the sheet.
        var amountText by rememberSaveable { mutableStateOf(heard?.amount?.stripTrailingZeros()?.toPlainString().orEmpty()) }
        var where by rememberSaveable { mutableStateOf(heard?.merchant.orEmpty()) }
        var category by rememberSaveable { mutableStateOf(heard?.category) }
        var income by rememberSaveable { mutableStateOf(heard?.type == TransactionType.INCOME) }
        val currency by rememberSaveable { mutableStateOf(heard?.currency ?: setup.baseCurrency) }
        // A day said out loud ("yesterday"); null is now.
        var day by rememberSaveable { mutableStateOf(heard?.date?.takeIf { it != LocalDate.now() }) }
        // Only accounts in the entry's currency: a balance moves by the raw amount.
        val cashAccounts = remember(currency) { cashAccounts(setup.balances, currency, setup.profileId) }
        var accountKey by rememberSaveable { mutableStateOf(cashAccounts.firstOrNull()?.let(::keyOf)) }
        val account = cashAccounts.firstOrNull { keyOf(it) == accountKey }
        val categories = if (income) setup.incomeCategories else setup.expenseCategories
        val type = if (income) TransactionType.INCOME else TransactionType.EXPENSE
        val amount = amountText.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
        val focus = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        val decimalComma = remember {
            DecimalFormatSymbols.getInstance(resources.configuration.locales[0]).decimalSeparator == ','
        }
        // Filled by voice: what's left is a tap on a category, not typing. The plain sheet
        // types, so its amount field takes focus again after a rotation too.
        LaunchedEffect(Unit) { if (!voiceMode || amountText.isEmpty()) focus.requestFocus() }

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
                    text = stringResource(if (income) R.string.quick_add_title_income else R.string.quick_add_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { openFullForm(amountText, where, category, account, type, currency, day) },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.quick_add_more_details))
                }
            }
            // What the words were read as, to correct: the plain sheet adds spends only.
            if (voiceMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    listOf(false to R.string.quick_add_spent, true to R.string.quick_add_received).forEach { (isIncome, label) ->
                        FilterChip(
                            selected = income == isIncome,
                            shapes = FilterChipDefaults.shapes(),
                            onClick = {
                                if (income != isIncome) {
                                    income = isIncome
                                    // Categories are per type.
                                    category = null
                                }
                            },
                            label = { Text(stringResource(label)) }
                        )
                    }
                    day?.let { d ->
                        InputChip(
                            selected = false,
                            onClick = { day = null },
                            label = { Text(d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.quick_add_date_today),
                                    modifier = Modifier.size(InputChipDefaults.IconSize)
                                )
                            }
                        )
                    }
                }
            }
            // Only with a choice to make; one account or none needs no line.
            if (account != null && cashAccounts.size > 1) {
                AccountPicker(account, cashAccounts) { accountKey = keyOf(it) }
            }
            OutlinedTextField(
                value = amountText,
                onValueChange = { typed -> normalizeAmount(typed, decimalComma)?.let { amountText = it } },
                prefix = { Text(CurrencyFormatter.getCurrencySymbol(currency) + " ") },
                textStyle = MaterialTheme.typography.headlineSmall.money(),
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
                categories.forEach { c ->
                    FilterChip(
                        selected = category == c.name,
                        shapes = FilterChipDefaults.shapes(),
                        colors = FilterChipDefaults.filterChipColors(),
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
                onClick = { save(amount!!, category!!, where, account, currency, type, day) },
                enabled = amount != null && category != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimensions.Component.minTouchTarget),
                shapes = ButtonDefaults.shapes()
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

    private fun save(
        amount: BigDecimal,
        category: String,
        where: String,
        account: AccountBalanceEntity?,
        currency: String,
        type: TransactionType,
        day: LocalDate?
    ) {
        if (saving) return
        saving = true
        val app = applicationContext
        appScope.launch {
            val saved = runCatching {
                addTransaction.execute(
                    amount = amount,
                    merchant = where.trim().ifEmpty { category },
                    category = category,
                    type = type,
                    date = day?.atTime(LocalTime.now()) ?: LocalDateTime.now(),
                    bankName = account?.bankName,
                    accountLast4 = account?.accountLast4,
                    currency = currency
                )
            }.onFailure { Log.e(TAG, "Quick add failed", it) }.isSuccess
            // Apart from the save: a refresh that fails mustn't report a saved
            // expense as failed and have it entered twice.
            if (saved) runCatching {
                WidgetRefresher.refreshTransactionWidgets(app)
                com.pennywiseai.tracker.worker.MoneyAlertWorker.enqueue(app)
            }.onFailure { Log.w(TAG, "Widget refresh failed", it) }
            val message = if (saved) {
                app.getString(R.string.quick_add_saved, CurrencyFormatter.formatCurrency(amount, currency), CategoryMapping.label(app, category))
            } else {
                app.getString(R.string.add_error_save_transaction)
            }
            withContext(Dispatchers.Main) { Toast.makeText(app, message, Toast.LENGTH_SHORT).show() }
        }
        finish()
    }

    /** The full form, with what was typed (or said) here so nothing is entered twice. */
    private fun openFullForm(
        amount: String,
        where: String,
        category: String?,
        account: AccountBalanceEntity?,
        type: TransactionType,
        currency: String,
        day: LocalDate?
    ) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_ADD_TRANSACTION, true)
                .putExtra(MainActivity.EXTRA_ADD_AMOUNT, amount.ifEmpty { null })
                .putExtra(MainActivity.EXTRA_ADD_MERCHANT, where.trim().ifEmpty { null })
                .putExtra(MainActivity.EXTRA_ADD_CATEGORY, category)
                .putExtra(MainActivity.EXTRA_ADD_BANK, account?.bankName)
                .putExtra(MainActivity.EXTRA_ADD_LAST4, account?.accountLast4)
                .putExtra(MainActivity.EXTRA_ADD_TYPE, type.name)
                .putExtra(MainActivity.EXTRA_ADD_CURRENCY, currency)
                .putExtra(MainActivity.EXTRA_ADD_DATE_TIME, day?.atTime(LocalTime.now())?.toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }

    /** A question: Chat with it in the box, not sent. Single top keeps the app as it was under the sheet. */
    private fun openChat(text: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_CHAT_TEXT, text)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    /** Where what was said goes: the sheet, Chat, or back to the mic. */
    internal sealed interface VoiceRoute {
        data class Fill(val entry: VoiceEntry) : VoiceRoute
        data object Chat : VoiceRoute
        data object Unclear : VoiceRoute

        /** A question or a plan, with the Chat tab hidden. */
        data object ChatHidden : VoiceRoute
    }

    companion object {
        private const val TAG = "QuickAddActivity"
        private const val KEY_UNLOCKED = "unlocked"
        private const val KEY_VOICE = "voice"
        private const val CASH = "CASH"

        /** How long the mic waits for the language list before starting anyway. */
        private const val LANGUAGES_WAIT_MS = 3_000L

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
         * One entry fills the sheet. A question or a plan goes to Chat (with no model it waits
         * there), or, with Chat hidden, says so. Two amounts go to Chat only with the model there
         * to read them: a cash spend said as "2 coffees 10" must not end up where it can't be
         * recorded. Anything else (no amount heard) is unclear.
         */
        internal fun voiceRoute(
            text: String,
            categories: List<CategoryEntity>,
            baseCurrency: String,
            chatOpen: Boolean,
            modelReady: Boolean,
            today: LocalDate = LocalDate.now(),
            label: (String) -> String = { it }
        ): VoiceRoute = VoiceEntry.parse(text, categories, baseCurrency, today, label)?.let { VoiceRoute.Fill(it) }
            ?: when {
                VoiceEntry.asksOrPlans(text) -> if (chatOpen) VoiceRoute.Chat else VoiceRoute.ChatHidden
                !chatOpen -> VoiceRoute.Unclear
                modelReady && VoiceEntry.needsModel(text) -> VoiceRoute.Chat
                else -> VoiceRoute.Unclear
            }

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
