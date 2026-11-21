package com.pennywiseai.tracker.presentation.settings

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation3.runtime.NavKey
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.navigation.Appearance
import com.pennywiseai.tracker.navigation.BackgroundAccess
import com.pennywiseai.tracker.navigation.Categories
import com.pennywiseai.tracker.navigation.ExchangeRates
import com.pennywiseai.tracker.navigation.Faq
import com.pennywiseai.tracker.navigation.ImportStatement
import com.pennywiseai.tracker.navigation.LearnedCategories
import com.pennywiseai.tracker.navigation.Loans
import com.pennywiseai.tracker.navigation.ManageAccounts
import com.pennywiseai.tracker.navigation.RecurringTransactions
import com.pennywiseai.tracker.navigation.Rules
import com.pennywiseai.tracker.navigation.SettingsPage
import com.pennywiseai.tracker.navigation.SettingsSection
import com.pennywiseai.tracker.navigation.SmsCheckup
import com.pennywiseai.tracker.navigation.TransactionGroups
import com.pennywiseai.tracker.navigation.UnrecognizedSms
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import java.text.Normalizer
import com.pennywiseai.feature.learnedcategories.R as LearnedCategoriesR

/**
 * One thing Settings search can find: the row's own strings, the page it sits on
 * ([parent], null for a page itself) and where a tap goes.
 */
internal class SettingsSearchEntry(
    @StringRes val title: Int,
    @StringRes val subtitle: Int?,
    @StringRes val parent: Int?,
    val icon: ImageVector,
    val target: NavKey,
    /** More words that find the row: synonyms, or rows on its page that only show once it is on. */
    val keywords: List<Int> = emptyList(),
)

private class Row(
    @StringRes val title: Int,
    @StringRes val subtitle: Int? = null,
    val target: NavKey? = null,
    val keywords: List<Int> = emptyList(),
    /** False drops the row. Used when the page hides the control, so search never opens a page without it. */
    val visible: Boolean = true,
)

