package com.pennywiseai.tracker.billing.license

import android.os.Build
import android.util.Log
import com.pennywiseai.tracker.data.preferences.StoredLicense
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Website-sold Pro: a Dodo license key entered in the app. Independent of
 * Play Billing — [com.pennywiseai.tracker.billing.EntitlementGate] ORs the
 * two. Works identically on every flavor; F-Droid is already Pro so the UI
 * simply never offers the field there.
 *
 * Activation limit is 1 per key (set on the Dodo product). Moving to a new
 * phone either comes free with a backup restore (key + instance travel in
 * the backup) or via [moveHere], which asks our Worker to free the old
 * activation before re-activating.
 */
@Singleton
class LicenseManager @Inject constructor(
    private val client: LicenseClient,
    private val preferences: UserPreferencesRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {

    sealed class ActivationOutcome {
        data object Activated : ActivationOutcome()
        data object InvalidKey : ActivationOutcome()
        /** Key is live on another device. UI offers "Move to this device". */
        data object ActiveElsewhere : ActivationOutcome()
        data object Offline : ActivationOutcome()
    }

    val license: StateFlow<StoredLicense?> = preferences.storedLicense
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** True while the stored key is within [LicensePolicy]'s window. */
    val isLicensed: StateFlow<Boolean> = preferences.storedLicense
        .map { it != null && LicensePolicy.grantsPro(it.validatedAt, System.currentTimeMillis()) }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val mutex = Mutex()

    init {
        scope.launch { revalidateIfDue() }
    }

    suspend fun activate(rawKey: String): ActivationOutcome = mutex.withLock {
        val key = rawKey.trim()
        if (key.isEmpty()) return ActivationOutcome.InvalidKey
        when (val result = client.activate(key, deviceName())) {
            is LicenseClient.ActivateResult.Activated -> {
                preferences.setStoredLicense(
                    StoredLicense(
                        key = key,
                        instanceId = result.instanceId,
                        validatedAt = System.currentTimeMillis(),
                        productName = result.productName,
                    ),
                )
                ActivationOutcome.Activated
            }
            LicenseClient.ActivateResult.InvalidKey -> ActivationOutcome.InvalidKey
            LicenseClient.ActivateResult.LimitReached -> ActivationOutcome.ActiveElsewhere
            is LicenseClient.ActivateResult.Error -> ActivationOutcome.Offline
        }
    }

    /** Frees the key's other activation through the move endpoint, then activates here. */
    suspend fun moveHere(rawKey: String): ActivationOutcome {
        if (!client.requestMove(rawKey.trim())) return ActivationOutcome.Offline
        return activate(rawKey)
    }

    /** Deactivates on Dodo (best effort) and forgets the key locally. */
    suspend fun remove() = mutex.withLock {
        val current = preferences.storedLicense.first() ?: return@withLock
        current.instanceId?.let { client.deactivate(current.key, it) }
        preferences.setStoredLicense(null)
    }

    /**
     * Backup restore path: adopt a key + instance from another install and
     * confirm it with Dodo. On a definitive "invalid" the key is dropped;
     * when offline it's kept with a zero timestamp, so Pro only lights up
     * once a validation succeeds.
     */
    suspend fun restore(key: String, instanceId: String?) {
        if (key.isBlank()) return
        mutex.withLock {
            preferences.setStoredLicense(StoredLicense(key, instanceId, validatedAt = 0L, productName = null))
        }
        revalidate(force = true)
    }

    suspend fun revalidateIfDue() = revalidate(force = false)

    private suspend fun revalidate(force: Boolean) = mutex.withLock {
        val current = preferences.storedLicense.first() ?: return@withLock
        val now = System.currentTimeMillis()
        if (!force && !LicensePolicy.isDue(current.validatedAt, now)) return@withLock
        when (client.validate(current.key, current.instanceId)) {
            LicenseClient.ValidateResult.Valid -> preferences.setStoredLicense(current.copy(validatedAt = now))
            LicenseClient.ValidateResult.Invalid -> {
                Log.i(TAG, "License no longer valid; clearing")
                preferences.setStoredLicense(null)
            }
            is LicenseClient.ValidateResult.Error -> Unit // keep cached state; LicensePolicy handles expiry
        }
    }

    private fun deviceName(): String =
        "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifEmpty { "Android" }

    private companion object {
        const val TAG = "LicenseManager"
    }
}
