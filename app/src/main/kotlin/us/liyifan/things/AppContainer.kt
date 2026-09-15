package us.liyifan.things

import android.content.Context
import androidx.work.DelegatingWorkerFactory

/**
 * Every singleton the app has, created lazily and owned by [ThingsApp]. Screens reach it through
 * the application context; workers are handed it by [workerFactory].
 */
class AppContainer(private val context: Context) {
    val workerFactory: DelegatingWorkerFactory = DelegatingWorkerFactory()
}
