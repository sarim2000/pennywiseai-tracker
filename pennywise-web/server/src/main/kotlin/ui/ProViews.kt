package com.example.ui

import com.example.ui.SharedComponents.commonHead
import com.example.ui.SharedComponents.commonStyles
import com.example.ui.SharedComponents.siteFooter
import com.example.ui.SharedComponents.siteHeader
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import kotlinx.html.*
import java.time.Instant

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
        val offerPrice: String? = null,
        val offerCadence: String? = null,
    )

    // The Diwali code lives in the Dodo dashboard: 25% off Annual and Lifetime,
    // first cycle only. These figures must match it.
    const val OFFER_CODE = "DIWALI25"
    private val OFFER_ENDS: Instant = Instant.parse("2026-11-10T18:29:59Z") // 11:59:59 pm IST

    /** Checked per request, so the offer leaves the site without a deploy. */
    fun offerActive(now: Instant = Instant.now()) = !now.isAfter(OFFER_ENDS)

    // Live Dodo product IDs — see memory/dodo-products-and-pricing.
    val plans = listOf(
        Plan("Monthly", "₹149", "per month", "Cancel any time from the email receipt.", "pdt_0NnzfnwsufJtgQ3kP5z6O"),
        Plan("Annual", "₹699", "per year", "Under ₹60 a month — 61% less than paying monthly.", "pdt_0NnzfRt53BXBEu7jThl0l",
            offerPrice = "₹524.25", offerCadence = "first year, then ₹699 a year"),
        Plan("Lifetime", "₹2,000", "once", "Pay once. Every future Pro feature included.", "pdt_0Nnzf26RZJeP6eTyc2GCl", featured = true,
            offerPrice = "₹1,500", offerCadence = "once"),
    )

    fun checkoutUrl(plan: Plan) = "https://checkout.dodopayments.com/buy/${plan.productId}?quantity=1"

    val includes = listOf(
        "Unlimited custom rules",
        "Unlimited PDF statement imports",
        "Unlimited CSV export",
        "Merge duplicate accounts",
        "Scheduled daily backups to a folder",
        "AI access from Claude, ChatGPT and the dashboard",
        "Webhook sync to a URL you choose",
    )

    suspend fun ApplicationCall.respondProPage() {
        val offer = offerActive()
        respondHtml(HttpStatusCode.OK) {
            lang = "en"
            head {
                commonHead(
                    "PennyWise Pro — ₹2,000 lifetime, ₹699 a year or ₹149 a month",
                    "Unlock every PennyWise feature with a license key. Pay once on the web, " +
                        "enter the key in the app. Prices include GST; no account, no tracking.",
                    "/pro",
                )
                style { unsafe { +commonStyles; +proStyles; if (offer) +offerStyles } }
            }
            body {
                siteHeader(currentPage = "pro")
                div(classes = "container pro") {
                    section(classes = "pro-hero") {
                        div(classes = "pro-owl") {
                            attributes["aria-hidden"] = "true"
                            unsafe {
                                +"""<picture><source srcset="/static/owl-dark.svg" media="(prefers-color-scheme: dark)"><img src="/static/owl.svg" alt="" width="96" height="96"></picture>"""
                            }
                        }
                        span(classes = "pro-eyebrow") { +"PennyWise Pro" }
                        h1 { +"Everything the app can do, unlocked." }
                        p(classes = "lede") {
                            +"Buy on this page, get a license key by email, paste it in the app. "
                            +"Works with the Play Store and F-Droid builds. The key unlocks features; it sends none of your data anywhere."
                        }
                    }

                    if (offer) offerPanel()

                    div(classes = "plans") {
                        plans.forEach { plan ->
                            div(classes = if (plan.featured) "plan featured" else "plan") {
                                div(classes = "plan-head") {
                                    div(classes = "plan-name") { +plan.name }
                                    if (plan.featured) span(classes = "plan-flag") { +"Most popular" }
                                }
                                if (offer) {
                                    // Empty on Monthly, so the three prices stay on one line.
                                    div(classes = "plan-was") { plan.offerPrice?.let { +plan.price } }
                                }
                                if (offer && plan.offerPrice != null) {
                                    div(classes = "plan-price plan-price-offer") { +plan.offerPrice }
                                    div(classes = "plan-cadence") { +plan.offerCadence.orEmpty() }
                                } else {
                                    div(classes = "plan-price") { +plan.price }
                                    div(classes = "plan-cadence") { +plan.cadence }
                                }
                                p(classes = "plan-blurb") { +plan.blurb }
                                a(href = checkoutUrl(plan), classes = "btn btn-filled btn-lg plan-buy") {
                                    +"Get ${plan.name}"
                                }
                            }
                        }
                    }
                    p(classes = "fine") {
                        +"Prices include GST. Cards, UPI and international cards accepted. "
                        +"Checkout and invoices are handled by Dodo Payments."
                    }

                    div(classes = "prose") {
                        h2 { +"What Pro unlocks" }
                        ul(classes = "includes") {
                            includes.forEach {
                                li {
                                    span(classes = "check") { unsafe { +checkSvg } }
                                    span { +it }
                                }
                            }
                        }

                        h2 { +"How it works" }
                        ol(classes = "steps") {
                            li { b { +"Pay" }; +" — pick a plan above and check out." }
                            li { b { +"Get your key" }; +" — it arrives by email within a minute (check spam if not)." }
                            li {
                                b { +"Activate" }
                                +" — in the app open "
                                span(classes = "path") { +"Settings → PennyWise Pro → Enter license key" }
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
                            faq("Why can't I buy Pro inside the app?") {
                                +"Google Play's rules don't allow an app to point at purchases made elsewhere, so the app sells nothing at all — "
                                +"it just takes your key. This page is where the buying happens."
                            }
                        }

                        div(classes = "footnote") {
                            +"PennyWise is open source — "
                            a(href = "https://github.com/sarim2000/pennywiseai-tracker") { +"read the code" }
                            +". Pro pays for the time to keep adding banks and features."
                            +" "
                            a(href = "/terms") { +"Terms of Service" }
                            +"."
                        }
                    }
                }
                siteFooter()
            }
        }
    }

    private fun FlowContent.offerPanel() {
        section(classes = "offer") {
            id = "diwali-offer"
            h2 { +"25% off Pro for Diwali" }
            p { +"On Annual and Lifetime, until 11:59 pm IST on 10 November. Enter this code at checkout." }
            button(type = ButtonType.button, classes = "offer-code") {
                attributes["data-code"] = OFFER_CODE
                span(classes = "offer-code-value") { +OFFER_CODE }
                span(classes = "offer-code-action") {
                    attributes["aria-live"] = "polite"
                    +"Copy code"
                }
            }
            div(classes = "diyas") {
                attributes["aria-hidden"] = "true"
                repeat(11) { i ->
                    span(classes = "diya") {
                        style = "--i:$i"
                        unsafe { +diyaSvg }
                    }
                }
            }
        }
        script { unsafe { +offerScript } }
    }

    private const val diyaSvg = """<svg viewBox="0 0 64 72"><g class="flame"><path fill="#FF8F3A" d="M32 12c6 12 11 20 0 34c-11-14-6-22 0-34z"/><path fill="#FFE08A" d="M32 26c3 6 5 10 0 19c-5-9-3-13 0-19z"/></g><path fill="#B4592C" d="M4 48c6 18 44 22 54 3l5-5c-7 1-13 3-18 3c-14 1-28 1-41-1z"/><path fill="#3E1B12" d="M9 48c12 3 28 3 40 0c-11 5-29 5-40 0z"/></svg>"""

    private const val checkSvg = """<svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><path fill="currentColor" d="M9.55 17.6 4.4 12.45l1.6-1.6 3.55 3.55 8.45-8.45 1.6 1.6z"/></svg>"""

    private const val offerScript = """
        (function () {
          var panel = document.getElementById('diwali-offer');
          var button = panel.querySelector('.offer-code');
          var action = panel.querySelector('.offer-code-action');
          button.addEventListener('click', function () {
            if (!navigator.clipboard) { action.textContent = 'Copy it from here'; return; }
            navigator.clipboard.writeText(button.dataset.code).then(function () {
              action.textContent = 'Copied';
              panel.classList.remove('flare');
              void panel.offsetWidth;
              panel.classList.add('flare');
              setTimeout(function () { action.textContent = 'Copy code'; }, 2200);
            }, function () {
              action.textContent = 'Copy it from here';
            });
          });
        })();
    """

    private fun FlowContent.faq(question: String, answer: P.() -> Unit) {
        details { summary { +question }; p { answer() } }
    }

    private val proStyles = """
        .pro { max-width: 1040px; }

        /* --- Hero --- */
        .pro-hero { text-align: center; padding: 16px 0 8px; }
        .pro-owl { position: relative; width: 120px; height: 120px; margin: 0 auto 20px; display: grid; place-items: center; }
        .pro-owl::before { content: ""; position: absolute; inset: 0; background: var(--tertiary-container); -webkit-mask: var(--shape-cookie) center / 100% 100% no-repeat; mask: var(--shape-cookie) center / 100% 100% no-repeat; }
        .pro-owl picture, .pro-owl img { position: relative; display: block; width: 84px; height: 84px; }
        .pro-eyebrow { display: inline-flex; align-items: center; height: 32px; padding: 0 14px; border-radius: var(--r-full); background: var(--tertiary-container); color: var(--on-tertiary-container); font-size: 14px; font-weight: 600; }
        .pro-hero h1 { font-size: clamp(36px, 6.4vw, 56px); font-weight: 800; line-height: 1.04; letter-spacing: -.03em; margin: 16px auto 16px; max-width: 14em; }
        .pro-hero .lede { margin: 0 auto; }

        /* --- Plans: big tonal cards; the featured one wears the Pro (tertiary) colours --- */
        .plans { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin: 40px 0 14px; align-items: stretch; }
        .plan { background: var(--surface-low); color: var(--on-bg); border-radius: var(--r-xl); padding: 24px; display: flex; flex-direction: column; min-width: 0; }
        .plan.featured { background: var(--tertiary-container); color: var(--on-tertiary-container); }
        .plan-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; flex-wrap: wrap; min-height: 32px; }
        .plan-name { font-family: var(--display); font-size: 20px; font-weight: 700; }
        .plan-flag { display: inline-flex; align-items: center; height: 28px; padding: 0 12px; border-radius: var(--r-full); background: var(--on-tertiary-container); color: var(--tertiary-container); font-size: 13px; font-weight: 700; white-space: nowrap; }
        .plan-price { font-family: var(--display); font-size: clamp(40px, 5vw, 52px); font-weight: 800; line-height: 1.05; letter-spacing: -.03em; font-variant-numeric: tabular-nums; margin-top: 18px; }
        .plan-cadence { color: var(--on-variant); font-size: 15px; font-weight: 500; margin-top: 2px; }
        .plan.featured .plan-cadence, .plan.featured .plan-blurb { color: inherit; }
        .plan-blurb { color: var(--on-variant); font-size: 15px; line-height: 1.5; flex: 1; margin: 14px 0 24px; }
        .plan a.plan-buy { width: 100%; }
        .plan.featured a.plan-buy { background: var(--on-tertiary-container); color: var(--tertiary-container); }
        .fine { text-align: center; color: var(--on-variant); font-size: 14px; margin: 0 0 8px; }

        /* --- What Pro unlocks: a segmented list --- */
        /* Only features the app actually gates — see FreeTierLimits / EntitlementGate. */
        .prose ul.includes { list-style: none; padding: 0; margin: 18px 0; display: grid; gap: 4px; }
        .prose .includes li { display: flex; align-items: center; gap: 14px; margin: 0; padding: 14px 18px; background: var(--surface-row); border-radius: var(--r-xs); color: var(--on-bg); font-size: 16px; font-weight: 500; }
        .includes li:first-child { border-top-left-radius: var(--r-lg); border-top-right-radius: var(--r-lg); }
        .includes li:last-child { border-bottom-left-radius: var(--r-lg); border-bottom-right-radius: var(--r-lg); }
        .includes .check { flex: none; width: 32px; height: 32px; border-radius: 50%; display: grid; place-items: center; background: var(--tertiary-container); color: var(--on-tertiary-container); }
        .includes .check svg { display: block; }

        /* --- How it works: numbered tonal cards --- */
        .prose ol.steps { list-style: none; padding: 0; margin: 18px 0; counter-reset: step; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
        .prose .steps li { counter-increment: step; margin: 0; padding: 20px; background: var(--surface-low); border-radius: var(--r-lg); color: var(--on-variant); font-size: 15px; line-height: 1.55; }
        .steps li::before { content: counter(step); display: grid; place-items: center; width: 40px; height: 40px; margin-bottom: 14px; border-radius: 50%; background: var(--primary-container); color: var(--on-primary-container); font-family: var(--display); font-size: 18px; font-weight: 800; }
        .steps li b { color: var(--on-bg); }
        .steps .path { display: inline; line-height: 1.9; -webkit-box-decoration-break: clone; box-decoration-break: clone; }

        @media (max-width: 860px) {
          .plans { grid-template-columns: 1fr; max-width: 520px; margin-left: auto; margin-right: auto; }
          .plan.featured { order: -1; }
        }
        @media (max-width: 640px) {
          .pro-owl { width: 96px; height: 96px; margin-bottom: 16px; }
          .pro-owl picture, .pro-owl img { width: 66px; height: 66px; }
          .plans { margin-top: 28px; }
          .plan { padding: 20px; }
          .prose ol.steps { grid-template-columns: 1fr; gap: 4px; }
          .prose .steps li { border-radius: var(--r-xs); }
          .prose .steps li:first-child { border-top-left-radius: var(--r-lg); border-top-right-radius: var(--r-lg); }
          .prose .steps li:last-child { border-bottom-left-radius: var(--r-lg); border-bottom-right-radius: var(--r-lg); }
        }
    """

    // A warm Diwali container (flat — no gradients) with its own light/dark roles.
    // Everything rests in its lit, final state; the animations only run from dark
    // to there, so reduced motion just switches them off.
    private val offerStyles = """
        :root { --offer-bg: #FFDCBE; --offer-on: #2C1600; --offer-on-variant: #5A3A1C; --offer-accent: #8B5000; --offer-on-accent: #FFFFFF; }
        @media (prefers-color-scheme: dark) {
          :root { --offer-bg: #45290C; --offer-on: #FFDCBE; --offer-on-variant: #F0CBA8; --offer-accent: #FFB95C; --offer-on-accent: #4A2800; }
        }
        .offer { overflow: hidden; margin: 36px 0 0; padding: clamp(24px, 5vw, 44px) clamp(20px, 5vw, 44px) 0; border-radius: var(--r-2xl); background: var(--offer-bg); color: var(--offer-on); }
        .offer h2 { font-size: clamp(32px, 6vw, 52px); line-height: 1.04; letter-spacing: -.03em; font-weight: 800; color: var(--offer-on); margin: 0 0 12px; }
        .offer p { color: var(--offer-on-variant); font-size: 17px; line-height: 1.5; max-width: 52ch; margin: 0 0 22px; }
        .offer button.offer-code { display: inline-flex; align-items: center; gap: 14px; height: auto; padding: 8px 8px 8px 20px; background: color-mix(in srgb, var(--offer-accent) 10%, transparent); color: var(--offer-on); border: 2px dashed var(--offer-accent); border-radius: var(--r-full); cursor: pointer; transition: background .2s; }
        .offer button.offer-code:hover { background: color-mix(in srgb, var(--offer-accent) 18%, transparent); filter: none; }
        .offer button.offer-code:active { border-radius: var(--r-full); }
        .offer button.offer-code:focus-visible { outline: 3px solid var(--offer-on); outline-offset: 3px; border-radius: var(--r-full); }
        .offer-code-value { font-family: var(--mono); font-size: 20px; font-weight: 500; letter-spacing: .06em; }
        .offer-code-action { background: var(--offer-accent); color: var(--offer-on-accent); font-weight: 700; font-size: 14px; padding: 8px 16px; border-radius: var(--r-full); }
        .diyas { display: flex; justify-content: space-between; margin: clamp(22px, 4vw, 36px) -6px 0; }
        .diya { position: relative; display: block; width: clamp(34px, 7vw, 62px); }
        .diya svg { position: relative; display: block; width: 100%; height: auto; overflow: visible; transform-origin: 50% 100%; }
        .flame { transform-box: fill-box; transform-origin: 50% 100%; animation: diya-light .6s calc(var(--i) * 130ms + 300ms) both cubic-bezier(.2,1.5,.4,1), diya-flicker calc(1.7s + var(--i) * 110ms) calc(var(--i) * 130ms + 900ms) infinite ease-in-out; }
        .offer.flare .diya svg { animation: diya-flare .6s calc(var(--i) * 30ms) ease-out; }
        .plan-was { position: relative; align-self: flex-start; height: 23px; line-height: 23px; margin-top: 14px; color: var(--on-variant); font-size: 16px; font-variant-numeric: tabular-nums; }
        .plan.featured .plan-was { color: inherit; }
        .plan-was:not(:empty)::after { content: ""; position: absolute; left: -2px; right: -2px; top: 52%; height: 1.5px; background: currentColor; transform-origin: left; animation: price-strike .4s 1.7s both ease-out; }
        .plan-price.plan-price-offer { color: var(--offer-accent); animation: price-arrive .5s 1.95s both cubic-bezier(.2,1.2,.4,1); }
        .plan-was + .plan-price { margin-top: 0; }
        @keyframes diya-light { from { opacity: 0; transform: scale(.1, .1); } }
        @keyframes diya-flicker { 0%, 100% { transform: scale(1, 1) rotate(0); } 30% { transform: scale(.95, 1.06) rotate(-2deg); } 65% { transform: scale(1.03, .96) rotate(1.5deg); } }
        @keyframes diya-flare { 40% { transform: translateY(-6px) scale(1.12); } }
        @keyframes price-strike { from { transform: scaleX(0); } }
        @keyframes price-arrive { from { opacity: 0; transform: translateY(8px); } }
        @media (max-width: 640px) { .diya:nth-child(n+8), .plan-was:empty { display: none; } }
        @media (prefers-reduced-motion: reduce) {
          .flame, .plan-was::after, .plan-price-offer, .offer.flare .diya svg { animation: none !important; }
        }
    """
}
