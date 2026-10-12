package com.example.ui

import com.example.ui.SharedComponents.commonHead
import com.example.ui.SharedComponents.commonStyles
import com.example.ui.SharedComponents.siteHeader
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import kotlinx.html.*

/**
 * /guide: the user guide people keep asking for in issues and on Discord.
 * One page (searchable with Ctrl+F, printable to PDF). Each topic pairs its text
 * with one phone frame; extra screenshots are thumbnails that swap into that
 * frame, so the page never turns into a wall of tall images.
 *
 * Screenshots live in static/guide/, taken on an emulator with made-up demo data
 * (no real SMS, names or numbers). Retake at 540px wide, status bar cropped, WebP.
 */
object GuideViews {

    private class Shot(val file: String, val caption: String)

    private class Topic(
        val id: String,
        val title: String,
        val intro: String,
        val shots: List<Shot>,
        val body: FlowContent.() -> Unit,
    )

    private fun shots(vararg s: Pair<String, String>) = s.map { Shot(it.first, it.second) }

    private val topics: List<Topic> = listOf(
        Topic(
            "getting-started", "Getting started",
            "Install the app, give it SMS access, and it builds your history from the bank messages already on your phone.",
            shots(
                "onboarding-welcome" to "Welcome",
                "onboarding-permissions" to "SMS permission",
                "onboarding-scan" to "First scan",
                "onboarding-account" to "Main account",
            ),
        ) {
            ol(classes = "steps") {
                li { b { +"Profile." }; +" A name and avatar for the greeting. It never leaves the phone." }
                li {
                    b { +"SMS permission." }
                    +" Only messages from bank senders are read; personal chats are ignored. Skip it if you'd rather add everything by hand."
                }
                li { b { +"First scan." }; +" Reads the bank SMS you already have and creates transactions and accounts." }
                li { b { +"Main account." }; +" The account whose balance Home shows." }
            }
            p {
                +"New bank SMS are picked up automatically after that. Check your bank on the "
                a(href = "/banks") { +"supported banks" }
                +" list: 140+ banks and wallets across 20+ countries."
            }
        },
        Topic(
            "home", "Home",
            "What you've spent this month, your balance, budgets, recent transactions, accounts and upcoming subscriptions.",
            shots(
                "home" to "Home",
                "home-bottom" to "Accounts and activity",
                "home-sync" to "Sync button",
                "monthly-recap" to "Monthly recap",
            ),
        ) {
            dl(classes = "defs") {
                def("Sync button", "Tap to check for new SMS. Long-press for a full rescan of every message.")
                def("+ button", "Add a transaction or subscription by hand.")
                def("Eye icon", "Hide or show amounts. PennyWise remembers your choice.")
                def("⋯ menu", "Monthly recap, Settings, and Edit home screen to reorder or hide sections.")
            }
            p {
                b { +"Monthly recap" }
                +" makes a shareable card of your month. It shows counts, never amounts, so it's safe to post."
            }
        },
        Topic(
            "transactions", "Transactions",
            "Everything PennyWise has found, grouped by day, with search and filters for period, account, type, category and currency.",
            shots(
                "transactions" to "Transactions",
                "transactions-select" to "Multi-select",
                "transaction-detail" to "Details and actions",
            ),
        ) {
            dl(classes = "defs") {
                def("Select several", "Long-press one, tap the rest. Then mark them as a transfer, change the category, group or delete them.")
                def("Open one", "See the original SMS, edit it, exclude it from analytics or mark it as a loan.")
                def("⋮ menu", "Add to a group, duplicate, report a parsing problem, or delete.")
            }
            h3 { +"The five types" }
            div(classes = "types") {
                type("Expense", "Money out of a bank account.")
                type("Income", "Salary, refunds, cashback.")
                type("Credit", "Card and wallet spend you pay later, kept apart so the card bill isn't counted twice.")
                type("Transfer", "Between your own accounts. Not spending.")
                type("Investment", "SIPs, stocks, mutual funds.")
            }
        },
        Topic(
            "adding", "Adding transactions yourself",
            "For cash, or a bank PennyWise can't read yet. Pick a type, amount and category; notes, tags and a receipt photo are optional.",
            shots("add-transaction" to "Transaction", "add-subscription" to "Subscription"),
        ) {
            p { +"Any of these opens the same form:" }
            ul(classes = "ticks") {
                li { +"The + button on Home or Transactions." }
                li { +"The Add Transaction widget or Quick Settings tile." }
                li { +"Sharing a message or receipt text from another app to PennyWise. It fills the form for you." }
                li { +"Typing it in "; a(href = "#chat") { +"AI chat" }; +", like “lunch 250”." }
            }
        },
        Topic(
            "categories-rules", "Categories and Smart Rules",
            "Transactions are categorised from the merchant. When one is wrong, fix it once with a rule.",
            shots("rules" to "Smart Rules", "create-rule" to "New rule", "categories" to "Categories"),
        ) {
            p {
                path("Settings → Smart Rules")
                +" turns “when this matches, do that” into something automatic: merchant contains UBER goes to Transportation, "
                +"amounts under 200 go to Food, OTP messages get blocked. Start from a template or build your own."
            }
            p { +"Rules run on every new transaction, highest priority first. A rule's ⋮ menu can also apply it to past transactions." }
            p { path("Settings → Categories"); +" adds your own categories and hides ones you don't use." }
        },
        Topic(
            "budgets", "Budgets",
            "Set limits by category and see what's left, with a daily allowance until the period resets.",
            shots("budgets" to "Budgets", "budget-edit" to "Edit budget"),
        ) {
            p { +"“Use Smart Defaults” builds Spending, Fixed Costs and Investments budgets from your history. Or create your own and pick the categories each one counts." }
            ul(classes = "ticks") {
                li { +"Weekly, monthly or one-time, each with its own colour and start day." }
                li { +"⋮ → View history shows past periods. Finished ones are frozen, so recategorising later doesn't rewrite them." }
                li { +"Paid on the 25th? Set "; path("Settings → Budget Cycle Start Day"); +"." }
            }
        },
        Topic(
            "subscriptions", "Subscriptions, recurring, loans and groups",
            "Four small tools for money that doesn't fit a single transaction.",
            shots(
                "subscriptions" to "Subscriptions",
                "recurring" to "Recurring",
                "mark-loan" to "Mark as loan",
                "loans" to "Loans",
                "groups" to "Groups",
            ),
        ) {
            dl(classes = "defs") {
                def("Subscriptions", "Netflix, Spotify, rent. Add them from the Subscription tab of the add screen; Home shows what's due next and the monthly total.")
                def("Recurring", "Settings → Recurring adds a transaction on a schedule. Useful for cash you pay regularly that no SMS will report.")
                def("Loans", "Paid for a friend? Open the transaction, tap Mark as loan and enter their name. Settings → Loans shows who owes whom.")
                def("Groups", "Collect a trip or a renovation under one name to see its total. Select transactions and tap the group icon.")
            }
        },
        Topic(
            "accounts", "Accounts and balances",
            "One account per bank account or card, created from your SMS and updated whenever a message includes a balance.",
            shots("accounts" to "Manage accounts", "account-detail" to "Account detail"),
        ) {
            p { path("Settings → Manage Accounts"); +" lets you:" }
            ul(classes = "ticks") {
                li { +"Correct a balance by hand and see its history." }
                li { +"Add a manual account for cash, a wallet, or a bank that doesn't text." }
                li { +"See each credit card's limit, outstanding and available amount." }
                li { +"Merge duplicates, like one card reported under two numbers (Pro)." }
            }
            p { +"Tap an account on Home for its balance trend and transactions." }
        },
        Topic(
            "analytics", "Analytics",
            "Any period, broken down by account and type: total, daily average, trend, top categories, top merchants.",
            shots("analytics" to "Totals and trend", "analytics-categories" to "Categories"),
        ) {
            p { +"Tap a category to jump straight to those transactions. Pro adds a breakdown by tag." }
        },
        Topic(
            "chat", "AI chat",
            "An assistant that runs entirely on your phone. Your questions and data never leave it.",
            shots("chat-add" to "Adding by chat", "chat-ask" to "Asking a question"),
        ) {
            p {
                +"It needs a one-time download of about 1.5 GB, from the Chat tab or "
                path("Settings → AI Features")
                +". Use Wi-Fi."
            }
            dl(classes = "defs") {
                def("Add by typing", "“coffee 180 at Starbucks”, “got 50000 salary”. A confirm card appears first; nothing is saved until you tap Add.")
                def("Ask", "“how much on groceries this month?”, “what did I spend at Swiggy?”")
            }
            p(classes = "note") { +"It's a small model on a phone: quick totals and quick adds, not long financial advice. Older phones take a few seconds longer." }
        },
        Topic(
            "currency", "Multiple currencies",
            "Every amount keeps its own currency. Rupees and dollars are never added together.",
            shots("settings-currency" to "Currency settings", "exchange-rates" to "Exchange rates"),
        ) {
            dl(classes = "defs") {
                def("Unified Currency Mode", "Convert everything into one display currency for combined totals.")
                def("Default Currency", "Used for manual entries. Choosing a Main Account sets it too.")
                def("Exchange Rates", "Fetched automatically. Tap one to set your own; custom rates survive refreshes.")
                def("Number Format", "Indian (1,00,000), international (100,000), or Auto per currency.")
            }
        },
        Topic(
            "data", "Import, export and backup",
            "There's no account and no cloud, so a backup file is the only copy outside your phone. Keep one.",
            shots("settings-data" to "Data management", "import-statement" to "Statement import", "export-csv" to "CSV export"),
        ) {
            p { +"Everything is under "; path("Settings → Data Management"); +"." }
            dl(classes = "defs") {
                def("Export / Import Data", "A full backup file: transactions, accounts, budgets, rules and more. Receipt photos optional.")
                def("Automatic Folder Backup", "Saves a backup to a folder you choose every night at 2 AM (Pro).")
                def("Import Statement", "PDF statements from Google Pay, PhonePe, Paytm, slice or ICICI Bank. Duplicates are skipped.")
                def("CSV", "Import a PennyWise CSV, or any sheet with Date, Amount and Type. Export from the download button on Transactions.")
                def("SMS Scan Period", "Scan everything, or only messages after a date.")
                def("Unrecognized SMS", "Bank messages PennyWise couldn't read. Report them in one tap so support gets added.")
            }
            h3 { +"Moving to a new phone" }
            ol(classes = "steps") {
                li { +"On the old phone, Export Data and save the file to Drive, email or a computer." }
                li { +"On the new phone, finish setup, then Import Data and pick the file. Pro comes with it." }
            }
        },
        Topic(
            "settings", "Other settings",
            "Look and feel, privacy, and a few extras.",
            shots("appearance" to "Appearance"),
        ) {
            dl(classes = "defs") {
                def("Appearance", "Light, dark or system theme, Material You colours, AMOLED black, navigation style, fonts.")
                def("App Lock", "Fingerprint or face unlock. Set up biometrics in Android first.")
                def("Contacts", "Show a contact's name instead of their UPI ID.")
                def("Bank Notification Access", "Also read bank app notifications, for banks that notify instead of texting.")
                def("Widgets", "Long-press your home screen → Widgets → PennyWise: Add Transaction, Budget Overview, Spending Pie, Recent Transactions.")
                def("Webhooks", "Send selected transaction data to your own server (Pro).")
            }
        },
        Topic(
            "pro", "Free and Pro",
            "Almost everything is free. Pro lifts a few limits and pays for new banks and features.",
            shots("pro-sheet" to "PennyWise Pro", "license-key" to "License key"),
        ) {
            div(classes = "compare") {
                div(classes = "compare-head") { span { +"" }; span { +"Free" }; span { +"Pro" } }
                compare("Smart Rules", "3", "Unlimited")
                compare("PDF statement imports", "1 a month", "Unlimited")
                compare("CSV export", "100 rows a month", "Unlimited")
                compare("Recurring transactions", "1", "Unlimited")
                compare("Folder backup, merge accounts, tag analytics, webhooks", "No", "Yes")
            }
            p {
                +"Buy a key on the "
                a(href = "/pro") { +"Pro page" }
                +", then open "
                path("Settings → PennyWise Pro → Enter license key")
                +". F-Droid builds include every Pro feature for free."
            }
        },
    )

