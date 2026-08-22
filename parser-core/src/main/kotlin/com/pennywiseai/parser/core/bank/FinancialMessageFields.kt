package com.pennywiseai.parser.core.bank

import java.math.BigDecimal

/** Conservative extraction for explicitly labelled financial fields. */
internal object FinancialMessageFields {

    enum class TransferDirection { INCOMING, OUTGOING }

    fun sarAmount(message: String, labels: Collection<String>): BigDecimal? {
        val value = labelledValue(message, labels) ?: return null
        return sarAmountFromValue(value)
    }

    fun sarSettlementOrAmount(message: String, labels: Collection<String>): BigDecimal? {
        val value = labelledValue(message, labels) ?: return null
        SETTLEMENT_SAR_FIRST.find(value)?.let { return parse(it.groupValues[1]) }
        SETTLEMENT_SAR_LAST.find(value)?.let { return parse(it.groupValues[1]) }
        return sarAmountFromValue(value)
    }

    fun transferDirection(message: String): TransferDirection? {
        val from = labelledValue(message, listOf("From")) != null
        val to = labelledValue(message, listOf("To")) != null
        return when {
            from && !to -> TransferDirection.INCOMING
            to && !from -> TransferDirection.OUTGOING
            else -> null
        }
    }

    private fun labelledValue(message: String, labels: Collection<String>): String? {
        val alternatives = labels.joinToString("|") { Regex.escape(it).replace("\\ ", "\\s+") }
        val pattern = Regex("""^(?:$alternatives)\s*:?\s*(.+?)\s*$""", RegexOption.IGNORE_CASE)
        return message.replace("\r\n", "\n").replace('\r', '\n')
            .lineSequence()
            .map { it.trim() }
            .firstNotNullOfOrNull { pattern.matchEntire(it)?.groupValues?.get(1)?.trim() }
    }

    private fun sarAmountFromValue(value: String): BigDecimal? {
        SAR_FIRST.matchEntire(value)?.let { return parse(it.groupValues[1]) }
        SAR_LAST.matchEntire(value)?.let { return parse(it.groupValues[1]) }
        return null
    }

    private fun parse(raw: String): BigDecimal? = try {
        BigDecimal(raw.replace(",", ""))
    } catch (_: NumberFormatException) {
        null
    }

    private val SAR_FIRST = Regex("""^(?:SAR|SR)\s*([0-9][0-9,]*(?:\.\d{1,2})?)$""", RegexOption.IGNORE_CASE)
    private val SAR_LAST = Regex("""^([0-9][0-9,]*(?:\.\d{1,2})?)\s*(?:SAR|SR)$""", RegexOption.IGNORE_CASE)
    private val SETTLEMENT_SAR_FIRST = Regex("""\(\s*(?:SAR|SR)\s*([0-9][0-9,]*(?:\.\d{1,2})?)\s*\)""", RegexOption.IGNORE_CASE)
    private val SETTLEMENT_SAR_LAST = Regex("""\(\s*([0-9][0-9,]*(?:\.\d{1,2})?)\s*(?:SAR|SR)\s*\)""", RegexOption.IGNORE_CASE)
}
