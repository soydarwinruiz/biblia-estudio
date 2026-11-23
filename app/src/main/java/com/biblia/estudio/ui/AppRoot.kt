package com.biblia.estudio.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

private data class Tab(val route: String, val label: String, val icon: ImageVector)
private val tabs = listOf(
    Tab("today", "Hoy", Icons.Filled.WbSunny),
    Tab("reader", "Biblia", Icons.AutoMirrored.Filled.MenuBook),
    Tab("study", "Estudio", Icons.Filled.School),
    Tab("mine", "Mis temas", Icons.Filled.Bookmarks),
    Tab("progress", "Progreso", Icons.Filled.BarChart),
)

fun NavController.openRef(ref: PassageRef) =
    navigate("reader?b=${ref.book}&c=${ref.chapter}&v=${ref.verse}&ve=${if (ref.chapterEnd == ref.chapter) ref.verseEnd else 999}") { launchSingleTop = true }

fun NavController.newStudy(ref: PassageRef) =
    navigate("studyedit/0?b=${ref.book}&c=${ref.chapter}&v=${ref.verse}&ce=${ref.chapterEnd}&ve=${ref.verseEnd}")

@Composable
fun AppRoot(settings: AppSettings) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "today"
    val showBar = tabs.any { route.substringBefore("?") == it.route }
    Scaffold(
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
                        icon = { Icon(t.icon, t.label) }, label = { Text(t.label) }
                    )
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = "today", modifier = Modifier.padding(bottom = if (showBar) pad.calculateBottomPadding() else androidx.compose.ui.unit.Dp(0f))) {
            composable("today") { TodayScreen(nav, settings) }
            composable(
                "reader?b={b}&c={c}&v={v}&ve={ve}",
                arguments = listOf(
                    navArgument("b") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("c") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("v") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("ve") { type = NavType.IntType; defaultValue = 0 },
                )
            ) { e ->
                val a = e.arguments!!
                ReaderScreen(nav, settings, a.getInt("b"), a.getInt("c"), a.getInt("v"), a.getInt("ve"))
            }
            composable("search") { SearchScreen(nav, settings) }
            composable("compare/{b}/{c}/{v}", arguments = listOf(navArgument("b") { type = NavType.IntType }, navArgument("c") { type = NavType.IntType }, navArgument("v") { type = NavType.IntType })) { e ->
                val a = e.arguments!!; CompareScreen(nav, settings, a.getInt("b"), a.getInt("c"), a.getInt("v"))
            }
            composable("study") { StudyHubScreen(nav) }
            composable("intro/{b}", arguments = listOf(navArgument("b") { type = NavType.IntType })) { e -> IntroScreen(nav, settings, e.arguments!!.getInt("b")) }
            composable("commentary/{b}/{c}", arguments = listOf(navArgument("b") { type = NavType.IntType }, navArgument("c") { type = NavType.IntType })) { e ->
                CommentaryScreen(nav, settings, e.arguments!!.getInt("b"), e.arguments!!.getInt("c"))
            }
            composable("article/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { e -> ArticleScreen(nav, settings, e.arguments!!.getInt("id")) }
            composable("lesson/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { e -> LessonScreen(nav, settings, e.arguments!!.getInt("id")) }
            composable("lessons") { LessonsListScreen(nav) }
            composable("index") { IndexScreen(nav) }
            composable("index/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { e -> IndexTopicScreen(nav, e.arguments!!.getInt("id")) }
            composable("studies") { StudiesListScreen(nav) }
            composable(
                "studyedit/{id}?b={b}&c={c}&v={v}&ce={ce}&ve={ve}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }) + listOf("b", "c", "v", "ce", "ve").map { k -> navArgument(k) { type = NavType.IntType; defaultValue = 0 } }
            ) { e ->
                val a = e.arguments!!
                StudyEditorScreen(nav, settings, a.getLong("id"), PassageRef(a.getInt("b"), a.getInt("c"), a.getInt("v"), a.getInt("ce"), a.getInt("ve")))
            }
            composable("mine") { MyTopicsScreen(nav, settings) }
            composable("topic/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e -> TopicDetailScreen(nav, settings, e.arguments!!.getLong("id")) }
            composable("tag/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e -> TagDetailScreen(nav, settings, e.arguments!!.getLong("id")) }
            composable("progress") { ProgressScreen(nav) }
            composable("plan/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { e -> PlanScreen(nav, e.arguments!!.getInt("id")) }
            composable("quiz?practice={p}", arguments = listOf(navArgument("p") { type = NavType.BoolType; defaultValue = false })) { e -> QuizScreen(nav, e.arguments!!.getBoolean("p")) }
            composable("prayer") { PrayerScreen(nav, settings) }
            composable("settings") { SettingsScreen(nav, settings) }
        }
    }
}
