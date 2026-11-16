package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.LoanRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/** Net worth in the active profile's scope, with each account's line. */
data class NetWorthSnapshot(
    val accounts: List<NetWorthAccount>,
    val totals: List<NetWorthTotal>,
    /** IOUs with people, per currency; empty while one profile is selected (loans have no profile). */
    val peopleOwed: List<PeopleOwed>,
)

/**
 * Loads net worth for the active profile, leaving out ignored accounts. Every change to
 * a balance or a transaction rewrites balance rows, so following those keeps it current.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NetWorthLoader @Inject constructor(
    private val accountBalanceRepository: AccountBalanceRepository,
    private val loanRepository: LoanRepository,
    private val ignoredAccountsStore: IgnoredAccountsStore,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    private val scoped: Flow<List<AccountBalanceEntity>> = combine(
        accountBalanceRepository.getAllLatestBalances(),
        ignoredAccountsStore.keysFlow,
        userPreferencesRepository.selectedProfileId,
    ) { balances, ignored, profileId ->
        balances.filter {
            !IgnoredAccountsStore.isIgnored(ignored, it.bankName, it.accountLast4) &&
                (profileId == null || it.profileId == profileId)
        }
    }

    fun observe(): Flow<NetWorthSnapshot> = combine(
        scoped,
        accountBalanceRepository.getUnanchoredAccounts(),
        accountBalanceRepository.getBalanceEvidence(),
        loanRepository.getActiveLoans(),
        userPreferencesRepository.selectedProfileId,
    ) { accounts, unanchored, evidence, loans, profileId ->
        Inputs(accounts, unanchored, evidence, if (profileId == null) NetWorth.peopleOwed(loans) else emptyList())
    }.mapLatest { (accounts, unanchored, evidence, peopleOwed) ->
        val now = LocalDateTime.now()
        val lines = accounts.map { account ->
            val key = account.bankName to account.accountLast4
            val source = NetWorth.source(account, evidence[key], key in unanchored)
            NetWorthAccount(
                account = account,
                source = source,
                transactionsSince = source?.since
                    ?.let { accountBalanceRepository.countTransactionsSince(account.bankName, account.accountLast4, it) }
                    ?: 0,
                daysSinceUpdate = NetWorth.daysSince(account.timestamp, now),
            )
        }
        NetWorthSnapshot(lines, NetWorth.totals(lines), peopleOwed)
    }

    /** Daily net worth per currency from [start] to [end]; see [NetWorth.trend]. */
    fun observeTrend(start: LocalDate, end: LocalDate): Flow<Map<String, List<NetWorthPoint>>> = combine(
        scoped,
        accountBalanceRepository.getTrendRows(start.atStartOfDay()),
        ::Pair,
    ).mapLatest { (accounts, rows) ->
        val keys = accounts.map { it.bankName to it.accountLast4 }.toSet()
        val derived = accounts.filter { accountBalanceRepository.isManualAccount(it.bankName, it.accountLast4) }
            .associate { account ->
                (account.bankName to account.accountLast4) to DerivedHistory(
                    currency = account.currency,
                    current = account.balance,
                    anchored = account.anchorAt != null,
                    effects = accountBalanceRepository.manualBalanceEffects(account.bankName, account.accountLast4),
                )
            }
        NetWorth.trend(rows.filter { (it.bankName to it.accountLast4) in keys }, derived, start, end)
    }

    private data class Inputs(
        val accounts: List<AccountBalanceEntity>,
        val unanchored: Set<Pair<String, String>>,
        val evidence: Map<Pair<String, String>, BalanceEvidence>,
        val peopleOwed: List<PeopleOwed>,
    )
}
