package com.example

import com.example.feedback.FeedbackService
import com.example.feedback.configureFeedback
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.sql.Connection
import java.sql.DriverManager

fun Application.configureDatabases() {
    val dbConnection: Connection = connectToPostgres()
    val smsReportService = SmsReportService(dbConnection)
    val feedbackService = FeedbackService(dbConnection)

    // Configure feedback routes
    configureFeedback(feedbackService)

    routing {
        // SMS Report endpoint
        post("/api/report") {
            try {
                val request = call.receive<CreateSmsReportRequest>()
                val reportId = smsReportService.createReport(request)
                call.respond(HttpStatusCode.Created, SmsReportResponse(
                    success = true,
                    message = "Report submitted successfully",
                    reportId = reportId
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, SmsReportResponse(
                    success = false,
                    message = "Failed to submit report: ${e.message}"
                ))
            }
        }
    }
}

/** Connects to the database named by the `postgres.url`, `postgres.user` and `postgres.password` config keys. */
fun Application.connectToPostgres(): Connection {
    Class.forName("org.postgresql.Driver")
    val url = environment.config.property("postgres.url").getString()
    val user = environment.config.property("postgres.user").getString()
    val password = environment.config.property("postgres.password").getString()

    log.info("Connecting to postgres database")

    return DriverManager.getConnection(url, user, password)
}
