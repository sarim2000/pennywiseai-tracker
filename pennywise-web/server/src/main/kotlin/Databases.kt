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
    val dbConnection: Connection = connectToPostgres(embedded = false)
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

/**
 * Makes a connection to a Postgres database.
 *
 * In order to connect to your running Postgres process,
 * please specify the following parameters in your configuration file:
 * - postgres.url -- Url of your running database process.
 * - postgres.user -- Username for database connection
 * - postgres.password -- Password for database connection
 *
 * If you don't have a database process running yet, you may need to [download]((https://www.postgresql.org/download/))
 * and install Postgres and follow the instructions [here](https://postgresapp.com/).
 * Then, you would be able to edit your url,  which is usually "jdbc:postgresql://host:port/database", as well as
 * user and password values.
 *
 *
 * @param embedded -- if [true] defaults to an embedded database for tests that runs locally in the same process.
 * In this case you don't have to provide any parameters in configuration file, and you don't have to run a process.
 *
 * @return [Connection] that represent connection to the database. Please, don't forget to close this connection when
 * your application shuts down by calling [Connection.close]
 * */
fun Application.connectToPostgres(embedded: Boolean): Connection {
    Class.forName("org.postgresql.Driver")
    if (embedded) {
        log.info("Using embedded H2 database for testing; replace this flag to use postgres")
        return DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "root", "")
    } else {
        val url = environment.config.property("postgres.url").getString()
        val user = environment.config.property("postgres.user").getString()
        val password = environment.config.property("postgres.password").getString()

        log.info("Connecting to postgres database")

        return DriverManager.getConnection(url, user, password)
    }
}
