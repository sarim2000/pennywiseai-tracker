package com.example

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the generated bank/country landing pages.
 *
 * Only [configureRouting] is installed — the full `module()` also opens a Postgres
 * connection for the feedback feature, which these pages don't touch.
 */
class BankRoutesTest {

    private fun routingOnly(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { configureRouting() }
        block()
    }

    @Test
    fun `banks index lists every country and the live totals`() = routingOnly {
        val body = client.get("/banks").bodyAsText()
        val cat = SupportedBanks.catalogue
        assertTrue(body.contains("${cat.totalBanks} banks"), "index should quote the live bank count")
        cat.countries.forEach {
            assertTrue(body.contains(it.country), "index is missing ${it.country}")
        }
    }

    @Test
    fun `every bank in the catalogue has a reachable page`() = routingOnly {
        // Guards against slug drift: if a parser is renamed and the slug changes, or two
        // banks collide onto one URL, the sitemap would advertise a 404 to Google.
        SupportedBanks.banks.forEach { bank ->
            val response = client.get("/banks/${bank.slug}")
            assertEquals(HttpStatusCode.OK, response.status, "no page for ${bank.name} (/banks/${bank.slug})")
        }
    }

    @Test
    fun `every country in the catalogue has a reachable page`() = routingOnly {
        SupportedBanks.countries.forEach { country ->
            val slug = SupportedBanks.countrySlug(country.country)
            val response = client.get("/banks/country/$slug")
            assertEquals(HttpStatusCode.OK, response.status, "no page for ${country.country}")
        }
    }

    @Test
    fun `a bank page carries the SEO tags that make it indexable`() = routingOnly {
        val body = client.get("/banks/hdfc-bank").bodyAsText()
        assertTrue(body.contains("HDFC Bank"), "page should name the bank")
        assertTrue(
            body.contains("""<link rel="canonical" href="https://pennywise.zynth.dev/banks/hdfc-bank">"""),
            "page needs a canonical URL"
        )
        assertTrue(body.contains("""<meta name="description""""), "page needs a meta description")
        assertTrue(body.contains("\"@type\":\"FAQPage\""), "page needs FAQ structured data")
        assertTrue(body.contains("\"@type\":\"BreadcrumbList\""), "page needs breadcrumb structured data")
        // The parser tool is what keeps these from being thin doorway pages.
        assertTrue(body.contains("/tools/parse"), "page should link to the live parser")
    }

    @Test
    fun `sitemap advertises every page and nothing that 404s`() = routingOnly {
        val sitemap = client.get("/sitemap.xml").bodyAsText()
        // "/" + "/banks", then one page per country and per bank. "/tools/parse" is an
        // alias of "/" and is deliberately absent so the two don't compete.
        val expected = 2 + SupportedBanks.countries.size + SupportedBanks.banks.size
        assertEquals(expected, Regex("<loc>").findAll(sitemap).count(), "sitemap URL count")

        val paths = Regex("<loc>https://pennywise\\.zynth\\.dev(.*?)</loc>")
            .findAll(sitemap).map { it.groupValues[1] }.toList()
        paths.forEach { path ->
            assertEquals(HttpStatusCode.OK, client.get(path).status, "sitemap advertises a dead URL: $path")
        }
    }

    @Test
    fun `robots points at the sitemap`() = routingOnly {
        val robots = client.get("/robots.txt").bodyAsText()
        assertTrue(robots.contains("Sitemap: https://pennywise.zynth.dev/sitemap.xml"), robots)
    }

    @Test
    fun `unknown slugs 404 rather than rendering an empty page`() = routingOnly {
        assertEquals(HttpStatusCode.NotFound, client.get("/banks/not-a-real-bank").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/banks/country/atlantis").status)
    }
}
