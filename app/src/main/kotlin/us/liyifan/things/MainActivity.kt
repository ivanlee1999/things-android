package us.liyifan.things

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import us.liyifan.things.data.api.ApiException
import us.liyifan.things.data.api.OkHttpThingsApi
import us.liyifan.things.data.settings.ConnectionConfig
import us.liyifan.things.data.settings.UrlPolicy
import us.liyifan.things.model.NewTaskInit
import us.liyifan.things.ui.ConnectionUi
import us.liyifan.things.ui.ThingsAppUi
import us.liyifan.things.ui.keys.PageKey
import us.liyifan.things.ui.keys.PageKeyBus
import us.liyifan.things.ui.nav.DeepLinks
import us.liyifan.things.ui.nav.Route
import us.liyifan.things.ui.screens.ConnectionStatus
import us.liyifan.things.ui.theme.ThingsTheme
import us.liyifan.things.vm.AppViewModel

class MainActivity : ComponentActivity() {

    private val container by lazy { (application as ThingsApp).container }
    private val einkEnabled = MutableStateFlow(false)
    private val connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Idle)
    private val draftConfig = MutableStateFlow(ConnectionConfig())

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            container.settings.connection.collect { saved ->
                // The form starts from what is saved; edits live in the draft until Save.
                if (draftConfig.value == ConnectionConfig()) draftConfig.value = saved
            }
        }
        lifecycleScope.launch {
            container.settings.appearance.collect { einkEnabled.value = it.eink.enabled }
        }

        setContent {
            val viewModel: AppViewModel = viewModel(
                factory = AppViewModel.factory(container.repository, container.settings),
            )
            val appearance by viewModel.appearance.collectAsStateWithLifecycle()
            val saved by container.settings.connection.collectAsStateWithLifecycle(ConnectionConfig())
            val draft by draftConfig.collectAsStateWithLifecycle()
            val status by connectionStatus.collectAsStateWithLifecycle()
            val navController = rememberNavController()

            LaunchedEffect(Unit) { handleIntent(intent, navController, viewModel) }

            ThingsTheme(appearance = appearance) {
                ThingsAppUi(
                    viewModel = viewModel,
                    version = BuildConfig.VERSION_NAME,
                    navController = navController,
                    connection = ConnectionUi(
                        config = draft,
                        configured = saved.configured,
                        status = status,
                        onChange = { draftConfig.value = it; connectionStatus.value = ConnectionStatus.Idle },
                        onTest = { testConnection(draft) },
                        onSave = { save(draft, viewModel) },
                        onSignOut = {
                            lifecycleScope.launch {
                                container.signOut()
                                draftConfig.value = ConnectionConfig()
                            }
                        },
                    ),
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    /**
     * The page-turn keys.
     *
     * They are claimed only in e-ink mode: a BOOX delivers them as volume keys unless the user
     * has remapped them, and silently swallowing volume on a device being used as a phone would
     * be a bug, not a feature.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (einkEnabled.value) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                    PageKeyBus.emit(PageKey.Down); return true
                }
                KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                    PageKeyBus.emit(PageKey.Up); return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (einkEnabled.value && keyCode in VOLUME_KEYS) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun handleIntent(intent: Intent?, navController: androidx.navigation.NavHostController, viewModel: AppViewModel) {
        val data = intent?.data?.toString() ?: return
        when {
            data == DeepLinks.TODAY -> navController.navigate(Route.Today)
            data == DeepLinks.NEW -> viewModel.createTask(NewTaskInit())
            data.startsWith(DeepLinks.TASK_PREFIX) -> {
                val id = data.removePrefix(DeepLinks.TASK_PREFIX)
                viewModel.expand(id)
            }
        }
    }

    /** Tries the unsaved settings against the server and says plainly what came back. */
    private fun testConnection(config: ConnectionConfig) {
        val problem = UrlPolicy.validate(config.baseUrl, allowCleartext = BuildConfig.DEBUG)
        if (problem != null) {
            connectionStatus.value = ConnectionStatus.Problem(problem.message)
            return
        }
        connectionStatus.value = ConnectionStatus.Testing
        lifecycleScope.launch {
            val api = OkHttpThingsApi(okhttp3.OkHttpClient()) { config }
            connectionStatus.value = try {
                // There is no lighter endpoint than the snapshot, so this is also a real read.
                val snapshot = api.snapshot(sync = false)
                val count = snapshot.tasks.size
                ConnectionStatus.Ok("Connected. $count open to-do${if (count == 1) "" else "s"}, today is ${snapshot.today}.")
            } catch (e: ApiException) {
                ConnectionStatus.Problem(
                    when (e.kind) {
                        ApiException.Kind.UNAUTHORIZED -> "The server refused that API key."
                        ApiException.Kind.ACCESS_DENIED -> "Cloudflare Access refused the request. Check the service token."
                        ApiException.Kind.NETWORK -> "Could not reach the server: ${e.message}"
                        ApiException.Kind.SERVER -> e.message ?: "The server answered with an error."
                    },
                )
            }
        }
    }

    private fun save(config: ConnectionConfig, viewModel: AppViewModel) {
        val problem = UrlPolicy.validate(config.baseUrl, allowCleartext = BuildConfig.DEBUG)
        if (problem != null) {
            connectionStatus.value = ConnectionStatus.Problem(problem.message)
            return
        }
        lifecycleScope.launch {
            container.settings.saveConnection(config)
            container.syncScheduler.ensurePeriodic()
            viewModel.refresh(sync = true, force = true)
            connectionStatus.value = ConnectionStatus.Ok("Saved.")
        }
    }

    private companion object {
        val VOLUME_KEYS = setOf(
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_PAGE_DOWN,
        )
    }
}