    suspend fun ApplicationCall.respondGuidePage() {
        respondHtml(HttpStatusCode.OK) {
            lang = "en"
            head {
                commonHead(
                    "PennyWise user guide: every feature, with screenshots",
                    "How to use PennyWise: SMS auto-tracking, budgets, rules, analytics, the on-device AI chat, " +
                        "multi-currency and backups, with a screenshot of every screen.",
                    "/guide",
                )
                style { unsafe { +commonStyles; +guideStyles } }
            }
            body {
                siteHeader(currentPage = "guide")
                div(classes = "guide") {
                    header(classes = "g-hero") {
                        div {
                            h1 { +"How to use PennyWise" }
                            p(classes = "g-sub") { +"Every screen in the app, explained with screenshots. Read top to bottom or jump to a topic." }
                        }
                        phone("home", "PennyWise home screen", eager = true)
                    }

                    div(classes = "g-layout") {
                        nav(classes = "g-side") {
                            attributes["aria-label"] = "Topics"
                            ol {
                                topics.forEach { t -> li { a(href = "#${t.id}") { +t.title } } }
                                li { a(href = "#troubleshooting") { +"Troubleshooting" } }
                            }
                        }

                        main(classes = "g-main") {
                            topics.forEach { topic(it) }
                            section(classes = "g-topic g-faq") {
                                id = "troubleshooting"
                                h2 { +"Troubleshooting" }
                                div(classes = "faq") { troubleshooting() }
                            }
                            p(classes = "g-foot") {
                                +"Still stuck? Ask on "
                                a(href = "https://discord.gg/H3xWeMWjKQ") { +"Discord" }
                                +" or open a "
                                a(href = "https://github.com/sarim2000/pennywiseai-tracker/issues") { +"GitHub issue" }
                                +". Need a PDF? Print this page and choose Save as PDF. Screenshots use made-up demo data."
                            }
                        }
                    }
                }
                script { unsafe { +guideScript } }
            }
        }
    }

