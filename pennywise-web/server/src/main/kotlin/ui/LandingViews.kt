package com.example.ui

import com.example.SupportedBanks
import com.example.ui.SharedComponents.APP_STORE_URL
import com.example.ui.SharedComponents.DISCORD_URL
import com.example.ui.SharedComponents.GITHUB_URL
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
import kotlinx.html.*

/** The landing page at "/": what PennyWise is, in the app's own Expressive look, and where to get it. */
object LandingViews {

    suspend fun ApplicationCall.respondLandingPage() {
        val bankCount = SupportedBanks.catalogue.totalBanks
        val countryCount = SupportedBanks.catalogue.totalCountries
        respondHtml(HttpStatusCode.OK) {
            lang = "en"
            head {
                commonHead(
                    "PennyWise — private expense tracker that reads your bank SMS",
                    "PennyWise turns the SMS your bank already sends into a private, zero-setup " +
                        "expense tracker with on-device AI. Free, open source, $bankCount banks in $countryCount countries.",
                    "/",
                )
                // The app's "report this SMS" link opens /#message=…&sender=… (SmsReportUrlBuilder),
                // and released versions keep doing so. The hash never reaches the server, so hand
                // those visits to the parser here, before anything paints.
                script {
                    unsafe {
                        +"if(/(^[#?]|&)(message|sender)=/.test(location.hash)||/(^[#?]|&)(message|sender)=/.test(location.search))location.replace('/tools/parse'+location.search+location.hash);"
                    }
                }
                style { unsafe { +commonStyles; +landingStyles } }
                script(type = "application/ld+json") { unsafe { +jsonLd() } }
            }
            body {
                siteHeader()
                main {
                    hero(bankCount, countryCount)
                    howItWorks()
                    features()
                    parserDemo()
                    banks(bankCount, countryCount)
                    privacy()
                    proTeaser()
                    community()
                    finalCta()
                }
                siteFooter()
            }
        }
    }

    // ---------------------------------------------------------------- sections

    private fun FlowContent.hero(bankCount: Int, countryCount: Int) {
        section(classes = "lp-hero") {
            div(classes = "lp-wrap lp-hero-grid") {
                div(classes = "lp-hero-copy") {
                    span(classes = "lp-chip") {
                        span(classes = "lp-chip-dot") {}
                        +"Free · Open source · AI that stays on your phone"
                    }
                    h1 { +"Your bank already texts you. PennyWise does the rest." }
                    p(classes = "lp-lede") {
                        +"Every transaction SMS becomes a clean, searchable money timeline, with budgets, bills, "
                        +"insights and an assistant you can ask anything. No account, no sign-up, and your data leaves the phone "
                        +"only through opt-in features you start."
                    }
                    downloadButtons()
                    ul(classes = "lp-proof") {
                        li { b { +"$bankCount" }; +" banks" }
                        li { b { +"$countryCount" }; +" countries" }
                        li { b { +"0" }; +" accounts to create" }
                    }
                }
                div(classes = "lp-hero-art") {
                    attributes["aria-hidden"] = "true"
                    div(classes = "lp-blob shape-cookie") {}
                    phone("home", "PennyWise home screen")
                    div(classes = "lp-float lp-float-sms") {
                        span(classes = "lp-float-k") { +"New from SMS" }
                        span(classes = "lp-float-v") { +"₹1,250 · FreshMart" }
                    }
                    div(classes = "lp-float lp-float-owl") {
                        owl()
                        span { +"On track this month" }
                    }
                }
            }
        }
    }

    private fun FlowContent.howItWorks() {
        section(classes = "lp-section") {
            id = "how"
            div(classes = "lp-wrap") {
                sectionHead("How it works", "Three steps, about a minute.")
                ol(classes = "lp-steps") {
                    step("shape-clover", "1", "Let it read your bank SMS",
                        "On Android, PennyWise asks for read-only SMS access and never sends messages or changes your inbox. " +
                            "iPhone apps can't read texts, so there a Shortcuts automation passes each bank SMS to PennyWise.")
                    step("shape-cookie", "2", "Transactions appear instantly",
                        "Amount, merchant, account and date are pulled from each bank SMS as it arrives. " +
                            "On Android, the ones already in your inbox come in too.")
                    step("shape-sunny", "3", "See where it goes",
                        "Insights, budgets, bills and a month-end forecast, plus an on-device assistant for everything else.")
                }
            }
        }
    }

