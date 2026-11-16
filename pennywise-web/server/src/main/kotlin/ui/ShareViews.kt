package com.example.ui

import com.example.share.ShareAchievement
import com.example.share.ShareChart
import com.example.share.ShareService
import com.example.share.ShareSnapshot
import com.example.ui.SharedComponents.OgImage
import com.example.ui.SharedComponents.SITE_ORIGIN
import com.example.ui.SharedComponents.commonHead
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.html.*
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * /s/{id} — what a friend lands on after tapping a shared link.
 *
 * The page IS the HTML rendering of the snapshot; the OG image exists only for the chat
 * preview and is never shown here. Every string comes pre-formatted from the app, so this
 * file only arranges them. The card keeps the snapshot's fixed palette (the same one the
 * app drew the image in, so the preview and the page read as one thing); the page around
 * it follows the visitor's light/dark preference.
 */
object ShareViews {

    /** Nothing third-party, no inline script; inline styles only for per-chart column counts. */
    const val CONTENT_SECURITY_POLICY =
        "default-src 'none'; style-src 'unsafe-inline'; font-src 'self'; img-src 'self'; " +
            "script-src 'self'; connect-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'"

    private class Chrome(
        val madeWith: String,
        val report: String,
        val reportConfirm: String,
        val reported: String,
        val highlights: String,
        val achievements: String,
        val unlocked: String,
        val sharedFrom: String,
        val kinds: Map<String, String>,
        val notFoundTitle: String,
        val notFoundBody: String,
        val goneTitle: String,
        val goneBody: String,
        val home: String,
    )

    private val EN = Chrome(
        madeWith = "Made with PennyWise",
        report = "Report",
        reportConfirm = "Report this page as abusive or inappropriate?",
        reported = "Reported. Thank you.",
        highlights = "Highlights",
        achievements = "Achievements",
        unlocked = "Unlocked %s",
        sharedFrom = "Shared from PennyWise, the private money tracker.",
        kinds = mapOf(
            "recap" to "Recap", "spending" to "Spending", "savings" to "Savings",
            "goal" to "Goal", "budget" to "Budget", "achievements" to "Achievements",
        ),
        notFoundTitle = "This share doesn't exist",
        notFoundBody = "Check the link, or ask the person who sent it to share it again.",
        goneTitle = "This share is no longer available",
        goneBody = "It expired, or the person who shared it took it down.",
        home = "Go to PennyWise",
    )

    private val AR = Chrome(
        madeWith = "صُنع بواسطة PennyWise",
        report = "إبلاغ",
        reportConfirm = "هل تريد الإبلاغ عن هذه الصفحة كمحتوى مسيء أو غير لائق؟",
        reported = "تم الإبلاغ. شكرًا لك.",
        highlights = "أبرز الأرقام",
        achievements = "الإنجازات",
        unlocked = "تحقّق في %s",
        sharedFrom = "تمت المشاركة من PennyWise، متتبّع المصاريف الخاص.",
        kinds = mapOf(
            "recap" to "ملخّص", "spending" to "الإنفاق", "savings" to "الادخار",
            "goal" to "هدف", "budget" to "الميزانية", "achievements" to "الإنجازات",
        ),
        notFoundTitle = "هذه المشاركة غير موجودة",
        notFoundBody = "تحقّق من الرابط، أو اطلب من المرسل مشاركته مرة أخرى.",
        goneTitle = "لم تعد هذه المشاركة متاحة",
        goneBody = "انتهت صلاحيتها، أو أزالها من شاركها.",
        home = "الذهاب إلى PennyWise",
    )

    private fun chromeFor(languageTag: String?): Chrome =
        if (languageTag?.substringBefore('-')?.lowercase() == "ar") AR else EN

