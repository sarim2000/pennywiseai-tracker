package com.example.ui

import com.example.ui.SharedComponents.commonHead
import com.example.ui.SharedComponents.commonStyles
import com.example.ui.SharedComponents.siteHeader
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put

/**
 * The user guide, structured like a help centre:
 *  - /guide          hub: search, popular guides, guides grouped by area as cards.
 *  - /guide/{slug}   one task per page: step-by-step player, "good to know", prev/next.
 *
 * `?app=1` is app mode, used when the Android app opens the guide. The Play build sells
 * nothing in-app, so in app mode the page must not lead to a purchase: a bare header (no
 * Get Pro, no links out), links to other site pages become plain text, no buy sentence on
 * the Pro guide, and guide-to-guide links keep `?app=1`.
 *
 * Every guide is a [Guide] below; the hub, the pages, search and the sitemap are all
 * generated from that one list. Screenshots live in static/guide/ and were taken on an
 * emulator with made-up demo data (no real SMS, names or numbers). Tap-marker positions
 * ([Step.x]/[Step.y], percent of the 540×1145 shot) must be re-checked if a shot is retaken.
 */
object GuideViews {

    private class Step(val shot: String, val title: String, val text: String, val x: Int? = null, val y: Int? = null)

    private class Guide(
        val slug: String,
        val group: String,
        val title: String,
        val intro: String,
        val steps: List<Step>,
        val keywords: String = "",
        /** Screenshot for the hub card's preview, when the first step's top half is empty. */
        val cover: String? = null,
        /** [app] is true in app mode (see the class doc); only the Pro guide uses it. */
        val details: FlowContent.(app: Boolean) -> Unit,
    )

    private val groups = listOf("Get started", "Everyday", "Plan ahead", "Organise", "Your data")

