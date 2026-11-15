package com.pennywiseai.tracker.billing

import com.pennywiseai.tracker.billing.license.LicenseManager
import com.pennywiseai.tracker.core.entitlement.ProEntitlement
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What `data` sees through [ProEntitlement] is exactly [EntitlementGate]'s state. */
class EntitlementContractsTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private fun gate(
        play: MutableStateFlow<Boolean>,
        initialized: MutableStateFlow<Boolean>,
        license: MutableStateFlow<Boolean?>,
    ): ProEntitlement {
        val source = mockk<EntitlementSource> {
            every { isPro } returns play
            every { isInitialized } returns initialized
        }
        val manager = mockk<LicenseManager> {
            every { resolvedLicenseEntitlement } returns license
            every { isLicensed } returns MutableStateFlow(license.value == true)
        }
        return EntitlementGate(source, manager, scope)
    }

    @Test
    fun `unresolved, free and Pro pass through the contract unchanged`() {
        val play = MutableStateFlow(false)
        val initialized = MutableStateFlow(false)
        val license = MutableStateFlow<Boolean?>(null)
        try {
            val pro = gate(play, initialized, license)
            assertNull(pro.resolvedProEntitlement.value)
            assertFalse(pro.isProEntitled.value)

            initialized.value = true
            license.value = false
            assertEquals(false, pro.resolvedProEntitlement.value)
            assertFalse(pro.isProEntitled.value)

            license.value = true
            assertEquals(true, pro.resolvedProEntitlement.value)
            assertTrue(pro.isProEntitled.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `an always-Pro source (F-Droid) is Pro from the first read`() {
        try {
            val pro = gate(MutableStateFlow(true), MutableStateFlow(true), MutableStateFlow(null))
            assertEquals(true, pro.resolvedProEntitlement.value)
            assertTrue(pro.isProEntitled.value)
        } finally {
            scope.cancel()
        }
    }
}
