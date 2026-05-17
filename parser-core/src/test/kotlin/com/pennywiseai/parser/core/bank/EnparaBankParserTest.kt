package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class EnparaBankParserTest {

    private val parser = EnparaBankParser()

    @TestFactory
    fun `enpara parser handles common cases`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "Card spend (Encard) - simple merchant",
                message = "Vadesiz TL hesabınıza bağlı 2589 ile biten Encard'ınızla " +
                        "10/05/2026 tarihinde 105100000024364-OBILET ISTANBUL TR " +
                        "firmasında 520,00 TL tutarında harcama yapıldı.",
                sender = "Enpara",
                expected = ExpectedTransaction(
                    amount = BigDecimal("520.00"),
                    currency = "TRY",
                    type = TransactionType.EXPENSE,
                    merchant = "OBILET ISTANBUL",
                    accountLast4 = "2589",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Card spend (Encard) - hyphenated merchant",
                message = "Vadesiz TL hesabınıza bağlı 2589 ile biten Encard'ınızla " +
                        "11/05/2026 tarihinde 105100000248567-Trendyol - Yemek ISTANBUL TR " +
                        "firmasında 350,00 TL tutarında harcama yapıldı.",
                sender = "Enpara",
                expected = ExpectedTransaction(
                    amount = BigDecimal("350.00"),
                    currency = "TRY",
                    type = TransactionType.EXPENSE,
                    merchant = "Trendyol - Yemek ISTANBUL",
                    accountLast4 = "2589",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Card spend (Encard) - space before dash in reference",
                message = "Vadesiz TL hesabınıza bağlı 2589 ile biten Encard'ınızla " +
                        "11/05/2026 tarihinde 2288088 -SBUX KRK KIRIKKALE PODIU KIRIKKALE TR " +
                        "firmasında 465,00 TL tutarında harcama yapıldı.",
                sender = "Enpara",
                expected = ExpectedTransaction(
                    amount = BigDecimal("465.00"),
                    currency = "TRY",
                    type = TransactionType.EXPENSE,
                    merchant = "SBUX KRK KIRIKKALE PODIU KIRIKKALE",
                    accountLast4 = "2589",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Card spend (Encard) - merchant with slash",
                message = "Vadesiz TL hesabınıza bağlı 2589 ile biten Encard'ınızla " +
                        "11/05/2026 tarihinde 000000003718799-NKOLAY/DIRK ROSSMANN ISTANBUL TR " +
                        "firmasında 528,00 TL tutarında harcama yapıldı.",
                sender = "Enpara",
                expected = ExpectedTransaction(
                    amount = BigDecimal("528.00"),
                    currency = "TRY",
                    type = TransactionType.EXPENSE,
                    merchant = "NKOLAY/DIRK ROSSMANN ISTANBUL",
                    accountLast4 = "2589",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Outgoing FAST transfer with balanceAfter",
                message = "13/05/2026 tarihinde vadesiz TL hesabınızdan Hakan G adlı alıcıya " +
                        "500,00 TL tutarında para transferi (FAST) yapıldı. " +
                        "İşlem sonrası hesap bakiyesi: 1.175,28 TL",
                sender = "Enpara",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "TRY",
                    type = TransactionType.EXPENSE,
                    merchant = "Hakan G",
                    balance = BigDecimal("1175.28"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Incoming FAST transfer with balanceAfter",
                message = "Vadesiz TL hesabınıza 11/05/2026 tarihinde Ismail U tarafından " +
                        "yapılan para transferi (FAST) sonucunda 200,00 TL giriş oldu. " +
                        "İşlem sonrası hesap bakiyesi: 3.231,27 TL",
                sender = "Enpara",
                expected = ExpectedTransaction(
                    amount = BigDecimal("200.00"),
                    currency = "TRY",
                    type = TransactionType.INCOME,
                    merchant = "Ismail U",
                    balance = BigDecimal("3231.27"),
                    isFromCard = false
                )
            )
        )

        val handleChecks = listOf(
            "Enpara" to true,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser,
            cases,
            handleChecks,
            "Enpara Bank Parser"
        )
    }
}