    private fun FlowContent.topic(t: Topic) {
        section(classes = "g-topic") {
            id = t.id
            div(classes = "g-text") {
                h2 { +t.title }
                p(classes = "g-intro") { +t.intro }
                t.body(this)
            }
            figure(classes = "g-shots") {
                phone(t.shots.first().file, t.shots.first().caption)
                if (t.shots.size > 1) {
                    div(classes = "thumbs") {
                        attributes["role"] = "group"
                        attributes["aria-label"] = "${t.title} screenshots"
                        t.shots.forEachIndexed { i, s ->
                            button(classes = "thumb") {
                                type = ButtonType.button
                                attributes["data-src"] = src(s.file)
                                attributes["data-alt"] = s.caption
                                attributes["aria-pressed"] = (i == 0).toString()
                                img(src = src(s.file), alt = "") {
                                    width = "540"; height = "1145"
                                    attributes["loading"] = "lazy"
                                }
                                span { +s.caption }
                            }
                        }
                    }
                } else {
                    figcaption { +t.shots.first().caption }
                }
            }
        }
    }

    private fun src(file: String) = "/static/guide/$file.webp"

    // width/height reserve the space before the image loads, so nothing jumps.
    private fun FlowContent.phone(file: String, alt: String, eager: Boolean = false) {
        div(classes = "phone") {
            img(src = src(file), alt = alt) {
                width = "540"; height = "1145"
                attributes["loading"] = if (eager) "eager" else "lazy"
                attributes["decoding"] = "async"
            }
        }
    }

