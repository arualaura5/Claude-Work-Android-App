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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
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

@Composable
private fun RoyalMilesRoot(
    repository: com.laurasheehan.royalmiles.data.PlanRepository,
    athleteProfileRepository: com.laurasheehan.royalmiles.data.AthleteProfileRepository,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.hierarchy?.firstOrNull()?.route

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
            if (currentRoute == Routes.DASHBOARD || currentRoute == Routes.CALENDAR || currentRoute == Routes.NUTRITION) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.DASHBOARD,
                        onClick = {
                            navController.navigate(Routes.DASHBOARD) {
                                popUpTo(Routes.DASHBOARD) { inclusive = true }
                            }
                        },
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Dashboard") },
                        label = { Text("Dashboard") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.CALENDAR,
                        onClick = { navController.navigate(Routes.CALENDAR) },
                        icon = { Icon(Icons.Filled.CalendarMonth, contentDescription = "Calendar") },
                        label = { Text("Calendar") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.NUTRITION,
                        onClick = { navController.navigate(Routes.NUTRITION) },
                        icon = { Icon(Icons.Filled.Restaurant, contentDescription = "Nutrition") },
                        label = { Text("Nutrition") },
                    )
                }
            }
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(padding)) {
            RoyalMilesNavHost(
                navController = navController,
                repository = repository,
                athleteProfileRepository = athleteProfileRepository,
            )
        }
    }
}
