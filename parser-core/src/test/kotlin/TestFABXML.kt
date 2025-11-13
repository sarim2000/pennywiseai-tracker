package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SMSData
import com.pennywiseai.parser.core.test.TestResult
import com.pennywiseai.parser.core.test.XMLTestUtils
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class FABXmlTest {

    @Test
    fun `fab parser validates XML driven scenarios`() {
        val parser = FABParser()

        ParserTestUtils.printTestHeader(
            parserName = "FAB XML Validation",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val handleChecks = listOf(
            "FAB" to true,
            "FABBANK" to true,
            "AD-FAB-A" to true,
            "HDFC" to false,
            "SBI" to false
        )

        ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = emptyList(),
            handleCases = handleChecks,
            suiteName = "canHandle coverage"
        )

        ParserTestUtils.printSectionHeader("XML Driven Transaction Validation")

        val smsMessages = XMLTestUtils.loadSMSDataFromResource("fab_sms_test_data_anonymized.xml")
        if (smsMessages.isEmpty()) {
            ParserTestUtils.printTestSummary(1, 0, 1, listOf("No SMS messages available"))
            fail<Unit>("No SMS messages found in fab_sms_test_data_anonymized.xml")
        }

        val xmlResults = mutableListOf<TestResult>()

        smsMessages.forEachIndexed { index, smsData ->
            val testName = "Message ${index + 1}: ${smsData.description}"
            try {
                val parsed = parser.parse(smsData.body, smsData.sender, smsData.timestamp)
                val shouldParse = parser.shouldParseTransactionMessage(smsData.body)

                val result = when {
                    parsed != null -> {
                        val validationErrors = validateResult(parsed, smsData)
                        if (validationErrors.isEmpty()) {
                            TestResult(
                                name = testName,
                                passed = true,
                                details = "Parsed ${parsed.amount} ${parsed.currency} (${parsed.type})"
                            )
                        } else {
                            TestResult(
                                name = testName,
                                passed = false,
                                error = validationErrors.joinToString("; ")
                            )
                        }
                    }

                    shouldParse -> TestResult(
                        name = testName,
                        passed = false,
                        error = "Parser returned null but message should parse"
                    )

                    else -> TestResult(
                        name = testName,
                        passed = true,
                        details = "Correctly rejected non-transaction message"
                    )
                }

                ParserTestUtils.printTestResult(result)
                xmlResults.add(result)
            } catch (e: Exception) {
                val errorResult = TestResult(
                    name = testName,
                    passed = false,
                    error = "Exception: ${e.message}"
                )
                ParserTestUtils.printTestResult(errorResult)
                xmlResults.add(errorResult)
            }
        }

        val passed = xmlResults.count { it.passed }
        val failed = xmlResults.size - passed
        val failureDetails = xmlResults.filterNot { it.passed }.mapNotNull { it.error }

        ParserTestUtils.printTestSummary(
            totalTests = xmlResults.size,
            passedTests = passed,
            failedTests = failed,
            failureDetails = failureDetails
        )
    }

    private fun validateResult(
        result: com.pennywiseai.parser.core.ParsedTransaction,
        smsData: SMSData
    ): List<String> {
        val errors = mutableListOf<String>()

        if (result.amount <= BigDecimal.ZERO) {
            errors.add("Amount should be positive: ${result.amount}")
        }

        if (result.type == TransactionType.INCOME && smsData.body.contains(
                "Debit",
                ignoreCase = true
            )
        ) {
            errors.add("Debit transaction marked as INCOME")
        }

        if (result.type == TransactionType.EXPENSE && smsData.body.contains(
                "credit",
                ignoreCase = true
            )
        ) {
            errors.add("Credit transaction marked as EXPENSE")
        }

        return errors
    }
}
