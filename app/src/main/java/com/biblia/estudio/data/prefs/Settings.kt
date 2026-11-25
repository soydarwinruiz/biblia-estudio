package com.biblia.estudio.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")
/** Datos que cambian a menudo (posición de lectura, último respaldo): van aparte para no redibujar toda la app. */
private val Context.stateStore by preferencesDataStore("state")

enum class ThemeMode(val label: String) { SYSTEM("Sistema"), LIGHT("Claro"), DARK("Oscuro"), SEPIA("Sepia") }

enum class ReaderFont(val label: String) { SERIF("Serif"), SANS("Sans serif") }

data class AppSettings(
    val version: String = "RVR1960",
    val parallelVersion: String = "NBLA",
    val parallel: Boolean = false,
    val fontSize: Float = 18f,
    val lineHeight: Float = 1.5f,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val readerFont: ReaderFont = ReaderFont.SERIF,
    val showHeadings: Boolean = true,
    val showVerseNumbers: Boolean = true,
    val showNoteMarkers: Boolean = true,
    val notifications: Boolean = true,
    val notifyHour: Int = 7,
    val notifyMinute: Int = 0,
    val compareVersions: String = "",
    val continuous: Boolean = false,
    /** Voz de lectura: "" = la predeterminada del teléfono; si no, una etiqueta BCP-47 como "es-MX". */
    val ttsLocale: String = "",
    val ttsRate: Float = 1f,
    /** Al terminar de leer un capítulo en voz alta, seguir con el siguiente. */
    val audioContinue: Boolean = false,
    /** Carpeta elegida para respaldos automáticos (URI de árbol SAF) y cada cuántos días (0 = desactivado). */
    val autoBackupUri: String = "",
    val autoBackupDays: Int = 0,
    val onboarded: Boolean = false,
)

data class ReadingPosition(val book: Int = 43, val chapter: Int = 1)

class SettingsRepository(private val context: Context) {
    private object K {
        val version = stringPreferencesKey("version")
        val parallelVersion = stringPreferencesKey("parallel_version")
        val parallel = booleanPreferencesKey("parallel")
        val fontSize = floatPreferencesKey("font_size")
        val lineHeight = floatPreferencesKey("line_height")
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val readerFont = stringPreferencesKey("reader_font")
        val showHeadings = booleanPreferencesKey("show_headings")
        val showVerseNumbers = booleanPreferencesKey("show_numbers")
        val showNoteMarkers = booleanPreferencesKey("show_note_markers")
        val notifications = booleanPreferencesKey("notifications")
        val notifyHour = intPreferencesKey("notify_hour")
        val notifyMinute = intPreferencesKey("notify_minute")
        val compareVersions = stringPreferencesKey("compare_versions")
        val continuous = booleanPreferencesKey("continuous")
        val ttsLocale = stringPreferencesKey("tts_locale")
        val ttsRate = floatPreferencesKey("tts_rate")
        val audioContinue = booleanPreferencesKey("audio_continue")
        val autoBackupUri = stringPreferencesKey("auto_backup_uri")
        val autoBackupDays = intPreferencesKey("auto_backup_days")
        val onboarded = booleanPreferencesKey("onboarded")
        // Antiguas: la posición vivía aquí (se migra a «state»).
        val lastBook = intPreferencesKey("last_book")
        val lastChapter = intPreferencesKey("last_chapter")
    }
    private object S {
        val lastBook = intPreferencesKey("last_book")
        val lastChapter = intPreferencesKey("last_chapter")
        val lastAutoBackup = longPreferencesKey("last_auto_backup")
        val recentSearches = stringPreferencesKey("recent_searches")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p -> p.toSettings() }.distinctUntilChanged()

