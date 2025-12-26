package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.*
import java.math.BigDecimal

class AdelFiParserTest {

    private val parser = AdelFiParser()

    @TestFactory
    fun `adelfi credit union parser handles key paths`(): List<DynamicTest> {
        ParserTestUtils.printTestHeader(
            parserName = "AdelFi Credit Union (USA)",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val cases = listOf(
            ParserTestCase(
                name = "Transaction - Tax Service",
                message = "Transaction Alert from AdelFi.\n**1234 had a transaction of (\$15.00). Description: 8042999971 P AND F TAX INC        CITY        CAUS. Date: Dec 19, 2025",
                sender = "42141",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15.00"),
                    currency = "USD",
                    type = TransactionType.CREDIT,
                    merchant = "P AND F TAX INC        CITY        CAUS",
                    accountLast4 = "1234"
                )
            ),
            ParserTestCase(
                name = "Transaction - Amazon Purchase",
                message = "Transaction Alert from AdelFi.\n**1234 had a transaction of (\$33.79). Description: 235251000999657 AMAZON MKTPL*ZX0Q15PH2 Amzn.com/billWAUS. Date: Dec 19, 2025",
                sender = "42141",
                expected = ExpectedTransaction(
                    amount = BigDecimal("33.79"),
                    currency = "USD",
                    type = TransactionType.CREDIT,
                    merchant = "AMAZON MKTPL*ZX0Q15PH2 Amzn.com/billWAUS",
                    accountLast4 = "1234"
                )
            )
        )

        return ParserTestUtils.runTestSuite(parser, cases)
    }

    @TestFactory
    fun `factory resolves adelfi credit union`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "AdelFi",
                sender = "42141",
                currency = "USD",
                message = "Transaction Alert from AdelFi.\n**1234 had a transaction of (\$15.00). Description: 8042999971 P AND F TAX INC        CITY        CAUS. Date: Dec 19, 2025",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15.00"),
                    currency = "USD",
                    type = TransactionType.CREDIT
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "AdelFi",
                sender = "42141",
                currency = "USD",
                message = "Transaction Alert from AdelFi.\n**5678 had a transaction of (\$100.50). Description: 123456789 GROCERY STORE LOCATION CAUS. Date: Dec 20, 2025",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.50"),
                    currency = "USD",
                    type = TransactionType.CREDIT
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Factory smoke tests")
    }
}