    private val guides: List<Guide> = listOf(
        // ── Get started ────────────────────────────────────────────────────────
        Guide(
            "set-up", "Get started", "Set up PennyWise",
            "Give it SMS access and it builds your history from the bank messages already on your phone.",
            listOf(
                Step("onboarding-welcome", "Open the app", "Tap Get Started. Setup takes about a minute and has four steps."),
                Step("onboarding-permissions", "Allow SMS access", "Tap Enable Permissions, then Allow. Only bank messages are read; your chats are never touched.", 50, 67),
                Step("onboarding-scan", "Let it scan", "PennyWise reads the bank SMS already on your phone and turns them into transactions."),
                Step("onboarding-account", "Pick your main account", "This balance shows on Home. Tap Finish.", 83, 92),
                Step("home-sync", "You're done", "New bank SMS are added automatically. Tap the sync button any time to check now.", 89, 86),
            ),
            "install start onboarding permission begin first",
            cover = "onboarding-account",
        ) {
            p {
                +"PennyWise reads messages from 140+ banks and wallets across 20+ countries. Check yours on the "
                a(href = "/banks") { +"supported banks" }
                +" list."
            }
            p { +"Skipped the SMS permission? Everything still works; you add transactions by hand instead." }
        },
        Guide(
            "find-your-way", "Get started", "Find your way around",
            "Home, the four tabs, and what each button does.",
            listOf(
                Step("home", "Home", "What you've spent this month, your balance and budgets. Tap the eye to hide amounts in public."),
                Step("home-bottom", "Keep scrolling", "Recent transactions, accounts, upcoming subscriptions and a heatmap of when you spend."),
                Step("home-sync", "The sync button", "Tap to check for new SMS. Long-press for a full rescan of every message.", 89, 86),
                Step("home", "More options", "⋯ opens Settings, Monthly recap, and Edit home screen to reorder or hide sections.", 88, 13),
                Step("monthly-recap", "Monthly recap", "A shareable card of your month. It shows counts, never amounts. Tap Share.", 50, 84),
            ),
            "home tabs navigation menu overview recap share",
        ) {
            dl(classes = "defs") {
                def("Transactions tab", "Everything PennyWise has found, with search and filters.")
                def("Analytics tab", "Where your money goes, by category, merchant and account.")
                def("Chat tab", "An AI assistant that runs on your phone.")
            }
        },
        Guide(
            "pro", "Get started", "Free vs Pro",
            "Almost everything is free. Pro lifts a few limits and pays for new banks and features.",
            listOf(
                Step("pro-sheet", "Open PennyWise Pro", "Settings → Upgrade to PennyWise Pro, then tap Enter license key.", 50, 75),
                Step("license-key", "Paste your key", "Paste the key from your purchase email and tap Activate. Pro turns on right away.", 50, 51),
            ),
            "license key upgrade premium buy activate price",
        ) {
            div(classes = "compare") {
                div(classes = "compare-head") { span { +"" }; span { +"Free" }; span { +"Pro" } }
                compare("Smart Rules", "3", "Unlimited")
                compare("PDF statement imports", "1 a month", "Unlimited")
                compare("CSV export", "100 rows a month", "Unlimited")
                compare("Recurring transactions", "1", "Unlimited")
                compare("Folder backup, merge accounts, tag analytics, webhooks", "No", "Yes")
            }
            if (it) {
                p { +"Already have a key from your purchase email? Enter it as shown above. F-Droid builds include every Pro feature for free." }
            } else {
                p {
                    +"Buy a key on the "
                    a(href = "/pro") { +"Pro page" }
                    +". F-Droid builds include every Pro feature for free."
                }
            }
        },

        // ── Everyday ───────────────────────────────────────────────────────────
        Guide(
            "add-transaction", "Everyday", "Add a transaction yourself",
            "For cash, or a bank PennyWise can't read yet.",
            listOf(
                Step("home", "Tap +", "From Home or Transactions, the + button opens the add screen.", 90, 78),
                Step("add-transaction", "Enter the amount", "Type the amount, then a merchant like “Chai stall”. Expense is already selected.", 67, 19),
                Step("add-transaction", "Pick a category", "Choose where it belongs so budgets and analytics stay accurate.", 28, 76),
                Step("add-transaction", "Save", "That's it. It now counts toward this month's spending.", 50, 91),
                Step("chat-add", "Or just type it", "In the Chat tab, write “coffee 180 at Starbucks” and tap Add on the card.", 71, 50),
            ),
            "manual cash expense add record",
        ) {
            p { +"The Add Transaction widget and Quick Settings tile open the same form. You can also share a message from another app to PennyWise and it fills the form for you." }
            h3 { +"The five types" }
            div(classes = "types") {
                type("Expense", "Money out of a bank account.")
                type("Income", "Salary, refunds, cashback.")
                type("Credit", "Card and wallet spend you pay later, kept apart so the bill isn't counted twice.")
                type("Transfer", "Between your own accounts. Not spending.")
                type("Investment", "SIPs, stocks, mutual funds.")
            }
        },
        Guide(
            "edit-transactions", "Everyday", "Find and edit transactions",
            "Search and filter, fix one, or change many at once.",
            listOf(
                Step("transactions", "Search or filter", "The Transactions tab filters by period, account, type, category and currency.", 40, 20),
                Step("transaction-detail", "Fix one", "Tap a transaction to see the original SMS. The pencil edits it; ⋮ duplicates, groups or deletes it.", 81, 5),
                Step("transactions", "Change many at once", "Press and hold one transaction until it highlights.", 40, 58),
                Step("transactions-select", "Tap the rest", "Tap every other one you want. The totals card shows just your selection.", 40, 77),
                Step("transactions-select", "Pick an action", "The top bar changes their category, groups or deletes them. To mark a transfer, select exactly two (the money out and the money in); the transfer button then appears.", 70, 5),
            ),
            "search filter bulk select multiple edit delete recategorise duplicate tag tags note receipt photo split",
        ) {
            dl(classes = "defs") {
                def("Tags and notes", "The pencil adds tags, a description and a display name. Tags can apply to every future transaction from the same merchant.")
                def("Receipt photos", "Attach one from the gallery or camera when adding or editing.")
                def("Split into categories", "Spread one expense across several categories from its detail page.")
                def("Same merchant, every time", "When changing a category, apply it to all transactions from that merchant.")
                def("Exclude from analytics", "Keeps it in your history and balance but out of spending totals.")
            }
        },
        Guide(
            "analytics", "Everyday", "See where your money goes",
            "Totals, trends, top categories and merchants for any period.",
            listOf(
                Step("analytics", "Choose a period", "Open the Analytics tab and pick a period, account or type from the chips at the top.", 76, 18),
                Step("analytics-categories", "Tap a category", "Scroll to Top Categories and tap one to see exactly which transactions make it up.", 68, 24),
            ),
            "chart report spending breakdown merchant category stats",
        ) {
            p { +"Pro adds a breakdown by tag." }
        },
        Guide(
            "ai-chat", "Everyday", "Ask the AI about your spending",
            "An assistant that runs entirely on your phone. Nothing you ask leaves it.",
            listOf(
                Step("chat-download", "Download the model", "The first time you open the Chat tab it downloads about 1.5 GB. You need about 3 GB free while it downloads. Use Wi-Fi; it only happens once."),
                Step("chat-add", "Add by typing", "Write “got 50000 salary” or “coffee 180”. A confirm card appears; nothing is saved until you tap Add.", 71, 50),
                Step("chat-ask", "Ask questions", "“How much on groceries this month?” “What did I spend at Swiggy?”", 40, 83),
            ),
            "ai assistant question chat model offline",
        ) {
            p { +"It's a small model on a phone: quick totals and quick adds, not long financial advice. Older phones take a few seconds longer to reply." }
        },

        // ── Plan ahead ─────────────────────────────────────────────────────────
        Guide(
            "budgets", "Plan ahead", "Set a monthly budget",
            "Set limits by category and see what's left, with a daily allowance.",
            listOf(
                Step("budgets-setup", "Open Budgets", "From Home tap View All next to Budgets. Use Smart Defaults starts from your own spending.", 50, 64),
                Step("budgets", "See what's left", "Each budget shows what's left this month and how much you can spend per day.", 70, 25),
                Step("budget-edit", "Adjust it", "The pencil changes the limit, colour, period, and the day your month starts."),
            ),
            "budget limit spending plan",
        ) {
            p { +"Budgets can be weekly, monthly or one-time. ⋮ → View history shows past periods; finished ones are frozen, so recategorising later doesn't rewrite them." }
            p { +"Paid on the 25th? Set "; path("Settings → Budget Cycle Start Day"); +" so months run 25th to 24th." }
        },
        Guide(
            "subscriptions", "Plan ahead", "Track subscriptions",
            "Netflix, Spotify, rent: see what's due next and the monthly total.",
            listOf(
                Step("add-subscription", "Add one", "Tap +, switch to the Subscription tab, then enter the service, amount and billing cycle.", 75, 11),
                Step("add-subscription", "Save it", "It now shows on Home with the next due date.", 50, 91),
                Step("subscriptions", "See them all", "Open Subscriptions from Home. ⋮ on one edits or ends it.", 86, 43),
            ),
            "netflix spotify bill renew due",
        ) {
            p { +"Subscriptions are reminders. The payment itself still comes in from your bank SMS, or add it by hand." }
        },
        Guide(
            "recurring", "Plan ahead", "Auto-add a regular cash payment",
            "For money you pay on a schedule that no SMS will report.",
            listOf(
                Step("recurring", "Open Recurring", "Settings → Recurring, then tap +.", 89, 92),
                Step("recurring-new", "Set the schedule", "Name it, enter the amount and pick the day of the month. Tap Save.", 76, 84),
                Step("recurring", "It adds itself", "On that day each month the transaction is added for you."),
            ),
            "schedule rent maid allowance automatic repeat",
        ) {
            p { +"Free accounts get one recurring transaction; Pro is unlimited." }
        },
        Guide(
            "loans", "Plan ahead", "Lent and borrowed money",
            "Track money you lend to friends and money you borrow, including partial repayments.",
            listOf(
                Step("loan-income", "Start from the payment", "Open the payment you made for them, or the money they sent you, and tap Mark as loan. No transaction yet? Add one first.", 50, 37),
                Step("loan-lent", "Lent: say who", "On money you paid out, it asks who you paid for. Enter their name and how much they owe; it can be less than the full amount.", 50, 91),
                Step("loan-borrowed", "Borrowed: say who", "On money you received, it becomes a loan you owe: “Who paid for you?” Enter the name and Confirm.", 50, 91),
                Step("loans", "See who owes whom", "Settings → Loans totals what you're owed and what you owe, person by person. Settle up clears everything with one person.", 83, 59),
                Step("loan-repay", "Record a repayment", "Open a loan and tap +. Link the transaction they paid you back with, or type the amount under Or enter manually.", 50, 57),
                Step("loan-detail", "Watch it go down", "History lists every payment. Partial repayments are fine, and the loan settles itself when it reaches zero. ⋮ settles early, changes the amount expected back, or deletes it.", 93, 5),
            ),
            "lend lent lending borrow borrowed loan split owe owes owed owing friend repay repaid repayment debt iou money back",
            cover = "loans",
        ) {
            p { +"Loans stay out of your totals: lent money isn't spending, borrowed money isn't income, and repayments aren't either, so your monthly numbers stay honest." }
            p { +"Paid back by UPI? Marking that incoming payment as a loan with the same person's name records it as a repayment automatically." }
            p { +"Settle a loan you lent for less than you're owed and the unpaid part counts as spending in the month you settle it." }
            p { +"Each loan is in one currency. There are no due dates or interest yet. Active loans also show on Home, which you can hide with Edit home screen." }
        },
        Guide(
            "groups", "Plan ahead", "Total up a trip or event",
            "Collect a holiday or a wedding under one name to see what it cost.",
            listOf(
                Step("transactions-select", "Select its transactions", "On the Transactions tab, long-press one, tap the rest, then tap the group icon.", 81, 5),
                Step("groups", "See the total", "Name the group. Settings → Transaction Groups shows each group's total."),
            ),
            "group trip wedding event project total",
        ) {
            p { +"A transaction's ⋮ menu can also add it to a group later." }
        },

        // ── Organise ───────────────────────────────────────────────────────────
        Guide(
            "categories", "Organise", "Fix wrong categories for good",
            "Correct one, then add a rule so it never happens again.",
            listOf(
                Step("transaction-detail", "Fix one transaction", "Open it and tap the pencil to pick the right category.", 81, 5),
                Step("rules", "Open Smart Rules", "Settings → Smart Rules, then tap + to make a rule.", 89, 92),
                Step("create-rule", "Describe the rule", "Start from a template, or say “merchant contains UBER → Transportation”.", 50, 28),
                Step("categories", "Tidy your categories", "Settings → Categories adds your own and hides ones you don't use."),
            ),
            "rule categorise categorize wrong merchant automatic",
        ) {
            p { +"Rules run on every new transaction, highest priority first. A rule's ⋮ menu applies it to past transactions too. Free accounts get three rules; Pro is unlimited." }
        },
        Guide(
            "accounts", "Organise", "Accounts and balances",
            "Fix a balance that's off, or add cash and wallets.",
            listOf(
                Step("accounts", "Open Manage Accounts", "Settings → Manage Accounts. Tap Update Balance if a balance is off.", 30, 32),
                Step("accounts", "Add cash or a wallet", "Tap + to add an account that doesn't send SMS, like cash in hand.", 89, 92),
                Step("account-detail", "Check its history", "Tap an account on Home to see its balance trend and transactions.", 75, 36),
            ),
            "balance wrong cash wallet manual account credit card merge low alert business personal profile",
        ) {
            p { +"Accounts are created from your SMS, one per bank account or card. Balances update whenever a message includes one. Credit cards show their limit, outstanding and available amount." }
            p { +"Same card showing up twice under different numbers? Merge them from Manage Accounts (Pro)." }
            dl(classes = "defs") {
                def("Low-balance alert", "An account's ⋮ menu sets a threshold; the account is flagged when it drops below it.")
                def("Personal and Business", "Mark an account as Business from its ⋮ menu, then use the profile filter on Home and Transactions to see one side at a time.")
            }
        },
        Guide(
            "currencies", "Organise", "Use more than one currency",
            "Every amount keeps its currency. Rupees and dollars are never added together.",
            listOf(
                Step("settings-currency", "Turn on Unified Currency Mode", "Settings → Currency. It converts everything into one display currency for combined totals.", 86, 12),
                Step("exchange-rates", "Set your own rate", "Exchange Rates update on their own. Tap one to override it.", 86, 22),
            ),
            "currency usd dollar exchange rate foreign travel",
        ) {
            dl(classes = "defs") {
                def("Default Currency", "Used for manual entries. Choosing a Main Account sets it too.")
                def("Number Format", "Indian (1,00,000), international (100,000), or Auto per currency.")
            }
        },
        Guide(
            "personalise", "Organise", "Make it yours",
            "Theme, app lock, widgets and the rest of Settings.",
            listOf(
                Step("appearance", "Appearance", "Settings → Appearance: light, dark or system theme, Material You colours, AMOLED black, fonts."),
            ),
            "theme dark light lock fingerprint widget notification webhook language contacts",
        ) {
            dl(classes = "defs") {
                def("App Lock", "Fingerprint or face unlock, with a lock timeout. Set up biometrics in Android first.")
                def("Widgets", "Long-press your home screen → Widgets → PennyWise: Add Transaction, Budget Overview, Spending Pie, Recent Transactions.")
                def("Notifications", "Each new transaction can notify you, with buttons to change its category right there.")
                def("Bank Notification Access", "Also read bank app notifications, for banks that notify instead of texting.")
                def("Contacts", "Show a contact's name instead of their UPI ID.")
                def("Language", "The app is translated by the community.")
                def("Webhooks", "Send selected transaction data to your own server (Pro).")
            }
        },

        // ── Your data ──────────────────────────────────────────────────────────
        Guide(
            "backup", "Your data", "Back up and move to a new phone",
            "There's no cloud, so a backup file is your only copy outside the phone.",
            listOf(
                Step("settings-data", "Back up", "Settings → Export Data saves one file with everything. Keep it on Drive, email or a computer.", 40, 23),
                Step("settings-data", "Restore on the new phone", "Install PennyWise, finish setup, then Settings → Import Data and pick the file. Pro comes with it.", 40, 51),
            ),
            "backup restore export import transfer switch device new phone",
        ) {
            dl(classes = "defs") {
                def("Automatic Folder Backup", "Saves a backup to a folder you choose every night at 2 AM (Pro).")
                def("Include receipt photos", "Adds attached receipts to the backup. Bigger file.")
                def("SMS Scan Period", "Scan everything, or only messages after a date.")
                def("Delete All Transactions", "Start over. Accounts and budgets stay.")
            }
        },
        Guide(
            "import-statement", "Your data", "Import a PDF statement",
            "Bring in history from Google Pay, PhonePe, Paytm, slice or ICICI Bank.",
            listOf(
                Step("settings-data", "Open Import Statement", "Settings → Import Statement.", 40, 67),
                Step("import-statement", "Pick the PDF", "Tap Select PDF Statement and choose the file. Duplicates are skipped.", 50, 56),
            ),
            "pdf gpay phonepe paytm upi history import",
            cover = "import-statement",
        ) {
            p { +"Free accounts can import one statement a month; Pro is unlimited." }
        },
        Guide(
            "export", "Your data", "Export to Excel",
            "Get your transactions into Sheets or Excel as a CSV.",
            listOf(
                Step("transactions", "Filter, then tap download", "On the Transactions tab, filter to what you want, then tap the download button.", 90, 77),
                Step("export-csv", "Export", "Tap Export and share the file to Sheets, Excel or email.", 65, 62),
            ),
            "csv excel sheets spreadsheet download export",
        ) {
            p {
                path("Settings → Import Transactions (CSV)")
                +" reads a PennyWise CSV back in, or any sheet with Date, Amount and Type columns. Free exports are capped at 100 rows a month."
            }
        },
    )

