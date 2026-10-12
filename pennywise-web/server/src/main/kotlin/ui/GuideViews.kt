package com.example.ui

import com.example.ui.SharedComponents.commonHead
import com.example.ui.SharedComponents.commonStyles
import com.example.ui.SharedComponents.siteHeader
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import kotlinx.html.*

/**
 * /guide — the user guide people keep asking for in issues and on Discord.
 * One long page so it can be searched with Ctrl+F and saved as a PDF from the
 * browser. Screenshots live in static/guide/ and were taken on an emulator with
 * made-up demo data (no real SMS, names or numbers). When a screen changes
 * noticeably, retake its shot at 540px wide, status bar cropped, WebP.
 */
object GuideViews {

    private class Section(val id: String, val title: String)

    private val sections = listOf(
        Section("getting-started", "Getting started"),
        Section("home", "Home screen"),
        Section("transactions", "Transactions"),
        Section("adding", "Adding transactions yourself"),
        Section("categories-rules", "Categories & Smart Rules"),
        Section("budgets", "Budgets"),
        Section("subscriptions", "Subscriptions, recurring, loans & groups"),
        Section("accounts", "Accounts & balances"),
        Section("analytics", "Analytics"),
        Section("chat", "AI chat"),
        Section("currency", "Multiple currencies"),
        Section("data", "Import, export & backup"),
        Section("settings", "Other settings"),
        Section("pro", "Free vs Pro"),
        Section("troubleshooting", "Troubleshooting"),
    )

