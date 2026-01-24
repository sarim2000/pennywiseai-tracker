package com.example.ui

import kotlinx.html.*

object SharedComponents {
    fun HEAD.commonHead(pageTitle: String) {
        title { +pageTitle }
        meta { charset = "utf-8" }
        meta { name = "viewport"; content = "width=device-width, initial-scale=1" }
        link { rel = "icon"; href = "/static/logo.png" }
        script { src = "https://unpkg.com/htmx.org@1.9.12" }
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
                    a(href = "/feedback", classes = if (currentPage == "feedback") "active" else "") {
                        +"Feedback"
                    }
                    a(href = "/roadmap", classes = if (currentPage == "roadmap") "active" else "") {
                        +"Roadmap"
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
        :root { color-scheme: dark; }
        * { box-sizing: border-box; }
        body { background: #000; color: #fff; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Fira Sans', 'Droid Sans', 'Helvetica Neue', Arial, sans-serif; margin: 0; line-height: 1.5; }
        .header { background: #0a0a0a; border-bottom: 1px solid #222; padding: 16px 0; margin-bottom: 24px; }
        .header-content { max-width: 1100px; margin: 0 auto; padding: 0 24px; display: flex; align-items: center; justify-content: space-between; }
        .logo-section { display: flex; align-items: center; gap: 12px; }
        .logo-section a { display: flex; align-items: center; gap: 12px; text-decoration: none; color: inherit; }
        .logo { width: 40px; height: 40px; border-radius: 8px; }
        .logo-text { font-size: 20px; font-weight: 600; }
        .nav-links { display: flex; gap: 20px; align-items: center; }
        .nav-links a { color: #9ca3af; text-decoration: none; display: flex; align-items: center; gap: 6px; transition: color 0.2s; font-size: 14px; }
        .nav-links a:hover { color: #fff; }
        .nav-links a.active { color: #fff; font-weight: 500; }
        .nav-links svg { width: 20px; height: 20px; }
        .container { max-width: 1100px; margin: 0 auto; padding: 24px; }
        h1 { margin: 0 0 6px 0; font-size: 22px; }
        .muted { color: #9ca3af; font-size: 14px; }
        label { display: block; font-weight: 600; margin: 12px 0 6px; color: #fff; font-size: 14px; }
        input[type=text], input[type=email], textarea, select { width: 100%; padding: 12px; background: #0a0a0a; border: 1px solid #333; border-radius: 8px; font-size: 14px; color: #fff; }
        input[type=text]::placeholder, textarea::placeholder { color: #9ca3af; }
        input[type=text]:focus, textarea:focus, select:focus { outline: none; border-color: #555; box-shadow: 0 0 0 3px rgba(255,255,255,0.06); }
        textarea { min-height: 100px; resize: vertical; }
        button { padding: 10px 16px; background: #fff; color: #000; border: 1px solid #fff; border-radius: 8px; cursor: pointer; font-weight: 600; font-size: 14px; }
        button:hover { background: #000; color: #fff; }
        button:disabled { opacity: .6; cursor: not-allowed; }
        .card { background: #0a0a0a; border: 1px solid #222; border-radius: 10px; padding: 16px; margin-top: 16px; }
        .spinner { display: none; width: 16px; height: 16px; border: 2px solid #333; border-top-color: #fff; border-radius: 50%; animation: spin 1s linear infinite; margin-left: 8px; vertical-align: middle; }
        .htmx-request .spinner { display: inline-block; }
        @keyframes spin { to { transform: rotate(360deg) } }
        @media (max-width: 640px) {
          .header-content { flex-direction: column; gap: 16px; }
          .container { padding: 16px; }
          h1 { font-size: 18px; }
          .logo-text { font-size: 18px; }
          .nav-links { width: 100%; justify-content: center; flex-wrap: wrap; }
        }
    """
}
