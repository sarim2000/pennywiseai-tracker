package com.example.ui

import com.example.BankPage
import com.example.CountryEntry
import com.example.SupportedBanks
import com.example.ui.SharedComponents.PLAY_STORE_URL
import com.example.ui.SharedComponents.SITE_ORIGIN
import com.example.ui.SharedComponents.commonHead
import com.example.ui.SharedComponents.commonStyles
import com.example.ui.SharedComponents.downloadButtons
import com.example.ui.SharedComponents.siteFooter
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
                span { +"Read on your phone" }
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
                +"A real ${bank.name} message format, as the app's parser reads it. Try your own "
                +"message in the "
                a(href = "/tools/parse") { +"parser tool" }
                +", which covers fewer banks than the app."
            }
        }
    }

    private const val SOURCE_URL = "https://github.com/sarim2000/pennywiseai-tracker"

    private fun FlowContent.downloadCta() {
        div(classes = "cta-row") {
            a(href = PLAY_STORE_URL, classes = "btn btn-filled btn-lg") {
                attributes["rel"] = "noopener"
                +"Get PennyWise on Google Play"
            }
            a(href = SOURCE_URL, classes = "btn btn-outlined btn-lg") {
                attributes["rel"] = "noopener"
                +"View the source"
            }
        }
    }

    /**
     * The page's opening block: a tonal hero holding the title, lede and the download
     * row. [art] is a decorative side piece (owl or a count badge), hidden on phones.
     */
    private fun FlowContent.hero(art: (DIV.() -> Unit)? = null, text: FlowContent.() -> Unit) {
        section(classes = "bank-hero") {
            div(classes = "bank-hero-text") {
                text()
                downloadCta()
            }
            if (art != null) {
                div(classes = "bank-hero-art") {
                    attributes["aria-hidden"] = "true"
                    art()
                }
            }
        }
    }

    private fun HTMLTag.owl() {
        unsafe {
            +"""<picture><source srcset="/static/owl-dark.svg" media="(prefers-color-scheme: dark)"><img src="/static/owl.svg" alt="" width="132" height="132" class="hero-owl"></picture>"""
        }
    }

    private fun FlowContent.countBadge(count: Int, label: String) {
        div(classes = "hero-badge shape-sunny") {
            span(classes = "hero-badge-n") { +count.toString() }
            span(classes = "hero-badge-l") { +label }
        }
    }

    /** Closing call to action: every install channel, plus the source for the sceptical. */
    private fun FlowContent.endCta(title: String) {
        section(classes = "end-cta") {
            h2 { +title }
            p {
                +"Free and open source. Parsing runs on your phone, and your data leaves it only through opt-in features you start. "
                a(href = SOURCE_URL) {
                    attributes["rel"] = "noopener"
                    +"View the source"
                }
                +"."
            }
            downloadButtons()
        }
    }

    private fun FlowContent.pageFooter() {
        div(classes = "footnote") {
            +"PennyWise is a free, open-source (AGPL-3.0) expense tracker. Bank SMS are parsed "
            +"entirely on your device, and there is no account to create. "
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
                "only reads the SMS $name sends you, which is why on Android it needs SMS " +
                "permission and nothing else. iPhone apps can't read texts, so there a Shortcuts " +
                "automation passes each bank SMS to PennyWise.",
            "Is my $name transaction data sent anywhere?" to
                "Only if you choose to. Parsing runs entirely on your device, and there is no account " +
                "and no analytics. Your data leaves the phone only through opt-in features you start, " +
                "like reporting a parsing problem. The source code is public so this can be verified.",
            "What if a message from $name is read incorrectly?" to
                "You can paste the message into the parser tool on this site to see what it " +
                "extracts, and report it in one click if it is wrong. The web demo covers fewer " +
                "banks than the app. Parser fixes ship in the next release.",
        )
    }

    suspend fun ApplicationCall.respondBankPage(bank: BankPage) {
        val name = bank.name
        val country = bank.country
        val path = "/banks/${bank.slug}"
        val pageTitle = bankTitle(name)
        val description = clampDescription(
            "Turn your $name SMS alerts into an automatic expense tracker. Free, open source, " +
                "works offline on your phone, with no bank login."
        )

        respondHtml(HttpStatusCode.OK) {
            lang = "en"
            head {
                commonHead(pageTitle, description, path)
                style { unsafe { +commonStyles; +bankStyles } }
            }
            body {
                siteHeader(currentPage = "banks")
                div(classes = "container") {
                    div(classes = "prose bank-page") {
                        breadcrumbs(
                            "Home" to "/",
                            "Supported banks" to "/banks",
                            "${country.country}" to "/banks/country/${SupportedBanks.countrySlug(country.country)}",
                            name to null,
                        )

                        hero(art = { owl() }) {
                            h1 { +"How to track $name transactions automatically" }
                            p(classes = "lede") {
                                +"$name already texts you every time money moves. PennyWise reads those "
                                +"messages on your phone and builds your spending record from them — no "
                                +"manual entry and no bank login."
                            }
                        }

                        // The demo is the argument, so it runs straight after the hero and
                        // before any of the explanation below.
                        val sample = SupportedBanks.sample(name)
                        if (sample != null) parseStrip(bank, sample)

                        h2 { +"How it works" }
                        ol(classes = "steps") {
                            li {
                                +"Install PennyWise. On Android, grant SMS permission; it is how the app "
                                +"sees your $name alerts. iPhone apps can't read texts, so there a "
                                +"Shortcuts automation passes each bank SMS to PennyWise."
                            }
                            li {
                                +"On Android, PennyWise scans the $name messages already on your phone and "
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
                                +"specifically for $name's message formats. You can try your own "
                                +"message, without installing anything: "
                                a(href = "/tools/parse") { +"open the parser tool" }
                                +" and paste in one of your $name messages. The web demo covers fewer "
                                +"banks than the app."
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

                        endCta("Start tracking $name automatically")
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
                siteFooter()
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
                "source, and parsed on your phone."
        )

        respondHtml(HttpStatusCode.OK) {
            lang = "en"
            head {
                commonHead(pageTitle, description, path)
                style { unsafe { +commonStyles; +bankStyles } }
            }
            body {
                siteHeader(currentPage = "banks")
                div(classes = "container") {
                    div(classes = "prose bank-page") {
                        breadcrumbs(
                            "Home" to "/",
                            "Supported banks" to "/banks",
                            country.country to null,
                        )

                        hero(art = { countBadge(country.bankCount, "banks") }) {
                            h1 { +"${country.flag} Automatic expense tracking for ${country.country}" }
                            p(classes = "lede") {
                                +"PennyWise recognises transaction messages from ${country.bankCount} "
                                +"banks and payment services in ${country.country}, and tracks everything "
                                +"in ${country.currency} (${country.symbol}) without you entering a single "
                                +"amount by hand."
                            }
                        }

                        div(classes = "country-head") {
                            h2 { +"Supported in ${country.country}" }
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
                                    "The app is free. An optional Pro upgrade lifts the free limits on PDF " +
                                    "statement imports, CSV export and custom rules, and adds duplicate-account merge.",
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

                        endCta("Start tracking your ${country.country} banks")
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
                siteFooter()
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
                style { unsafe { +commonStyles; +bankStyles } }
            }
            body {
                siteHeader(currentPage = "banks")
                div(classes = "container") {
                    div(classes = "prose bank-page") {
                        breadcrumbs("Home" to "/", "Supported banks" to null)

                        hero(art = { owl() }) {
                            h1 { +"Supported banks and payment services" }
                            p(classes = "lede") {
                                +"PennyWise turns transaction SMS into tracked spending for "
                                +"${cat.totalBanks} banks and payment services across "
                                +"${cat.totalCountries} countries — all parsed on your own device."
                            }
                        }

                        // 26 countries make a long page; a chip row lets people jump to theirs.
                        nav(classes = "country-jump") {
                            attributes["aria-label"] = "Jump to a country"
                            cat.countries.forEach { country ->
                                a(href = "#${SupportedBanks.countrySlug(country.country)}") {
                                    +"${country.flag} ${country.country}"
                                }
                            }
                        }

                        cat.countries.forEach { country ->
                            val countrySlug = SupportedBanks.countrySlug(country.country)
                            div(classes = "country-head") {
                                id = countrySlug
                                h2 {
                                    a(href = "/banks/country/$countrySlug") {
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
                siteFooter()
            }
        }
    }

    // ------------------------------------------------------------------ styles

    /**
     * Page-specific layer over [commonStyles]: the tonal hero, the parse strip as a
     * showcase card, numbered steps and the closing CTA. Role tokens only, so every rule
     * works in light and dark.
     */
    private val bankStyles = """
        .bank-hero { display: flex; align-items: center; gap: 24px; background: var(--primary-container); color: var(--on-primary-container); border-radius: var(--r-2xl); padding: 36px 36px 30px; margin: 0 0 8px; }
        .bank-hero-text { flex: 1; min-width: 0; }
        .bank-hero h1 { color: var(--on-primary-container); font-size: clamp(32px, 5vw, 46px); margin: 0 0 14px; overflow-wrap: anywhere; }
        .bank-hero .lede { color: var(--on-primary-container) !important; margin: 0; }
        .bank-hero .cta-row { margin: 24px 0 0; }
        .bank-hero a.btn-outlined { color: var(--on-primary-container); border-color: color-mix(in srgb, var(--on-primary-container) 40%, transparent); }
        .bank-hero a.btn-outlined:hover { background: color-mix(in srgb, var(--on-primary-container) 10%, transparent); }
        .bank-hero-art { flex: none; align-self: flex-end; }
        .bank-hero-art picture, .hero-owl { display: block; width: 132px; height: 132px; }
        .hero-badge { width: 148px; height: 148px; background: var(--primary); color: var(--on-primary); display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; align-self: center; }
        .hero-badge-n { font-family: var(--display); font-size: 48px; font-weight: 800; line-height: 1; letter-spacing: -.02em; font-variant-numeric: tabular-nums; }
        .hero-badge-l { font-size: 14px; font-weight: 600; margin-top: 4px; }

        /* Segmented lists read better on a tonal tile than on the near-identical page bg. */
        .bank-page .grid { border-radius: var(--r-lg); overflow: hidden; }
        .bank-page .grid a { background: var(--surface-container); }
        .bank-page .grid a:focus-visible { outline-offset: -3px; border-radius: var(--r-lg); }
        .bank-page .faq details { background: var(--surface-container); }
        .bank-page .faq details summary::after { background: var(--surface-highest); }

        /* The parse strip as a showcase card. */
        .bank-page .parse { background: var(--surface-low); border-radius: var(--r-2xl); padding: 24px; margin: 24px 0 8px; }
        .bank-page .parse .eyebrow { font-size: 16px; font-weight: 600; color: var(--on-bg); margin-bottom: 14px; }
        .bank-page .parse .sms { background: var(--surface-high); }
        .bank-page .parse-note { margin: 14px 2px 0; }

        .country-jump { display: flex; flex-wrap: wrap; gap: 8px; margin: 24px 0 0; }
        .country-jump a { display: inline-flex; align-items: center; height: 36px; padding: 0 14px; border-radius: var(--r-full); background: var(--surface-container); color: var(--on-bg); text-decoration: none; font-size: 14px; font-weight: 500; transition: background .15s; }
        .country-jump a:hover { background: var(--secondary-container); color: var(--on-secondary-container); }
        .country-head { scroll-margin-top: 88px; }

        .prose ol.steps { list-style: none; padding: 0; margin: 16px 0; counter-reset: step; display: grid; gap: 4px; }
        .prose ol.steps li { counter-increment: step; display: flex; gap: 16px; align-items: flex-start; margin: 0; padding: 18px 20px; background: var(--surface-container); color: var(--on-bg); border-radius: var(--r-xs); }
        .prose ol.steps li:first-child { border-top-left-radius: var(--r-lg); border-top-right-radius: var(--r-lg); }
        .prose ol.steps li:last-child { border-bottom-left-radius: var(--r-lg); border-bottom-right-radius: var(--r-lg); }
        .prose ol.steps li::before { content: counter(step); flex: none; width: 32px; height: 32px; border-radius: 50%; display: grid; place-items: center; background: var(--primary); color: var(--on-primary); font-family: var(--display); font-weight: 700; font-size: 15px; }

        .end-cta { margin: 56px 0 0; padding: 32px; background: var(--surface-low); border-radius: var(--r-2xl); }
        .prose .end-cta h2 { margin: 0 0 8px; font-size: 28px; font-weight: 800; }
        .end-cta p { margin: 0 0 22px; }

        @media (max-width: 720px) {
          .bank-hero { padding: 26px 20px 22px; border-radius: var(--r-xl); }
          .bank-hero-art { display: none; }
          .bank-page .parse { padding: 16px; border-radius: var(--r-xl); }
          .prose ol.steps li { padding: 16px; gap: 12px; }
          .end-cta { padding: 24px 20px; border-radius: var(--r-xl); }
          .prose .end-cta h2 { font-size: 24px; }
        }
    """.trimIndent()

    // ------------------------------------------------------------ sitemap/robots

    fun buildSitemap(): String {
        val urls = buildList {
            add("/" to "1.0")
            add("/tools/parse" to "0.9")
            add("/pro" to "1.0")
            add("/terms" to "0.3")
            GuideViews.paths.forEach { add(it to if (it == "/guide") "0.9" else "0.7") }
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
