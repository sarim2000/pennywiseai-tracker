package com.pennywiseai.tracker.data.preferences

import com.pennywiseai.parser.core.bank.PNBBankParser
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.tracker.data.backup.AppPreferences
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class BankAccountMergeStoreTest {
    private fun account(bank: String, suffix: String, currency: String = "INR", card: Boolean = false) =
        AccountBalanceEntity(bankName = bank, accountLast4 = suffix, currency = currency,
            isCreditCard = card, balance = BigDecimal.ZERO, timestamp = LocalDateTime.of(2026, 1, 1, 0, 0))

    @Test fun `confirmed mappings work for multiple banks without crossing bank or currency`() {
        val mappings = mapOf(BankAccountMergeStore.mappingKey("Example Bank", "INR", "000") to "1000",
            BankAccountMergeStore.mappingKey("Another Bank", "INR", "000") to "2000")
        assertEquals("1000", BankAccountMergeStore.resolveSuffix("Example Bank", "INR", "000", mappings))
        assertEquals("2000", BankAccountMergeStore.resolveSuffix("Another Bank", "INR", "000", mappings))
        assertEquals("000", BankAccountMergeStore.resolveSuffix("Other Bank", "INR", "000", mappings))
        assertEquals("000", BankAccountMergeStore.resolveSuffix("Example Bank", "USD", "000", mappings))
        assertEquals("000", BankAccountMergeStore.resolveSuffix("Example Bank", "INR", "000", emptyMap()))
    }

    @Test fun `candidate matching excludes cards ambiguous suffixes and different profiles`() {
        val short = account("Example Bank", "000")
        val full = account("Example Bank", "1000")
        assertEquals(listOf(short to full), BankAccountMergeStore.duplicatePairs(listOf(short, full)))
        assertTrue(BankAccountMergeStore.duplicatePairs(listOf(short, full, account("Example Bank", "2000"))).isEmpty())
        assertTrue(BankAccountMergeStore.duplicatePairs(listOf(short, full.copy(profileId = 2))).isEmpty())
        assertTrue(BankAccountMergeStore.duplicatePairs(listOf(short, full.copy(isCreditCard = true))).isEmpty())
        assertTrue(BankAccountMergeStore.duplicatePairs(listOf(short, full.copy(currency = "USD"))).isEmpty())
        assertTrue(BankAccountMergeStore.duplicatePairs(listOf(short, full.copy(bankName = "Another Bank"))).isEmpty())
    }

    @Test fun `invalid mapping and card identifiers remain unchanged`() {
        val parsed = PNBBankParser().parse("A/c X000 debited INR 125.00 to Example Shop thru UPI:000000000001-PNB", "PNB", 0)!!
        val key = BankAccountMergeStore.mappingKey(parsed.bankName, parsed.currency, "000")
        assertEquals("1000", BankAccountMergeStore.resolve(parsed, mapOf(key to "1000")).accountLast4)
        assertEquals("000", BankAccountMergeStore.resolve(parsed.copy(isFromCard = true), mapOf(key to "1000")).accountLast4)
        assertEquals("000", BankAccountMergeStore.resolve(parsed, mapOf(key to "1999")).accountLast4)
        assertEquals("000", BankAccountMergeStore.resolve(parsed.copy(type = TransactionType.CREDIT), mapOf(key to "1000")).accountLast4)
    }

    @Test fun `later merges retarget compatible aliases and invalidate incompatible targets`() {
        val source = account("Example Bank", "1000")
        val key = BankAccountMergeStore.mappingKey("Example Bank", "INR", "000")
        val otherKey = BankAccountMergeStore.mappingKey("Another Bank", "INR", "000")
        val mappings = mapOf(key to "1000", otherKey to "1000")
        val compatible = BankAccountMergeStore.mappingsAfterMerge(mappings, source, account("Example Bank", "2000"))
        assertEquals("2000", compatible[key])
        assertEquals("1000", compatible[otherKey])
        for (target in listOf(account("Example Bank", "1999"), account("Another Bank", "2000"),
            account("Example Bank", "2000", currency = "USD"), account("Example Bank", "2000", card = true))) {
            val invalidated = BankAccountMergeStore.mappingsAfterMerge(mappings, source, target)
            assertFalse(invalidated.containsKey(key))
            assertEquals("1000", invalidated[otherKey])
            assertEquals("000", BankAccountMergeStore.resolveSuffix("Example Bank", "INR", "000", invalidated))
        }
    }

    @Test fun `merge mappings roundtrip in backup and older backups have no mappings`() {
        val preferences = AppPreferences(bankAccountMerges = mapOf("Example Bank|INR|000" to "1000"))
        assertEquals(preferences, Json.decodeFromString<AppPreferences>(Json.encodeToString(preferences)))
        assertTrue(Json.decodeFromString<AppPreferences>("{}").bankAccountMerges.isEmpty())
    }
}
