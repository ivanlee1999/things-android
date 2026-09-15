package us.liyifan.things.data.api

import us.liyifan.things.model.EditFields

/**
 * The backend's REST surface, as the app uses it.
 *
 * An interface rather than a concrete client so the repository's tests can drive a fake that
 * mints ids and fails on demand, and so a future client that talks to Things Cloud directly has
 * a shape to fit.
 */
interface ThingsApi {

    /**
     * The whole open world in one read.
     *
     * [sync] false reads the server's mirror as it stands; true makes it pull from Things Cloud
     * first, which is a round trip measured in seconds. Read with false right after a write —
     * the write already refreshed the mirror.
     */
    suspend fun snapshot(sync: Boolean = true): SnapshotDto

    /** Completed and cancelled work, newest first, paged by the [before] completion instant. */
    suspend fun logbook(
        limit: Int = 100,
        before: String? = null,
        project: String? = null,
        sync: Boolean = false,
    ): List<TaskDto>

    suspend fun trash(limit: Int = 300, sync: Boolean = false): List<TaskDto>

    /** Returns the id the server minted. */
    suspend fun createTask(request: CreateTaskRequest): String

    suspend fun editTask(fields: EditFields)

    suspend fun taskAction(action: TaskAction, uuid: String)

    suspend fun moveTask(uuid: String, to: String)

    suspend fun createChecklistItem(taskUuid: String, title: String): String

    suspend fun checklistAction(action: ChecklistAction, uuid: String)

    suspend fun createProject(request: CreateProjectRequest): String

    suspend fun createHeading(title: String, project: String): String

    suspend fun createArea(title: String): String

    suspend fun editArea(uuid: String, title: String)

    suspend fun deleteArea(uuid: String)

    suspend fun createTag(title: String): String
}

/** The one-argument task writes, which differ only in their path. */
enum class TaskAction(val path: String) {
    COMPLETE("complete"),
    UNCOMPLETE("uncomplete"),
    CANCEL("cancel"),
    TRASH("trash"),
    UNTRASH("untrash"),
}

enum class ChecklistAction(val path: String) {
    COMPLETE("complete"),
    UNCOMPLETE("uncomplete"),
    DELETE("delete"),
}

/**
 * A request that reached the server and came back refused.
 *
 * [permanent] decides the outbox's behaviour: a permanent failure is dropped and reported,
 * because retrying it will fail identically forever. The backend refuses writes to pre-2020
 * Things ids and (by default) area deletes with a 400, and those are the common cases.
 * A 503 is the pre-read sync to Things Cloud failing, which is worth retrying.
 */
class ApiException(
    val status: Int,
    message: String,
    val kind: Kind = Kind.SERVER,
) : Exception(message) {

    enum class Kind { SERVER, UNAUTHORIZED, ACCESS_DENIED, NETWORK }

    val permanent: Boolean
        get() = kind != Kind.NETWORK && status in 400..499 && status != 408 && status != 429

    companion object {
        fun network(message: String) = ApiException(0, message, Kind.NETWORK)
    }
}
