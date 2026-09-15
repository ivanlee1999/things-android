package us.liyifan.things

import android.app.Application
import androidx.work.Configuration

/**
 * The process. Owns the one [AppContainer] every screen and worker reads from.
 */
class ThingsApp : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }

    // WorkManager's own initializer is removed in the manifest so its workers can be handed the
    // container rather than reaching for a global.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(container.workerFactory)
            .build()
}

/** The container, from anywhere with a context. */
val android.content.Context.container: AppContainer
    get() = (applicationContext as ThingsApp).container
