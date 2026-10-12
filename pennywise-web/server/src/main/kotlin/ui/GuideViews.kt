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

    /** One screen of a walkthrough. [x]/[y] (percent of the screenshot) place the tap marker. */
    private class Step(val shot: String, val title: String, val text: String, val x: Int? = null, val y: Int? = null)

    private class Walkthrough(val id: String, val goal: String, val steps: List<Step>)

    // Marker positions were measured on the 540×1145 screenshots; retaking a shot means re-checking them.
    private val walkthroughs = listOf(
        Walkthrough("setup", "Set up PennyWise", listOf(
            Step("onboarding-welcome", "Open the app", "Tap Get Started. Setup takes about a minute and has four steps."),
            Step("onboarding-permissions", "Allow SMS access", "Tap Enable Permissions, then Allow. Only bank messages are read; your chats are never touched.", 50, 67),
            Step("onboarding-scan", "Let it scan", "PennyWise reads the bank SMS already on your phone and turns them into transactions."),
            Step("onboarding-account", "Pick your main account", "This balance shows on Home. Tap Finish.", 83, 92),
            Step("home-sync", "You're done", "New bank SMS are added automatically. Tap the sync button any time to check now.", 89, 86),
        )),
        Walkthrough("cash", "Add cash spending", listOf(
            Step("home", "Tap +", "From Home or Transactions, the + button opens the add screen.", 90, 78),
            Step("add-transaction", "Enter the amount", "Type the amount, then a merchant like “Chai stall”. Expense is already selected.", 67, 19),
            Step("add-transaction", "Pick a category", "Choose where it belongs so budgets and analytics stay accurate.", 28, 76),
            Step("add-transaction", "Save", "That's it. It now counts toward this month's spending.", 50, 91),
            Step("chat-add", "Or just type it", "In the Chat tab, write “coffee 180 at Starbucks” and tap Add on the card.", 71, 50),
        )),
        Walkthrough("budget", "Set a monthly budget", listOf(
            Step("budgets-setup", "Open Budgets", "From Home tap View All next to Budgets. Tap Use Smart Defaults to start from your own spending.", 50, 64),
            Step("budgets", "See what's left", "Each budget shows what's left this month and how much you can spend per day.", 70, 25),
            Step("budget-edit", "Adjust it", "Tap the pencil to change the limit, colour, period or the day your month starts."),
        )),
        Walkthrough("category", "Fix wrong categories for good", listOf(
            Step("transaction-detail", "Fix one transaction", "Open it and tap the pencil to pick the right category.", 81, 5),
            Step("rules", "Open Smart Rules", "Settings → Smart Rules, then tap + to make a rule.", 89, 92),
            Step("create-rule", "Describe the rule", "Start from a template, or say “merchant contains UBER → Transportation”. Every new match is categorised automatically.", 50, 28),
        )),
        Walkthrough("loan", "Track money a friend owes you", listOf(
            Step("transaction-detail", "Open the payment", "Find what you paid for them and tap Mark as loan.", 50, 37),
            Step("mark-loan", "Say who", "Type their name and how much they owe, then Confirm.", 50, 55),
            Step("loans", "Keep track", "Settings → Loans lists who owes you and whom you owe."),
        )),
        Walkthrough("phone", "Move to a new phone", listOf(
            Step("settings-data", "Back up the old phone", "Settings → Export Data saves one file with everything. Put it on Drive, email it, or copy it to a computer.", 40, 23),
            Step("settings-data", "Restore on the new phone", "Install PennyWise, finish setup, then Settings → Import Data and pick the file. Pro comes with it.", 40, 51),
        )),
    )

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
                        h1 { +"How to use PennyWise" }
                        p(classes = "g-sub") { +"Pick what you want to do and follow it step by step, or browse every screen below." }
                    }
                    walkthroughs()
                    h2(classes = "g-ref-title") { +"Every screen, explained" }

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

    // Every step is rendered server-side; the script shows one at a time. Without JS
    // the first walkthrough reads as a numbered list of screens.
    private fun FlowContent.walkthroughs() {
        section(classes = "wt") {
            id = "walkthroughs"
            div(classes = "wt-goals") {
                attributes["role"] = "tablist"
                attributes["aria-label"] = "What do you want to do?"
                walkthroughs.forEachIndexed { i, w ->
                    button(classes = "wt-goal") {
                        type = ButtonType.button
                        attributes["role"] = "tab"
                        attributes["aria-selected"] = (i == 0).toString()
                        attributes["aria-controls"] = "wt-${w.id}"
                        +w.goal
                    }
                }
            }
            walkthroughs.forEachIndexed { i, w ->
                div(classes = "wt-panel") {
                    id = "wt-${w.id}"
                    attributes["role"] = "tabpanel"
                    if (i > 0) attributes["hidden"] = ""
                    ol(classes = "wt-steps") {
                        w.steps.forEachIndexed { n, st ->
                            li(classes = "wt-step") {
                                if (n > 0) attributes["data-later"] = ""
                                div(classes = "phone") {
                                    div(classes = "screen") {
                                        img(src = src(st.shot), alt = st.title) {
                                            width = "540"; height = "1145"
                                            attributes["loading"] = if (i == 0 && n == 0) "eager" else "lazy"
                                        }
                                        if (st.x != null && st.y != null) {
                                            span(classes = "tap") {
                                                attributes["style"] = "left:${st.x}%;top:${st.y}%"
                                                attributes["aria-hidden"] = "true"
                                            }
                                        }
                                    }
                                }
                                div(classes = "wt-copy") {
                                    p(classes = "wt-count") { +"Step ${n + 1} of ${w.steps.size}" }
                                    h3 { +st.title }
                                    p { +st.text }
                                }
                            }
                        }
                    }
                    div(classes = "wt-nav") {
                        button(classes = "wt-back") { type = ButtonType.button; +"Back" }
                        div(classes = "wt-dots") { w.steps.forEach { _ -> span() } }
                        button(classes = "wt-next") { type = ButtonType.button; +"Next" }
                    }
                }
            }
        }
    }

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
        document.querySelectorAll('.wt').forEach(function (wt) {
          var goals = wt.querySelectorAll('.wt-goal'), panels = wt.querySelectorAll('.wt-panel');
          wt.classList.add('js');
          panels.forEach(function (panel) {
            var steps = panel.querySelectorAll('.wt-step'), dots = panel.querySelectorAll('.wt-dots span');
            var back = panel.querySelector('.wt-back'), next = panel.querySelector('.wt-next'), i = 0;
            function show(n) {
              i = Math.max(0, Math.min(steps.length - 1, n));
              steps.forEach(function (s, k) { s.hidden = k !== i; });
              dots.forEach(function (d, k) { d.className = k === i ? 'on' : (k < i ? 'done' : ''); });
              back.disabled = i === 0;
              next.textContent = i === steps.length - 1 ? 'Start over' : 'Next';
            }
            back.addEventListener('click', function () { show(i - 1); });
            next.addEventListener('click', function () { show(i === steps.length - 1 ? 0 : i + 1); });
            panel.addEventListener('keydown', function (e) {
              if (e.key === 'ArrowRight') show(i + 1);
              if (e.key === 'ArrowLeft') show(i - 1);
            });
            show(0);
          });
          goals.forEach(function (g) {
            g.addEventListener('click', function () {
              goals.forEach(function (o) { o.setAttribute('aria-selected', String(o === g)); });
              panels.forEach(function (p) { p.hidden = p.id !== g.getAttribute('aria-controls'); });
            });
          });
        });
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
        .g-hero { padding: 20px 0 24px; }
        .g-hero h1 { font-size: clamp(32px, 4.4vw, 48px); line-height: 1.05; letter-spacing: -.035em; font-weight: 800; margin: 0 0 12px; }
        .g-sub { font-size: 18px; line-height: 1.55; color: var(--muted); max-width: 44ch; margin: 0; }

        /* Walkthroughs: goal tabs, then one step at a time with a tap marker on the screenshot. */
        .wt { margin-bottom: 72px; }
        .wt-goals { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 28px; }
        .wt-goal { background: var(--surface); color: var(--muted); border: 1px solid var(--line); border-radius: 999px; padding: 9px 16px; font-weight: 600; font-size: 14px; }
        .wt-goal:hover { background: var(--surface-2); color: var(--text); }
        .wt-goal[aria-selected="true"] { background: var(--text); color: var(--ink); border-color: var(--text); }
        .wt-panel { position: relative; background: var(--surface); border: 1px solid var(--line); border-radius: 20px; padding: 28px 32px; }
        .wt-steps { list-style: none; margin: 0; padding: 0; }
        .wt:not(.js) .wt-step + .wt-step { margin-top: 32px; }
        .wt-step { display: grid; grid-template-columns: 230px minmax(0, 1fr); gap: 56px; align-items: center; }
        .wt-step[hidden] { display: none; }
        .wt.js .wt-step:not([hidden]) .wt-copy { animation: wt-in .35s cubic-bezier(.16,1,.3,1); }
        @keyframes wt-in { from { opacity: 0; transform: translateY(8px); } }
        .wt-count { font-family: var(--mono); font-size: 12px; color: var(--blue); margin: 0 0 10px; }
        .wt-copy h3 { font-size: clamp(24px, 3vw, 32px); font-weight: 800; letter-spacing: -.03em; line-height: 1.1; margin: 0 0 14px; }
        .wt-copy p:last-child { font-size: 17px; line-height: 1.6; color: #B9C3CF; max-width: 40ch; margin: 0; }
        .screen { position: relative; }
        .tap { position: absolute; width: 44px; height: 44px; margin: -22px 0 0 -22px; border-radius: 50%; border: 3px solid var(--blue); box-shadow: 0 0 0 4px rgba(111,178,255,.25); pointer-events: none; }
        .tap::after { content: ""; position: absolute; inset: -3px; border-radius: 50%; border: 2px solid var(--blue); animation: tap 1.6s ease-out infinite; }
        @keyframes tap { from { transform: scale(1); opacity: .9; } to { transform: scale(1.9); opacity: 0; } }
        .wt-nav { display: none; align-items: center; justify-content: space-between; gap: 16px; margin-top: 28px; padding-top: 20px; border-top: 1px solid var(--line-soft); }
        .wt.js .wt-nav { display: flex; }
        /* Desktop: controls sit under the step text, beside the phone, so they stay in view. */
        @media (min-width: 761px) {
          .wt-nav { position: absolute; left: calc(32px + 230px + 56px); right: 32px; bottom: 28px; margin: 0; padding: 0; border: 0; justify-content: flex-start; }
          .wt-dots { order: -1; margin-right: auto; }
        }
        .wt-nav button { min-width: 112px; border-radius: 999px; }
        .wt-back { background: transparent; color: var(--text); border-color: var(--line); }
        .wt-back:hover { border-color: var(--muted); }
        .wt-next:hover { background: var(--blue); border-color: var(--blue); color: var(--ink); }
        .wt-nav button:active { transform: scale(.98); }
        .wt-dots { display: flex; gap: 6px; }
        .wt-dots span { width: 8px; height: 8px; border-radius: 50%; background: var(--line); transition: background .2s, width .2s; }
        .wt-dots span.done { background: var(--muted); }
        .wt-dots span.on { background: var(--blue); width: 22px; border-radius: 4px; }
        .g-ref-title { font-size: 15px; font-weight: 600; color: var(--muted); letter-spacing: 0; margin: 0 0 8px; padding-bottom: 14px; border-bottom: 1px solid var(--line-soft); }

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
        @media (prefers-reduced-motion: reduce) { .tap::after { animation: none; } }
        @media (max-width: 760px) {
          .wt-goals { flex-wrap: nowrap; overflow-x: auto; margin: 0 -16px 20px; padding: 0 16px 4px; scroll-snap-type: x mandatory; }
          .wt-goal { flex: 0 0 auto; scroll-snap-align: start; }
          .wt-panel { padding: 20px 16px; border-radius: 16px; }
          .wt-step { grid-template-columns: minmax(0, 1fr); gap: 20px; }
          .wt-step .phone { width: min(230px, 64vw); margin: 0 auto; }
          .wt-copy { text-align: center; }
          .wt-copy p:last-child { margin: 0 auto; font-size: 16px; }
          .wt-nav button { min-width: 0; padding: 10px 18px; }
          .guide { padding: 0 16px 48px; }
          .g-hero { padding: 8px 0 24px; }
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
          .header, .g-side, .thumbs, .wt { display: none; }
          .g-layout, .g-topic { display: block; }
          .g-shots { position: static; width: 200px; margin: 16px 0; }
          .g-topic { break-inside: avoid; padding: 24px 0; }
          .g-text p, .g-text li, .g-text dd, .g-intro, .compare-row, .faq details p { color: #222 !important; }
          .path { background: #f2f2f2; color: #111; border-color: #ccc; }
        }
    """
}
