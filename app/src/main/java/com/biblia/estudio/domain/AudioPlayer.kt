package com.biblia.estudio.domain

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import com.biblia.estudio.data.content.ContentRepository
import com.biblia.estudio.data.prefs.SettingsRepository
import com.biblia.estudio.work.AudioService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Capítulo que se está escuchando. [index] = fragmento actual (0 = título, 1.. = versículos). */
data class AudioState(val book: Int, val chapter: Int, val version: String, val playing: Boolean, val index: Int = 0) {
    val key get() = "cap:$book:$chapter:$version"
}

/**
 * Lectura de capítulos en voz alta, independiente de la pantalla: pausar y seguir donde se quedó,
 * capítulo anterior/siguiente, continuar solo con el siguiente, temporizador para dormir y
 * pausa automática si entra una llamada u otra app reproduce sonido.
 * El servicio [AudioService] la mantiene viva con la pantalla apagada y muestra los controles.
 */
class AudioPlayer(private val context: Context, private val content: ContentRepository, private val settings: SettingsRepository, private val speaker: Speaker) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow<AudioState?>(null)
    val state: StateFlow<AudioState?> = _state
    private val _sleepAt = MutableStateFlow(0L)
    /** Hora (millis) en que se detendrá la lectura, o 0 si no hay temporizador. */
    val sleepAt: StateFlow<Long> = _sleepAt
    private var sleepJob: Job? = null
    private val am = context.getSystemService(AudioManager::class.java)
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setOnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> scope.launch { pause() }
            }
        }.build()

    init {
        scope.launch { speaker.finished.collect { key -> val s = _state.value; if (s != null && s.playing && key == s.key) onChapterEnd() } }
        // Otra lectura (versículo del día, memorizar…) tomó la voz: el capítulo queda en pausa
        scope.launch { speaker.speaking.collect { k -> val s = _state.value; if (s != null && s.playing && k != null && k != s.key) _state.value = s.copy(playing = false) } }
        scope.launch { speaker.current.collect { i -> val s = _state.value; if (s != null && s.playing && i >= 0 && speaker.speaking.value == s.key) _state.value = s.copy(index = i) } }
    }

    private suspend fun parts(book: Int, chapter: Int, version: String): List<String> =
        listOf("${content.book(book).name}, capítulo $chapter.") + content.chapter(version, book, chapter).map { it.text }

    fun label(s: AudioState) = "${content.book(s.book).name} ${s.chapter}"

    /** Empieza (o retoma) un capítulo desde el fragmento [from]. */
    fun play(book: Int, chapter: Int, version: String, from: Int = 0) {
        scope.launch {
            val p = parts(book, chapter, version)
            val start = from.coerceIn(0, p.lastIndex)
            _state.value = AudioState(book, chapter, version, true, start)
            am.requestAudioFocus(focus)
            speaker.speak("cap:$book:$chapter:$version", p.drop(start), start)
            runCatching { AudioService.start(context) }
        }
    }

    fun pause() {
        val s = _state.value ?: return
        if (!s.playing) return
        _state.value = s.copy(playing = false)
        speaker.stop()
    }

    fun resume() { val s = _state.value ?: return; if (!s.playing) play(s.book, s.chapter, s.version, s.index) }

    fun toggle() { val s = _state.value ?: return; if (s.playing) pause() else resume() }

    private fun nextOf(b: Int, c: Int) = if (c < content.book(b).chapters) b to c + 1 else if (b < 66) b + 1 to 1 else null
    private fun prevOf(b: Int, c: Int) = if (c > 1) b to c - 1 else if (b > 1) b - 1 to content.book(b - 1).chapters else null

    fun next() { val s = _state.value ?: return; nextOf(s.book, s.chapter)?.let { play(it.first, it.second, s.version) } ?: stop() }
    fun previous() { val s = _state.value ?: return; prevOf(s.book, s.chapter)?.let { play(it.first, it.second, s.version) } }

    fun stop() {
        speaker.stop()
        _state.value = null
        sleepJob?.cancel(); _sleepAt.value = 0
        am.abandonAudioFocusRequest(focus)
        runCatching { AudioService.stop(context) }
    }

    private suspend fun onChapterEnd() {
        if (settings.current().audioContinue) next()
        else { val s = _state.value ?: return; _state.value = s.copy(playing = false, index = 0); am.abandonAudioFocusRequest(focus) }
    }

    /** Temporizador para dormir: detiene la lectura dentro de [minutes] minutos (0 = cancelar). */
    fun sleepIn(minutes: Int) {
        sleepJob?.cancel(); _sleepAt.value = 0
        if (minutes <= 0) return
        _sleepAt.value = System.currentTimeMillis() + minutes * 60_000L
        sleepJob = scope.launch { delay(minutes * 60_000L); _sleepAt.value = 0; stop() }
    }
}
