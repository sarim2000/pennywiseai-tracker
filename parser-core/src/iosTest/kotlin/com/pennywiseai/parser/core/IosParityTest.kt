package com.pennywiseai.parser.core

import com.pennywiseai.parser.core.bank.BankParserFactory
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.posix.getenv
import java.math.RoundingMode
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every sample in src/test/resources/ios-parity.tsv must parse on iOS exactly as
 * on the JVM (the expected column), so Kotlin/Native's regex engine and the
 * BigDecimal stand-in can't drift unseen. Regenerate the file with IosParityFixture.
 */
class IosParityTest {
    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun parsersMatchTheJvm() {
        val path = getenv("PARITY_FILE")?.toKString() ?: fail("PARITY_FILE not set (see parser-core/build.gradle.kts)")
        val text = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) ?: fail("Can't read $path")
        val rows = text.lines().filter { it.isNotEmpty() }.map { it.split('\t').map(::unescape) }
        val mismatches = rows.mapNotNull { (sender, body, expected) ->
            val actual = digest(sender, body)
            if (actual == expected) null else "$sender | ${body.take(80)}\n  jvm: $expected\n  ios: $actual"
        }
        assertTrue(rows.size > 500, "only ${rows.size} rows read from $path")
        assertTrue(mismatches.isEmpty(), "${mismatches.size}/${rows.size} differ from the JVM:\n" + mismatches.joinToString("\n"))
    }

    // Same as IosParityFixture.digest().
    private fun digest(sender: String, body: String): String = try {
        BankParserFactory.parse(body, sender, 0L)?.run {
            listOf(type, amount.setScale(2, RoundingMode.HALF_UP).toPlainString(), merchant, accountLast4,
                balance?.setScale(2, RoundingMode.HALF_UP)?.toPlainString(), bankName, currency, accountCurrency,
                isFromCard, transactionHash ?: generateTransactionId()).joinToString("|")
        } ?: "NONE"
    } catch (e: Exception) {
        "THROW"
    }

    private fun unescape(s: String) = Regex("""\\([\\tnr])""").replace(s) {
        when (it.groupValues[1]) { "t" -> "\t"; "n" -> "\n"; "r" -> "\r"; else -> "\\" }
    }
}
