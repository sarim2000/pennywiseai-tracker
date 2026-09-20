package com.pennywiseai.tracker.billing.license

import android.os.Build
import android.util.Log
import com.pennywiseai.tracker.data.preferences.StoredLicense
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Website-sold Pro: a Dodo license key entered in the app. Independent of
 * Play Billing — [com.pennywiseai.tracker.billing.EntitlementGate] ORs the
 * two. Works identically on every flavor; F-Droid is already Pro so the UI
 * simply never offers the field there.
 *
 * Activation limit is 1 per key (set on the Dodo product). Moving to a new
 * phone happens either through a backup restore — which *transfers* the
 * activation (deactivates the instance carried in the backup, activates
 * afresh here), so two restores of one backup can't both hold Pro — or via
 * [moveHere], which asks our Worker to free the old activation first.
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

    /**
     * Re-evaluates the policy on a slow ticker as well as on every DataStore
     * change, so a process that stays alive across the 30/60-day boundaries
     * still re-checks and, if need be, lapses. The tick also drives the
     * background revalidation.
     */
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MS)
        }
    }.onEach { scope.launch { revalidateIfDue() } }

    /** True while the stored key is within [LicensePolicy]'s window. */
    val isLicensed: StateFlow<Boolean> =
        combine(preferences.storedLicense, ticker) { license, _ ->
            license != null && LicensePolicy.grantsPro(license.validatedAt, System.currentTimeMillis())
        }.stateIn(scope, SharingStarted.Eagerly, false)

    private val mutex = Mutex()

    suspend fun activate(rawKey: String): ActivationOutcome = mutex.withLock {
        val key = rawKey.trim()
        if (key.isEmpty()) return ActivationOutcome.InvalidKey
        activateLocked(key)
    }

    /** Frees the key's other activation through the move endpoint, then activates here. */
    suspend fun moveHere(rawKey: String, email: String): ActivationOutcome {
        if (!client.requestMove(rawKey.trim(), email.trim())) return ActivationOutcome.Offline
        return activate(rawKey)
    }

    /** Deactivates on Dodo (best effort) and forgets the key locally. */
    suspend fun remove() = mutex.withLock {
        val current = preferences.storedLicense.first() ?: return@withLock
        current.instanceId?.let { client.deactivate(current.key, it) }
        preferences.setStoredLicense(null)
    }

    /**
     * Backup restore path. Only writes DataStore synchronously (the importer
     * runs inside a Room transaction); the network work happens on the
     * application scope afterwards. Order matters: we try to activate this
     * device *first* and only release the instance the backup came from if
     * Dodo says the limit is reached — so a transient failure never leaves
     * the old phone revoked with nothing activated here. Until activation
     * succeeds the stored license has no instance and grants nothing; the
     * ticker keeps retrying a pending adoption.
     */
    suspend fun restore(key: String, previousInstanceId: String?) {
        if (key.isBlank()) return
        mutex.withLock {
            preferences.setStoredLicense(
                StoredLicense(key, instanceId = null, validatedAt = 0L, productName = null),
            )
        }
        scope.launch {
            val first = mutex.withLock { activateLocked(key) }
            if (first == ActivationOutcome.ActiveElsewhere && previousInstanceId != null) {
                if (client.deactivate(key, previousInstanceId)) revalidate(force = true)
            } else if (first == ActivationOutcome.InvalidKey) {
                mutex.withLock { preferences.setStoredLicense(null) }
            }
        }
    }

    suspend fun revalidateIfDue() = revalidate(force = false)

    private suspend fun revalidate(force: Boolean) = mutex.withLock {
        val current = preferences.storedLicense.first() ?: return@withLock
        // A key without an instance is a pending adoption (restore): bind it to
        // this device. Dodo enforces the activation limit for us.
        if (current.instanceId == null) {
            if (activateLocked(current.key) != ActivationOutcome.Offline &&
                preferences.storedLicense.first()?.instanceId == null
            ) {
                preferences.setStoredLicense(null) // invalid or held by another device
            }
            return@withLock
        }
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

    private suspend fun activateLocked(key: String): ActivationOutcome =
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

    private fun deviceName(): String =
        "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifEmpty { "Android" }

    private companion object {
        const val TAG = "LicenseManager"
        val TICK_MS: Long = TimeUnit.MINUTES.toMillis(15)
    }
}
