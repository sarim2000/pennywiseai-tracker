package com.example.ui

import com.example.module
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParsePageSecurityTest {

    // configureDatabases() opens its connection eagerly from config; point it at
    // an in-memory H2 instance so the page-render tests stay hermetic.
    private fun ApplicationTestBuilder.parsePageApp() {
        environment {
            config = MapApplicationConfig(
                "postgres.url" to "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
                "postgres.user" to "root",
                "postgres.password" to ""
            )
        }
        application { module() }
    }

    @Test
    fun `report modal never assigns innerHTML from parsed fields`() = testApplication {
        parsePageApp()
        val response = client.get("/tools/parse")
        assertEquals(HttpStatusCode.OK, response.status)
        val html = response.bodyAsText()
        // The only permitted innerHTML assignments are the literal empty-string
        // clears in hideReportModal(); anything else is a DOM XSS sink.
        val dynamicSinks = Regex("""innerHTML\s*=\s*([^;\r\n]+)""").findAll(html)
            .map { it.groupValues[1].trim() }
            .filterNot { it == "''" }
            .toList()
        assertTrue(
            dynamicSinks.isEmpty(),
            "innerHTML assignments beyond static clears found: $dynamicSinks"
        )
        assertTrue(
            html.contains("parsedDetails.textContent"),
            "dynamic fields must be written via textContent"
        )
        assertTrue(
            html.contains("if (parsed.type)") && html.contains("if (parsed.merchant)"),
            "Type/Merchant rows must be guarded against absent fields"
        )
    }

    @Test
    fun `submitReport error path writes status via textContent`() = testApplication {
        parsePageApp()
        val html = client.get("/tools/parse").bodyAsText()
        assertFalse(
            Regex("""innerHTML\s*=\s*[^;\n]*\+""").containsMatchIn(html),
            "server-controlled result.message interpolated into innerHTML"
        )
        assertTrue(
            html.contains("function showStatus") && html.contains("el.textContent = text"),
            "status messages must be written via textContent"
        )
    }
}