    suspend fun ApplicationCall.respondGuidePage() {
        respondHtml(HttpStatusCode.OK) {
            lang = "en"
            head {
                commonHead(
                    "PennyWise user guide — every feature, with screenshots",
                    "How to use PennyWise: SMS auto-tracking, budgets, rules, analytics, the on-device AI chat, " +
                        "multi-currency, backups and more, with screenshots of every screen.",
                    "/guide",
                )
                style { unsafe { +commonStyles; +guideStyles } }
            }
            body {
                siteHeader(currentPage = "guide")
                div(classes = "container prose") {
                    span(classes = "eyebrow") { +"User guide" }
                    h1 { +"How to use PennyWise" }
                    p(classes = "lede") {
                        +"PennyWise reads your bank SMS and turns them into a spending tracker — on your phone, "
                        +"with nothing sent anywhere. This guide walks through every screen. "
                        +"Want a PDF? Use your browser's Print → Save as PDF."
                    }

                    nav(classes = "toc") {
                        attributes["aria-label"] = "Contents"
                        ol { sections.forEach { s -> li { a(href = "#${s.id}") { +s.title } } } }
                    }

                    // ── 1 ───────────────────────────────────────────────────────
                    section(sections[0]) {
                        p {
                            +"Install PennyWise from "
                            a(href = SharedComponents.PLAY_STORE_URL) { +"Google Play" }
                            +" or F-Droid. The first launch walks you through four steps:"
                        }
                        ol {
                            li { b { +"Profile" }; +" — a name and avatar for the greeting. Stays on the phone." }
                            li {
                                b { +"SMS permission" }
                                +" — lets PennyWise read bank messages. Only messages from bank senders are processed; "
                                +"personal chats are ignored. You can skip this and add everything by hand instead."
                            }
                            li { b { +"First scan" }; +" — reads your existing bank SMS and creates transactions and accounts." }
                            li { b { +"Main account" }; +" — the account whose balance is shown on Home." }
                        }
                        shots(
                            "onboarding-welcome" to "Welcome",
                            "onboarding-permissions" to "SMS permission",
                            "onboarding-scan" to "First scan",
                            "onboarding-account" to "Pick your main account",
                        )
                        p {
                            +"After that, new bank SMS are picked up automatically. Check that your bank is covered on the "
                            a(href = "/banks") { +"supported banks" }
                            +" page — PennyWise reads messages from 140+ banks and wallets in 20+ countries."
                        }
                    }

                    // ── 2 ───────────────────────────────────────────────────────
                    section(sections[1]) {
                        p {
                            +"Home shows what you've spent this month, your balance, budgets, recent transactions, "
                            +"accounts, upcoming subscriptions and a spending heatmap. Tap the eye icon to hide or show amounts "
                            +"(handy in public) — PennyWise remembers your choice."
                        }
                        ul {
                            li { b { +"Sync button" }; +" (circular arrows, bottom right): tap to scan for new SMS. Long-press for a full rescan of every message." }
                            li { b { +"+ button" }; +": add a transaction or subscription by hand." }
                            li { b { +"⋯ menu" }; +": Monthly recap, Settings, Edit home screen (reorder or hide sections), Discord and rating." }
                            li { b { +"Bank icon" }; +": jumps to your accounts." }
                        }
                        shots(
                            "home" to "Home",
                            "home-bottom" to "Accounts, subscriptions and activity",
                            "home-sync" to "The sync button",
                            "monthly-recap" to "Monthly recap card",
                        )
                        p {
                            b { +"Monthly recap" }
                            +" makes a shareable card of your month. It contains no amounts — only counts — so it's safe to post."
                        }
                    }

                    // ── 3 ───────────────────────────────────────────────────────
                    section(sections[2]) {
                        p {
                            +"The Transactions tab lists everything, grouped by day. Use the search bar, or the chips under it "
                            +"to filter by period, account, type, category and currency. The summary card shows income, "
                            +"expenses, credit-card spend and net for whatever is filtered."
                        }
                        p {
                            b { +"Select several at once" }
                            +": long-press a transaction, then tap others. The top bar lets you mark them as a transfer, "
                            +"change the category, add them to a group or delete them."
                        }
                        p {
                            b { +"Open a transaction" }
                            +" to see the details and the original SMS. From there you can edit it (pencil), exclude it from "
                            +"analytics, mark it as a loan, and from the ⋮ menu add it to a group, duplicate it, report a "
                            +"parsing problem or delete it."
                        }
                        shots(
                            "transactions" to "Transactions tab",
                            "transactions-select" to "Multi-select",
                            "transaction-detail" to "Transaction details and ⋮ menu",
                        )
                        h3 { +"The five transaction types" }
                        ul {
                            li { b { +"Expense" }; +" — money out of a bank account." }
                            li { b { +"Income" }; +" — salary, refunds, cashback." }
                            li { b { +"Credit" }; +" — credit-card and wallet spend you'll pay later. Shown separately so it isn't counted twice when you pay the card bill." }
                            li { b { +"Transfer" }; +" — money between your own accounts. Doesn't count as spending." }
                            li { b { +"Investment" }; +" — SIPs, stocks, mutual funds." }
                        }
                    }

                    // ── 4 ───────────────────────────────────────────────────────
                    section(sections[3]) {
                        p { +"For cash, or a bank PennyWise can't read yet, add transactions yourself. Any of these opens the same form:" }
                        ul {
                            li { +"The + button on Home or Transactions." }
                            li { +"The " ; b { +"Add Transaction" }; +" home-screen widget or Quick Settings tile." }
                            li { +"Sharing text (an SMS, a receipt) from another app to PennyWise — it fills the form for you." }
                            li { +"Typing it in " ; a(href = "#chat") { +"AI chat" }; +", e.g. “lunch 250”." }
                        }
                        p {
                            +"Pick the type, amount, currency, merchant, account and category. Tags, notes and a receipt photo "
                            +"are optional. The Subscription tab of the same screen adds a "
                            a(href = "#subscriptions") { +"subscription" }
                            +"."
                        }
                        shots("add-transaction" to "Add a transaction", "add-subscription" to "Add a subscription")
                    }

                    // ── 5 ───────────────────────────────────────────────────────
                    section(sections[4]) {
                        p {
                            +"Every transaction gets a category automatically from the merchant. Change one by opening the transaction. "
                            +"Manage the list in "
                            path("Settings → Categories")
                            +": add your own and hide ones you don't use."
                        }
                        p {
                            b { +"Smart Rules" }
                            +" ("
                            path("Settings → Smart Rules")
                            +") fix categorisation for good. A rule says “when this matches, do that” — e.g. "
                            +"“merchant contains UBER → Transportation”, “amount under 200 → Food”, or block OTP messages. "
                            +"Start from a quick template or build your own. Rules run on every new transaction, highest priority first, "
                            +"and a rule's ⋮ menu can apply it to past transactions too."
                        }
                        shots("categories" to "Categories", "rules" to "Smart Rules", "create-rule" to "Creating a rule")
                    }

                    // ── 6 ───────────────────────────────────────────────────────
                    section(sections[5]) {
                        p {
                            +"Open Budgets from Home or "
                            path("Settings → Budgets")
                            +". “Use Smart Defaults” sets up Spending, Fixed Costs and Investments budgets from your history, "
                            +"or create your own and choose which categories count toward each."
                        }
                        ul {
                            li { +"Budgets can be weekly, monthly or one-time, with their own colour and start day." }
                            li { +"Each card shows what's left, a daily allowance and when it resets." }
                            li { +"⋮ → View history shows past periods. The current one is “Live”; finished ones are “Frozen” so they don't change when you recategorise." }
                            li { +"Paid on the 25th? Set " ; path("Settings → Budget Cycle Start Day") ; +" so months run 25th → 24th." }
                        }
                        shots("budgets" to "Budgets", "budget-edit" to "Editing a budget")
                    }

                    // ── 7 ───────────────────────────────────────────────────────
                    section(sections[6]) {
                        h3 { +"Subscriptions" }
                        p {
                            +"Track Netflix, Spotify, rent and so on from the Subscription tab of the add screen. Home shows what's "
                            +"due next and the monthly total. Subscriptions are reminders — the actual payment still comes in from your bank SMS (or add it by hand)."
                        }
                        h3 { +"Recurring" }
                        p {
                            path("Settings → Recurring")
                            +" adds a transaction for you on a schedule — useful for cash you pay regularly (house help, pocket money) that no SMS will ever report."
                        }
                        h3 { +"Loans" }
                        p {
                            +"Paid for a friend? Open the transaction and tap "
                            b { +"Mark as loan" }
                            +", then enter their name. "
                            path("Settings → Loans")
                            +" shows who owes you and whom you owe."
                        }
                        h3 { +"Transaction groups" }
                        p {
                            +"Group related transactions under a topic — a trip, a wedding, a renovation — to see their total. "
                            +"Select transactions and tap the group icon, or manage groups in "
                            path("Settings → Transaction Groups")
                            +"."
                        }
                        shots(
                            "subscriptions" to "Subscriptions",
                            "recurring" to "Recurring",
                            "mark-loan" to "Mark as loan",
                            "loans" to "Loans",
                            "groups" to "Groups",
                        )
                    }

                    // ── 8 ───────────────────────────────────────────────────────
                    section(sections[7]) {
                        p {
                            +"Accounts are created automatically from your SMS — one per bank account or card (by the last four digits). "
                            +"Balances update whenever an SMS includes one. Open "
                            path("Settings → Manage Accounts")
                            +" to:"
                        }
                        ul {
                            li { +"Update a balance by hand when it drifts, and see its balance history." }
                            li { +"Add a manual account for cash, a wallet, or a bank that doesn't send SMS." }
                            li { +"Merge duplicates (e.g. the same card reported with two different numbers) — Pro." }
                            li { +"See credit cards with their limit, outstanding and available amount." }
                        }
                        p { +"Tap an account on Home to see its balance trend and its transactions." }
                        shots("accounts" to "Manage accounts", "account-detail" to "Account detail")
                    }

                    // ── 9 ───────────────────────────────────────────────────────
                    section(sections[8]) {
                        p {
                            +"The Analytics tab breaks down any period by account and type: total, daily average, a trend chart, "
                            +"top categories, top merchants and a per-account split. Tap a category to jump to exactly those "
                            +"transactions. Pro adds a breakdown by tag."
                        }
                        shots("analytics" to "Totals and trend", "analytics-categories" to "Categories and merchants")
                    }

                    // ── 10 ──────────────────────────────────────────────────────
                    section(sections[9]) {
                        p {
                            +"The Chat tab is an AI assistant that runs entirely on your phone — your questions and data never leave it. "
                            +"It needs a one-time model download of about 1.5 GB (from the Chat tab or "
                            path("Settings → AI Features")
                            +"); use Wi-Fi."
                        }
                        ul {
                            li { b { +"Add by typing" }; +": “coffee 180 at Starbucks”, “got 50000 salary”. You always get a confirm card first — nothing is saved until you tap Add." }
                            li { b { +"Ask" }; +": “how much on groceries this month?”, “what did I spend at Swiggy?”." }
                        }
                        shots("chat-add" to "Adding by chat", "chat-ask" to "Asking a question")
                        p(classes = "muted") { +"It's a small model running on a phone: great for quick totals and quick adds, less so for long financial advice. It's slower on older phones." }
                    }

                    // ── 11 ──────────────────────────────────────────────────────
                    section(sections[10]) {
                        p {
                            +"PennyWise keeps every amount in its own currency and never adds rupees to dollars. "
                            +"Home and Transactions let you switch which currency you're looking at. The Currency section in Settings has:"
                        }
                        ul {
                            li { b { +"Unified Currency Mode" }; +" — convert everything into one display currency for combined totals." }
                            li { b { +"Default Currency" }; +" — used for manual entries; " ; b { +"Main Account" } ; +" sets it too." }
                            li { b { +"Exchange Rates" }; +" — rates are fetched automatically; tap one to set your own. Custom rates survive refreshes." }
                            li { b { +"Number Format" }; +" — Indian (1,00,000), international (100,000), or Auto to match each currency." }
                            li { b { +"Count card spend as expense" }; +" — include credit-card spend in “spent this month”." }
                        }
                        shots("settings-currency" to "Currency settings", "exchange-rates" to "Exchange rates")
                    }

                    // ── 12 ──────────────────────────────────────────────────────
                    section(sections[11]) {
                        p { +"Everything lives under " ; path("Settings → Data Management") ; +"." }
                        ul {
                            li { b { +"Export Data / Import Data" }; +" — a full backup file: transactions, accounts, budgets, rules and more. Optionally include receipt photos." }
                            li { b { +"Automatic Folder Backup" }; +" — saves a backup to a folder you choose every night at 2 AM (Pro)." }
                            li { b { +"Import Statement" }; +" — read a PDF statement from Google Pay, PhonePe, Paytm, slice or ICICI Bank. Duplicates are skipped." }
                            li { b { +"Import Transactions (CSV)" }; +" — bring back a PennyWise CSV export (or a spreadsheet with Date, Amount and Type columns)." }
                            li { b { +"Export to CSV" }; +" — the download button on the Transactions tab exports what's filtered, for Excel or Sheets." }
                            li { b { +"SMS Scan Period" }; +" — scan all your SMS, or only those after a date." }
                            li { b { +"Unrecognized SMS" }; +" — bank messages PennyWise couldn't read. Report them in one tap so support can be added." }
                            li { b { +"Delete All Transactions" }; +" — start over; accounts and budgets stay." }
                        }
                        shots("settings-data" to "Data management", "import-statement" to "Statement import", "export-csv" to "CSV export")
                        h3 { +"Moving to a new phone" }
                        ol {
                            li { +"Old phone: " ; path("Export Data") ; +" and save the file somewhere you can reach (Drive, email, USB)." }
                            li { +"New phone: install PennyWise, finish setup, then " ; path("Import Data") ; +" and pick the file." }
                        }
                        p { +"Pro moves with the backup. There's no account and no cloud sync, so a backup file is the only copy outside your phone — keep one." }
                    }

                    // ── 13 ──────────────────────────────────────────────────────
                    section(sections[12]) {
                        ul {
                            li { b { +"Appearance" }; +" — light, dark or system theme; Material You colours; AMOLED black; floating or standard navigation bar; fonts." }
                            li { b { +"Language" }; +" — the app is translated by the community." }
                            li { b { +"App Lock" }; +" — unlock with fingerprint or face. Set up biometrics in Android settings first." }
                            li { b { +"Contacts" }; +" — show a contact's name instead of their UPI ID (needs contacts permission)." }
                            li { b { +"Bank Notification Access" }; +" — also read transaction notifications from bank apps, for banks that notify instead of texting." }
                            li { b { +"Notifications" }; +" — each new transaction can notify you, with buttons to change its category right there." }
                            li { b { +"Widgets" }; +" — long-press your home screen → Widgets → PennyWise: Add Transaction, Budget Overview, Spending Pie and Recent Transactions." }
                            li { b { +"Webhooks" }; +" — send selected transaction data to your own server (Pro, for tinkerers)." }
                        }
                        shots("appearance" to "Appearance")
                    }

                    // ── 14 ──────────────────────────────────────────────────────
                    section(sections[13]) {
                        p { +"Almost everything is free: SMS tracking, budgets, analytics, AI chat, subscriptions, loans, multi-currency, backups, widgets and App Lock. Pro removes a few limits:" }
                        table(classes = "plan-table") {
                            thead { tr { th { +"" }; th { +"Free" }; th { +"Pro" } } }
                            tbody {
                                row("Smart Rules", "3", "Unlimited")
                                row("PDF statement imports", "1 a month", "Unlimited")
                                row("CSV export", "100 rows a month", "Unlimited")
                                row("Recurring transactions", "1", "Unlimited")
                                row("Automatic folder backup", "—", "✓")
                                row("Merge accounts", "—", "✓")
                                row("Analytics by tag", "—", "✓")
                                row("Webhooks", "—", "✓")
                            }
                        }
                        p {
                            +"Buy a license key on the "
                            a(href = "/pro") { +"Pro page" }
                            +", then in the app open "
                            path("Settings → PennyWise Pro → Enter license key")
                            +". F-Droid builds include every Pro feature for free."
                        }
                        shots("pro-sheet" to "PennyWise Pro", "license-key" to "Entering a license key")
                    }

                    // ── 15 ──────────────────────────────────────────────────────
                    section(sections[14]) {
                        div(classes = "faq") {
                            faq("My bank's SMS aren't being picked up.") {
                                +"Check your bank is on the "
                                a(href = "/banks") { +"supported banks" }
                                +" list and that SMS permission is on (Android settings → Apps → PennyWise → Permissions). "
                                +"Paste one of the messages into the "
                                a(href = "/") { +"SMS parser" }
                                +" on this site to see if it's read. If not, report it from "
                                path("Settings → Unrecognized SMS")
                                +" or open a "
                                a(href = "https://github.com/sarim2000/pennywiseai-tracker/issues") { +"GitHub issue" }
                                +" with the message (hide your account number)."
                            }
                            faq("Older transactions are missing.") {
                                +"Check "
                                path("Settings → SMS Scan Period")
                                +" is set to All Time, then long-press the sync button on Home for a full rescan. "
                                +"PennyWise can only see SMS still on your phone."
                            }
                            faq("A transaction shows up twice.") {
                                +"Some banks send two messages for one payment. PennyWise skips most duplicates; delete any that slip through."
                            }
                            faq("A balance is wrong.") {
                                +"Balances come from the latest SMS that mentions one. Fix it in "
                                path("Settings → Manage Accounts → Update Balance")
                                +"."
                            }
                            faq("Everything is in the wrong category.") {
                                +"Change one and create a "
                                a(href = "#categories-rules") { +"Smart Rule" }
                                +" for that merchant so it never happens again."
                            }
                            faq("Transactions stopped appearing in the background.") {
                                +"Some phones (Xiaomi, Oppo, Vivo, Samsung) kill background apps. Set PennyWise's battery usage to "
                                +"“Unrestricted” in Android settings, or just tap sync when you open the app."
                            }
                            faq("The AI model won't download, or chat is slow.") {
                                +"The model needs about 1.5 GB of free space and a stable connection; try Wi-Fi. "
                                +"Replies take a few seconds and longer on older phones."
                            }
                            faq("A backup won't restore.") {
                                +"Update PennyWise to the latest version first — new versions always read older backups. "
                                +"If it still fails, ask on Discord with the error message."
                            }
                            faq("Is my data private?") {
                                +"Yes. Your SMS and transactions are processed and stored only on your phone, and the AI runs on the phone too. "
                                +"PennyWise has no account and no servers holding your data. The "
                                a(href = "https://github.com/sarim2000/pennywiseai-tracker") { +"code is open source" }
                                +"."
                            }
                        }
                    }

                    div(classes = "footnote") {
                        +"Still stuck? Ask on "
                        a(href = "https://discord.gg/H3xWeMWjKQ") { +"Discord" }
                        +" or open an issue on "
                        a(href = "https://github.com/sarim2000/pennywiseai-tracker/issues") { +"GitHub" }
                        +". Screenshots use made-up demo data."
                    }
                }
            }
        }
    }

