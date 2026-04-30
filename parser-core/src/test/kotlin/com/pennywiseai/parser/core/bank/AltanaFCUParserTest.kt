package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class AltanaFCUParserTest {

    private val parser = AltanaFCUParser()

    @TestFactory
    fun `altana fcu parser handles key paths`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "Pending debit card charge",
                message = "Pending charge for \$43.92 on 04/24 20:39 CDT at MERCHANT NAME, CITY, ST for Debit Consumer card ending in 1234.",
                sender = "Altana FCU",
                expected = ExpectedTransaction(
                    amount = BigDecimal("43.92"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "MERCHANT NAME, CITY, ST",
                    accountLast4 = "1234",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Pending charge with comma in amount",
                message = "Pending charge for \$1,250.00 on 05/01 12:34 CDT at HOME GOODS STORE, AUSTIN, TX for Debit Consumer card ending in 5678.",
                sender = "8775905546",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1250.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "HOME GOODS STORE, AUSTIN, TX",
                    accountLast4 = "5678",
                    isFromCard = true
                )
            )
        )

        return ParserTestUtils.runTestSuite(parser, cases)
    }

    @TestFactory
    fun `factory resolves altana fcu`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Altana Federal Credit Union",
                sender = "Altana FCU",
                currency = "USD",
                message = "Pending charge for \$43.92 on 04/24 20:39 CDT at MERCHANT NAME, CITY, ST for Debit Consumer card ending in 1234.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("43.92"),
                    currency = "USD",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "Altana Federal Credit Union",
                sender = "8775905546",
                currency = "USD",
                message = "Pending charge for \$10.00 on 04/24 20:39 CDT at MERCHANT, CITY, ST for Debit Consumer card ending in 1234.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "Altana Federal Credit Union",
                sender = "(877) 590-5546",
                currency = "USD",
                message = "Pending charge for \$10.00 on 04/24 20:39 CDT at MERCHANT, CITY, ST for Debit Consumer card ending in 1234.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Factory smoke tests")
    }
}
