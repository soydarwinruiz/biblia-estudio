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

    private var ready = false
    private var pending: Triple<String, List<String>, Int>? = null
    /** Índice global del último fragmento de la lectura en curso. */
    @Volatile private var lastIndex = -1
    @Volatile private var activeKey: String? = null
    private var localeTag = ""
    var rate: Float = 1f; private set

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            applyLocale()
            pending?.let { (k, p, o) -> speak(k, p, o) }; pending = null
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) { _current.value = id.substringAfterLast("#").toIntOrNull() ?: -1 }
            override fun onDone(id: String) {
                val key = id.substringBeforeLast("#"); val idx = id.substringAfterLast("#").toIntOrNull() ?: return
                if (key == activeKey && idx == lastIndex) {
                    _speaking.value = null; _current.value = -1; activeKey = null
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

    /**
     * Lee una lista de fragmentos (p. ej. versículos) en orden. [offset] = índice del primer fragmento
     * dentro de la lectura completa (para seguir donde se pausó sin perder la numeración).
     */
    fun speak(key: String, parts: List<String>, offset: Int = 0) {
        if (!ready) { pending = Triple(key, parts, offset); _speaking.value = key; return }
        activeKey = null
        tts.stop(); tts.setSpeechRate(rate)
        if (parts.isEmpty()) { _speaking.value = null; return }
        activeKey = key; lastIndex = offset + parts.lastIndex
        _speaking.value = key
        parts.forEachIndexed { i, p -> tts.speak(p, TextToSpeech.QUEUE_ADD, null, "$key#${offset + i}") }
    }

    fun toggle(key: String, parts: List<String>) { if (_speaking.value == key) stop() else speak(key, parts) }

    fun stop() { pending = null; activeKey = null; tts.stop(); _speaking.value = null; _current.value = -1 }

}
