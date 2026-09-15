package us.liyifan.things

import android.app.Application
import androidx.work.Configuration

/**
 * The process. Holds the one [AppContainer] every screen and worker reads its dependencies from
 * — there is no DI framework here, because there are about a dozen singletons and a framework
 * would be more machinery than the thing it wires.
 */
class ThingsApp : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    // WorkManager's own initializer is removed in the manifest so the workers can be handed the
    // container rather than reaching for a global.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(container.workerFactory)
            .build()
}
