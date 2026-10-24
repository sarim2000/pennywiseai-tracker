package com.pennywiseai.parser.core.bank

import java.math.BigDecimal

// The JVM gets these from kotlin.text (default-imported). On iOS they live in
// the parsers' own packages so the unchanged parser sources resolve them.
// Twin file: com/pennywiseai/parser/core/BigDecimalExtensions.kt.

internal fun String.toBigDecimal(): BigDecimal = BigDecimal(this)

internal fun String.toBigDecimalOrNull(): BigDecimal? =
    try { BigDecimal(this) } catch (_: NumberFormatException) { null }

internal fun Int.toBigDecimal(): BigDecimal = BigDecimal(this)
internal fun Long.toBigDecimal(): BigDecimal = BigDecimal(this)
internal fun Double.toBigDecimal(): BigDecimal = BigDecimal(this.toString())
