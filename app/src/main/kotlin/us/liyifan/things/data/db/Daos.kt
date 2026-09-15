package us.liyifan.things.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAreas(rows: List<AreaEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTags(rows: List<TagEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTasks(rows: List<TaskEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTaskTags(rows: List<TaskTagEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putChecklist(rows: List<ChecklistEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putViews(rows: List<ViewMembershipEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putProjectDone(rows: List<ProjectDoneEntity>)

    // A snapshot replaces the server's world but must leave this device's un-sent rows standing.
    @Query("DELETE FROM areas WHERE provisional = 0") suspend fun clearAreas()
    @Query("DELETE FROM tags WHERE provisional = 0") suspend fun clearTags()
    @Query("DELETE FROM tasks WHERE provisional = 0") suspend fun clearTasks()
    @Query("DELETE FROM checklist WHERE provisional = 0") suspend fun clearChecklist()
    @Query("DELETE FROM task_tags WHERE taskId NOT IN (SELECT id FROM tasks)") suspend fun clearOrphanTaskTags()
    @Query("DELETE FROM view_membership") suspend fun clearViews()
    @Query("DELETE FROM project_done") suspend fun clearProjectDone()

    @Query("SELECT * FROM areas") suspend fun areas(): List<AreaEntity>
    @Query("SELECT * FROM tags") suspend fun tags(): List<TagEntity>
    @Query("SELECT * FROM tasks") suspend fun tasks(): List<TaskEntity>
    @Query("SELECT * FROM task_tags") suspend fun taskTags(): List<TaskTagEntity>
    @Query("SELECT * FROM checklist") suspend fun checklist(): List<ChecklistEntity>
    @Query("SELECT * FROM view_membership ORDER BY position") suspend fun views(): List<ViewMembershipEntity>
    @Query("SELECT * FROM project_done") suspend fun projectDone(): List<ProjectDoneEntity>

    // Used only by an explicit Resync, which throws away un-sent work on purpose.
    @Query("DELETE FROM areas") suspend fun deleteAllAreas()
    @Query("DELETE FROM tags") suspend fun deleteAllTags()
    @Query("DELETE FROM tasks") suspend fun deleteAllTasks()
    @Query("DELETE FROM task_tags") suspend fun deleteAllTaskTags()
    @Query("DELETE FROM checklist") suspend fun deleteAllChecklist()

    @Query("SELECT * FROM tasks WHERE provisional = 1") suspend fun provisionalTasks(): List<TaskEntity>
    @Query("SELECT * FROM areas WHERE provisional = 1") suspend fun provisionalAreas(): List<AreaEntity>
    @Query("SELECT * FROM tags WHERE provisional = 1") suspend fun provisionalTags(): List<TagEntity>
    @Query("SELECT * FROM checklist WHERE provisional = 1") suspend fun provisionalChecklist(): List<ChecklistEntity>
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun byId(id: String): TaskEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(task: TaskEntity)
    @Update suspend fun update(task: TaskEntity)
    @Query("DELETE FROM tasks WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM tasks WHERE projectId = :projectId") suspend fun deleteInProject(projectId: String)
    @Query("DELETE FROM tasks WHERE areaId = :areaId") suspend fun deleteInArea(areaId: String)
    @Query("SELECT * FROM tasks WHERE areaId = :areaId AND type = 1") suspend fun projectsInArea(areaId: String): List<TaskEntity>
    @Query("SELECT * FROM tasks WHERE areaId = :areaId AND type = 0") suspend fun tasksInArea(areaId: String): List<TaskEntity>
    @Query("SELECT tagId FROM task_tags WHERE taskId = :taskId") suspend fun tagsOf(taskId: String): List<String>
    @Query("DELETE FROM task_tags WHERE taskId = :taskId") suspend fun clearTagsOf(taskId: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTags(rows: List<TaskTagEntity>)
    @Query("UPDATE tasks SET id = :to, provisional = 0 WHERE id = :from") suspend fun renameId(from: String, to: String)
    @Query("UPDATE tasks SET projectId = :to WHERE projectId = :from") suspend fun renameProject(from: String, to: String)
    @Query("UPDATE tasks SET headingId = :to WHERE headingId = :from") suspend fun renameHeading(from: String, to: String)
    @Query("UPDATE tasks SET areaId = :to WHERE areaId = :from") suspend fun renameArea(from: String, to: String)
    @Query("UPDATE task_tags SET taskId = :to WHERE taskId = :from") suspend fun renameTaskTagTask(from: String, to: String)
    @Query("UPDATE task_tags SET tagId = :to WHERE tagId = :from") suspend fun renameTaskTagTag(from: String, to: String)
}

@Dao
interface AreaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(area: AreaEntity)
    @Query("SELECT * FROM areas WHERE id = :id") suspend fun byId(id: String): AreaEntity?
    @Query("DELETE FROM areas WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE areas SET id = :to, provisional = 0 WHERE id = :from") suspend fun renameId(from: String, to: String)
    @Query("SELECT COALESCE(MAX(idx), 0) FROM areas") suspend fun maxIndex(): Int
}

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(tag: TagEntity)
    @Query("DELETE FROM tags WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE tags SET id = :to, provisional = 0 WHERE id = :from") suspend fun renameId(from: String, to: String)
    @Query("SELECT COALESCE(MAX(idx), 0) FROM tags") suspend fun maxIndex(): Int
}

@Dao
interface ChecklistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(item: ChecklistEntity)
    @Query("SELECT * FROM checklist WHERE id = :id") suspend fun byId(id: String): ChecklistEntity?
    @Query("SELECT COUNT(*) FROM checklist WHERE taskId = :taskId") suspend fun countFor(taskId: String): Int
    @Query("DELETE FROM checklist WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM checklist WHERE taskId = :taskId") suspend fun deleteForTask(taskId: String)
    @Query("UPDATE checklist SET id = :to, provisional = 0 WHERE id = :from") suspend fun renameId(from: String, to: String)
    @Query("UPDATE checklist SET taskId = :to WHERE taskId = :from") suspend fun renameTask(from: String, to: String)
}

@Dao
interface ViewDao {
    @Query("SELECT * FROM view_membership ORDER BY position") suspend fun all(): List<ViewMembershipEntity>
    @Query("DELETE FROM view_membership") suspend fun clear()
    @Query("DELETE FROM view_membership WHERE taskId = :taskId") suspend fun remove(taskId: String)
    @Query("UPDATE view_membership SET taskId = :to WHERE taskId = :from") suspend fun renameId(from: String, to: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(rows: List<ViewMembershipEntity>)
}

@Dao
interface LogbookDao {
    @Query("SELECT * FROM logbook WHERE scope IS NULL ORDER BY completedAt DESC") suspend fun page(): List<LogbookEntity>
    @Query("SELECT * FROM logbook WHERE scope = :projectId ORDER BY completedAt DESC") suspend fun forProject(projectId: String): List<LogbookEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(rows: List<LogbookEntity>)
    @Query("DELETE FROM logbook WHERE scope IS NULL") suspend fun clearMain()
    @Query("DELETE FROM logbook WHERE scope = :projectId") suspend fun clearProject(projectId: String)
    @Query("DELETE FROM logbook WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM logbook") suspend fun clearAll()

    @Query("SELECT * FROM trash ORDER BY modifiedAt DESC") suspend fun trash(): List<TrashEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTrash(rows: List<TrashEntity>)
    @Query("DELETE FROM trash") suspend fun clearTrash()
    @Query("DELETE FROM trash WHERE id = :id") suspend fun deleteTrash(id: String)
}

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox ORDER BY id LIMIT 1") suspend fun oldest(): OutboxEntity?
    @Query("SELECT * FROM outbox ORDER BY id") suspend fun all(): List<OutboxEntity>
    @Query("SELECT COUNT(*) FROM outbox") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM outbox") fun countFlow(): Flow<Int>
    @Insert suspend fun insert(row: OutboxEntity): Long
    @Query("DELETE FROM outbox WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM outbox") suspend fun clear()
    @Query("UPDATE outbox SET payload = :payload WHERE id = :id") suspend fun setPayload(id: Long, payload: String)
    @Query("UPDATE outbox SET tempId = :tempId WHERE id = :id") suspend fun setTempId(id: Long, tempId: String?)
    @Query("UPDATE outbox SET attempts = attempts + 1, lastError = :error, lastAttemptAt = :at WHERE id = :id")
    suspend fun recordFailure(id: Long, error: String?, at: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putIdMap(row: IdMapEntity)
    @Query("SELECT realId FROM id_map WHERE tempId = :tempId") suspend fun realIdFor(tempId: String): String?
    @Query("DELETE FROM id_map WHERE resolvedAt < :before") suspend fun pruneIdMap(before: Long)
    @Query("DELETE FROM id_map") suspend fun clearIdMap()
}

@Dao
interface MetaDao {
    @Query("SELECT value FROM sync_meta WHERE key = :key") suspend fun get(key: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(row: SyncMetaEntity)
    @Query("DELETE FROM sync_meta") suspend fun clear()

    /**
     * One counter bumped by every writing transaction, and the only thing the snapshot flow
     * watches. Observing the tables instead would emit once per table per write and rebuild the
     * whole model several times for a single edit.
     */
    @Query("SELECT value FROM sync_meta WHERE key = 'generation'")
    fun generationFlow(): Flow<String?>

    @Transaction
    suspend fun bumpGeneration() {
        val next = (get("generation")?.toLongOrNull() ?: 0L) + 1
        put(SyncMetaEntity("generation", next.toString()))
    }
}
