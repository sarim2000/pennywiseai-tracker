package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.test.TestResult
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal

class HDFCMandateParsingTest {

    @TestFactory
    fun `HDFC e-mandate merchant extraction`(): List<DynamicTest> {
        val parser = HDFCBankParser()

        return listOf(
            dynamicTest("E-Mandate with 'For X mandate' pattern extracts merchant") {
                val message = """E-Mandate!
Rs.3821.00 will be deducted on 02/03/26, 00:00:00
For Rentomojo mandate
UMN 6cc417efb7894c0593866a09c8716737@ybl
Maintain Balance
-HDFC Bank"""
                val info = parser.parseEMandateSubscription(message)
                assertNotNull(info, "Should parse e-mandate notification")
                assertEquals(BigDecimal("3821.00"), info!!.amount, "Amount mismatch")
                assertEquals("Rentomojo", info.merchant, "Merchant should be 'Rentomojo' without 'mandate' suffix")
                assertEquals("6cc417efb7894c0593866a09c8716737@ybl", info.umn, "UMN mismatch")
            },

            dynamicTest("E-Mandate with 'for X ID:' pattern still works") {
                val message = """E-Mandate!
Rs.299.00 will be deducted on 15/04/26
for StreamingApp ID: abc123
UMN test123@ybl
-HDFC Bank"""
                val info = parser.parseEMandateSubscription(message)
                assertNotNull(info, "Should parse e-mandate notification")
                assertEquals(BigDecimal("299.00"), info!!.amount, "Amount mismatch")
                assertEquals("StreamingApp", info.merchant, "Merchant should be 'StreamingApp'")
            },

            dynamicTest("E-Mandate with 'towards X' pattern still works") {
                val message = """E-Mandate!
Rs.599.00 towards CloudStorage from A/c XX1234
UMN ref456@ybl
-HDFC Bank"""
                val info = parser.parseEMandateSubscription(message)
                assertNotNull(info, "Should parse e-mandate notification")
                assertEquals(BigDecimal("599.00"), info!!.amount, "Amount mismatch")
                assertEquals("CloudStorage", info.merchant, "Merchant should be 'CloudStorage'")
            },

            dynamicTest("E-Mandate notification is not parsed as transaction") {
                val message = """E-Mandate!
Rs.3821.00 will be deducted on 02/03/26, 00:00:00
For Rentomojo mandate
UMN 6cc417efb7894c0593866a09c8716737@ybl
Maintain Balance
-HDFC Bank"""
                val parsed = parser.parse(message, "HDFCBK", System.currentTimeMillis())
                assertNull(parsed, "E-Mandate notification should NOT be parsed as a transaction")
            }
        )
    }

    @TestFactory
    fun `HDFC base parser mandate merchant extraction`(): List<DynamicTest> {
        val parser = HDFCBankParser() // extends BaseIndianBankParser

        return listOf(
            dynamicTest("parseMandateSubscription extracts merchant from 'For X mandate'") {
                val message = """E-Mandate!
Rs.3821.00 will be deducted on 02/03/26, 00:00:00
For Rentomojo mandate
UMN 6cc417efb7894c0593866a09c8716737@ybl
Maintain Balance
-HDFC Bank"""
                val info = parser.parseMandateSubscription(message)
                assertNotNull(info, "Should parse mandate subscription")
                assertEquals(BigDecimal("3821.00"), info!!.amount, "Amount mismatch")
                assertEquals("Rentomojo", info.merchant, "Merchant should be 'Rentomojo'")
            },

            dynamicTest("parseMandateSubscription still works with 'for X ID:' pattern") {
                val message = """E-Mandate!
Rs.149.00 will be deducted on 10/05/26
for MusicApp ID: sub789
-HDFC Bank"""
                val info = parser.parseMandateSubscription(message)
                assertNotNull(info, "Should parse mandate subscription")
                assertEquals(BigDecimal("149.00"), info!!.amount, "Amount mismatch")
                assertEquals("MusicApp", info.merchant, "Merchant should be 'MusicApp'")
            }
        )
    }

    @TestFactory
    fun `HDFC future debit merchant extraction`(): List<DynamicTest> {
        val parser = HDFCBankParser()

        return listOf(
            dynamicTest("parseFutureDebit extracts merchant from 'for X mandate'") {
                val message = """Rs.500.00 will be debited on 15/04/26 for FitnessApp mandate from A/c XX5678
-HDFC Bank"""
                val info = parser.parseFutureDebit(message)
                assertNotNull(info, "Should parse future debit notification")
                assertEquals(BigDecimal("500.00"), info!!.amount, "Amount mismatch")
                assertEquals("FitnessApp", info.merchant, "Merchant should be 'FitnessApp'")
            },

            dynamicTest("parseFutureDebit extracts merchant from 'for X will be'") {
                val message = """Rs.999.00 for PremiumService will be debited on 01/05/26 from A/c XX9012
-HDFC Bank"""
                val info = parser.parseFutureDebit(message)
                assertNotNull(info, "Should parse future debit notification")
                assertEquals(BigDecimal("999.00"), info!!.amount, "Amount mismatch")
                assertEquals("PremiumService", info.merchant, "Merchant should be 'PremiumService'")
            }
        )
    }
}