    suspend fun ApplicationCall.respondSharePage(id: String, s: ShareSnapshot) {
        val c = chromeFor(s.locale)
        val locale = Locale.forLanguageTag(s.locale)
        val description = s.subtitle ?: s.hero?.let { "${it.label}: ${it.value}" } ?: c.sharedFrom
        respondHtml {
            attributes["lang"] = s.locale
            attributes["dir"] = if (s.isRtl) "rtl" else "ltr"
            head {
                commonHead(
                    pageTitle = "${s.title} · PennyWise",
                    description = description,
                    canonicalPath = "/s/$id",
                    ogTitle = s.title,
                    ogImage = OgImage(
                        url = "$SITE_ORIGIN/s/$id/og.png",
                        width = ShareService.OG_WIDTH,
                        height = ShareService.OG_HEIGHT,
                        alt = s.title,
                    ),
                    noindex = true,
                )
                meta { name = "color-scheme"; content = "dark light" }
                style { unsafe { +SharedComponents.commonStyles; +shareStyles } }
                script { src = "/static/share.js"; defer = true }
            }
            body(classes = "share-page theme-${s.theme}") {
                header(classes = "share-bar") {
                    a(href = "/", classes = "share-brand") {
                        img(src = "/static/logo.png", alt = "", classes = "share-logo")
                        span { +"PennyWise" }
                    }
                    span(classes = "eyebrow") { +(c.kinds[s.kind] ?: s.kind) }
                }
                main(classes = "share-main") {
                    shareCard(s, c, locale)
                    if (s.achievements.isNotEmpty()) achievementsGrid(s.achievements, c, locale, heading = s.kind != "achievements")
                    footer(classes = "share-foot") {
                        a(href = "/", classes = "share-made") {
                            img(src = "/static/logo.png", alt = "", classes = "share-logo-sm")
                            span { +c.madeWith }
                        }
                        form(action = "/s/$id/report", method = FormMethod.post) {
                            this.id = "share-report"
                            attributes["data-confirm"] = c.reportConfirm
                            attributes["data-done"] = c.reported
                            button(type = ButtonType.submit, classes = "share-report") { +c.report }
                        }
                    }
                }
            }
        }
    }

    private fun FlowContent.shareCard(s: ShareSnapshot, c: Chrome, locale: Locale) {
        article(classes = "share-card") {
            // The kind is already named in the bar above; only a period earns a second label.
            s.periodLabel?.let { div(classes = "sc-eyebrow") { +it } }
            h1(classes = "sc-title") { +s.title }
            s.subtitle?.let { p(classes = "sc-sub") { +it } }

            s.hero?.let { h ->
                div(classes = "sc-hero") {
                    div(classes = "sc-hero-label") { +h.label }
                    div(classes = "sc-hero-row") {
                        val tint = if (h.delta == null) " s-${h.sentiment}" else ""
                        span(classes = "sc-hero-value$tint") { +h.value }
                        h.delta?.let { span(classes = "sc-delta s-${h.sentiment}") { +it } }
                    }
                }
            }

            s.chart?.let { chart ->
                div(classes = "sc-chart chart-${chart.type}") {
                    when (chart.type) {
                        "bars" -> columnChart(chart, s.isRtl, line = false)
                        "line" -> columnChart(chart, s.isRtl, line = true)
                        "ring" -> ringChart(chart, s.isRtl, locale)
                        "diverging" -> divergingChart(chart, s.isRtl)
                    }
                }
            }

            if (s.stats.isNotEmpty()) {
                div(classes = "sc-stats") {
                    attributes["aria-label"] = c.highlights
                    s.stats.forEach { st ->
                        div(classes = "sc-stat") {
                            span(classes = "sc-stat-label") { +st.label }
                            span(classes = "sc-stat-value s-${st.sentiment}") { +st.value }
                        }
                    }
                }
            }
        }
    }

    private fun Double.fmt(): String = "%.2f".format(Locale.ROOT, this)

