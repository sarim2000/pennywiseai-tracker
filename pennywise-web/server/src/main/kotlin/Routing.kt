package com.example

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import io.ktor.server.plugins.cors.routing.*
import com.pennywiseai.parser.core.bank.BankParserRegistry
import com.pennywiseai.parser.core.bank.*
import com.example.ui.ParseViews.respondParsePage
import com.example.ui.ParseViews.renderParseResult
import com.example.ui.BankViews
import com.example.ui.BankViews.respondBankPage
import com.example.ui.BankViews.respondBanksIndex
import com.example.ui.BankViews.respondCountryPage

fun Application.configureRouting() {
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
    }

    val registry = BankParserRegistry(
        listOf(
            HDFCBankParser(), SBIBankParser(), ICICIBankParser(), AxisBankParser(), KotakBankParser(),
            FederalBankParser(), IDFCFirstBankParser(), IDBIBankParser(), CanaraBankParser(), BankOfBarodaParser(),
            BankOfIndiaParser(), IndianBankParser(), IndianOverseasBankParser(), CentralBankOfIndiaParser(),
            CityUnionBankParser(), KarnatakaBankParser(), PNBBankParser(), UnionBankParser(), JKBankParser(),
            AirtelPaymentsBankParser(), IPPBParser(), JioPaymentsBankParser(), JioPayParser(), JupiterBankParser(),
            HSBCBankParser(), SliceParser(), LazyPayParser(), AMEXBankParser(), OneCardParser(), SouthIndianBankParser(),
            UtkarshBankParser(), DBSBankParser(), JuspayParser(),
            // Sri Lanka (#658) — the web tester advertises NDB samples, so the
            // /htmx/parse registry must be able to parse them too.
            NDBBankParser()
        )
    )

    routing {
        // Serve static resources
        staticResources("/static", "static")

        // Serve HTMX page at root
        get("/") { call.respondParsePage() }
        get("/tools/parse") { call.respondParsePage() }

        // Long-tail landing pages, one per supported bank/country. Generated from the
        // same catalogue the README and Play listing use, so they can never advertise a
        // bank we don't actually parse.
        get("/banks") { call.respondBanksIndex() }
        get("/banks/country/{slug}") {
            val country = SupportedBanks.country(call.parameters["slug"].orEmpty())
            if (country == null) call.respond(HttpStatusCode.NotFound, "Unknown country")
            else call.respondCountryPage(country)
        }
        get("/banks/{slug}") {
            val bank = SupportedBanks.bank(call.parameters["slug"].orEmpty())
            if (bank == null) call.respond(HttpStatusCode.NotFound, "Unknown bank")
            else call.respondBankPage(bank)
        }

        get("/sitemap.xml") {
            call.respondText(BankViews.buildSitemap(), ContentType.Text.Xml)
        }
        get("/robots.txt") {
            call.respondText(BankViews.buildRobots(), ContentType.Text.Plain)
        }

        // HTMX endpoint that returns an HTML snippet with parsed result
        post("/htmx/parse") {
            val params = call.receiveParameters()
            val sender = params["sender"]?.trim().orEmpty()
            val smsBody = params["smsBody"]?.trim().orEmpty()
            val tsStr = params["timestamp"]?.trim()
            val ts = tsStr?.toLongOrNull() ?: System.currentTimeMillis()

            if (sender.isEmpty() || smsBody.isEmpty()) {
                call.respondHtml(HttpStatusCode.BadRequest) {
                    body {
                        div {
                            p { +"Sender and message body are required." }
                        }
                    }
                }
                return@post
            }

            val parser = registry.getParser(sender)
            val parsed = parser?.parse(smsBody, sender, ts)

            call.respondHtml(HttpStatusCode.OK) { body { renderParseResult(parsed, sender, smsBody) } }
        }
    }
}
