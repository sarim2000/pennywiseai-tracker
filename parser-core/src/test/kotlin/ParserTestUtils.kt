package com.pennywiseai.parser.core.test

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.BankParser
import com.pennywiseai.parser.core.bank.BankParserFactory
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.function.Executable
import java.math.BigDecimal

// ========================================
// Data Classes
// ========================================

data class ExpectedTransaction(
    val amount: BigDecimal,
    val currency: String,
    val type: TransactionType,
    val merchant: String? = null,
    val reference: String? = null,
    val accountLast4: String? = null,
    val balance: BigDecimal? = null,
    val creditLimit: BigDecimal? = null,
    val isFromCard: Boolean? = null,
    val fromAccount: String? = null,
    val toAccount: String? = null
)

data class ParserTestCase(
    val name: String,
    val message: String,
    val sender: String,
    val expected: ExpectedTransaction? = null,
    val shouldParse: Boolean = true,
    val description: String = ""
)

data class SimpleTestCase(
    val bankName: String,
    val sender: String,
    val currency: String,
    val message: String,
    val expected: ExpectedTransaction? = null,
    val shouldParse: Boolean = true,
    val shouldHandle: Boolean? = null,
    val description: String = ""
)

data class TestResult(
    val name: String,
    val passed: Boolean,
    val error: String? = null,
    val details: String? = null
)

data class TestSuiteResult(
    val totalTests: Int,
    val passedTests: Int,
    val failedTests: Int,
    val results: List<TestResult>,
    val failureDetails: List<String>
)

// ========================================
// Main Test Utilities
// ========================================

object ParserTestUtils {

    fun runSingleTest(parser: BankParser, testCase: ParserTestCase): TestResult {
        val parsed = parser.parse(testCase.message, testCase.sender, System.currentTimeMillis())

        return when {
            parsed == null && testCase.shouldParse -> TestResult(
                testCase.name, false,
                "Parser returned null but expected to parse: ${testCase.message.take(100)}..."
            )

            parsed != null && !testCase.shouldParse -> TestResult(
                testCase.name, false,
                "Parser parsed message but should have rejected: ${parsed.amount} ${parsed.currency}"
            )

            parsed == null -> TestResult(
                testCase.name, true,
                details = "Correctly rejected non-transaction message"
            )

            testCase.expected == null -> TestResult(
                testCase.name, true,
                details = "Parsed ${parsed.amount} ${parsed.currency} (${parsed.type})"
            )

            else -> {
                val errors = validateResult(parsed, testCase.expected)
                TestResult(
                    testCase.name,
                    errors.isEmpty(),
                    errors.takeIf { it.isNotEmpty() }?.joinToString(", "),
                    if (errors.isEmpty()) "Successfully parsed: ${parsed.amount} ${parsed.currency}" else null
                )
            }
        }
    }

    fun validateResult(result: ParsedTransaction, expected: ExpectedTransaction): List<String> {
        return buildList {
            checkField(result.amount, expected.amount, "Amount")
            checkField(result.currency, expected.currency, "Currency")
            checkField(result.type, expected.type, "Transaction type")

            expected.merchant?.let { checkField(result.merchant, it, "Merchant") { "'$it'" } }
            expected.reference?.let { checkField(result.reference, it, "Reference") { "'$it'" } }
            expected.accountLast4?.let {
                checkField(
                    result.accountLast4,
                    it,
                    "Account"
                ) { "'$it'" }
            }
            expected.balance?.let { checkField(result.balance, it, "Balance") }
            expected.creditLimit?.let { checkField(result.creditLimit, it, "Credit limit") }
            expected.isFromCard?.let { checkField(result.isFromCard, it, "isFromCard") }
            expected.fromAccount?.let {
                checkField(
                    result.fromAccount,
                    it,
                    "From account"
                ) { "'$it'" }
            }
            expected.toAccount?.let { checkField(result.toAccount, it, "To account") { "'$it'" } }
        }
    }

    fun runTestSuite(
        parser: BankParser,
        testCases: List<ParserTestCase>,
        handleCases: List<Pair<String, Boolean>> = emptyList(),
        suiteName: String = ""
    ) {
        if (suiteName.isNotEmpty()) printSectionHeader(suiteName)

        val results = mutableListOf<TestResult>()

        testCases.forEach { testCase ->
            runSingleTest(parser, testCase).also {
                printTestResult(it)
                results.add(it)
            }
        }

        handleCases.forEach { (sender, shouldHandle) ->
            val canHandle = parser.canHandle(sender)
            val result = TestResult(
                name = "Handle check for sender '$sender'",
                passed = canHandle == shouldHandle,
                error = if (canHandle != shouldHandle) {
                    if (shouldHandle) "Parser should handle sender '$sender' but did not."
                    else "Parser should not handle sender '$sender' but did."
                } else null,
                details = if (canHandle == shouldHandle) {
                    if (canHandle) "Correctly handles sender '$sender'"
                    else "Correctly does not handle sender '$sender'"
                } else null
            )
            printTestResult(result, showDetails = !result.passed)
            results.add(result)
        }
        printTestSummaryFromResults(results)
    }

