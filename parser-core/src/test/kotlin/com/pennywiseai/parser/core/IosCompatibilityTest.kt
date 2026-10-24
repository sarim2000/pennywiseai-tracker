package com.pennywiseai.parser.core

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The parser sources also compile for iOS, where parser-core/src/iosMain stands in
 * for the JDK (see docs/adding-bank-parsers.md). CI only builds the JVM, so this
 * catches, on Linux, a parser that would break the iOS build.
 */
class IosCompatibilityTest {
    private val allowedJava = setOf("java.math.BigDecimal", "java.math.RoundingMode", "java.text.Normalizer", "java.util.Currency")
    private val javaName = Regex("""\bjavax?\.[a-z]+\.[A-Z][A-Za-z]*""")
    private val forbidden = listOf(
        "String.format(", ".format(", "Math.", "Character.", "System.", "Locale", ".toPattern(",
        // BigDecimal members the iOS stand-in doesn't have.
        "stripTrailingZeros", ".precision(", ".movePoint", ".remainder(", ".toBigInteger(", ".pow(", ".round(", "ValueExact"
    )

    @Test
    fun `parsers only use JDK APIs the iOS stand-in provides`() {
        val problems = File("src/main/kotlin").walk().filter { it.extension == "kt" }.flatMap { file ->
            file.readLines().withIndex()
                .filterNot { (_, line) -> line.trimStart().let { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") } }
                .flatMap { (i, line) ->
                    val where = "${file.path}:${i + 1}"
                    javaName.findAll(line).map { it.value }.filter { it !in allowedJava }.map { "$where uses $it" } +
                        forbidden.filter { it in line }.map { "$where uses $it" }
                }
        }.toList()
        assertTrue(File("src/main/kotlin").isDirectory, "run from the parser-core project dir")
        assertTrue(problems.isEmpty(), "Not available on iOS (add it to parser-core/src/iosMain or avoid it):\n" + problems.joinToString("\n"))
    }
}