    private fun DL.def(term: String, desc: String) {
        dt { +term }
        dd { +desc }
    }

    private fun FlowContent.type(name: String, desc: String) {
        div(classes = "type") { b { +name }; span { +desc } }
    }

    private fun FlowContent.compare(feature: String, free: String, pro: String) {
        div(classes = "compare-row") { span { +feature }; span { +free }; span { +pro } }
    }

    private fun FlowContent.path(text: String) {
        span(classes = "path") { +text }
    }

    private fun FlowContent.faq(question: String, answer: P.() -> Unit) {
        details { summary { +question }; p { answer() } }
    }

    private fun FlowContent.troubleshooting() {
        faq("My bank's SMS aren't being picked up.") {
            +"Check your bank is on the "
            a(href = "/banks") { +"supported banks" }
            +" list and SMS permission is on. Paste one message into the "
            a(href = "/") { +"SMS parser" }
            +" here to see if it's read. If it isn't, report it from "
            path("Settings → Unrecognized SMS")
            +" or open a "
            a(href = "https://github.com/sarim2000/pennywiseai-tracker/issues") { +"GitHub issue" }
            +" with the message (hide your account number)."
        }
        faq("Older transactions are missing.") {
            +"Set "
            path("Settings → SMS Scan Period")
            +" to All Time, then long-press the sync button for a full rescan. PennyWise can only see SMS still on your phone."
        }
        faq("A transaction shows up twice.") {
            +"Some banks send two messages for one payment. Most duplicates are skipped; delete any that slip through."
        }
        faq("A balance is wrong.") {
            +"Balances come from the latest SMS that mentions one. Correct it in "
            path("Settings → Manage Accounts → Update Balance")
            +"."
        }
        faq("Lots of transactions are in the wrong category.") {
            +"Change one, then add a "
            a(href = "#categories-rules") { +"Smart Rule" }
            +" for that merchant so it doesn't happen again."
        }
        faq("New transactions stop appearing in the background.") {
            +"Some phones (Xiaomi, Oppo, Vivo, Samsung) stop background apps. Set PennyWise's battery usage to Unrestricted, or tap sync when you open the app."
        }
        faq("The AI model won't download, or chat is slow.") {
            +"It needs about 1.5 GB free and a steady connection, so try Wi-Fi. Replies take a few seconds, longer on older phones."
        }
        faq("A backup won't restore.") {
            +"Update PennyWise first; new versions always read older backups. If it still fails, ask on Discord with the error."
        }
        faq("Is my data private?") {
            +"Yes. SMS and transactions are processed and stored only on your phone, and the AI runs there too. No account, no servers holding your data, and the "
            a(href = "https://github.com/sarim2000/pennywiseai-tracker") { +"code is open source" }
            +"."
        }
    }

