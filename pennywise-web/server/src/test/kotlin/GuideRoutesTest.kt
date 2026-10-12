package com.example

import com.example.ui.GuideViews
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Covers /guide pages, and that app mode (opened from the Play build) never leads to a purchase. */
class GuideRoutesTest {

    private fun routingOnly(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { configureRouting() }
        block()
    }

    @Test
    fun `every guide page in the sitemap loads`() = routingOnly {
        GuideViews.paths.forEach { path ->
            assertEquals(HttpStatusCode.OK, client.get(path).status, "no page at $path")
        }
        assertEquals(HttpStatusCode.NotFound, client.get("/guide/not-a-guide").status)
    }

    @Test
    fun `app mode has no route to a purchase and keeps app mode on guide links`() = routingOnly {
        GuideViews.paths.forEach { path ->
            val body = client.get("$path?app=1").bodyAsText()
            assertFalse(body.contains("Get Pro"), "$path shows Get Pro in app mode")
            // The embedded search index counts too, not just the visible page.
            assertFalse(body.contains("Buy a key"), "$path offers a purchase in app mode (page or search index)")
            assertFalse(Regex("""<a\b[^>]*href="/(?!guide)""").containsMatchIn(body), "$path links outside the guide in app mode")
            Regex("""href="(/guide[^"]*)"""").findAll(body).forEach { m ->
                assertTrue(m.groupValues[1].contains("?app=1"), "$path has a guide link without app mode: ${m.groupValues[1]}")
            }
        }
        // The normal site still sells Pro from the guide.
        assertTrue(client.get("/guide/pro").bodyAsText().contains("href=\"/pro\""))
    }
}
