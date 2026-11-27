package com.biblia.estudio.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.biblia.estudio.data.content.PassageRef
import com.biblia.estudio.data.prefs.AppSettings
import com.biblia.estudio.ui.screens.*
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String, val icon: ImageVector)
private val tabs = listOf(
    Tab("today", "Hoy", Icons.Filled.WbSunny),
    Tab("reader", "Biblia", Icons.AutoMirrored.Filled.MenuBook),
    Tab("study", "Estudio", Icons.Filled.School),
    Tab("journal", "Diario", Icons.Filled.EventNote),
    Tab("mine", "Temas", Icons.Filled.Bookmarks),
    Tab("progress", "Progreso", Icons.Filled.BarChart),
)

/** Visibilidad de las barras (el lector las oculta al leer). */
val LocalBarsVisible = compositionLocalOf<androidx.compose.runtime.MutableState<Boolean>> { androidx.compose.runtime.mutableStateOf(true) }

/** Ajustes actuales, accesibles desde cualquier pantalla y siempre al día. */
val LocalSettings = compositionLocalOf { AppSettings() }

fun refRoute(ref: PassageRef) = "reader?b=${ref.book}&c=${ref.chapter}&v=${ref.verse}&ve=${if (ref.chapterEnd == ref.chapter) ref.verseEnd else 999}"

fun NavController.openRef(ref: PassageRef) = navigate(refRoute(ref)) { launchSingleTop = true }

/** Abre una referencia desde el lector dejando el capítulo actual en la pila, para poder volver a él. */
fun NavController.openRefFromReader(ref: PassageRef, fromLabel: String) =
    navigate(refRoute(ref) + "&from=" + android.net.Uri.encode(fromLabel))

fun NavController.openPulpit(ref: PassageRef) =
    navigate("pulpit?b=${ref.book}&c=${ref.chapter}&v=${ref.verse}&ce=${ref.chapterEnd}&ve=${ref.verseEnd}")

fun NavController.newStudy(ref: PassageRef) =
    navigate("studyedit/0?b=${ref.book}&c=${ref.chapter}&v=${ref.verse}&ce=${ref.chapterEnd}&ve=${ref.verseEnd}")

