package com.example.ui

import com.example.BankPage
import com.example.CountryEntry
import com.example.SupportedBanks
import com.example.ui.SharedComponents.PLAY_STORE_URL
import com.example.ui.SharedComponents.SITE_ORIGIN
import com.example.ui.SharedComponents.commonHead
import com.example.ui.SharedComponents.commonStyles
import com.example.ui.SharedComponents.siteHeader
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import kotlinx.html.*

/**
 * Long-tail landing pages: one per supported bank and one per country.
 *
 * These target queries like "how to track HDFC transactions automatically" or
 * "M-Pesa expense tracker" — near-zero-competition searches we're well placed to answer.
 * Each page is built from the generated catalogue (see [SupportedBanks]) and links into
 * the live parser tool pre-filled for that bank, so it carries a real, working feature
 * rather than being a thin doorway page.
 */
object BankViews {

    // ---------------------------------------------------------------- helpers

    private fun FlowContent.breadcrumbs(vararg crumbs: Pair<String, String?>) {
        div(classes = "breadcrumb") {
            crumbs.forEachIndexed { i, (label, href) ->
                if (i > 0) +" › "
                if (href != null) a(href = href) { +label } else +label
            }
        }
    }

    /** JSON-LD block. Google reads these for FAQ rich results and breadcrumb trails. */
    private fun FlowContent.jsonLd(payload: String) {
        script {
            attributes["type"] = "application/ld+json"
            unsafe { +payload }
        }
    }

    private fun esc(value: String) = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", " ")

    private fun faqJsonLd(faqs: List<Pair<String, String>>): String {
        val items = faqs.joinToString(",") { (q, a) ->
            """{"@type":"Question","name":"${esc(q)}","acceptedAnswer":{"@type":"Answer","text":"${esc(a)}"}}"""
        }
        return """{"@context":"https://schema.org","@type":"FAQPage","mainEntity":[$items]}"""
    }

    private fun breadcrumbJsonLd(crumbs: List<Pair<String, String>>): String {
        val items = crumbs.mapIndexed { i, (name, path) ->
            """{"@type":"ListItem","position":${i + 1},"name":"${esc(name)}","item":"$SITE_ORIGIN$path"}"""
        }.joinToString(",")
        return """{"@context":"https://schema.org","@type":"BreadcrumbList","itemListElement":[$items]}"""
    }

    /**
     * Google truncates titles around 60 characters, and bank names range from "Slice" to
     * "Saraswat Co-operative Bank" — so pick the most descriptive phrasing that still fits
     * rather than letting long names push the keyword out of the visible part.
     */
    private fun bankTitle(name: String): String = listOf(
        "Track $name transactions automatically",
        "$name SMS expense tracker",
        name,
    ).firstOrNull { it.length <= 60 } ?: name.take(60)

    /** Meta descriptions get cut around 160; build to fit instead of being trimmed. */
    private fun clampDescription(text: String, limit: Int = 155): String {
        if (text.length <= limit) return text
        val cut = text.take(limit)
        return cut.substring(0, cut.lastIndexOf(' ').coerceAtLeast(1)).trimEnd(',', ';', '—', '-') + "…"
    }

    private fun FlowContent.faqSection(faqs: List<Pair<String, String>>) {
        h2 { +"Common questions" }
        div(classes = "faq") {
            faqs.forEach { (q, a) ->
                details {
                    summary { +q }
                    p { +a }
                }
            }
        }
        jsonLd(faqJsonLd(faqs))
    }

    // ------------------------------------------------------- the parse strip

    /** Plain-language names — "CREDIT" means nothing to someone tracking their spending. */
    private fun typeLabel(type: String) = when (type) {
        "EXPENSE" -> "Money out"
        "INCOME" -> "Money in"
        "CREDIT" -> "Credit card"
        "TRANSFER" -> "Transfer"
        "INVESTMENT" -> "Investment"
        "BALANCE_UPDATE" -> "Balance update"
        else -> type.lowercase().replaceFirstChar { it.uppercase() }
    }

    /** Groups the integer part with commas; leaves the parser's own precision alone. */
    private fun formatAmount(raw: String): String {
        val negative = raw.startsWith("-")
        val body = raw.removePrefix("-")
        val whole = body.substringBefore('.')
        val frac = body.substringAfter('.', "")
        val grouped = whole.reversed().chunked(3).joinToString(",").reversed()
        return (if (negative) "-" else "") + grouped + if (frac.isNotEmpty()) ".$frac" else ""
    }

