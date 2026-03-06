package com.pennywiseai.shared.data.util

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.timeZoneForSecondsFromGMT
import platform.posix.time

@OptIn(ExperimentalForeignApi::class)
actual fun currentTimeMillis(): Long = time(null).toLong() * 1000L

actual fun monthStartEpochMillis(): Long {
    val calendar = NSCalendar.currentCalendar
    val components = calendar.components(
        NSCalendarUnitYear or NSCalendarUnitMonth,
        fromDate = NSDate()
    )
    components.setDay(1)
    components.setHour(0)
    components.setMinute(0)
    components.setSecond(0)
    val startOfMonth = calendar.dateFromComponents(components) ?: NSDate()
    return (startOfMonth.timeIntervalSince1970 * 1000.0).toLong()
}

actual fun monthStartEpochMillisIST(): Long {
    val istTimeZone = NSTimeZone.timeZoneForSecondsFromGMT(19800) // 5.5 hours = 19800 seconds
    val calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian)!!
    calendar.timeZone = istTimeZone
    val components = calendar.components(
        NSCalendarUnitYear or NSCalendarUnitMonth,
        fromDate = NSDate()
    )
    components.setDay(1)
    components.setHour(0)
    components.setMinute(0)
    components.setSecond(0)
    val startOfMonth = calendar.dateFromComponents(components) ?: NSDate()
    return (startOfMonth.timeIntervalSince1970 * 1000.0).toLong()
}
