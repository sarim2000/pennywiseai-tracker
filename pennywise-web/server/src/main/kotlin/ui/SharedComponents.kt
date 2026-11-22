package com.example.ui

import kotlinx.html.*

object SharedComponents {

    /** Absolute origin — needed for canonical/OG tags and the sitemap. */
    const val SITE_ORIGIN = "https://pennywise.zynth.dev"

    const val PLAY_STORE_URL =
        "https://play.google.com/store/apps/details?id=com.pennywiseai.tracker"
    const val APP_STORE_URL = "https://apps.apple.com/app/id6793073167"
    const val FDROID_URL = "https://f-droid.org/packages/com.pennywiseai.tracker/"
    const val RELEASES_URL = "https://github.com/sarim2000/pennywiseai-tracker/releases"
    const val GITHUB_URL = "https://github.com/sarim2000/pennywiseai-tracker"
    const val DISCORD_URL = "https://discord.gg/H3xWeMWjKQ"

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
        meta { name = "color-scheme"; content = "light dark" }
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
        // Matches --bg in each scheme so the browser chrome blends into the page.
        meta { name = "theme-color"; content = "#F9F9FF"; attributes["media"] = "(prefers-color-scheme: light)" }
        meta { name = "theme-color"; content = "#111318"; attributes["media"] = "(prefers-color-scheme: dark)" }
        link { rel = "icon"; href = "/static/logo.png" }
        // Google Sans Flex is the app's own typeface since the Material 3 Expressive
        // redesign, so the site speaks in the same voice. IBM Plex Mono stays for SMS
        // text and parsed fields — the "data" voice.
        //
        // Self-hosted, NOT loaded from Google Fonts. This is the marketing site for an app
        // whose entire claim is that nothing leaves your device; shipping a third-party
        // font request would disclose every visitor's IP, User-Agent and Referer to Google
        // before the page rendered. See static/fonts/LICENSE.md. Preload only the face used
        // above the fold — the mono faces load normally.
        link {
            rel = "preload"; href = "/static/fonts/google-sans-flex-latin.woff2"
            attributes["as"] = "font"
            attributes["type"] = "font/woff2"
            attributes["crossorigin"] = ""
        }
        if (htmx) script { src = "https://unpkg.com/htmx.org@1.9.12" }
    }

    fun FlowContent.siteHeader(currentPage: String = "") {
        header(classes = "header") {
            div(classes = "header-content") {
                a(href = "/", classes = "brand") {
                    img(src = "/static/logo.png", alt = "", classes = "logo")
                    span(classes = "logo-text") { +"PennyWise" }
                }
                nav(classes = "nav-links") {
                    attributes["aria-label"] = "Main"
                    fun item(href: String, key: String, label: String) {
                        a(href = href, classes = if (currentPage == key) "active" else null) {
                            if (currentPage == key) attributes["aria-current"] = "page"
                            +label
                        }
                    }
                    item("/tools/parse", "parse", "SMS parser")
                    item("/banks", "banks", "Banks")
                    item("/guide", "guide", "Guide")
                    item("/roadmap", "roadmap", "Roadmap")
                    a(href = "/pro", classes = if (currentPage == "pro") "nav-pro active" else "nav-pro") {
                        if (currentPage == "pro") attributes["aria-current"] = "page"
                        +"Get Pro"
                    }
                    a(href = GITHUB_URL, classes = "nav-icon", target = "_blank") {
                        rel = "noopener"
                        attributes["aria-label"] = "PennyWise on GitHub"
                        unsafe { +GITHUB_SVG }
                    }
                    a(href = DISCORD_URL, classes = "nav-icon", target = "_blank") {
                        rel = "noopener"
                        attributes["aria-label"] = "PennyWise Discord"
                        unsafe { +DISCORD_SVG }
                    }
                }
                a(href = "/#download", classes = "btn btn-filled header-cta") { +"Get the app" }
            }
        }
    }

    /** The three places to install from, as Expressive pill buttons. */
    fun FlowContent.downloadButtons() {
        div(classes = "download-row") {
            a(href = PLAY_STORE_URL, classes = "btn btn-filled btn-lg", target = "_blank") {
                rel = "noopener"
                unsafe { +PLAY_SVG }
                span { +"Google Play" }
            }
            a(href = APP_STORE_URL, classes = "btn btn-tonal btn-lg", target = "_blank") {
                rel = "noopener"
                unsafe { +APPLE_SVG }
                span { +"App Store" }
            }
            a(href = FDROID_URL, classes = "btn btn-tonal btn-lg", target = "_blank") {
                rel = "noopener"
                +"F-Droid"
            }
            a(href = RELEASES_URL, classes = "btn btn-outlined btn-lg", target = "_blank") {
                rel = "noopener"
                +"APK on GitHub"
            }
        }
    }

    fun FlowContent.siteFooter() {
        footer(classes = "site-footer") {
            div(classes = "footer-inner") {
                div(classes = "footer-brand") {
                    a(href = "/", classes = "brand") {
                        img(src = "/static/logo.png", alt = "", classes = "logo")
                        span(classes = "logo-text") { +"PennyWise" }
                    }
                    p { +"Private, on-device expense tracking from your bank SMS. Open source." }
                }
                footerCol("Get the app", listOf("Google Play" to PLAY_STORE_URL, "App Store" to APP_STORE_URL, "F-Droid" to FDROID_URL, "GitHub releases" to RELEASES_URL))
                footerCol("Learn", listOf("Guide" to "/guide", "Supported banks" to "/banks", "SMS parser" to "/tools/parse", "Roadmap" to "/roadmap"))
                footerCol("Community", listOf("GitHub" to GITHUB_URL, "Discord" to DISCORD_URL, "Feedback" to "/feedback"))
                footerCol("PennyWise Pro", listOf("Get Pro" to "/pro", "Terms" to "/terms"))
            }
        }
    }

    private fun FlowContent.footerCol(title: String, links: List<Pair<String, String>>) {
        div(classes = "footer-col") {
            h2 { +title }
            ul {
                links.forEach { (label, href) ->
                    li {
                        a(href = href) {
                            if (href.startsWith("http")) { target = "_blank"; rel = "noopener" }
                            +label
                        }
                    }
                }
            }
        }
    }

    /**
     * Play can't sell Pro right now and the app isn't allowed to link here, so the
     * landing page has to make the offer unmissable: prices up front, one tap to /pro.
     */
    fun FlowContent.proBanner() {
        a(href = "/pro", classes = "pro-banner") {
            span(classes = "pro-banner-title") { +"PennyWise Pro" }
            span(classes = "pro-banner-prices") { +if (ProViews.offerActive()) "Diwali: 25% off Annual and Lifetime until 10 Nov" else "₹2,000 lifetime · ₹699 / year · ₹149 / month" }
            span(classes = "pro-banner-cta") { +"Get Pro →" }
        }
    }

    private const val GITHUB_SVG = """<svg fill="currentColor" viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z"/></svg>"""

    private const val DISCORD_SVG = """<svg fill="currentColor" viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path d="M20.317 4.37a19.791 19.791 0 0 0-4.885-1.515a.074.074 0 0 0-.079.037c-.21.375-.444.864-.608 1.25a18.27 18.27 0 0 0-5.487 0a12.64 12.64 0 0 0-.617-1.25a.077.077 0 0 0-.079-.037A19.736 19.736 0 0 0 3.677 4.37a.07.07 0 0 0-.032.027C.533 9.046-.32 13.58.099 18.057a.082.082 0 0 0 .031.057a19.9 19.9 0 0 0 5.993 3.03a.078.078 0 0 0 .084-.028a14.09 14.09 0 0 0 1.226-1.994a.076.076 0 0 0-.041-.106a13.107 13.107 0 0 1-1.872-.892a.077.077 0 0 1-.008-.128a10.2 10.2 0 0 0 .372-.292a.074.074 0 0 1 .077-.01c3.928 1.793 8.18 1.793 12.062 0a.074.074 0 0 1 .078.01c.12.098.246.198.373.292a.077.077 0 0 1-.006.127a12.299 12.299 0 0 1-1.873.892a.077.077 0 0 0-.041.107c.36.698.772 1.362 1.225 1.993a.076.076 0 0 0 .084.028a19.839 19.839 0 0 0 6.002-3.03a.077.077 0 0 0 .032-.054c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 0 0-.031-.03zM8.02 15.33c-1.183 0-2.157-1.085-2.157-2.419c0-1.333.956-2.419 2.157-2.419c1.21 0 2.176 1.096 2.157 2.42c0 1.333-.956 2.418-2.157 2.418zm7.975 0c-1.183 0-2.157-1.085-2.157-2.419c0-1.333.955-2.419 2.157-2.419c1.21 0 2.176 1.096 2.157 2.42c0 1.333-.946 2.418-2.157 2.418z"/></svg>"""

    private const val PLAY_SVG = """<svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor" aria-hidden="true"><path d="M4.6 2.3a1.5 1.5 0 0 0-.6 1.2v17a1.5 1.5 0 0 0 .6 1.2l9.3-9.7zM15.3 13.4l2.6 2.7-11.3 6.4zM15.3 10.6 6.6 1.5l11.3 6.4zM19.2 8.6l2.4 1.4a1.6 1.6 0 0 1 0 2.8l-2.4 1.4-2.9-3z"/></svg>"""

    private const val APPLE_SVG = """<svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor" aria-hidden="true"><path d="M16.37 12.6c-.02-2.2 1.8-3.26 1.88-3.31-1.03-1.5-2.62-1.7-3.18-1.73-1.35-.14-2.64.8-3.33.8-.69 0-1.74-.78-2.87-.76-1.47.02-2.83.86-3.59 2.18-1.53 2.66-.39 6.59 1.1 8.75.73 1.05 1.6 2.24 2.73 2.2 1.1-.05 1.51-.71 2.84-.71 1.32 0 1.7.71 2.86.69 1.18-.02 1.93-1.07 2.65-2.13.84-1.22 1.18-2.4 1.2-2.46-.03-.01-2.3-.88-2.32-3.5zM14.2 6.13c.6-.73 1-1.74.9-2.75-.87.04-1.92.58-2.54 1.3-.56.64-1.05 1.67-.92 2.66.97.08 1.96-.49 2.56-1.21z"/></svg>"""

    /**
     * Design tokens mirror the app's Material 3 Expressive theme (indigo-blue seed,
     * tonal containers, scalloped "cookie/clover" shapes, big rounded corners) in both
     * light and dark — the site follows the visitor's system setting, like the app.
     */
    val commonStyles = """
        /* Self-hosted — see static/fonts/LICENSE.md for why and under what licence. */
        @font-face {
          font-family: 'Google Sans Flex';
          src: url('/static/fonts/google-sans-flex-latin.woff2') format('woff2');
          font-weight: 400 800;
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
          color-scheme: light dark;
          /* Material 3 colour roles (light). Use these names in page CSS — never raw hex. */
          --bg: #F9F9FF;
          --on-bg: #1A1B20;
          --on-variant: #45464F;
          --surface-lowest: #FFFFFF;
          /* Segmented-list rows: white on light (like the app), a lifted container on dark. */
          --surface-row: #FFFFFF;
          --surface-low: #F3F3FA;
          --surface-container: #EDEDF4;
          --surface-high: #E7E8EE;
          --surface-highest: #E2E2E9;
          --outline: #757780;
          --outline-variant: #C5C6D0;
          --primary: #475D92;
          --on-primary: #FFFFFF;
          --primary-container: #D9E2FF;
          --on-primary-container: #2F4578;
          --secondary-container: #DCE2F9;
          --on-secondary-container: #3E4759;
          --tertiary-container: #FDD7FA;
          --on-tertiary-container: #5A3D5A;
          --success-container: #B9F0E2;
          --on-success-container: #00201B;
          --success: #006B5E;
          --error: #BA1A1A;
          --error-container: #FFDAD6;
          --on-error-container: #93000A;
          --inverse-surface: #2F3036;
          --inverse-on-surface: #F0F0F7;
          /* Parse-strip field colours: each means one specific thing about money. */
          --f-amount: #8B5000;
          --f-merchant: #6B4FA0;
          --f-account: #2F5BB5;
          --f-ref: #006B5E;

          --display: 'Google Sans Flex', 'Google Sans', system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
          --body: 'Google Sans Flex', 'Google Sans', system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
          --mono: 'IBM Plex Mono', ui-monospace, SFMono-Regular, Menlo, monospace;

          /* Shape scale (M3 Expressive). */
          --r-xs: 8px; --r-sm: 12px; --r-md: 16px; --r-lg: 24px; --r-xl: 28px; --r-2xl: 36px; --r-full: 999px;
          --shape-cookie: url("data:image/svg+xml,%3Csvg xmlns=%27http://www.w3.org/2000/svg%27 viewBox=%270 0 100 100%27%3E%3Cpath d=%27M50.0 0.0L53.4 0.9L56.6 3.2L59.3 6.2L61.8 8.9L64.4 10.5L67.4 10.9L71.0 10.5L75.0 9.9L78.9 10.2L82.1 11.7L84.2 14.6L85.1 18.4L85.3 22.4L85.5 26.1L86.4 29.0L88.4 31.3L91.5 33.2L94.9 35.4L97.8 38.1L99.2 41.3L99.0 44.9L97.2 48.4L94.7 51.6L92.5 54.5L91.4 57.3L91.5 60.3L92.6 63.8L93.8 67.7L94.3 71.6L93.3 75.0L90.8 77.5L87.2 79.1L83.3 80.0L79.7 80.8L77.0 82.2L75.1 84.6L73.7 88.0L72.2 91.7L70.0 95.0L67.1 97.0L63.6 97.3L59.8 96.2L56.2 94.3L53.0 92.7L50.0 92.0L47.0 92.7L43.8 94.3L40.2 96.2L36.4 97.3L32.9 97.0L30.0 95.0L27.8 91.7L26.3 88.0L24.9 84.6L23.0 82.2L20.3 80.8L16.7 80.0L12.8 79.1L9.2 77.5L6.7 75.0L5.7 71.6L6.2 67.7L7.4 63.8L8.5 60.3L8.6 57.3L7.5 54.5L5.3 51.6L2.8 48.4L1.0 44.9L0.8 41.3L2.2 38.1L5.1 35.4L8.5 33.2L11.6 31.3L13.6 29.0L14.5 26.1L14.7 22.4L14.9 18.4L15.8 14.6L17.9 11.7L21.1 10.2L25.0 9.9L29.0 10.5L32.6 10.9L35.6 10.5L38.2 8.9L40.7 6.2L43.4 3.2L46.6 0.9Z%27/%3E%3C/svg%3E");
          --shape-clover: url("data:image/svg+xml,%3Csvg xmlns=%27http://www.w3.org/2000/svg%27 viewBox=%270 0 100 100%27%3E%3Cpath d=%27M50.0 0.0L53.5 0.5L56.8 1.8L59.8 4.0L62.4 6.8L64.6 10.0L66.3 13.4L67.7 16.8L68.8 19.9L69.8 22.7L70.9 25.1L72.2 27.0L73.9 28.5L76.0 29.7L78.6 30.7L81.6 31.8L84.9 33.0L88.3 34.5L91.6 36.5L94.7 38.9L97.2 41.7L99.0 44.9L99.9 48.3L99.9 51.7L99.0 55.1L97.2 58.3L94.7 61.1L91.6 63.5L88.3 65.5L84.9 67.0L81.6 68.3L78.6 69.3L76.0 70.3L73.9 71.5L72.2 73.0L70.9 74.9L69.8 77.3L68.8 80.1L67.7 83.2L66.3 86.6L64.6 90.0L62.4 93.2L59.8 96.0L56.8 98.2L53.5 99.5L50.0 100.0L46.5 99.5L43.2 98.2L40.2 96.0L37.6 93.2L35.4 90.0L33.7 86.6L32.3 83.2L31.2 80.1L30.2 77.3L29.1 74.9L27.8 73.0L26.1 71.5L24.0 70.3L21.4 69.3L18.4 68.3L15.1 67.0L11.7 65.5L8.4 63.5L5.3 61.1L2.8 58.3L1.0 55.1L0.1 51.7L0.1 48.3L1.0 44.9L2.8 41.7L5.3 38.9L8.4 36.5L11.7 34.5L15.1 33.0L18.4 31.7L21.4 30.7L24.0 29.7L26.1 28.5L27.8 27.0L29.1 25.1L30.2 22.7L31.2 19.9L32.3 16.8L33.7 13.4L35.4 10.0L37.6 6.8L40.2 4.0L43.2 1.8L46.5 0.5Z%27/%3E%3C/svg%3E");
          --shape-sunny: url("data:image/svg+xml,%3Csvg xmlns=%27http://www.w3.org/2000/svg%27 viewBox=%270 0 100 100%27%3E%3Cpath d=%27M50.0 0.0L53.5 0.6L56.7 2.2L59.7 4.3L62.4 6.6L65.1 8.5L67.9 9.7L71.1 10.4L74.5 10.8L78.2 11.2L81.7 12.2L84.7 14.1L87.0 16.7L88.4 20.0L89.1 23.7L89.4 27.3L89.9 30.6L90.8 33.5L92.4 36.2L94.5 38.9L96.8 41.7L98.7 44.9L99.9 48.3L99.9 51.7L98.7 55.1L96.8 58.3L94.5 61.1L92.4 63.8L90.8 66.5L89.9 69.4L89.4 72.7L89.1 76.3L88.4 80.0L87.0 83.3L84.7 85.9L81.7 87.8L78.2 88.8L74.5 89.2L71.1 89.6L67.9 90.3L65.1 91.5L62.4 93.4L59.7 95.7L56.7 97.8L53.5 99.4L50.0 100.0L46.5 99.4L43.3 97.8L40.3 95.7L37.6 93.4L34.9 91.5L32.1 90.3L28.9 89.6L25.5 89.2L21.8 88.8L18.3 87.8L15.3 85.9L13.0 83.3L11.6 80.0L10.9 76.3L10.6 72.8L10.1 69.4L9.2 66.5L7.6 63.8L5.5 61.1L3.2 58.3L1.3 55.1L0.1 51.7L0.1 48.3L1.3 44.9L3.2 41.7L5.5 38.9L7.6 36.2L9.2 33.5L10.1 30.6L10.6 27.2L10.9 23.7L11.6 20.0L13.0 16.7L15.3 14.1L18.3 12.2L21.8 11.2L25.5 10.8L28.9 10.4L32.1 9.7L34.9 8.5L37.6 6.6L40.3 4.3L43.3 2.2L46.5 0.6Z%27/%3E%3C/svg%3E");
          --shape-burst: url("data:image/svg+xml,%3Csvg xmlns=%27http://www.w3.org/2000/svg%27 viewBox=%270 0 100 100%27%3E%3Cpath d=%27M50.0 0.0L53.4 1.1L56.5 3.8L59.3 6.4L62.1 7.6L65.6 7.2L69.5 6.2L73.4 6.1L76.4 7.8L78.2 11.2L79.2 15.1L80.6 18.3L83.1 20.2L86.8 21.3L90.6 22.6L93.3 25.0L94.0 28.5L93.3 32.5L92.4 36.2L92.8 39.3L94.8 42.1L97.7 45.0L99.7 48.3L99.7 51.7L97.7 55.0L94.8 57.9L92.8 60.7L92.4 63.8L93.3 67.5L94.0 71.5L93.3 75.0L90.6 77.4L86.8 78.7L83.1 79.8L80.6 81.7L79.2 84.9L78.2 88.8L76.4 92.2L73.4 93.9L69.5 93.8L65.6 92.8L62.1 92.4L59.3 93.6L56.5 96.2L53.4 98.9L50.0 100.0L46.6 98.9L43.5 96.2L40.7 93.6L37.9 92.4L34.4 92.8L30.5 93.8L26.6 93.9L23.6 92.2L21.8 88.8L20.8 84.9L19.4 81.7L16.9 79.8L13.2 78.7L9.4 77.4L6.7 75.0L6.0 71.5L6.7 67.5L7.6 63.8L7.2 60.7L5.2 57.9L2.3 55.0L0.3 51.7L0.3 48.3L2.3 45.0L5.2 42.1L7.2 39.3L7.6 36.2L6.7 32.5L6.0 28.5L6.7 25.0L9.4 22.6L13.2 21.3L16.9 20.2L19.4 18.3L20.8 15.1L21.8 11.2L23.6 7.8L26.6 6.1L30.5 6.2L34.4 7.2L37.9 7.6L40.7 6.4L43.5 3.8L46.6 1.1Z%27/%3E%3C/svg%3E");
        }
        @media (prefers-color-scheme: dark) {
          :root {
            --bg: #111318;
            --on-bg: #E2E2E9;
            --on-variant: #C5C6D0;
            --surface-lowest: #0C0E13;
            --surface-row: #1E1F25;
            --surface-low: #1A1B21;
            --surface-container: #1E1F25;
            --surface-high: #282A2F;
            --surface-highest: #33353A;
            --outline: #8F9099;
            --outline-variant: #45464F;
            --primary: #B0C6FF;
            --on-primary: #152E60;
            --primary-container: #2F4578;
            --on-primary-container: #D9E2FF;
            --secondary-container: #3E4759;
            --on-secondary-container: #DCE2F9;
            --tertiary-container: #5A3D5A;
            --on-tertiary-container: #FDD7FA;
            --success-container: #00504A;
            --on-success-container: #B9F0E2;
            --success: #82D5C6;
            --error: #FFB4AB;
            --error-container: #93000A;
            --on-error-container: #FFDAD6;
            --inverse-surface: #E2E2E9;
            --inverse-on-surface: #2F3036;
            --f-amount: #FFB95C;
            --f-merchant: #D0BCFF;
            --f-account: #B0C6FF;
            --f-ref: #82D5C6;
          }
        }
        * { box-sizing: border-box; }
        html { -webkit-text-size-adjust: 100%; }
        body { background: var(--bg); color: var(--on-bg); font-family: var(--body); margin: 0; line-height: 1.55; -webkit-font-smoothing: antialiased; font-size: 16px; }
        ::selection { background: var(--primary-container); color: var(--on-primary-container); }
        a:focus-visible, button:focus-visible, summary:focus-visible, input:focus-visible, textarea:focus-visible, select:focus-visible { outline: 3px solid var(--primary); outline-offset: 3px; border-radius: 6px; }
        img { max-width: 100%; }

        /* --- Shapes: scalloped Expressive badges, as CSS masks --- */
        .shape-cookie, .shape-clover, .shape-sunny, .shape-burst { -webkit-mask-size: 100% 100%; mask-size: 100% 100%; border-radius: 0 !important; }
        .shape-cookie { -webkit-mask-image: var(--shape-cookie); mask-image: var(--shape-cookie); }
        .shape-clover { -webkit-mask-image: var(--shape-clover); mask-image: var(--shape-clover); }
        .shape-sunny  { -webkit-mask-image: var(--shape-sunny);  mask-image: var(--shape-sunny); }
        .shape-burst  { -webkit-mask-image: var(--shape-burst);  mask-image: var(--shape-burst); }

        /* --- Header --- */
        .header { background: color-mix(in srgb, var(--bg) 86%, transparent); backdrop-filter: saturate(160%) blur(14px); -webkit-backdrop-filter: saturate(160%) blur(14px); padding: 12px 0; margin-bottom: 24px; position: sticky; top: 0; z-index: 50; }
        .header-content { max-width: 1160px; margin: 0 auto; padding: 0 24px; display: flex; align-items: center; gap: 16px; }
        .brand { display: flex; align-items: center; gap: 10px; text-decoration: none; color: var(--on-bg); flex: none; }
        .logo { width: 36px; height: 36px; border-radius: 12px; }
        .logo-text { font-family: var(--display); font-size: 20px; font-weight: 700; letter-spacing: -.01em; }
        .nav-links { display: flex; gap: 4px; align-items: center; margin-left: auto; }
        .nav-links a { color: var(--on-variant); text-decoration: none; display: inline-flex; align-items: center; gap: 6px; height: 40px; padding: 0 14px; border-radius: var(--r-full); font-size: 15px; font-weight: 500; transition: background .15s, color .15s; white-space: nowrap; }
        .nav-links a:hover { background: var(--surface-high); color: var(--on-bg); }
        .nav-links a.active { background: var(--secondary-container); color: var(--on-secondary-container); font-weight: 600; }
        .nav-links a.nav-icon { width: 40px; padding: 0; justify-content: center; }
        .nav-links a.nav-pro { background: var(--tertiary-container); color: var(--on-tertiary-container); font-weight: 600; }
        .nav-links a.nav-pro:hover { filter: brightness(.97); }
        .nav-links svg { width: 20px; height: 20px; }
        .header-cta { flex: none; }

        /* --- Buttons (M3 Expressive: pill, 40–56px tall) --- */
        .btn { display: inline-flex; align-items: center; justify-content: center; gap: 8px; height: 44px; padding: 0 22px; border-radius: var(--r-full); text-decoration: none; font-family: var(--body); font-weight: 600; font-size: 15px; border: 1px solid transparent; cursor: pointer; transition: border-radius .2s, filter .15s, background .15s; }
        .btn:active { border-radius: var(--r-sm); }
        .btn-lg { height: 56px; padding: 0 28px; font-size: 16px; }
        .btn svg { width: 20px; height: 20px; flex: none; }
        a.btn-filled, button.btn-filled, a.btn-primary { background: var(--primary); color: var(--on-primary); }
        a.btn-tonal, button.btn-tonal { background: var(--secondary-container); color: var(--on-secondary-container); }
        a.btn-outlined, button.btn-outlined, a.btn-secondary { background: transparent; color: var(--on-bg); border-color: var(--outline-variant); }
        a.btn:hover, button.btn:hover { filter: brightness(1.06); }
        a.btn-outlined:hover, a.btn-secondary:hover { background: var(--surface-high); filter: none; }
        .download-row { display: flex; flex-wrap: wrap; gap: 10px; }

        /* --- Pro banner --- */
        .pro-banner { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; margin: 0 0 24px; padding: 16px 20px; border-radius: var(--r-xl); text-decoration: none; background: var(--tertiary-container); color: var(--on-tertiary-container); transition: border-radius .2s; }
        .pro-banner:hover { border-radius: var(--r-lg); }
        .pro-banner-title { font-family: var(--display); font-weight: 800; font-size: 18px; }
        .pro-banner-prices { font-size: 14px; opacity: .9; flex: 1; font-variant-numeric: tabular-nums; }
        .pro-banner-cta { font-weight: 700; font-size: 15px; white-space: nowrap; }

        /* --- Layout & type --- */
        .container { max-width: 1160px; margin: 0 auto; padding: 8px 24px 48px; }
        h1, h2, h3 { font-family: var(--display); letter-spacing: -.015em; color: var(--on-bg); }
        h1 { margin: 0 0 8px; font-size: clamp(30px, 5vw, 44px); font-weight: 800; line-height: 1.1; }
        .muted { color: var(--on-variant); font-size: 15px; }
        label { display: block; font-weight: 600; margin: 14px 0 6px; color: var(--on-bg); font-size: 14px; }
        input[type=text], input[type=email], input[type=number], textarea, select { width: 100%; padding: 14px 16px; background: var(--surface-container); border: 1px solid transparent; border-radius: var(--r-md); font-size: 15px; color: var(--on-bg); font-family: inherit; transition: border-color .15s, background .15s; }
        input::placeholder, textarea::placeholder { color: var(--on-variant); opacity: .8; }
        input[type=text]:focus, input[type=email]:focus, input[type=number]:focus, textarea:focus, select:focus { outline: none; border-color: var(--primary); background: var(--surface-low); }
        textarea { min-height: 120px; resize: vertical; font-family: var(--mono); font-size: 13.5px; }
        button { height: 44px; padding: 0 22px; background: var(--primary); color: var(--on-primary); border: none; border-radius: var(--r-full); cursor: pointer; font-weight: 600; font-size: 15px; font-family: inherit; transition: border-radius .2s, filter .15s; }
        button:hover { filter: brightness(1.08); }
        button:active { border-radius: var(--r-sm); }
        button:disabled { opacity: .5; cursor: not-allowed; }
        .card { background: var(--surface-low); border-radius: var(--r-xl); padding: 20px 22px; margin-top: 16px; }
        .spinner { display: none; width: 18px; height: 18px; border: 3px solid var(--primary-container); border-top-color: var(--primary); border-radius: 50%; animation: spin 1s linear infinite; margin-left: 10px; vertical-align: middle; }
        .htmx-request .spinner, .spinner.htmx-request { display: inline-block; }
        @keyframes spin { to { transform: rotate(360deg) } }

        /* --- Content pages (banks / country / guide prose) --- */
        .breadcrumb { font-size: 13px; color: var(--on-variant); margin-bottom: 20px; }
        .breadcrumb a { color: var(--on-variant); text-decoration: none; }
        .breadcrumb a:hover { color: var(--primary); }
        .prose { max-width: 720px; margin: 0 auto; }
        .prose h1 { font-size: clamp(34px, 6vw, 52px); font-weight: 800; line-height: 1.06; margin: 0 0 18px; }
        .prose h2 { font-size: 24px; font-weight: 700; margin: 52px 0 12px; }
        .prose h3 { font-size: 18px; font-weight: 600; margin: 24px 0 6px; }
        .prose p, .prose ul, .prose ol { color: var(--on-variant); margin: 12px 0; }
        .prose ul, .prose ol { padding-left: 22px; }
        .prose li { margin: 8px 0; }
        .prose a { color: var(--primary); text-underline-offset: 3px; }
        .lede { font-size: 19px; line-height: 1.55; color: var(--on-variant) !important; max-width: 34em; }
        .eyebrow { font-size: 14px; font-weight: 500; color: var(--on-variant); }
        .cta-row { display: flex; gap: 10px; flex-wrap: wrap; margin: 28px 0; }
        .prose a.btn { text-decoration: none; }
        .prose a.btn-filled, .prose a.btn-primary { color: var(--on-primary); }
        .prose a.btn-tonal { color: var(--on-secondary-container); }
        .prose a.btn-outlined, .prose a.btn-secondary { color: var(--on-bg); }
        /* Segmented list: big outer corners, small inner ones, 2px gaps — the app's GroupedList. */
        .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); gap: 4px; margin: 18px 0; }
        .grid a { display: block; padding: 14px 16px; background: var(--surface-row); border-radius: var(--r-xs); color: var(--on-bg); text-decoration: none; font-size: 15px; font-weight: 500; transition: background .15s, border-radius .2s; }
        .grid a:hover { background: var(--secondary-container); color: var(--on-secondary-container); border-radius: var(--r-lg); }
        .country-head { display: flex; align-items: center; gap: 10px; margin: 44px 0 2px; flex-wrap: wrap; }
        .country-head h2 { margin: 0; }
        .country-head h2 a { color: var(--on-bg); text-decoration: none; }
        .country-head h2 a:hover { color: var(--primary); }
        .pill { display: inline-flex; align-items: center; height: 28px; padding: 0 12px; border-radius: var(--r-full); background: var(--secondary-container); color: var(--on-secondary-container); font-size: 13px; font-weight: 600; }
        .faq details { background: var(--surface-row); border-radius: var(--r-xs); padding: 16px 20px; margin: 0 0 4px; }
        .faq details:first-of-type { border-top-left-radius: var(--r-lg); border-top-right-radius: var(--r-lg); }
        .faq details:last-of-type { border-bottom-left-radius: var(--r-lg); border-bottom-right-radius: var(--r-lg); }
        .faq details summary { color: var(--on-bg); font-weight: 600; font-size: 16px; cursor: pointer; list-style: none; display: flex; justify-content: space-between; gap: 12px; align-items: center; }
        .faq details summary::-webkit-details-marker { display: none; }
        .faq details summary::after { content: "+"; flex: none; width: 32px; height: 32px; border-radius: 50%; display: grid; place-items: center; background: var(--surface-container); color: var(--on-variant); font-size: 20px; font-weight: 500; transition: transform .2s; }
        .faq details[open] summary::after { transform: rotate(45deg); }
        .faq details p { margin: 10px 0 2px; }
        .path { font-size: 14px; font-weight: 600; color: var(--on-secondary-container); background: var(--secondary-container); border-radius: var(--r-xs); padding: 2px 8px; }
        .footnote { margin-top: 56px; padding-top: 22px; border-top: 1px solid var(--outline-variant); color: var(--on-variant); font-size: 14px; }
        .footnote a { color: var(--primary); }

        /* ---------------------------------------------------------------------
           The parse strip: a real, anonymised SMS with the exact substrings the
           parser consumed tinted in place; the fields it produced sit underneath in
           the same colours. The colour IS the mapping.
           --------------------------------------------------------------------- */
        .parse { margin: 30px 0 8px; }
        .parse .eyebrow { display: block; margin-bottom: 10px; }
        .sms { background: var(--surface-container); border-radius: var(--r-lg) var(--r-lg) var(--r-lg) var(--r-xs); padding: 18px 20px; }
        .sms-from { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--on-variant); margin-bottom: 10px; }
        .sms-from b { color: var(--on-bg); font-weight: 600; }
        .sms-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--success); flex: none; }
        .sms-body { font-family: var(--mono); font-size: 13.5px; line-height: 1.75; color: var(--on-bg); word-break: break-word; }
        .sms-body mark { background: transparent; padding: 0; font-weight: 500; }
        .f-amount   { color: var(--f-amount);   box-shadow: inset 0 -2px 0 color-mix(in srgb, var(--f-amount) 45%, transparent); }
        .f-merchant { color: var(--f-merchant); box-shadow: inset 0 -2px 0 color-mix(in srgb, var(--f-merchant) 45%, transparent); }
        .f-account  { color: var(--f-account);  box-shadow: inset 0 -2px 0 color-mix(in srgb, var(--f-account) 45%, transparent); }
        .f-ref      { color: var(--f-ref);      box-shadow: inset 0 -2px 0 color-mix(in srgb, var(--f-ref) 45%, transparent); }
        .parse-arrow { display: flex; align-items: center; gap: 10px; margin: 0; padding: 10px 0 10px 24px; }
        .parse-arrow span { font-size: 13px; font-weight: 500; color: var(--on-variant); }
        .parse-arrow i { display: block; width: 2px; height: 22px; border-radius: 2px; background: var(--outline-variant); }
        .parsed { background: var(--primary-container); color: var(--on-primary-container); border-radius: var(--r-xl); padding: 20px 22px; }
        .parsed-top { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
        .parsed-amount { font-family: var(--display); font-size: 40px; font-weight: 800; letter-spacing: -.02em; font-variant-numeric: tabular-nums; }
        .tag { font-size: 13px; font-weight: 600; padding: 4px 12px; border-radius: var(--r-full); background: color-mix(in srgb, var(--surface-lowest) 60%, transparent); color: inherit; }
        .fields { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 12px 18px; margin-top: 16px; padding-top: 16px; border-top: 1px solid color-mix(in srgb, currentColor 18%, transparent); }
        .field-k { font-size: 12px; font-weight: 500; opacity: .8; }
        .field-v { font-family: var(--mono); font-size: 13.5px; margin-top: 2px; word-break: break-word; }
        .parse-note { font-size: 13px; color: var(--on-variant); margin-top: 12px; }
        .parse-note a { color: var(--primary); }

        /* --- Footer --- */
        .site-footer { margin-top: 72px; background: var(--surface-low); border-radius: var(--r-2xl) var(--r-2xl) 0 0; }
        .footer-inner { max-width: 1160px; margin: 0 auto; padding: 44px 24px 56px; display: grid; grid-template-columns: 1.6fr repeat(4, 1fr); gap: 28px; }
        .footer-brand p { color: var(--on-variant); font-size: 14px; max-width: 26em; margin: 14px 0 0; }
        .footer-col h2 { font-size: 14px; font-weight: 600; margin: 6px 0 10px; color: var(--on-bg); letter-spacing: 0; }
        .footer-col ul { list-style: none; margin: 0; padding: 0; }
        .footer-col li { margin: 8px 0; }
        .footer-col a { color: var(--on-variant); text-decoration: none; font-size: 14px; }
        .footer-col a:hover { color: var(--primary); text-decoration: underline; text-underline-offset: 3px; }

        @media (max-width: 960px) {
          .header-cta { display: none; }
          .footer-inner { grid-template-columns: 1fr 1fr; }
          .footer-brand { grid-column: 1 / -1; }
        }
        @media (max-width: 720px) {
          .header { padding: 10px 0 8px; margin-bottom: 16px; }
          .header-content { flex-wrap: wrap; gap: 8px; padding: 0 16px; }
          /* Nav becomes a horizontally scrollable chip row under the brand. */
          .nav-links { order: 3; width: 100%; margin: 0 -16px; padding: 0 16px; overflow-x: auto; scrollbar-width: none; }
          .nav-links::-webkit-scrollbar { display: none; }
          .nav-links a { height: 36px; padding: 0 12px; font-size: 14px; }
          .nav-links a.nav-icon { width: 36px; }
          .container { padding: 4px 16px 32px; }
          .grid { grid-template-columns: 1fr 1fr; }
          .grid a { font-size: 14px; padding: 12px 14px; }
          .prose h2 { margin: 40px 0 10px; }
          .parsed-amount { font-size: 32px; }
          .sms-body { font-size: 12.5px; }
          .cta-row .btn, .download-row .btn { flex: 1 1 100%; }
          .pro-banner { gap: 6px; }
          .pro-banner-prices { flex-basis: 100%; }
          .footer-inner { padding: 36px 16px 44px; }
        }
        @media (prefers-reduced-motion: reduce) {
          * { animation-duration: .01ms !important; transition-duration: .01ms !important; }
        }
    """
}
