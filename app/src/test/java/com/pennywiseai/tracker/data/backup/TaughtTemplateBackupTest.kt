package com.pennywiseai.tracker.data.backup

import com.pennywiseai.parser.core.TemplateField
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.tracker.data.preferences.TaughtTemplatesStore.Companion.toTemplate
import com.pennywiseai.tracker.data.repository.SmsInboxReader
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Taught templates in backups (#279). */
class TaughtTemplateBackupTest {
    private val good = TaughtTemplate(
        id = "t",
        senderKey = "ZQTBNK",
        parts = listOf(TaughtTemplatePart("LITERAL", "Paid "), TaughtTemplatePart("AMOUNT")),
        type = "EXPENSE",
        currency = "SAR",
        bankName = "Zqt Bank"
    )

    @Test
    fun `round trips through a backup`() {
        val json = backupJson.encodeToString(AppPreferences(taughtTemplates = listOf(good)))
        val back = backupJson.decodeFromString<AppPreferences>(json)
        assertEquals(listOf(good), back.taughtTemplates)
        val template = back.taughtTemplates.single().toTemplate()!!
        assertEquals(TemplateField.AMOUNT, template.parts.last().field)
        assertEquals(TransactionType.EXPENSE, template.type)
    }

    @Test
    fun `a backup from before this feature still decodes`() {
        val back = backupJson.decodeFromString<AppPreferences>("{}")
        assertTrue(back.taughtTemplates.isEmpty())
    }

    @Test
    fun `a template missing its keys decodes and is not used`() {
        val back = backupJson.decodeFromString<TaughtTemplate>("{}")
        assertNull(back.toTemplate())
    }

    @Test
    fun `what this version can't understand reads nothing rather than misreads`() {
        assertNotNull(good.toTemplate())
        assertNull("a newer version", good.copy(version = TaughtTemplate.CURRENT_VERSION + 1).toTemplate())
        assertNull("an unknown field", good.copy(parts = good.parts + TaughtTemplatePart("TIP")).toTemplate())
        assertNull("an unknown type", good.copy(type = "GIFT").toTemplate())
        assertNull("an unknown format", good.copy(amountFormat = "SPACE").toTemplate())
        assertNull("no currency, never a default", good.copy(currency = "").toTemplate())
        assertNull("nothing to read", good.copy(parts = listOf(TaughtTemplatePart("LITERAL", "Paid"))).toTemplate())
    }

    @Test
    fun `promotional and government senders aren't offered from the inbox`() {
        val money = "Paid SAR 5.00 at Bar"
        assertTrue(SmsInboxReader.worthOffering("AD-ZQTBNK", money))
        assertTrue(SmsInboxReader.worthOffering("AD-ZQTBNK-S", money))
        assertFalse(SmsInboxReader.worthOffering("AD-ZQTBNK-P", money))
        assertFalse(SmsInboxReader.worthOffering("AD-ZQTBNK-G", money))
        assertFalse(SmsInboxReader.worthOffering("AD-ZQTBNK", "Your parcel arrived"))
    }
}