// ponytail: a hand-kept list of the rows on each page, since the pages declare their
// rows inline. A tap opens the row's page (or its own screen); scrolling to the row
// itself waits until someone misses it. A row that only shows once a switch above it
// is on (lock timeout, display currency, back up now) is a keyword of that switch, so
// the tap never lands on a page without it. AMOLED black is a keyword of Dark, because
// Light hides that switch. Blur effects is left out below Android 12, where Appearance
// hides it. SettingsSearchTest fails when a page gains a row this list neither holds
// nor skips. Left out: rows that hang on something elsewhere (main account needs an account,
// Budgets/Subscriptions a hidden Plan tab, the conscience nudge the app language,
// Mascot Playground developer mode), the Pro/support row, which differs by flavor,
// and the Help links, which leave the app. Pro-gated screens (AI access, webhooks)
// point at their page so its upgrade gate applies.
internal fun settingsSearchIndex(sdkInt: Int = Build.VERSION.SDK_INT): List<SettingsSearchEntry> = buildList {
    fun page(title: Int, subtitle: Int?, icon: ImageVector, target: NavKey, vararg rows: Row) {
        add(SettingsSearchEntry(title, subtitle, null, icon, target))
        rows.filter { it.visible }.forEach {
            add(SettingsSearchEntry(it.title, it.subtitle, title, icon, it.target ?: target, it.keywords))
        }
    }
    fun section(title: Int, subtitle: Int, icon: ImageVector, section: SettingsSection, vararg rows: Row) =
        page(title, subtitle, icon, SettingsPage(section), *rows)

    page(R.string.manage_accounts_title, null, Icons.Default.AccountBalance, ManageAccounts)
    page(R.string.rules_title, null, Icons.AutoMirrored.Filled.Rule, Rules)
    page(R.string.categories_title, null, Icons.Default.Category, Categories)
    page(R.string.import_statement_title, R.string.settings_tile_import_subtitle, Icons.Default.UploadFile, ImportStatement)

    section(
        R.string.settings_tracking_title, R.string.settings_tracking_summary, Icons.Default.Sms, SettingsSection.TRACKING,
        Row(R.string.sms_checkup_title, R.string.sms_checkup_settings_subtitle, SmsCheckup),
        Row(R.string.settings_sms_scan_title),
        Row(R.string.settings_unrecognized_sms_title, R.string.settings_unrecognized_sms_subtitle, UnrecognizedSms),
        Row(R.string.settings_notification_access_title),
        Row(R.string.settings_tracked_apps_title),
        Row(R.string.background_access_title, R.string.background_access_settings_subtitle, BackgroundAccess),
    )
    section(
        R.string.settings_accounts_categories_title, R.string.settings_accounts_categories_summary, Icons.Default.AccountBalance, SettingsSection.ACCOUNTS,
        Row(LearnedCategoriesR.string.learned_categories_title, LearnedCategoriesR.string.learned_categories_subtitle, LearnedCategories),
        Row(R.string.settings_card_spend_as_expense_title, R.string.settings_card_spend_as_expense_subtitle),
        Row(R.string.settings_atm_cash_to_wallet_title, R.string.settings_atm_cash_to_wallet_subtitle),
        Row(R.string.settings_contacts_vpa_title, R.string.settings_contacts_vpa_subtitle),
        Row(R.string.settings_upi_title, R.string.settings_upi_subtitle),
    )
    section(
        R.string.settings_planning_title, R.string.settings_planning_summary, Icons.Default.AccountBalanceWallet, SettingsSection.PLANNING,
        Row(R.string.settings_budget_cycle_title),
        Row(R.string.settings_recurring_title, R.string.settings_recurring_subtitle, RecurringTransactions),
        Row(R.string.settings_loans_title, R.string.settings_loans_subtitle, Loans),
        Row(R.string.settings_transaction_groups_title, R.string.settings_transaction_groups_subtitle, TransactionGroups),
    )
    section(
        R.string.settings_currency_section, R.string.settings_currency_summary, Icons.Default.CurrencyExchange, SettingsSection.CURRENCY,
        Row(R.string.settings_default_currency_title, R.string.settings_default_currency_subtitle),
        Row(
            R.string.settings_unified_currency_title, R.string.settings_unified_currency_subtitle,
            keywords = listOf(R.string.settings_display_currency_title),
        ),
        Row(R.string.settings_exchange_rates_title, R.string.settings_exchange_rates_subtitle, ExchangeRates),
    )
    page(
        R.string.settings_appearance_title, R.string.settings_appearance_subtitle, Icons.Default.Palette, Appearance,
        Row(R.string.appearance_mode_light, keywords = listOf(R.string.settings_search_keywords_light)),
        Row(
            R.string.appearance_mode_dark,
            keywords = listOf(
                R.string.settings_search_keywords_dark,
                // Light hides the switch, so these words open Dark, which is what shows it.
                R.string.appearance_amoled_title,
                R.string.appearance_amoled_subtitle,
            ),
        ),
        Row(R.string.appearance_fonts_section),
        Row(R.string.appearance_navigation_section),
        Row(R.string.appearance_bottom_bar_section, R.string.appearance_bottom_bar_subtitle),
        // Same gate as AppearanceScreen: Build.VERSION_CODES.S (Android 12).
        Row(
            R.string.appearance_blur_title,
            R.string.appearance_blur_subtitle,
            visible = sdkInt >= Build.VERSION_CODES.S,
        ),
        Row(R.string.appearance_red_spending_title, R.string.appearance_red_spending_subtitle),
    )
    section(
        R.string.settings_formats_title, R.string.settings_formats_summary, Icons.Default.Language, SettingsSection.FORMATS,
        Row(R.string.settings_language_title),
        Row(R.string.settings_number_format_title),
        Row(R.string.settings_date_format_title),
    )
    section(
        R.string.settings_notifications_section, R.string.settings_notifications_summary, Icons.Default.Notifications, SettingsSection.NOTIFICATIONS,
        Row(R.string.settings_daily_summary_title, R.string.settings_daily_summary_subtitle),
        Row(R.string.settings_weekly_summary_title, R.string.settings_weekly_summary_subtitle),
        Row(R.string.settings_budget_alerts_title),
        Row(R.string.settings_notification_types_title, R.string.settings_notification_types_subtitle),
    )
    section(
        R.string.settings_security_section, R.string.settings_security_summary, Icons.Default.Lock, SettingsSection.SECURITY,
        Row(
            R.string.settings_app_lock_title,
            keywords = listOf(R.string.settings_lock_timeout_title, R.string.settings_search_keywords_app_lock),
        ),
        Row(R.string.settings_shake_to_hide_title, R.string.settings_shake_to_hide_subtitle),
    )
    section(
        R.string.settings_data_section, R.string.settings_data_summary, Icons.Default.Backup, SettingsSection.DATA,
        Row(R.string.settings_export_data_title, R.string.settings_export_data_subtitle),
        Row(R.string.settings_export_include_receipts_title, R.string.settings_export_include_receipts_subtitle),
        Row(R.string.settings_import_data_title, R.string.settings_import_data_subtitle),
        Row(
            R.string.settings_folder_backup_title,
            keywords = listOf(
                R.string.settings_backup_now_title,
                R.string.settings_folder_backup_include_receipts_title,
                R.string.settings_change_backup_folder_title,
            ),
        ),
        Row(R.string.settings_import_csv_title, R.string.settings_import_csv_subtitle),
        Row(R.string.settings_delete_all_title, R.string.settings_delete_all_subtitle),
    )
    section(
        R.string.settings_ai_section, R.string.settings_ai_summary, Icons.Default.AutoAwesome, SettingsSection.AI,
        Row(R.string.settings_ai_chat_title),
        Row(R.string.ai_access_settings_title),
    )
    section(
        R.string.settings_developer_section, R.string.settings_developer_summary, Icons.Default.Code, SettingsSection.DEVELOPER,
        Row(R.string.settings_webhooks_title),
        Row(R.string.settings_developer_mode_title, R.string.settings_developer_mode_subtitle),
    )
    page(R.string.settings_help_faq_title, R.string.settings_help_faq_subtitle, Icons.AutoMirrored.Filled.Help, Faq)
}

