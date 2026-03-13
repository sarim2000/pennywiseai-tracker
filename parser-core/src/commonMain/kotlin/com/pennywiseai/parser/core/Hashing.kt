package com.pennywiseai.parser.core

import kotlin.math.abs
import kotlin.math.sin

private val MD5_SHIFT_AMOUNTS = intArrayOf(
    7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
    5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
    4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
    6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21
)

private val MD5_K = IntArray(64) { i ->
    (abs(sin((i + 1).toDouble())) * 4294967296.0).toLong().toInt()
}

fun md5Hex(input: String): String {
    return md5(input.encodeToByteArray())
        .joinToString("") { byte -> byte.toUByte().toString(16).padStart(2, '0') }
}

private fun md5(input: ByteArray): ByteArray {
    val originalBitLength = input.size.toLong() * 8L
    val paddedLength = ((input.size + 8) / 64 + 1) * 64
    val padded = ByteArray(paddedLength)

    input.copyInto(padded)
    padded[input.size] = 0x80.toByte()
    for (i in 0 until 8) {
        padded[paddedLength - 8 + i] = ((originalBitLength ushr (8 * i)) and 0xff).toByte()
    }

    var a0 = 0x67452301
    var b0 = 0xefcdab89.toInt()
    var c0 = 0x98badcfe.toInt()
    var d0 = 0x10325476

    val words = IntArray(16)
    var offset = 0
    while (offset < padded.size) {
        for (i in 0 until 16) {
            val index = offset + (i * 4)
            words[i] = (padded[index].toInt() and 0xff) or
                ((padded[index + 1].toInt() and 0xff) shl 8) or
                ((padded[index + 2].toInt() and 0xff) shl 16) or
                ((padded[index + 3].toInt() and 0xff) shl 24)
        }

        var a = a0
        var b = b0
        var c = c0
        var d = d0

        for (i in 0 until 64) {
            val (f, g) = when {
                i < 16 -> ((b and c) or (b.inv() and d)) to i
                i < 32 -> ((d and b) or (d.inv() and c)) to ((5 * i + 1) % 16)
                i < 48 -> (b xor c xor d) to ((3 * i + 5) % 16)
                else -> (c xor (b or d.inv())) to ((7 * i) % 16)
            }

            val temp = d
            d = c
            c = b
            val sum = a + f + MD5_K[i] + words[g]
            b += leftRotate(sum, MD5_SHIFT_AMOUNTS[i])
            a = temp
        }

        a0 += a
        b0 += b
        c0 += c
        d0 += d
        offset += 64
    }

    return byteArrayOf(
        a0.toByte(), (a0 ushr 8).toByte(), (a0 ushr 16).toByte(), (a0 ushr 24).toByte(),
        b0.toByte(), (b0 ushr 8).toByte(), (b0 ushr 16).toByte(), (b0 ushr 24).toByte(),
        c0.toByte(), (c0 ushr 8).toByte(), (c0 ushr 16).toByte(), (c0 ushr 24).toByte(),
        d0.toByte(), (d0 ushr 8).toByte(), (d0 ushr 16).toByte(), (d0 ushr 24).toByte()
    )
}

private fun leftRotate(value: Int, amount: Int): Int {
    return (value shl amount) or (value ushr (32 - amount))
}
