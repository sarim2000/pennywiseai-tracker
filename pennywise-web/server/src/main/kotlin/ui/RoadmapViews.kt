package com.example.ui

import com.example.feedback.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import kotlinx.html.*

object RoadmapViews {
    suspend fun ApplicationCall.respondRoadmapPage(items: List<FeedbackItem>) {
        val planned = items.filter { it.status == FeedbackStatus.PLANNED }
        val inProgress = items.filter { it.status == FeedbackStatus.IN_PROGRESS }
        val completed = items.filter { it.status == FeedbackStatus.COMPLETED }

        respondHtml(HttpStatusCode.OK) {
            head {
                SharedComponents.apply { commonHead("PennyWise AI - Roadmap") }
                style {
                    unsafe {
                        +SharedComponents.commonStyles
                        +roadmapStyles
                    }
                }
            }
            body {
                SharedComponents.apply { siteHeader("roadmap") }

                div(classes = "container") {
                    h1 { +"Public Roadmap" }
                    p(classes = "muted") { +"Track the progress of features and bug fixes. Vote to help prioritize what gets built next." }

                    div(classes = "roadmap-grid") {
                        div(classes = "roadmap-column") {
                            div(classes = "column-header planned") {
                                h2 { +"Planned" }
                                span(classes = "count") { +planned.size.toString() }
                            }
                            div(classes = "column-content") {
                                if (planned.isEmpty()) {
                                    div(classes = "empty-column") { +"No planned items" }
                                } else {
                                    planned.forEach { item -> renderRoadmapCard(item) }
                                }
                            }
                        }

                        div(classes = "roadmap-column") {
                            div(classes = "column-header in-progress") {
                                h2 { +"In Progress" }
                                span(classes = "count") { +inProgress.size.toString() }
                            }
                            div(classes = "column-content") {
                                if (inProgress.isEmpty()) {
                                    div(classes = "empty-column") { +"Nothing in progress" }
                                } else {
                                    inProgress.forEach { item -> renderRoadmapCard(item) }
                                }
                            }
                        }

                        div(classes = "roadmap-column") {
                            div(classes = "column-header completed") {
                                h2 { +"Completed" }
                                span(classes = "count") { +completed.size.toString() }
                            }
                            div(classes = "column-content") {
                                if (completed.isEmpty()) {
                                    div(classes = "empty-column") { +"No completed items yet" }
                                } else {
                                    completed.forEach { item -> renderRoadmapCard(item) }
                                }
                            }
                        }
                    }

                    div(classes = "roadmap-cta") {
                        p { +"Have a suggestion? " }
                        a(href = "/feedback") { +"Submit feedback" }
                    }
                }
            }
        }
    }

    private fun FlowContent.renderRoadmapCard(item: FeedbackItem) {
        div(classes = "roadmap-card") {
            div(classes = "card-header") {
                span(classes = "badge badge-${item.itemType.name.lowercase()}") {
                    +if (item.itemType == FeedbackItemType.BUG) "Bug" else "Feature"
                }
                span(classes = "badge badge-source") {
                    +if (item.source == FeedbackSource.ANDROID_APP) "Android" else "Web"
                }
            }
            h3(classes = "card-title") { +item.title }
            div(classes = "card-footer") {
                FeedbackViews.apply { renderVoteButton(item.id, item.voteCount, item.hasVoted) }
            }
        }
    }

    private val roadmapStyles = """
        .roadmap-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 24px; margin-top: 32px; }
        .roadmap-column { background: #0a0a0a; border: 1px solid #222; border-radius: 12px; overflow: hidden; }
        .column-header { padding: 16px 20px; border-bottom: 1px solid #222; display: flex; justify-content: space-between; align-items: center; }
        .column-header h2 { margin: 0; font-size: 16px; font-weight: 600; }
        .column-header .count { background: #1a1a1a; padding: 2px 8px; border-radius: 10px; font-size: 12px; color: #9ca3af; }
        .column-header.planned { border-top: 3px solid #eab308; }
        .column-header.in-progress { border-top: 3px solid #3b82f6; }
        .column-header.completed { border-top: 3px solid #22c55e; }
        .column-content { padding: 16px; min-height: 200px; }
        .empty-column { color: #6b7280; text-align: center; padding: 32px 16px; font-size: 14px; }
        .roadmap-card { background: #111; border: 1px solid #333; border-radius: 8px; padding: 14px; margin-bottom: 12px; }
        .roadmap-card .card-header { display: flex; gap: 6px; margin-bottom: 8px; }
        .roadmap-card .card-title { margin: 0 0 12px 0; font-size: 14px; font-weight: 500; line-height: 1.4; }
        .roadmap-card .card-footer { display: flex; justify-content: flex-start; }
        .badge { padding: 3px 8px; border-radius: 4px; font-size: 10px; font-weight: 600; text-transform: uppercase; }
        .badge-bug { background: #7f1d1d; color: #fca5a5; }
        .badge-feature { background: #1e3a8a; color: #93c5fd; }
        .badge-source { background: #1a1a1a; color: #9ca3af; }
        .vote-btn { display: flex; align-items: center; gap: 6px; background: #1a1a1a; border: 1px solid #333; color: #9ca3af; padding: 6px 12px; font-size: 13px; border-radius: 6px; }
        .vote-btn:hover { border-color: #555; color: #fff; background: #1a1a1a; }
        .vote-btn.voted { background: #1e3a8a; border-color: #1e3a8a; color: #93c5fd; }
        .roadmap-cta { margin-top: 32px; text-align: center; color: #9ca3af; }
        .roadmap-cta a { color: #3b82f6; text-decoration: none; }
        .roadmap-cta a:hover { text-decoration: underline; }
        @media (max-width: 900px) {
            .roadmap-grid { grid-template-columns: 1fr; }
            .column-content { min-height: auto; }
        }
    """
}
