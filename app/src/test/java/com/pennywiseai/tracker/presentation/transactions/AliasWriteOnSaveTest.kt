package com.pennywiseai.tracker.presentation.transactions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AliasWriteOnSaveTest {

    @Test
    fun `renaming one transaction's merchant without touching the alias writes nothing`() {
        // "Google Play" (alias "Play Store") → one transaction renamed to "Google AI Pro".
        assertNull(aliasWriteOnSave("Google Play", "Google AI Pro", originalAlias = "Play Store", editedAlias = "Play Store "))
    }

    @Test
    fun `a merchant only re-cased on save keeps its alias under the new spelling`() {
        assertEquals(
            AliasWrite.Set("Google Play", "Play Store"),
            aliasWriteOnSave("GOOGLE PLAY", "Google Play", originalAlias = "Play Store", editedAlias = "Play Store")
        )
        assertNull(aliasWriteOnSave("GOOGLE PLAY", "Google Play", originalAlias = "", editedAlias = ""))
    }

    @Test
    fun `re-casing never overwrites the new spelling's own alias`() {
        assertNull(
            aliasWriteOnSave(
                "GOOGLE PLAY", "Google Play", originalAlias = "Play Store", editedAlias = "Play Store",
                existingAliasForMerchant = "Apps & AI"
            )
        )
    }

    @Test
    fun `an edited alias is written under the saved merchant`() {
        assertEquals(AliasWrite.Set("Google Play", "Play Store"), aliasWriteOnSave("Google Play", "Google Play", "", "Play Store"))
    }

    @Test
    fun `clearing the alias removes it`() {
        assertEquals(AliasWrite.Remove("Google Play"), aliasWriteOnSave("Google Play", "Google Play", "Play Store", "  "))
    }
}
