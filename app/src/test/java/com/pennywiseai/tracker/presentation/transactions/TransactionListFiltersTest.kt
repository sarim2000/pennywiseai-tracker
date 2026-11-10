package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.domain.model.TransactionTypeFilter
import com.pennywiseai.tracker.presentation.common.filterTransactionsByAccount
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class TransactionListFiltersTest {

    private fun tx(
        id: Long,
        category: String = "Food",
        type: TransactionType = TransactionType.EXPENSE,
        bank: String? = "Test Bank",
        last4: String? = "1234",
        profileId: Long? = null,
    ) = TransactionEntity(
        id = id,
        amount = BigDecimal.TEN,
        merchantName = "Synthetic Shop",
        category = category,
        transactionType = type,
        dateTime = LocalDateTime.of(2026, 1, 1, 12, 0),
        transactionHash = "filters-$id",
        bankName = bank,
        accountNumber = last4,
        profileId = profileId,
    )

    private fun split(transactionId: Long, category: String) =
        TransactionSplitEntity(transactionId = transactionId, category = category, amount = BigDecimal.ONE)

    private fun ids(rows: List<TransactionEntity>) = rows.map { it.id }

    // ─── Type ────────────────────────────────────────────────────────────────

    private val oneOfEachType = TransactionType.entries.mapIndexed { i, type -> tx(i.toLong(), type = type) }

    private fun typesLeftBy(filter: TransactionTypeFilter) =
        filterByType(oneOfEachType, filter).map { it.transactionType }.toSet()

    @Test
    fun `the All type filter keeps every row`() {
        assertEquals(oneOfEachType, filterByType(oneOfEachType, TransactionTypeFilter.ALL))
    }

    @Test
    fun `each type filter keeps only its type`() {
        assertEquals(setOf(TransactionType.INCOME), typesLeftBy(TransactionTypeFilter.INCOME))
        assertEquals(setOf(TransactionType.EXPENSE), typesLeftBy(TransactionTypeFilter.EXPENSE))
        assertEquals(setOf(TransactionType.CREDIT), typesLeftBy(TransactionTypeFilter.CREDIT))
        assertEquals(setOf(TransactionType.TRANSFER), typesLeftBy(TransactionTypeFilter.TRANSFER))
        assertEquals(setOf(TransactionType.INVESTMENT), typesLeftBy(TransactionTypeFilter.INVESTMENT))
    }

    @Test
    fun `the Spending type filter keeps expenses and credit`() {
        assertEquals(setOf(TransactionType.EXPENSE, TransactionType.CREDIT), typesLeftBy(TransactionTypeFilter.SPENDING))
    }

    // ─── Categories ──────────────────────────────────────────────────────────

    @Test
    fun `a row's own category matches`() {
        val rows = listOf(tx(1, "Food"), tx(2, "Travel"))
        assertEquals(listOf(1L), ids(filterByCategories(rows, setOf("Food"), emptyList())))
    }

    @Test
    fun `a category found only in a split matches`() {
        val rows = listOf(tx(1, "Others"), tx(2, "Others"))
        assertEquals(listOf(1L), ids(filterByCategories(rows, setOf("Groceries"), listOf(split(1, "Groceries")))))
    }

    @Test
    fun `another transaction's split does not match`() {
        assertEquals(emptyList<Long>(), ids(filterByCategories(listOf(tx(1, "Others")), setOf("Groceries"), listOf(split(9, "Groceries")))))
    }

    // ─── Ignored accounts and profile ────────────────────────────────────────

    private val ignoredKey = "Test Bank_0000"

    @Test
    fun `an ignored account's rows drop out even for All profiles`() {
        val rows = listOf(tx(1, last4 = "0000"), tx(2))
        assertEquals(listOf(2L), ids(filterTrackedByProfile(rows, null, setOf(ignoredKey), emptyMap())))
    }

    @Test
    fun `an ignored account's rows drop out even for their own profile`() {
        val rows = listOf(tx(1, last4 = "0000", profileId = 2))
        assertEquals(emptyList<Long>(), ids(filterTrackedByProfile(rows, 2, setOf(ignoredKey), emptyMap())))
    }

    @Test
    fun `no ignored accounts and All profiles keeps every row`() {
        val rows = listOf(tx(1), tx(2, bank = null, last4 = null))
        assertEquals(rows, filterTrackedByProfile(rows, null, emptySet(), emptyMap()))
    }

    @Test
    fun `a row's own profile wins over its account's`() {
        val rows = listOf(tx(1, profileId = 2))
        val accountInThree = mapOf(3L to setOf("Test Bank_1234"))
        assertEquals(listOf(1L), ids(filterTrackedByProfile(rows, 2, emptySet(), accountInThree)))
        assertEquals(emptyList<Long>(), ids(filterTrackedByProfile(rows, 3, emptySet(), accountInThree)))
    }

    @Test
    fun `a row inherits its account's profile`() {
        val accountInFive = mapOf(5L to setOf("Test Bank_1234"))
        assertEquals(listOf(1L), ids(filterTrackedByProfile(listOf(tx(1)), 5, emptySet(), accountInFive)))
    }

    @Test
    fun `a row with no profile or known account counts as Personal`() {
        val rows = listOf(tx(1, bank = null, last4 = null))
        assertEquals(listOf(1L), ids(filterTrackedByProfile(rows, ProfileEntity.PERSONAL_ID, emptySet(), emptyMap())))
        assertEquals(emptyList<Long>(), ids(filterTrackedByProfile(rows, 5, emptySet(), emptyMap())))
    }

    // ─── Account ─────────────────────────────────────────────────────────────

    @Test
    fun `the account filter matches the exact bank and last four`() {
        val rows = listOf(tx(1), tx(2, last4 = "5678"), tx(3, bank = "Other Bank"))
        assertEquals(listOf(1L), ids(filterTransactionsByAccount(rows, "Test Bank_1234")))
    }

    @Test
    fun `no account filter keeps every row`() {
        val rows = listOf(tx(1), tx(2, last4 = "5678"))
        assertEquals(rows, filterTransactionsByAccount(rows, null))
    }

    // ─── Tag ─────────────────────────────────────────────────────────────────

    private val tagged = mapOf(1L to listOf("Home", "Work Trip"), 2L to listOf("Work"))

    @Test
    fun `the tag filter matches the whole tag and ignores case`() {
        val rows = listOf(tx(1), tx(2))
        assertEquals(listOf(1L), ids(filterByTag(rows, "work trip", tagged)))
    }

    @Test
    fun `a partial tag does not match`() {
        assertEquals(emptyList<Long>(), ids(filterByTag(listOf(tx(1)), "Trip", tagged)))
    }

    @Test
    fun `an untagged row is excluded`() {
        assertEquals(emptyList<Long>(), ids(filterByTag(listOf(tx(3)), "Home", tagged)))
    }

    @Test
    fun `no tag filter keeps every row`() {
        val rows = listOf(tx(1), tx(3))
        assertEquals(rows, filterByTag(rows, null, tagged))
    }

    // ─── Filterable categories ───────────────────────────────────────────────

    @Test
    fun `a split's category is filterable`() {
        assertEquals(listOf("Food", "Groceries"), filterableCategories(listOf(tx(1)), listOf(split(1, "Groceries")), emptyMap()))
    }

    @Test
    fun `the parent of a present sub-category is filterable`() {
        assertEquals(listOf("Living", "Rent"), filterableCategories(listOf(tx(1, "Rent")), emptyList(), mapOf("Rent" to "Living")))
    }

    @Test
    fun `splits of rows outside the list do not count`() {
        assertEquals(listOf("Food"), filterableCategories(listOf(tx(1)), listOf(split(9, "Groceries")), emptyMap()))
    }

    @Test
    fun `filterable categories are distinct and sorted`() {
        val rows = listOf(tx(1, "Travel"), tx(2, "Food"), tx(3, "Travel"))
        assertEquals(listOf("Food", "Travel"), filterableCategories(rows, listOf(split(2, "Food")), emptyMap()))
    }
}
