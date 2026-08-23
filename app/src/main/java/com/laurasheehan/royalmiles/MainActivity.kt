package com.laurasheehan.royalmiles

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.laurasheehan.royalmiles.navigation.RoyalMilesNavHost
import com.laurasheehan.royalmiles.navigation.Routes
import com.laurasheehan.royalmiles.ui.theme.RoyalMilesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as RoyalMilesApp).repository

        setContent {
            RoyalMilesTheme {
                LaunchedEffect(Unit) {
                    repository.ensureSeeded(raceDate = RaceConfig.RACE_DATE, peakLongRunKm = RaceConfig.PEAK_LONG_RUN_KM)
                }
                RoyalMilesRoot(repository)
            }
        }
    }
}

@Composable
private fun RoyalMilesRoot(repository: com.laurasheehan.royalmiles.data.PlanRepository) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.hierarchy?.firstOrNull()?.route

    Scaffold(
        bottomBar = {
            if (currentRoute == Routes.DASHBOARD || currentRoute == Routes.CALENDAR) {
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
                }
            }
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(padding)) {
            RoyalMilesNavHost(navController = navController, repository = repository)
        }
    }
}
