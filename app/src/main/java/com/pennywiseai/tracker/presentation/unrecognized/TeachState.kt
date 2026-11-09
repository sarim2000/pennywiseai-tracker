package com.pennywiseai.tracker.presentation.unrecognized

import com.pennywiseai.parser.core.AmountFormat
import com.pennywiseai.parser.core.MessageTemplates
import com.pennywiseai.parser.core.TappableSpan
import com.pennywiseai.parser.core.TeachSelection
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

enum class TeachField { AMOUNT, MERCHANT, ACCOUNT }

/** The one thing still needed before saving, in the order the sheet asks for it. */
enum class TeachMissing { AMOUNT, CURRENCY, FORMAT, DIRECTION }

/** The Teach sheet: a message, what's been tapped in it, and what that adds up to (#279). */
data class TeachState(
    val sender: String,
    val body: String,
    val timestamp: Long,
    val spans: List<TappableSpan>,
    val active: TeachField,
    val amount: IntRange? = null,
    val merchant: IntRange? = null,
    val account: IntRange? = null,
    /** A name typed in, for messages that don't say who it was. Unused while [merchant] is tapped. */
    val merchantTyped: String = "",
    val type: TransactionType? = null,
    val currency: String? = null,
    val amountFormat: AmountFormat? = null,
    val cannotLearn: Boolean = false
) {
    val amountText: String? get() = amount?.let(::textOf)

    /** Null until the separators are settled: a figure shown here is what will be saved. */
    val amountValue: BigDecimal?
        get() {
            val text = amountText ?: return null
            val code = currency ?: return null
            return MessageTemplates.parseAmount(text, amountFormat, code)
        }

    /** True while the separator could mean two things, so the choice stays open to change. */
    val canChooseFormat: Boolean
        get() {
            val text = amountText ?: return false
            val code = currency ?: return false
            return MessageTemplates.isFormatAmbiguous(text, code)
        }

    val isFormatAmbiguous: Boolean get() = canChooseFormat && amountFormat == null

    val merchantName: String
        get() = merchant?.let { textOf(it).split(WHITESPACE).joinToString(" ").trim() } ?: merchantTyped

    val accountDigits: String? get() = account?.let(::textOf)?.takeLast(4)

    /** A few words around the amount, so a balance read by mistake is plain to see. */
    val amountSource: String? get() = amount?.let { textOf(MessageTemplates.contextAround(body, it)) }

    val missing: TeachMissing?
        get() = when {
            amount == null -> TeachMissing.AMOUNT
            currency == null -> TeachMissing.CURRENCY
            isFormatAmbiguous -> TeachMissing.FORMAT
            type == null -> TeachMissing.DIRECTION
            else -> null
        }

    val selection: TeachSelection? get() = amount?.let { TeachSelection(it, merchant, account) }

    private fun textOf(range: IntRange) = body.substring(range.first, range.last + 1)

    /** A tap at [offset] of the message, in terms of the field being filled. */
    fun tap(offset: Int): TeachState {
        val index = spanIndexAt(offset) ?: return this
        val span = spans[index].range
        fieldHolding(span)?.takeIf { it != active }?.let { return copy(active = it) }
        return when (active) {
            TeachField.AMOUNT -> tapAmount(index)
            TeachField.ACCOUNT -> tapAccount(index)
            TeachField.MERCHANT -> tapMerchant(index)
        }.copy(cannotLearn = false)
    }

    private fun tapAmount(index: Int): TeachState {
        val span = spans[index]
        if (!span.isNumber) return this
        return copy(
            amount = span.range,
            currency = MessageTemplates.currencyBeside(body, span.range) ?: currency,
            amountFormat = null,
            active = if (merchant == null) TeachField.MERCHANT else active
        )
    }

    private fun tapAccount(index: Int): TeachState {
        val span = spans[index]
        if (!span.isNumber) return this
        return copy(account = if (account == span.range) null else span.range)
    }

    /** One word starts the name, the next word along adds to it, and a word at either end takes it back off. */
    private fun tapMerchant(index: Int): TeachState {
        val current = merchant
        val first = current?.let { spanIndexContaining(it.first) }
        val last = current?.let { spanIndexContaining(it.last) }
        val range = when {
            first == null || last == null -> index..index
            first == last && index == first -> null
            index == first -> first + 1..last
            index == last -> first..last - 1
            index in first..last -> index..index
            index == last + 1 -> first..index
            index == first - 1 -> index..last
            else -> index..index
        }
        return copy(merchant = range?.let { spans[it.first].range.first..spans[it.last].range.last })
    }

    private fun fieldHolding(span: IntRange): TeachField? = when {
        amount?.overlaps(span) == true -> TeachField.AMOUNT
        account?.overlaps(span) == true -> TeachField.ACCOUNT
        merchant?.overlaps(span) == true -> TeachField.MERCHANT
        else -> null
    }

    private fun spanIndexContaining(position: Int) = spans.indexOfFirst { position in it.range }.takeIf { it >= 0 }

    /** Text layout reports a caret position, which can sit just after the character that was tapped. */
    private fun spanIndexAt(offset: Int): Int? = spanIndexContaining(offset) ?: spanIndexContaining(offset - 1)

    companion object {
        private val WHITESPACE = Regex("""\s+""")

        /** Opens on the first reading of [body], and on the first field that reading left empty. */
        fun forMessage(sender: String, body: String, timestamp: Long): TeachState {
            val guess = MessageTemplates.guess(sender, body)
            return TeachState(
                sender = sender,
                body = body,
                timestamp = timestamp,
                spans = MessageTemplates.tappableSpans(body),
                active = when {
                    guess.amount == null -> TeachField.AMOUNT
                    guess.merchant == null -> TeachField.MERCHANT
                    guess.account == null -> TeachField.ACCOUNT
                    else -> TeachField.AMOUNT
                },
                amount = guess.amount,
                merchant = guess.merchant,
                account = guess.account,
                type = guess.type,
                currency = guess.currency
            )
        }

        private fun IntRange.overlaps(other: IntRange) = first <= other.last && other.first <= last
    }
}
