package com.biblia.estudio.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

enum class ThemeMode(val label: String) { SYSTEM("Sistema"), LIGHT("Claro"), DARK("Oscuro"), SEPIA("Sepia") }

data class AppSettings(
    val version: String = "RVR1960",
    val parallelVersion: String = "NBLA",
    val parallel: Boolean = false,
    val fontSize: Float = 18f,
    val lineHeight: Float = 1.5f,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val showHeadings: Boolean = true,
    val showVerseNumbers: Boolean = true,
    val showNoteMarkers: Boolean = true,
    val notifications: Boolean = true,
    val notifyHour: Int = 7,
    val notifyMinute: Int = 0,
    val lastBook: Int = 43,
    val lastChapter: Int = 1,
    val compareVersions: String = "",
)

class SettingsRepository(private val context: Context) {
    private object K {
        val version = stringPreferencesKey("version")
        val parallelVersion = stringPreferencesKey("parallel_version")
        val parallel = booleanPreferencesKey("parallel")
        val fontSize = floatPreferencesKey("font_size")
        val lineHeight = floatPreferencesKey("line_height")
        val theme = stringPreferencesKey("theme")
        val showHeadings = booleanPreferencesKey("show_headings")
        val showVerseNumbers = booleanPreferencesKey("show_numbers")
        val showNoteMarkers = booleanPreferencesKey("show_note_markers")
        val notifications = booleanPreferencesKey("notifications")
        val notifyHour = intPreferencesKey("notify_hour")
        val notifyMinute = intPreferencesKey("notify_minute")
        val lastBook = intPreferencesKey("last_book")
        val lastChapter = intPreferencesKey("last_chapter")
        val compareVersions = stringPreferencesKey("compare_versions")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p -> p.toSettings() }

    private fun Preferences.toSettings() = AppSettings(
        version = this[K.version] ?: "RVR1960",
        parallelVersion = this[K.parallelVersion] ?: "NBLA",
        parallel = this[K.parallel] ?: false,
        fontSize = this[K.fontSize] ?: 18f,
        lineHeight = this[K.lineHeight] ?: 1.5f,
        theme = runCatching { ThemeMode.valueOf(this[K.theme] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
        showHeadings = this[K.showHeadings] ?: true,
        showVerseNumbers = this[K.showVerseNumbers] ?: true,
        showNoteMarkers = this[K.showNoteMarkers] ?: true,
        notifications = this[K.notifications] ?: true,
        notifyHour = this[K.notifyHour] ?: 7,
        notifyMinute = this[K.notifyMinute] ?: 0,
        lastBook = this[K.lastBook] ?: 43,
        lastChapter = this[K.lastChapter] ?: 1,
        compareVersions = this[K.compareVersions] ?: "",
    )

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val s = transform(p.toSettings())
            p[K.version] = s.version; p[K.parallelVersion] = s.parallelVersion; p[K.parallel] = s.parallel
            p[K.fontSize] = s.fontSize; p[K.lineHeight] = s.lineHeight; p[K.theme] = s.theme.name
            p[K.showHeadings] = s.showHeadings; p[K.showVerseNumbers] = s.showVerseNumbers; p[K.showNoteMarkers] = s.showNoteMarkers
            p[K.notifications] = s.notifications; p[K.notifyHour] = s.notifyHour; p[K.notifyMinute] = s.notifyMinute
            p[K.lastBook] = s.lastBook; p[K.lastChapter] = s.lastChapter; p[K.compareVersions] = s.compareVersions
        }
    }
}
