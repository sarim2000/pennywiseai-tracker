package com.pennywiseai.tracker.data.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class DynamicParserRuleTest {

    // Helper functions to copy the logic we wrote in CreateCustomRuleDialog.kt
    // so we can test the regex generation logic directly.
    private fun generateRegexPattern(
        body: String,
        amount: String,
        merchant: String
    ): RegexGenerationResult {
        val idxAmount = body.indexOf(amount)
        val idxMerchant = body.indexOf(merchant)

        if (idxAmount == -1) {
            return RegexGenerationResult.Error("Amount '$amount' not found")
        }
        if (idxMerchant == -1) {
            return RegexGenerationResult.Error("Merchant '$merchant' not found")
        }

        val pattern: String
        val amountGroupIndex: Int
        val merchantGroupIndex: Int
        val safeMerchantGroup = "((?:(?!\\b(?:bal|balance|ref|txn|transaction|debited|credited|avail|available|limit)\\b).)+?)"

        if (idxAmount < idxMerchant) {
            val part1 = body.substring(0, idxAmount)
            val part2 = body.substring(idxAmount + amount.length, idxMerchant)
            val part3 = body.substring(idxMerchant + merchant.length)
            val part3Robust = getRobustSuffix(part3)

            pattern = if (part3Robust.isNotEmpty()) {
                "^" + Regex.escape(part1) + "([0-9.,]+)" + Regex.escape(part2) + safeMerchantGroup + Regex.escape(part3Robust) + ".*$"
            } else {
                "^" + Regex.escape(part1) + "([0-9.,]+)" + Regex.escape(part2) + safeMerchantGroup + "$"
            }
            amountGroupIndex = 1
            merchantGroupIndex = 2
        } else {
            val part1 = body.substring(0, idxMerchant)
            val part2 = body.substring(idxMerchant + merchant.length, idxAmount)
            val part3 = body.substring(idxAmount + amount.length)
            val part3Robust = getRobustSuffix(part3)

            pattern = if (part3Robust.isNotEmpty()) {
                "^" + Regex.escape(part1) + safeMerchantGroup + Regex.escape(part2) + "([0-9.,]+)" + Regex.escape(part3Robust) + ".*$"
            } else {
                "^" + Regex.escape(part1) + safeMerchantGroup + Regex.escape(part2) + "([0-9.,]+)$"
            }
            merchantGroupIndex = 1
            amountGroupIndex = 2
        }

        return RegexGenerationResult.Success(pattern, amountGroupIndex, merchantGroupIndex)
    }

    private fun getRobustSuffix(part3: String): String {
        if (part3.isEmpty()) return ""
        
        val boundaryIdx = findSentenceBoundary(part3)
        val cleanPart3 = if (boundaryIdx != -1) {
            part3.substring(0, boundaryIdx + 1)
        } else {
            part3
        }

        val firstDigitIdx = cleanPart3.indexOfFirst { it.isDigit() }
        val limit = if (firstDigitIdx != -1) firstDigitIdx else cleanPart3.length
        
        var foundWord = false
        var stopIdx = -1
        for (i in 0 until limit) {
            val char = cleanPart3[i]
            if (char.isLetter()) {
                foundWord = true
            } else if (foundWord && (char.isWhitespace() || char == '.' || char == ',')) {
                stopIdx = i
                break
            }
        }
        val endIdx = if (stopIdx != -1) stopIdx else limit
        return cleanPart3.substring(0, endIdx)
    }

    private fun findSentenceBoundary(text: String): Int {
        val patterns = listOf(". ", "! ", "? ")
        var minIdx = -1
        for (p in patterns) {
            val idx = text.indexOf(p)
            if (idx != -1) {
                if (minIdx == -1 || idx < minIdx) {
                    minIdx = idx
                }
            }
        }
        return minIdx
    }

    sealed class RegexGenerationResult {
        data class Success(val pattern: String, val amountGroupIndex: Int, val merchantGroupIndex: Int) : RegexGenerationResult()
        data class Error(val message: String) : RegexGenerationResult()
    }

    @Test
    fun testRegexGeneration_AmountBeforeMerchant() {
        val rawBody = "UPI: You paid Rs. 150.00 to Starbucks at 10:00 AM."
        val userAmount = "150.00"
        val userMerchant = "Starbucks"

        val result = generateRegexPattern(rawBody, userAmount, userMerchant)
        assertTrue(result is RegexGenerationResult.Success)

        val success = result as RegexGenerationResult.Success
        assertEquals(1, success.amountGroupIndex)
        assertEquals(2, success.merchantGroupIndex)

        // Compile generated pattern
        val regex = Regex(success.pattern, RegexOption.IGNORE_CASE)
        
        // Match against original text
        val matchResult = regex.find(rawBody)
        assertNotNull(matchResult)
        assertEquals("150.00", matchResult!!.groupValues[success.amountGroupIndex])
        assertEquals("Starbucks", matchResult.groupValues[success.merchantGroupIndex])

        // Verify it works for a NEW message using the exact same format
        val newMessage = "UPI: You paid Rs. 350.50 to Spotify at 11:30 PM."
        val newMatch = regex.find(newMessage)
        assertNotNull(newMatch)
        assertEquals("350.50", newMatch!!.groupValues[success.amountGroupIndex])
        assertEquals("Spotify", newMatch.groupValues[success.merchantGroupIndex])
    }

    @Test
    fun testRegexGeneration_MerchantBeforeAmount() {
        val rawBody = "Transaction alert: Swiggy debited Rs. 420 from your account."
        val userAmount = "420"
        val userMerchant = "Swiggy"

        val result = generateRegexPattern(rawBody, userAmount, userMerchant)
        assertTrue(result is RegexGenerationResult.Success)

        val success = result as RegexGenerationResult.Success
        assertEquals(2, success.amountGroupIndex)
        assertEquals(1, success.merchantGroupIndex)

        // Compile generated pattern
        val regex = Regex(success.pattern, RegexOption.IGNORE_CASE)

        // Match against original text
        val matchResult = regex.find(rawBody)
        assertNotNull(matchResult)
        assertEquals("420", matchResult!!.groupValues[success.amountGroupIndex])
        assertEquals("Swiggy", matchResult.groupValues[success.merchantGroupIndex])

        // Verify it works for a new message in the same format
        val newMessage = "Transaction alert: Uber debited Rs. 120 from your account."
        val newMatch = regex.find(newMessage)
        assertNotNull(newMatch)
        assertEquals("120", newMatch!!.groupValues[success.amountGroupIndex])
        assertEquals("Uber", newMatch.groupValues[success.merchantGroupIndex])
    }

    @Test
    fun testRegexGeneration_NotFoundErrors() {
        val rawBody = "You paid Rs. 150 to Swiggy"
        
        // Invalid amount
        val res1 = generateRegexPattern(rawBody, "200", "Swiggy")
        assertTrue(res1 is RegexGenerationResult.Error)

        // Invalid merchant
        val res2 = generateRegexPattern(rawBody, "150", "Zomato")
        assertTrue(res2 is RegexGenerationResult.Error)
    }

    @Test
    fun testRegexGeneration_MultiSentenceAndGreedyGuard() {
        // Test 1: Multi-sentence bank message (truncation at sentence boundary)
        val rawBody = "Paid Rs. 100 to Starbucks. Balance is Rs. 5000."
        val userAmount = "100"
        val userMerchant = "Starbucks"

        val result = generateRegexPattern(rawBody, userAmount, userMerchant)
        assertTrue(result is RegexGenerationResult.Success)

        val success = result as RegexGenerationResult.Success
        val regex = Regex(success.pattern, RegexOption.IGNORE_CASE)

        // Match original text
        val matchResult = regex.find(rawBody)
        assertNotNull(matchResult)
        assertEquals("100", matchResult!!.groupValues[success.amountGroupIndex])
        assertEquals("Starbucks", matchResult.groupValues[success.merchantGroupIndex])

        // Verify it works for a new message with a completely different second sentence/balance!
        val newMessage = "Paid Rs. 200 to Starbucks. Ref: 987654321."
        val newMatch = regex.find(newMessage)
        assertNotNull(newMatch)
        assertEquals("200", newMatch!!.groupValues[success.amountGroupIndex])
        assertEquals("Starbucks", newMatch.groupValues[success.merchantGroupIndex])

        // Test 2: Over-Greedy Match Guard
        // Ensures the regex doesn't swallow words like "Ref" or "Balance" into the merchant name
        val rawBody2 = "Paid 50 to Zomato Balance is 450."
        val result2 = generateRegexPattern(rawBody2, "50", "Zomato")
        assertTrue(result2 is RegexGenerationResult.Success)

        val success2 = result2 as RegexGenerationResult.Success
        val regex2 = Regex(success2.pattern, RegexOption.IGNORE_CASE)

        val matchResult2 = regex2.find(rawBody2)
        assertNotNull(matchResult2)
        assertEquals("50", matchResult2!!.groupValues[success2.amountGroupIndex])
        assertEquals("Zomato", matchResult2.groupValues[success2.merchantGroupIndex])

        val newMessage2 = "Paid 80 to Swiggy Balance is 1000."
        val result3 = generateRegexPattern(newMessage2, "80", "Swiggy")
        assertTrue(result3 is RegexGenerationResult.Success)
        val success3 = result3 as RegexGenerationResult.Success
        val regex3 = Regex(success3.pattern, RegexOption.IGNORE_CASE)
        val matchResult3 = regex3.find(newMessage2)
        assertNotNull(matchResult3)
        assertEquals("80", matchResult3!!.groupValues[success3.amountGroupIndex])
        assertEquals("Swiggy", matchResult3.groupValues[success3.merchantGroupIndex])
    }
}
