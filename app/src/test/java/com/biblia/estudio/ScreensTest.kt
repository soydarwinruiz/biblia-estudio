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

    private fun waitText(text: String, substring: Boolean = false) =
        rule.waitUntil(60_000) { rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    @Test fun walkThroughScreens() {
        waitText("Versículo del día", true)
        waitText("Leer pasaje")
        shot("01_hoy")
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
        rule.onNodeWithText("Hermenéutica: principios de interpretación", substring = true).performScrollTo().performClick()
        waitText("Principios fundamentales")
        shot("07_hermeneutica")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        rule.onNodeWithText("Mis temas").performClick()
        waitText("Temas")
        shot("08_mis_temas")

        rule.onNodeWithText("Progreso").performClick()
        waitText("Lectura de la Biblia")
        shot("09_progreso")

        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    }

    @Test fun quizAndStudy() {
        waitText("Preguntas del día")
        rule.onNodeWithText("Comenzar").performScrollTo().performClick()
        waitText("Pregunta 1 de 5")
        shot("10_quiz")
        rule.onAllNodes(hasClickAction()).filter(hasText("", substring = true)).fetchSemanticsNodes()
        rule.onNodeWithText("Biblia").assertDoesNotExist()
    }

    @Test fun introAndStudyEditor() {
        waitText("Versículo del día", true)
        rule.runOnUiThread { }
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
}
