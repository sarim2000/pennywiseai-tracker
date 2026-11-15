package com.pennywiseai.tracker.core.entitlement

/**
 * Hands a license key found in a backup to the license rail. Backup import
 * depends on this instead of `billing.license.LicenseManager`, which
 * implements it; `di/EntitlementContractsModule` binds the two.
 */
interface LicenseRestorer {

    /**
     * Stores [key] as a pending license and activates it on this device in the
     * background. [previousInstanceId] is the activation the backup came from,
     * released only if the key's activation limit is reached.
     */
    suspend fun restore(key: String, previousInstanceId: String?)
}