    /**
     * Bars and lines share one layout: the SVG draws only the marks, stretched to the plot
     * box, and the labels sit in an HTML grid with one column per point underneath. Text
     * never scales with the SVG, so it stays legible on a phone, and in RTL the grid flows
     * right-to-left on its own; the marks are mirrored to match.
     */
    private fun FlowContent.columnChart(chart: ShareChart, rtl: Boolean, line: Boolean) {
        val pts = chart.points
        val n = pts.size
        val width = n * 10.0
        fun slotX(i: Int) = (if (rtl) n - 1 - i else i) * 10.0 + 5.0
        val hl = chart.highlightIndex
        val svg = StringBuilder()
        svg.append("""<svg class="plot" viewBox="0 0 ${width.fmt()} 100" preserveAspectRatio="none" aria-hidden="true">""")
        if (line) {
            val lo = pts.minOf { it.value }
            val hi = pts.maxOf { it.value }
            val span = hi - lo
            fun y(v: Double) = if (span == 0.0) 50.0 else 8.0 + (hi - v) / span * 84.0
            val coords = pts.indices.sortedBy { slotX(it) }.map { slotX(it) to y(pts[it].value) }
            val linePath = coords.mapIndexed { k, (x, yy) -> (if (k == 0) "M" else "L") + "${x.fmt()} ${yy.fmt()}" }.joinToString(" ")
            svg.append("""<defs><linearGradient id="area" x1="0" y1="0" x2="0" y2="1"><stop offset="0" class="area-top"/><stop offset="1" class="area-bottom"/></linearGradient></defs>""")
            if (n > 1) {
                svg.append("""<path class="area" d="$linePath L${coords.last().first.fmt()} 100 L${coords.first().first.fmt()} 100 Z"/>""")
                svg.append("""<path class="line" d="$linePath" vector-effect="non-scaling-stroke"/>""")
            }
            pts.forEachIndexed { i, p ->
                // A zero-length round-capped stroke with a non-scaling width renders as a true
                // circle even though the plot is stretched non-uniformly.
                val cls = if (i == hl) "dot hl" else "dot"
                svg.append("""<path class="$cls" d="M${slotX(i).fmt()} ${y(p.value).fmt()} h0.01" vector-effect="non-scaling-stroke"/>""")
            }
        } else {
            val lo = min(0.0, pts.minOf { it.value })
            val hi = max(0.0, pts.maxOf { it.value })
            val span = (hi - lo).takeIf { it > 0 } ?: 1.0
            fun y(v: Double) = (hi - v) / span * 100.0
            pts.forEachIndexed { i, p ->
                val top = y(max(p.value, 0.0))
                val bottom = y(min(p.value, 0.0))
                val h = max(bottom - top, 1.2)
                val cls = when {
                    hl == null -> "bar"
                    i == hl -> "bar hl"
                    else -> "bar dim"
                }
                svg.append("""<rect class="$cls" x="${(slotX(i) - 2.9).fmt()}" y="${min(top, 100.0 - h).fmt()}" width="5.8" height="${h.fmt()}"/>""")
            }
            svg.append("""<line class="axis" x1="0" x2="${width.fmt()}" y1="${y(0.0).fmt()}" y2="${y(0.0).fmt()}" vector-effect="non-scaling-stroke"/>""")
        }
        svg.append("</svg>")
        div(classes = "plot-box") { unsafe { +svg.toString() } }
        // Only the highlighted value is printed when there are many points: twelve amounts
        // in a row on a phone is a table, not a chart.
        val showAll = n <= 6
        div(classes = "ticks") {
            style = "grid-template-columns: repeat($n, minmax(0, 1fr))"
            pts.forEachIndexed { i, p ->
                div(classes = if (i == hl) "tick hl" else "tick") {
                    span(classes = "tick-label") { +p.label }
                    if (p.display != null && (showAll || i == hl)) span(classes = "tick-value") { +p.display }
                }
            }
        }
    }

