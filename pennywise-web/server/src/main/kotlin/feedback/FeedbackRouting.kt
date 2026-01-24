package com.example.feedback

import com.example.ui.FeedbackViews
import com.example.ui.FeedbackViews.respondFeedbackPage
import com.example.ui.RoadmapViews.respondRoadmapPage
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*

fun Application.configureFeedback(feedbackService: FeedbackService) {
    routing {
        // Feedback page
        get("/feedback") {
            setFingerprintCookie(call)
            call.respondFeedbackPage()
        }

        // Roadmap page
        get("/roadmap") {
            setFingerprintCookie(call)
            val fingerprint = FingerprintService.getFingerprint(call)
            val items = feedbackService.getFeedbackItems(fingerprint, forRoadmap = true)
            call.respondRoadmapPage(items)
        }

        // HTMX: Submit feedback
        post("/htmx/feedback") {
            setFingerprintCookie(call)
            val fingerprint = FingerprintService.getFingerprint(call)
            val params = call.receiveParameters()

            val request = CreateFeedbackRequest(
                itemType = params["itemType"] ?: "BUG",
                source = params["source"] ?: "ANDROID_APP",
                title = params["title"]?.trim() ?: "",
                description = params["description"]?.trim() ?: ""
            )

            val result = feedbackService.createFeedback(request, fingerprint)

            call.respondHtml(HttpStatusCode.OK) {
                body {
                    result.fold(
                        onSuccess = {
                            FeedbackViews.apply {
                                renderSubmitResult(true, "Thanks for your feedback! Your submission has been recorded.")
                            }
                        },
                        onFailure = { e ->
                            FeedbackViews.apply {
                                renderSubmitResult(false, e.message ?: "Failed to submit feedback")
                            }
                        }
                    )
                }
            }
        }

        // HTMX: Vote toggle
        post("/htmx/vote/{id}") {
            setFingerprintCookie(call)
            val fingerprint = FingerprintService.getFingerprint(call)
            val feedbackId = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)

            val result = feedbackService.toggleVote(feedbackId, fingerprint)

            call.respondHtml(HttpStatusCode.OK) {
                body {
                    FeedbackViews.apply {
                        renderVoteButton(feedbackId, result.newVoteCount, result.hasVoted)
                    }
                }
            }
        }

        // HTMX: Feedback list with filters
        get("/htmx/feedback-list") {
            setFingerprintCookie(call)
            val fingerprint = FingerprintService.getFingerprint(call)
            val typeFilter = call.request.queryParameters["type"]?.let {
                try { FeedbackItemType.valueOf(it.uppercase()) } catch (e: Exception) { null }
            }

            val items = feedbackService.getFeedbackItems(fingerprint, typeFilter = typeFilter)

            call.respondHtml(HttpStatusCode.OK) {
                body {
                    FeedbackViews.apply {
                        renderFeedbackList(items)
                    }
                }
            }
        }

        // JSON API: Get feedback items
        get("/api/feedback") {
            setFingerprintCookie(call)
            val fingerprint = FingerprintService.getFingerprint(call)
            val typeFilter = call.request.queryParameters["type"]?.let {
                try { FeedbackItemType.valueOf(it.uppercase()) } catch (e: Exception) { null }
            }
            val forRoadmap = call.request.queryParameters["roadmap"] == "true"

            val items = feedbackService.getFeedbackItems(fingerprint, typeFilter = typeFilter, forRoadmap = forRoadmap)
            call.respond(FeedbackListResponse(items = items, total = items.size))
        }

        // JSON API: Submit feedback
        post("/api/feedback") {
            setFingerprintCookie(call)
            val fingerprint = FingerprintService.getFingerprint(call)
            val request = call.receive<CreateFeedbackRequest>()

            val result = feedbackService.createFeedback(request, fingerprint)

            result.fold(
                onSuccess = { id ->
                    call.respond(HttpStatusCode.Created, FeedbackResponse(
                        success = true,
                        message = "Feedback submitted successfully",
                        feedbackId = id
                    ))
                },
                onFailure = { e ->
                    call.respond(HttpStatusCode.BadRequest, FeedbackResponse(
                        success = false,
                        message = e.message ?: "Failed to submit feedback"
                    ))
                }
            )
        }
    }
}

private fun setFingerprintCookie(call: ApplicationCall) {
    val existingCookie = call.request.cookies[FingerprintService.getCookieName()]
    if (existingCookie == null || existingCookie.length != 64) {
        val fingerprint = FingerprintService.generateFingerprint(call)
        call.response.cookies.append(
            Cookie(
                name = FingerprintService.getCookieName(),
                value = fingerprint,
                maxAge = FingerprintService.getCookieMaxAge(),
                path = "/",
                httpOnly = true,
                secure = false // Set to true in production with HTTPS
            )
        )
    }
}
