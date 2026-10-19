package com.pennywiseai.tracker.worker

import com.pennywiseai.tracker.worker.OptimizedSmsReaderWorker.Companion.isUnrecognizedCandidate
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
}
