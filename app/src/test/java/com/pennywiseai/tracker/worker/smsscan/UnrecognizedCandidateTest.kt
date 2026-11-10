package com.pennywiseai.tracker.worker.smsscan

import com.pennywiseai.parser.core.MessageTemplates
import com.pennywiseai.parser.core.TeachSelection
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.tracker.worker.smsscan.SmsScanClassifier.Companion.isUnrecognizedCandidate
import com.pennywiseai.tracker.worker.smsscan.SmsScanClassifier.Companion.taughtSenderKeeps
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnrecognizedCandidateTest {

    @Test
    fun `indian DLT senders are always kept`() {
        assertTrue(isUnrecognizedCandidate("VM-NEWBNK-S", "Your OTP is 123456"))
        assertTrue(isUnrecognizedCandidate("AD-NEWBNK-T", "anything"))
    }

    @Test
    fun `non-DLT bank sender with a currency amount is kept`() {
        assertTrue(isUnrecognizedCandidate("TestBanka", "Placanje karticom 12,50 EUR. Raspolozivo 100,00 EUR"))
        assertTrue(isUnrecognizedCandidate("TestBanka", "Card payment EUR 12.50 at TEST SHOP"))
        assertTrue(isUnrecognizedCandidate("TestBanka", "Paid €12.50 at TEST SHOP"))
        // Some banks drop the leading zero on small amounts.
        assertTrue(isUnrecognizedCandidate("TestBanka", "Card payment EUR .93 at TEST SHOP"))
        assertTrue(isUnrecognizedCandidate("TestBanka", "Paid € .50 at TEST SHOP"))
    }

    @Test
    fun `OTPs, promos and people are left out`() {
        assertFalse(isUnrecognizedCandidate("TestBanka", "Vas OTP kod je 123456"))
        assertFalse(isUnrecognizedCandidate("TestBanka", "OTP 123456 for login"))
        assertFalse(isUnrecognizedCandidate("TestShop", "Big sale this weekend, visit us"))
        assertFalse(isUnrecognizedCandidate("+38200000000", "I paid 12,50 EUR for lunch"))
    }

    @Test
    fun `a scan keeps a taught sender's new format while it's recent`() {
        val sample = "Paid SAR 50.00 at Juice Bar"
        val templates = listOf(
            MessageTemplates.build(
                id = "t", senderKey = "ZQTBNK", body = sample,
                selection = TeachSelection(amount = 9..13),
                type = TransactionType.EXPENSE, currency = "SAR", bankName = "Zqt Bank"
            )!!
        )
        fun kept(body: String, timestamp: Long) = taughtSenderKeeps(templates, "ZQTBNK", body, timestamp, recentSince = 100L)

        assertTrue("taught shape", kept("Paid SAR 75.00 at Juice Bar", 200L))
        assertTrue("taught shape, however old", kept("Paid SAR 75.00 at Juice Bar", 50L))
        assertTrue("new format", kept("You spent SAR 75.00 today", 200L))
        assertFalse("an old one is no change of format", kept("You spent SAR 75.00 today", 50L))
        assertFalse("not about money", kept("Your parcel has arrived", 200L))
        assertFalse("an OTP", kept("OTP 123456 for your SAR 75.00 payment", 200L))
    }
}
