package com.laurasheehan.royalmiles

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.laurasheehan.royalmiles.ui.theme.RoyalMilesTheme

/**
 * Required by Android 14+ (and by Health Connect itself) for any app requesting health
 * permissions: the system links here from its own permissions screen, and — on some OS
 * versions — the permission request dialog won't even open without this activity declared.
 */
class HealthConnectRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RoyalMilesTheme {
                Scaffold { padding ->
                    Column(
                        modifier = Modifier
                            .padding(padding)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Health Connect data use", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Royal Miles reads workout sessions and nutrition entries you've already " +
                                "logged in apps like Strava, Garmin Connect, Google Fit, or Cronometer — " +
                                "purely to show them on your dashboard and let you match a workout to a " +
                                "planned training session. Nothing is uploaded, shared, or sent anywhere; " +
                                "it stays on this device, in this app's own local database.",
                        )
                        Button(onClick = { finish() }) { Text("Done") }
                    }
                }
            }
        }
    }
}
