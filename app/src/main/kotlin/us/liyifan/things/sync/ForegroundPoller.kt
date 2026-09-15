package us.liyifan.things.sync

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import us.liyifan.things.data.repo.ThingsRepository
import us.liyifan.things.data.settings.SettingsStore

/**
 * Keeps the app roughly in step with the server while it is on screen.
 *
 * Three rules, all of them about not being a nuisance. Nothing polls while the app is in the
 * background, because the periodic worker covers that. Nothing polls while a card is open,
 * because a snapshot landing mid-edit is how a half-typed title gets replaced. And nothing polls
 * at all in e-ink mode: a list that reflows by itself every minute is unreadable on e-paper, so
 * there it refreshes on resume and on demand, and the sync line says when something is waiting.
 */
class ForegroundPoller(
    private val scope: CoroutineScope,
    private val repository: ThingsRepository,
    private val settings: SettingsStore,
) : DefaultLifecycleObserver {

    private var job: Job? = null

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        scope.launch {
            repository.applyStagedIfAny()
            val since = System.currentTimeMillis() - repository.syncState.value.lastSyncedAt
            if (since > STALE_MS) repository.refresh(sync = true, force = true)
        }
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                delay(INTERVAL_MS)
                if (settings.appearance.first().eink.enabled) continue
                if (UiActivity.editorOpen.value) continue
                repository.refresh(sync = true)
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        job?.cancel()
        job = null
    }

    private companion object {
        const val INTERVAL_MS = 60_000L
        const val STALE_MS = 20_000L
    }
}