    /**
     * The message may write the amount differently from how the parser normalised it
     * ("15000.00" vs "15,000" vs "15000"), so try the plausible spellings.
     */
    private fun amountCandidates(amount: String): List<String> {
        val whole = amount.substringBefore('.')
        val grouped = whole.reversed().chunked(3).joinToString(",").reversed()
        return listOfNotNull(
            amount,
            formatAmount(amount),
            whole.takeIf { it != amount },
            grouped.takeIf { it != whole },
        ).distinct().sortedByDescending { it.length }
    }

    private data class Span(val start: Int, val end: Int, val cls: String)

    /**
     * Locates each extracted value inside the raw message so it can be tinted in place.
     * Claimed in priority order — the account's last-4 is the most collision-prone (those
     * digits often also appear inside a reference number), so it goes last and only takes
     * a range nothing else wanted.
     */
    private fun locate(message: String, sample: com.example.BankSample): List<Span> {
        val claims = buildList {
            sample.reference?.let { add("f-ref" to listOf(it)) }
            sample.merchant?.let { add("f-merchant" to listOf(it)) }
            add("f-amount" to amountCandidates(sample.amount))
            sample.accountLast4?.let { add("f-account" to listOf(it)) }
        }
        val spans = mutableListOf<Span>()
        for ((cls, candidates) in claims) {
            for (candidate in candidates) {
                if (candidate.isBlank()) continue
                var from = 0
                var placed = false
                while (!placed) {
                    val idx = message.indexOf(candidate, from, ignoreCase = true)
                    if (idx < 0) break
                    val end = idx + candidate.length
                    if (spans.none { idx < it.end && it.start < end }) {
                        spans.add(Span(idx, end, cls)); placed = true
                    } else {
                        from = idx + 1
                    }
                }
                if (placed) break
            }
        }
        return spans.sortedBy { it.start }
    }

    private fun FlowContent.parseStrip(bank: BankPage, sample: com.example.BankSample) {
        val spans = locate(sample.message, sample)
        val moneyIn = sample.type == "INCOME"
        val symbol = if (sample.currency == bank.country.currency) bank.country.symbol else ""

        div(classes = "parse") {
            // Plural, no article — "a HDFC Bank" / "an ICICI Bank" can't both be right
            // from one template, and the a/an choice varies across all 141 names.
            div(classes = "eyebrow") { +"What PennyWise reads from your ${bank.name} messages" }
            div(classes = "sms") {
                div(classes = "sms-from") {
                    span(classes = "sms-dot") {}
                    +"SMS from "
                    b { +sample.sender }
                }
                div(classes = "sms-body") {
                    var cursor = 0
                    spans.forEach { s ->
                        if (s.start > cursor) +sample.message.substring(cursor, s.start)
                        mark(classes = s.cls) { +sample.message.substring(s.start, s.end) }
                        cursor = s.end
                    }
                    if (cursor < sample.message.length) +sample.message.substring(cursor)
                }
            }

            div(classes = "parse-arrow") {
                i {}
                span { +"Read on your phone — nothing sent anywhere" }
            }

            div(classes = "parsed") {
                div(classes = "parsed-top") {
                    div(classes = "parsed-amount " + if (moneyIn) "in" else "out") {
                        +((if (moneyIn) "+" else "−") + symbol + formatAmount(sample.amount))
                        if (symbol.isEmpty()) {
                            span(classes = "tag") { +sample.currency }
                        }
                    }
                    span(classes = "tag") { +typeLabel(sample.type) }
                }
                div(classes = "fields") {
                    sample.merchant?.let {
                        div {
                            div(classes = "field-k") { +"Merchant" }
                            div(classes = "field-v f-merchant") { +it }
                        }
                    }
                    sample.accountLast4?.let {
                        div {
                            div(classes = "field-k") { +"Account" }
                            div(classes = "field-v f-account") { +"••$it" }
                        }
                    }
                    sample.reference?.let {
                        div {
                            div(classes = "field-k") { +"Reference" }
                            div(classes = "field-v f-ref") { +it }
                        }
                    }
                    sample.balance?.let {
                        div {
                            div(classes = "field-k") { +"Balance after" }
                            div(classes = "field-v") { +(symbol + formatAmount(it)) }
                        }
                    }
                }
            }

            p(classes = "parse-note") {
                +"A real ${bank.name} message format, run through the same parser that ships "
                +"in the app. Try it on your own message in the "
                a(href = "/tools/parse") { +"parser tool" }
                +"."
            }
        }
    }

