package com.example.ui

import com.example.ui.SharedComponents.commonHead
import com.example.ui.SharedComponents.commonStyles
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
            offerPrice = "₹524", offerCadence = "first year, then ₹699 a year"),
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
                div(classes = "container prose") {
                    span(classes = "eyebrow") { +"PennyWise Pro" }
                    h1 { +"Everything the app can do, unlocked." }
                    p(classes = "lede") {
                        +"Buy on this page, get a license key by email, paste it in the app. "
                        +"Works with the Play Store and F-Droid builds. Still nothing leaves your phone."
                    }

                    if (offer) offerPanel()

                    div(classes = "plans") {
                        plans.forEach { plan ->
                            div(classes = if (plan.featured) "plan featured" else "plan") {
                                if (plan.featured) span(classes = "plan-flag") { +"Most popular" }
                                div(classes = "plan-name") { +plan.name }
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
                    }
                }
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
                        span(classes = "diya-glow")
                        unsafe { +diyaSvg }
                    }
                }
            }
        }
        script { unsafe { +offerScript } }
    }

    private const val diyaSvg = """<svg viewBox="0 0 64 72"><g class="flame"><path fill="#FF8F3A" d="M32 12c6 12 11 20 0 34c-11-14-6-22 0-34z"/><path fill="#FFE08A" d="M32 26c3 6 5 10 0 19c-5-9-3-13 0-19z"/></g><path fill="#B4592C" d="M4 48c6 18 44 22 54 3l5-5c-7 1-13 3-18 3c-14 1-28 1-41-1z"/><path fill="#3E1B12" d="M9 48c12 3 28 3 40 0c-11 5-29 5-40 0z"/></svg>"""

    private const val offerScript = """
        (function () {
          var panel = document.getElementById('diwali-offer');
          var button = panel.querySelector('.offer-code');
          var action = panel.querySelector('.offer-code-action');
          button.addEventListener('click', function () {
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
        /* Only features the app actually gates — see FreeTierLimits / EntitlementGate. */
        .includes li { padding-left: 22px; position: relative; }
        .includes li::before { content: "✓"; color: var(--mint); position: absolute; left: 0; font-weight: 700; }
        .steps li { margin: 12px 0; }
        @media (max-width: 640px) { .plans { grid-template-columns: 1fr; } .plan.featured { order: -1; } }
    """

    // Everything rests in its lit, final state; the animations only run from dark
    // to there, so reduced motion just switches them off.
    private val offerStyles = """
        .offer { position: relative; overflow: hidden; margin: 28px 0 26px; padding: clamp(24px, 5vw, 44px) clamp(20px, 5vw, 44px) 0; border-radius: 20px; background: #190F26; border: 1px solid rgba(255,183,77,.28); isolation: isolate; }
        .offer::after { content: ""; position: absolute; inset: 35% -10% -30%; z-index: -1; background: radial-gradient(60% 70% at 50% 100%, rgba(255,143,58,.5), rgba(214,62,104,.2) 55%, transparent 75%); animation: offer-dawn 2.2s .2s both ease-out; }
        .prose .offer h2 { font-size: clamp(32px, 6.5vw, 58px); line-height: 1.02; letter-spacing: -.035em; font-weight: 800; color: #FFF3DF; margin: 0 0 12px; border: 0; padding: 0; }
        .prose .offer p { color: #E7D6C8; font-size: 16px; line-height: 1.5; max-width: 52ch; margin: 0 0 20px; }
        .offer button.offer-code { display: inline-flex; align-items: center; gap: 14px; padding: 10px 10px 10px 18px; background: rgba(255,183,77,.1); color: #FFF3DF; border: 1.5px dashed var(--amber); border-radius: 12px; cursor: pointer; transition: background .2s; }
        .offer button.offer-code:hover { background: rgba(255,183,77,.2); }
        .offer button.offer-code:focus-visible { outline: 3px solid #FFF3DF; outline-offset: 3px; }
        .offer-code-value { font-family: var(--mono); font-size: 20px; font-weight: 500; letter-spacing: .06em; }
        .offer-code-action { background: var(--amber); color: var(--ink); font-weight: 700; font-size: 14px; padding: 7px 12px; border-radius: 8px; }
        .diyas { display: flex; justify-content: space-between; margin: clamp(22px, 4vw, 36px) -6px 0; }
        .diya { position: relative; display: block; width: clamp(38px, 7vw, 62px); }
        .diya svg { position: relative; display: block; width: 100%; height: auto; overflow: visible; }
        .diya-glow { position: absolute; left: 50%; top: 22%; width: 240%; aspect-ratio: 1; transform: translate(-50%, -50%); background: radial-gradient(closest-side, rgba(255,190,110,.55), transparent); animation: diya-glow .9s calc(var(--i) * 130ms + 300ms) both ease-out; }
        .flame { transform-box: fill-box; transform-origin: 50% 100%; animation: diya-light .6s calc(var(--i) * 130ms + 300ms) both cubic-bezier(.2,1.5,.4,1), diya-flicker calc(1.7s + var(--i) * 110ms) calc(var(--i) * 130ms + 900ms) infinite ease-in-out; }
        .offer.flare .diya-glow { animation: diya-flare .8s calc(var(--i) * 30ms) ease-out; }
        .plan-was { position: relative; align-self: flex-start; height: 23px; line-height: 23px; margin-top: 8px; color: var(--muted); font-size: 15px; }
        .plan-was:not(:empty)::after { content: ""; position: absolute; left: -2px; right: -2px; top: 52%; height: 1.5px; background: var(--muted); transform-origin: left; animation: price-strike .4s 1.7s both ease-out; }
        .plan-price.plan-price-offer { color: var(--amber); animation: price-arrive .5s 1.95s both cubic-bezier(.2,1.2,.4,1); }
        .plan-was + .plan-price { margin-top: 0; }
        @keyframes offer-dawn { from { opacity: 0; } }
        @keyframes diya-light { from { opacity: 0; transform: scale(.1, .1); } }
        @keyframes diya-glow { from { opacity: 0; } }
        @keyframes diya-flicker { 0%, 100% { transform: scale(1, 1) rotate(0); } 30% { transform: scale(.95, 1.06) rotate(-2deg); } 65% { transform: scale(1.03, .96) rotate(1.5deg); } }
        @keyframes diya-flare { 40% { opacity: 1; transform: translate(-50%, -50%) scale(1.5); } }
        @keyframes price-strike { from { transform: scaleX(0); } }
        @keyframes price-arrive { from { opacity: 0; transform: translateY(8px); } }
        @media (max-width: 640px) { .diya:nth-child(n+8), .plan-was:empty { display: none; } }
        @media (prefers-reduced-motion: reduce) {
          .offer::after, .diya-glow, .flame, .plan-was::after, .plan-price-offer, .offer.flare .diya-glow { animation: none !important; }
        }
    """
}