    private fun FlowContent.features() {
        section(classes = "lp-section") {
            id = "features"
            div(classes = "lp-wrap") {
                sectionHead("Everything in one calm place", "Built with Material 3 Expressive, in light and dark.")
                div(classes = "lp-bento") {
                    div(classes = "lp-tile lp-tile-ask") {
                        div(classes = "lp-tile-copy") {
                            badge("shape-cookie", "✦")
                            h3 { +"Ask about your money" }
                            p { +"“What did I spend on food?” “Bills due next week?” The assistant runs Qwen 2.5 entirely on your phone, and you can dictate with the mic." }
                        }
                        phone("ask", "", small = true)
                    }
                    div(classes = "lp-tile lp-tile-ins") {
                        div(classes = "lp-tile-copy") {
                            badge("shape-clover", "▮")
                            h3 { +"Insights that look ahead" }
                            p { +"Trends by category, merchant or account, month-over-month comparisons and a forecast for the end of the month." }
                        }
                        phone("ins", "", small = true)
                    }
                    tile("lp-tile-plan", "shape-sunny", "◎", "Budgets, bills & goals",
                        "Budgets with custom cycles, safe-to-spend and a daily allowance. Subscriptions are spotted for you, with reminders before they’re due.")
                    tile("lp-tile-cur", "shape-burst", "¤", "Multi-currency, done right",
                        "Every account keeps its own currency. Totals are shown per currency and never mixed.")
                    tile("", "shape-clover", "⇪", "Import statements",
                        "PDF (even password-protected), Excel and CSV, with column mapping, preview and a balance check.")
                    tile("", "shape-cookie", "▦", "Widgets & quick add",
                        "Budget, recent-transaction, spending and goal widgets, plus a Quick Settings tile.")
                    tile("", "shape-sunny", "⟲", "Backups you own",
                        "Full backup and restore to a file or a folder you choose, and CSV export whenever you want it.")
                    tile("", "shape-burst", "⚑", "Rules that learn",
                        "Categories, tags and rules with several conditions. PennyWise remembers how you sort each merchant.")
                }
            }
        }
    }

    private fun FlowContent.parserDemo() {
        section(classes = "lp-section") {
            id = "parser"
            div(classes = "lp-wrap lp-split") {
                div {
                    sectionHead("See exactly what it reads", null)
                    p(classes = "lp-body") {
                        +"Try a bank SMS in the web demo, or open a bank's page for a sample, and watch the amount, "
                        +"merchant, account and reference come out, colour-matched to the text they came from. "
                        +"The demo covers fewer banks than the app does."
                    }
                    div(classes = "cta-row") {
                        a(href = "/tools/parse", classes = "btn btn-filled btn-lg") { +"Try the parser demo" }
                        a(href = "/banks", classes = "btn btn-outlined btn-lg") { +"Supported banks" }
                    }
                }
                // Illustrative message: made-up merchant, masked account, invented reference.
                div(classes = "parse") {
                    div(classes = "sms") {
                        div(classes = "sms-from") { span(classes = "sms-dot") {}; b { +"AX-BANK" }; +" · just now" }
                        div(classes = "sms-body") {
                            +"Rs."; mark(classes = "f-amount") { +"1,250.00" }; +" debited from A/c "
                            mark(classes = "f-account") { +"XX1234" }; +" to "
                            mark(classes = "f-merchant") { +"FRESHMART" }; +" on 12-Nov. UPI Ref "
                            mark(classes = "f-ref") { +"431298765012" }; +"."
                        }
                    }
                    div(classes = "parse-arrow") { i {}; span { +"PennyWise reads" } }
                    div(classes = "parsed") {
                        div(classes = "parsed-top") {
                            span(classes = "parsed-amount") { +"₹1,250.00" }
                            span(classes = "tag") { +"Expense" }
                        }
                        div(classes = "fields") {
                            field("Merchant", "FRESHMART", "f-merchant")
                            field("Account", "••1234", "f-account")
                            field("Reference", "431298765012", "f-ref")
                        }
                    }
                }
            }
        }
    }

