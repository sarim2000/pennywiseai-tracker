package com.example.ui

import com.example.ui.SharedComponents.commonHead
import com.example.ui.SharedComponents.commonStyles
import com.example.ui.SharedComponents.siteHeader
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import kotlinx.html.*

/** /terms: the Terms of Service URL given to Google Play. */
object TermsViews {

    private const val PRIVACY_URL = "https://github.com/sarim2000/pennywiseai-tracker/blob/main/PRIVACY.md"
    private const val LICENSE_URL = "https://github.com/sarim2000/pennywiseai-tracker/blob/main/LICENSE"
    private const val ISSUES_URL = "https://github.com/sarim2000/pennywiseai-tracker/issues"
    private const val DISCORD_URL = "https://discord.gg/H3xWeMWjKQ"

    suspend fun ApplicationCall.respondTermsPage() {
        respondHtml(HttpStatusCode.OK) {
            lang = "en"
            head {
                commonHead(
                    "Terms of Service — PennyWise",
                    "The terms for using the PennyWise app and buying PennyWise Pro.",
                    "/terms",
                )
                style { unsafe { +commonStyles } }
            }
            body {
                siteHeader()
                div(classes = "container prose") {
                    h1 { +"Terms of Service" }
                    p { +"Last updated: 6 November 2026" }
                    p {
                        +"These terms cover the PennyWise expense tracker app, this website and PennyWise Pro. "
                        +"By using any of them, you agree to these terms. If you don't agree, please don't use them."
                    }

                    h2 { +"What PennyWise does" }
                    p {
                        +"PennyWise reads bank SMS on your device and turns them into a record of your transactions. "
                        +"It can also answer questions about your spending with on-device AI. Your use of PennyWise must follow the law, "
                        +"and you should only use it with messages and accounts that you are allowed to access."
                    }

                    h2 { +"Your data" }
                    p {
                        +"Your transactions are stored on your device, and you are responsible for keeping your own backups. "
                        +"If you delete the app without a backup, your data can't be recovered. The "
                        a(href = PRIVACY_URL) { +"Privacy Policy" }
                        +" explains what leaves your device and when."
                    }

                    h2 { +"Not financial advice" }
                    p {
                        +"PennyWise can misread a message, miss a transaction or show a wrong total, and AI answers can be wrong. "
                        +"Check anything important with your bank. Nothing in PennyWise is financial, tax or legal advice."
                    }

                    h2 { +"PennyWise Pro" }
                    ul {
                        li {
                            +"Purchases on this website are processed by Dodo Payments, the merchant of record, which also handles invoices and taxes. "
                            +"Purchases on Google Play are processed by Google under its own terms."
                        }
                        li { +"A license key works on one phone at a time. You can move it to a new phone, but please don't share or resell it." }
                        li { +"Monthly and Annual plans renew automatically until you cancel. You can cancel from the link in your receipt email." }
                        li { +"Lifetime is a single payment that covers Pro for as long as PennyWise Pro is offered." }
                        li {
                            +"For a refund, reply to your receipt email or contact us on "
                            a(href = DISCORD_URL) { +"Discord" }
                            +". This doesn't affect any refund rights you have under your local law."
                        }
                    }

                    h2 { +"Open source" }
                    p {
                        +"The PennyWise source code is available under the "
                        a(href = LICENSE_URL) { +"GNU Affero General Public License v3" }
                        +". That license, not these terms, governs your use of the code."
                    }

                    h2 { +"Other services" }
                    p {
                        +"If you connect an AI app such as Claude or ChatGPT, or use another outside service with PennyWise, "
                        +"that service's own terms and privacy policy apply to what it receives."
                    }

                    h2 { +"No warranty and limits on liability" }
                    p {
                        +"PennyWise is provided \"as is\", without warranties of any kind, and we don't promise that it will always work or be free of errors. "
                        +"To the extent the law allows, we aren't liable for indirect or consequential losses, "
                        +"and our total liability is limited to the amount you paid for Pro."
                    }

                    h2 { +"Changes" }
                    p {
                        +"We may update these terms, and this page will show the new date. "
                        +"If you keep using PennyWise after a change, the updated terms apply."
                    }

                    h2 { +"Contact" }
                    p {
                        +"Reach us on "
                        a(href = DISCORD_URL) { +"Discord" }
                        +" or "
                        a(href = ISSUES_URL) { +"GitHub" }
                        +"."
                    }
                }
            }
        }
    }
}
