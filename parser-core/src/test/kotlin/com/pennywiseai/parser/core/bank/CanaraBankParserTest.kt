package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class CanaraBankParserTest {

    private val parser = CanaraBankParser()

    @TestFactory
    fun `canara bank parser handles key paths`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "RTGS MF redemption credit typed as INCOME",
                message = "An amount of INR 13,30,614.75 has been credited to XXXX6785 on 02/12/2025 towards RTGS by Sender AXIS MUTUAL FUND REDEMPTION PO, IFSC UTIB0000004, Sender A/c XXXX9108, AXIS BANK, MUMBAI BRANCH, UTR UTIBR72025120200011461, Total Avail. Bal INR 2679815.88- Canara Bank",
                sender = "VA-CANBNK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1330614.75"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "AXIS MUTUAL FUND REDEMPTION PO",
                    accountLast4 = "9108"
                )
            )
        )

        return ParserTestUtils.runTestSuite(parser, cases)
    }
}