    private fun FlowContent.downloadCta() {
        div(classes = "cta-row") {
            a(href = PLAY_STORE_URL, classes = "btn btn-primary") {
                attributes["rel"] = "noopener"
                +"Get PennyWise on Google Play"
            }
            a(
                href = "https://github.com/sarim2000/pennywiseai-tracker",
                classes = "btn btn-secondary"
            ) {
                attributes["rel"] = "noopener"
                +"View the source"
            }
        }
    }

    private fun FlowContent.pageFooter() {
        div(classes = "footnote") {
            +"PennyWise is a free, open-source (AGPL-3.0) expense tracker. Bank SMS are parsed "
            +"entirely on your device — nothing is uploaded, and there is no account to create. "
            a(href = "/banks") { +"Browse all supported banks" }
            +"."
        }
    }

    // ------------------------------------------------------------- bank pages

    private fun bankFaqs(bank: BankPage): List<Pair<String, String>> {
        val name = bank.name
        val currency = bank.country.currency
        return listOf(
            "Does PennyWise work with $name?" to
                "Yes. $name is one of ${SupportedBanks.catalogue.totalBanks} banks and payment " +
                "services PennyWise recognises. Transaction alerts from $name are detected " +
                "automatically and turned into categorised transactions in $currency.",
            "Do I have to type my $name transactions in by hand?" to
                "No. PennyWise reads the transaction SMS $name already sends you and creates the " +
                "entry itself — amount, merchant, date, account and running balance where the " +
                "message includes it.",
            "Does PennyWise need my $name net-banking login or password?" to
                "Never. PennyWise has no login to your bank and no connection to $name at all. It " +
                "only reads the SMS already sitting on your phone, which is why it needs SMS " +
                "permission and nothing else.",
            "Is my $name transaction data sent anywhere?" to
                "No. Parsing runs entirely on your device. There is no server, no account and no " +
                "analytics on your financial data, and the source code is public so this can be " +
                "verified.",
            "What if a message from $name is read incorrectly?" to
                "You can paste the message into the parser tool on this site to see exactly what " +
                "PennyWise extracts, and report it in one click if it is wrong. Parser fixes ship " +
                "in the next release.",
        )
    }