    private fun Preferences.toSettings() = AppSettings(
        version = this[K.version] ?: "RVR1960",
        parallelVersion = this[K.parallelVersion] ?: "NBLA",
        parallel = this[K.parallel] ?: false,
        fontSize = this[K.fontSize] ?: 18f,
        lineHeight = this[K.lineHeight] ?: 1.5f,
        theme = runCatching { ThemeMode.valueOf(this[K.theme] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
        dynamicColor = this[K.dynamicColor] ?: false,
        readerFont = runCatching { ReaderFont.valueOf(this[K.readerFont] ?: "SERIF") }.getOrDefault(ReaderFont.SERIF),
        showHeadings = this[K.showHeadings] ?: true,
        showVerseNumbers = this[K.showVerseNumbers] ?: true,
        showNoteMarkers = this[K.showNoteMarkers] ?: true,
        notifications = this[K.notifications] ?: true,
        notifyHour = this[K.notifyHour] ?: 7,
        notifyMinute = this[K.notifyMinute] ?: 0,
        compareVersions = this[K.compareVersions] ?: "",
        continuous = this[K.continuous] ?: false,
        ttsLocale = this[K.ttsLocale] ?: "",
        ttsRate = this[K.ttsRate] ?: 1f,
        audioContinue = this[K.audioContinue] ?: false,
        autoBackupUri = this[K.autoBackupUri] ?: "",
        autoBackupDays = this[K.autoBackupDays] ?: 0,
        onboarded = this[K.onboarded] ?: false,
    )

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val s = transform(p.toSettings())
            p[K.version] = s.version; p[K.parallelVersion] = s.parallelVersion; p[K.parallel] = s.parallel
            p[K.fontSize] = s.fontSize; p[K.lineHeight] = s.lineHeight; p[K.theme] = s.theme.name
            p[K.dynamicColor] = s.dynamicColor; p[K.readerFont] = s.readerFont.name
            p[K.showHeadings] = s.showHeadings; p[K.showVerseNumbers] = s.showVerseNumbers; p[K.showNoteMarkers] = s.showNoteMarkers
            p[K.notifications] = s.notifications; p[K.notifyHour] = s.notifyHour; p[K.notifyMinute] = s.notifyMinute
            p[K.compareVersions] = s.compareVersions; p[K.continuous] = s.continuous
            p[K.ttsLocale] = s.ttsLocale; p[K.ttsRate] = s.ttsRate; p[K.audioContinue] = s.audioContinue
            p[K.autoBackupUri] = s.autoBackupUri; p[K.autoBackupDays] = s.autoBackupDays; p[K.onboarded] = s.onboarded
        }
    }

    // ---------------- Posición de lectura (almacén aparte) ----------------
    val position: Flow<ReadingPosition> = context.stateStore.data
        .map { p -> ReadingPosition(p[S.lastBook] ?: 43, p[S.lastChapter] ?: 1) }.distinctUntilChanged()

    suspend fun currentPosition(): ReadingPosition = position.first()

    suspend fun setPosition(book: Int, chapter: Int) {
        context.stateStore.edit { it[S.lastBook] = book; it[S.lastChapter] = chapter }
    }

    /** Copia la posición guardada por versiones anteriores (≤ 1.4) al nuevo almacén, una sola vez. */
    suspend fun migratePosition() {
        val old = context.dataStore.data.first()
        val b = old[K.lastBook] ?: return
        val c = old[K.lastChapter] ?: 1
        context.stateStore.edit { s -> if (s[S.lastBook] == null) { s[S.lastBook] = b; s[S.lastChapter] = c } }
        context.dataStore.edit { it.remove(K.lastBook); it.remove(K.lastChapter) }
    }

    val recentSearches: Flow<List<String>> = context.stateStore.data.map { p -> p[S.recentSearches]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList() }

    suspend fun addRecentSearch(q: String) {
        val t = q.trim(); if (t.length < 2) return
        context.stateStore.edit { p ->
            val cur = p[S.recentSearches]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
            p[S.recentSearches] = (listOf(t) + cur.filter { !it.equals(t, ignoreCase = true) }).take(12).joinToString("\n")
        }
    }

    suspend fun clearRecentSearches() { context.stateStore.edit { it.remove(S.recentSearches) } }

    suspend fun lastAutoBackup(): Long = context.stateStore.data.first()[S.lastAutoBackup] ?: 0L
    suspend fun setLastAutoBackup(time: Long) { context.stateStore.edit { it[S.lastAutoBackup] = time } }
}
