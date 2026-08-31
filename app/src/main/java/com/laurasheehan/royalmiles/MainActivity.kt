package com.laurasheehan.royalmiles

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.laurasheehan.royalmiles.navigation.RoyalMilesNavHost
import com.laurasheehan.royalmiles.navigation.Routes
import com.laurasheehan.royalmiles.ui.theme.RoyalMilesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as RoyalMilesApp
        val repository = app.repository
        val athleteProfileRepository = app.athleteProfileRepository

        setContent {
            RoyalMilesTheme {
                LaunchedEffect(Unit) {
                    repository.ensureSeeded(raceDate = RaceConfig.RACE_DATE, peakLongRunKm = RaceConfig.PEAK_LONG_RUN_KM)
                }
                RoyalMilesRoot(repository, athleteProfileRepository)
            }
        }
    }
}

// A shared single-line label: at labelMedium the 5-item bar has no room for "Dashboard", which
// wraps to two lines while every other tab stays on one — labelSmall plus a forced single line
// keeps all five tabs the same height regardless of label length.
@Composable
private fun NavLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun RoyalMilesRoot(
    repository: com.laurasheehan.royalmiles.data.PlanRepository,
    athleteProfileRepository: com.laurasheehan.royalmiles.data.AthleteProfileRepository,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.hierarchy?.firstOrNull()?.route
    fun navigateToTab(route: String) {
        navController.navigate(route) {
            launchSingleTop = true
            restoreState = true
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = remember { ActivityResultContracts.RequestPermission() },
        onResult = {},
    )
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in setOf(Routes.DASHBOARD, Routes.CALENDAR, Routes.ACTIVITY, Routes.COACH, Routes.NUTRITION)) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.DASHBOARD,
                        onClick = { navigateToTab(Routes.DASHBOARD) },
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Dashboard") },
                        label = { NavLabel("Dashboard") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.CALENDAR,
                        onClick = { navigateToTab(Routes.CALENDAR) },
                        icon = { Icon(Icons.Filled.CalendarMonth, contentDescription = "Calendar") },
                        label = { NavLabel("Calendar") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.ACTIVITY,
                        onClick = { navigateToTab(Routes.ACTIVITY) },
                        icon = { Icon(Icons.Filled.History, contentDescription = "Activity") },
                        label = { NavLabel("Activity") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.COACH,
                        onClick = { navigateToTab(Routes.COACH) },
                        icon = { Icon(Icons.Filled.Insights, contentDescription = "Coach") },
                        label = { NavLabel("Coach") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.NUTRITION,
                        onClick = { navigateToTab(Routes.NUTRITION) },
                        icon = { Icon(Icons.Filled.Restaurant, contentDescription = "Nutrition") },
                        label = { NavLabel("Nutrition") },
                    )
                }
            }
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            RoyalMilesNavHost(
                navController = navController,
                repository = repository,
                athleteProfileRepository = athleteProfileRepository,
            )
        }
    }
}