    private fun FlowContent.ringChart(chart: ShareChart, rtl: Boolean, locale: Locale) {
        val p = chart.points.first()
        val pct = (p.value * 100).coerceIn(0.0, 100.0)
        // Clockwise from twelve o'clock; mirrored in RTL so progress runs in reading order.
        val transform = if (rtl) "translate(120 0) scale(-1 1) rotate(-90 60 60)" else "rotate(-90 60 60)"
        val cap = if (pct > 0) "round" else "butt"
        val svg = """<svg class="ring" viewBox="0 0 120 120" aria-hidden="true">""" +
            """<circle class="ring-track" cx="60" cy="60" r="50"/>""" +
            """<circle class="ring-arc" cx="60" cy="60" r="50" pathLength="100" stroke-linecap="$cap" stroke-dasharray="${pct.fmt()} 100" transform="$transform"/>""" +
            "</svg>"
        div(classes = "ring-wrap") {
            div(classes = "ring-box") {
                unsafe { +svg }
                span(classes = "ring-value") { +(p.display ?: NumberFormat.getPercentInstance(locale).format(p.value)) }
            }
            if (p.label.isNotBlank()) div(classes = "ring-label") { +p.label }
        }
    }

    private fun FlowContent.divergingChart(chart: ShareChart, rtl: Boolean) {
        val maxAbs = chart.points.maxOf { abs(it.value) }.takeIf { it > 0 } ?: 1.0
        div(classes = "div-rows") {
            chart.points.forEachIndexed { i, p ->
                val reach = p.value / maxAbs * 48.0
                val (from, to) = if (rtl) (50.0 - reach) to 50.0 else 50.0 to (50.0 + reach)
                val x = min(from, to)
                val w = max(abs(to - from), 0.8)
                val cls = buildString {
                    append(if (p.value < 0) "neg" else "pos")
                    if (chart.highlightIndex != null && chart.highlightIndex != i) append(" dim")
                }
                div(classes = if (chart.highlightIndex == i) "div-row hl" else "div-row") {
                    span(classes = "div-label") { +p.label }
                    div(classes = "div-bar") {
                        unsafe {
                            +(
                                """<svg viewBox="0 0 100 10" preserveAspectRatio="none" aria-hidden="true">""" +
                                    """<line class="axis" x1="50" x2="50" y1="0" y2="10" vector-effect="non-scaling-stroke"/>""" +
                                    """<rect class="$cls" x="${x.fmt()}" y="1.5" width="${w.fmt()}" height="7"/></svg>"""
                                )
                        }
                    }
                    span(classes = "div-value") { +(p.display ?: "") }
                }
            }
        }
    }

