package io.github.zigorminsk.spoty.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.zigorminsk.spoty.BuildConfig
import io.github.zigorminsk.spoty.data.model.SourceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class AppSettings(
    val enabledSources: Set<String> = SourceId.entries.map { it.key }.toSet(),
    val jamendoClientId: String = BuildConfig.DEFAULT_JAMENDO_CLIENT_ID,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val openLicenseOnly: Boolean = false,
    val wifiOnlyDownloads: Boolean = true,
    val autoSync: Boolean = true,
    val cacheMb: Int = 512,
    val lastSyncAt: Long = 0L,
)

/** Снимок состояния плеера для возобновления после перезапуска. */
data class PlaybackSnapshot(
    val index: Int = 0,
    val positionMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = 0,
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context, scope: CoroutineScope) {
    private object Keys {
        val sources = stringSetPreferencesKey("sources")
        val jamendo = stringPreferencesKey("jamendo_client_id")
        val theme = intPreferencesKey("theme_mode")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val openOnly = booleanPreferencesKey("open_license_only")
        val wifiOnly = booleanPreferencesKey("wifi_only_downloads")
        val autoSync = booleanPreferencesKey("auto_sync")
        val cacheMb = intPreferencesKey("cache_mb")
        val lastSync = longPreferencesKey("last_sync")
        val pbIndex = intPreferencesKey("pb_index")
        val pbPosition = longPreferencesKey("pb_position")
        val pbShuffle = booleanPreferencesKey("pb_shuffle")
        val pbRepeat = intPreferencesKey("pb_repeat")
    }

    private val store get() = context.settingsDataStore

    /** Поток настроек и их «синхронное» текущее значение ([value]). */
    val state: StateFlow<AppSettings> = store.data.map { p ->
        val defaults = AppSettings()
        AppSettings(
            enabledSources = p[Keys.sources] ?: defaults.enabledSources,
            jamendoClientId = p[Keys.jamendo] ?: defaults.jamendoClientId,
            themeMode = ThemeMode.entries.getOrElse(p[Keys.theme] ?: defaults.themeMode.ordinal) { defaults.themeMode },
            dynamicColor = p[Keys.dynamic] ?: defaults.dynamicColor,
            openLicenseOnly = p[Keys.openOnly] ?: defaults.openLicenseOnly,
            wifiOnlyDownloads = p[Keys.wifiOnly] ?: defaults.wifiOnlyDownloads,
            autoSync = p[Keys.autoSync] ?: defaults.autoSync,
            cacheMb = p[Keys.cacheMb] ?: defaults.cacheMb,
            lastSyncAt = p[Keys.lastSync] ?: defaults.lastSyncAt,
        )
    }.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    val value: AppSettings get() = state.value

    suspend fun setSourceEnabled(source: SourceId, enabled: Boolean) {
        store.edit { p ->
            val current = p[Keys.sources] ?: AppSettings().enabledSources
            p[Keys.sources] = if (enabled) current + source.key else current - source.key
        }
    }

    suspend fun setJamendoClientId(value: String) = store.edit { it[Keys.jamendo] = value.trim() }.let { }
    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[Keys.theme] = mode.ordinal }.let { }
    suspend fun setDynamicColor(enabled: Boolean) = store.edit { it[Keys.dynamic] = enabled }.let { }
    suspend fun setOpenLicenseOnly(enabled: Boolean) = store.edit { it[Keys.openOnly] = enabled }.let { }
    suspend fun setWifiOnlyDownloads(enabled: Boolean) = store.edit { it[Keys.wifiOnly] = enabled }.let { }
    suspend fun setAutoSync(enabled: Boolean) = store.edit { it[Keys.autoSync] = enabled }.let { }
    suspend fun setCacheMb(value: Int) = store.edit { it[Keys.cacheMb] = value }.let { }
    suspend fun setLastSync(value: Long) = store.edit { it[Keys.lastSync] = value }.let { }

    suspend fun savePlayback(snapshot: PlaybackSnapshot) {
        store.edit { p ->
            p[Keys.pbIndex] = snapshot.index
            p[Keys.pbPosition] = snapshot.positionMs
            p[Keys.pbShuffle] = snapshot.shuffle
            p[Keys.pbRepeat] = snapshot.repeatMode
        }
    }

    suspend fun loadPlayback(): PlaybackSnapshot {
        val p = store.data.first()
        return PlaybackSnapshot(
            index = p[Keys.pbIndex] ?: 0,
            positionMs = p[Keys.pbPosition] ?: 0L,
            shuffle = p[Keys.pbShuffle] ?: false,
            repeatMode = p[Keys.pbRepeat] ?: 0,
        )
    }
}
