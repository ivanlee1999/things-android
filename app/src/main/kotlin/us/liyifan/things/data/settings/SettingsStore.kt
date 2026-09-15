package us.liyifan.things.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Everything the app remembers about itself: where the server is, what it looks like, and when
 * it last heard from the backend.
 *
 * The three secrets are held as ciphertext from [KeystoreCipher]; the rest is plain. One store
 * rather than two so a single read gives a screen everything it needs.
 */
class SettingsStore(
    context: Context,
    private val cipher: KeystoreCipher = KeystoreCipher(),
) {
    private val store = context.applicationContext.dataStore

    val connection: Flow<ConnectionConfig> = store.data.map { p ->
        ConnectionConfig(
            baseUrl = p[BASE_URL].orEmpty(),
            apiKey = p[API_KEY]?.let { cipher.decrypt(it) }.orEmpty(),
            cfAccessClientId = p[CF_ID]?.let { cipher.decrypt(it) }.orEmpty(),
            cfAccessClientSecret = p[CF_SECRET]?.let { cipher.decrypt(it) }.orEmpty(),
        )
    }

    val appearance: Flow<Appearance> = store.data.map { p ->
        Appearance(
            theme = ThemeMode.fromWire(p[THEME]),
            eink = EinkConfig(
                enabled = p[EINK] ?: false,
                colorMode = EinkColorMode.fromWire(p[EINK_COLOR]),
                fullRefresh = p[EINK_FULL_REFRESH] ?: true,
            ),
        )
    }

    val lastSyncedAt: Flow<Long> = store.data.map { it[LAST_SYNCED_AT] ?: 0L }

    suspend fun saveConnection(config: ConnectionConfig) {
        store.edit { p ->
            p[BASE_URL] = config.baseUrl.trim().trimEnd('/')
            cipher.encrypt(config.apiKey)?.let { p[API_KEY] = it }
            cipher.encrypt(config.cfAccessClientId)?.let { p[CF_ID] = it }
            cipher.encrypt(config.cfAccessClientSecret)?.let { p[CF_SECRET] = it }
        }
    }

    suspend fun saveAppearance(appearance: Appearance) {
        store.edit { p ->
            p[THEME] = appearance.theme.wire
            p[EINK] = appearance.eink.enabled
            p[EINK_COLOR] = appearance.eink.colorMode.wire
            p[EINK_FULL_REFRESH] = appearance.eink.fullRefresh
        }
    }

    suspend fun setLastSyncedAt(epochMillis: Long) {
        store.edit { it[LAST_SYNCED_AT] = epochMillis }
    }

    /** Forgets the server and its credentials, leaving the look alone. */
    suspend fun clearConnection() {
        store.edit { p ->
            p.remove(BASE_URL); p.remove(API_KEY); p.remove(CF_ID); p.remove(CF_SECRET)
            p.remove(LAST_SYNCED_AT)
        }
    }

    private companion object {
        val BASE_URL = stringPreferencesKey("base_url")
        val API_KEY = stringPreferencesKey("api_key")
        val CF_ID = stringPreferencesKey("cf_access_client_id")
        val CF_SECRET = stringPreferencesKey("cf_access_client_secret")
        val THEME = stringPreferencesKey("theme_mode")
        val EINK = booleanPreferencesKey("eink_enabled")
        val EINK_COLOR = stringPreferencesKey("eink_color_mode")
        val EINK_FULL_REFRESH = booleanPreferencesKey("eink_full_refresh")
        val LAST_SYNCED_AT = longPreferencesKey("last_synced_at")
    }
}

data class Appearance(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val eink: EinkConfig = EinkConfig(),
)

enum class ThemeMode(val wire: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        fun fromWire(s: String?) = entries.firstOrNull { it.wire == s } ?: SYSTEM
    }
}

/**
 * E-ink mode, off by default and turned on by hand in Settings.
 *
 * It is not auto-detected: a BOOX is still an Android phone, and which of the two looks someone
 * wants on it is a preference, not a fact about the hardware.
 */
data class EinkConfig(
    val enabled: Boolean = false,
    val colorMode: EinkColorMode = EinkColorMode.COLOR_ACCENTS,
    /** Ask the panel for a full repaint after navigating, to clear ghosting. BOOX only. */
    val fullRefresh: Boolean = true,
)

enum class EinkColorMode(val wire: String) {
    /** Everything black on white — the safest reading on any e-paper. */
    MONOCHROME("mono"),

    /** Keep Things' list colours; a Kaleido panel shows them, muted but legible. */
    COLOR_ACCENTS("color");

    companion object {
        fun fromWire(s: String?) = entries.firstOrNull { it.wire == s } ?: COLOR_ACCENTS
    }
}
