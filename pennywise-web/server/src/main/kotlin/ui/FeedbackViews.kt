package com.example.ui

import com.example.feedback.*
import com.example.ui.SharedComponents.commonHead
import com.example.ui.SharedComponents.commonStyles
import com.example.ui.SharedComponents.siteFooter
import com.example.ui.SharedComponents.siteHeader
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import kotlinx.html.*

object FeedbackViews {
    suspend fun ApplicationCall.respondFeedbackPage() {
        respondHtml(HttpStatusCode.OK) {
            head {
                commonHead("PennyWise AI - Feedback", htmx = true)
                style {
                    unsafe {
                        +commonStyles
                        +chipStyles
                        +feedbackStyles
                    }
                }
            }
            body {
                siteHeader("feedback")

                div(classes = "container") {
                    div(classes = "page-head") {
                        h1 { +"Submit Feedback" }
                        p(classes = "lede") { +"Report bugs or suggest new features to help improve PennyWise." }
                    }

                    div(classes = "feedback-layout") {
                        div(classes = "form-section") {
                            form(classes = "form-card") {
                                attributes["hx-post"] = "/htmx/feedback"
                                attributes["hx-target"] = "#form-result"
                                attributes["hx-swap"] = "innerHTML"
                                attributes["hx-indicator"] = "#submit-indicator"

                                div(classes = "toggle-group") {
                                    label { +"Type" }
                                    div(classes = "toggle-buttons") {
                                        input(type = InputType.radio, name = "itemType") {
                                            id = "type-bug"; value = "BUG"; checked = true
                                        }
                                        label { htmlFor = "type-bug"; +"Bug Report" }
                                        input(type = InputType.radio, name = "itemType") {
                                            id = "type-feature"; value = "FEATURE"
                                        }
                                        label { htmlFor = "type-feature"; +"Feature Request" }
                                    }
                                }

                                div(classes = "toggle-group") {
                                    label { +"Source" }
                                    div(classes = "toggle-buttons") {
                                        input(type = InputType.radio, name = "source") {
                                            id = "source-android"; value = "ANDROID_APP"; checked = true
                                        }
                                        label { htmlFor = "source-android"; +"Android App" }
                                        input(type = InputType.radio, name = "source") {
                                            id = "source-web"; value = "WEB_TOOL"
                                        }
                                        label { htmlFor = "source-web"; +"Web Tool" }
                                    }
                                }

                                label { htmlFor = "title"; +"Title" }
                                input(type = InputType.text, name = "title") {
                                    id = "title"
                                    placeholder = "Brief description of the issue or feature"
                                    maxLength = "200"
                                    required = true
                                }

                                label { htmlFor = "description"; +"Description" }
                                textArea {
                                    id = "description"
                                    name = "description"
                                    placeholder = "Provide details about the bug or feature request..."
                                    required = true
                                }

                                button(type = ButtonType.submit, classes = "submit-btn") {
                                    +"Submit Feedback"
                                    span(classes = "spinner") { id = "submit-indicator" }
                                }
                            }

                            div { id = "form-result" }
                        }

                        div(classes = "list-section") {
                            div(classes = "filter-tabs") {
                                button(classes = "filter-tab active") {
                                    attributes["hx-get"] = "/htmx/feedback-list"
                                    attributes["hx-target"] = "#feedback-list"
                                    attributes["onclick"] = "setActiveTab(this)"
                                    +"All"
                                }
                                button(classes = "filter-tab") {
                                    attributes["hx-get"] = "/htmx/feedback-list?type=BUG"
                                    attributes["hx-target"] = "#feedback-list"
                                    attributes["onclick"] = "setActiveTab(this)"
                                    +"Bugs"
                                }
                                button(classes = "filter-tab") {
                                    attributes["hx-get"] = "/htmx/feedback-list?type=FEATURE"
                                    attributes["hx-target"] = "#feedback-list"
                                    attributes["onclick"] = "setActiveTab(this)"
                                    +"Features"
                                }
                            }

                            div {
                                id = "feedback-list"
                                attributes["hx-get"] = "/htmx/feedback-list"
                                attributes["hx-trigger"] = "load"
                                p(classes = "muted") { +"Loading..." }
                            }
                        }
                    }
                }

                script {
                    unsafe {
                        +"""
                        function setActiveTab(el) {
                            document.querySelectorAll('.filter-tab').forEach(t => t.classList.remove('active'));
                            el.classList.add('active');
                        }
                        """
                    }
                }

                siteFooter()
            }
        }
    }

    fun FlowContent.renderFeedbackList(items: List<FeedbackItem>) {
        if (items.isEmpty()) {
            div(classes = "empty-state") {
                p { +"No feedback items yet. Be the first to submit!" }
            }
            return
        }

        items.forEach { item ->
            renderFeedbackCard(item)
        }
    }

