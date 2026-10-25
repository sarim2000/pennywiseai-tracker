package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class DubaiFirstParserTest {

    @TestFactory
    fun `dubai first card notifications`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "Card transaction notification",
                message = "Card Transaction\nCard ending with: 1111\nAmount: AED 280\nAt: Paymob**TEST TOYS TR\nDate: 01/01/2030\nAvailable Balance: AED 10247.04",
                sender = "DubaiFirst",
                expected = ExpectedTransaction(
                    amount = BigDecimal("280"),
                    currency = "AED",
                    type = TransactionType.CREDIT,
                    merchant = "Paymob**TEST TOYS TR",
                    accountLast4 = "1111",
                    creditLimit = BigDecimal("10247.04"),
                    isFromCard = true,
                    expectNullAccountCurrency = true
                )
            ),
            ParserTestCase(
                name = "Merchant with a hyphen",
                message = hyphenMerchant,
                sender = "DubaiFirst",
                expected = ExpectedTransaction(
                    amount = BigDecimal("58"),
                    currency = "AED",
                    type = TransactionType.CREDIT,
                    merchant = "TEST BURGERS-AL NAHDA",
                    accountLast4 = "1111",
                    creditLimit = BigDecimal("10527.04")
                )
            ),
            ParserTestCase(
                name = "Collapsed notification on one line",
                message = oneLine,
                sender = "DubaiFirst",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1250.50"),
                    currency = "AED",
                    type = TransactionType.CREDIT,
                    merchant = "TEST STORE",
                    accountLast4 = "1111",
                    creditLimit = BigDecimal("9000.00")
                )
            ),
            ParserTestCase(
                name = "Foreign-currency spend keeps the AED limit currency",
                message = foreignSpend,
                sender = "DubaiFirst",
                expected = ExpectedTransaction(
                    amount = BigDecimal("20.00"),
                    currency = "USD",
                    type = TransactionType.CREDIT,
                    merchant = "TEST ONLINE",
                    creditLimit = BigDecimal("8900.00"),
                    accountCurrency = "AED"
                )
            ),
            ParserTestCase(
                name = "Refund is income",
                message = refund,
                sender = "DubaiFirst",
                expected = ExpectedTransaction(
                    amount = BigDecimal("58"),
                    currency = "AED",
                    type = TransactionType.INCOME,
                    merchant = "TEST BURGERS-AL NAHDA"
                )
            ),
            ParserTestCase(
                name = "Non-transaction notification is ignored",
                message = promo,
                sender = "DubaiFirst",
                shouldParse = false
            )
        )
        val handleCases = listOf(
            "DubaiFirst" to true,
            "Dubai First" to true,
            "FAB" to false,
            "" to false
        )
        return ParserTestUtils.runTestSuite(DubaiFirstParser(), cases, handleCases, "Dubai First")
    }

    @TestFactory
    fun `factory routes the notification alias`(): List<DynamicTest> =
        ParserTestUtils.runFactoryTestSuite(
            listOf(
                SimpleTestCase(
                    bankName = "Dubai First",
                    sender = "DubaiFirst",
                    currency = "AED",
                    message = hyphenMerchant,
                    expected = ExpectedTransaction(
                        amount = BigDecimal("58"),
                        currency = "AED",
                        type = TransactionType.CREDIT
                    ),
                    shouldHandle = true
                )
            ),
            "Dubai First factory routing"
        )

    private val hyphenMerchant =
        "Card Transaction\nCard ending with: 1111\nAmount: AED 58\nAt: TEST BURGERS-AL NAHDA\nDate: 01/01/2030\nAvailable Balance: AED 10527.04"
    private val oneLine =
        "Card Transaction Card ending with: 1111 Amount: AED 1,250.50 At: TEST STORE Date: 01/01/2030 Available Balance: AED 9,000.00"
    private val foreignSpend =
        "Card Transaction\nCard ending with: 1111\nAmount: USD 20.00\nAt: TEST ONLINE\nDate: 01/01/2030\nAvailable Balance: AED 8900.00"
    private val refund =
        "Card Transaction Refund\nCard ending with: 1111\nAmount: AED 58\nAt: TEST BURGERS-AL NAHDA\nDate: 01/01/2030\nAvailable Balance: AED 10585.04"
    private val promo =
        "Earn 5% cashback on dining this weekend with your Dubai First card. T&Cs apply."
}