    suspend fun ApplicationCall.respondBankPage(bank: BankPage) {
        val name = bank.name
        val country = bank.country
        val path = "/banks/${bank.slug}"
        val pageTitle = bankTitle(name)
        val description = clampDescription(
            "Turn your $name SMS alerts into an automatic expense tracker. Free, open source, " +
                "works offline on your phone — no bank login, nothing uploaded."
        )

        respondHtml(HttpStatusCode.OK) {
            lang = "en"
            head {
                commonHead(pageTitle, description, path)
                style { unsafe { +commonStyles } }
            }
            body {
                siteHeader(currentPage = "banks")
                div(classes = "container") {
                    div(classes = "prose") {
                        breadcrumbs(
                            "Home" to "/",
                            "Supported banks" to "/banks",
                            "${country.country}" to "/banks/country/${SupportedBanks.countrySlug(country.country)}",
                            name to null,
                        )

                        h1 { +"How to track $name transactions automatically" }
                        p(classes = "lede") {
                            +"$name already texts you every time money moves. PennyWise reads those "
                            +"messages on your phone and builds your spending record from them — no "
                            +"manual entry, no bank login, nothing leaves the device."
                        }

                        // The demo is the argument, so it runs before the sales pitch.
                        val sample = SupportedBanks.sample(name)
                        if (sample != null) parseStrip(bank, sample)

                        downloadCta()

                        h2 { +"How it works" }
                        ol {
                            li {
                                +"Install PennyWise and grant SMS permission. That permission is the "
                                +"whole product — it is how the app sees your $name alerts."
                            }
                            li {
                                +"PennyWise scans the $name messages already on your phone and "
                                +"back-fills your transaction history in seconds."
                            }
                            li {
                                +"Every new $name alert is picked up as it arrives and categorised "
                                +"automatically. Budgets and spending trends update on their own."
                            }
                        }

                        // Only spell the field list out when there is no live demo above
                        // it. After the parse strip this is the same information again in
                        // weaker form, and repeating it is what makes a page feel padded.
                        if (sample == null) {
                            h2 { +"What PennyWise reads from your $name messages" }
                            ul {
                                li { +"Amount and whether it was money in or money out" }
                                li { +"Merchant or counterparty name, cleaned up for readability" }
                                li { +"The last 4 digits of the account or card, when the message includes them" }
                                li { +"Running balance and available credit limit, when the message includes them" }
                                li {
                                    // "UTR" is the Indian NEFT/RTGS term and a keyword worth carrying on
                                    // those pages — but it means nothing on an M-Pesa or Telebirr page.
                                    if (country.currency == "INR") {
                                        +"Reference or UTR number, for matching against your statement"
                                    } else {
                                        +"Reference or transaction ID, for matching against your statement"
                                    }
                                }
                                li { +"Currency — ${country.currency} (${country.symbol}) for $name, with multi-currency support built in" }
                            }
                            p {
                                +"Everything above is extracted by an on-device parser written "
                                +"specifically for $name's message formats. You can see it run on your own "
                                +"message, without installing anything: "
                                a(href = "/tools/parse") { +"open the parser tool" }
                                +" and paste in one of your $name messages."
                            }
                        }

                        h2 { +"Why not just use a bank-login app?" }
                        p {
                            +"Apps that aggregate accounts ask for your net-banking or account "
                            +"credentials and hold your transaction history on their servers. PennyWise "
                            +"asks for neither. It has no account system, makes no network calls to read "
                            +"your money, and its full source is published under AGPL-3.0 so the claim is "
                            +"checkable rather than promised."
                        }

                        faqSection(bankFaqs(bank))

                        h2 { +"Other banks PennyWise supports in ${country.country}" }
                        p(classes = "muted") {
                            +"${country.bankCount} supported in ${country.country} — "
                            a(href = "/banks/country/${SupportedBanks.countrySlug(country.country)}") {
                                +"see them all"
                            }
                            +"."
                        }
                        div(classes = "grid") {
                            country.banks.filter { it != name }.take(11).forEach { other ->
                                val slug = SupportedBanks.banks.first { it.name == other }.slug
                                a(href = "/banks/$slug") { +other }
                            }
                        }

                        downloadCta()
                        pageFooter()

                        jsonLd(
                            breadcrumbJsonLd(
                                listOf(
                                    "Supported banks" to "/banks",
                                    country.country to "/banks/country/${SupportedBanks.countrySlug(country.country)}",
                                    name to path,
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------- country pages

    suspend fun ApplicationCall.respondCountryPage(country: CountryEntry) {
        val slug = SupportedBanks.countrySlug(country.country)
        val path = "/banks/country/$slug"
        val pageTitle = "Expense tracker for ${country.country} banks"
        val description = clampDescription(
            "PennyWise reads SMS alerts from ${country.bankCount} banks in ${country.country} " +
                "and tracks your spending in ${country.currency} automatically. Free, open " +
                "source, fully offline."
        )

        respondHtml(HttpStatusCode.OK) {
            lang = "en"
            head {
                commonHead(pageTitle, description, path)
                style { unsafe { +commonStyles } }
            }
            body {
                siteHeader(currentPage = "banks")
                div(classes = "container") {
                    div(classes = "prose") {
                        breadcrumbs(
                            "Home" to "/",
                            "Supported banks" to "/banks",
                            country.country to null,
                        )

                        h1 { +"${country.flag} Automatic expense tracking for ${country.country}" }
                        p(classes = "lede") {
                            +"PennyWise recognises transaction messages from ${country.bankCount} "
                            +"banks and payment services in ${country.country}, and tracks everything "
                            +"in ${country.currency} (${country.symbol}) without you entering a single "
                            +"amount by hand."
                        }

                        downloadCta()

                        h2 { +"Supported in ${country.country} (${country.bankCount})" }
                        div(classes = "grid") {
                            country.banks.forEach { bank ->
                                val bankSlug = SupportedBanks.banks.first { it.name == bank }.slug
                                a(href = "/banks/$bankSlug") { +bank }
                            }
                        }

                        h2 { +"How it works" }
                        p {
                            +"Your bank already sends an SMS for every transaction. PennyWise reads "
                            +"those messages on your device, extracts the amount, merchant, account and "
                            +"balance, and files each one into a searchable timeline with budgets and "
                            +"spending trends on top."
                        }
                        p {
                            +"There is no account to create and no bank login to hand over. Parsing "
                            +"runs offline, and the source code is public under AGPL-3.0."
                        }

                        faqSection(
                            listOf(
                                "Which ${country.country} banks does PennyWise support?" to
                                    "${country.bankCount} banks and payment services, listed in full on " +
                                    "this page. If yours is missing you can report it and a parser can " +
                                    "usually be added from a couple of sample messages.",
                                "Does it work in ${country.currency}?" to
                                    "Yes. ${country.country} transactions are tracked in " +
                                    "${country.currency} (${country.symbol}), and PennyWise handles " +
                                    "multiple currencies at once without ever adding them together.",
                                "Does PennyWise need an internet connection?" to
                                    "No. Reading messages, categorising transactions and the built-in AI " +
                                    "assistant all run on the phone itself, so it works offline.",
                                "What does it cost?" to
                                    "The app is free. An optional Pro upgrade adds PDF statement imports, " +
                                    "CSV export, unlimited custom rules and duplicate-account merge.",
                            )
                        )

                        h2 { +"Other countries" }
                        div(classes = "grid") {
                            SupportedBanks.countries
                                .filter { it.country != country.country }
                                .forEach {
                                    a(href = "/banks/country/${SupportedBanks.countrySlug(it.country)}") {
                                        +"${it.flag} ${it.country} (${it.bankCount})"
                                    }
                                }
                        }

                        downloadCta()
                        pageFooter()

                        jsonLd(
                            breadcrumbJsonLd(
                                listOf(
                                    "Supported banks" to "/banks",
                                    country.country to path,
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------- banks index

    suspend fun ApplicationCall.respondBanksIndex() {
        val cat = SupportedBanks.catalogue
        val path = "/banks"
        val pageTitle =
            "Supported banks — ${cat.totalBanks} banks, ${cat.totalCountries} countries"
        val description = clampDescription(
            "Every bank and payment service PennyWise reads SMS from: ${cat.totalBanks} across " +
                "${cat.totalCountries} countries, with automatic, offline expense tracking."
        )

        respondHtml(HttpStatusCode.OK) {
            lang = "en"
            head {
                commonHead(pageTitle, description, path)
                style { unsafe { +commonStyles } }
            }
            body {
                siteHeader(currentPage = "banks")
                div(classes = "container") {
                    div(classes = "prose") {
                        breadcrumbs("Home" to "/", "Supported banks" to null)

                        h1 { +"Supported banks and payment services" }
                        p(classes = "lede") {
                            +"PennyWise turns transaction SMS into tracked spending for "
                            +"${cat.totalBanks} banks and payment services across "
                            +"${cat.totalCountries} countries — all parsed on your own device."
                        }

                        downloadCta()

                        cat.countries.forEach { country ->
                            div(classes = "country-head") {
                                h2 {
                                    a(href = "/banks/country/${SupportedBanks.countrySlug(country.country)}") {
                                        +"${country.flag} ${country.country}"
                                    }
                                }
                                span(classes = "pill") {
                                    +"${country.bankCount} · ${country.currency} ${country.symbol}"
                                }
                            }
                            div(classes = "grid") {
                                country.banks.forEach { bank ->
                                    val bankSlug = SupportedBanks.banks.first { it.name == bank }.slug
                                    a(href = "/banks/$bankSlug") { +bank }
                                }
                            }
                        }

                        h2 { +"Bank missing?" }
                        p {
                            +"Parsers are written from real message formats, so a missing bank usually "
                            +"just needs a couple of sample messages. Paste one into the "
                            a(href = "/tools/parse") { +"parser tool" }
                            +" and report it, or open an issue on "
                            a(href = "https://github.com/sarim2000/pennywiseai-tracker/issues") {
                                +"GitHub"
                            }
                            +"."
                        }

                        pageFooter()
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------ sitemap/robots

    fun buildSitemap(): String {
        val urls = buildList {
            // "/" and "/tools/parse" render the same page; only the canonical one is
            // listed, otherwise the two compete with each other for the same query.
            add("/" to "1.0")
            add("/banks" to "0.9")
            SupportedBanks.countries.forEach {
                add("/banks/country/${SupportedBanks.countrySlug(it.country)}" to "0.7")
            }
            SupportedBanks.banks.forEach { add("/banks/${it.slug}" to "0.6") }
        }
        val body = urls.joinToString("\n") { (path, priority) ->
            "  <url><loc>$SITE_ORIGIN$path</loc><priority>$priority</priority></url>"
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
        """.trimIndent() + "\n" + body + "\n</urlset>\n"
    }

    fun buildRobots(): String = """
        User-agent: *
        Allow: /

        Sitemap: $SITE_ORIGIN/sitemap.xml
    """.trimIndent() + "\n"
}
