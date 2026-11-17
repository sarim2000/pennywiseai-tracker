package com.pennywiseai.tracker.domain.model

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * The amount calculator's arithmetic: `+ − × ÷` and brackets over [BigDecimal],
 * so "12.5+30×2" is exactly 72.50, never 72.4999…
 *
 * Reads the keypad's `÷ × − + ( )` and '.'-normalised amounts, with digits in any
 * script (٣ is 3) and the Arabic decimal mark "٫" for a field's starting value.
 * Unary minus works, so the result can be negative: deciding what a form accepts
 * is the caller's job.
 */
object AmountExpression {
    /** Longest expression read; also what bounds the brackets' nesting depth. */
    const val MAX_LENGTH = 64

    /** Results this large or larger are rejected: no real amount is, and storage has limits. */
    private val MAX_AMOUNT = BigDecimal.TEN.pow(15)

    /** What [check] makes of an expression. */
    sealed interface Outcome {
        data class Amount(val value: BigDecimal) : Outcome

        /** Somewhere it divides by zero. */
        data object DividesByZero : Outcome

        /** The result is [MAX_AMOUNT] or more either way. */
        data object TooLarge : Outcome

        /** Empty, unfinished ("12+", "(1+2") or otherwise not a sum ("1..2"). */
        data object Unfinished : Outcome
    }

    /**
     * [input] evaluated and rounded half-up to [fractionDigits] (the currency's
     * decimals). Null for a malformed or over-long expression, division by
     * zero, or a result of [MAX_AMOUNT] or more either way.
     */
    fun evaluate(input: String, fractionDigits: Int): BigDecimal? =
        (check(input, fractionDigits) as? Outcome.Amount)?.value

    /** Like [evaluate], but says why there's no amount, so the keypad can tell the user. */
    fun check(input: String, fractionDigits: Int): Outcome {
        if (input.length > MAX_LENGTH) return Outcome.Unfinished
        val tokens = normalize(input)
        if (tokens.isEmpty()) return Outcome.Unfinished
        val parser = Parser(tokens)
        val value = try {
            parser.parse()
        } catch (_: ArithmeticException) {
            // Only a result beyond BigDecimal's range gets here.
            return Outcome.TooLarge
        } catch (_: NumberFormatException) {
            null
        }
        return when {
            parser.dividedByZero -> Outcome.DividesByZero
            value == null -> Outcome.Unfinished
            value.abs() >= MAX_AMOUNT -> Outcome.TooLarge
            else -> Outcome.Amount(value.setScale(fractionDigits, RoundingMode.HALF_UP))
        }
    }

    /** ASCII digits, '.', and the operators `+ - * / ( )`; anything unknown is kept so parsing fails. */
    private fun normalize(input: String): String = buildString {
        for (c in input) {
            when {
                c == '٫' -> append('.')
                Character.isDigit(c) -> append('0' + Character.digit(c, 10))
                c == '×' -> append('*')
                c == '÷' -> append('/')
                c == '−' -> append('-')
                else -> append(c)
            }
        }
    }

    /** Recursive descent: expression = term (± term)*, term = factor (×÷ factor)*. */
    private class Parser(private val s: String) {
        private var i = 0
        private val math = MathContext.DECIMAL128

        /** Set when parsing stopped at a division by zero. */
        var dividedByZero = false
            private set

        fun parse(): BigDecimal? = expression()?.takeIf { i == s.length }

        private fun expression(): BigDecimal? {
            var left = term() ?: return null
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val op = s[i++]
                val right = term() ?: return null
                left = if (op == '+') left.add(right, math) else left.subtract(right, math)
            }
            return left
        }

        private fun term(): BigDecimal? {
            var left = factor() ?: return null
            while (i < s.length && (s[i] == '*' || s[i] == '/')) {
                val op = s[i++]
                val right = factor() ?: return null
                left = if (op == '*') {
                    left.multiply(right, math)
                } else {
                    if (right.signum() == 0) {
                        dividedByZero = true
                        return null
                    }
                    left.divide(right, math)
                }
            }
            return left
        }

        private fun factor(): BigDecimal? {
            if (i >= s.length) return null
            return when (s[i]) {
                '-' -> { i++; factor()?.negate() }
                '+' -> { i++; factor() }
                '(' -> {
                    i++
                    val inner = expression() ?: return null
                    if (i >= s.length || s[i] != ')') return null
                    i++
                    inner
                }
                else -> number()
            }
        }

        private fun number(): BigDecimal? {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            val text = s.substring(start, i)
            if (text.isEmpty() || text == "." || text.count { it == '.' } > 1) return null
            return BigDecimal(text)
        }
    }
}
