package com.pennywiseai.tracker.receiver

import com.pennywiseai.parser.core.bank.BankParserFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The allowlist maps an app's package to a sender alias, and that alias is what
 * the notification listener hands to BankParserFactory. Nothing else ties the two
 * together, so a renamed parser or a typo'd alias would silently drop every
 * notification for that bank.
 */
class BankNotificationConfigTest {

    private val expectedBankByPackage = mapOf(
        "com.avanza.ambitwizfbl" to "Faysal Bank",
        "finansbank.enpara" to "Enpara",
        "com.enparabank.retail" to "Enpara",
        "com.huntington.m" to "Huntington Bank"
    )

    @Test
    fun `every allowed package routes to its bank's parser`() {
        expectedBankByPackage.forEach { (pkg, bank) ->
            assertTrue("$pkg should be allowed", BankNotificationConfig.isAllowed(pkg))
            val parser = BankParserFactory.getParser(BankNotificationConfig.senderAlias(pkg))
            assertEquals("$pkg should route to $bank", bank, parser?.getBankName())
        }
    }

    @Test
    fun `huntington notification parses like its text`() {
        val alias = BankNotificationConfig.senderAlias("com.huntington.m")
        val body = "Huntington Heads Up. We processed an ATM withdrawal: \$17.07 at TEST ATM. " +
            "Acct CK1234 has a \$500.00 bal (10/01/26 7:13 AM ET)."
        val parsed = BankParserFactory.getParser(alias)?.parse(body, alias, 0L)
        assertEquals("17.07", parsed?.amount?.toPlainString())
        assertEquals("USD", parsed?.currency)
    }

    @Test
    fun `unlisted packages are not read`() {
        assertFalse(BankNotificationConfig.isAllowed("com.whatsapp"))
    }
}
