package com.pennywiseai.tracker.data.currency

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * open.er-api.com is the primary source; allratestoday.com is only asked when it fails.
 */
class FreeExchangeRateProviderFallbackTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun body(provider: String, inr: Double, withEol: Boolean) = """
        {"result":"success","provider":"$provider","documentation":"d","terms_of_use":"t",
         "time_last_update_unix":1793372400,"time_last_update_utc":"u",
         "time_next_update_unix":1793631600,"time_next_update_utc":"n",
         ${if (withEol) "\"time_eol_unix\":0," else ""}
         "base_code":"USD","rates":{"USD":1,"INR":$inr}}
    """.trimIndent()

    private fun provider(requested: MutableList<String>, primaryOk: Boolean, fallbackOk: Boolean) =
        FreeExchangeRateProvider(MockEngine { request ->
            val host = request.url.host
            requested += host
            when {
                host == "open.er-api.com" && primaryOk ->
                    respond(body("https://www.exchangerate-api.com", 95.1, withEol = true), HttpStatusCode.OK, jsonHeaders)
                host == "allratestoday.com" && fallbackOk ->
                    // The fallback response has no time_eol_unix field.
                    respond(body("https://allratestoday.com", 94.8, withEol = false), HttpStatusCode.OK, jsonHeaders)
                else -> respond("""{"result":"error"}""", HttpStatusCode.ServiceUnavailable, jsonHeaders)
            }
        })

    @Test
    fun `primary success does not call the fallback`() = runBlocking {
        val requested = mutableListOf<String>()
        val result = provider(requested, primaryOk = true, fallbackOk = true)
            .fetchAllExchangeRatesWithMetadata("usd")

        assertEquals(listOf("open.er-api.com"), requested)
        assertEquals(BigDecimal("95.100000"), result?.rates?.get("INR"))
    }

    @Test
    fun `primary failure falls back and parses a response without time_eol_unix`() = runBlocking {
        val requested = mutableListOf<String>()
        val result = provider(requested, primaryOk = false, fallbackOk = true)
            .fetchAllExchangeRatesWithMetadata("USD")

        assertEquals(listOf("open.er-api.com", "allratestoday.com"), requested)
        assertEquals(BigDecimal("94.800000"), result?.rates?.get("INR"))
        assertEquals("https://allratestoday.com", result?.provider)
        assertEquals(1793631600L, result?.nextUpdateTimeUnix)
    }

    @Test
    fun `both sources failing returns null`() = runBlocking {
        val requested = mutableListOf<String>()
        val result = provider(requested, primaryOk = false, fallbackOk = false)
            .fetchAllExchangeRatesWithMetadata("USD")

        assertEquals(listOf("open.er-api.com", "allratestoday.com"), requested)
        assertNull(result)
    }
}
