package com.biblia.estudio.ui.components

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Aviso «Eliminado · Deshacer» que muestra AppRoot en la parte inferior de cualquier pantalla. */
class UndoEvent(val message: String, val undo: suspend () -> Unit)

object Undo {
    private val _events = MutableSharedFlow<UndoEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<UndoEvent> = _events
    /** Avisa de un borrado ya hecho y ofrece deshacerlo durante unos segundos. */
    fun offer(message: String, undo: suspend () -> Unit) { _events.tryEmit(UndoEvent(message, undo)) }
}
