package com.pennywiseai.parser.core.test

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.BankParser
import com.pennywiseai.parser.core.bank.BankParserFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
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
        val errors = mutableListOf<String>()
        if (result.amount != expected.amount) errors.add("Amount mismatch: expected ${expected.amount}, got ${result.amount}")
        if (result.currency != expected.currency) errors.add("Currency mismatch: expected ${expected.currency}, got ${result.currency}")
        if (result.type != expected.type) errors.add("Transaction type mismatch: expected ${expected.type}, got ${result.type}")

        expected.merchant?.let { if (result.merchant != it) errors.add("Merchant mismatch: expected $it, got ${result.merchant}") }
        expected.reference?.let { if (result.reference != it) errors.add("Reference mismatch: expected $it, got ${result.reference}") }
        expected.accountLast4?.let { if (result.accountLast4 != it) errors.add("Account mismatch: expected $it, got ${result.accountLast4}") }
        expected.balance?.let { if (result.balance != it) errors.add("Balance mismatch: expected $it, got ${result.balance}") }
        expected.creditLimit?.let { if (result.creditLimit != it) errors.add("Credit limit mismatch: expected $it, got ${result.creditLimit}") }
        expected.isFromCard?.let { if (result.isFromCard != it) errors.add("isFromCard mismatch: expected $it, got ${result.isFromCard}") }
        expected.fromAccount?.let { if (result.fromAccount != it) errors.add("From account mismatch: expected $it, got ${result.fromAccount}") }
        expected.toAccount?.let { if (result.toAccount != it) errors.add("To account mismatch: expected $it, got ${result.toAccount}") }

        return errors
    }

    fun validateResultDynamic(parsed: ParsedTransaction, expected: ExpectedTransaction) {
        assertAll(
            "Transaction Details",
            { assertEquals(expected.amount, parsed.amount, "Amount mismatch") },
            { assertEquals(expected.currency, parsed.currency, "Currency mismatch") },
            { assertEquals(expected.type, parsed.type, "Transaction type mismatch") },

            {
                expected.merchant?.let {
                    assertEquals(it, parsed.merchant, "Merchant mismatch")
                }
            },
            {
                expected.reference?.let {
                    assertEquals(it, parsed.reference, "Reference mismatch")
                }
            },
            {
                expected.accountLast4?.let {
                    assertEquals(it, parsed.accountLast4, "Account mismatch")
                }
            },
            {
                expected.balance?.let {
                    assertEquals(it, parsed.balance, "Balance mismatch")
                }
            },
            {
                expected.creditLimit?.let {
                    assertEquals(it, parsed.creditLimit, "Credit limit mismatch")
                }
            },
            {
                expected.isFromCard?.let {
                    assertEquals(it, parsed.isFromCard, "isFromCard mismatch")
                }
            },
            {
                expected.fromAccount?.let {
                    assertEquals(it, parsed.fromAccount, "From account mismatch")
                }
            },
            {
                expected.toAccount?.let {
                    assertEquals(it, parsed.toAccount, "To account mismatch")
                }
            }
        )
    }

    fun runTestSuite(
        parser: BankParser,
        testCases: List<ParserTestCase>,
        handleCases: List<Pair<String, Boolean>> = emptyList(),
        suiteName: String = ""
    ): List<DynamicTest> {
        val tests = mutableListOf<DynamicTest>()

        testCases.forEach { testCase ->
            tests.add(dynamicTest(testCase.name) {
                val parsed = parser.parse(testCase.message, testCase.sender, System.currentTimeMillis())

                if (testCase.shouldParse) {
                    assertNotNull(parsed, "Parser returned null but expected to parse: ${testCase.message}")
                    testCase.expected?.let { validateResultDynamic(parsed!!, it) }
                } else {
                    assertNull(parsed, "Parser parsed message but should have rejected: $parsed")
                }
            })
        }

        handleCases.forEach { (sender, shouldHandle) ->
            tests.add(dynamicTest("Handle check: $sender") {
                assertEquals(shouldHandle, parser.canHandle(sender), "canHandle mismatch for $sender")
            })
        }

        return tests
    }

    fun runFactoryTestSuite(testCases: List<SimpleTestCase>, suiteName: String = ""): List<DynamicTest> {
        return testCases.mapIndexed { index, testCase ->
            val displayName = testCase.description.ifBlank {
                "${index + 1}. ${testCase.bankName} (${testCase.sender})"
            }

            dynamicTest(displayName) {
                val parser = BankParserFactory.getParser(testCase.sender)

                if (testCase.shouldParse) {
                    assertNotNull(parser, "Factory returned null for sender '${testCase.sender}'")
                    assertEquals(testCase.bankName, parser!!.getBankName(), "Bank name mismatch")
                    assertEquals(testCase.currency, parser.getCurrency(), "Currency mismatch")

                    testCase.shouldHandle?.let { expectedHandle ->
                        assertTrue(parser.canHandle(testCase.sender) == expectedHandle, "canHandle mismatch")
                    }

                    val parsed = parser.parse(testCase.message, testCase.sender, System.currentTimeMillis())
                    assertNotNull(parsed, "Parser returned null but expected to parse message")
                    testCase.expected?.let { validateResultDynamic(parsed!!, it) }
                } else {
                    // If it shouldn't parse, it either returns null parser or the parser should return null
                    if (parser != null) {
                        val parsed = parser.parse(testCase.message, testCase.sender, System.currentTimeMillis())
                        assertNull(parsed, "Parser parsed message but should have rejected: $parsed")
                    }
                }
            }
        }
    }

    // Deprecated methods for compatibility during migration
    @Deprecated("Use runTestSuite with @TestFactory")
    fun runTestSuiteLegacy(parser: BankParser, testCases: List<ParserTestCase>, handleCases: List<Pair<String, Boolean>> = emptyList()) {
        testCases.forEach { testCase ->
            val parsed = parser.parse(testCase.message, testCase.sender, System.currentTimeMillis())
            if (testCase.shouldParse) {
                assertNotNull(parsed)
                testCase.expected?.let { validateResultDynamic(parsed!!, it) }
            } else {
                assertNull(parsed)
            }
        }
    }

    // These print methods are now no-ops to support legacy test code without failing compilation
    fun printTestHeader(parserName: String = "", bankName: String = "", currency: String = "", additionalInfo: String = "") {}
    fun printSectionHeader(title: String) {}
    fun printTestResult(result: TestResult, showDetails: Boolean = true) {}
    fun printTestSummaryFromResults(results: List<TestResult>) {}
    fun printTestSummary(totalTests: Int, passedTests: Int, failedTests: Int, failureDetails: List<String> = emptyList()) {}
}