import com.example.configureRouting
import com.example.ui.ProViews
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProOfferTest {
    @Test
    fun `the offer runs until 1159 pm IST on 10 Nov`() {
        assertTrue(ProViews.offerActive(Instant.parse("2026-11-10T18:29:59Z")))
        assertFalse(ProViews.offerActive(Instant.parse("2026-11-10T18:30:00Z")))
    }
}

class TermsRouteTest {
    @Test
    fun `terms page loads and is linked from pro and the sitemap`() = testApplication {
        application { configureRouting() }
        assertEquals(HttpStatusCode.OK, client.get("/terms").status)
        assertTrue(client.get("/pro").bodyAsText().contains("href=\"/terms\""))
        assertTrue(client.get("/sitemap.xml").bodyAsText().contains("/terms</loc>"))
    }
}
