package com.laurasheehan.royalmiles.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.laurasheehan.royalmiles.RaceConfig
import com.laurasheehan.royalmiles.data.AthleteProfileRepository
import com.laurasheehan.royalmiles.data.CelebrationStore
import com.laurasheehan.royalmiles.data.PlanRepository
import com.laurasheehan.royalmiles.data.coach.CoachRepository
import com.laurasheehan.royalmiles.data.coach.CoachSuggestionDecisionStore
import com.laurasheehan.royalmiles.data.health.HealthConnectRepository
import com.laurasheehan.royalmiles.data.health.HealthDiagnostics
import com.laurasheehan.royalmiles.ui.diagnostics.DiagnosticsScreen
import com.laurasheehan.royalmiles.ui.diagnostics.DiagnosticsViewModel
import com.laurasheehan.royalmiles.ui.activity.ActivityLogScreen
import com.laurasheehan.royalmiles.ui.activity.ActivityLogViewModel
import com.laurasheehan.royalmiles.ui.calendar.CalendarScreen
import com.laurasheehan.royalmiles.ui.calendar.CalendarViewModel
import com.laurasheehan.royalmiles.ui.coach.CoachScreen
import com.laurasheehan.royalmiles.ui.coach.CoachViewModel
import com.laurasheehan.royalmiles.ui.dashboard.DashboardScreen
import com.laurasheehan.royalmiles.ui.dashboard.DashboardViewModel
import com.laurasheehan.royalmiles.ui.nutrition.FlashcardScreen
import com.laurasheehan.royalmiles.ui.nutrition.NutritionScreen
import com.laurasheehan.royalmiles.ui.nutrition.NutritionViewModel
import com.laurasheehan.royalmiles.ui.session.SessionEditScreen
import com.laurasheehan.royalmiles.ui.session.SessionEditViewModel
import com.laurasheehan.royalmiles.ui.sync.SyncScreen
import com.laurasheehan.royalmiles.ui.sync.SyncViewModel

object Routes {
    const val DASHBOARD = "dashboard"
    const val CALENDAR = "calendar"
    const val ACTIVITY = "activity"
    const val COACH = "coach"
    const val NUTRITION = "nutrition"
    const val NUTRITION_LEARN = "nutrition/learn"
    const val HEALTH_DIAGNOSTICS = "sync/diagnostics"
    const val SESSION = "session/{sessionId}"
    const val SYNC = "sync"
    const val NEW_SESSION_ID = -1L

    fun session(id: Long) = "session/$id"
}

@Composable
fun RoyalMilesNavHost(
    navController: NavHostController,
    repository: PlanRepository,
    athleteProfileRepository: AthleteProfileRepository,
    coachRepository: CoachRepository,
) {
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val viewModel: DashboardViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        DashboardViewModel(
                            repository = repository,
                            coachRepository = coachRepository,
                            raceDate = RaceConfig.RACE_DATE,
                            celebrations = CelebrationStore(context.applicationContext),
                            suggestionDecisions = CoachSuggestionDecisionStore(context.applicationContext),
                        )
                    }
                },
            )
            DashboardScreen(
                viewModel = viewModel,
                onOpenSession = { navController.navigate(Routes.session(it)) },
                onOpenSync = { navController.navigate(Routes.SYNC) },
                onOpenCalendar = { navController.navigate(Routes.CALENDAR) },
            )
        }
        composable(Routes.ACTIVITY) {
            val viewModel: ActivityLogViewModel = viewModel(
                factory = viewModelFactory { initializer { ActivityLogViewModel(repository) } },
            )
            ActivityLogScreen(
                viewModel = viewModel,
                onOpenSession = { navController.navigate(Routes.session(it)) },
            )
        }
        composable(Routes.COACH) {
            val viewModel: CoachViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { CoachViewModel(coachRepository) }
                },
            )
            CoachScreen(viewModel = viewModel)
        }
        composable(Routes.NUTRITION) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val viewModel: NutritionViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        NutritionViewModel(
                            repository = repository,
                            healthConnect = HealthConnectRepository(context.applicationContext),
                            athleteProfileRepository = athleteProfileRepository,
                            raceDate = RaceConfig.RACE_DATE,
                        )
                    }
                },
            )
            NutritionScreen(
                viewModel = viewModel,
                onOpenLearn = { navController.navigate(Routes.NUTRITION_LEARN) },
            )
        }
        composable(Routes.NUTRITION_LEARN) {
            FlashcardScreen(onDone = { navController.popBackStack() })
        }
        composable(Routes.CALENDAR) {
            val viewModel: CalendarViewModel = viewModel(
                factory = viewModelFactory { initializer { CalendarViewModel(repository) } },
            )
            CalendarScreen(
                viewModel = viewModel,
                onOpenSession = { navController.navigate(Routes.session(it)) },
                onAddSession = { navController.navigate(Routes.session(Routes.NEW_SESSION_ID)) },
            )
        }
        composable(
            route = Routes.SESSION,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: Routes.NEW_SESSION_ID
            val resolvedId = if (sessionId == Routes.NEW_SESSION_ID) null else sessionId
            val viewModel: SessionEditViewModel = viewModel(
                factory = viewModelFactory { initializer { SessionEditViewModel(repository, resolvedId) } },
            )
            SessionEditScreen(viewModel = viewModel, onDone = { navController.popBackStack() })
        }
        composable(Routes.SYNC) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val viewModel: SyncViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { SyncViewModel(repository, HealthConnectRepository(context.applicationContext)) }
                },
            )
            SyncScreen(
                viewModel = viewModel,
                onDone = { navController.popBackStack() },
                onOpenDiagnostics = { navController.navigate(Routes.HEALTH_DIAGNOSTICS) },
            )
        }
        composable(Routes.HEALTH_DIAGNOSTICS) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val viewModel: DiagnosticsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        DiagnosticsViewModel(
                            diagnostics = HealthDiagnostics(context.applicationContext),
                            healthConnect = HealthConnectRepository(context.applicationContext),
                        )
                    }
                },
            )
            DiagnosticsScreen(viewModel = viewModel, onDone = { navController.popBackStack() })
        }
    }
}