    private fun FlowContent.section(s: Section, body: SECTION.() -> Unit) {
        section {
            id = s.id
            h2 { a(href = "#${s.id}", classes = "anchor") { +s.title } }
            body()
        }
    }

    private fun FlowContent.path(text: String) {
        span(classes = "path") { +text }
    }

    // Screenshots are 540×1145 WebP; width/height let the browser reserve space
    // before they load so the page doesn't jump while scrolling.
    private fun FlowContent.shots(vararg shots: Pair<String, String>) {
        div(classes = "shots") {
            shots.forEach { (file, caption) ->
                figure {
                    img(src = "/static/guide/$file.webp", alt = caption) {
                        width = "540"; height = "1145"
                        attributes["loading"] = "lazy"
                        attributes["decoding"] = "async"
                    }
                    figcaption { +caption }
                }
            }
        }
    }

    private fun TBODY.row(feature: String, free: String, pro: String) {
        tr { td { +feature }; td { +free }; td { +pro } }
    }

    private fun FlowContent.faq(question: String, answer: P.() -> Unit) {
        details { summary { +question }; p { answer() } }
    }

    private val guideStyles = """
        .toc { background: var(--surface); border: 1px solid var(--line); border-radius: 14px; padding: 16px 20px; margin: 28px 0 8px; }
        .toc ol { columns: 2; column-gap: 28px; margin: 0; padding-left: 20px; }
        .toc li { margin: 5px 0; break-inside: avoid; }
        .toc a { color: var(--text); text-decoration: none; }
        .toc a:hover { color: var(--blue); }
        section { scroll-margin-top: 80px; }
        .prose h2 a.anchor { color: var(--text); text-decoration: none; }
        .prose h2 a.anchor:hover { color: var(--blue); }
        .shots { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 14px; margin: 22px 0; }
        .shots figure { margin: 0; }
        .shots img { display: block; width: 100%; height: auto; border-radius: 16px; border: 1px solid var(--line); background: var(--surface); }
        .shots figcaption { font-size: 12.5px; color: var(--muted); margin-top: 8px; text-align: center; }
        .plan-table { width: 100%; border-collapse: collapse; margin: 16px 0; font-size: 14px; }
        .plan-table th, .plan-table td { text-align: left; padding: 10px 8px; border-bottom: 1px solid var(--line-soft); color: #C4CDD8; }
        .plan-table th { color: var(--muted); font-family: var(--mono); font-size: 11px; letter-spacing: .08em; text-transform: uppercase; font-weight: 500; }
        .plan-table td:first-child { color: var(--text); }
        @media (max-width: 640px) {
          .toc ol { columns: 1; }
          .shots { grid-template-columns: 1fr 1fr; gap: 10px; }
          section { scroll-margin-top: 12px; }
        }
        @media print {
          :root { color-scheme: light; }
          body { background: #fff; color: #111; }
          .header, .toc { display: none; }
          .prose p, .prose li, .plan-table td, .shots figcaption { color: #222 !important; }
          .prose h2 a.anchor { color: #111; }
          .shots { grid-template-columns: repeat(4, 1fr); }
          .shots figure, .faq details { break-inside: avoid; }
          .path { background: #f2f2f2; color: #111; border-color: #ccc; }
        }
    """
}