    fun runFactoryTestSuite(testCases: List<SimpleTestCase>, suiteName: String = "") {
        if (suiteName.isNotEmpty()) printSectionHeader(suiteName)

        val results = testCases.mapIndexed { index, testCase ->
            val displayName = testCase.description.ifBlank {
                "${index + 1}. ${testCase.bankName} (${testCase.sender})"
            }

            val parser = BankParserFactory.getParser(testCase.sender)

            val result = when {
                parser == null && !testCase.shouldParse -> TestResult(
                    displayName, true,
                    details = "Correctly returned null parser for sender '${testCase.sender}'"
                )

                parser == null -> TestResult(
                    displayName, false,
                    "Factory returned null for sender '${testCase.sender}'"
                )

                else -> validateFactoryParser(parser, testCase, displayName)
            }

            printTestResult(result)
            result
        }
        printTestSummaryFromResults(results)
    }

    fun printTestHeader(
        parserName: String,
        bankName: String,
        currency: String,
        additionalInfo: String = ""
    ) {
        val sep = "=".repeat(80)
        println(sep)
        println("$parserName Test Suite$additionalInfo")
        println(sep)
        println("Bank Name: $bankName")
        println("Currency: $currency")
        println()
    }

    fun printSectionHeader(sectionName: String) {
        println("=== $sectionName ===\n")
    }

    fun printTestResult(result: TestResult, showDetails: Boolean = true) {
        println("${if (result.passed) "✓ PASSED" else "✗ FAILED"}: ${result.name}")
        if (result.passed && showDetails) result.details?.let { println("  $it") }
        if (!result.passed) result.error?.let { println("  Error: $it") }
        println()
    }

    fun printTestSummaryFromResults(results: List<TestResult>) {
        val resultsX = this.createSuiteResult(results, "")
        printTestSummary(
            totalTests = resultsX.totalTests,
            passedTests = resultsX.passedTests,
            failedTests = resultsX.failedTests,
            failureDetails = resultsX.failureDetails
        )
    }

    fun printTestSummary(
        totalTests: Int,
        passedTests: Int,
        failedTests: Int,
        failureDetails: List<String> = emptyList()
    ) {
        val sep = "=".repeat(80)
        println(sep)
        println("Test Summary")
        println(sep)
        println("Total tests: $totalTests")
        println("Passed: $passedTests")
        println("Failed: $failedTests")

        if (totalTests > 0) {
            val successRate = passedTests.toDouble() / totalTests * 100
            println("Success rate: ${"%.2f".format(successRate)}%")

            if (failedTests > 0 && failureDetails.isNotEmpty()) {
                println("\n$sep")
                println("Failure Details")
                println(sep)
                failureDetails.forEach { println("  - $it") }
            }

            val emoji = if (failedTests == 0) "✅" else "❌"
            val status = if (failedTests == 0) "passed" else "failed"
            println("\n$emoji Overall test $status with success rate: ${"%.2f".format(successRate)}%")
        } else {
            println("Success rate: N/A")
        }
    }

    // ========================================
    // Private Helpers
    // ========================================

    private fun <T> MutableList<String>.checkField(
        actual: T,
        expected: T,
        fieldName: String,
        formatter: (T) -> String = { it.toString() }
    ) {
        if (actual != expected) {
            add("$fieldName mismatch: expected ${formatter(expected)}, got ${formatter(actual)}")
        }
    }

    private fun validateFactoryParser(
        parser: BankParser,
        testCase: SimpleTestCase,
        displayName: String
    ): TestResult {
        val errors = mutableListOf<String>()

        if (parser.getBankName() != testCase.bankName) {
            errors.add("Bank name mismatch: expected '${testCase.bankName}', got '${parser.getBankName()}'")
        }
        if (parser.getCurrency() != testCase.currency) {
            errors.add("Currency mismatch: expected '${testCase.currency}', got '${parser.getCurrency()}'")
        }
        testCase.shouldHandle?.let { expectedHandle ->
            if (parser.canHandle(testCase.sender) != expectedHandle) {
                errors.add(
                    if (expectedHandle) "Parser should handle sender '${testCase.sender}' but did not."
                    else "Parser should not handle sender '${testCase.sender}' but did."
                )
            }
        }

        val parseTestCase = ParserTestCase(
            displayName, testCase.message, testCase.sender,
            testCase.expected, testCase.shouldParse, testCase.description
        )
        val parseResult = runSingleTest(parser, parseTestCase)

        if (!parseResult.passed) parseResult.error?.let { errors.add(it) }

        return if (errors.isEmpty()) {
            parseResult.copy(
                details = parseResult.details
                    ?: "Factory matched ${parser.getBankName()} (${parser.getCurrency()})"
            )
        } else {
            TestResult(displayName, false, errors.joinToString("; "))
        }
    }

    private fun createSuiteResult(
        results: List<TestResult>,
        assertionLabel: String
    ): TestSuiteResult {
        val executables = results.map { result ->
            Executable {
                assertTrue(
                    result.passed,
                    { "${result.name}: ${result.error ?: "Test failed"}" }
                )
            }
        }

        if (executables.isNotEmpty()) {
            assertAll(assertionLabel, *executables.toTypedArray())
        }

        val passed = results.count { it.passed }
        val failureDetails = results.filter { !it.passed }.mapNotNull { result ->
            result.error?.let { "${result.name}: $it" }
        }

        return TestSuiteResult(
            totalTests = results.size,
            passedTests = passed,
            failedTests = results.size - passed,
            results = results,
            failureDetails = failureDetails
        )
    }
}