    fun FlowContent.renderFeedbackCard(item: FeedbackItem) {
        div(classes = "feedback-card") {
            div(classes = "feedback-header") {
                span(classes = "badge badge-${item.itemType.name.lowercase()}") {
                    +item.itemType.name.lowercase().replaceFirstChar { it.uppercase() }
                }
                span(classes = "badge badge-source") {
                    +if (item.source == FeedbackSource.ANDROID_APP) "Android" else "Web"
                }
                if (item.status != FeedbackStatus.PENDING) {
                    span(classes = "badge badge-status badge-${item.status.name.lowercase()}") {
                        +item.status.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                    }
                }
            }
            h3(classes = "feedback-title") { +item.title }
            p(classes = "feedback-desc") { +item.description.take(200) }
            div(classes = "feedback-footer") {
                renderVoteButton(item.id, item.voteCount, item.hasVoted)
                span(classes = "feedback-date") {
                    +formatDate(item.createdAt)
                }
            }
        }
    }

    fun FlowContent.renderVoteButton(itemId: String, voteCount: Int, hasVoted: Boolean) {
        button(classes = "vote-btn ${if (hasVoted) "voted" else ""}") {
            attributes["hx-post"] = "/htmx/vote/$itemId"
            attributes["hx-swap"] = "outerHTML"
            attributes["aria-pressed"] = hasVoted.toString()
            attributes["aria-label"] = "Upvote, $voteCount ${if (voteCount == 1) "vote" else "votes"}"
            unsafe {
                +"""<svg width="16" height="16" viewBox="0 0 24 24" fill="${if (hasVoted) "currentColor" else "none"}" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 19V5M5 12l7-7 7 7"/></svg>"""
            }
            span { +voteCount.toString() }
        }
    }

    fun FlowContent.renderSubmitResult(success: Boolean, message: String) {
        div(classes = if (success) "alert alert-success" else "alert alert-error") {
            attributes["role"] = if (success) "status" else "alert"
            +message
            if (success) {
                script {
                    unsafe {
                        +"""
                        document.querySelector('form').reset();
                        htmx.trigger('#feedback-list', 'load');
                        """
                    }
                }
            }
        }
    }

    private fun formatDate(dateStr: String): String {
        return try {
            val parts = dateStr.split(" ")
            if (parts.isNotEmpty()) parts[0] else dateStr
        } catch (e: Exception) {
            dateStr.take(10)
        }
    }

    /**
     * Chips and the vote toggle, shared by /feedback and /roadmap (the roadmap renders
     * the same vote button). Type/source are outlined assist chips; workflow status is a
     * filled, tinted pill so the two kinds never read as the same thing.
     */
    internal val chipStyles = """
        .badge { display: inline-flex; align-items: center; gap: 6px; height: 26px; padding: 0 10px; border-radius: var(--r-full); font-size: 12px; font-weight: 600; line-height: 1; white-space: nowrap; border: 1px solid var(--outline-variant); color: var(--on-variant); background: transparent; }
        .badge-bug { color: var(--error); border-color: color-mix(in srgb, var(--error) 45%, transparent); }
        .badge-feature { color: var(--primary); border-color: color-mix(in srgb, var(--primary) 45%, transparent); }
        .badge-status { border-color: transparent; }
        .badge-status::before { content: ""; width: 6px; height: 6px; border-radius: 50%; background: currentColor; flex: none; }
        .badge-planned { background: var(--secondary-container); color: var(--on-secondary-container); }
        .badge-in_progress { background: var(--primary-container); color: var(--on-primary-container); }
        .badge-completed { background: var(--success-container); color: var(--on-success-container); }
        .badge-rejected { background: var(--surface-highest); color: var(--on-variant); }

        /* Vote: an M3 toggle pill. Off = outlined, on = primary-container. */
        button.vote-btn { display: inline-flex; align-items: center; gap: 6px; height: 40px; padding: 0 16px; background: transparent; color: var(--on-variant); border: 1px solid var(--outline-variant); border-radius: var(--r-full); font-size: 14px; font-weight: 600; font-variant-numeric: tabular-nums; transition: background .15s, color .15s, border-radius .2s; }
        button.vote-btn:hover { background: var(--surface-high); color: var(--on-bg); filter: none; }
        button.vote-btn:active { border-radius: var(--r-sm); }
        button.vote-btn.voted { background: var(--primary-container); color: var(--on-primary-container); border-color: transparent; }
        button.vote-btn.voted:hover { background: var(--primary-container); filter: brightness(.97); }
        button.vote-btn svg { width: 16px; height: 16px; flex: none; }
        button.vote-btn.htmx-request { opacity: .6; }
    """