    private fun FlowContent.banks(bankCount: Int, countryCount: Int) {
        section(classes = "lp-section") {
            id = "banks"
            div(classes = "lp-wrap") {
                sectionHead("$bankCount banks across $countryCount countries", "SMS from banks we don’t support yet still show up as suggested transactions.")
                div(classes = "lp-countries") {
                    SupportedBanks.countries.sortedByDescending { it.bankCount }.forEach { c ->
                        a(href = "/banks/country/${SupportedBanks.countrySlug(c.country)}", classes = "lp-country") {
                            span(classes = "lp-flag") { +c.flag }
                            span { +c.country }
                            span(classes = "lp-count") { +"${c.bankCount}" }
                        }
                    }
                }
                div(classes = "cta-row") {
                    a(href = "/banks", classes = "btn btn-tonal") { +"Browse every bank →" }
                }
            }
        }
    }

    private fun FlowContent.privacy() {
        section(classes = "lp-section") {
            id = "privacy"
            div(classes = "lp-wrap") {
                div(classes = "lp-privacy") {
                    div(classes = "lp-privacy-head") {
                        h2 { +"Private by design, not by promise" }
                        p { +"The code is open, so you don’t have to take our word for it." }
                    }
                    ul(classes = "lp-privacy-list") {
                        privacyItem("No account, no sign-up", "Your transactions are stored on your phone, not on a PennyWise server.")
                        privacyItem("AI runs on-device", "SMS parsing, storage and the assistant all run on your phone.")
                        privacyItem("No ads, no trackers", "No analytics SDKs. Your data leaves the phone only through opt-in features you start, like reporting a parsing problem.")
                        privacyItem("Open source (AGPL v3)", "Read every line on GitHub, or build it yourself.")
                    }
                }
            }
        }
    }

    private fun FlowContent.proTeaser() {
        section(classes = "lp-section") {
            div(classes = "lp-wrap") {
                a(href = "/pro", classes = "lp-pro") {
                    div(classes = "lp-pro-badge shape-sunny") { +"PRO" }
                    div(classes = "lp-pro-copy") {
                        h2 { +"Support the solo developer with PennyWise Pro" }
                        p { +"AI access from Claude or ChatGPT, webhooks and more. One-time lifetime option. F-Droid builds include every Pro feature for free." }
                    }
                    span(classes = "btn btn-filled lp-pro-cta") { +"See Pro" }
                }
            }
        }
    }

    private fun FlowContent.community() {
        section(classes = "lp-section") {
            div(classes = "lp-wrap") {
                sectionHead("Made in the open", "Ask questions, report problems, follow along.")
                div(classes = "lp-links") {
                    linkCard(GITHUB_URL, "GitHub", "Source code, issues and releases", external = true)
                    linkCard(DISCORD_URL, "Discord", "Chat with the community and the developer", external = true)
                    linkCard("/guide", "User guide", "Every feature, step by step, with screenshots")
                }
            }
        }
    }

    private fun FlowContent.finalCta() {
        section(classes = "lp-section") {
            id = "download"
            div(classes = "lp-wrap") {
                div(classes = "lp-final") {
                    div(classes = "lp-final-owl shape-cookie") { owl() }
                    h2 { +"Get PennyWise" }
                    p { +"Android 8.0 and newer, and iPhone through a Shortcuts automation. Free, with no ads." }
                    downloadButtons()
                }
            }
        }
    }

    // ---------------------------------------------------------------- pieces

