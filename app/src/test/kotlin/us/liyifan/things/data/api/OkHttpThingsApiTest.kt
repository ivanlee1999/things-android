package us.liyifan.things.data.api

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import us.liyifan.things.data.settings.ConnectionConfig
import us.liyifan.things.model.EditFields

class OkHttpThingsApiTest {

    private lateinit var server: MockWebServer
    private var config = ConnectionConfig(baseUrl = "", apiKey = "secret-key")
    private lateinit var api: OkHttpThingsApi

    @Before fun start() {
        server = MockWebServer()
        server.start()
        config = config.copy(baseUrl = server.url("/").toString().trimEnd('/'))
        api = OkHttpThingsApi(OkHttpClient()) { config }
    }

    @After fun stop() = server.close()

    private fun enqueue(body: String, code: Int = 200, contentType: String = "application/json") {
        server.enqueue(
            MockResponse.Builder()
                .code(code)
                .setHeader("Content-Type", contentType)
                .body(body)
                .build(),
        )
    }

    @Test fun `a snapshot read carries the key and asks the server to sync`() = runTest {
        enqueue("""{"today":"2026-09-15","tasks":[{"id":"a","title":"Alpha"}]}""")
        val snapshot = api.snapshot(sync = true)

        val request = server.takeRequest()
        assertEquals("/api/snapshot", request.url.encodedPath)
        assertNull(request.url.queryParameter("sync"))
        assertEquals("Bearer secret-key", request.headers["Authorization"])
        assertNull(request.headers["CF-Access-Client-Id"])
        assertEquals("2026-09-15", snapshot.today)
        assertEquals("Alpha", snapshot.toModel().tasks.single().title)
    }

    @Test fun `sync=0 is how a read after a write avoids a Things Cloud round trip`() = runTest {
        enqueue("""{"today":"2026-09-15"}""")
        api.snapshot(sync = false)
        assertEquals("0", server.takeRequest().url.queryParameter("sync"))
    }

    @Test fun `Cloudflare Access headers ride along when configured`() = runTest {
        config = config.copy(cfAccessClientId = "id.access", cfAccessClientSecret = "shh")
        enqueue("""{"today":"2026-09-15"}""")
        api.snapshot()

        val request = server.takeRequest()
        assertEquals("id.access", request.headers["CF-Access-Client-Id"])
        assertEquals("shh", request.headers["CF-Access-Client-Secret"])
    }

    @Test fun `unknown fields in the response are ignored, not fatal`() = runTest {
        enqueue("""{"today":"2026-09-15","somethingNew":42,"tasks":[{"id":"a","futureField":true}]}""")
        assertEquals("a", api.snapshot().tasks.single().id)
    }

    @Test fun `logbook paging passes its cursor through`() = runTest {
        enqueue("""[{"id":"x","completedAt":"2026-09-14T10:00:00Z"}]""")
        val page = api.logbook(limit = 50, before = "2026-09-15T00:00:00Z", project = "p1")

        val request = server.takeRequest()
        assertEquals("50", request.url.queryParameter("limit"))
        assertEquals("2026-09-15T00:00:00Z", request.url.queryParameter("before"))
        assertEquals("p1", request.url.queryParameter("project"))
        assertEquals("0", request.url.queryParameter("sync"))
        assertEquals("x", page.single().id)
    }

    @Test fun `creating a task returns the id the server minted`() = runTest {
        enqueue("""{"status":"created","uuid":"ud5VEgS2ApfLrrD9tTeve","title":"Buy milk"}""")
        val id = api.createTask(CreateTaskRequest(title = "Buy milk", whenValue = "today"))
        assertEquals("ud5VEgS2ApfLrrD9tTeve", id)

        val request = server.takeRequest()
        assertEquals("/api/tasks/create", request.url.encodedPath)
        val body = request.body.utf8()
        assertTrue(body.contains(""""title":"Buy milk""""))
        // The backend's field is `when`, whatever Kotlin calls it.
        assertTrue(body.contains(""""when":"today""""))
    }

    @Test fun `an edit omits absent fields entirely`() = runTest {
        enqueue("""{"status":"updated","uuid":"u1"}""")
        api.editTask(EditFields(uuid = "u1", title = "New title", deadline = "none"))

        val body = server.takeRequest().body.utf8()
        assertTrue(body.contains(""""uuid":"u1""""))
        assertTrue(body.contains(""""title":"New title""""))
        assertTrue(body.contains(""""deadline":"none""""))
        assertTrue("an absent field must not appear at all: $body", !body.contains("\"note\""))
        assertTrue(!body.contains("\"area\""))
    }

    @Test fun `task actions map onto their paths`() = runTest {
        enqueue("""{"status":"completed","uuid":"u1"}""")
        api.taskAction(TaskAction.COMPLETE, "u1")
        assertEquals("/api/tasks/complete", server.takeRequest().url.encodedPath)

        enqueue("""{"status":"untrashed","uuid":"u1"}""")
        api.taskAction(TaskAction.UNTRASH, "u1")
        assertEquals("/api/tasks/untrash", server.takeRequest().url.encodedPath)
    }

    @Test fun `the server's error message is what the user is told`() = runTest {
        enqueue("""{"error":"legacy ids are read-only"}""", code = 400)
        val error = runCatching { api.taskAction(TaskAction.TRASH, "old-id") }.exceptionOrNull()
        assertTrue(error is ApiException)
        error as ApiException
        assertEquals(400, error.status)
        assertEquals("legacy ids are read-only", error.message)
        assertTrue("a 400 will fail identically forever", error.permanent)
    }

    @Test fun `a failed pre-read sync is worth retrying`() = runTest {
        enqueue("""{"error":"pre-read sync failed: dial tcp: timeout"}""", code = 503)
        val error = runCatching { api.snapshot() }.exceptionOrNull() as ApiException
        assertEquals(503, error.status)
        assertTrue(!error.permanent)
    }

    @Test fun `a bad key is named as such`() = runTest {
        enqueue("""{"error":"unauthorized"}""", code = 401)
        val error = runCatching { api.snapshot() }.exceptionOrNull() as ApiException
        assertEquals(ApiException.Kind.UNAUTHORIZED, error.kind)
        assertTrue(error.permanent)
    }

    @Test fun `an HTML refusal is Cloudflare Access, not a broken server`() = runTest {
        enqueue("<html><body>Sign in to continue</body></html>", code = 403, contentType = "text/html")
        val error = runCatching { api.snapshot() }.exceptionOrNull() as ApiException
        assertEquals(ApiException.Kind.ACCESS_DENIED, error.kind)
        assertTrue(error.message!!.contains("Cloudflare"))
    }

    @Test fun `an unreachable server is a network failure, which is retried`() = runTest {
        server.close()
        val error = runCatching { api.snapshot() }.exceptionOrNull() as ApiException
        assertEquals(ApiException.Kind.NETWORK, error.kind)
        assertTrue(!error.permanent)
    }

    @Test fun `an unusable base URL is reported before any request`() = runTest {
        config = config.copy(baseUrl = "not a url")
        val error = runCatching { api.snapshot() }.exceptionOrNull() as ApiException
        assertEquals(ApiException.Kind.NETWORK, error.kind)
    }
}
