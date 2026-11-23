package com.biblia.estudio.domain

import com.biblia.estudio.data.content.PassageRef

/** Un fragmento de texto reconocido como referencia bíblica. */
data class RefSpan(val start: Int, val end: Int, val ref: PassageRef)

/**
 * Reconoce referencias bíblicas en español dentro de un texto, p. ej.:
 * "Gn. 1:1–3", "1 Co. 4.6", "Mt. 8:4; Mr. 12:26", "Sal. 90:2; 148:5", "(cp. 6:32, 51)" y "vv. 3, 4".
 * Las referencias sin libro se resuelven con el libro/capítulo de contexto.
 */
class RefParser(abbreviations: Map<String, Int>) {
    private val abbr: Map<String, Int> = abbreviations.mapKeys { norm(it.key) }
    private val bookPattern: String = abbreviations.keys.map { norm(it) }.distinct().sortedByDescending { it.length }
        .joinToString("|") { a -> a.replace(Regex("[^\\p{L}\\d ]"), "").replace(" ", "\\s?") }

    private val cv = "(\\d{1,3})[:.](\\d{1,3})(?:\\s?[–-]\\s?(\\d{1,3})(?:[:.](\\d{1,3}))?)?"
    private val bookRef = Regex("(?<![\\p{L}\\d])($bookPattern)\\.?\\s?(\\d{1,3})(?:[:.](\\d{1,3}))?(?:\\s?[–-]\\s?(\\d{1,3})(?:[:.](\\d{1,3}))?)?", RegexOption.IGNORE_CASE)
    private val bareRef = Regex("(?<![\\p{L}\\d:.])$cv")
    private val versesRef = Regex("\\bvv?\\.\\s?(\\d{1,3})(?:\\s?[–-]\\s?(\\d{1,3}))?")
    private val continuation = Regex("^\\s*[;,]\\s*$cv")
    private val verseCont = Regex("^\\s*,\\s?(\\d{1,3})(?![\\d:.])")

    private fun norm(s: String) = s.lowercase().replace(".", "").trim()

    /** Abdías, Filemón, 2 Juan, 3 Juan y Judas tienen un solo capítulo: «Jud. 14» es el versículo 14. */
    private val singleChapter = setOf(31, 57, 63, 64, 65)

    private fun mk(book: Int, c: Int, v: Int?, a: String?, b: String?): PassageRef {
        if (book in singleChapter && v == null) {
            val end = a?.toIntOrNull() ?: c
            return PassageRef(book, 1, c, 1, end)
        }
        val verse = v ?: 0
        return when {
            a != null && b != null -> PassageRef(book, c, verse, a.toInt(), b.toInt())
            a != null && v != null -> PassageRef(book, c, verse, c, a.toInt())
            a != null -> PassageRef(book, c, 0, a.toInt(), 0)
            else -> PassageRef(book, c, verse, c, verse)
        }
    }

    fun parse(text: String, ctxBook: Int = 0, ctxChapter: Int = 0): List<RefSpan> {
        val out = mutableListOf<RefSpan>()
        val taken = BooleanArray(text.length + 1)
        fun add(start: Int, end: Int, ref: PassageRef) {
            if ((start until end).any { taken[it] }) return
            for (i in start until end) taken[i] = true
            out.add(RefSpan(start, end, ref))
        }
        for (m in bookRef.findAll(text)) {
            val book = abbr[norm(m.groupValues[1])] ?: continue
            val c = m.groupValues[2].toInt()
            val v = m.groupValues[3].ifEmpty { null }?.toInt()
            val ref = mk(book, c, v, m.groupValues[4].ifEmpty { null }, m.groupValues[5].ifEmpty { null })
            add(m.range.first, m.range.last + 1, ref)
            // continuaciones "; 12:3" y ", 5" del mismo libro
            var pos = m.range.last + 1; var curCh = ref.chapterEnd
            while (pos < text.length) {
                val rest = text.substring(pos)
                val cm = continuation.find(rest)
                if (cm != null && cm.range.first == 0) {
                    val g = cm.groupValues
                    val r = mk(book, g[1].toInt(), g[2].toInt(), g[3].ifEmpty { null }, g[4].ifEmpty { null })
                    val s = pos + cm.groups[1]!!.range.first
                    add(s, pos + cm.range.last + 1, r); curCh = r.chapterEnd; pos += cm.range.last + 1; continue
                }
                val vm = verseCont.find(rest)
                if (vm != null && vm.range.first == 0 && v != null) {
                    val vv = vm.groupValues[1].toInt()
                    add(pos + vm.groups[1]!!.range.first, pos + vm.range.last + 1, PassageRef(book, curCh, vv, curCh, vv))
                    pos += vm.range.last + 1; continue
                }
                break
            }
        }
        if (ctxBook > 0) {
            for (m in bareRef.findAll(text)) {
                val g = m.groupValues
                add(m.range.first, m.range.last + 1, mk(ctxBook, g[1].toInt(), g[2].toInt(), g[3].ifEmpty { null }, g[4].ifEmpty { null }))
            }
            if (ctxChapter > 0) for (m in versesRef.findAll(text)) {
                val a = m.groupValues[1].toInt(); val b = m.groupValues[2].ifEmpty { null }?.toInt() ?: a
                add(m.range.first, m.range.last + 1, PassageRef(ctxBook, ctxChapter, a, ctxChapter, b))
            }
        }
        return out.sortedBy { it.start }
    }

    /** Interpreta lo que el usuario escribe en el buscador, p. ej. "juan 3:16" o "Ro 8". */
    fun parseQuery(q: String): PassageRef? {
        val m = bookRef.find(q.trim()) ?: return null
        if (m.range.first != 0) return null
        val book = abbr[norm(m.groupValues[1])] ?: return null
        return mk(book, m.groupValues[2].toInt(), m.groupValues[3].ifEmpty { null }?.toInt(), m.groupValues[4].ifEmpty { null }, m.groupValues[5].ifEmpty { null })
    }
}
