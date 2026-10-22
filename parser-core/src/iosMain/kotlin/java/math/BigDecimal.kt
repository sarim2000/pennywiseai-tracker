package java.math

/**
 * iOS stand-in for the slice of `java.math.BigDecimal` the bank parsers use, so
 * the JVM parser sources (src/main/kotlin) compile unchanged for iOS. Parsers
 * keep `import java.math.BigDecimal`; on the JVM that is the JDK class, here it
 * is this one. Semantics follow the JDK for every call the parsers make
 * (string parsing, scale, HALF_UP rounding, scale-aware equals, compareTo that
 * ignores scale). [BigDecimalShimTest] pins them against JDK outputs.
 *
 * ponytail: unscaled value is a Long. A string with more than 16 integer digits
 * or 18 significant digits is rejected with NumberFormatException (the parser
 * then treats it as "no amount"), which keeps setScale(2) and the hash clear of
 * Long overflow; compareTo falls back to doubles past that. SMS amounts and
 * balances fit with room to spare. Swap to a multiplatform bignum if a parser
 * ever needs more.
 */
class BigDecimal private constructor(
    private val unscaled: Long,
    private val scale: Int
) : Number(), Comparable<BigDecimal> {

    constructor(value: String) : this(parse(value))
    constructor(value: Int) : this(value.toLong(), 0)
    constructor(value: Long) : this(value, 0)

    private constructor(parts: Pair<Long, Int>) : this(parts.first, parts.second)

    fun scale(): Int = scale

    fun signum(): Int = unscaled.compareTo(0L)

    fun negate(): BigDecimal = BigDecimal(Math.negateExact(unscaled), scale)

    fun abs(): BigDecimal = if (unscaled < 0) negate() else this

    fun add(other: BigDecimal): BigDecimal {
        val s = maxOf(scale, other.scale)
        return BigDecimal(Math.addExact(rescaleUp(s), other.rescaleUp(s)), s)
    }

    fun subtract(other: BigDecimal): BigDecimal = add(other.negate())

    fun multiply(other: BigDecimal): BigDecimal =
        BigDecimal(Math.multiplyExact(unscaled, other.unscaled), scale + other.scale)

    /** `divide(other, scale, mode)`: the quotient at [newScale], rounded by [mode]. */
    fun divide(other: BigDecimal, newScale: Int, mode: RoundingMode): BigDecimal {
        if (other.unscaled == 0L) throw ArithmeticException("Division by zero")
        // this / other = (u1 * 10^(newScale - s1 + s2)) / u2, at newScale.
        val shift = newScale - scale + other.scale
        var num = unscaled
        var den = other.unscaled
        if (shift >= 0) num = Math.multiplyExact(num, pow10(shift)) else den = Math.multiplyExact(den, pow10(-shift))
        return BigDecimal(roundedDiv(num, den, mode), newScale)
    }

    fun setScale(newScale: Int): BigDecimal = setScale(newScale, RoundingMode.UNNECESSARY)

    fun setScale(newScale: Int, mode: RoundingMode): BigDecimal = when {
        newScale == scale -> this
        newScale > scale -> BigDecimal(rescaleUp(newScale), newScale)
        else -> BigDecimal(roundedDiv(unscaled, pow10(scale - newScale), mode), newScale)
    }

    fun toPlainString(): String {
        if (scale <= 0) {
            return if (unscaled == 0L) "0" else unscaled.toString() + "0".repeat(-scale)
        }
        val digits = kotlin.math.abs(unscaled).toString().padStart(scale + 1, '0')
        val sign = if (unscaled < 0) "-" else ""
        return sign + digits.dropLast(scale) + "." + digits.takeLast(scale)
    }

    override fun toString(): String = toPlainString()

    override fun compareTo(other: BigDecimal): Int {
        val s = maxOf(scale, other.scale)
        return try {
            rescaleUp(s).compareTo(other.rescaleUp(s))
        } catch (_: ArithmeticException) {
            toDouble().compareTo(other.toDouble()) // only for values too far apart in scale to align in a Long
        }
    }

    /** Scale-aware, like the JDK: 2.0 != 2.00 (use compareTo for value equality). */
    override fun equals(other: Any?): Boolean =
        other is BigDecimal && unscaled == other.unscaled && scale == other.scale

    override fun hashCode(): Int = 31 * unscaled.hashCode() + scale

    override fun toDouble(): Double = toPlainString().toDouble()
    override fun toFloat(): Float = toDouble().toFloat()
    override fun toLong(): Long = setScale(0, RoundingMode.DOWN).unscaled
    override fun toInt(): Int = toLong().toInt()
    override fun toShort(): Short = toLong().toShort()
    override fun toByte(): Byte = toLong().toByte()

    // Kotlin's JVM operator extensions on BigDecimal, as members.
    operator fun plus(other: BigDecimal): BigDecimal = add(other)
    operator fun minus(other: BigDecimal): BigDecimal = subtract(other)
    operator fun times(other: BigDecimal): BigDecimal = multiply(other)
    operator fun div(other: BigDecimal): BigDecimal = divide(other, scale, RoundingMode.HALF_EVEN)
    operator fun unaryMinus(): BigDecimal = negate()

    private fun rescaleUp(newScale: Int): Long =
        if (newScale <= scale) unscaled else Math.multiplyExact(unscaled, pow10(newScale - scale))

    companion object {
        val ZERO = BigDecimal(0L, 0)
        val ONE = BigDecimal(1L, 0)
        val TEN = BigDecimal(10L, 0)

        fun valueOf(value: Long): BigDecimal = BigDecimal(value, 0)
        fun valueOf(value: Double): BigDecimal = BigDecimal(value.toString())

        private val NUMBER = Regex("""([+-]?)(\d*)(?:\.(\d*))?(?:[eE]([+-]?\d+))?""")

        private fun parse(text: String): Pair<Long, Int> {
            // The JDK reads any Unicode digit ("४५०" = 450); fold them to ASCII before toLong().
            val ascii = buildString { text.forEach { append(if (it.isDigit()) '0' + it.digitToInt() else it) } }
            val m = NUMBER.matchEntire(ascii) ?: throw NumberFormatException("Not a decimal: $text")
            val (sign, intPart, fracPart, exp) = m.destructured
            if (intPart.isEmpty() && fracPart.isEmpty()) throw NumberFormatException("Not a decimal: $text")
            val digits = (intPart + fracPart).trimStart('0').ifEmpty { "0" }
            val scale = fracPart.length - (exp.toIntOrNull() ?: 0)
            if (digits.length > 18 || (digits != "0" && digits.length - scale > MAX_INTEGER_DIGITS)) {
                throw NumberFormatException("Too large for the iOS BigDecimal: $text")
            }
            val magnitude = digits.toLong()
            return (if (sign == "-") -magnitude else magnitude) to scale
        }

        private const val MAX_INTEGER_DIGITS = 16

        private fun pow10(n: Int): Long {
            if (n > 18) throw ArithmeticException("Overflow")
            var r = 1L
            repeat(n) { r *= 10 }
            return r
        }

        private fun roundedDiv(num: Long, den: Long, mode: RoundingMode): Long {
            val q = num / den
            val r = num % den
            if (r == 0L) return q
            val sign = if ((num < 0) != (den < 0)) -1 else 1
            val twice = kotlin.math.abs(r) * 2
            val absDen = kotlin.math.abs(den)
            val awayFromZero = when (mode) {
                RoundingMode.UP -> true
                RoundingMode.DOWN -> false
                RoundingMode.CEILING -> sign > 0
                RoundingMode.FLOOR -> sign < 0
                RoundingMode.HALF_UP -> twice >= absDen
                RoundingMode.HALF_DOWN -> twice > absDen
                RoundingMode.HALF_EVEN -> twice > absDen || (twice == absDen && q % 2 != 0L)
                RoundingMode.UNNECESSARY -> throw ArithmeticException("Rounding necessary")
            }
            return if (awayFromZero) q + sign else q
        }
    }
}

enum class RoundingMode { UP, DOWN, CEILING, FLOOR, HALF_UP, HALF_DOWN, HALF_EVEN, UNNECESSARY }

/** `Math.*Exact` from the JDK: throw on overflow instead of wrapping silently. */
private object Math {
    fun addExact(a: Long, b: Long): Long {
        val r = a + b
        if (((a xor r) and (b xor r)) < 0) throw ArithmeticException("Overflow")
        return r
    }

    fun multiplyExact(a: Long, b: Long): Long {
        if (a == 0L || b == 0L) return 0L
        val r = a * b
        if (r / b != a || (a == -1L && b == Long.MIN_VALUE) || (b == -1L && a == Long.MIN_VALUE)) {
            throw ArithmeticException("Overflow")
        }
        return r
    }

    fun negateExact(a: Long): Long {
        if (a == Long.MIN_VALUE) throw ArithmeticException("Overflow")
        return -a
    }
}