    private fun FlowContent.sectionHead(title: String, sub: String?) {
        div(classes = "lp-head") {
            h2 { +title }
            if (sub != null) p { +sub }
        }
    }

    private fun OL.step(shape: String, n: String, title: String, body: String) {
        li(classes = "lp-step") {
            span(classes = "lp-step-n $shape") { +n }
            h3 { +title }
            p { +body }
        }
    }

    private fun FlowContent.badge(shape: String, glyph: String) {
        span(classes = "lp-badge $shape") { attributes["aria-hidden"] = "true"; +glyph }
    }

    private fun FlowContent.tile(extra: String, shape: String, glyph: String, title: String, body: String) {
        div(classes = "lp-tile $extra".trim()) {
            div(classes = "lp-tile-copy") {
                badge(shape, glyph)
                h3 { +title }
                p { +body }
            }
        }
    }

    private fun FlowContent.field(k: String, v: String, cls: String) {
        div {
            div(classes = "field-k") { +k }
            div(classes = "field-v $cls") { +v }
        }
    }

    private fun UL.privacyItem(title: String, body: String) {
        li {
            span(classes = "lp-check") { attributes["aria-hidden"] = "true"; +"✓" }
            div { b { +title }; span { +body } }
        }
    }

    private fun FlowContent.linkCard(href: String, title: String, body: String, external: Boolean = false) {
        a(href = href, classes = "lp-link") {
            if (external) { target = "_blank"; rel = "noopener" }
            b { +title }
            span { +body }
            span(classes = "lp-link-arrow") { attributes["aria-hidden"] = "true"; +"→" }
        }
    }

    /** A phone frame holding a real screenshot of the app, swapped for the dark one in dark mode. */
    private fun FlowContent.phone(shot: String, alt: String, small: Boolean = false) {
        div(classes = if (small) "lp-phone lp-phone-sm" else "lp-phone") {
            unsafe {
                +"""<picture><source srcset="/static/app/d-$shot.webp" media="(prefers-color-scheme: dark)"><img src="/static/app/$shot.webp" alt="$alt" width="540" height="1105" loading="${if (small) "lazy" else "eager"}"></picture>"""
            }
        }
    }

    private fun HTMLTag.owl() {
        unsafe {
            +"""<picture class="lp-owl"><source srcset="/static/owl-dark.svg" media="(prefers-color-scheme: dark)"><img src="/static/owl.svg" alt="" width="64" height="64"></picture>"""
        }
    }

    private fun jsonLd(): String = """
        {"@context":"https://schema.org","@type":"SoftwareApplication","name":"PennyWise AI",
         "operatingSystem":"Android, iOS","applicationCategory":"FinanceApplication",
         "description":"Private, on-device expense tracker that reads your bank SMS.",
         "url":"$SITE_ORIGIN/","image":"$SITE_ORIGIN/static/logo.png",
         "offers":{"@type":"Offer","price":"0","priceCurrency":"USD"},
         "sameAs":["$PLAY_STORE_URL","$APP_STORE_URL","$GITHUB_URL"]}
    """.trimIndent()

