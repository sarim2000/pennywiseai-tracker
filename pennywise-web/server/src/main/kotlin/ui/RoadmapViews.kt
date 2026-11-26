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

object RoadmapViews {
    suspend fun ApplicationCall.respondRoadmapPage(items: List<FeedbackItem>) {
        val planned = items.filter { it.status == FeedbackStatus.PLANNED }
        val inProgress = items.filter { it.status == FeedbackStatus.IN_PROGRESS }
        val completed = items.filter { it.status == FeedbackStatus.COMPLETED }

        respondHtml(HttpStatusCode.OK) {
            head {
                commonHead("PennyWise AI - Roadmap")
                style {
                    unsafe {
                        +commonStyles
                        +FeedbackViews.chipStyles
                        +roadmapStyles
                    }
                }
            }
            body {
                siteHeader("roadmap")

                div(classes = "container") {
                    div(classes = "page-head") {
                        h1 { +"Public Roadmap" }
                        p(classes = "lede") { +"Track the progress of features and bug fixes. Vote to help prioritize what gets built next." }
                    }

                    div(classes = "roadmap-grid") {
                        roadmapColumn("planned", "Planned", planned, "No planned items")
                        roadmapColumn("in-progress", "In Progress", inProgress, "Nothing in progress")
                        roadmapColumn("completed", "Completed", completed, "No completed items yet")
                    }

                    div(classes = "roadmap-cta") {
                        picture {
                            attributes["class"] = "roadmap-owl"
                            source {
                                attributes["srcset"] = "/static/owl-dark.svg"
                                attributes["media"] = "(prefers-color-scheme: dark)"
                            }
                            img(src = "/static/owl.svg", alt = "")
                        }
                        p { +"Have a suggestion? " }
                        a(href = "/feedback", classes = "btn btn-filled btn-lg") { +"Submit feedback" }
                    }
                }

                siteFooter()
            }
        }
    }

    private fun FlowContent.roadmapColumn(key: String, title: String, items: List<FeedbackItem>, emptyText: String) {
        section(classes = "roadmap-column") {
            div(classes = "column-header $key") {
                h2 { +title }
                span(classes = "count") { +items.size.toString() }
            }
            div(classes = "column-content") {
                if (items.isEmpty()) {
                    div(classes = "empty-column") { +emptyText }
                } else {
                    items.forEach { item -> renderRoadmapCard(item) }
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
        .page-head { max-width: 720px; margin: 8px 0 36px; }
        .page-head h1 { font-size: clamp(34px, 6vw, 52px); line-height: 1.06; margin: 0 0 14px; }
        .page-head .lede { margin: 0; }

        .roadmap-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 20px; align-items: start; }
        .roadmap-column { background: var(--surface-low); border-radius: var(--r-2xl); padding: 12px; }

        /* Column title is the status pill: planned/in progress/done = secondary/primary/success. */
        .column-header { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 4px 4px 14px; }
        .column-header h2 { display: inline-flex; align-items: center; gap: 8px; height: 40px; margin: 0; padding: 0 18px; border-radius: var(--r-full); font-size: 16px; font-weight: 700; letter-spacing: 0; color: inherit; }
        .column-header h2::before { content: ""; width: 8px; height: 8px; border-radius: 50%; background: currentColor; flex: none; }
        .column-header.planned h2 { background: var(--secondary-container); color: var(--on-secondary-container); }
        .column-header.in-progress h2 { background: var(--primary-container); color: var(--on-primary-container); }
        .column-header.completed h2 { background: var(--success-container); color: var(--on-success-container); }
        .column-header .count { min-width: 32px; height: 32px; padding: 0 10px; display: inline-grid; place-items: center; border-radius: var(--r-full); background: var(--surface-highest); color: var(--on-variant); font-size: 14px; font-weight: 700; font-variant-numeric: tabular-nums; }

        .column-content { display: flex; flex-direction: column; gap: 8px; }
        .empty-column { color: var(--on-variant); text-align: center; padding: 36px 16px; font-size: 14px; border: 1.5px dashed var(--outline-variant); border-radius: var(--r-lg); }
        .roadmap-card { background: var(--surface-lowest); border-radius: var(--r-lg); padding: 16px 18px; }
        .roadmap-card .card-header { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 10px; }
        .roadmap-card .card-title { margin: 0 0 14px; font-size: 16px; font-weight: 600; line-height: 1.35; overflow-wrap: anywhere; }
        .roadmap-card .card-footer { display: flex; justify-content: flex-start; }

        .roadmap-cta { margin-top: 48px; display: flex; align-items: center; justify-content: center; gap: 16px 20px; flex-wrap: wrap; padding: 28px 32px; border-radius: var(--r-2xl); background: var(--primary-container); color: var(--on-primary-container); text-align: center; }
        .roadmap-cta p { margin: 0; font-family: var(--display); font-size: 22px; font-weight: 700; }
        .roadmap-owl img { display: block; width: 64px; height: 64px; }

        @media (max-width: 960px) {
            .roadmap-grid { grid-template-columns: 1fr; gap: 16px; }
        }
        @media (max-width: 720px) {
            .page-head { margin-bottom: 24px; }
            .roadmap-column { padding: 10px; border-radius: var(--r-xl); }
            .roadmap-cta { flex-direction: column; padding: 24px 20px; margin-top: 36px; }
            .roadmap-cta .btn { width: 100%; }
        }
    """
}
