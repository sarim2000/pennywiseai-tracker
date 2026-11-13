package com.pennywiseai.tracker.utils

import androidx.core.content.UnusedAppRestrictionsConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundAccessTest {

    @Test
    fun `detects the manufacturer family from manufacturer or brand`() {
        assertEquals(DeviceOem.SAMSUNG, DeviceOem.detect("samsung", "samsung"))
        assertEquals(DeviceOem.XIAOMI, DeviceOem.detect("Xiaomi", "POCO"))
        assertEquals(DeviceOem.HONOR, DeviceOem.detect("HUAWEI", "HONOR"))
        assertEquals(DeviceOem.HUAWEI, DeviceOem.detect("HUAWEI", "HUAWEI"))
        assertEquals(DeviceOem.OPPO, DeviceOem.detect("realme", "realme"))
        assertEquals(DeviceOem.ONEPLUS, DeviceOem.detect("OnePlus", "OnePlus"))
        assertEquals(DeviceOem.VIVO, DeviceOem.detect("vivo", "iQOO"))
        assertEquals(DeviceOem.TRANSSION, DeviceOem.detect("INFINIX MOBILITY LIMITED", "Infinix"))
    }

    @Test
    fun `stock-like phones get no extra step and unknown ones get the generic hint`() {
        assertEquals(null, DeviceOem.detect("Google", "google"))
        assertEquals(null, DeviceOem.detect("Sony", "Sony"))
        assertEquals(null, DeviceOem.detect("Nothing", "Nothing"))
        assertEquals(DeviceOem.OTHER, DeviceOem.detect("motorola", "motorola"))
    }

    @Test
    fun `steps left counts only the rows that apply and are still open`() {
        val open = BackgroundAccessState(
            batteryUnrestricted = false,
            backgroundRestricted = true,
            unusedRestrictionsStatus = UnusedAppRestrictionsConstants.API_31,
            oem = DeviceOem.SAMSUNG
        )
        assertEquals(3, open.stepsLeft)
        assertEquals(4, open.copy(oem = DeviceOem.XIAOMI).stepsLeft)
        assertEquals(3, open.copy(oem = DeviceOem.XIAOMI, oemConfirmed = true).stepsLeft)

        val done = BackgroundAccessState(
            batteryUnrestricted = true,
            backgroundRestricted = false,
            unusedRestrictionsStatus = UnusedAppRestrictionsConstants.DISABLED,
            oem = null
        )
        assertEquals(0, done.stepsLeft)
    }

    @Test
    fun `battery exemption replaces the Samsung step but not other manufacturers`() {
        val samsung = BackgroundAccessState(batteryUnrestricted = true, oem = DeviceOem.SAMSUNG)
        assertFalse(samsung.showOemStep)
        assertEquals(0, samsung.stepsLeft)
        assertEquals(1, samsung.copy(oem = DeviceOem.XIAOMI).stepsLeft)
    }

    @Test
    fun `on Samsung the battery step and Never sleeping apps count as one step`() {
        val open = BackgroundAccessState(batteryUnrestricted = false, oem = DeviceOem.SAMSUNG)
        assertEquals(1, open.stepsLeft)
        assertTrue(open.showOemStep)

        val confirmed = open.copy(oemConfirmed = true)
        assertFalse(confirmed.showBatteryStep)
        assertEquals(0, confirmed.stepsLeft)
    }

    @Test
    fun `an unreadable unused-app status hides the row instead of passing or failing it`() {
        val state = BackgroundAccessState(unusedRestrictionsStatus = UnusedAppRestrictionsConstants.ERROR)
        assertFalse(state.showKeepAccess)
        assertEquals(0, state.stepsLeft)
    }

    @Test
    fun `tracking problem is the worst readable blocker, or null once fixed`() {
        val fine = BackgroundAccessState(batteryUnrestricted = true, backgroundRestricted = false)
        assertEquals(null, trackingProblem(smsGranted = true, access = fine))
        assertEquals(TrackingProblem.SMS_ACCESS, trackingProblem(smsGranted = false, access = null))
        assertEquals(null, trackingProblem(smsGranted = true, access = null))

        val restricted = fine.copy(batteryUnrestricted = false, backgroundRestricted = true)
        assertEquals(TrackingProblem.SMS_ACCESS, trackingProblem(false, restricted))
        assertEquals(TrackingProblem.BACKGROUND_RESTRICTED, trackingProblem(true, restricted))
        assertEquals(TrackingProblem.BATTERY_OPTIMIZED, trackingProblem(true, fine.copy(batteryUnrestricted = false)))

        // Unused-app and manufacturer steps can't be read back, so they never raise it.
        val unverifiable = fine.copy(
            unusedRestrictionsStatus = UnusedAppRestrictionsConstants.API_31,
            oem = DeviceOem.XIAOMI
        )
        assertEquals(null, trackingProblem(true, unverifiable))
        // Samsung's confirmed Never-sleeping step stands in for the battery exemption.
        val samsung = fine.copy(batteryUnrestricted = false, oem = DeviceOem.SAMSUNG, oemConfirmed = true)
        assertEquals(null, trackingProblem(true, samsung))
    }
}
