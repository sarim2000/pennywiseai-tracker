package com.pennywiseai.shared.data.util

import java.util.Calendar

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun monthStartEpochMillis(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

actual fun monthStartEpochMillisIST(): Long {
    val ist = java.util.TimeZone.getTimeZone("Asia/Kolkata")
    val cal = Calendar.getInstance(ist)
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
