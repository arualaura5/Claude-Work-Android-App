package com.laurasheehan.royalmiles.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.laurasheehan.royalmiles.core.gamification.Badge
import com.laurasheehan.royalmiles.ui.components.BadgeChip
import com.laurasheehan.royalmiles.ui.theme.RoyalMilesTheme
import org.junit.Rule
import org.junit.Test

/** The dashboard's one-line race banner, and badges new (glowing), settled and still to come. */
class DashboardTopScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(fontScale = 1.15f),
        theme = "android:Theme.Material.NoActionBar",
        renderingMode = SessionParams.RenderingMode.V_SCROLL,
        maxPercentDifference = 0.1,
    )

    @Test
    fun raceBannerAndBadges() = paparazzi.snapshot {
        RoyalMilesTheme(darkTheme = true) {
            Column(
                Modifier.background(MaterialTheme.colorScheme.background).fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RaceBanner(daysToRace = 29)
                Text("Badges", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                val badges = Badge.entries
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BadgeChip(badges[0], unlocked = true, fresh = true)
                    BadgeChip(badges[1], unlocked = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BadgeChip(badges[2], unlocked = true)
                    BadgeChip(badges[3], unlocked = false)
                }
            }
        }
    }
}
