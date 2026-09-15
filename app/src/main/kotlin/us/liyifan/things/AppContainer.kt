package us.liyifan.things

import android.content.Context
import androidx.work.WorkerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import us.liyifan.things.data.api.OkHttpThingsApi
import us.liyifan.things.data.api.ThingsApi
import us.liyifan.things.data.db.AppDatabase
import us.liyifan.things.data.db.SnapshotStore
import us.liyifan.things.data.repo.HubThingsRepository
import us.liyifan.things.data.repo.StagedSnapshot
import us.liyifan.things.data.repo.ThingsRepository
import us.liyifan.things.data.settings.ConnectionConfig
import us.liyifan.things.data.settings.SettingsStore
import us.liyifan.things.sync.ForegroundPoller
import us.liyifan.things.sync.SyncScheduler
import us.liyifan.things.sync.ThingsWorkerFactory
import us.liyifan.things.sync.UiActivity
import java.util.concurrent.TimeUnit

/**
 * Every singleton the app has.
 *
 * Hand-wired rather than injected: there are a dozen objects here, and a DI framework would be
 * more machinery than the thing it wires. The one non-obvious piece is [config], a snapshot of
 * the connection settings the HTTP client reads on every request, so changing the server in
 * Settings takes effect without rebuilding the client.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
        coerceInputValues = true
    }

    val settings = SettingsStore(appContext)

    /** The latest saved connection, kept current so the API client can read it per request. */
    private val config = MutableStateFlow(ConnectionConfig())

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            // A snapshot read makes the backend sync with Things Cloud before it answers, which
            // on a cold mirror is tens of seconds. This is a read timeout for a slow friend,
            // not for a broken network.
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    val api: ThingsApi by lazy { OkHttpThingsApi(httpClient) { config.value } }

    val database: AppDatabase by lazy { AppDatabase.open(appContext) }

    val snapshotStore: SnapshotStore by lazy { SnapshotStore(database, json) }

    val syncScheduler: SyncScheduler by lazy { SyncScheduler(appContext) }

    val repository: HubThingsRepository by lazy {
        HubThingsRepository(
            db = database,
            api = api,
            store = snapshotStore,
            json = json,
            staged = StagedSnapshot(),
            readyToDraw = { UiActivity.readyToDraw() },
            kickOutbox = { syncScheduler.kickOutbox() },
            onSynced = { at -> settings.setLastSyncedAt(at) },
        )
    }

    val workerFactory: WorkerFactory by lazy {
        ThingsWorkerFactory(processor = { repository.processor }, repository = { repository as ThingsRepository })
    }

    private val poller by lazy { ForegroundPoller(scope, repository, settings) }

    fun start() {
        scope.launch {
            settings.connection.collect { config.value = it }
        }
        scope.launch {
            if (settings.connection.first().configured) {
                syncScheduler.ensurePeriodic()
                repository.refresh(sync = true, force = true)
            }
        }
        poller.start()
    }

    /** Forgets the server: the credentials, the cached world, and the queued writes with it. */
    suspend fun signOut() {
        syncScheduler.cancelAll()
        snapshotStore.wipe()
        settings.clearConnection()
    }
}