    // Thumbnail → main phone swap, and sidebar highlighting via IntersectionObserver
    // (no scroll listener). Both are progressive: without JS the page still reads fine.
    private val guideScript = """
        document.querySelectorAll('.g-shots').forEach(function (fig) {
          var main = fig.querySelector('.phone img');
          fig.querySelectorAll('.thumb').forEach(function (btn) {
            btn.addEventListener('click', function () {
              main.src = btn.dataset.src; main.alt = btn.dataset.alt;
              fig.querySelectorAll('.thumb').forEach(function (b) { b.setAttribute('aria-pressed', String(b === btn)); });
            });
          });
        });
        var links = {};
        document.querySelectorAll('.g-side a').forEach(function (a) { links[a.hash.slice(1)] = a; });
        if ('IntersectionObserver' in window) {
          var io = new IntersectionObserver(function (entries) {
            entries.forEach(function (e) {
              if (!e.isIntersecting) return;
              Object.values(links).forEach(function (a) { a.removeAttribute('aria-current'); });
              var a = links[e.target.id]; if (a) a.setAttribute('aria-current', 'true');
            });
          }, { rootMargin: '-35% 0px -60% 0px' });
          document.querySelectorAll('.g-topic').forEach(function (s) { io.observe(s); });
        }
    """

