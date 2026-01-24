package com.example.ui

import com.example.feedback.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import kotlinx.html.*

object FeedbackViews {
    suspend fun ApplicationCall.respondFeedbackPage() {
        respondHtml(HttpStatusCode.OK) {
            head {
                SharedComponents.apply { commonHead("PennyWise AI - Feedback") }
                style {
                    unsafe {
                        +SharedComponents.commonStyles
                        +feedbackStyles
                    }
                }
            }
            body {
                SharedComponents.apply { siteHeader("feedback") }

                div(classes = "container") {
                    h1 { +"Submit Feedback" }
                    p(classes = "muted") { +"Report bugs or suggest new features to help improve PennyWise." }

                    div(classes = "feedback-layout") {
                        div(classes = "form-section") {
                            form {
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

                                button(type = ButtonType.submit) {
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
                    span(classes = "badge badge-${item.status.name.lowercase()}") {
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
            unsafe {
                +"""<svg width="16" height="16" viewBox="0 0 24 24" fill="${if (hasVoted) "currentColor" else "none"}" stroke="currentColor" stroke-width="2"><path d="M12 19V5M5 12l7-7 7 7"/></svg>"""
            }
            span { +voteCount.toString() }
        }
    }

    fun FlowContent.renderSubmitResult(success: Boolean, message: String) {
        div(classes = if (success) "alert alert-success" else "alert alert-error") {
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

    private val feedbackStyles = """
        .feedback-layout { display: grid; grid-template-columns: 1fr 1fr; gap: 32px; margin-top: 24px; }
        .form-section { }
        .list-section { }
        .toggle-group { margin-bottom: 16px; }
        .toggle-group > label { margin-bottom: 8px; }
        .toggle-buttons { display: flex; gap: 0; }
        .toggle-buttons input { display: none; }
        .toggle-buttons label { padding: 8px 16px; background: #1a1a1a; border: 1px solid #333; cursor: pointer; font-size: 14px; font-weight: 500; transition: all 0.2s; }
        .toggle-buttons label:first-of-type { border-radius: 6px 0 0 6px; }
        .toggle-buttons label:last-of-type { border-radius: 0 6px 6px 0; border-left: none; }
        .toggle-buttons input:checked + label { background: #fff; color: #000; border-color: #fff; }
        .filter-tabs { display: flex; gap: 8px; margin-bottom: 16px; }
        .filter-tab { background: transparent; border: 1px solid #333; color: #9ca3af; padding: 6px 14px; font-size: 13px; }
        .filter-tab:hover { color: #fff; border-color: #555; background: transparent; }
        .filter-tab.active { background: #fff; color: #000; border-color: #fff; }
        .feedback-card { background: #0a0a0a; border: 1px solid #222; border-radius: 10px; padding: 16px; margin-bottom: 12px; }
        .feedback-header { display: flex; gap: 8px; margin-bottom: 8px; flex-wrap: wrap; }
        .badge { padding: 3px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; text-transform: uppercase; }
        .badge-bug { background: #7f1d1d; color: #fca5a5; }
        .badge-feature { background: #1e3a8a; color: #93c5fd; }
        .badge-source { background: #1a1a1a; color: #9ca3af; }
        .badge-planned { background: #854d0e; color: #fcd34d; }
        .badge-in_progress { background: #1e40af; color: #93c5fd; }
        .badge-completed { background: #166534; color: #86efac; }
        .feedback-title { margin: 0 0 6px 0; font-size: 15px; font-weight: 600; }
        .feedback-desc { margin: 0 0 12px 0; color: #9ca3af; font-size: 13px; line-height: 1.5; }
        .feedback-footer { display: flex; justify-content: space-between; align-items: center; }
        .feedback-date { color: #6b7280; font-size: 12px; }
        .vote-btn { display: flex; align-items: center; gap: 6px; background: #1a1a1a; border: 1px solid #333; color: #9ca3af; padding: 6px 12px; font-size: 13px; border-radius: 6px; }
        .vote-btn:hover { border-color: #555; color: #fff; background: #1a1a1a; }
        .vote-btn.voted { background: #1e3a8a; border-color: #1e3a8a; color: #93c5fd; }
        .empty-state { text-align: center; padding: 32px; color: #6b7280; }
        .alert { padding: 12px 16px; border-radius: 8px; margin-top: 16px; font-size: 14px; }
        .alert-success { background: #065f46; border: 1px solid #10b981; color: #10b981; }
        .alert-error { background: #7f1d1d; border: 1px solid #ef4444; color: #fca5a5; }
        @media (max-width: 768px) {
            .feedback-layout { grid-template-columns: 1fr; }
            .list-section { order: -1; }
        }
    """
}
