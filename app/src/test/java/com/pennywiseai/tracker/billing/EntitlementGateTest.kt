package com.pennywiseai.tracker.billing

import com.pennywiseai.tracker.billing.license.LicenseManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class EntitlementGateTest {
    @Test fun `startup caches resolve before free state and revocation remains observable`() {
        val play = MutableStateFlow(false)
        val initialized = MutableStateFlow(false)
        val license = MutableStateFlow<Boolean?>(null)
        val source = mockk<EntitlementSource> {
            every { isPro } returns play
            every { isInitialized } returns initialized
        }
        val manager = mockk<LicenseManager> {
            every { resolvedLicenseEntitlement } returns license
            every { isLicensed } returns MutableStateFlow(false)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val gate = EntitlementGate(source, manager, scope)
            assertNull(gate.resolvedProEntitlement.value)
            assertFalse(gate.isProEntitled.value)
            initialized.value = true
            assertNull(gate.resolvedProEntitlement.value)
            license.value = true
            assertEquals(true, gate.resolvedProEntitlement.value)
            assertTrue(gate.isProEntitled.value)
            license.value = false
            assertEquals(false, gate.resolvedProEntitlement.value)
            assertFalse(gate.isProEntitled.value)
            play.value = true
            assertTrue(gate.isProEntitled.value)
            play.value = false
            assertFalse(gate.isProEntitled.value)
        } finally {
            scope.cancel()
        }
    }

    @Test fun `loaded license alone cannot declare free while play cache loads`() {
        val play = MutableStateFlow(false)
        val initialized = MutableStateFlow(false)
        val source = mockk<EntitlementSource> {
            every { isPro } returns play
            every { isInitialized } returns initialized
        }
        val manager = mockk<LicenseManager> {
            every { resolvedLicenseEntitlement } returns MutableStateFlow<Boolean?>(false)
            every { isLicensed } returns MutableStateFlow(false)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val gate = EntitlementGate(source, manager, scope)
            assertNull(gate.resolvedProEntitlement.value)
            play.value = true
            initialized.value = true
            assertTrue(gate.isProEntitled.value)
        } finally {
            scope.cancel()
        }
    }
}