    private class Faq(val q: String, val k: String, val a: P.() -> Unit)

    private val faqs = listOf(
        Faq("My bank's SMS aren't being picked up.", "detect detected not working unsupported bank missing parse") {
            +"Check your bank is on the "
            a(href = "/banks") { +"supported banks" }
            +" list and SMS permission is on. Paste one message into the "
            a(href = "/") { +"SMS parser" }
            +" to see if it's read. If not, report it from "
            path("Settings → Unrecognized SMS")
            +" or open a "
            a(href = "https://github.com/sarim2000/pennywiseai-tracker/issues") { +"GitHub issue" }
            +" with the message (hide your account number)."
        },
        Faq("Older transactions are missing.", "old history scan rescan past") {
            +"Set "
            path("Settings → SMS Scan Period")
            +" to All Time, then long-press the sync button for a full rescan. PennyWise can only see SMS still on your phone."
        },
        Faq("A transaction shows up twice.", "duplicate double twice repeated") {
            +"Some banks send two messages for one payment. Most duplicates are skipped; delete any that slip through."
        },
        Faq("A balance is wrong.", "incorrect balance mismatch fix") {
            +"Balances come from the latest SMS that mentions one. "
            a(href = "/guide/accounts") { +"Correct it in Manage Accounts" }
            +"."
        },
        Faq("New transactions stop appearing in the background.", "background battery stopped not updating notification") {
            +"Some phones (Xiaomi, Oppo, Vivo, Samsung) stop background apps. Set PennyWise's battery usage to Unrestricted, or tap sync when you open the app."
        },
        Faq("The AI model won't download, or chat is slow.", "ai model download slow chat stuck") {
            +"The download is about 1.5 GB, but it needs about 3 GB of free space while downloading, and a steady connection, so try Wi-Fi. Replies take a few seconds, longer on older phones."
        },
        Faq("A backup won't restore.", "restore import backup failed error") {
            +"Update PennyWise first; new versions always read older backups. If it still fails, ask on Discord with the error."
        },
        Faq("Is my data private?", "privacy secure safe data server") {
            +"Yes. SMS and transactions are processed and stored only on your phone, and the AI runs there too. No account, no servers holding your data, and the "
            a(href = "https://github.com/sarim2000/pennywiseai-tracker") { +"code is open source" }
            +"."
        },
    )

