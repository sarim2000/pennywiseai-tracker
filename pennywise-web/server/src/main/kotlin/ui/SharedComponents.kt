package com.example.ui

import kotlinx.html.*

object SharedComponents {

    /** Absolute origin — needed for canonical/OG tags and the sitemap. */
    const val SITE_ORIGIN = "https://pennywise.zynth.dev"

    const val PLAY_STORE_URL =
        "https://play.google.com/store/apps/details?id=com.pennywiseai.tracker"

    /**
     * @param description meta description — the snippet Google shows under the result.
     *   Keep it under ~160 chars and make it answer the query the page targets.
     * @param canonicalPath absolute path (e.g. "/banks/hdfc-bank") for the canonical URL.
     * @param htmx load htmx from the CDN. Off by default: only the parser and feedback
     *   pages actually use it, and the landing pages shouldn't disclose a visitor's IP,
     *   User-Agent and Referer to a third party — nor block rendering on it — for a
     *   script they never call.
     */
    fun HEAD.commonHead(
        pageTitle: String,
        description: String? = null,
        canonicalPath: String? = null,
        htmx: Boolean = false,
    ) {
        title { +pageTitle }
        meta { charset = "utf-8" }
        meta { name = "viewport"; content = "width=device-width, initial-scale=1" }
        if (description != null) {
            meta { name = "description"; content = description }
        }
        if (canonicalPath != null) {
            link { rel = "canonical"; href = SITE_ORIGIN + canonicalPath }
            meta { attributes["property"] = "og:url"; content = SITE_ORIGIN + canonicalPath }
        }
        meta { attributes["property"] = "og:title"; content = pageTitle }
        if (description != null) {
            meta { attributes["property"] = "og:description"; content = description }
        }
        meta { attributes["property"] = "og:type"; content = "website" }
        meta { attributes["property"] = "og:image"; content = "$SITE_ORIGIN/static/logo.png" }
        meta { name = "twitter:card"; content = "summary" }
        meta { name = "theme-color"; content = "#0A0C10" }
        link { rel = "icon"; href = "/static/logo.png" }
        // Archivo carries the headings; IBM Plex Mono renders the SMS and the parsed
        // fields, which is where most of this design's personality lives. Body text stays
        // on the system stack deliberately — it costs nothing on a mobile connection and
        // falls back correctly across the scripts this audience actually reads in.
        //
        // Self-hosted, NOT loaded from Google Fonts. This is the marketing site for an app
        // whose entire claim is that nothing leaves your device; shipping a third-party
        // font request would disclose every visitor's IP, User-Agent and Referer to Google
        // before the page rendered. See static/fonts/LICENSE.md. It also removes a
        // render-blocking cross-origin round trip. Preload only the face used above the
        // fold (the h1) — the rest load normally.
        link {
            rel = "preload"; href = "/static/fonts/archivo-variable-latin.woff2"
            attributes["as"] = "font"
            attributes["type"] = "font/woff2"
            attributes["crossorigin"] = ""
        }
        if (htmx) script { src = "https://unpkg.com/htmx.org@1.9.12" }
    }

    fun FlowContent.siteHeader(currentPage: String = "") {
        div(classes = "header") {
            div(classes = "header-content") {
                div(classes = "logo-section") {
                    a(href = "/") {
                        img(src = "/static/logo.png", alt = "PennyWise AI", classes = "logo")
                        span(classes = "logo-text") { +"PennyWise AI" }
                    }
                }
                nav(classes = "nav-links") {
                    a(href = "/", classes = if (currentPage == "parse") "active" else "") {
                        +"Parser"
                    }
                    a(href = "/banks", classes = if (currentPage == "banks") "active" else "") {
                        +"Supported banks"
                    }
                    a(href = "https://github.com/sarim2000/pennywiseai-tracker", target = "_blank") {
                        unsafe {
                            +"""<svg fill="currentColor" viewBox="0 0 24 24" width="20" height="20"><path d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z"/></svg>"""
                        }
                    }
                    a(href = "https://discord.gg/H3xWeMWjKQ", target = "_blank") {
                        unsafe {
                            +"""<svg fill="currentColor" viewBox="0 0 24 24" width="20" height="20"><path d="M20.317 4.37a19.791 19.791 0 0 0-4.885-1.515a.074.074 0 0 0-.079.037c-.21.375-.444.864-.608 1.25a18.27 18.27 0 0 0-5.487 0a12.64 12.64 0 0 0-.617-1.25a.077.077 0 0 0-.079-.037A19.736 19.736 0 0 0 3.677 4.37a.07.07 0 0 0-.032.027C.533 9.046-.32 13.58.099 18.057a.082.082 0 0 0 .031.057a19.9 19.9 0 0 0 5.993 3.03a.078.078 0 0 0 .084-.028a14.09 14.09 0 0 0 1.226-1.994a.076.076 0 0 0-.041-.106a13.107 13.107 0 0 1-1.872-.892a.077.077 0 0 1-.008-.128a10.2 10.2 0 0 0 .372-.292a.074.074 0 0 1 .077-.01c3.928 1.793 8.18 1.793 12.062 0a.074.074 0 0 1 .078.01c.12.098.246.198.373.292a.077.077 0 0 1-.006.127a12.299 12.299 0 0 1-1.873.892a.077.077 0 0 0-.041.107c.36.698.772 1.362 1.225 1.993a.076.076 0 0 0 .084.028a19.839 19.839 0 0 0 6.002-3.03a.077.077 0 0 0 .032-.054c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 0 0-.031-.03zM8.02 15.33c-1.183 0-2.157-1.085-2.157-2.419c0-1.333.956-2.419 2.157-2.419c1.21 0 2.176 1.096 2.157 2.42c0 1.333-.956 2.418-2.157 2.418zm7.975 0c-1.183 0-2.157-1.085-2.157-2.419c0-1.333.955-2.419 2.157-2.419c1.21 0 2.176 1.096 2.157 2.42c0 1.333-.946 2.418-2.157 2.418z"/></svg>"""
                        }
                    }
                }
            }
        }
    }

