package com.pennywiseai.parser.core

import com.pennywiseai.parser.core.bank.BankParserFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.io.File
import java.math.RoundingMode

/**
 * Writes the JVM's answers into src/test/resources/ios-parity.tsv, which
 * IosParityTest (iosTest, Mac only) checks iOS against. Off by default; after a
 * parser change on purpose alters a sample, regenerate:
 *   PARITY_REGEN=1 ./gradlew :parser-core:jvmTest --tests '*IosParityFixture*'
 * Columns: sender, body, expected (\\, \t, \n escaped). Keep digest() in step
 * with the copy in IosParityTest.
 */
class IosParityFixture {
    @Test
    @EnabledIfEnvironmentVariable(named = "PARITY_REGEN", matches = "1")
    fun regenerate() {
        val file = File("src/test/resources/ios-parity.tsv")
        file.writeText(file.readLines().filter { it.isNotEmpty() }.joinToString("\n", postfix = "\n") { line ->
            val (sender, body) = line.split('\t').map(::unescape)
            "${escape(sender)}\t${escape(body)}\t${escape(digest(sender, body))}"
        })
    }

    private fun digest(sender: String, body: String): String = try {
        BankParserFactory.parse(body, sender, 0L)?.run {
            listOf(type, amount.setScale(2, RoundingMode.HALF_UP).toPlainString(), merchant, accountLast4,
                balance?.setScale(2, RoundingMode.HALF_UP)?.toPlainString(), bankName, currency, accountCurrency,
                isFromCard, transactionHash ?: generateTransactionId()).joinToString("|")
        } ?: "NONE"
    } catch (e: Exception) {
        "THROW"
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")
    private fun unescape(s: String) = Regex("""\\([\\tnr])""").replace(s) {
        when (it.groupValues[1]) { "t" -> "\t"; "n" -> "\n"; "r" -> "\r"; else -> "\\" }
    }
}
