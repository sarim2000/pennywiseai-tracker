package com.example.ui

import com.example.ui.SharedComponents.commonHead
import com.example.ui.SharedComponents.commonStyles
import com.example.ui.SharedComponents.siteHeader
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import kotlinx.html.*

/**
 * /pro — the only place PennyWise Pro is sold now that Play checkout is
 * unavailable. Checkout is Dodo Payments (merchant of record: they invoice,
 * collect GST, handle refunds) and fulfils with a license key the app
 * activates. The app itself never links here (Play anti-steering), so this
 * page has to carry the whole pitch on its own.
 */
object ProViews {

    data class Plan(
        val name: String,
        val price: String,
        val cadence: String,
        val blurb: String,
        val productId: String,
        val featured: Boolean = false,
    )

    // Live Dodo product IDs — see memory/dodo-products-and-pricing.
    val plans = listOf(
        Plan("Monthly", "₹149", "per month", "Cancel any time from the email receipt.", "pdt_0NnzfnwsufJtgQ3kP5z6O"),
        Plan("Annual", "₹699", "per year", "Two months free versus monthly.", "pdt_0NnzfRt53BXBEu7jThl0l"),
        Plan("Lifetime", "₹2,000", "once", "Pay once. Every future Pro feature included.", "pdt_0Nnzf26RZJeP6eTyc2GCl", featured = true),
    )

    fun checkoutUrl(plan: Plan) = "https://checkout.dodopayments.com/buy/${plan.productId}?quantity=1"

    val includes = listOf(
        "Unlimited custom rules",
        "Unlimited PDF statement imports",
        "Unlimited CSV export",
        "Merge duplicate accounts",
        "Scheduled daily backups to a folder",
        "On-device AI chat over your transactions",
    )

    suspend fun ApplicationCall.respondProPage() {
        respondHtml(HttpStatusCode.OK) {
            lang = "en"
            head {
                commonHead(
                    "PennyWise Pro — ₹2,000 lifetime, ₹699 a year or ₹149 a month",
                    "Unlock every PennyWise feature with a license key. Pay once on the web, " +
                        "enter the key in the app. Prices include GST; no account, no tracking.",
                    "/pro",
                )
                style { unsafe { +commonStyles; +proStyles } }
            }
            body {
                siteHeader(currentPage = "pro")
                div(classes = "container prose") {
                    span(classes = "eyebrow") { +"PennyWise Pro" }
                    h1 { +"Everything the app can do, unlocked." }
                    p(classes = "lede") {
                        +"Buy on this page, get a license key by email, paste it in the app. "
                        +"Works with the Play Store and F-Droid builds. Still nothing leaves your phone."
                    }

                    div(classes = "plans") {
                        plans.forEach { plan ->
                            div(classes = if (plan.featured) "plan featured" else "plan") {
                                if (plan.featured) span(classes = "plan-flag") { +"Most popular" }
                                div(classes = "plan-name") { +plan.name }
                                div(classes = "plan-price") { +plan.price }
                                div(classes = "plan-cadence") { +plan.cadence }
                                p(classes = "plan-blurb") { +plan.blurb }
                                a(href = checkoutUrl(plan), classes = if (plan.featured) "btn btn-primary" else "btn btn-secondary") {
                                    +"Get ${plan.name}"
                                }
                            }
                        }
                    }
                    p(classes = "muted fine") {
                        +"Prices include GST. Cards, UPI and international cards accepted. "
                        +"Checkout and invoices are handled by Dodo Payments."
                    }

                    h2 { +"What Pro unlocks" }
                    ul(classes = "includes") { includes.forEach { li { +it } } }

                    h2 { +"How it works" }
                    ol(classes = "steps") {
                        li { b { +"Pay" }; +" — pick a plan above and check out." }
                        li { b { +"Get your key" }; +" — it arrives by email within a minute (check spam if not)." }
                        li {
                            b { +"Activate" }
                            +" — in the app open "
                            span(classes = "path") { +"Settings → PennyWise Pro → Have a license key?" }
                            +", paste the key, done."
                        }
                    }

                    h2 { +"Questions" }
                    div(classes = "faq") {
                        faq("Which devices can I use it on?") {
                            +"One phone at a time. Restoring a PennyWise backup on a new phone moves Pro with it. "
                            +"No backup? Enter the key on the new phone and choose \"Move to this device\"."
                        }
                        faq("I already bought Pro on Google Play.") {
                            +"Nothing changes for you. Your Play purchase keeps working and you don't need a key."
                        }
                        faq("I use the F-Droid build.") {
                            +"F-Droid builds have every Pro feature built in, free. Buying a key there is purely a way to support the app "
                            +"(and it works if you ever switch to the Play build)."
                        }
                        faq("Refunds and cancellations?") {
                            +"Subscriptions cancel from the link in your receipt email. For a refund, reply to that email or reach us on "
                            a(href = "https://discord.gg/H3xWeMWjKQ") { +"Discord" }
                            +"."
                        }
                        faq("Why isn't this in the app?") {
                            +"Google Play's rules don't allow apps to point at purchases made outside Play, so the app only has a place to enter a key. "
                            +"This page is where the buying happens."
                        }
                    }

                    div(classes = "footnote") {
                        +"PennyWise is open source — "
                        a(href = "https://github.com/sarim2000/pennywiseai-tracker") { +"read the code" }
                        +". Pro pays for the time to keep adding banks and features."
                    }
                }
            }
        }
    }

    private fun FlowContent.faq(question: String, answer: P.() -> Unit) {
        details { summary { +question }; p { answer() } }
    }

    private val proStyles = """
        .plans { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 12px; margin: 28px 0 10px; }
        .plan { position: relative; background: var(--surface); border: 1px solid var(--line); border-radius: 14px; padding: 20px 18px; display: flex; flex-direction: column; }
        .plan.featured { border-color: var(--amber); background: var(--surface-2); box-shadow: 0 0 0 3px rgba(255,183,77,.12); }
        .plan-flag { position: absolute; top: -11px; left: 16px; background: var(--amber); color: var(--ink); font-family: var(--mono); font-size: 10.5px; letter-spacing: .08em; text-transform: uppercase; font-weight: 500; padding: 3px 9px; border-radius: 999px; }
        .plan-name { font-family: var(--mono); font-size: 12px; letter-spacing: .1em; text-transform: uppercase; color: var(--muted); }
        .plan-price { font-family: var(--display); font-size: 34px; font-weight: 800; letter-spacing: -.03em; margin-top: 8px; }
        .plan-cadence { color: var(--muted); font-size: 13px; }
        .plan-blurb { color: #C4CDD8; font-size: 14px; flex: 1; margin: 12px 0 16px !important; }
        .plan a.btn { text-align: center; }
        .fine { font-size: 13px; }
        .includes { list-style: none; padding: 0 !important; display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 6px 18px; }
        .includes li { padding-left: 22px; position: relative; }
        .includes li::before { content: "✓"; color: var(--mint); position: absolute; left: 0; font-weight: 700; }
        .steps li { margin: 12px 0; }
        .path { font-family: var(--mono); font-size: 13px; color: var(--text); background: var(--surface); border: 1px solid var(--line); border-radius: 6px; padding: 2px 7px; }
        @media (max-width: 640px) { .plans { grid-template-columns: 1fr; } .plan.featured { order: -1; } }
    """
}
