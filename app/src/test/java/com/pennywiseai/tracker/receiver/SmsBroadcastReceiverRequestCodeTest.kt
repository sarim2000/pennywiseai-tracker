package com.pennywiseai.tracker.receiver

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SmsBroadcastReceiverRequestCodeTest {

    @Test
    fun `codes unique across transactions and both category slots`() {
        val ids = listOf(98L, 99L, 100L, 101L, 4095L, 123456L)
        val codes = ids.flatMap { id -> (0..1).map { slot -> SmsBroadcastReceiver.categoryRequestCode(id, slot) } }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `audited collision pair tx99-slot1_vs_tx100-slot0 now distinct`() {
        // Old scheme: 99 + 1 + 1 == 100 + 0 + 1 == 101 → wrong-transaction recategorize
        assertNotEquals(
            SmsBroadcastReceiver.categoryRequestCode(99L, 1),
            SmsBroadcastReceiver.categoryRequestCode(100L, 0)
        )
    }

    @Test
    fun `same transaction same slot is stable`() {
        assertEquals(
            SmsBroadcastReceiver.categoryRequestCode(100L, 0),
            SmsBroadcastReceiver.categoryRequestCode(100L, 0)
        )
        assertEquals(1000L, SmsBroadcastReceiver.categoryRequestCode(100L, 0).toLong())
    }

    @Test
    fun `picker code occupies reserved slot outside category slots`() {
        val picker = SmsBroadcastReceiver.pickerRequestCode(100L)
        assertNotEquals(picker, SmsBroadcastReceiver.categoryRequestCode(100L, 0))
        assertNotEquals(picker, SmsBroadcastReceiver.categoryRequestCode(100L, 1))
    }
}
