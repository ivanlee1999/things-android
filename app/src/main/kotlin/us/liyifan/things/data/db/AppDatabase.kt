package us.liyifan.things.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AreaEntity::class, TagEntity::class, TaskEntity::class, TaskTagEntity::class,
        ChecklistEntity::class, ViewMembershipEntity::class, ProjectDoneEntity::class,
        LogbookEntity::class, TrashEntity::class, SyncMetaEntity::class,
        OutboxEntity::class, IdMapEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun snapshotDao(): SnapshotDao
    abstract fun taskDao(): TaskDao
    abstract fun areaDao(): AreaDao
    abstract fun tagDao(): TagDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun viewDao(): ViewDao
    abstract fun logbookDao(): LogbookDao
    abstract fun outboxDao(): OutboxDao
    abstract fun metaDao(): MetaDao

    companion object {
        fun open(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "things.db")
                // Everything here except the outbox is a cache of the server's snapshot, and a
                // destructive migration costs one refetch. The outbox is the exception, so a
                // schema bump belongs in a release that drains it first.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
