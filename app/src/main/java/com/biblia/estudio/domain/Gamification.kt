package com.biblia.estudio.domain

import com.biblia.estudio.data.user.ActivityDay

data class Level(val number: Int, val name: String, val min: Int, val next: Int?)

object Gamification {
    private val levels = listOf(
        0 to "Buscador", 100 to "Oyente", 300 to "Discípulo", 700 to "Lector fiel", 1500 to "Estudiante de la Palabra",
        3000 to "Obrero aprobado", 6000 to "Maestro", 10000 to "Escriba del reino", 16000 to "Atalaya", 25000 to "Anciano sabio"
    )

    fun level(points: Int): Level {
        val idx = levels.indexOfLast { points >= it.first }.coerceAtLeast(0)
        return Level(idx + 1, levels[idx].second, levels[idx].first, levels.getOrNull(idx + 1)?.first)
    }

    /** Días consecutivos con actividad, contando hasta hoy (o hasta ayer si hoy aún no hay actividad). */
    fun streak(activity: List<ActivityDay>, today: Long): Int {
        val days = activity.filter { it.points > 0 }.map { it.epochDay }.toSet()
        var d = if (today in days) today else today - 1
        var n = 0
        while (d in days) { n++; d-- }
        return n
    }

    fun bestStreak(activity: List<ActivityDay>): Int {
        val days = activity.filter { it.points > 0 }.map { it.epochDay }.sorted()
        var best = 0; var cur = 0; var prev = Long.MIN_VALUE
        for (d in days) { cur = if (d == prev + 1) cur + 1 else 1; best = maxOf(best, cur); prev = d }
        return best
    }
}

data class Achievement(val title: String, val description: String, val icon: String, val unlocked: Boolean, val progress: Float)

data class Stats(
    val chaptersRead: Int, val otRead: Int, val ntRead: Int, val booksCompleted: Int, val streak: Int, val bestStreak: Int,
    val points: Int, val quizDays: Int, val perfectQuizzes: Int, val highlights: Int, val topics: Int, val notes: Int,
    val studies: Int, val devotionals: Int, val plansCompleted: Int
)

object Achievements {
    fun compute(s: Stats): List<Achievement> {
        fun a(t: String, d: String, icon: String, value: Int, goal: Int) =
            Achievement(t, d, icon, value >= goal, (value.toFloat() / goal).coerceIn(0f, 1f))
        return listOf(
            a("Primer paso", "Lee tu primer capítulo", "📖", s.chaptersRead, 1),
            a("Lector constante", "Lee 50 capítulos", "📚", s.chaptersRead, 50),
            a("Centurión", "Lee 250 capítulos", "🛡️", s.chaptersRead, 250),
            a("Nuevo Testamento", "Lee los 260 capítulos del NT", "✝️", s.ntRead, 260),
            a("Antiguo Testamento", "Lee los 929 capítulos del AT", "📜", s.otRead, 929),
            a("Toda la Escritura", "Lee los 1.189 capítulos de la Biblia", "👑", s.chaptersRead, 1189),
            a("Libro completo", "Termina un libro de la Biblia", "✅", s.booksCompleted, 1),
            a("Diez libros", "Termina 10 libros", "🏅", s.booksCompleted, 10),
            a("Una semana", "Racha de 7 días", "🔥", s.bestStreak, 7),
            a("Un mes fiel", "Racha de 30 días", "🌟", s.bestStreak, 30),
            a("Cien días", "Racha de 100 días", "💎", s.bestStreak, 100),
            a("Curioso", "Completa 7 cuestionarios diarios", "❓", s.quizDays, 7),
            a("Perfecto", "Logra 10 cuestionarios perfectos", "🎯", s.perfectQuizzes, 10),
            a("Marcador", "Resalta 25 versículos", "🖍️", s.highlights, 25),
            a("Organizador", "Crea 5 temas", "🗂️", s.topics, 5),
            a("Escritor", "Escribe 20 notas personales", "✍️", s.notes, 20),
            a("Exégeta", "Guarda 5 estudios bíblicos", "🔎", s.studies, 5),
            a("Devoto", "Completa 50 elementos devocionales", "🙏", s.devotionals, 50),
            a("Plan cumplido", "Completa un plan de lectura", "🏁", s.plansCompleted, 1),
            a("Mil puntos", "Alcanza 1.000 puntos", "⭐", s.points, 1000),
        )
    }
}
