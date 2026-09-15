package us.liyifan.things.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import us.liyifan.things.data.settings.ConnectionConfig
import us.liyifan.things.model.EditFields
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * The REST client.
 *
 * Hand-written over OkHttp rather than generated: there are twenty endpoints, one base URL and
 * two header conventions, and a converter library would be more configuration than code.
 *
 * [config] is read per request, not captured, so changing the server in Settings takes effect
 * without rebuilding anything.
 */
class OkHttpThingsApi(
    private val client: OkHttpClient,
    private val config: () -> ConnectionConfig,
) : ThingsApi {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
        coerceInputValues = true
    }

    override suspend fun snapshot(sync: Boolean): SnapshotDto =
        get("snapshot") { if (!sync) it.addQueryParameter("sync", "0") }

    override suspend fun logbook(limit: Int, before: String?, project: String?, sync: Boolean): List<TaskDto> =
        get("tasks/logbook") { b ->
            b.addQueryParameter("limit", limit.toString())
            before?.let { b.addQueryParameter("before", it) }
            project?.let { b.addQueryParameter("project", it) }
            if (!sync) b.addQueryParameter("sync", "0")
        }

    override suspend fun trash(limit: Int, sync: Boolean): List<TaskDto> =
        get("trash") { b ->
            b.addQueryParameter("limit", limit.toString())
            if (!sync) b.addQueryParameter("sync", "0")
        }

    override suspend fun createTask(request: CreateTaskRequest): String =
        postForId("tasks/create", request)

    override suspend fun editTask(fields: EditFields) {
        post(
            "tasks/edit",
            EditTaskRequest(
                uuid = fields.uuid,
                title = fields.title,
                note = fields.note,
                whenValue = fields.whenValue,
                deadline = fields.deadline,
                project = fields.project,
                area = fields.area,
                heading = fields.heading,
                tags = fields.tags,
            ),
        )
    }

    override suspend fun taskAction(action: TaskAction, uuid: String) {
        post("tasks/${action.path}", UuidRequest(uuid))
    }

    override suspend fun moveTask(uuid: String, to: String) {
        post("tasks/move", MoveRequest(uuid, to))
    }

    override suspend fun createChecklistItem(taskUuid: String, title: String): String =
        postForId("checklist/create", CreateChecklistRequest(taskUuid, title))

    override suspend fun checklistAction(action: ChecklistAction, uuid: String) {
        post("checklist/${action.path}", UuidRequest(uuid))
    }

    override suspend fun createProject(request: CreateProjectRequest): String =
        postForId("projects/create", request)

    override suspend fun createHeading(title: String, project: String): String =
        postForId("headings/create", CreateHeadingRequest(title, project))

    override suspend fun createArea(title: String): String =
        postForId("areas/create", TitleRequest(title))

    override suspend fun editArea(uuid: String, title: String) {
        post("areas/edit", EditAreaRequest(uuid, title))
    }

    override suspend fun deleteArea(uuid: String) {
        post("areas/delete", UuidRequest(uuid))
    }

    override suspend fun createTag(title: String): String =
        postForId("tags/create", CreateTagRequest(title))

    // -----------------------------------------------------------------------------------------

    private suspend inline fun <reified T> get(
        path: String,
        query: (HttpUrl.Builder) -> Unit = {},
    ): T {
        val url = url(path).also(query).build()
        return json.decodeFromString(execute(Request.Builder().url(url).get()))
    }

    private suspend inline fun <reified T> post(path: String, body: T): WriteResponse {
        val payload = json.encodeToString(body).toRequestBody(JSON_MEDIA)
        val text = execute(Request.Builder().url(url(path).build()).post(payload))
        // A few handlers answer with a bare status; decoding leniently keeps them all one path.
        return runCatching { json.decodeFromString<WriteResponse>(text) }.getOrDefault(WriteResponse())
    }

    private suspend inline fun <reified T> postForId(path: String, body: T): String =
        post(path, body).uuid
            ?: throw ApiException(502, "the server created something but did not say what")

    private fun url(path: String): HttpUrl.Builder {
        val base = config().baseUrl.trimEnd('/')
        val parsed = "$base/api/$path".toHttpUrlOrNull()
            ?: throw ApiException(0, "\"$base\" is not a usable address", ApiException.Kind.NETWORK)
        return parsed.newBuilder()
    }

    private suspend fun execute(builder: Request.Builder): String = withContext(Dispatchers.IO) {
        val c = config()
        builder.header("Accept", "application/json")
        if (c.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${c.apiKey}")
        // Cloudflare Access service tokens, when the server sits behind a tunnel with a policy.
        if (c.cfAccessClientId.isNotBlank()) {
            builder.header("CF-Access-Client-Id", c.cfAccessClientId)
            builder.header("CF-Access-Client-Secret", c.cfAccessClientSecret)
        }
        val response = try {
            client.newCall(builder.build()).executeSuspending()
        } catch (e: UnknownHostException) {
            throw ApiException.network("cannot find ${e.message ?: "the server"}")
        } catch (e: SocketTimeoutException) {
            throw ApiException.network("the server did not answer in time")
        } catch (e: IOException) {
            throw ApiException.network(e.message ?: "the network is unreachable")
        }
        response.use {
            val text = it.body.string()
            if (it.isSuccessful) return@withContext text
            throw failure(it.code, it.header("Content-Type"), text)
        }
    }

    private fun failure(code: Int, contentType: String?, body: String): ApiException {
        // Cloudflare Access refuses with an HTML login page, not with this API's JSON. Saying
        // "access denied" beats echoing a wall of markup, or claiming the device is offline.
        if (contentType?.contains("text/html") == true) {
            return ApiException(code, "Cloudflare Access refused the request", ApiException.Kind.ACCESS_DENIED)
        }
        val message = runCatching { json.decodeFromString<ErrorResponse>(body).error }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "the server answered $code"
        val kind = if (code == 401) ApiException.Kind.UNAUTHORIZED else ApiException.Kind.SERVER
        return ApiException(code, message, kind)
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

/** OkHttp's blocking execute, made cancellable from a coroutine. */
private suspend fun Call.executeSuspending(): okhttp3.Response =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { runCatching { cancel() } }
        try {
            cont.resumeWith(Result.success(execute()))
        } catch (e: Throwable) {
            if (!cont.isCancelled) cont.resumeWith(Result.failure(e))
        }
    }