    // Radius scale: 12px for panels and thumbnails, 30px only for phone frames.
    private val guideStyles = """
        .guide { max-width: 1180px; margin: 0 auto; padding: 8px 24px 64px; }
        .g-hero { display: grid; grid-template-columns: minmax(0, 1fr) 260px; gap: 48px; align-items: center; padding: 32px 0 0; border-bottom: 1px solid var(--line-soft); }
        .g-hero h1 { font-size: clamp(34px, 5vw, 56px); line-height: 1.04; letter-spacing: -.035em; font-weight: 800; margin: 0 0 16px; }
        .g-sub { font-size: 18px; line-height: 1.55; color: var(--muted); max-width: 34ch; margin: 0 0 8px; }
        /* Hero phone shows only its top half and runs into the divider, like a device rising out of the page. */
        .g-hero .phone { height: 330px; overflow: hidden; border-bottom: 0; border-radius: 30px 30px 0 0; padding-bottom: 0; box-shadow: none; -webkit-mask-image: linear-gradient(#000 70%, transparent); mask-image: linear-gradient(#000 70%, transparent); }
        .g-hero .phone img { border-radius: 23px 23px 0 0; }
        .g-hero > div { padding-bottom: 32px; }

        .g-layout { display: grid; grid-template-columns: 200px minmax(0, 1fr); gap: 56px; margin-top: 48px; }
        .g-side { position: sticky; top: 88px; align-self: start; max-height: calc(100dvh - 110px); overflow-y: auto; }
        .g-side ol { list-style: none; margin: 0; padding: 0; border-left: 1px solid var(--line); }
        .g-side a { display: block; padding: 6px 0 6px 14px; margin-left: -1px; border-left: 2px solid transparent; color: var(--muted); text-decoration: none; font-size: 14px; line-height: 1.35; transition: color .15s, border-color .15s; }
        .g-side a:hover { color: var(--text); }
        .g-side a[aria-current] { color: var(--text); border-left-color: var(--blue); font-weight: 600; }

        .g-topic { display: grid; grid-template-columns: minmax(0, 1fr) 248px; gap: 48px; padding: 56px 0; border-bottom: 1px solid var(--line-soft); scroll-margin-top: 72px; }
        .g-topic:first-child { padding-top: 8px; }
        .g-topic h2 { font-size: 28px; font-weight: 750; letter-spacing: -.03em; margin: 0 0 10px; line-height: 1.15; }
        .g-topic h3 { font-size: 15px; font-weight: 700; margin: 28px 0 12px; }
        .g-intro { font-size: 17px; color: var(--text) !important; line-height: 1.55; margin: 0 0 22px; max-width: 58ch; }
        .g-text p, .g-text li, .g-text dd { color: #B9C3CF; font-size: 15.5px; line-height: 1.65; max-width: 62ch; }
        .g-text p { margin: 14px 0; }
        .g-text a, .g-foot a, .faq a { color: var(--blue); text-underline-offset: 3px; }

        .defs { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: 12px 24px; margin: 0; }
        .defs dt { color: var(--text); font-weight: 600; font-size: 14.5px; padding-top: 2px; }
        .defs dd { margin: 0; }
        .steps { counter-reset: s; list-style: none; padding: 0; margin: 0; }
        .steps li { counter-increment: s; position: relative; padding-left: 40px; margin: 0 0 14px; }
        .steps li::before { content: counter(s); position: absolute; left: 0; top: 1px; width: 26px; height: 26px; border-radius: 50%; background: var(--surface-2); border: 1px solid var(--line); color: var(--text); font-family: var(--mono); font-size: 12.5px; display: grid; place-items: center; }
        .steps b { color: var(--text); }
        .ticks { list-style: none; padding: 0; margin: 0; }
        .ticks li { position: relative; padding-left: 22px; margin: 0 0 10px; }
        .ticks li::before { content: ""; position: absolute; left: 2px; top: .62em; width: 8px; height: 2px; background: var(--blue); border-radius: 2px; }
        .types { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 10px; }
        .type { background: var(--surface); border: 1px solid var(--line-soft); border-radius: 12px; padding: 12px 14px; font-size: 14px; color: #B9C3CF; line-height: 1.5; }
        .type b { display: block; color: var(--text); margin-bottom: 2px; }
        .note { font-size: 14px !important; color: var(--muted) !important; }

        .g-shots { margin: 0; position: sticky; top: 88px; align-self: start; }
        .phone { border-radius: 30px; padding: 7px; background: #05070A; border: 1px solid var(--line); box-shadow: 0 24px 60px -24px rgba(0,0,0,.7), inset 0 1px 0 rgba(255,255,255,.05); }
        .phone img { display: block; width: 100%; height: auto; border-radius: 23px; background: var(--surface); }
        .g-shots figcaption { text-align: center; font-size: 13px; color: var(--muted); margin-top: 12px; }
        .thumbs { display: flex; gap: 8px; margin-top: 14px; overflow-x: auto; scroll-snap-type: x mandatory; padding-bottom: 4px; }
        .thumb { flex: 0 0 auto; width: 56px; padding: 0; background: none; border: 0; color: var(--muted); cursor: pointer; scroll-snap-align: start; text-align: center; font-weight: 500; }
        .thumb:hover { background: none; color: var(--text); }
        .thumb img { display: block; width: 100%; height: auto; border-radius: 12px; border: 2px solid var(--line); opacity: .6; transition: opacity .15s, border-color .15s; }
        .thumb span { display: block; font-size: 11.5px; line-height: 1.3; margin-top: 6px; }
        .thumb:hover img { opacity: .9; }
        .thumb[aria-pressed="true"] img { opacity: 1; border-color: var(--blue); }
        .thumb[aria-pressed="true"] span { color: var(--text); }
        .thumb:active img { transform: scale(.97); }

        .compare { border: 1px solid var(--line); border-radius: 12px; overflow: hidden; font-size: 14.5px; margin: 4px 0 18px; }
        .compare-head, .compare-row { display: grid; grid-template-columns: minmax(0, 1.6fr) 1fr 1fr; gap: 12px; padding: 12px 16px; }
        .compare-head { background: var(--surface-2); color: var(--muted); font-size: 13px; font-weight: 600; }
        .compare-row { color: #B9C3CF; }
        .compare-row:nth-child(odd) { background: var(--surface); }
        .compare-row span:first-child { color: var(--text); }
        .compare-row span:last-child { color: var(--blue); font-weight: 600; }

        .g-faq { display: block; }
        .g-faq h2 { margin-bottom: 8px; }
        .g-faq .faq { max-width: 70ch; }
        .faq details p { color: #B9C3CF; font-size: 15px; line-height: 1.65; }
        .g-foot { color: var(--muted); font-size: 14px; margin-top: 40px; max-width: 70ch; }

        @media (max-width: 1024px) {
          .g-layout { grid-template-columns: minmax(0, 1fr); gap: 0; margin-top: 24px; }
          .g-side { display: none; }
        }
        @media (max-width: 760px) {
          .guide { padding: 0 16px 48px; }
          .g-hero { grid-template-columns: minmax(0, 1fr); gap: 0; padding: 4px 0 32px; }
          .g-hero .phone { display: none; }
          .g-topic { grid-template-columns: minmax(0, 1fr); gap: 28px; padding: 40px 0; }
          .g-topic h2 { font-size: 24px; }
          .g-shots { position: static; width: min(260px, 72vw); margin: 0 auto; }
          .defs { grid-template-columns: minmax(0, 1fr); gap: 2px; }
          .defs dd { margin-bottom: 12px; }
          .compare-head, .compare-row { grid-template-columns: minmax(0, 1.3fr) 1fr 1fr; padding: 11px 12px; font-size: 13.5px; }
        }
        @media print {
          :root { color-scheme: light; }
          body { background: #fff; color: #111; }
          .header, .g-side, .thumbs, .g-hero .phone { display: none; }
          .g-layout, .g-topic { display: block; }
          .g-shots { position: static; width: 200px; margin: 16px 0; }
          .g-topic { break-inside: avoid; padding: 24px 0; }
          .g-text p, .g-text li, .g-text dd, .g-intro, .compare-row, .faq details p { color: #222 !important; }
          .path { background: #f2f2f2; color: #111; border-color: #ccc; }
        }
    """
}
