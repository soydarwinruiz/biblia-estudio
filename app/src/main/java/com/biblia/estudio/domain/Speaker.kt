package com.biblia.estudio.domain

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private var ready = false
    private var pending: Pair<String, List<String>>? = null
    var rate: Float = 1f

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            val r = tts.setLanguage(Locale("es", "ES"))
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale("es"))
            pending?.let { (k, p) -> speak(k, p) }; pending = null
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) { _current.value = id.substringAfterLast(":").toIntOrNull() ?: -1 }
            override fun onDone(id: String) { if (id.endsWith(":last")) { _speaking.value = null; _current.value = -1 } }
            @Deprecated("Deprecated in Java") override fun onError(id: String) { _speaking.value = null; _current.value = -1 }
            override fun onStop(id: String, interrupted: Boolean) { _current.value = -1 }
        })
    }

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
}
