package com.pennywiseai.tracker.data.manager

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** [ResumableFetch] against a real local HTTP server (the JDK's built-in one). */
class ResumableFetchTest {

    private val content = ByteArray(64 * 1024) { (it % 251).toByte() }
    private lateinit var server: HttpServer
    private lateinit var part: File

    /** How the server answers the next request. */
    private var handler: (HttpExchange) -> Unit = ::serveWithRange

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/model") { exchange -> exchange.use { handler(it) } }
        server.start()
        part = File.createTempFile("model", ".part").apply { delete() }
    }

    @After
    fun tearDown() {
        server.stop(0)
        part.delete()
    }

    private fun fetch() = ResumableFetch(
        url = "http://127.0.0.1:${server.address.port}/model",
        expectedBytes = content.size.toLong(),
        timeoutMs = 5_000,
    )

    private fun serveWithRange(exchange: HttpExchange) {
        val from = exchange.requestHeaders.getFirst("Range")?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
        if (from >= content.size) {
            exchange.sendResponseHeaders(416, -1)
            return
        }
        exchange.sendResponseHeaders(if (from > 0) 206 else 200, (content.size - from).toLong())
        exchange.responseBody.write(content, from, content.size - from)
    }

    @Test
    fun `206 appends to the partial file`() = runBlocking {
        part.writeBytes(content.copyOfRange(0, 10_000))
        fetch().into(part)
        assertArrayEquals(content, part.readBytes())
    }

    @Test
    fun `200 when the server ignores the range restarts from zero`() = runBlocking {
        part.writeBytes(ByteArray(10_000) { 7 }) // junk that must not survive
        handler = { exchange ->
            exchange.sendResponseHeaders(200, content.size.toLong())
            exchange.responseBody.write(content)
        }
        fetch().into(part)
        assertArrayEquals(content, part.readBytes())
    }

    @Test
    fun `416 with a complete file is done`() = runBlocking {
        part.writeBytes(content)
        fetch().into(part)
        assertArrayEquals(content, part.readBytes())
    }

    @Test
    fun `416 with a short file discards it and fails`() = runBlocking {
        part.writeBytes(content.copyOfRange(0, 10_000))
        handler = { it.sendResponseHeaders(416, -1) }
        try {
            fetch().into(part)
            fail("expected IOException")
        } catch (e: IOException) {
            assertFalse(part.exists())
        }
    }

    @Test
    fun `a short response fails, and the next attempt resumes to completion`() = runBlocking {
        handler = { exchange ->
            exchange.sendResponseHeaders(200, 0) // chunked: no length to hold it to
            exchange.responseBody.write(content, 0, 20_000)
        }
        try {
            fetch().into(part)
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(part.length() == 20_000L)
        }
        handler = ::serveWithRange
        fetch().into(part)
        assertArrayEquals(content, part.readBytes())
    }

    @Test
    fun `cancelled before the response arrives leaves the partial file untouched`() = runBlocking {
        val saved = content.copyOfRange(0, 10_000)
        part.writeBytes(saved)
        val requested = CountDownLatch(1)
        handler = { exchange ->
            requested.countDown()
            Thread.sleep(500) // response arrives after the cancel
            exchange.sendResponseHeaders(200, content.size.toLong())
            exchange.responseBody.write(content)
        }
        val job = launch(Dispatchers.IO) { fetch().into(part) }
        assertTrue(requested.await(5, TimeUnit.SECONDS))
        job.cancel()
        job.join()
        delay(100)
        assertArrayEquals(saved, part.readBytes())
    }
}
