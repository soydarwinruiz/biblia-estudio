package com.biblia.estudio

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Revisión visual con pantalla estrecha (360 dp) y letra del sistema grande (130 %):
 * recorre las pantallas principales y guarda capturas en build/shots/audit para detectar textos partidos o cortados.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = BibliaApp::class, qualifiers = "w360dp-h780dp-xhdpi")
class LargeFontAuditTest {
    @get:Rule val rule = createEmptyComposeRule()

    private fun shot(name: String) { rule.waitForIdle(); rule.onRoot().captureRoboImage("build/shots/audit/$name.png") }
    private fun wait(text: String, sub: Boolean = false) = rule.waitUntil(60_000) { rule.onAllNodesWithText(text, substring = sub).fetchSemanticsNodes().isNotEmpty() }
    private fun back(s: ActivityScenario<MainActivity>) { s.onActivity { it.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle() }
    private fun tab(name: String) = rule.onAllNodesWithText(name).filter(hasClickAction()).onLast().performClick()

    @Test fun audit() {
        RuntimeEnvironment.setFontScale(1.3f)
        val s = ActivityScenario.launch(MainActivity::class.java)
        rule.waitUntil(90_000) { rule.onAllNodesWithText("Comenzar recorrido").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithText("Leer pasaje").fetchSemanticsNodes().isNotEmpty() }
        if (rule.onAllNodesWithText("Comenzar recorrido").fetchSemanticsNodes().isNotEmpty()) {
            shot("01_tour_inicio"); rule.onNodeWithText("Comenzar recorrido").performClick(); shot("02_tour_pagina")
            rule.onNodeWithText("Saltar").performClick(); wait("Último paso"); shot("03_tour_final")
        }
        lateinit var app: AppContainer
        s.onActivity { app = it.app }
        kotlinx.coroutines.runBlocking {
            app.settings.update { it.copy(onboarded = true, tourSeen = true, userName = "María José", immersiveReader = false, theme = com.biblia.estudio.data.prefs.ThemeMode.LIGHT) }
            app.settings.setPosition(43, 1); app.position = com.biblia.estudio.data.prefs.ReadingPosition(43, 1)
            app.user.addMemory(19, 23, 1, 1)
            app.user.dao.upsertJournal(com.biblia.estudio.data.user.JournalEntry(kind = "event", title = "Vigilia de jóvenes con oración", eventType = "Vigilia",
                eventAt = System.currentTimeMillis() + 86_400_000L, place = "Templo central, calle 10", reminderMinutes = 60, repeatRule = "monthly_nth"))
        }
        wait("Leer pasaje"); shot("04_hoy")
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Estudiar lección")); shot("05_hoy_abajo")

        tab("Biblia"); wait("El Verbo hecho carne"); shot("06_lector")
        rule.onAllNodesWithText("En el principio era el Verbo", substring = true).onFirst().performClick(); shot("07_seleccion")
        rule.onNodeWithText("Más").performClick(); shot("08_seleccion_mas"); rule.onNodeWithText("Comparar versiones").performClick(); wait("Comparar", true); shot("08b_comparar"); back(s)
        rule.onNodeWithText("RVR1960").performClick(); shot("10_menu_version"); rule.onNodeWithText("RVR1960 —", substring = true).performClick()
        rule.onNodeWithContentDescription("Más opciones").performClick(); shot("11_menu_mas"); rule.onNodeWithText("Introducción a Juan").performClick(); wait("Autor y fecha", true); shot("11b_introduccion"); back(s)
        wait("El Verbo hecho carne"); rule.waitUntil(30_000) { rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Marcar como leído") or hasText("Capítulo leído")); shot("12_lector_final")

        tab("Estudio"); wait("Nuevo estudio", true); shot("13_estudio")
        rule.onNodeWithText("Nuevo estudio", substring = true).performClick()
        rule.onNodeWithText("Nuevo T.").performClick(); shot("14_selector_libros")
        rule.onNodeWithText("Romanos").performClick(); rule.onNodeWithText("12").performClick(); wait("Título del estudio"); shot("15_estudio_pasaje")
        rule.onNodeWithText("Homilética").performClick(); shot("16_estudio_homiletica"); back(s)

        tab("Diario"); wait("Vigilia de jóvenes con oración"); shot("17_diario_agenda")
        rule.onAllNodesWithText("Vigilia de jóvenes con oración").onFirst().performClick(); wait("Tipo de actividad"); shot("18_actividad")
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Añadir al calendario del teléfono", substring = true)); shot("19_actividad_abajo"); back(s)
        wait("Agenda"); rule.onNodeWithText("Oración").performClick(); shot("20_diario_oracion")

        tab("Temas"); wait("Memorizar", true); shot("21_temas")
        rule.onNodeWithText("Memorizar", substring = true).performClick(); wait("Repasar ahora", true); shot("22_memorizar")
        rule.onNodeWithText("Repasar ahora", substring = true).performClick(); rule.onNodeWithText("Mostrar").performClick(); shot("23_memorizar_respuestas"); back(s); back(s)

        tab("Progreso"); wait("Lectura de la Biblia"); shot("24_progreso")
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("La Biblia en un año")); rule.onNodeWithText("La Biblia en un año").performClick(); wait("Comenzar hoy"); shot("25_plan"); back(s)
        rule.onNodeWithContentDescription("Preguntas").performClick(); wait("Pregunta 1 de 5"); shot("26_quiz")
        rule.onAllNodes(hasClickAction())[2].performClick(); shot("27_quiz_respuesta"); back(s)

        tab("Hoy"); wait("Leer pasaje")
        rule.onNodeWithContentDescription("Ajustes").performClick(); wait("Apariencia"); shot("28_ajustes")
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Respaldo automático")); shot("29_ajustes_respaldo")
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Acerca de")); shot("30_ajustes_fin"); back(s)

        rule.onNodeWithContentDescription("Buscar").performClick(); rule.onNode(hasSetTextAction()).performTextInput("gracia")
        rule.waitUntil(60_000) { rule.onAllNodesWithText("resultado", substring = true).fetchSemanticsNodes().isNotEmpty() }; shot("31_busqueda")
        s.close()
    }
}
