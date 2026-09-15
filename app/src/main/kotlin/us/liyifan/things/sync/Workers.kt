package us.liyifan.things.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import us.liyifan.things.data.outbox.OutboxProcessor
import us.liyifan.things.data.repo.ThingsRepository
import java.util.concurrent.TimeUnit

/**
 * Sends whatever is queued, and retries with backoff when it cannot.
 *
 * The repository also drains directly while the app is in front of the user, which is the fast
 * path; this is the one that survives the app being closed, and the one that eventually gets a
 * to-do written on a train onto the server.
 */
class OutboxWorker(
    context: Context,
    params: WorkerParameters,
    private val processor: OutboxProcessor,
    private val repository: ThingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (val result = processor.drain()) {
        is OutboxProcessor.Result.Blocked -> Result.retry()
        is OutboxProcessor.Result.Drained -> {
            // A read costs the server a round trip to Things Cloud, so only ask for a fresh one
            // when something was actually refused and the local copy is suspect.
            repository.refresh(sync = result.needsFullRefresh, force = true)
            Result.success()
        }
    }
}

/** The background heartbeat: drain first, then pull. */
class RefreshWorker(
    context: Context,
    params: WorkerParameters,
    private val processor: OutboxProcessor,
    private val repository: ThingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (processor.pending() > 0 && processor.drain() is OutboxProcessor.Result.Blocked) {
            return Result.retry()
        }
        repository.refresh(sync = true, force = true)
        return Result.success()
    }
}

/** Hands the workers their dependencies; WorkManager's own initializer is off in the manifest. */
class ThingsWorkerFactory(
    private val processor: () -> OutboxProcessor,
    private val repository: () -> ThingsRepository,
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        OutboxWorker::class.java.name -> OutboxWorker(appContext, workerParameters, processor(), repository())
        RefreshWorker::class.java.name -> RefreshWorker(appContext, workerParameters, processor(), repository())
        else -> null
    }
}

/**
 * When the app talks to the server.
 *
 * Reads are deliberately infrequent. Every one of them makes the backend sync with Things Cloud
 * before it answers, so this polls in minutes, not seconds — the backend's own README asks for
 * exactly that.
 */
class SyncScheduler(context: Context) {

    private val work = WorkManager.getInstance(context.applicationContext)

    fun kickOutbox() {
        work.enqueueUniqueWork(
            OUTBOX_WORK,
            // Appending rather than replacing: a second edit must not cancel the send of the first.
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<OutboxWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build(),
        )
    }

    fun ensurePeriodic() {
        work.enqueueUniquePeriodicWork(
            REFRESH_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build(),
        )
    }

    fun cancelAll() {
        work.cancelUniqueWork(OUTBOX_WORK)
        work.cancelUniqueWork(REFRESH_WORK)
    }

    private companion object {
        const val OUTBOX_WORK = "outbox"
        const val REFRESH_WORK = "refresh"
    }
}
