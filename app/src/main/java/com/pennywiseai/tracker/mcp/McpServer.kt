package com.pennywiseai.tracker.mcp

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class McpServer(
    private val toolHandler: McpToolHandler,
    private val port: Int = DEFAULT_PORT
) {
    companion object {
        const val DEFAULT_PORT = 8765
    }

    private var server: EmbeddedServer<*, *>? = null

    suspend fun start() {
        withContext(Dispatchers.IO) {
            server = embeddedServer(CIO, host = "0.0.0.0", port = port) {
                install(ContentNegotiation) { json(McpJson) }
                mcpStreamableHttp {
                    toolHandler.createServer()
                }
                routing {
                    get("/health") {
                        call.respondText("PennyWise MCP Server running")
                    }
                }
            }.also {
                it.start(wait = false)
            }
        }
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 1000, timeoutMillis = 2000)
        server = null
    }
}