    private val landingStyles = """
        .header { margin-bottom: 0; }
        main { overflow-x: clip; }
        .lp-wrap { max-width: 1160px; margin: 0 auto; padding: 0 24px; }
        .lp-section { padding: 56px 0; }
        .lp-head { max-width: 640px; margin-bottom: 28px; }
        .lp-head h2 { font-size: clamp(28px, 4vw, 40px); font-weight: 800; line-height: 1.1; margin: 0 0 8px; }
        .lp-head p { color: var(--on-variant); font-size: 18px; margin: 0; }
        .lp-body { color: var(--on-variant); font-size: 18px; max-width: 32em; }

        /* Hero */
        .lp-hero { padding: 40px 0 48px; }
        .lp-hero-grid { display: grid; grid-template-columns: 1.25fr .75fr; gap: 48px; align-items: center; }
        .lp-chip { display: inline-flex; align-items: center; gap: 8px; height: 36px; padding: 0 16px 0 12px; border-radius: var(--r-full); background: var(--surface-container); color: var(--on-variant); font-size: 14px; font-weight: 500; }
        .lp-chip-dot { width: 10px; height: 10px; border-radius: 50%; background: var(--success); }
        .lp-hero h1 { font-size: clamp(40px, 6.4vw, 76px); line-height: 1.02; letter-spacing: -.03em; margin: 22px 0 18px; }
        .lp-lede { font-size: 20px; line-height: 1.5; color: var(--on-variant); max-width: 30em; margin: 0 0 30px; }
        .lp-hero .btn-lg { padding: 0 20px; }
        .lp-proof { list-style: none; display: flex; flex-wrap: wrap; gap: 8px 28px; padding: 0; margin: 28px 0 0; color: var(--on-variant); font-size: 15px; }
        .lp-proof b { color: var(--on-bg); font-size: 22px; font-weight: 800; margin-right: 4px; font-variant-numeric: tabular-nums; }
        .lp-hero-art { position: relative; display: grid; place-items: center; padding: 24px 0; }
        .lp-blob { position: absolute; width: min(460px, 100%); aspect-ratio: 1; background: var(--primary-container); }
        .lp-phone { position: relative; width: min(300px, 78%); padding: 9px; border-radius: 46px; background: #111318; box-shadow: 0 0 0 1px var(--outline-variant), 0 30px 60px -28px rgba(16,24,40,.45); }
        .lp-phone img { display: block; width: 100%; height: auto; border-radius: 38px; }
        .lp-phone-sm { width: 220px; border-radius: 38px; padding: 7px; }
        .lp-phone-sm img { border-radius: 31px; }
        .lp-float { position: absolute; display: flex; align-items: center; gap: 10px; padding: 12px 18px; border-radius: var(--r-lg); background: var(--surface-lowest); box-shadow: 0 12px 32px -12px rgba(16,24,40,.28); font-size: 14px; font-weight: 600; }
        .lp-float-sms { top: 16%; left: -4%; flex-direction: column; align-items: flex-start; gap: 0; }
        .lp-float-k { font-size: 12px; font-weight: 500; color: var(--on-variant); }
        .lp-float-v { font-size: 16px; font-weight: 700; color: var(--on-bg); }
        .lp-float-owl { bottom: 12%; right: -2%; padding: 8px 18px 8px 8px; }
        .lp-owl img { display: block; width: 44px; height: 44px; }

        /* Steps */
        .lp-steps { list-style: none; padding: 0; margin: 0; display: grid; grid-template-columns: repeat(3, 1fr); gap: 4px; }
        .lp-step { background: var(--surface-low); padding: 28px; border-radius: var(--r-xs); }
        .lp-step:first-child { border-radius: var(--r-xl) var(--r-xs) var(--r-xs) var(--r-xl); }
        .lp-step:last-child { border-radius: var(--r-xs) var(--r-xl) var(--r-xl) var(--r-xs); }
        .lp-step-n { display: grid; place-items: center; width: 56px; height: 56px; background: var(--primary-container); color: var(--on-primary-container); font-size: 22px; font-weight: 800; }
        .lp-step h3 { font-size: 20px; margin: 18px 0 6px; }
        .lp-step p { color: var(--on-variant); margin: 0; }

        /* Bento */
        .lp-bento { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
        .lp-tile { background: var(--surface-low); border-radius: var(--r-xl); padding: 26px; overflow: hidden; transition: border-radius .25s; }
        .lp-tile:hover { border-radius: var(--r-2xl); }
        .lp-tile h3 { font-size: 20px; margin: 16px 0 6px; }
        .lp-tile p { color: var(--on-variant); margin: 0; font-size: 15px; }
        .lp-badge { display: grid; place-items: center; width: 48px; height: 48px; background: var(--secondary-container); color: var(--on-secondary-container); font-size: 20px; font-weight: 700; }
        /* 2 + 4 + 2 tiles: the last two share the final row. */
        .lp-tile:nth-last-child(-n+2) { grid-column: span 2; }
        .lp-tile-ask, .lp-tile-ins { grid-column: span 2; display: grid; grid-template-columns: 1fr auto; gap: 20px; padding-bottom: 0; }
        .lp-tile-ask { background: var(--primary-container); }
        .lp-tile-ask h3, .lp-tile-ask p { color: var(--on-primary-container); }
        .lp-tile-ask .lp-badge { background: var(--surface-lowest); color: var(--primary); }
        .lp-tile-ins { background: var(--surface-container); }
        .lp-tile-ask .lp-phone, .lp-tile-ins .lp-phone { align-self: end; margin-bottom: -60px; }
        .lp-tile-plan { background: var(--tertiary-container); }
        .lp-tile-plan h3, .lp-tile-plan p { color: var(--on-tertiary-container); }
        .lp-tile-plan .lp-badge { background: var(--surface-lowest); color: var(--on-tertiary-container); }
        .lp-tile-cur { background: var(--success-container); }
        .lp-tile-cur h3, .lp-tile-cur p { color: var(--on-success-container); }
        .lp-tile-cur .lp-badge { background: var(--surface-lowest); color: var(--success); }

        /* Parser demo */
        .lp-split { display: grid; grid-template-columns: 1fr 1fr; gap: 48px; align-items: center; }
        .lp-split .parse { margin: 0; }

        /* Banks */
        .lp-countries { display: flex; flex-wrap: wrap; gap: 8px; }
        .lp-country { display: inline-flex; align-items: center; gap: 8px; height: 44px; padding: 0 8px 0 14px; border-radius: var(--r-full); background: var(--surface-low); color: var(--on-bg); text-decoration: none; font-size: 15px; font-weight: 500; transition: background .15s; }
        .lp-country:hover { background: var(--secondary-container); color: var(--on-secondary-container); }
        .lp-flag { font-size: 18px; }
        .lp-count { min-width: 28px; height: 28px; padding: 0 8px; border-radius: var(--r-full); background: var(--surface-high); display: grid; place-items: center; font-size: 13px; font-weight: 700; font-variant-numeric: tabular-nums; }

        /* Privacy: the one inverse block on the page */
        .lp-privacy { background: var(--inverse-surface); color: var(--inverse-on-surface); border-radius: var(--r-2xl); padding: 44px; display: grid; grid-template-columns: .9fr 1.1fr; gap: 40px; }
        .lp-privacy h2 { color: var(--inverse-on-surface); font-size: clamp(28px, 4vw, 40px); font-weight: 800; line-height: 1.1; margin: 0 0 12px; }
        .lp-privacy-head p { opacity: .8; font-size: 18px; margin: 0; }
        .lp-privacy-list { list-style: none; padding: 0; margin: 0; display: grid; gap: 18px; }
        .lp-privacy-list li { display: flex; gap: 14px; align-items: flex-start; }
        .lp-privacy-list b { display: block; font-size: 17px; }
        .lp-privacy-list span { opacity: .8; font-size: 15px; }
        .lp-check { flex: none; width: 32px; height: 32px; border-radius: 50%; display: grid; place-items: center; background: var(--success-container); color: var(--on-success-container); font-weight: 800; }

        /* Inverse is dark-on-light in light mode; in dark mode it would be a glaring light slab. */
        @media (prefers-color-scheme: dark) {
          .lp-privacy { background: var(--surface-high); color: var(--on-bg); }
          .lp-privacy h2 { color: var(--on-bg); }
        }

        /* Pro */
        .lp-pro { display: flex; align-items: center; gap: 24px; padding: 28px 32px; border-radius: var(--r-2xl); background: var(--tertiary-container); color: var(--on-tertiary-container); text-decoration: none; transition: border-radius .25s; }
        .lp-pro:hover { border-radius: var(--r-xl); }
        .lp-pro-badge { flex: none; width: 76px; height: 76px; display: grid; place-items: center; background: var(--on-tertiary-container); color: var(--tertiary-container); font-weight: 800; font-size: 16px; }
        .lp-pro h2 { color: inherit; font-size: 24px; margin: 0 0 6px; }
        .lp-pro p { margin: 0; opacity: .9; }
        .lp-pro-copy { flex: 1; }
        .lp-pro-cta { flex: none; }

        /* Community */
        .lp-links { display: grid; grid-template-columns: repeat(3, 1fr); gap: 4px; }
        .lp-link { position: relative; display: flex; flex-direction: column; gap: 4px; padding: 22px 22px 48px; background: var(--surface-low); border-radius: var(--r-xs); color: var(--on-bg); text-decoration: none; transition: background .15s, border-radius .2s; }
        .lp-link:first-child { border-radius: var(--r-xl) var(--r-xs) var(--r-xs) var(--r-xl); }
        .lp-link:last-child { border-radius: var(--r-xs) var(--r-xl) var(--r-xl) var(--r-xs); }
        .lp-link:hover { background: var(--secondary-container); border-radius: var(--r-xl); }
        .lp-link b { font-size: 18px; }
        .lp-link span { color: var(--on-variant); font-size: 14px; }
        .lp-link .lp-link-arrow { position: absolute; left: 22px; bottom: 16px; font-size: 20px; color: var(--primary); }

        /* Final CTA */
        .lp-final { background: var(--primary-container); color: var(--on-primary-container); border-radius: var(--r-2xl); padding: 52px 32px; text-align: center; display: flex; flex-direction: column; align-items: center; }
        .lp-final h2 { color: inherit; font-size: clamp(32px, 5vw, 52px); font-weight: 800; margin: 18px 0 6px; }
        .lp-final p { margin: 0 0 26px; font-size: 18px; }
        .lp-final .download-row { justify-content: center; }
        .lp-final-owl { width: 104px; height: 104px; display: grid; place-items: center; background: var(--surface-lowest); }
        .lp-final-owl .lp-owl img { width: 68px; height: 68px; }
        .site-footer { margin-top: 24px; }

        @media (max-width: 960px) {
          .lp-hero-grid, .lp-split, .lp-privacy { grid-template-columns: 1fr; }
          .lp-hero-art { max-width: 460px; margin: 0 auto; width: 100%; }
          .lp-bento { grid-template-columns: 1fr 1fr; }
          .lp-tile:nth-last-child(-n+2) { grid-column: auto; }
        }
        @media (max-width: 720px) {
          .lp-wrap { padding: 0 16px; }
          .lp-section { padding: 36px 0; }
          .lp-hero { padding: 20px 0 24px; }
          .lp-lede { font-size: 18px; }
          .lp-steps { grid-template-columns: 1fr; }
          .lp-step, .lp-step:first-child, .lp-step:last-child { border-radius: var(--r-xs); }
          .lp-step:first-child { border-radius: var(--r-xl) var(--r-xl) var(--r-xs) var(--r-xs); }
          .lp-step:last-child { border-radius: var(--r-xs) var(--r-xs) var(--r-xl) var(--r-xl); }
          .lp-bento { grid-template-columns: 1fr; }
          .lp-tile-ask, .lp-tile-ins { grid-column: auto; grid-template-columns: 1fr; }
          .lp-tile-ask .lp-phone, .lp-tile-ins .lp-phone { justify-self: center; }
          .lp-float-sms { left: 0; top: 8%; }
          .lp-float-owl { right: 0; bottom: 6%; }
          .lp-privacy { padding: 28px 22px; }
          .lp-pro { flex-direction: column; align-items: flex-start; padding: 24px; }
          .lp-links { grid-template-columns: 1fr; }
          .lp-link, .lp-link:first-child, .lp-link:last-child { border-radius: var(--r-xs); }
          .lp-link:first-child { border-radius: var(--r-xl) var(--r-xl) var(--r-xs) var(--r-xs); }
          .lp-link:last-child { border-radius: var(--r-xs) var(--r-xs) var(--r-xl) var(--r-xl); }
          .lp-final { padding: 40px 20px; }
        }
    """
}
