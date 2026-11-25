package com.biblia.estudio.domain

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Lectura en voz alta con el motor de texto a voz del teléfono (funciona sin conexión
 * si la voz en español está descargada en el dispositivo).
 */
class Speaker(context: Context) {
    private val _speaking = MutableStateFlow<String?>(null)   // clave de lo que se está leyendo
    val speaking: StateFlow<String?> = _speaking
    private val _current = MutableStateFlow(-1)               // índice del fragmento actual
    val current: StateFlow<Int> = _current
    /** Emite la clave de cada lectura que termina completa (no si se detiene). */
    private val _finished = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val finished: SharedFlow<String> = _finished
    /** Hora (millis) en que se apagará la lectura, o 0 si no hay temporizador. */
    private val _sleepAt = MutableStateFlow(0L)
    val sleepAt: StateFlow<Long> = _sleepAt

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var sleepJob: Job? = null
    private var ready = false
    private var pending: Pair<String, List<String>>? = null
    private var localeTag = ""
    var rate: Float = 1f; private set

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            applyLocale()
            pending?.let { (k, p) -> speak(k, p) }; pending = null
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) { _current.value = id.substringAfterLast(":").toIntOrNull() ?: -1 }
            override fun onDone(id: String) {
                if (id.endsWith(":last")) {
                    val key = id.removeSuffix(":last")
                    _speaking.value = null; _current.value = -1
                    _finished.tryEmit(key)
                }
            }
            @Deprecated("Deprecated in Java") override fun onError(id: String) { _speaking.value = null; _current.value = -1 }
            override fun onStop(id: String, interrupted: Boolean) { _current.value = -1 }
        })
    }

    private fun applyLocale() {
        val wanted = if (localeTag.isBlank()) Locale.getDefault().takeIf { it.language == "es" } ?: Locale("es", "ES") else Locale.forLanguageTag(localeTag)
        val r = tts.setLanguage(wanted)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale("es"))
    }

    /** Cambia la voz (etiqueta BCP-47, p. ej. "es-MX"; "" = predeterminada) y la velocidad (0.5–2). */
    fun configure(locale: String, speechRate: Float) {
        localeTag = locale; rate = speechRate
        if (ready) { applyLocale(); tts.setSpeechRate(rate) }
    }

    /** Variantes de español instaladas en el motor de voz del teléfono. */
    fun availableSpanish(): List<Locale> =
        if (!ready) emptyList() else runCatching { tts.availableLanguages.filter { it.language == "es" }.sortedBy { it.displayCountry } }.getOrDefault(emptyList())

    /** Lee una lista de fragmentos (p. ej. versículos) en orden. */
    fun speak(key: String, parts: List<String>) {
        if (!ready) { pending = key to parts; _speaking.value = key; return }
        tts.stop(); tts.setSpeechRate(rate)
        _speaking.value = key
        parts.forEachIndexed { i, p ->
            val id = if (i == parts.lastIndex) "$key:last" else "$key:$i"
            tts.speak(p, TextToSpeech.QUEUE_ADD, null, id)
        }
        if (parts.isEmpty()) _speaking.value = null
    }

    fun toggle(key: String, parts: List<String>) { if (_speaking.value == key) stop() else speak(key, parts) }

    fun stop() { pending = null; tts.stop(); _speaking.value = null; _current.value = -1 }

    /** Temporizador para dormir: detiene la lectura dentro de [minutes] minutos (0 = cancelar). */
    fun sleepIn(minutes: Int) {
        sleepJob?.cancel(); _sleepAt.value = 0
        if (minutes <= 0) return
        _sleepAt.value = System.currentTimeMillis() + minutes * 60_000L
        sleepJob = scope.launch { delay(minutes * 60_000L); _sleepAt.value = 0; stop() }
    }
}
