package com.biblia.estudio

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = BibliaApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ScreensTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/shots/$name.png")
    }

    /** Las preferencias persisten entre pruebas: empezar siempre en Juan 1 con tema claro. */
    private fun reset() = kotlinx.coroutines.runBlocking {
        val a = rule.activity.app
        a.settings.update { it.copy(theme = com.biblia.estudio.data.prefs.ThemeMode.LIGHT, onboarded = true, tourSeen = true, immersiveReader = false, todayCollapsed = "") }
        a.settings.setPosition(43, 1); a.position = com.biblia.estudio.data.prefs.ReadingPosition(43, 1)
    }

    /** La primera vez aparece la bienvenida: se captura y se marca como vista. */
    private fun start() {
        rule.waitUntil(60_000) {
            rule.onAllNodesWithText("Comenzar recorrido").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithText("Leer pasaje").fetchSemanticsNodes().isNotEmpty()
        }
        if (rule.onAllNodesWithText("Comenzar recorrido").fetchSemanticsNodes().isNotEmpty()) {
            shot("00_bienvenida")
            kotlinx.coroutines.runBlocking { rule.activity.app.settings.update { it.copy(onboarded = true, tourSeen = true) } }
        }
        waitText("Leer pasaje")
    }

    private fun waitText(text: String, substring: Boolean = false) {
        try {
            rule.waitUntil(60_000) { rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            rule.onRoot().printToLog("ARBOL")
            println(rule.onRoot().printToString(maxDepth = 30))
            rule.onRoot().captureRoboImage("build/shots/fallo_${text.take(12)}.png")
            throw e
        }
    }

    @Test fun walkThroughScreens() {
        start()
        reset()
        shot("01_hoy")
        rule.activityRule.scenario.onActivity { a ->
            val d = androidx.core.content.ContextCompat.getDrawable(a, R.mipmap.ic_launcher)!!
            val bmp = android.graphics.Bitmap.createBitmap(432, 432, android.graphics.Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, 432, 432); d.draw(android.graphics.Canvas(bmp))
            java.io.File("build/shots").mkdirs()
            java.io.FileOutputStream("build/shots/00_icono.png").use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        rule.onRoot().performTouchInput { swipeUp() }
        shot("02_hoy_abajo")

        rule.onNodeWithText("Biblia").performClick()
        waitText("Juan 1", true)
        shot("03_lector")
        rule.onAllNodesWithText("En el principio era el Verbo", substring = true).onFirst().performClick()
        shot("04_seleccion")
        rule.onNodeWithText("MacArthur").performClick()
        waitText("Notas de la Biblia de Estudio MacArthur")
        Thread.sleep(500)
        shot("05_nota_macarthur")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        rule.onNodeWithText("Estudio").performClick()
        waitText("Introducciones a los libros")
        shot("06_estudio")
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Hermenéutica: principios de interpretación", substring = true))
        rule.onNodeWithText("Hermenéutica: principios de interpretación", substring = true).performClick()
        waitText("Principios fundamentales")
        shot("07_hermeneutica")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        rule.onNodeWithText("Temas").performClick()
        waitText("Temas")
        shot("08_mis_temas")

        rule.onNodeWithText("Progreso").performClick()
        waitText("Lectura de la Biblia")
        shot("09_progreso")

        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    }

    @Test fun quizAndStudy() {
        start()
        waitText("Comenzar")
        rule.onNodeWithText("Comenzar").performScrollTo().performClick()
        waitText("Pregunta 1 de 5")
        shot("10_quiz")
        rule.onAllNodes(hasClickAction())[2].performClick()
        waitText("Siguiente")
        shot("10b_quiz_respuesta")
    }

    @Test fun introAndStudyEditor() {
        start()
        reset()
        rule.onNodeWithText("Estudio").performClick()
        waitText("Nuevo estudio", true)
        rule.onNodeWithText("Nuevo estudio", substring = true).performClick()
        rule.onNodeWithText("Nuevo T.").performClick()
        rule.onNodeWithText("Romanos").performClick()
        rule.onNodeWithText("8").performClick()
        waitText("Título del estudio")
        Thread.sleep(500)
        shot("11_estudio_pasaje")
        rule.onNodeWithText("Homilética").performClick()
        shot("12_homiletica")
    }

    private val container get() = rule.activity.app

    @Test fun darkModeMemoryAndLesson() {
        start()
        reset()
        kotlinx.coroutines.runBlocking {
            container.settings.update { it.copy(theme = com.biblia.estudio.data.prefs.ThemeMode.DARK) }
            container.user.addMemory(19, 23, 1, 1)
        }
        waitText("Repasar")
        shot("13_hoy_oscuro")
        rule.onNodeWithText("Repasar").performClick()
        waitText("Repasar ahora (1)")
        rule.onNodeWithText("Repasar ahora (1)").performClick()
        waitText("Pista")
        rule.onNodeWithText("Pista").performClick()
        shot("14_memorizar")
        rule.onNodeWithText("Mostrar").performClick()
        rule.onNodeWithText("Bien").performClick()
        waitText("Repaso terminado")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitText("Estudiar lección")
        rule.onNodeWithText("Estudiar lección").performScrollTo().performClick()
        waitText("Mi reflexión")
        rule.onNodeWithText("Mi reflexión").performScrollTo()
        shot("15_leccion_reflexion")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        kotlinx.coroutines.runBlocking {
            container.user.setHighlight(43, 1, 1, 0xFFFFF176.toInt()); container.user.setHighlight(43, 1, 4, 0xFF90CAF9.toInt())
        }
        rule.onNodeWithText("Biblia").performClick()
        waitText("El Verbo hecho carne")
        shot("16_lector_oscuro")
    }

    @Test fun verseSelector() {
        start()
        reset()
        rule.onNodeWithText("Biblia").performClick()
        waitText("El Verbo hecho carne")
        rule.onAllNodesWithText("Juan 1", useUnmergedTree = true).onLast().performClick()
        waitText("Elegir libro")
        rule.onNodeWithText("Nuevo T.").performClick()
        rule.onNodeWithText("Romanos").performClick()
        rule.onNodeWithText("8").performClick()
        waitText("Ir al capítulo completo")
        shot("17_selector_versiculo")
        rule.onNodeWithText("28").performClick()
        waitText("a los que aman a Dios", true)
        shot("18_romanos_8_28")
    }

    @Test fun dictionaryWordsAndContinuous() {
        start()
        reset()
        // Diccionario desde Estudio
        rule.onNodeWithText("Estudio").performClick()
        waitText("Diccionario bíblico")
        rule.onNodeWithText("Diccionario bíblico").performClick()
        waitText("280 entradas")
        rule.onNode(hasSetTextAction()).performTextInput("Abra")
        waitText("Abraham")
        rule.onNodeWithText("Abraham").performClick()
        waitText("En la Biblia")
        Thread.sleep(800); rule.waitForIdle()
        shot("19_diccionario_abraham")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        // Palabras resaltadas en Juan 1:1
        rule.onNodeWithText("Biblia").performClick()
        waitText("El Verbo hecho carne")
        rule.onAllNodesWithText("En el principio era el Verbo", substring = true).onFirst().performClick()
        rule.onNodeWithText("Más").performClick()
        rule.onNodeWithText("Resaltar palabras").performClick()
        waitText("Toca las palabras que quieres marcar y elige un color.")
        rule.onAllNodesWithText("Verbo,").onFirst().performClick()
        shot("20_palabras_dialogo")
        // primer color de la paleta
        rule.onAllNodes(hasClickAction()).filter(hasContentDescription("Quitar").not()).fetchSemanticsNodes()
        kotlinx.coroutines.runBlocking {
            rule.activity.app.user.setWordHighlights("RVR1960", 43, 1, 1, listOf(Triple(4, 4, 0xFFFFF176.toInt()), Triple(14, 15, 0xFF90CAF9.toInt())))
        }
        rule.onNodeWithText("Cancelar").performClick()
        rule.waitForIdle(); Thread.sleep(300)
        shot("21_palabras_resaltadas")

        // Lectura continua
        rule.onNodeWithText("RVR1960").performClick()
        rule.onNodeWithText("Lectura continua").performClick()
        try {
            // Esperar a que el ajuste se guarde, que el lector recargue y que aparezca la lista continua
            rule.waitUntil(20_000) { kotlinx.coroutines.runBlocking { rule.activity.app.settings.current().continuous } }
            rule.waitForIdle()
            rule.waitUntil(20_000) { rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty() }
            rule.waitForIdle()
        } catch (e: Throwable) { rule.onRoot().captureRoboImage("build/shots/fallo_continua.png"); println(rule.onRoot().printToString(maxDepth = 12)); throw e }
        try { rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Marcar Juan 1 como leído") or hasText("Juan 1 leído")) }
        catch (e: Throwable) { rule.onRoot().captureRoboImage("build/shots/fallo_continua2.png"); println("ARBOL:\n" + rule.onRoot().printToString(maxDepth = 6)); throw e }
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Juan 2"))
        waitText("Juan 2")
        shot("22_lectura_continua")
        kotlinx.coroutines.runBlocking { rule.activity.app.settings.update { it.copy(continuous = false) } }
    }

    @Test fun journalPulpitSearchAndSettings() {
        start()
        reset()
        // Diario de oración desde un versículo del lector
        rule.onNodeWithText("Biblia").performClick()
        waitText("El Verbo hecho carne")
        rule.onAllNodesWithText("En el principio era el Verbo", substring = true).onFirst().performClick()
        rule.onNodeWithText("Más").performClick()
        rule.onNodeWithText("Orar por esto").performClick()
        waitText("Petición de oración")
        rule.onNode(hasSetTextAction() and hasText("Por qué o por quién oras")).performTextInput("Por mi familia")
        rule.onAllNodes(hasSetTextAction())[1].performTextInput("Señor, guarda a los míos.")
        shot("23_diario_peticion")
        rule.onNodeWithText("Guardar").performClick()
        waitText("El Verbo hecho carne")
        kotlinx.coroutines.runBlocking {
            val e = rule.activity.app.user.dao.dumpJournal().first()
            org.junit.Assert.assertEquals("Por mi familia", e.title)
            org.junit.Assert.assertEquals(43, e.book)
        }

        // Modo púlpito
        waitText("En el principio era el Verbo", true)
        rule.onAllNodesWithText("En el principio era el Verbo", substring = true).onFirst().performClick()
        rule.onNodeWithText("Más").performClick()
        rule.onNodeWithText("Modo púlpito").performClick()
        waitText("Velocidad de desplazamiento", true)
        shot("24_pulpito")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        // Imagen para compartir
        waitText("En el principio era el Verbo", true)
        rule.onAllNodesWithText("En el principio era el Verbo", substring = true).onFirst().performClick()
        rule.onNodeWithText("Más").performClick()
        rule.onNodeWithText("Imagen para compartir").performClick()
        waitText("Imagen para compartir")
        shot("25_imagen_versiculo")
        rule.onNode(hasText("Cerrar") and hasAnyAncestor(isDialog())).performClick()

        // Búsqueda: frase exacta y en mis notas
        rule.onNodeWithContentDescription("Buscar").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("principio era el Verbo")
        rule.onNode(hasSetTextAction()).performImeAction()
        rule.onNodeWithText("Frase exacta").performClick()
        val t0 = System.currentTimeMillis()
        rule.waitUntil(180_000) { rule.onAllNodesWithText("1 resultado").fetchSemanticsNodes().isNotEmpty() }
        println("TIEMPO_FRASE=" + (System.currentTimeMillis() - t0))
        shot("26_busqueda_frase")
        rule.onNodeWithText("Mis notas y diario").performClick()
        rule.onNode(hasSetTextAction()).performTextClearance()
        rule.onNode(hasSetTextAction()).performTextInput("familia")
        rule.onNode(hasSetTextAction()).performImeAction()
        waitText("Oración")
        shot("27_busqueda_diario")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        // Diario como sección: Agenda con una actividad nueva
        rule.onNodeWithText("Diario").performClick()
        waitText("Agenda vacía")
        rule.onNodeWithText("Nueva actividad", useUnmergedTree = true).performClick()
        waitText("Tipo de actividad")
        rule.onNodeWithText("🍞 Ayuno").performClick()
        rule.onNode(hasSetTextAction() and hasText("Nombre de la actividad")).performTextInput("Ayuno congregacional")
        rule.onNode(hasSetTextAction() and hasText("Lugar")).performTextInput("Templo central")
        shot("30_actividad_nueva")
        rule.onNodeWithText("Guardar").performClick()
        waitText("Ayuno congregacional")
        shot("31_agenda")
        kotlinx.coroutines.runBlocking {
            val ev = rule.activity.app.user.dao.dumpJournal().first { it.kind == "event" }
            org.junit.Assert.assertEquals("Ayuno", ev.eventType)
            org.junit.Assert.assertEquals("Templo central", ev.place)
            org.junit.Assert.assertEquals(60, ev.reminderMinutes)
            // El aviso queda programado una hora antes
            val am = rule.activity.getSystemService(android.app.AlarmManager::class.java)
            val next = org.robolectric.Shadows.shadowOf(am).scheduledAlarms.map { it.triggerAtTime }
            org.junit.Assert.assertTrue("aviso programado: $next", next.any { it == ev.eventAt - 3_600_000L })
        }
        // La actividad aparece en «Hoy»
        rule.onAllNodesWithText("Hoy").filter(hasClickAction()).onFirst().performClick()
        waitText("Próximas actividades")
        shot("32_hoy_actividades")

        // Ajustes nuevos
        rule.onNodeWithContentDescription("Ajustes").performClick()
        waitText("Apariencia")
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Creada por Danruz", substring = true))
        shot("33_acerca_de")
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Lectura en voz alta"))
        shot("28_ajustes_voz")
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Respaldo automático"))
        shot("29_ajustes_respaldo")
    }

    @Test fun usabilityImprovements() {
        start()
        reset()
        val app = rule.activity.app
        // 1) «Hoy»: una tarjeta completada se pliega a una línea con su resumen
        rule.onAllNodesWithContentDescription("Marcar como completado").onFirst().performClick()
        waitText("VERSÍCULO DEL DÍA")
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("Desplegar").fetchSemanticsNodes().isNotEmpty() }
        shot("34_hoy_plegado")

        // 2) Lector: barra de selección compacta y «Aa»
        rule.onNodeWithText("Biblia").performClick()
        waitText("El Verbo hecho carne")
        rule.onAllNodesWithText("En el principio era el Verbo", substring = true).onFirst().performClick()
        shot("35_seleccion_compacta")
        rule.onNodeWithText("Más").performClick()
        waitText("Comparar versiones")
        shot("36_seleccion_mas")
        rule.onNodeWithText("Marcador").performClick()
        rule.waitUntil(10_000) { kotlinx.coroutines.runBlocking { app.user.dao.dumpBookmarks().isNotEmpty() } }
        rule.onNodeWithContentDescription("Texto y pantalla").performClick()
        waitText("Texto y pantalla")
        val before = kotlinx.coroutines.runBlocking { app.settings.current().fontSize }
        rule.onNodeWithText("A+").performClick(); rule.onNodeWithText("A+").performClick()
        shot("37_texto_y_pantalla")
        rule.waitUntil(5_000) { kotlinx.coroutines.runBlocking { app.settings.current().fontSize } == (before + 2f).coerceAtMost(34f) }
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        // 3) Deshacer: quitar un marcador desde Temas › Marcadores y deshacerlo
        rule.onNodeWithText("Temas").performClick()
        rule.onNodeWithText("Marcadores").performClick()
        // El aviso dura unos segundos: se controla el reloj para que no desaparezca antes de comprobarlo
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Quitar marcador").performClick()
        var seen = false
        repeat(100) { if (!seen) { rule.mainClock.advanceTimeBy(100); Thread.sleep(20); seen = rule.onAllNodesWithText("Deshacer").fetchSemanticsNodes().isNotEmpty() } }
        org.junit.Assert.assertTrue("debe aparecer «Deshacer»", seen)
        rule.onRoot().captureRoboImage("build/shots/38_deshacer.png")
        rule.onNodeWithText("Deshacer").performClick()
        rule.mainClock.autoAdvance = true
        rule.waitUntil(10_000) { kotlinx.coroutines.runBlocking { app.user.dao.dumpBookmarks().size } == 1 }

        // 4) Búsqueda mientras se escribe (sin pulsar Enter)
        rule.onNodeWithText("Biblia").performClick()
        waitText("El Verbo hecho carne")
        rule.onNodeWithContentDescription("Buscar").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("contrito")
        rule.waitUntil(60_000) { rule.onAllNodesWithText("resultado", substring = true).fetchSemanticsNodes().isNotEmpty() }
        shot("39_busqueda_al_escribir")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        // 5) Estudio: salir con «atrás» del teléfono guarda lo escrito
        rule.onNodeWithText("Estudio").performClick()
        waitText("Nuevo estudio", true)
        rule.onNodeWithText("Nuevo estudio", substring = true).performClick()
        rule.onNodeWithText("Nuevo T.").performClick()
        rule.onNodeWithText("Romanos").performClick()
        rule.onNodeWithText("12").performClick()
        waitText("Título del estudio")
        rule.onNode(hasSetTextAction() and hasText("Título del estudio")).performTextClearance()
        rule.onNode(hasSetTextAction() and hasText("Título del estudio")).performTextInput("Sacrificio vivo")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil(10_000) { kotlinx.coroutines.runBlocking { app.user.dao.dumpStudies().any { it.title == "Sacrificio vivo" } } }
    }

    @Test fun welcomeTourWithName() {
        rule.waitUntil(60_000) {
            rule.onAllNodesWithText("Comenzar recorrido").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithText("Leer pasaje").fetchSemanticsNodes().isNotEmpty()
        }
        // Los ajustes pueden venir de otra prueba: se vuelve a pedir el recorrido (como desde Ajustes)
        kotlinx.coroutines.runBlocking { rule.activity.app.settings.update { it.copy(tourSeen = false, userName = "") } }
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Comenzar recorrido").fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasSetTextAction()).performTextInput("Ana María")
        waitText("¡Mucho gusto, Ana!", true)
        shot("40_tour_nombre")
        rule.onNodeWithText("Comenzar recorrido").performClick()
        waitText("Tu devocional de cada día", true)
        shot("41_tour_hoy")
        repeat(2) { rule.onNodeWithText("Siguiente").performClick() }
        waitText("Toca un versículo")
        shot("42_tour_versiculo")
        rule.onNodeWithText("Saltar").performClick()
        waitText("Último paso, Ana")
        shot("43_tour_final")
        // Sin recordatorio para no pedir el permiso de notificaciones en la prueba
        rule.onNode(isToggleable()).performClick()
        rule.onNodeWithText("Empezar").performClick()
        waitText("Leer pasaje")
        val expected = com.biblia.estudio.domain.Greeting.now("Ana")   // «Buenos días, Ana», etc.
        rule.waitUntil(10_000) { rule.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty() }
        shot("44_hoy_saludo")
        val st = kotlinx.coroutines.runBlocking { rule.activity.app.settings.current() }
        org.junit.Assert.assertEquals("Ana María", st.userName)
        org.junit.Assert.assertTrue(st.tourSeen && st.onboarded)
        // Repetir el recorrido desde Ajustes
        rule.onNodeWithContentDescription("Ajustes").performClick()
        waitText("Ver el recorrido de bienvenida")
        rule.onNodeWithText("Ver el recorrido de bienvenida").performClick()
        waitText("Comenzar recorrido")
    }

    @Test fun lessonReflectionAutosaveAndComplete() {
        start()
        reset()
        val app = rule.activity.app
        kotlinx.coroutines.runBlocking { app.settings.update { it.copy(fontSize = 26f) } }   // letra grande, como en el teléfono del usuario
        waitText("Estudiar lección")
        rule.onNodeWithText("Estudiar lección").performScrollTo().performClick()
        waitText("Mi reflexión")
        rule.onNode(hasSetTextAction()).performScrollTo().performTextInput("Dios es el centro de todo.")
        // Se guarda sola, sin botón
        waitText("Reflexión guardada ✓")
        val lessonId = kotlinx.coroutines.runBlocking { app.content.lessonOfDay(com.biblia.estudio.data.user.today()).id }
        rule.waitUntil(10_000) { kotlinx.coroutines.runBlocking { app.user.dao.dumpReflections().any { it.lessonId == lessonId && it.text == "Dios es el centro de todo." } } }
        // «Completar» marca la lección y cambia a «Completada ✓»
        rule.onNodeWithText("Completar la lección de hoy", substring = true).performScrollTo().performClick()
        waitText("Lección de hoy completada", true)
        rule.onNodeWithText("Siguiente ›").performScrollTo()
        shot("45_leccion_completada")
        val done = kotlinx.coroutines.runBlocking { app.user.dao.dumpDevotional().any { it.type == "lesson" && it.epochDay == com.biblia.estudio.data.user.today() } }
        org.junit.Assert.assertTrue(done)
    }
}
