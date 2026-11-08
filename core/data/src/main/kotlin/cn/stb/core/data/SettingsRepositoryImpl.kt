package cn.stb.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cn.stb.core.model.AppSettings
import cn.stb.core.model.DarkMode
import cn.stb.core.model.HomePref
import cn.stb.core.model.SettingsRepository
import cn.stb.core.model.ThemePref
import cn.stb.core.model.VisibilityPref
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 个性化配置的持久化。
 *
 * 只存「意图」：用户置顶了谁、隐藏了谁、选了什么颜色。
 * 首页长什么样由 HomeComposer 每次实时算出来，绝不把计算结果写进存储——
 * 否则一旦内容升级导致工具增减，存下来的快照就会和现实对不上。
 */
class SettingsRepositoryImpl(context: Context) : SettingsRepository {

    private val appContext = context.applicationContext

    private object Keys {
        val seed = longPreferencesKey("theme_seed")
        val dark = stringPreferencesKey("theme_dark")
        val fontScale = floatPreferencesKey("font_scale")
        val pinned = stringPreferencesKey("pinned_tools")
        val showRecents = booleanPreferencesKey("show_recents")
        val hiddenSubjects = stringPreferencesKey("hidden_subjects")
        val hiddenTools = stringPreferencesKey("hidden_tools")
        val schema = intPreferencesKey("schema_version")
        val seeded = booleanPreferencesKey("seeded")
    }

    override val settings: Flow<AppSettings> =
        appContext.settingsStore.data.map { it.toSettings() }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        appContext.settingsStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[Keys.seed] = next.theme.seedArgb
            prefs[Keys.dark] = next.theme.darkMode.name
            prefs[Keys.fontScale] = next.theme.fontScale
            prefs[Keys.pinned] = next.home.pinnedToolIds.joinToString(SEP)
            prefs[Keys.showRecents] = next.home.showRecents
            prefs[Keys.hiddenSubjects] = next.visibility.hiddenSubjectIds.joinToString(SEP)
            prefs[Keys.hiddenTools] = next.visibility.hiddenToolIds.joinToString(SEP)
            prefs[Keys.schema] = next.schemaVersion
            prefs[Keys.seeded] = next.seeded
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val theme = ThemePref(
            seedArgb = this[Keys.seed] ?: 0xFF2F6B4F,
            darkMode = runCatching {
                DarkMode.valueOf(this[Keys.dark] ?: DarkMode.FOLLOW_SYSTEM.name)
            }.getOrDefault(DarkMode.FOLLOW_SYSTEM),
            fontScale = this[Keys.fontScale] ?: 1f,
        )
        val home = HomePref(
            pinnedToolIds = decode(this[Keys.pinned]),
            showRecents = this[Keys.showRecents] ?: false,
        )
        val visibility = VisibilityPref(
            hiddenSubjectIds = decode(this[Keys.hiddenSubjects]).toSet(),
            hiddenToolIds = decode(this[Keys.hiddenTools]).toSet(),
        )
        return AppSettings(
            theme = theme,
            home = home,
            visibility = visibility,
            schemaVersion = this[Keys.schema] ?: AppSettings.CURRENT_SCHEMA,
            seeded = this[Keys.seeded] ?: false,
        )
    }

    private fun decode(raw: String?): List<String> =
        raw?.split(SEP)?.filter { it.isNotBlank() } ?: emptyList()

    private companion object {
        // 工具 id 只含小写字母、数字与点，用竖线分隔不会冲突
        const val SEP = "|"
    }
}