    private val popular = listOf("set-up", "add-transaction", "budgets", "backup", "categories")

    /** Paths for the sitemap. */
    val paths: List<String> = listOf("/guide") + guides.map { "/guide/${it.slug}" } + "/guide/troubleshooting"

    private fun url(g: Guide) = "/guide/${g.slug}"
    private fun groupId(group: String) = group.lowercase().replace(' ', '-')
    private fun src(file: String) = "/static/guide/$file.webp"

    // ── Pages ────────────────────────────────────────────────────────────────

    suspend fun ApplicationCall.respondGuideHub() {
        respondGuideHtml { app ->
            page(app, "PennyWise help: step-by-step guides with screenshots",
                "Step-by-step help for every part of PennyWise: setup, budgets, rules, AI chat, backups and more.", "/guide") {
                header(classes = "hub-hero") {
                    h1 { +"How can we help?" }
                    p(classes = "hub-sub") { +"Step-by-step guides for every part of PennyWise, with screenshots." }
                    search(large = true)
                    p(classes = "popular") {
                        span { +"Popular:" }
                        popular.mapNotNull { s -> guides.find { it.slug == s } }.forEach { g -> a(href = url(g)) { +g.title } }
                    }
                }
                groups.forEach { group ->
                    section(classes = "hub-group") {
                        id = groupId(group)
                        h2 { +group }
                        div(classes = "cards") {
                            guides.filter { it.group == group }.forEach { card(it) }
                        }
                    }
                }
                section(classes = "stuck") {
                    div {
                        h2 { +"Still stuck?" }
                        p { +"The questions people ask most, with fixes." }
                        ul {
                            faqs.take(4).forEachIndexed { i, f -> li { a(href = "/guide/troubleshooting#q-${i + 1}") { +f.q } } }
                        }
                        a(href = "/guide/troubleshooting", classes = "more") { +"All troubleshooting" }
                    }
                    div(classes = "stuck-ask") {
                        p { b { +"Ask a person." }; +" Other PennyWise users and the developer answer on Discord." }
                        a(href = "https://discord.gg/H3xWeMWjKQ", classes = "btn-solid") { +"Ask on Discord" }
                        a(href = "https://github.com/sarim2000/pennywiseai-tracker/issues", classes = "btn-line") { +"Report a bug" }
                    }
                }
            }
        }
    }

    /** Returns false when the slug doesn't exist, so the route can 404. */
    suspend fun ApplicationCall.respondGuidePage(slug: String): Boolean {
        if (slug == "troubleshooting") { respondTroubleshooting(); return true }
        val i = guides.indexOfFirst { it.slug == slug }
        if (i < 0) return false
        val g = guides[i]
        respondGuideHtml { app ->
            page(app, "${g.title}: PennyWise guide", g.intro, url(g)) {
                articleHead(g.group, g.title, g.intro)
                player(g)
                section(classes = "know") {
                    h2 { +"Good to know" }
                    div(classes = "know-body") { g.details(this, app) }
                }
                prevNext(guides.getOrNull(i - 1), guides.getOrNull(i + 1))
            }
        }
        return true
    }

    private suspend fun ApplicationCall.respondTroubleshooting() {
        respondGuideHtml { app ->
            page(app, "Troubleshooting: PennyWise guide", "Fixes for the PennyWise questions people ask most.", "/guide/troubleshooting") {
                articleHead(null, "Troubleshooting", "Fixes for the questions people ask most.")
                div(classes = "faq faq-page") {
                    faqs.forEachIndexed { i, f ->
                        details {
                            id = "q-${i + 1}"
                            summary { +f.q }
                            p { f.a(this) }
                        }
                    }
                }
                div(classes = "stuck stuck-inline") {
                    div(classes = "stuck-ask") {
                        p { b { +"Not here?" }; +" Ask on Discord, or report a bug on GitHub." }
                        a(href = "https://discord.gg/H3xWeMWjKQ", classes = "btn-solid") { +"Ask on Discord" }
                        a(href = "https://github.com/sarim2000/pennywiseai-tracker/issues", classes = "btn-line") { +"Report a bug" }
                    }
                }
            }
        }
    }

    // ── Building blocks ──────────────────────────────────────────────────────

    // Rendered to a string so app mode can rewrite links in one place, whatever page built them.
    private suspend fun ApplicationCall.respondGuideHtml(block: HTML.(app: Boolean) -> Unit) {
        val app = request.queryParameters["app"] == "1"
        val html = "<!DOCTYPE html>\n" + createHTML().html { block(app) }
        respondText(if (app) appModeLinks(html) else html, ContentType.Text.Html)
    }