    private val feedbackStyles = """
        .page-head { max-width: 720px; margin: 8px 0 32px; }
        .page-head h1 { font-size: clamp(34px, 6vw, 52px); line-height: 1.06; margin: 0 0 14px; }
        .page-head .lede { margin: 0; }
        .feedback-layout { display: grid; grid-template-columns: minmax(0, 5fr) minmax(0, 7fr); gap: 32px; align-items: start; }

        /* --- Submit form: a tonal card with filled fields --- */
        .form-card { background: var(--surface-container); border-radius: var(--r-2xl); padding: 24px 24px 28px; }
        .form-card label { margin: 20px 0 8px; color: var(--on-bg); }
        .form-card .toggle-group:first-child > label { margin-top: 0; }
        .form-card input[type=text], .form-card textarea { background: var(--surface-lowest); border: 1px solid var(--outline-variant); }
        .form-card input[type=text]:hover, .form-card textarea:hover { border-color: var(--outline); }
        .form-card input[type=text]:focus, .form-card textarea:focus { background: var(--surface-lowest); border-color: var(--primary); box-shadow: inset 0 0 0 1px var(--primary); }
        .form-card textarea { font-family: var(--body); font-size: 15px; min-height: 148px; line-height: 1.5; }

        /* Connected button group: the selected segment morphs to a full pill. Radios stay
           focusable (visually hidden, not display:none) so the keyboard can reach them. */
        .toggle-buttons { display: flex; gap: 2px; }
        .toggle-buttons input { position: absolute; width: 1px; height: 1px; opacity: 0; pointer-events: none; }
        .toggle-buttons label { flex: 1 1 0; min-width: 0; display: inline-flex; align-items: center; justify-content: center; gap: 6px; height: 44px; margin: 0; padding: 0 14px; background: var(--surface-lowest); color: var(--on-variant); font-size: 14px; font-weight: 600; text-align: center; cursor: pointer; border-radius: var(--r-xs); transition: background .15s, color .15s, border-radius .2s; }
        .toggle-buttons label:first-of-type { border-radius: var(--r-full) var(--r-xs) var(--r-xs) var(--r-full); }
        .toggle-buttons label:last-of-type { border-radius: var(--r-xs) var(--r-full) var(--r-full) var(--r-xs); }
        .toggle-buttons label:hover { background: var(--surface-high); color: var(--on-bg); }
        .toggle-buttons input:checked + label { background: var(--primary); color: var(--on-primary); border-radius: var(--r-full); }
        .toggle-buttons input:checked + label::before { content: "✓"; font-weight: 700; }
        .toggle-buttons input:focus-visible + label { outline: 3px solid var(--primary); outline-offset: 2px; }

        button.submit-btn { display: inline-flex; align-items: center; justify-content: center; gap: 10px; margin-top: 24px; height: 56px; padding: 0 30px; font-size: 16px; }
        .submit-btn .spinner { margin: 0; border-color: color-mix(in srgb, var(--on-primary) 35%, transparent); border-top-color: var(--on-primary); }
        /* hx-indicator puts .htmx-request on the spinner itself, not on an ancestor. */
        .spinner.htmx-request { display: inline-block; }

        .alert { margin-top: 16px; padding: 14px 18px; border-radius: var(--r-lg); font-size: 15px; font-weight: 500; }
        .alert-success { background: var(--success-container); color: var(--on-success-container); }
        .alert-error { background: var(--error-container); color: var(--on-error-container); }

        /* --- List: filter chips + a segmented list of cards --- */
        .filter-tabs { display: flex; gap: 8px; flex-wrap: wrap; margin: 0 0 16px; }
        button.filter-tab { display: inline-flex; align-items: center; gap: 6px; height: 36px; padding: 0 16px; background: transparent; color: var(--on-variant); border: 1px solid var(--outline-variant); border-radius: var(--r-full); font-size: 14px; font-weight: 600; transition: background .15s, color .15s; }
        button.filter-tab:hover { background: var(--surface-high); color: var(--on-bg); filter: none; }
        button.filter-tab.active { background: var(--secondary-container); color: var(--on-secondary-container); border-color: transparent; }
        button.filter-tab.active::before { content: "✓"; font-weight: 700; }

        #feedback-list { display: flex; flex-direction: column; gap: 4px; }
        #feedback-list > .muted { margin: 0; padding: 18px 20px; }
        .feedback-card { background: var(--surface-container); border-radius: var(--r-xs); padding: 18px 20px; transition: border-radius .2s; }
        .feedback-card:first-child { border-top-left-radius: var(--r-lg); border-top-right-radius: var(--r-lg); }
        .feedback-card:last-child { border-bottom-left-radius: var(--r-lg); border-bottom-right-radius: var(--r-lg); }
        .feedback-header { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 12px; }
        .feedback-title { margin: 0 0 6px; font-size: 18px; font-weight: 700; line-height: 1.3; overflow-wrap: anywhere; }
        .feedback-desc { margin: 0 0 14px; color: var(--on-variant); font-size: 14px; line-height: 1.55; overflow-wrap: anywhere; }
        .feedback-footer { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
        .feedback-date { color: var(--on-variant); font-size: 13px; font-variant-numeric: tabular-nums; }
        .empty-state { text-align: center; padding: 40px 24px; color: var(--on-variant); background: var(--surface-container); border-radius: var(--r-lg); }
        .empty-state p { margin: 0; }

        @media (max-width: 860px) {
            .feedback-layout { grid-template-columns: 1fr; gap: 28px; }
            .list-section { order: -1; }
        }
        @media (max-width: 720px) {
            .page-head { margin-bottom: 24px; }
            .form-card { padding: 20px 16px 24px; border-radius: var(--r-xl); }
            button.submit-btn { width: 100%; }
            .feedback-card { padding: 16px; }
        }
    """
}
