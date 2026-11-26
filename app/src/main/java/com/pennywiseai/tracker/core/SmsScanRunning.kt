package com.pennywiseai.tracker.core

import kotlinx.coroutines.flow.Flow

/**
 * Whether an SMS scan is queued or running now. `worker/SmsScanManager` implements it
 * (bound in `di/SmsScanRunningModule.kt`), so a widget can tell a scan in progress from
 * one that died mid-run without importing `worker`.
 */
fun interface SmsScanRunning {
    fun isScanRunning(): Flow<Boolean>
}
