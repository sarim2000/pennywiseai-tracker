package com.pennywiseai.tracker.domain.loan

import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.LoanShareEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder
import java.util.Currency

/** One person's share when saving a split bill (#346), in the transaction's currency. */
data class SplitShare(val personName: String, val amount: BigDecimal)

/** A saved share with the loan it sits on (null if that loan was deleted). */
data class SplitPerson(val share: LoanShareEntity, val loan: LoanEntity?)

enum class SplitError { NO_PEOPLE, TOO_MANY_PEOPLE, BLANK_NAME, DUPLICATE_PERSON, SHARE_NOT_POSITIVE, OVER_AMOUNT, YOUR_PART_NEGATIVE, NOT_BALANCED }

/**
 * Share math for a split bill. Everything is in the bill's one currency: the
 * shares plus your part always add up to exactly the bill.
 */
object SplitRules {
    const val MAX_PEOPLE = 5

    /** The currency's smallest unit as decimal places (2 for INR, 0 for JPY). */
    fun fractionDigits(currency: String): Int =
        runCatching { Currency.getInstance(currency.uppercase()).defaultFractionDigits }.getOrDefault(2).coerceAtLeast(0)

    /**
     * [total] split equally between [people] and you. Each person's share is
     * rounded down to the currency's smallest unit and you take what's left,
     * so ₹1,000 between you and 2 others is ₹333.33, ₹333.33 and you ₹333.34.
     */
    fun equalShares(total: BigDecimal, people: Int, currency: String): List<BigDecimal> {
        if (people <= 0) return emptyList()
        val each = total.divide(BigDecimal(people + 1), fractionDigits(currency), RoundingMode.DOWN)
        return List(people) { each }
    }

    /** Your part when the others' [shares] are taken out of [total]. */
    fun yourPart(total: BigDecimal, shares: List<BigDecimal>): BigDecimal = total - shares.sum()

    /** What isn't assigned yet: zero when balanced, positive when some is left, negative when over. */
    fun remainder(total: BigDecimal, yours: BigDecimal, shares: List<BigDecimal>): BigDecimal =
        total - yours - shares.sum()

    /** Why [shares] of [total] can't be saved, or null. Your part is what's left of [total]. */
    fun validate(total: BigDecimal, shares: List<SplitShare>): SplitError? = when {
        shares.isEmpty() -> SplitError.NO_PEOPLE
        shares.size > MAX_PEOPLE -> SplitError.TOO_MANY_PEOPLE
        shares.any { it.personName.isBlank() } -> SplitError.BLANK_NAME
        shares.map { it.personName }.toSet().size != shares.size -> SplitError.DUPLICATE_PERSON
        shares.any { it.amount.signum() <= 0 } -> SplitError.SHARE_NOT_POSITIVE
        shares.map { it.amount }.sum() > total -> SplitError.OVER_AMOUNT
        else -> null
    }

    /** [validate] for custom amounts, where you also type [yours]: everything must add up to [total]. */
    fun validateCustom(total: BigDecimal, yours: BigDecimal, shares: List<SplitShare>): SplitError? =
        validate(total, shares) ?: when {
            yours.signum() < 0 -> SplitError.YOUR_PART_NEGATIVE
            remainder(total, yours, shares.map { it.amount }).signum() != 0 -> SplitError.NOT_BALANCED
            else -> null
        }

    private fun List<BigDecimal>.sum(): BigDecimal = fold(BigDecimal.ZERO, BigDecimal::add)
}

/** Asking someone to pay their share over UPI (INR only). */
object UpiRequest {
    // name@handle, e.g. "name.surname@bank". The handle starts with a letter.
    private val UPI_ID = Regex("^[A-Za-z0-9._-]{2,256}@[A-Za-z][A-Za-z0-9]{1,63}$")

    // UPI apps cut long notes; keep the merchant readable.
    private const val MAX_NOTE = 50

    fun isValidUpiId(id: String): Boolean = UPI_ID.matches(id.trim())

    /** `upi://pay` link for [amount] INR to [upiId]; [payeeName] and [note] are optional and encoded. */
    fun payUri(upiId: String, payeeName: String?, amount: BigDecimal, note: String?): String = buildString {
        append("upi://pay?pa=").append(upiId.trim())
        payeeName?.trim()?.takeIf { it.isNotEmpty() }?.let { append("&pn=").append(encode(it)) }
        append("&am=").append(amount.setScale(2, RoundingMode.HALF_UP).toPlainString())
        append("&cu=INR")
        note?.trim()?.takeIf { it.isNotEmpty() }?.let { append("&tn=").append(encode(it.take(MAX_NOTE))) }
    }

    // Percent-encoding with %20 for spaces: some UPI apps show a literal "+".
    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