    private val siteLink = Regex("""<a\b[^>]*\bhref="/(?!guide)[^"]*"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
    private val guideLink = Regex("""href="(/guide[^"#?]*)(#[^"]*)?"""")

    /** Links to other site pages (which carry Get Pro) become plain text; guide links keep app mode. */
    internal fun appModeLinks(html: String): String =
        html.replace(siteLink, "$1").replace(guideLink) { m -> "href=\"${m.groupValues[1]}?app=1${m.groupValues[2]}\"" }

    private fun HTML.page(app: Boolean, title: String, description: String, path: String, content: DIV.() -> Unit) {
        lang = "en"
        head {
            commonHead(title, description, path)
            style { unsafe { +commonStyles; +guideStyles } }
        }
        body {
            if (app) appHeader() else siteHeader(currentPage = "guide")
            div(classes = "guide") { content() }
            script(type = "application/json") { id = "guide-index"; unsafe { +(if (app) appSearchIndex else searchIndex) } }
            script { unsafe { +guideScript } }
        }
    }

    private fun FlowContent.appHeader() {
        div(classes = "header") {
            div(classes = "header-content") {
                div(classes = "logo-section") {
                    img(src = "/static/logo.png", alt = "", classes = "logo")
                    span(classes = "logo-text") { +"PennyWise guide" }
                }
                nav(classes = "nav-links") { a(href = "/guide") { +"All guides" } }
            }
        }
    }

    private fun DIV.articleHead(group: String?, title: String, intro: String) {
        div(classes = "art-top") {
            nav(classes = "crumbs") {
                attributes["aria-label"] = "Breadcrumb"
                a(href = "/guide") { +"Guide" }
                if (group != null) { span { +"/" }; a(href = "/guide#${groupId(group)}") { +group } }
            }
            search(large = false)
        }
        header(classes = "art-head") {
            h1 { +title }
            p { +intro }
        }
    }

    private fun FlowContent.search(large: Boolean) {
        div(classes = if (large) "g-search large" else "g-search") {
            label(classes = "sr-only") { htmlFor = if (large) "guide-q" else "guide-q-small"; +"Search the guide" }
            input(type = InputType.search) {
                id = if (large) "guide-q" else "guide-q-small"
                placeholder = if (large) "Search, e.g. backup, budget, duplicate" else "Search the guide"
                attributes["autocomplete"] = "off"
                attributes["spellcheck"] = "false"
                attributes["role"] = "combobox"
                attributes["aria-expanded"] = "false"
                attributes["aria-autocomplete"] = "list"
                attributes["data-search"] = ""
            }
            kbd { +"/" }
            ul(classes = "g-hits") { attributes["role"] = "listbox"; attributes["hidden"] = "" }
        }
    }

    private fun FlowContent.card(g: Guide) {
        a(href = url(g), classes = "card") {
            div(classes = "peek") {
                img(src = src(g.cover ?: g.steps.first().shot), alt = "") {
                    width = "540"; height = "1145"
                    attributes["loading"] = "lazy"
                    attributes["decoding"] = "async"
                }
            }
            div(classes = "card-body") {
                h3 { +g.title }
                p { +g.intro }
                span(classes = "card-meta") { +if (g.steps.size == 1) "1 screen" else "${g.steps.size} steps" }
            }
        }
    }

    // All steps are rendered server-side; the script shows one at a time. Without JS
    // they read as a numbered list of screens.
    private fun FlowContent.player(g: Guide) {
        section(classes = "player") {
            attributes["aria-label"] = "${g.title}, step by step"
            ol(classes = "steps-list") {
                g.steps.forEachIndexed { n, st ->
                    li(classes = "step") {
                        id = "step-${n + 1}"
                        div(classes = "phone") {
                            div(classes = "screen") {
                                img(src = src(st.shot), alt = st.title) {
                                    width = "540"; height = "1145"
                                    attributes["loading"] = if (n == 0) "eager" else "lazy"
                                }
                                if (st.x != null && st.y != null) {
                                    span(classes = "tap") {
                                        attributes["style"] = "left:${st.x}%;top:${st.y}%"
                                        attributes["aria-hidden"] = "true"
                                    }
                                }
                            }
                        }
                        div(classes = "step-copy") {
                            if (g.steps.size > 1) p(classes = "step-count") { +"Step ${n + 1} of ${g.steps.size}" }
                            h2 { +st.title }
                            p { +st.text }
                        }
                    }
                }
            }
            if (g.steps.size > 1) {
                div(classes = "player-nav") {
                    div(classes = "dots") { g.steps.forEach { _ -> span() } }
                    button(classes = "back") { type = ButtonType.button; +"Back" }
                    button(classes = "next") { type = ButtonType.button; +"Next" }
                }
            }
        }
    }

    private fun DIV.prevNext(prev: Guide?, next: Guide?) {
        nav(classes = "pager") {
            attributes["aria-label"] = "More guides"
            if (prev != null) a(href = url(prev), classes = "pg prev") { span { +"Previous" }; b { +prev.title } } else div {}
            if (next != null) a(href = url(next), classes = "pg next") { span { +"Next" }; b { +next.title } }
            else a(href = "/guide/troubleshooting", classes = "pg next") { span { +"Still stuck?" }; b { +"Troubleshooting" } }
        }
    }

    private fun DL.def(term: String, desc: String) { dt { +term }; dd { +desc } }

    private fun FlowContent.type(name: String, desc: String) { div(classes = "type") { b { +name }; span { +desc } } }

    private fun FlowContent.compare(feature: String, free: String, pro: String) {
        div(classes = "compare-row") { span { +feature }; span { +free }; span { +pro } }
    }

    private fun FlowContent.path(text: String) { span(classes = "path") { +text } }

    // ── Search index (embedded as JSON on every page) ────────────────────────

    private fun plain(block: DIV.() -> Unit): String =
        createHTML().div { block() }.replace(Regex("<[^>]+>"), " ").replace("&amp;", "&").replace(Regex("\\s+"), " ").trim()

    // Built per mode so app-mode search can't surface text the app-mode page leaves out.
    private val searchIndex: String by lazy { buildIndex(app = false) }
    private val appSearchIndex: String by lazy { buildIndex(app = true) }

    private fun buildIndex(app: Boolean): String =
        buildJsonArray {
            guides.forEach { g ->
                addJsonObject {
                    put("kind", "Guide"); put("title", g.title); put("where", g.group); put("url", url(g))
                    put("text", g.intro + " " + plain { g.details(this, app) }); put("k", g.keywords)
                }
                g.steps.forEachIndexed { n, st ->
                    addJsonObject {
                        put("kind", "Step"); put("title", st.title); put("where", g.title)
                        put("url", url(g) + "#step-${n + 1}"); put("text", st.text); put("k", "")
                    }
                }
            }
            faqs.forEachIndexed { i, f ->
                addJsonObject {
                    put("kind", "Answer"); put("title", f.q); put("where", "Troubleshooting")
                    put("url", "/guide/troubleshooting#q-${i + 1}"); put("text", plain { p { f.a(this) } }); put("k", f.k)
                }
            }
        }.toString().replace("</", "<\\/")

    private val guideScript = """
        // Step player: one step at a time, deep-linkable as #step-N.
        document.querySelectorAll('.player').forEach(function (pl) {
          var steps = pl.querySelectorAll('.step'), dots = pl.querySelectorAll('.dots span');
          var back = pl.querySelector('.back'), next = pl.querySelector('.next'), i = 0;
          if (!next) return;
          pl.classList.add('js');
          function show(n, fromHash) {
            i = Math.max(0, Math.min(steps.length - 1, n));
            steps.forEach(function (s, k) { s.hidden = k !== i; });
            dots.forEach(function (d, k) { d.className = k === i ? 'on' : (k < i ? 'done' : ''); });
            back.disabled = i === 0;
            next.textContent = i === steps.length - 1 ? 'Start over' : 'Next';
            if (!fromHash) history.replaceState(null, '', i ? '#step-' + (i + 1) : location.pathname + location.search);
          }
          back.addEventListener('click', function () { show(i - 1); });
          next.addEventListener('click', function () { show(i === steps.length - 1 ? 0 : i + 1); });
          document.addEventListener('keydown', function (e) {
            if (document.activeElement && document.activeElement.tagName === 'INPUT') return;
            if (e.key === 'ArrowRight') show(i + 1);
            if (e.key === 'ArrowLeft') show(i - 1);
          });
          function fromHash() {
            var m = /^#step-(\d+)$/.exec(location.hash);
            show(m ? +m[1] - 1 : 0, true);
            if (m) pl.scrollIntoView({ block: 'start' });
          }
          window.addEventListener('hashchange', fromHash);
          fromHash();
        });

        // Troubleshooting deep links open their answer.
        function openFaq() {
          var d = /^#q-\d+$/.test(location.hash) && document.querySelector('details' + location.hash);
          if (d) { d.open = true; d.classList.remove('flash'); void d.offsetWidth; d.classList.add('flash'); }
        }
        window.addEventListener('hashchange', openFaq); openFaq();

        // Save as PDF: print every answer, then put them back as they were.
        var closedForPrint = [];
        window.addEventListener('beforeprint', function () {
          closedForPrint = [].filter.call(document.querySelectorAll('details'), function (d) { return !d.open; });
          closedForPrint.forEach(function (d) { d.open = true; });
        });
        window.addEventListener('afterprint', function () { closedForPrint.forEach(function (d) { d.open = false; }); });

        // Search: a dropdown over an index of every guide, step and answer.
        (function () {
          var APP = /[?&]app=1(&|$)/.test(location.search);
          function withApp(u) {
            if (!APP) return u;
            var h = u.indexOf('#'), base = h < 0 ? u : u.slice(0, h);
            return base + '?app=1' + (h < 0 ? '' : u.slice(h));
          }
          var idx = JSON.parse(document.getElementById('guide-index').textContent);
          var STOP = { a: 1, an: 1, the: 1, to: 1, my: 1, is: 1, i: 1, how: 1, do: 1, of: 1, in: 1, on: 1, for: 1, it: 1, can: 1, what: 1, why: 1, with: 1, and: 1, or: 1, me: 1, are: 1, be: 1 };
          function terms(v) {
            return v.toLowerCase().split(/[^a-z0-9]+/).filter(function (w) { return w.length > 1 && !STOP[w]; })
              .map(function (w) { return w.length > 4 ? w.replace(/(ing|ed|es|s)$/, '') : w; });
          }
          function esc(s) { return s.replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }
          // One regex over raw text, so a short word can't match inside an inserted <mark>.
          function mark(s, words) {
            if (!words.length) return esc(s);
            var re = new RegExp('(' + words.map(function (w) { return w.replace(/[.*+?^{}()|[\]\\$]/g, '\\$&'); }).join('|') + ')', 'ig');
            return s.split(re).map(function (part, k) { return k % 2 ? '<mark>' + esc(part) + '</mark>' : esc(part); }).join('');
          }
          function snippet(s, words) {
            var low = s.toLowerCase(), at = -1;
            words.forEach(function (w) { var p = low.indexOf(w); if (p >= 0 && (at < 0 || p < at)) at = p; });
            if (at < 0 || s.length <= 110) return s.slice(0, 110) + (s.length > 110 ? '…' : '');
            var from = Math.max(0, s.lastIndexOf(' ', Math.max(0, at - 30)) + 1);
            return (from ? '…' : '') + s.slice(from, from + 110) + (from + 110 < s.length ? '…' : '');
          }
          function search(v) {
            var words = terms(v), seen = {}, need = Math.max(1, Math.ceil(words.length * 0.6));
            if (!words.length) return [];
            return idx.map(function (e, i) {
              var title = e.title.toLowerCase(), body = (e.text + ' ' + e.k).toLowerCase(), m = 0, score = 0;
              words.forEach(function (w) {
                var t = title.indexOf(w);
                if (t >= 0) { m++; score += t === 0 ? 4 : 3; }
                else if (body.indexOf(w) >= 0) { m++; score += 1; }
                if (e.k.indexOf(w) >= 0) score += 2;
              });
              if (m < need) return null;
              if (e.kind === 'Guide') score += 1;
              return { e: e, m: m, score: score, i: i };
            }).filter(Boolean).sort(function (a, b) { return b.m - a.m || b.score - a.score || a.i - b.i; }).filter(function (h) {
              // Dedupe after sorting so the best entry for a title survives.
              var key = h.e.title + '|' + h.e.where; if (seen[key]) return false; seen[key] = 1; return true;
            }).slice(0, 8);
          }
          document.querySelectorAll('[data-search]').forEach(function (q) {
            var box = q.parentNode.querySelector('.g-hits'), hits = [], active = 0;
            box.id = q.id + '-hits'; q.setAttribute('aria-controls', box.id);
            function close() { box.hidden = true; q.setAttribute('aria-expanded', 'false'); q.removeAttribute('aria-activedescendant'); }
            function render() {
              var v = q.value, words = terms(v);
              hits = []; active = 0;
              if (!v.trim()) { close(); return; }
              hits = search(v);
              box.innerHTML = hits.length ? hits.map(function (h, n) {
                var e = h.e;
                return '<li role="option" id="' + box.id + '-' + n + '"' + (n === 0 ? ' aria-selected="true"' : '') + '>' +
                  '<a class="g-hit" href="' + withApp(e.url) + '" tabindex="-1">' +
                  '<span class="hit-kind hit-' + e.kind.toLowerCase() + '">' + e.kind + '</span>' +
                  '<span class="hit-body"><span class="hit-title">' + mark(e.title, words) + '</span>' +
                  '<span class="hit-where">' + esc(e.where) + '</span>' +
                  (e.text ? '<span class="hit-text">' + mark(snippet(e.text, words), words) + '</span>' : '') + '</span></a></li>';
              }).join('') : '<li class="g-nohit">Nothing matches “' + esc(v.trim()) + '”. Try another word, or <a href="https://discord.gg/H3xWeMWjKQ">ask on Discord</a>.</li>';
              box.hidden = false; q.setAttribute('aria-expanded', 'true');
              if (hits.length) q.setAttribute('aria-activedescendant', box.id + '-0');
            }
            function move(d) {
              if (!hits.length) return;
              active = (active + d + hits.length) % hits.length;
              box.querySelectorAll('li[role=option]').forEach(function (li, n) {
                if (n === active) { li.setAttribute('aria-selected', 'true'); li.scrollIntoView({ block: 'nearest' }); } else li.removeAttribute('aria-selected');
              });
              q.setAttribute('aria-activedescendant', box.id + '-' + active);
            }
            q.addEventListener('input', render);
            q.addEventListener('focus', function () { if (q.value.trim()) render(); });
            q.addEventListener('keydown', function (ev) {
              if (ev.key === 'ArrowDown') { ev.preventDefault(); move(1); }
              else if (ev.key === 'ArrowUp') { ev.preventDefault(); move(-1); }
              else if (ev.key === 'Enter' && !box.hidden && hits[active]) { ev.preventDefault(); close(); location.href = withApp(hits[active].e.url); }
              else if (ev.key === 'Escape') { if (box.hidden) q.value = ''; close(); }
            });
            box.addEventListener('mousemove', function (ev) {
              var li = ev.target.closest('li[role=option]'); if (!li) return;
              var n = [].indexOf.call(box.children, li); if (n !== active) move(n - active);
            });
            box.addEventListener('click', close);
            document.addEventListener('click', function (ev) { if (!q.parentNode.contains(ev.target)) close(); });
          });
          document.addEventListener('keydown', function (ev) {
            var tag = document.activeElement.tagName;
            if (ev.key === '/' && tag !== 'INPUT' && tag !== 'TEXTAREA') {
              var q = document.querySelector('[data-search]'); if (q) { ev.preventDefault(); q.focus(); q.select(); }
            }
          });
        })();
    """

    // Radius scale: 12px controls and small panels, 18px cards and the player, 30px phone frames.
    private val guideStyles = """
        .guide { max-width: 1120px; margin: 0 auto; padding: 8px 24px 80px; }
        .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }

        /* Hub */
        .hub-hero { padding: 40px 0 16px; max-width: 720px; }
        .hub-hero h1 { font-size: clamp(34px, 5vw, 52px); line-height: 1.05; letter-spacing: -.035em; font-weight: 800; margin: 0 0 12px; }
        .hub-sub { font-size: 18px; color: var(--muted); margin: 0 0 26px; }
        .popular { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 16px; margin: 16px 0 0; font-size: 14px; }
        .popular span { color: var(--muted); }
        .popular a { color: var(--text); text-decoration: none; border-bottom: 1px solid var(--line); padding-bottom: 1px; }
        .popular a:hover { border-color: var(--blue); color: var(--blue); }
        .hub-group { padding-top: 56px; scroll-margin-top: 72px; }
        .hub-group h2 { font-size: 22px; font-weight: 750; letter-spacing: -.02em; margin: 0 0 18px; }
        .cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 16px; }
        .card { display: flex; flex-direction: column; background: var(--surface); border: 1px solid var(--line-soft); border-radius: 18px; overflow: hidden; text-decoration: none; color: inherit; transition: border-color .2s, transform .2s cubic-bezier(.16,1,.3,1); }
        .card:hover { border-color: var(--line); transform: translateY(-2px); }
        .card:active { transform: translateY(0) scale(.99); }
        /* A peek at the guide's first screen: the top of the phone, cropped. */
        .peek { height: 150px; overflow: hidden; padding: 18px 28px 0; background: linear-gradient(180deg, var(--surface-2), var(--surface)); }
        .peek img { display: block; width: 100%; height: auto; border-radius: 14px 14px 0 0; border: 1px solid var(--line); border-bottom: 0; transition: transform .35s cubic-bezier(.16,1,.3,1); }
        .card:hover .peek img { transform: translateY(-6px); }
        .card-body { padding: 16px 18px 18px; display: flex; flex-direction: column; flex: 1; }
        .card-body h3 { font-size: 16px; font-weight: 700; letter-spacing: -.01em; margin: 0 0 6px; }
        .card-body p { font-size: 14px; color: #A9B4C1; line-height: 1.5; margin: 0 0 14px; flex: 1; }
        .card-meta { font-size: 12.5px; color: var(--muted); }
        .stuck { display: grid; grid-template-columns: 1.3fr 1fr; gap: 16px; margin-top: 72px; }
        .stuck > div { background: var(--surface); border: 1px solid var(--line-soft); border-radius: 18px; padding: 26px 28px; }
        .stuck h2 { font-size: 22px; font-weight: 750; letter-spacing: -.02em; margin: 0 0 6px; }
        .stuck > div > p { color: var(--muted); margin: 0 0 14px; }
        .stuck ul { list-style: none; padding: 0; margin: 0 0 18px; }
        .stuck li a { display: block; padding: 9px 0; color: var(--text); text-decoration: none; border-bottom: 1px solid var(--line-soft); }
        .stuck li:last-child a { border-bottom: 0; }
        .stuck li a:hover { color: var(--blue); }
        .more { color: var(--blue); font-weight: 600; font-size: 14px; text-decoration: none; }
        .stuck-ask { display: flex; flex-direction: column; align-items: flex-start; justify-content: center; gap: 12px; }
        .stuck-ask p { color: #B9C3CF; margin: 0 0 6px; line-height: 1.6; }
        .stuck-ask b { color: var(--text); }
        .btn-solid, .btn-line { display: inline-block; padding: 11px 18px; border-radius: 12px; font-weight: 700; font-size: 14px; text-decoration: none; }
        .btn-solid { background: var(--text); color: var(--ink); }
        .btn-solid:hover { background: var(--blue); }
        .btn-line { color: var(--text); border: 1px solid var(--line); }
        .btn-line:hover { border-color: var(--muted); }
        .stuck-inline { grid-template-columns: 1fr; margin-top: 48px; }
        .stuck-inline .stuck-ask { flex-direction: row; align-items: center; flex-wrap: wrap; }
        .stuck-inline .stuck-ask p { margin: 0 auto 0 0; }

        /* Search */
        .g-search { position: relative; z-index: 40; }
        .g-search input { width: 100%; height: 44px; padding: 0 40px 0 14px; color: var(--text); font-family: var(--body); font-size: 14.5px; border-radius: 12px; background: var(--surface); border: 1px solid var(--line); -webkit-appearance: none; transition: border-color .15s, box-shadow .15s; }
        .g-search.large input { height: 56px; padding: 0 52px 0 20px; font-size: 16px; border-radius: 14px; }
        .g-search input::-webkit-search-cancel-button { -webkit-appearance: none; }
        .g-search input::placeholder { color: var(--muted); }
        .g-search input:hover { border-color: #2E3843; }
        .g-search input:focus { outline: none; border-color: var(--blue); box-shadow: 0 0 0 3px rgba(111,178,255,.16); }
        .g-search kbd { position: absolute; right: 12px; top: 22px; transform: translateY(-50%); font-family: var(--mono); font-size: 12px; color: var(--muted); border: 1px solid var(--line); border-radius: 6px; padding: 1px 7px; pointer-events: none; }
        .g-search.large kbd { top: 28px; right: 16px; }
        .g-search input:focus + kbd, .g-search input:not(:placeholder-shown) + kbd { display: none; }
        .g-hits { position: absolute; top: calc(100% + 8px); left: 0; right: 0; min-width: 300px; margin: 0; padding: 6px; list-style: none; background: var(--surface-2); border: 1px solid var(--line); border-radius: 14px; box-shadow: 0 24px 60px -16px rgba(0,0,0,.75); max-height: min(460px, 62vh); overflow-y: auto; }
        .g-search:not(.large) .g-hits { left: auto; width: min(480px, 92vw); }
        .g-hit { display: flex; gap: 12px; align-items: flex-start; padding: 10px 12px; border-radius: 10px; text-decoration: none; color: inherit; }
        li[aria-selected="true"] > .g-hit { background: rgba(111,178,255,.1); }
        .hit-kind { flex: 0 0 62px; margin-top: 2px; font-size: 11px; font-weight: 600; color: var(--muted); }
        .hit-guide { color: var(--blue); }
        .hit-body { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
        .hit-title { color: var(--text); font-weight: 600; font-size: 14.5px; }
        .hit-where { color: var(--muted); font-size: 12px; }
        .hit-text { color: #A9B4C1; font-size: 13px; line-height: 1.5; margin-top: 2px; }
        .g-hits mark { background: none; color: var(--blue); font-weight: 600; }
        .g-nohit { padding: 14px 12px; color: var(--muted); font-size: 14px; }
        .g-nohit a { color: var(--blue); }

        /* Guide page */
        .art-top { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 20px 0 0; }
        .art-top .g-search { width: 280px; }
        .crumbs { font-size: 14px; display: flex; gap: 8px; }
        .crumbs a { color: var(--muted); text-decoration: none; }
        .crumbs a:hover { color: var(--text); }
        .crumbs span { color: var(--line); }
        .art-head { padding: 28px 0 28px; max-width: 720px; }
        .art-head h1 { font-size: clamp(30px, 4.4vw, 46px); line-height: 1.06; letter-spacing: -.035em; font-weight: 800; margin: 0 0 12px; }
        .art-head p { font-size: 18px; color: var(--muted); margin: 0; line-height: 1.55; }

        .player { position: relative; background: var(--surface); border: 1px solid var(--line); border-radius: 18px; padding: 32px 40px; }
        .steps-list { list-style: none; margin: 0; padding: 0; }
        .player:not(.js) .step + .step { margin-top: 40px; }
        .step { display: grid; grid-template-columns: 240px minmax(0, 1fr); gap: 64px; align-items: center; scroll-margin-top: 80px; }
        .step[hidden] { display: none; }
        .player.js .step:not([hidden]) .step-copy { animation: in .35s cubic-bezier(.16,1,.3,1); }
        @keyframes in { from { opacity: 0; transform: translateY(8px); } }
        .player.js .step-copy { padding-bottom: 72px; }
        .step-count { font-family: var(--mono); font-size: 12px; color: var(--blue); margin: 0 0 10px; }
        .step-copy h2 { font-size: clamp(24px, 3vw, 32px); font-weight: 800; letter-spacing: -.03em; line-height: 1.1; margin: 0 0 14px; }
        .step-copy p:last-child { font-size: 17px; line-height: 1.6; color: #B9C3CF; max-width: 40ch; margin: 0; }
        .phone { border-radius: 30px; padding: 7px; background: #05070A; border: 1px solid var(--line); box-shadow: 0 24px 60px -24px rgba(0,0,0,.7), inset 0 1px 0 rgba(255,255,255,.05); }
        .screen { position: relative; }
        .screen img { display: block; width: 100%; height: auto; border-radius: 23px; background: var(--surface); }
        .tap { position: absolute; width: 44px; height: 44px; margin: -22px 0 0 -22px; border-radius: 50%; border: 3px solid var(--blue); box-shadow: 0 0 0 4px rgba(111,178,255,.25); pointer-events: none; }
        .tap::after { content: ""; position: absolute; inset: -3px; border-radius: 50%; border: 2px solid var(--blue); animation: tap 1.6s ease-out infinite; }
        @keyframes tap { from { transform: scale(1); opacity: .9; } to { transform: scale(1.9); opacity: 0; } }
        .player-nav { display: none; position: absolute; left: calc(40px + 240px + 64px); right: 40px; bottom: 32px; align-items: center; gap: 10px; }
        .player.js .player-nav { display: flex; }
        .dots { display: flex; gap: 6px; margin-right: auto; }
        .dots span { width: 8px; height: 8px; border-radius: 50%; background: var(--line); transition: background .2s, width .2s; }
        .dots span.done { background: var(--muted); }
        .dots span.on { background: var(--blue); width: 22px; border-radius: 4px; }
        .player-nav button { min-width: 104px; border-radius: 12px; }
        .back { background: transparent; color: var(--text); border-color: var(--line); }
        .back:hover { border-color: var(--muted); }
        .next:hover { background: var(--blue); border-color: var(--blue); color: var(--ink); }
        .player-nav button:active { transform: scale(.98); }

        .know { max-width: 720px; padding-top: 48px; }
        .know h2 { font-size: 20px; font-weight: 750; letter-spacing: -.02em; margin: 0 0 14px; }
        .know h3 { font-size: 15px; font-weight: 700; margin: 26px 0 12px; }
        .know-body p, .know-body dd { color: #B9C3CF; font-size: 16px; line-height: 1.65; }
        .know-body p { margin: 0 0 14px; }
        .know-body a, .faq a { color: var(--blue); text-underline-offset: 3px; }
        .defs { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: 12px 24px; margin: 0; }
        .defs dt { color: var(--text); font-weight: 600; font-size: 15px; padding-top: 2px; }
        .defs dd { margin: 0; }
        .types { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 10px; }
        .type { background: var(--surface); border: 1px solid var(--line-soft); border-radius: 12px; padding: 12px 14px; font-size: 14px; color: #B9C3CF; line-height: 1.5; }
        .type b { display: block; color: var(--text); margin-bottom: 2px; }
        .compare { border: 1px solid var(--line); border-radius: 12px; overflow: hidden; font-size: 14.5px; margin: 0 0 18px; }
        .compare-head, .compare-row { display: grid; grid-template-columns: minmax(0, 1.6fr) 1fr 1fr; gap: 12px; padding: 12px 16px; }
        .compare-head { background: var(--surface-2); color: var(--muted); font-size: 13px; font-weight: 600; }
        .compare-row { color: #B9C3CF; }
        .compare-row:nth-child(odd) { background: var(--surface); }
        .compare-row span:first-child { color: var(--text); }
        .compare-row span:last-child { color: var(--blue); font-weight: 600; }

        .pager { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-top: 56px; padding-top: 32px; border-top: 1px solid var(--line-soft); }
        .pg { display: flex; flex-direction: column; gap: 4px; padding: 18px 20px; border: 1px solid var(--line-soft); border-radius: 18px; text-decoration: none; color: inherit; transition: border-color .15s; }
        .pg:hover { border-color: var(--line); }
        .pg span { font-size: 13px; color: var(--muted); }
        .pg b { color: var(--text); font-size: 16px; }
        .pg.next { text-align: right; grid-column: 2; }

        .faq-page { max-width: 760px; }
        .faq details p { color: #B9C3CF; font-size: 15.5px; line-height: 1.65; }
        .faq details { scroll-margin-top: 80px; border-radius: 10px; }
        .flash { animation: flash 1.4s ease-out; }
        @keyframes flash { 0%, 30% { box-shadow: 0 0 0 2px var(--blue); background: rgba(111,178,255,.06); } 100% { box-shadow: 0 0 0 2px transparent; } }
        @media (prefers-reduced-motion: reduce) { .tap::after { animation: none; } }

        @media (max-width: 760px) {
          .guide { padding: 0 16px 56px; }
          .hub-hero { padding: 16px 0 8px; }
          .hub-group { padding-top: 40px; }
          .cards { grid-template-columns: minmax(0, 1fr); gap: 10px; }
          /* Phone: cards become compact rows, peek on the left. */
          .card { flex-direction: row; border-radius: 14px; }
          .peek { flex: 0 0 92px; height: 108px; padding: 12px 12px 0; }
          .peek img { border-radius: 10px 10px 0 0; }
          .card-body { padding: 14px 16px; }
          .card-body p { margin-bottom: 6px; }
          .stuck { grid-template-columns: minmax(0, 1fr); margin-top: 48px; }
          .stuck > div { padding: 22px 20px; }
          .art-top { flex-direction: column; align-items: stretch; gap: 14px; padding-top: 8px; }
          .art-top .g-search { width: 100%; }
          .g-search:not(.large) .g-hits { left: 0; width: auto; }
          .hit-kind { display: none; }
          .art-head { padding: 22px 0 20px; }
          .player { padding: 20px 16px; border-radius: 16px; }
          .step { grid-template-columns: minmax(0, 1fr); gap: 20px; }
          .step .phone { width: min(230px, 64vw); margin: 0 auto; }
          .step-copy, .player.js .step-copy { text-align: center; padding-bottom: 0; }
          .step-copy p:last-child { margin: 0 auto; font-size: 16px; }
          .player-nav { position: static; margin-top: 22px; }
          .player-nav button { min-width: 0; padding: 10px 18px; }
          .defs { grid-template-columns: minmax(0, 1fr); gap: 2px; }
          .defs dd { margin-bottom: 12px; }
          .compare-head, .compare-row { grid-template-columns: minmax(0, 1.3fr) 1fr 1fr; padding: 11px 12px; font-size: 13.5px; }
          .pager { grid-template-columns: minmax(0, 1fr); }
          .pg.next { grid-column: 1; }
        }
        @media print {
          :root { color-scheme: light; }
          body { background: #fff; color: #111; }
          .header, .g-search, .player-nav, .pager, .stuck { display: none; }
          .step, .step[hidden] { display: grid !important; break-inside: avoid; grid-template-columns: 160px 1fr; gap: 24px; margin-bottom: 24px; }
          .step-copy { padding: 0 !important; }
          .player { border: 0; padding: 0; }
          .guide, .guide * { color: #111 !important; box-shadow: none !important; }
          .guide a { color: #0B57D0 !important; }
          .type, .compare, .compare-head, .compare-row, .player { background: none !important; border-color: #ccc !important; }
          .path { background: #f2f2f2; color: #111; border-color: #ccc; }
        }
    """
}
