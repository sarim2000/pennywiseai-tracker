package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class BankAlEtihadParserTest {

    @TestFactory
    fun `bank al etihad parser handles common cases`(): List<DynamicTest> {
        val parser = BankAlEtihadParser()

        val cases = listOf(
            ParserTestCase(
                name = "Card purchase",
                message = "0.802 JOD at ORACLE IRE.\nCard 123.\nAvailable balance: 0.308 JOD.",
                sender = "BankEtihad",
                expected = ExpectedTransaction(
                    amount = BigDecimal("0.802"),
                    currency = "JOD",
                    type = TransactionType.EXPENSE,
                    merchant = "ORACLE IRE",
                    accountLast4 = "123",
                    balance = BigDecimal("0.308"),
                    isFromCard = true,
                    expectNullAccountCurrency = true
                )
            ),
            ParserTestCase(
                name = "Card purchase, merchant containing dots",
                message = "0.369 JOD at MODAL.COM+.\nCard 123.\nAvailable balance: 1.229 JOD.",
                sender = "BankEtihad",
                expected = ExpectedTransaction(
                    amount = BigDecimal("0.369"),
                    currency = "JOD",
                    type = TransactionType.EXPENSE,
                    merchant = "MODAL.COM+",
                    balance = BigDecimal("1.229")
                )
            ),
            ParserTestCase(
                name = "Refund",
                message = "Refund of 0.100 JOD at CAREEM PLU.\nAvailable balance: 0.100 JOD.",
                sender = "BankEtihad",
                expected = ExpectedTransaction(
                    amount = BigDecimal("0.100"),
                    currency = "JOD",
                    type = TransactionType.INCOME,
                    merchant = "CAREEM PLU",
                    balance = BigDecimal("0.100"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "CliQ transfer in",
                message = "0.800 JOD CliQ transfer from JOHN SMITH.\nAvailable balance: 1.598 JOD.",
                sender = "BankEtihad",
                expected = ExpectedTransaction(
                    amount = BigDecimal("0.800"),
                    currency = "JOD",
                    type = TransactionType.INCOME,
                    merchant = "JOHN SMITH",
                    balance = BigDecimal("1.598")
                )
            ),
            ParserTestCase(
                name = "CliQ transfer out",
                message = "1.202 JOD CliQ transfer to Jane Doe.\nAvailable balance: 0.000 JOD.",
                sender = "BankEtihad",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1.202"),
                    currency = "JOD",
                    type = TransactionType.EXPENSE,
                    merchant = "Jane Doe",
                    balance = BigDecimal("0.000")
                )
            ),
            ParserTestCase(
                name = "Declined transaction is ignored",
                message = "Your transaction on debit card ending with 123 for USD 20 at OPENAI *CHATGPT SUBSCR+14150000000US " +
                    "has been declined due to Insufficient funds",
                sender = "BankEtihad",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Purchase OTP is ignored",
                message = "123456 هو رمز التأكيد OTP لتنفيذ حركة شراء بقيمة EUR 0.85 من Oracle Ire لبطاقتك المنتهية بالأرقام 123. لا تشارك هذا الرمز مع أحد",
                sender = "BankEtihad",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Prepaid card top-up (own money moving) is ignored",
                message = "Top-up your prepaid card 1234**** with 1.000 JOD.",
                sender = "BankEtihad",
                shouldParse = false
            )
        )

        val handleChecks = listOf(
            "BankEtihad" to true,
            "Bank Etihad" to true,
            "Etihad" to false
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleChecks, "Bank al Etihad Parser")
    }
}
