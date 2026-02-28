package com.pennywiseai.parser.core

import com.pennywiseai.parser.core.bank.JKBankParser
import com.pennywiseai.parser.core.bank.SBIBankParser
import com.pennywiseai.parser.core.bank.SouthIndianBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class UPIReferenceDuplicateTest {

    @TestFactory
    fun `SBI parser extracts reference from UPI credit SMS`(): List<DynamicTest> {
        val parser = SBIBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "SBI UPI credit - account 4821 - Rahul Kumar",
                message = "Dear SBI User, your A/c X4821-credited by Rs.25730 on 160ct25 transfer from RAHUL KUMAR Ref No 528902998134 -SBI",
                sender = "SBIBK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("25730"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "RAHUL KUMAR",
                    accountLast4 = "4821",
                    reference = "528902998134"
                )
            ),
            ParserTestCase(
                name = "SBI UPI credit - account 7377 - ShamAkram",
                message = "Dear SBI User, your A/c X7377-credited by Rs.15000 on 26Dec25 transfer from ShamAkram Ref No 190200588907 -SBI",
                sender = "SBIBK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15000"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "ShamAkram",
                    accountLast4 = "7377",
                    reference = "190200588907"
                )
            )
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            suiteName = "SBI UPI Reference Extraction"
        )
    }

    @TestFactory
    fun `South Indian Bank parser extracts reference from UPI credit SMS`(): List<DynamicTest> {
        val parser = SouthIndianBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "South Indian Bank UPI credit - account 4821 - RahulKU",
                message = "UPI Credit:INR Rs.25730.00 in A/c X4821. Info: UPI/ICICI/528908998134/ RAHULKU on 16-10-25 12:11:27. Final balance is Rs.29990.75 -South Indian Bank",
                sender = "SOUTHINDIANBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("25730.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    accountLast4 = "4821",
                    balance = BigDecimal("29990.75"),
                    reference = "528908998134"
                )
            ),
            ParserTestCase(
                name = "South Indian Bank UPI credit - account 7377 - Sham Ak",
                message = "UPI Credit:INR Rs.15000.00 in A/c X7377. Info: UPI/TGRB/190200588907/ Sham Ak on 26-12-25 19:02:01.Final balance is Rs.34567.67 -South Indian Bank",
                sender = "SIBSMS",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15000.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    accountLast4 = "7377",
                    balance = BigDecimal("34567.67"),
                    reference = "190200588907"
                )
            )
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            suiteName = "South Indian Bank UPI Reference Extraction"
        )
    }

    @Test
    fun `Deduplicator test case 1 - different references (528902 vs 528908)`() {
        val sbiParser = SBIBankParser()
        val southIndianParser = SouthIndianBankParser()

        val timestamp = System.currentTimeMillis()

        val sbiMessage = "Dear SBI User, your A/c X4821-credited by Rs.25730 on 160ct25 transfer from RAHUL KUMAR Ref No 528902998134 -SBI"
        val southIndianMessage = "UPI Credit:INR Rs.25730.00 in A/c X4821. Info: UPI/ICICI/528908998134/ RAHULKU on 16-10-25 12:11:27. Final balance is Rs.29990.75 -South Indian Bank"

        val sbiParsed = sbiParser.parse(sbiMessage, "SBIBK", timestamp)
        val southIndianParsed = southIndianParser.parse(southIndianMessage, "SOUTHINDIANBANK", timestamp)

        Assertions.assertNotNull(sbiParsed, "SBI parsed should not be null")
        Assertions.assertNotNull(southIndianParsed, "South Indian Bank parsed should not be null")

        val sbi = sbiParsed!!
        val sib = southIndianParsed!!

        Assertions.assertEquals("4821", sbi.accountLast4, "SBI account should be 4821")
        Assertions.assertEquals("4821", sib.accountLast4, "SIB account should be 4821")
        Assertions.assertEquals(0, BigDecimal("25730").compareTo(sbi.amount), "SBI amount should be 25730")
        Assertions.assertEquals(0, BigDecimal("25730").compareTo(sib.amount), "SIB amount should be 25730")
        Assertions.assertEquals(TransactionType.INCOME, sbi.type)
        Assertions.assertEquals(TransactionType.INCOME, sib.type)

        Assertions.assertEquals("528902998134", sbi.reference, "SBI reference")
        Assertions.assertEquals("528908998134", sib.reference, "SIB reference")

        Assertions.assertNotEquals(
            sbi.reference, 
            sib.reference,
            "References should be different - needs account+amount+time fallback"
        )

        val isDuplicateByAccountAmountTime = sib.accountLast4 == sbi.accountLast4 &&
            sib.amount.compareTo(sbi.amount) == 0 &&
            sib.type == TransactionType.INCOME

        Assertions.assertTrue(
            isDuplicateByAccountAmountTime,
            "Test 1: Both have same account '4821', amount '25730', type INCOME"
        )
    }

    @Test
    fun `Deduplicator test case 2 - same reference (190200588907)`() {
        val sbiParser = SBIBankParser()
        val southIndianParser = SouthIndianBankParser()

        val timestamp = System.currentTimeMillis()

        val sbiMessage = "Dear SBI User, your A/c X7377-credited by Rs.15000 on 26Dec25 transfer from ShamAkram Ref No 190200588907 -SBI"
        val southIndianMessage = "UPI Credit:INR Rs.15000.00 in A/c X7377. Info: UPI/TGRB/190200588907/ Sham Ak on 26-12-25 19:02:01.Final balance is Rs.34567.67 -South Indian Bank"

        val sbiParsed = sbiParser.parse(sbiMessage, "SBIBK", timestamp)
        val southIndianParsed = southIndianParser.parse(southIndianMessage, "SIBSMS", timestamp)

        Assertions.assertNotNull(sbiParsed, "SBI parsed should not be null")
        Assertions.assertNotNull(southIndianParsed, "South Indian Bank parsed should not be null")

        val sbi = sbiParsed!!
        val sib = southIndianParsed!!

        Assertions.assertEquals("7377", sbi.accountLast4, "SBI account should be 7377")
        Assertions.assertEquals("7377", sib.accountLast4, "SIB account should be 7377")
        Assertions.assertEquals(0, BigDecimal("15000").compareTo(sbi.amount), "SBI amount should be 15000")
        Assertions.assertEquals(0, BigDecimal("15000").compareTo(sib.amount), "SIB amount should be 15000")
        Assertions.assertEquals(TransactionType.INCOME, sbi.type)
        Assertions.assertEquals(TransactionType.INCOME, sib.type)

        Assertions.assertEquals("190200588907", sbi.reference, "SBI reference")
        Assertions.assertEquals("190200588907", sib.reference, "SIB reference")

        val isDuplicateByReference = sbi.reference == sib.reference && 
            sbi.reference != null && sbi.reference!!.isNotBlank()

        Assertions.assertTrue(
            isDuplicateByReference,
            "Test 2: Both have same reference '190200588907'"
        )
    }

    @Test
    fun `JK Bank IMPS duplicate detection test`() {
        val jkBankParser = JKBankParser()

        val timestamp = System.currentTimeMillis()

        val sms1 = "A/C XXXX9651 Credited by INR 5000 at 09:02 by mTFTR/IMPSPSP2AI/IMPSINWARD/5. A/C Bal is INR 6046.22 Cr, Available Bal is INR 6046.22 Cr JK BANK."
        val sms2 = "A/C XXXX9651 Credited by INR 5000 at 09:02 by mTFTR/IMPSPSP2AI/IMPSINWARD/5. A/C Bal is INR 6046.22 Cr, Available Bal is INR 6046.22 Cr JK BANK."
        val sms3 = "IMPS Fund transfer of Rs.5000.00 successfully credited to your A/C XXXX9651 with RRN No.536565960299984 dated 31-12-2025. Amt received from ZERODHA BROKING LIMITED DSCNCNB having A/C No.XXXXXXX0164. JK BANK"

        val parsed1 = jkBankParser.parse(sms1, "JKBANK", timestamp)
        val parsed2 = jkBankParser.parse(sms2, "JKBANK", timestamp)
        val parsed3 = jkBankParser.parse(sms3, "JKBANK", timestamp)

        Assertions.assertNotNull(parsed1, "SMS 1 should parse")
        Assertions.assertNotNull(parsed2, "SMS 2 should parse")
        Assertions.assertNotNull(parsed3, "SMS 3 should parse")

        val p1 = parsed1!!
        val p2 = parsed2!!
        val p3 = parsed3!!

        Assertions.assertEquals("9651", p1.accountLast4, "SMS 1 account")
        Assertions.assertEquals("9651", p2.accountLast4, "SMS 2 account")
        Assertions.assertEquals("9651", p3.accountLast4, "SMS 3 account")

        Assertions.assertEquals(0, BigDecimal("5000").compareTo(p1.amount), "SMS 1 amount")
        Assertions.assertEquals(0, BigDecimal("5000").compareTo(p2.amount), "SMS 2 amount")
        Assertions.assertEquals(0, BigDecimal("5000").compareTo(p3.amount), "SMS 3 amount")

        Assertions.assertEquals(TransactionType.INCOME, p1.type, "SMS 1 type")
        Assertions.assertEquals(TransactionType.INCOME, p2.type, "SMS 2 type")
        Assertions.assertEquals(TransactionType.INCOME, p3.type, "SMS 3 type")

        Assertions.assertEquals(
            p1.transactionHash, p2.transactionHash,
            "SMS 1 and SMS 2 should have same hash (exact duplicate)"
        )

        Assertions.assertEquals("536565960299984", p3.reference, "SMS 3 should have RRN")
    }
}
