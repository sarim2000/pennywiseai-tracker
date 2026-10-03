package com.pennywiseai.tracker.data.webhook

import com.pennywiseai.tracker.data.database.entity.*
import com.pennywiseai.tracker.data.repository.WebhookHeaderEncoder
import org.junit.Assert.*
import org.junit.Test

class WebhookValidationTest {
    private val draft = WebhookProfileDraft(name = "Test", url = "https://example.com/hook", enabled = true,
        dataTypes = setOf(WebhookDataType.TRANSACTIONS), rangePreset = WebhookRangePreset.TODAY,
        currency = "INR", headers = emptyList())

    @Test fun `URL requires valid host and rejects embedded credentials`() {
        listOf("https://", "https://bad host/hook", "ftp://example.com", "http://example.com", "https://user:pass@example.com", "https://example.com/#fragment").forEach {
            assertNotNull(it, WebhookValidation.validateUrl(it))
        }
        assertNull(WebhookValidation.validateUrl(" HTTPS://example.com/hook "))
        assertNull(WebhookValidation.validateUrl("http://127.0.0.1:8765/hook"))
    }
    @Test fun `draft requires data currency valid dates and safe unique headers`() {
        assertNull(WebhookValidation.validateDraft(draft))
        assertNotNull(WebhookValidation.validateDraft(draft.copy(dataTypes = emptySet())))
        assertNotNull(WebhookValidation.validateDraft(draft.copy(currency = "INVALID")))
        assertNotNull(WebhookValidation.validateDraft(draft.copy(rangePreset = WebhookRangePreset.CUSTOM)))
        listOf(listOf(WebhookHeader("X-Test", "x\r\ny")), listOf(WebhookHeader("Host", "example.com")),
            listOf(WebhookHeader("bad name", "x")), listOf(WebhookHeader("X-Test", "x"), WebhookHeader("x-test", "y"))).forEach {
            assertNotNull(WebhookValidation.validateDraft(draft.copy(headers = it)))
        }
    }
    @Test fun `headers round trip punctuation without exporting credentials`() {
        val headers = listOf(WebhookHeader("Authorization", "synthetic \"value\""))
        assertEquals(headers, WebhookHeaderEncoder.decode(WebhookHeaderEncoder.encode(headers)))
        assertEquals("", WebhookHeaderEncoder.sanitizeForExport(headers).single().value)
    }
}