    val commonStyles = """
        /* Self-hosted — see static/fonts/LICENSE.md for why and under what licence. */
        @font-face {
          font-family: 'Archivo';
          src: url('/static/fonts/archivo-variable-latin.woff2') format('woff2-variations');
          font-weight: 600 800;
          font-style: normal;
          font-display: swap;
        }
        @font-face {
          font-family: 'IBM Plex Mono';
          src: url('/static/fonts/ibm-plex-mono-400-latin.woff2') format('woff2');
          font-weight: 400;
          font-style: normal;
          font-display: swap;
        }
        @font-face {
          font-family: 'IBM Plex Mono';
          src: url('/static/fonts/ibm-plex-mono-500-latin.woff2') format('woff2');
          font-weight: 500;
          font-style: normal;
          font-display: swap;
        }
        :root {
          color-scheme: dark;
          /* Palette is lifted from the app's own Material theme (Color.kt): the dark-mode
             primary blue and secondary amber, plus a mint for money-in. Semantic, not
             decorative — every accent below means one specific thing about money. */
          --ink: #0A0C10;
          --surface: #11151B;
          --surface-2: #171D25;
          --line: #222A34;
          --line-soft: #1A2129;
          --text: #E9EEF4;
          --muted: #8593A4;
          --blue: #6FB2FF;
          --amber: #FFB74D;
          --mint: #5BD9A5;
          --violet: #B79BFF;
          --display: 'Archivo', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
          --body: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Helvetica Neue', Arial, sans-serif;
          --mono: 'IBM Plex Mono', ui-monospace, SFMono-Regular, Menlo, monospace;
        }
        * { box-sizing: border-box; }
        body { background: var(--ink); color: var(--text); font-family: var(--body); margin: 0; line-height: 1.55; -webkit-font-smoothing: antialiased; }
        ::selection { background: rgba(111,178,255,.28); }
        a:focus-visible, button:focus-visible, summary:focus-visible { outline: 2px solid var(--blue); outline-offset: 3px; border-radius: 4px; }
        .header { background: rgba(10,12,16,.86); backdrop-filter: saturate(140%) blur(10px); border-bottom: 1px solid var(--line-soft); padding: 14px 0; margin-bottom: 32px; position: sticky; top: 0; z-index: 50; }
        .header-content { max-width: 1100px; margin: 0 auto; padding: 0 24px; display: flex; align-items: center; justify-content: space-between; }
        .logo-section { display: flex; align-items: center; gap: 12px; }
        .logo-section a { display: flex; align-items: center; gap: 11px; text-decoration: none; color: inherit; }
        .logo { width: 32px; height: 32px; border-radius: 8px; }
        .logo-text { font-family: var(--display); font-size: 17px; font-weight: 700; letter-spacing: -.02em; }
        .nav-links { display: flex; gap: 20px; align-items: center; }
        .nav-links a { color: var(--muted); text-decoration: none; display: flex; align-items: center; gap: 6px; transition: color .15s; font-size: 14px; }
        .nav-links a:hover { color: var(--text); }
        .nav-links a.active { color: var(--text); font-weight: 600; }
        .nav-links svg { width: 19px; height: 19px; }
        .container { max-width: 1100px; margin: 0 auto; padding: 24px; }
        h1, h2, h3 { font-family: var(--display); letter-spacing: -.025em; }
        h1 { margin: 0 0 6px 0; font-size: 22px; font-weight: 800; }
        .muted { color: var(--muted); font-size: 14px; }
        label { display: block; font-weight: 600; margin: 12px 0 6px; color: var(--text); font-size: 14px; }
        input[type=text], input[type=email], textarea, select { width: 100%; padding: 12px; background: var(--surface); border: 1px solid var(--line); border-radius: 10px; font-size: 14px; color: var(--text); font-family: inherit; }
        input[type=text]::placeholder, textarea::placeholder { color: var(--muted); }
        input[type=text]:focus, textarea:focus, select:focus { outline: none; border-color: var(--blue); box-shadow: 0 0 0 3px rgba(111,178,255,.16); }
        textarea { min-height: 100px; resize: vertical; font-family: var(--mono); font-size: 13px; }
        button { padding: 10px 16px; background: var(--text); color: var(--ink); border: 1px solid var(--text); border-radius: 9px; cursor: pointer; font-weight: 700; font-size: 14px; font-family: inherit; transition: background .15s, color .15s; }
        button:hover { background: transparent; color: var(--text); }
        button:disabled { opacity: .6; cursor: not-allowed; }
        .card { background: var(--surface); border: 1px solid var(--line); border-radius: 12px; padding: 16px; margin-top: 16px; }
        .spinner { display: none; width: 16px; height: 16px; border: 2px solid var(--line); border-top-color: var(--text); border-radius: 50%; animation: spin 1s linear infinite; margin-left: 8px; vertical-align: middle; }
        .htmx-request .spinner { display: inline-block; }
        @keyframes spin { to { transform: rotate(360deg) } }
        /* --- content pages (supported banks / country landing) --- */
        .breadcrumb { font-family: var(--mono); font-size: 12px; color: var(--muted); margin-bottom: 22px; letter-spacing: .01em; }
        .breadcrumb a { color: var(--muted); text-decoration: none; }
        .breadcrumb a:hover { color: var(--text); }
        .prose { max-width: 680px; margin: 0 auto; }
        .prose h1 { font-size: clamp(30px, 5vw, 42px); font-weight: 800; line-height: 1.08; margin: 0 0 16px; }
        .prose h2 { font-size: 21px; font-weight: 700; margin: 52px 0 12px; }
        .prose h3 { font-size: 16px; margin: 22px 0 6px; }
        .prose p { color: #C4CDD8; margin: 12px 0; }
        .prose ul, .prose ol { color: #C4CDD8; padding-left: 20px; }
        .prose li { margin: 8px 0; }
        .prose a { color: var(--blue); text-underline-offset: 3px; }
        .lede { font-size: 18px; line-height: 1.6; color: var(--muted) !important; max-width: 34em; }
        /* Eyebrow labels name what the thing below actually is — they're wayfinding,
           not ornament, so they stay in the mono/data voice. */
        .eyebrow { font-family: var(--mono); font-size: 11px; font-weight: 500; letter-spacing: .12em; text-transform: uppercase; color: var(--muted); }
        .cta-row { display: flex; gap: 10px; flex-wrap: wrap; margin: 26px 0; }
        .btn { display: inline-block; padding: 12px 20px; border-radius: 10px; text-decoration: none; font-weight: 700; font-size: 14px; transition: transform .12s, background .15s, color .15s, border-color .15s; }
        /* Qualified with `a` so these beat `.prose a` on specificity — the unqualified
           class loses to it and renders the label in link-blue on a white button. */
        a.btn-primary { background: var(--text); color: var(--ink); border: 1px solid var(--text); }
        a.btn-primary:hover { background: var(--blue); border-color: var(--blue); color: var(--ink); }
        a.btn-secondary { background: transparent; color: var(--text); border: 1px solid var(--line); }
        a.btn-secondary:hover { border-color: var(--muted); }
        a.btn:active { transform: translateY(1px); }
        .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 8px; margin: 18px 0; }
        .grid a { display: block; padding: 11px 13px; background: var(--surface); border: 1px solid var(--line-soft); border-radius: 9px; color: #C4CDD8; text-decoration: none; font-size: 14px; transition: border-color .15s, color .15s, background .15s; }
        .grid a:hover { border-color: var(--blue); color: var(--text); background: var(--surface-2); }
        .country-head { display: flex; align-items: center; gap: 10px; margin: 44px 0 2px; flex-wrap: wrap; }
        .country-head h2 { margin: 0; }
        .country-head h2 a { color: var(--text); text-decoration: none; }
        .country-head h2 a:hover { color: var(--blue); }
        .pill { display: inline-block; padding: 3px 10px; border-radius: 999px; background: var(--surface); border: 1px solid var(--line); color: var(--muted); font-family: var(--mono); font-size: 11px; }
        .faq details { border-bottom: 1px solid var(--line-soft); padding: 14px 0; margin: 0; }
        .faq details summary { color: var(--text); font-weight: 600; font-size: 15px; cursor: pointer; list-style: none; display: flex; gap: 10px; align-items: baseline; }
        .faq details summary::-webkit-details-marker { display: none; }
        .faq details summary::before { content: "+"; color: var(--blue); font-family: var(--mono); font-weight: 500; }
        .faq details[open] summary::before { content: "−"; }
        .faq details p { margin: 10px 0 2px 20px; }
        .footnote { margin-top: 56px; padding-top: 22px; border-top: 1px solid var(--line-soft); color: var(--muted); font-size: 13px; }
        .footnote a { color: var(--text); }

        /* ---------------------------------------------------------------------
           The parse strip — this page's signature.
           A real, already-anonymised message from this bank sits on top with the
           exact substrings the parser consumed tinted in place; the fields it
           produced sit underneath in the same colours. The colour IS the mapping,
           which is why nothing here is decorative.
           --------------------------------------------------------------------- */
        .parse { margin: 30px 0 8px; }
        .parse .eyebrow { display: block; margin-bottom: 10px; }
        .sms { background: var(--surface); border: 1px solid var(--line); border-radius: 14px 14px 14px 4px; padding: 16px 18px; }
        .sms-from { display: flex; align-items: center; gap: 8px; font-family: var(--mono); font-size: 11px; color: var(--muted); margin-bottom: 10px; }
        .sms-from b { color: var(--text); font-weight: 500; letter-spacing: .04em; }
        .sms-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--mint); flex: none; }
        .sms-body { font-family: var(--mono); font-size: 13.5px; line-height: 1.75; color: #C9D3DE; word-break: break-word; }
        /* No horizontal padding: it would widen the highlighted run and visibly break the
           spacing of the surrounding sentence. The tint and underline carry the mapping. */
        .sms-body mark { background: transparent; padding: 0; font-weight: 500; }
        .f-amount   { color: var(--amber); box-shadow: inset 0 -1px 0 rgba(255,183,77,.45); }
        .f-merchant { color: var(--violet); box-shadow: inset 0 -1px 0 rgba(183,155,255,.45); }
        .f-account  { color: var(--blue); box-shadow: inset 0 -1px 0 rgba(111,178,255,.45); }
        .f-ref      { color: var(--mint); box-shadow: inset 0 -1px 0 rgba(91,217,165,.45); }
        .parse-arrow { display: flex; align-items: center; gap: 10px; margin: 0; padding: 12px 0 12px 22px; }
        .parse-arrow span { font-family: var(--mono); font-size: 11px; letter-spacing: .08em; text-transform: uppercase; color: var(--muted); }
        .parse-arrow i { display: block; width: 1px; height: 22px; background: linear-gradient(var(--line), var(--muted)); }
        .parsed { background: var(--surface-2); border: 1px solid var(--line); border-radius: 14px; padding: 16px 18px; }
        .parsed-top { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
        .parsed-amount { font-family: var(--display); font-size: 30px; font-weight: 800; letter-spacing: -.03em; }
        .parsed-amount.out { color: var(--amber); }
        .parsed-amount.in { color: var(--mint); }
        .tag { font-family: var(--mono); font-size: 11px; letter-spacing: .06em; text-transform: uppercase; padding: 4px 9px; border-radius: 999px; border: 1px solid var(--line); color: var(--muted); }
        .fields { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 12px 18px; margin-top: 14px; padding-top: 14px; border-top: 1px solid var(--line-soft); }
        .field-k { font-family: var(--mono); font-size: 10.5px; letter-spacing: .1em; text-transform: uppercase; color: var(--muted); }
        .field-v { font-family: var(--mono); font-size: 13.5px; margin-top: 3px; word-break: break-word; }
        .parse-note { font-size: 12.5px; color: var(--muted); margin-top: 12px; }
        .parse-note a { color: var(--muted); }

        @media (max-width: 640px) {
          .header { position: static; margin-bottom: 22px; }
          .header-content { flex-direction: column; gap: 14px; }
          .container { padding: 18px 16px 32px; }
          h1 { font-size: 18px; }
          .logo-text { font-size: 16px; }
          .nav-links { width: 100%; justify-content: center; flex-wrap: wrap; gap: 16px; }
          .grid { grid-template-columns: 1fr 1fr; }
          .prose h2 { margin: 40px 0 10px; }
          .parsed-amount { font-size: 26px; }
          .sms-body { font-size: 12.5px; }
          .cta-row a.btn { flex: 1 1 100%; text-align: center; }
        }
        @media (prefers-reduced-motion: reduce) {
          * { animation-duration: .01ms !important; transition-duration: .01ms !important; }
        }
    """
}