private val marks = Regex("\\p{Mn}+")
private val punctuation = Regex("\\p{P}+")
private val whitespace = Regex("\\s+")

/**
 * Text as search compares it: lower case, without punctuation ("check-up" finds
 * "checkup"), accents or Arabic vowel marks
 * (NFD also splits the hamza off أ/إ/آ), with ى/ة folded to ي/ه and no tatweel.
 * ponytail: drops every combining mark, which loosens matching a little in scripts
 * that write vowels as marks (Devanagari, Thai); per-script folding if that bites.
 */
internal fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(marks, "")
        .replace(punctuation, "")
        .replace(whitespace, " ")
        .replace('ى', 'ي')
        .replace('ة', 'ه')
        .replace("ـ", "")
        .lowercase()
        .trim()

internal fun searchWords(query: String): List<String> = normalizeForSearch(query).split(' ').filter { it.isNotEmpty() }

/**
 * The items holding every word of [query] somewhere in their [fields] (title first,
 * then the rest by weight), best first: the exact title, a title starting with the
 * query, then by how well each word matched. A word starting a word beats one inside
 * a word ("curr" puts Currency above Recurring), and an earlier field beats a later
 * one. List order breaks ties.
 * ponytail: substring match, no typo tolerance ("bakup" finds nothing); fuzzy
 * matching if people ask for it.
 */
internal fun <T> List<T>.matchingSearch(query: String, fields: (T) -> List<String>): List<T> {
    val words = searchWords(query)
    if (words.isEmpty()) return emptyList()
    val needle = words.joinToString(" ")
    return mapNotNull { item ->
        val texts = fields(item).map(::normalizeForSearch)
        val cost = words.sumOf { word -> wordCost(word, texts) ?: return@mapNotNull null }
        val title = texts.first()
        item to when {
            title == needle -> -2
            title.startsWith(needle) -> -1
            else -> cost
        }
    }.sortedBy { it.second }.map { it.first }
}

