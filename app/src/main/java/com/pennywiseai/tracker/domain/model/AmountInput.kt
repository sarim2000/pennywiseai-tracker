package com.pennywiseai.tracker.domain.model

import java.math.BigDecimal

/**
 * The text of an amount field that also takes a sum ("120+35"), typed with the
 * operator row above the keyboard. Kept in one form whatever was typed: ASCII
 * digits, '.' as the point, and the operators `+ − × ÷`, so [AmountExpression]
 * reads it and a plain amount is still a plain number.
 */
object AmountInput {
    const val OPERATORS = "+−×÷"

    private val OPERAND = Regex("""\d*\.?\d*""")

    fun hasOperator(text: String): Boolean = text.any { it in OPERATORS }

    /**
     * [input] in the field's form, or null to turn the keystroke away: something
     * that isn't a digit, point or operator, two points in one number, an operator
     * first or straight after another, or a sum past [AmountExpression.MAX_LENGTH].
     * A ',' is the point where [decimalComma], a grouping mark (dropped) otherwise.
     */
    fun normalize(input: String, decimalComma: Boolean): String? {
        val out = StringBuilder()
        for (c in input) {
            when {
                c == '٫' || (c == ',' && decimalComma) -> out.append('.')
                c == ',' || c == '٬' -> Unit
                Character.isDigit(c) -> out.append('0' + Character.digit(c, 10))
                c == '.' -> out.append('.')
                c == '+' -> out.append('+')
                c == '-' || c == '−' -> out.append('−')
                c == '*' || c == '×' -> out.append('×')
                c == '/' || c == '÷' -> out.append('÷')
                else -> return null
            }
        }
        val text = out.toString()
        if (!hasOperator(text)) return text.takeIf { OPERAND.matches(it) }
        if (text.length > AmountExpression.MAX_LENGTH || text.first() in OPERATORS) return null
        val operands = text.split(*OPERATORS.toCharArray())
        // Only the last operand may still be empty: "12+" is being typed, "12++" isn't a sum.
        if (operands.dropLast(1).any { it.isEmpty() }) return null
        return text.takeIf { operands.all { OPERAND.matches(it) } }
    }

    /**
     * [text] with [operator] added at the end, replacing an operator already there
     * so a mistyped one is simply changed. Unchanged while empty: a sum starts with a number.
     */
    fun append(text: String, operator: Char): String = when {
        text.isEmpty() -> text
        text.last() in OPERATORS -> text.dropLast(1) + operator
        text.length >= AmountExpression.MAX_LENGTH -> text
        else -> text + operator
    }

    /** What [text] comes to, rounded to [fractionDigits]: a plain amount or a finished sum, else null. */
    fun value(text: String, fractionDigits: Int): BigDecimal? =
        if (hasOperator(text)) AmountExpression.evaluate(text, fractionDigits) else text.toBigDecimalOrNull()

    /**
     * A sum folded into its result ("120+35" → "155"), leaving a trailing operator out;
     * null when [text] isn't a sum, or doesn't come to an amount above zero.
     */
    fun fold(text: String, fractionDigits: Int): String? {
        if (!hasOperator(text)) return null
        val finished = text.trimEnd { it in OPERATORS }
        val result = value(finished, fractionDigits)?.takeIf { it.signum() > 0 } ?: return null
        return result.stripTrailingZeros().toPlainString()
    }
}
