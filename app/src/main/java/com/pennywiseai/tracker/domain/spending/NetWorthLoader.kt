package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.utils.CurrencyFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** Net worth in the active profile's scope, with the accounts it counts. */
data class NetWorthSnapshot(
    val accounts: List<AccountBalanceEntity>,
    val totals: List<NetWorthTotal>,
)

/**
 * Loads net worth for the active profile, leaving out ignored accounts. Every change to
 * a balance or a transaction rewrites balance rows, so following those keeps it current.
 */
class NetWorthLoader @Inject constructor(
    private val accountBalanceRepository: AccountBalanceRepository,
    private val ignoredAccountsStore: IgnoredAccountsStore,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    fun observe(): Flow<NetWorthSnapshot> = combine(
        accountBalanceRepository.getAllLatestBalances(),
        ignoredAccountsStore.keysFlow,
        userPreferencesRepository.selectedProfileId,
    ) { balances, ignored, profileId ->
        val accounts = balances.filter {
            !IgnoredAccountsStore.isIgnored(ignored, it.bankName, it.accountLast4) &&
                (profileId == null || it.profileId == profileId)
        }
            // Two rows can share an account's newest timestamp; count the account once.
            .sortedByDescending { it.id }
            .distinctBy { it.bankName to it.accountLast4 }
            // The currency Manage Accounts shows: an older SMS-tracked account can be stored as INR.
            .map { it.copy(currency = CurrencyFormatter.resolveAccountCurrency(it.sourceType, it.currency, it.bankName)) }
        NetWorthSnapshot(accounts, NetWorth.totals(accounts))
    }
}
