package app.hahn.tukplus.core.network

import app.hahn.tukplus.core.logging.LogLevel
import app.hahn.tukplus.core.logging.Redactor
import app.hahn.tukplus.core.logging.TukLog
import app.hahn.tukplus.core.model.ShopOpenStatus
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TukApiTest {
    private lateinit var server: MockWebServer
    private val events = mutableListOf<Triple<LogLevel, String, Map<String, Any?>>>()
    private val log = object : TukLog {
        override fun log(level: LogLevel, tag: String, event: String, fields: Map<String, Any?>) {
            synchronized(events) { events += Triple(level, event, fields) }
        }
    }
    private val redactor = Redactor("salt")
    private val sleeps = mutableListOf<Long>()
    private lateinit var api: TukApi
    private lateinit var baseUrl: String

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        baseUrl = server.url("/prod/tuk/").toString()
        val client = TukApi.createClient(
            deviceUuid = { "dev-uuid" },
            userAgent = "TukPlus-Test",
            log = log,
            redactor = redactor,
            baseUrl = baseUrl,
            retry = RetryInterceptor(sleep = { sleeps += it }),
        )
        api = TukApi(client, baseUrl, clock = { 42L })
    }

    @AfterTest
    fun tearDown() = server.close()

    private fun respond(code: Int, body: String) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    @Test
    fun `sends the auth header and the user agent`() = runTest {
        respond(200, "3.7058f")
        assertEquals("3.7058f", api.version().getOrNull())
        val request = server.takeRequest()
        assertEquals("Bearer helloworld|dev-uuid", request.headers["Authorization"])
        assertEquals("TukPlus-Test", request.headers["User-Agent"])
    }

    @Test
    fun `null body is an empty list`() = runTest {
        respond(200, "null")
        assertEquals(emptyList(), api.pageBlobs("Home_9").getOrNull())
        val url = server.takeRequest().url
        assertEquals("page", url.queryParameter("type"))
        assertEquals("Home_9", url.queryParameter("page"))
        assertEquals("Chiang Mai", url.queryParameter("region"))
    }

    @Test
    fun `eateries send coords and a cache buster`() = runTest {
        respond(200, """[{"id":"b1","name":"Shop","workflows":null}]""")
        val shops = api.eateries().getOrNull()!!
        assertEquals("b1", shops.single().id)
        val url = server.takeRequest().url
        assertEquals("18.796143,98.979263", url.queryParameter("coords"))
        assertEquals("42", url.queryParameter("ts"))
    }

    @Test
    fun `plain text 500 is an HTTP error with the text, and is not tried again`() = runTest {
        respond(500, "sql: no rows in result set")
        val result = api.shortLink("@nothing")
        assertIs<ApiResult.HttpError>(result)
        assertEquals("sql: no rows in result set", result.text)
        assertEquals(1, server.requestCount)
        assertEquals(emptyList(), sleeps)
    }

    @Test
    fun `closed shop answer is not a failure`() = runTest {
        respond(500, "checkCommerceAllowTransaction: Shop is not currently open (Shop is closed today)")
        respond(200, "yes")
        assertEquals(ShopOpenStatus.Closed("Shop is not currently open (Shop is closed today)"), api.shopOpen("b1").getOrNull())
        assertEquals(ShopOpenStatus.Open, api.shopOpen("b1").getOrNull())
    }

    @Test
    fun `gateway errors on GET are tried again`() = runTest {
        respond(503, "busy")
        respond(502, "busy")
        respond(200, "3.1")
        assertEquals("3.1", api.version().getOrNull())
        assertEquals(3, server.requestCount)
        assertEquals(listOf(1_000L, 3_000L), sleeps)
        val http = events.single { it.second == "http" }.third
        assertEquals(2, http["retries"])
    }

    @Test
    fun `POST is never tried again`() {
        respond(503, "busy")
        val client = TukApi.createClient({ "u" }, "t", log, redactor, baseUrl, RetryInterceptor(sleep = { sleeps += it }))
        val body = """{"created_by":"user-1","data":{"order":{"order_value":380}}}""".toRequestBody("application/json".toMediaType())
        client.newCall(Request.Builder().url(baseUrl + "transactions").post(body).build()).execute().close()
        assertEquals(1, server.requestCount)
        assertEquals(emptyList(), sleeps)
        val event = events.single { it.second == "http" }.third
        assertTrue(event["req"].toString().contains(redactor.hash("user-1")))
        assertTrue(!event["req"].toString().contains("user-1"))
        assertEquals("busy", event["resp"])
    }

    @Test
    fun `log has the redacted path and no cache buster`() = runTest {
        respond(200, "[]")
        api.forYou("26ba9921-6f9a-11eb-a3cb-96d83c7bed1c")
        val fields = events.single { it.second == "http" }.third
        assertEquals("recommendations/foryou?page=eat&user_id=${redactor.hash("26ba9921-6f9a-11eb-a3cb-96d83c7bed1c")}", fields["path"])
        assertEquals(200, fields["status"])
    }

    @Test
    fun `bad JSON is a parse error with the start of the body`() = runTest {
        respond(200, "<html>oops</html>")
        val result = api.eateries()
        assertIs<ApiResult.ParseError>(result)
        assertEquals("<html>oops</html>", result.snippet)
    }

    @Test
    fun `device uuid is version 1`() {
        val uuid: UUID = DeviceUuid.newV1()
        assertEquals(1, uuid.version())
        assertEquals(2, uuid.variant())
        val timeMs = (uuid.timestamp() - 0x01B21DD213814000L) / 10_000
        assertTrue(kotlin.math.abs(timeMs - System.currentTimeMillis()) < 60_000)
    }
}
