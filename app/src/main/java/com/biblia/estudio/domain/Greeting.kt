package com.biblia.estudio.domain

import java.time.LocalTime

/** Saludo según la hora del día, con el nombre si lo hay: «Buenos días, Ana». */
object Greeting {
    fun forTime(time: LocalTime, name: String): String {
        val base = when (time.hour) {
            in 5..11 -> "Buenos días"
            in 12..18 -> "Buenas tardes"
            else -> "Buenas noches"
        }
        val n = name.trim()
        return if (n.isEmpty()) base else "$base, $n"
    }

    fun now(name: String) = forTime(LocalTime.now(), name)
}