/** How well [word] matches its best field in [texts]; null when it is in none. */
private fun wordCost(word: String, texts: List<String>): Int? = texts.indices.mapNotNull { i ->
    when {
        texts[i].startsWith(word) || " $word" in texts[i] -> i
        word in texts[i] -> texts.size + i
        else -> null
    }
}.minOrNull()

/**
 * The hub's search field, in place of the bar's title. [focus] requests focus when the
 * field is created (just opened, or recreated by rotation while it was focused), so
 * coming back from a result doesn't pop the keyboard over the list. [onFocusChange]
 * reports later focus, including a tap on the field after that.
 */
@Composable
internal fun SettingsSearchField(
    state: TextFieldState,
    focus: Boolean,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    // Attaching reports unfocused before the open or rotation request lands.
    // Ignore that, or it clears the saved focus and the next rotation leaves the keyboard down.
    var gainedFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (focus) focusRequester.requestFocus() }
    // The placeholder goes once there is text, so TalkBack gets the label from here.
    val label = stringResource(R.string.settings_search)
    BasicTextField(
        state = state,
        lineLimits = TextFieldLineLimits.SingleLine,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            // hasFocus, not isFocused: the field's inner editor is the focused child.
            .onFocusChanged { focusState ->
                if (focusState.hasFocus) gainedFocus = true
                if (gainedFocus) onFocusChange(focusState.hasFocus)
            }
            .semantics { contentDescription = label },
        decorator = { innerTextField ->
            Box {
                if (state.text.isEmpty()) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                innerTextField()
            }
        },
    )
}

private class ResolvedEntry(
    val entry: SettingsSearchEntry,
    val title: String,
    val subtitle: String?,
    val parent: String?,
    val fields: List<String>,
)

/**
 * What matches [query], each row naming the page it lives on, or its own subtitle
 * when that is where a word matched. Counts are announced to TalkBack as they change.
 */
@Composable
internal fun SettingsSearchResults(query: String, contentPadding: PaddingValues, onNavigate: (NavKey) -> Unit) {
    val resources = LocalResources.current
    val resolved = remember(resources) {
        settingsSearchIndex().map { entry ->
            val title = resources.getString(entry.title)
            val subtitle = entry.subtitle?.let(resources::getString)
            val parent = entry.parent?.let(resources::getString)
            val keywords = entry.keywords.joinToString(" ") { resources.getString(it) }
            ResolvedEntry(entry, title, subtitle, parent, listOf(title, subtitle.orEmpty(), keywords, parent.orEmpty()))
        }
    }
    val results = remember(resolved, query) { resolved.matchingSearch(query) { it.fields } }
    val words = remember(query) { searchWords(query) }

    if (results.isEmpty()) {
        Text(
            text = stringResource(R.string.settings_search_empty, query.trim()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding)
                .padding(Dimensions.Padding.content)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.Layout.groupedListGap),
    ) {
        item(key = "count") {
            Text(
                text = pluralStringResource(R.plurals.settings_search_results, results.size, results.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = Spacing.md)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        itemsIndexed(results, key = { _, it -> "${it.entry.title}/${it.entry.target}" }) { index, result ->
            val ownSubtitle = result.subtitle?.let(::normalizeForSearch)
            val title = normalizeForSearch(result.title)
            val matchedSubtitle = ownSubtitle != null && words.any { it !in title && it in ownSubtitle }
            SettingsNavItem(
                icon = result.entry.icon,
                title = result.title,
                subtitle = if (matchedSubtitle) result.subtitle else result.parent ?: result.subtitle.orEmpty(),
                onClick = { onNavigate(result.entry.target) },
                position = ListItemPosition.from(index, results.size),
            )
        }
    }
}
