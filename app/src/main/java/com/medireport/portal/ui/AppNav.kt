package com.medireport.portal.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.medireport.portal.data.Role
import com.medireport.portal.ui.screens.AdminHomeScreen
import com.medireport.portal.ui.screens.AdminLoginScreen
import com.medireport.portal.ui.screens.PageSetupScreen
import com.medireport.portal.ui.screens.PatientDetailScreen
import com.medireport.portal.ui.screens.PatientFormScreen
import com.medireport.portal.ui.screens.MailSettingsScreen
import com.medireport.portal.ui.screens.ReportBuilderScreen
import com.medireport.portal.ui.screens.SplashScreen
import com.medireport.portal.ui.screens.TemplateEditorScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object Routes {
    const val ADMIN_LOGIN = "admin_login"
    const val ADMIN = "admin"
    const val PATIENT = "patient/{id}"
    const val PATIENT_FORM = "patient_form/{id}"
    const val REPORT_BUILDER = "report_builder/{id}"
    const val TEMPLATE_EDIT = "template_edit/{id}"
    const val PAGE_SETUP = "page_setup"
    const val MAIL_SETTINGS = "mail_settings"

    fun patient(id: Long) = "patient/$id"
    fun patientForm(id: Long) = "patient_form/$id"
    fun reportBuilder(id: Long) = "report_builder/$id"
    fun templateEdit(id: Long) = "template_edit/$id"

    /** Screens that start with a dark gradient under the status bar. */
    val heroRoutes = setOf(ADMIN_LOGIN)
}

private val idArg = listOf(navArgument("id") { type = NavType.LongType })
private fun NavBackStackEntry.id(): Long = arguments?.getLong("id") ?: 0L

@Composable
fun AppRoot(onHeroChanged: (Boolean) -> Unit) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    var start by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val s = repo.prefs.session.first()
        start = if (s.role == Role.ADMIN) Routes.ADMIN else Routes.ADMIN_LOGIN
    }

    val startRoute = start
    if (startRoute == null) {
        LaunchedEffect(Unit) { onHeroChanged(true) }
        SplashScreen()
        return
    }

    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    LaunchedEffect(route) { onHeroChanged(route == null || route in Routes.heroRoutes) }

    fun logout() {
        scope.launch {
            repo.prefs.endSession()
            nav.navigate(Routes.ADMIN_LOGIN) { popUpTo(nav.graph.id) { inclusive = true } }
        }
    }

    NavHost(
        navController = nav,
        startDestination = startRoute,
        enterTransition = { slideInHorizontally(tween(260)) { it / 6 } + fadeIn(tween(260)) },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { slideOutHorizontally(tween(220)) { it / 6 } + fadeOut(tween(220)) }
    ) {
        composable(Routes.ADMIN_LOGIN) {
            AdminLoginScreen(
                onSuccess = { nav.navigate(Routes.ADMIN) { popUpTo(Routes.ADMIN_LOGIN) { inclusive = true } } }
            )
        }
        composable(Routes.ADMIN) {
            AdminHomeScreen(
                onOpenPatient = { nav.navigate(Routes.patient(it)) },
                onAddPatient = { nav.navigate(Routes.patientForm(0L)) },
                onEditPatient = { nav.navigate(Routes.patientForm(it)) },
                onNewReport = { nav.navigate(Routes.reportBuilder(it)) },
                onEditTemplate = { nav.navigate(Routes.templateEdit(it)) },
                onPageSetup = { nav.navigate(Routes.PAGE_SETUP) },
                onMailSettings = { nav.navigate(Routes.MAIL_SETTINGS) },
                onLogout = { logout() }
            )
        }
        composable(Routes.PATIENT, arguments = idArg) { e ->
            val id = e.id()
            PatientDetailScreen(
                patientId = id,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.patientForm(id)) },
                onNewReport = { nav.navigate(Routes.reportBuilder(id)) }
            )
        }
        composable(Routes.PATIENT_FORM, arguments = idArg) { e ->
            PatientFormScreen(e.id(), onBack = { nav.popBackStack() })
        }
        composable(Routes.REPORT_BUILDER, arguments = idArg) { e ->
            ReportBuilderScreen(e.id(), onBack = { nav.popBackStack() })
        }
        composable(Routes.TEMPLATE_EDIT, arguments = idArg) { e ->
            TemplateEditorScreen(e.id(), onBack = { nav.popBackStack() })
        }
        composable(Routes.MAIL_SETTINGS) {
            MailSettingsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.PAGE_SETUP) {
            PageSetupScreen(onBack = { nav.popBackStack() })
        }
    }
}
