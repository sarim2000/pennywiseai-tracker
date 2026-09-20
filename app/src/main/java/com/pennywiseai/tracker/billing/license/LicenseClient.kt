package com.pennywiseai.tracker.billing.license

import com.pennywiseai.tracker.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over Dodo Payments' public license endpoints. All three are
 * unauthenticated by design (Dodo built them for client apps), so no API key
 * ships in the APK. Base URL is test-mode on debug builds so emulator runs
 * hit test keys, live otherwise.
 */
@Singleton
class LicenseClient @Inject constructor() {

    sealed class ActivateResult {
        data class Activated(val instanceId: String, val licenseKeyId: String, val productName: String?) : ActivateResult()
        data object InvalidKey : ActivateResult()
        data object LimitReached : ActivateResult()
        data class Error(val message: String) : ActivateResult()
    }

    sealed class ValidateResult {
        data object Valid : ValidateResult()
        data object Invalid : ValidateResult()
        data class Error(val message: String) : ValidateResult()
    }

    private val client = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 20_000
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true })
        }
    }

    suspend fun activate(key: String, deviceName: String): ActivateResult = try {
        val response = client.post("$BASE_URL/licenses/activate") {
            contentType(ContentType.Application.Json)
            setBody(ActivateRequest(key, deviceName))
        }
        when (response.status) {
            HttpStatusCode.Created, HttpStatusCode.OK -> {
                val body = response.body<ActivateResponse>()
                ActivateResult.Activated(body.id, body.licenseKeyId, body.product?.name)
            }
            HttpStatusCode.NotFound, HttpStatusCode.Forbidden -> ActivateResult.InvalidKey
            HttpStatusCode.UnprocessableEntity -> ActivateResult.LimitReached
            else -> ActivateResult.Error("HTTP ${response.status.value}")
        }
    } catch (e: Exception) {
        ActivateResult.Error(e.message ?: "network")
    }

    suspend fun validate(key: String, instanceId: String?): ValidateResult = try {
        val response = client.post("$BASE_URL/licenses/validate") {
            contentType(ContentType.Application.Json)
            setBody(ValidateRequest(key, instanceId))
        }
        // Only a 200 carries a verdict. Anything else (429, 5xx, gateway
        // errors) is retryable and must not revoke a stored license.
        when (response.status.value) {
            200 -> if (response.body<ValidateResponse>().valid) ValidateResult.Valid else ValidateResult.Invalid
            else -> ValidateResult.Error("HTTP ${response.status.value}")
        }
    } catch (e: Exception) {
        ValidateResult.Error(e.message ?: "network")
    }

    /** Best effort; a failed deactivate just leaves a stale instance that "Move" can clear later. */
    suspend fun deactivate(key: String, instanceId: String): Boolean = try {
        client.post("$BASE_URL/licenses/deactivate") {
            contentType(ContentType.Application.Json)
            setBody(DeactivateRequest(key, instanceId))
        }.status.isSuccessCode()
    } catch (e: Exception) {
        false
    }

    /**
     * Asks our move endpoint (a tiny Cloudflare Worker holding the Dodo API
     * key) to free every activation on [key]. The Worker only acts when
     * [email] matches the key's purchaser, so a leaked key string alone
     * can't evict the owner. Returns false when the endpoint isn't
     * configured for this build or the request fails.
     */
    suspend fun requestMove(key: String, email: String): Boolean {
        if (BuildConfig.LICENSE_MOVE_URL.isBlank()) return false
        return try {
            client.post(BuildConfig.LICENSE_MOVE_URL) {
                contentType(ContentType.Application.Json)
                setBody(MoveRequest(key, email))
            }.status.isSuccessCode()
        } catch (e: Exception) {
            false
        }
    }

    private fun HttpStatusCode.isSuccessCode() = value in 200..299

    @Serializable private data class ActivateRequest(val license_key: String, val name: String)
    @Serializable private data class ValidateRequest(val license_key: String, val license_key_instance_id: String?)
    @Serializable private data class DeactivateRequest(val license_key: String, val license_key_instance_id: String)
    @Serializable private data class MoveRequest(val license_key: String, val email: String)

    @Serializable
    private data class ActivateResponse(
        val id: String,
        @kotlinx.serialization.SerialName("license_key_id") val licenseKeyId: String = "",
        val product: ProductRef? = null,
    )

    @Serializable private data class ProductRef(val name: String? = null)
    @Serializable private data class ValidateResponse(val valid: Boolean)

    companion object {
        val BASE_URL: String =
            if (BuildConfig.DEBUG) "https://test.dodopayments.com" else "https://live.dodopayments.com"
    }
}
