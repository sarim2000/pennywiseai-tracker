package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SMSData
import com.pennywiseai.parser.core.test.XMLTestUtils
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.*
import java.math.BigDecimal

class FABXmlTest {

    @TestFactory
    fun `fab parser validates XML driven scenarios`(): List<DynamicTest> {
        val parser = FABParser()
        val allTests = mutableListOf<DynamicTest>()

        val handleChecks = listOf(
            "FAB" to true,
            "FABBANK" to true,
            "AD-FAB-A" to true,
            "HDFC" to false,
            "SBI" to false
        )

        // 1. Handle checks
        allTests.addAll(
            ParserTestUtils.runTestSuite(
                parser = parser,
                testCases = emptyList(),
                handleCases = handleChecks,
                suiteName = "canHandle coverage"
            )
        )

        // 2. XML Driven Transaction Validation
        val smsMessages = XMLTestUtils.loadSMSDataFromResource("fab_sms_test_data_anonymized.xml")
        if (smsMessages.isEmpty()) {
            fail<Unit>("No SMS messages found in fab_sms_test_data_anonymized.xml")
        }

        smsMessages.forEachIndexed { index, smsData ->
            val testName = "XML Message ${index + 1}: ${smsData.description}"
            allTests.add(DynamicTest.dynamicTest(testName) {
                val parsed = parser.parse(smsData.body, smsData.sender, smsData.timestamp)
                val shouldParse = parser.shouldParseTransactionMessage(smsData.body)

                if (shouldParse) {
                    Assertions.assertNotNull(parsed, "Parser returned null but message should parse: ${smsData.body}")
                    val validationErrors = validateResult(parsed!!, smsData)
                    Assertions.assertTrue(validationErrors.isEmpty(), validationErrors.joinToString("; "))
                } else {
                    Assertions.assertNull(parsed, "Parser parsed message but should have rejected: ${smsData.body}")
                }
            })
        }

        return allTests
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
