package com.pennywiseai.tracker.debug

import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.random.Random

internal data class DemoAccount(
    val bank: String,
    val last4: String,
    val currency: String,
    val profileId: Long = ProfileEntity.PERSONAL_ID,
    val purchaseType: TransactionType = TransactionType.EXPENSE
)

// Keep the extra traffic out of categories with pinned Chat and budget expectations.
private val chartMerchants = listOf(
    "Demo Fuel" to "Transportation",
    "Demo Rail" to "Transportation",
    "Demo Concert Hall" to "Entertainment",
    "Demo Cinema" to "Entertainment",
    "Demo Clinic" to "Healthcare",
    "Demo Pharmacy" to "Healthcare",
    "Demo Salon" to "Personal Care",
    "Demo Mobile" to "Mobile",
    "Demo Fitness" to "Fitness",
    "Demo Parking" to "Transportation",
    "Demo Workshops" to "Education",
    "Demo Museum" to "Travel"
)

internal fun demoChartTransactions(today: LocalDate, accounts: List<DemoAccount>): List<TransactionEntity> = buildList {
    val random = Random(734)
    var day = today.withDayOfMonth(1).minusMonths(11)
    while (day <= today) {
        val posted = day.atStartOfDay()
        repeat(random.nextInt(9, 17)) { index ->
            val (merchant, category) = chartMerchants.random(random)
            val account = accounts.random(random)
            val amount = BigDecimal.valueOf(random.nextLong(250, 2200L + (day.monthValue % 4) * 350), 2)
            add(TransactionEntity(
                amount = amount,
                merchantName = merchant,
                category = category,
                transactionType = account.purchaseType,
                dateTime = if (day == today) posted else posted.plusMinutes(random.nextLong(420, 1320)),
                createdAt = posted,
                updatedAt = posted,
                bankName = account.bank,
                accountNumber = account.last4,
                currency = account.currency,
                profileId = account.profileId,
                transactionHash = "${DEMO_PREFIX}chart-$day-$index"
            ))
        }
        day = day.plusDays(1)
    }
}