@Composable
fun AppRoot(settings: AppSettings, externalRoute: String? = null, onExternalConsumed: () -> Unit = {}) {
    val nav = rememberNavController()
    val snackbar = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }
    val appScope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.biblia.estudio.ui.components.Undo.events.collect { ev ->
            appScope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val r = snackbar.showSnackbar(ev.message, actionLabel = "Deshacer", withDismissAction = true, duration = androidx.compose.material3.SnackbarDuration.Long)
                if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) ev.undo()
            }
        }
    }
    // Destinos pedidos desde fuera de la app (atajos, widgets, «Compartir»)
    androidx.compose.runtime.LaunchedEffect(externalRoute) {
        if (externalRoute != null) { nav.navigate(externalRoute) { launchSingleTop = true }; onExternalConsumed() }
    }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "today"
    val barsVisible = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    // Con el teclado abierto se oculta la barra inferior y el contenido se ajusta encima del teclado
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    val imeOpen = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    val showBar = tabs.any { route.substringBefore("?") == it.route } && barsVisible.value && !imeOpen
    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route.substringBefore("?") == t.route,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, t.label) }, label = { com.biblia.estudio.ui.components.FitText(t.label, androidx.compose.material3.MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    ) { pad ->
        CompositionLocalProvider(LocalSettings provides settings, LocalBarsVisible provides barsVisible) {
        NavHost(nav, startDestination = "today", modifier = Modifier.padding(bottom = if (showBar) pad.calculateBottomPadding() else androidx.compose.ui.unit.Dp(0f)).imePadding()) {
            composable("today") { TodayScreen(nav, LocalSettings.current) }
            composable(
                "reader?b={b}&c={c}&v={v}&ve={ve}&from={from}",
                arguments = listOf(
                    navArgument("from") { type = NavType.StringType; defaultValue = "" },
                    navArgument("b") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("c") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("v") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("ve") { type = NavType.IntType; defaultValue = 0 },
                )
            ) { e ->
                val a = e.arguments!!
                ReaderScreen(nav, LocalSettings.current, a.getInt("b"), a.getInt("c"), a.getInt("v"), a.getInt("ve"), a.getString("from") ?: "")
            }
            composable("search?q={q}", arguments = listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" })) { e -> SearchScreen(nav, LocalSettings.current, e.arguments!!.getString("q") ?: "") }
            composable("dictionary?q={q}", arguments = listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" })) { e -> DictionaryScreen(nav, e.arguments!!.getString("q") ?: "") }
            composable("dict/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { e -> DictionaryEntryScreen(nav, LocalSettings.current, e.arguments!!.getInt("id")) }
            composable("compare/{b}/{c}/{v}", arguments = listOf(navArgument("b") { type = NavType.IntType }, navArgument("c") { type = NavType.IntType }, navArgument("v") { type = NavType.IntType })) { e ->
                val a = e.arguments!!; CompareScreen(nav, LocalSettings.current, a.getInt("b"), a.getInt("c"), a.getInt("v"))
            }
            composable("study") { StudyHubScreen(nav) }
            composable("intro/{b}", arguments = listOf(navArgument("b") { type = NavType.IntType })) { e -> IntroScreen(nav, LocalSettings.current, e.arguments!!.getInt("b")) }
            composable("commentary/{b}/{c}", arguments = listOf(navArgument("b") { type = NavType.IntType }, navArgument("c") { type = NavType.IntType })) { e ->
                CommentaryScreen(nav, LocalSettings.current, e.arguments!!.getInt("b"), e.arguments!!.getInt("c"))
            }
            composable("article/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { e -> ArticleScreen(nav, LocalSettings.current, e.arguments!!.getInt("id")) }
            composable("lesson/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { e -> LessonScreen(nav, LocalSettings.current, e.arguments!!.getInt("id")) }
            composable("lessons") { LessonsListScreen(nav) }
            composable("index") { IndexScreen(nav) }
            composable("index/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { e -> IndexTopicScreen(nav, e.arguments!!.getInt("id")) }
            composable("studies") { StudiesListScreen(nav) }
            composable(
                "studyedit/{id}?b={b}&c={c}&v={v}&ce={ce}&ve={ve}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }) + listOf("b", "c", "v", "ce", "ve").map { k -> navArgument(k) { type = NavType.IntType; defaultValue = 0 } }
            ) { e ->
                val a = e.arguments!!
                StudyEditorScreen(nav, LocalSettings.current, a.getLong("id"), PassageRef(a.getInt("b"), a.getInt("c"), a.getInt("v"), a.getInt("ce"), a.getInt("ve")))
            }
            composable("mine") { MyTopicsScreen(nav, LocalSettings.current) }
            composable("topic/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e -> TopicDetailScreen(nav, LocalSettings.current, e.arguments!!.getLong("id")) }
            composable("tag/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e -> TagDetailScreen(nav, LocalSettings.current, e.arguments!!.getLong("id")) }
            composable("progress") { ProgressScreen(nav) }
            composable("plan/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { e -> PlanScreen(nav, e.arguments!!.getInt("id")) }
            composable("quiz?practice={p}", arguments = listOf(navArgument("p") { type = NavType.BoolType; defaultValue = false })) { e -> QuizScreen(nav, e.arguments!!.getBoolean("p")) }
            composable("prayer?o={o}", arguments = listOf(navArgument("o") { type = NavType.IntType; defaultValue = 0 })) { e -> PrayerScreen(nav, LocalSettings.current, e.arguments!!.getInt("o")) }
            composable("memory") { MemoryScreen(nav, LocalSettings.current) }
            composable("settings") { SettingsScreen(nav, LocalSettings.current) }
            composable("journal?t={t}", arguments = listOf(navArgument("t") { type = NavType.IntType; defaultValue = 0 })) { e -> JournalScreen(nav, e.arguments!!.getInt("t")) }
            composable("journal/{id}?kind={kind}&b={b}&c={c}&v={v}", arguments = listOf(
                navArgument("id") { type = NavType.LongType }, navArgument("kind") { type = NavType.StringType; defaultValue = "prayer" },
                navArgument("b") { type = NavType.IntType; defaultValue = 0 }, navArgument("c") { type = NavType.IntType; defaultValue = 0 }, navArgument("v") { type = NavType.IntType; defaultValue = 0 },
            )) { e -> val a = e.arguments!!; JournalEditScreen(nav, a.getLong("id"), a.getString("kind") ?: "prayer", PassageRef(a.getInt("b"), a.getInt("c"), a.getInt("v"))) }
            composable(
                "pulpit?b={b}&c={c}&v={v}&ce={ce}&ve={ve}",
                arguments = listOf("b", "c", "v", "ce", "ve").map { k -> navArgument(k) { type = NavType.IntType; defaultValue = 0 } }
            ) { e ->
                val a = e.arguments!!
                PulpitScreen(nav, LocalSettings.current, PassageRef(a.getInt("b"), a.getInt("c"), a.getInt("v"), a.getInt("ce"), a.getInt("ve")))
            }
        }
        }
    }
}