    private fun FlowContent.achievementsGrid(items: List<ShareAchievement>, c: Chrome, locale: Locale, heading: Boolean) {
        val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        section(classes = "share-section") {
            if (heading) h2(classes = "eyebrow section-title") { +c.achievements }
            div(classes = "ach-grid") {
                items.forEach { a ->
                    div(classes = "ach tier-${a.tier}") {
                        unsafe { +MEDAL_SVG }
                        div(classes = "ach-body") {
                            div(classes = "ach-title") { +a.title }
                            if (a.description.isNotBlank()) div(classes = "ach-desc") { +a.description }
                            div(classes = "ach-meta") {
                                if (a.tier > 0) {
                                    span(classes = "pips") {
                                        attributes["aria-hidden"] = "true"
                                        repeat(5) { k -> span(classes = if (k < a.tier) "pip on" else "pip") {} }
                                    }
                                }
                                a.unlockedOn?.let { d ->
                                    span(classes = "ach-date") { +c.unlocked.format(LocalDate.parse(d).format(dateFormat)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private const val MEDAL_SVG =
        """<svg class="medal" viewBox="0 0 48 48" aria-hidden="true">""" +
            """<path class="medal-face" d="M24 3l18.2 10.5v21L24 45 5.8 34.5v-21z"/>""" +
            """<path class="medal-rim" d="M24 8.5l13.4 7.75v15.5L24 39.5l-13.4-7.75v-15.5z"/>""" +
            """<path class="medal-star" d="M24 15.5l2.6 5.3 5.8.85-4.2 4.1 1 5.8L24 28.8l-5.2 2.75 1-5.8-4.2-4.1 5.8-.85z"/>""" +
            "</svg>"

    suspend fun ApplicationCall.respondShareNotFound() = respondShareError(HttpStatusCode.NotFound)

    suspend fun ApplicationCall.respondShareGone() = respondShareError(HttpStatusCode.Gone)

    private suspend fun ApplicationCall.respondShareError(status: HttpStatusCode) {
        val c = chromeFor(request.acceptLanguageItems().firstOrNull()?.value)
        val rtl = c === AR
        val title = if (status == HttpStatusCode.Gone) c.goneTitle else c.notFoundTitle
        val body = if (status == HttpStatusCode.Gone) c.goneBody else c.notFoundBody
        response.header(HttpHeaders.CacheControl, "no-store")
        respondHtml(status) {
            attributes["lang"] = if (rtl) "ar" else "en"
            attributes["dir"] = if (rtl) "rtl" else "ltr"
            head {
                commonHead(pageTitle = "$title · PennyWise", noindex = true)
                meta { name = "color-scheme"; content = "dark light" }
                style { unsafe { +SharedComponents.commonStyles; +shareStyles } }
            }
            body(classes = "share-page theme-midnight") {
                main(classes = "share-main share-missing") {
                    img(src = "/static/logo.png", alt = "", classes = "share-logo-lg")
                    h1 { +title }
                    p(classes = "muted") { +body }
                    a(href = "/", classes = "btn btn-primary") { +c.home }
                }
            }
        }
    }

    private val shareStyles = """
        /* IBM Plex Sans Arabic, self-hosted like the other faces (static/fonts/LICENSE.md).
           unicode-range keeps it off the wire unless the page actually contains Arabic. */
        @font-face {
          font-family: 'IBM Plex Sans Arabic';
          src: url('/static/fonts/ibm-plex-sans-arabic-400-arabic.woff2') format('woff2');
          font-weight: 400; font-style: normal; font-display: swap;
          unicode-range: U+0600-06FF, U+0750-077F, U+0870-08FF, U+200C-200E, U+2010-2011, U+204F, U+2E41, U+FB50-FDFF, U+FE70-FEFC;
        }
        @font-face {
          font-family: 'IBM Plex Sans Arabic';
          src: url('/static/fonts/ibm-plex-sans-arabic-700-arabic.woff2') format('woff2');
          font-weight: 600 800; font-style: normal; font-display: swap;
          unicode-range: U+0600-06FF, U+0750-077F, U+0870-08FF, U+200C-200E, U+2010-2011, U+204F, U+2E41, U+FB50-FDFF, U+FE70-FEFC;
        }
        :root {
          color-scheme: dark light;
          --display: 'Archivo', 'IBM Plex Sans Arabic', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
          /* Arabic has no glyphs in the Latin-subset faces; route it to Plex Sans Arabic
             from every stack, mono included, instead of whatever the OS falls back to. */
          --mono: 'IBM Plex Mono', 'IBM Plex Sans Arabic', ui-monospace, SFMono-Regular, Menlo, monospace;
          --body: 'IBM Plex Sans Arabic', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
          --good: #5BD9A5;
          --bad: #FF8A7A;
          --shadow: 0 40px 90px -40px rgba(0,0,0,.85), 0 0 0 1px rgba(255,255,255,.04);
        }
        @media (prefers-color-scheme: light) {
          :root {
            --ink: #F4F5F7; --surface: #FFFFFF; --surface-2: #EEF1F4; --line: #DCE1E7; --line-soft: #E6E9EE;
            --text: #10151C; --muted: #5C6876; --good: #13845A; --bad: #C2412D;
            --shadow: 0 30px 70px -35px rgba(16,21,28,.45), 0 0 0 1px rgba(16,21,28,.05);
          }
        }

        /* Card palettes. Fixed, never Material You or the visitor's theme: the card has to
           look like the image that was in the chat. */
        .theme-midnight {
          --c-bg: radial-gradient(110% 80% at 100% 0%, rgba(64,110,190,.42) 0%, rgba(64,110,190,0) 58%), linear-gradient(180deg, #0F1626 0%, #090C14 100%);
          --c-text: #EEF3FA; --c-muted: #8E9BB2; --c-line: rgba(255,255,255,.09); --c-track: rgba(255,255,255,.09);
          --c-accent: #6FB2FF; --c-warm: #FFB74D; --c-good: #5BD9A5; --c-bad: #FF8A7A;
          --wash: rgba(111,178,255,.13);
        }
        .theme-aurora {
          --c-bg: radial-gradient(90% 70% at 100% 0%, rgba(46,230,184,.34), rgba(46,230,184,0) 62%), radial-gradient(85% 75% at 0% 100%, rgba(139,92,246,.42), rgba(139,92,246,0) 62%), linear-gradient(160deg, #0A1B20 0%, #120E26 100%);
          --c-text: #F1F7F5; --c-muted: #A0B4B1; --c-line: rgba(255,255,255,.10); --c-track: rgba(255,255,255,.10);
          --c-accent: #6EF0CC; --c-warm: #C9AEFF; --c-good: #6EF0CC; --c-bad: #FF9D8C;
          --wash: rgba(110,240,204,.11);
        }
        .theme-paper {
          --c-bg: radial-gradient(100% 70% at 100% 0%, rgba(214,160,72,.20), rgba(214,160,72,0) 60%), linear-gradient(180deg, #FBF8F1 0%, #F1EBDD 100%);
          --c-text: #1B1915; --c-muted: #6F685D; --c-line: rgba(27,25,21,.11); --c-track: rgba(27,25,21,.09);
          --c-accent: #2C5ACF; --c-warm: #B86E0B; --c-good: #1B8457; --c-bad: #C1432D;
          --wash: rgba(214,160,72,.12);
        }

        body.share-page { background: radial-gradient(900px 520px at 50% -120px, var(--wash), transparent 70%), var(--ink); min-height: 100vh; }
        html[dir=rtl] .share-page * { letter-spacing: normal !important; }
        .share-bar { max-width: 640px; margin: 0 auto; padding: 22px 20px 0; display: flex; align-items: center; justify-content: space-between; gap: 12px; }
        .share-brand { display: inline-flex; align-items: center; gap: 10px; color: var(--text); text-decoration: none; font-family: var(--display); font-weight: 700; font-size: 16px; letter-spacing: -.02em; }
        .share-logo { width: 28px; height: 28px; border-radius: 8px; }
        .share-main { max-width: 640px; margin: 0 auto; padding: 22px 20px 48px; }

        .share-card { position: relative; overflow: hidden; background: var(--c-bg); color: var(--c-text); border-radius: 28px; padding: 30px 30px 28px; box-shadow: var(--shadow); isolation: isolate; }
        .share-card::after { content: ""; position: absolute; inset: 0; border-radius: inherit; border: 1px solid var(--c-line); pointer-events: none; }
        .sc-eyebrow { margin-bottom: 10px; font-family: var(--mono); font-size: 11.5px; font-weight: 500; letter-spacing: .14em; text-transform: uppercase; color: var(--c-muted); }
        .sc-title { font-family: var(--display); font-size: clamp(24px, 5.2vw, 32px); font-weight: 800; line-height: 1.12; letter-spacing: -.03em; margin: 0; text-wrap: balance; }
        .sc-sub { color: var(--c-muted); font-size: 15.5px; line-height: 1.5; margin: 8px 0 0; max-width: 34em; }
        .sc-hero { margin-top: 26px; }
        .sc-hero-label { font-size: 14px; color: var(--c-muted); font-weight: 500; }
        .sc-hero-row { display: flex; align-items: baseline; flex-wrap: wrap; gap: 6px 14px; margin-top: 4px; }
        .sc-hero-value { font-family: var(--display); font-size: clamp(44px, 12vw, 68px); font-weight: 800; line-height: 1.02; letter-spacing: -.045em; font-variant-numeric: tabular-nums; unicode-bidi: isolate; }
        .sc-delta { display: inline-flex; align-items: center; padding: 5px 11px; border-radius: 999px; font-family: var(--mono); font-size: 13px; font-weight: 500; background: color-mix(in srgb, currentColor 14%, transparent); unicode-bidi: isolate; }
        .share-card .s-good { color: var(--c-good); }
        .share-card .s-bad { color: var(--c-bad); }
        .share-card .sc-delta.s-neutral { color: var(--c-muted); }

        .sc-chart { margin-top: 26px; }
        .plot-box { height: 150px; }
        .plot { width: 100%; height: 100%; display: block; overflow: visible; }
        .bar { fill: var(--c-accent); }
        .bar.dim { fill: var(--c-accent); opacity: .3; }
        .axis { stroke: var(--c-line); stroke-width: 1; }
        .line { fill: none; stroke: var(--c-accent); stroke-width: 2.5; stroke-linejoin: round; stroke-linecap: round; }
        .area { fill: url(#area); }
        .area-top { stop-color: var(--c-accent); stop-opacity: .32; }
        .area-bottom { stop-color: var(--c-accent); stop-opacity: 0; }
        .dot { stroke: var(--c-accent); stroke-width: 7; stroke-linecap: round; fill: none; }
        .dot.hl { stroke: var(--c-text); stroke-width: 12; }
        .ticks { display: grid; gap: 4px; margin-top: 10px; }
        .tick { display: flex; flex-direction: column; align-items: center; text-align: center; min-width: 0; }
        .tick-label { font-size: 11.5px; color: var(--c-muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 100%; }
        .tick-value { font-family: var(--mono); font-size: 11px; color: var(--c-muted); margin-top: 2px; white-space: nowrap; unicode-bidi: isolate; }
        .tick.hl .tick-label { color: var(--c-text); font-weight: 600; }
        .tick.hl .tick-value { color: var(--c-accent); font-weight: 500; font-size: 12px; }

        .ring-wrap { display: flex; align-items: center; gap: 22px; }
        .ring-box { position: relative; width: 148px; height: 148px; flex: none; }
        .ring { width: 100%; height: 100%; display: block; }
        .ring-track { fill: none; stroke: var(--c-track); stroke-width: 11; }
        .ring-arc { fill: none; stroke: var(--c-accent); stroke-width: 11; }
        .ring-value { position: absolute; inset: 0; display: grid; place-items: center; font-family: var(--display); font-weight: 800; font-size: 28px; letter-spacing: -.03em; unicode-bidi: isolate; }
        .ring-label { font-size: 15px; color: var(--c-muted); line-height: 1.45; }

        .div-rows { display: grid; gap: 12px; }
        .div-row { display: grid; grid-template-columns: minmax(0, 7fr) minmax(0, 9fr) minmax(0, 5fr); align-items: center; gap: 12px; }
        .div-label { font-size: 14px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        .div-bar svg { width: 100%; height: 16px; display: block; }
        .div-bar .pos { fill: var(--c-warm); }
        .div-bar .neg { fill: var(--c-accent); }
        .div-bar .dim { opacity: .35; }
        .div-value { font-family: var(--mono); font-size: 12.5px; color: var(--c-muted); text-align: end; white-space: nowrap; unicode-bidi: isolate; }
        .div-row.hl .div-label { font-weight: 600; }
        .div-row.hl .div-value { color: var(--c-text); }

        .sc-stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 18px 22px; margin-top: 28px; padding-top: 22px; border-top: 1px solid var(--c-line); }
        .sc-stat { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
        .sc-stat-label { font-size: 12.5px; color: var(--c-muted); }
        .sc-stat-value { font-family: var(--display); font-size: 21px; font-weight: 700; letter-spacing: -.02em; font-variant-numeric: tabular-nums; unicode-bidi: isolate; overflow-wrap: anywhere; }

        .share-section { margin-top: 36px; }
        .section-title { margin: 0 0 14px; }
        .ach-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(250px, 1fr)); gap: 10px; }
        .ach { display: flex; gap: 14px; align-items: flex-start; background: var(--surface); border: 1px solid var(--line-soft); border-radius: 18px; padding: 16px; }
        .medal { width: 46px; height: 46px; flex: none; }
        .medal-face { fill: var(--tier); }
        .medal-rim { fill: none; stroke: rgba(255,255,255,.45); stroke-width: 1.4; }
        .medal-star { fill: rgba(255,255,255,.92); }
        .tier-0 { --tier: #8593A4; } .tier-1 { --tier: #C0814F; } .tier-2 { --tier: #9AA6B4; }
        .tier-3 { --tier: #E9A93A; } .tier-4 { --tier: #34B98A; } .tier-5 { --tier: #8E6BF0; }
        .ach-title { font-family: var(--display); font-weight: 700; font-size: 16px; letter-spacing: -.01em; line-height: 1.25; }
        .ach-desc { color: var(--muted); font-size: 13.5px; line-height: 1.45; margin-top: 3px; }
        .ach-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 6px 12px; margin-top: 8px; }
        .pips { display: inline-flex; gap: 3px; }
        .pip { width: 6px; height: 6px; border-radius: 50%; background: var(--line); }
        .pip.on { background: var(--tier); }
        .ach-date { font-family: var(--mono); font-size: 11px; color: var(--muted); }

        .share-foot { margin-top: 40px; padding-top: 18px; border-top: 1px solid var(--line-soft); display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
        .share-made { display: inline-flex; align-items: center; gap: 8px; color: var(--muted); text-decoration: none; font-size: 13.5px; }
        .share-made:hover { color: var(--text); }
        .share-logo-sm { width: 20px; height: 20px; border-radius: 6px; }
        .share-foot form { margin: 0; }
        button.share-report { background: none; border: 0; padding: 4px 0; color: var(--muted); font-weight: 500; font-size: 13px; text-decoration: underline; text-underline-offset: 3px; }
        button.share-report:hover { color: var(--text); background: none; }

        .share-missing { text-align: center; padding-top: 18vh; }
        .share-missing h1 { font-size: clamp(24px, 5vw, 32px); margin: 22px 0 10px; }
        .share-missing p { margin: 0 auto 26px; max-width: 26em; }
        .share-logo-lg { width: 56px; height: 56px; border-radius: 14px; }

        @media (max-width: 640px) {
          .share-bar { padding: 16px 16px 0; }
          .share-main { padding: 16px 16px 36px; }
          .share-card { border-radius: 24px; padding: 24px 22px 22px; }
          .sc-stats { grid-template-columns: 1fr 1fr; gap: 16px 18px; margin-top: 24px; padding-top: 20px; }
          .sc-stat-value { font-size: 19px; }
          .plot-box { height: 128px; }
          .ring-box { width: 124px; height: 124px; }
          .ring-value { font-size: 24px; }
          .div-row { grid-template-columns: minmax(0, 6fr) minmax(0, 7fr) minmax(0, 5fr); gap: 10px; }
          .ach-grid { grid-template-columns: 1fr; }
        }
    """
}
