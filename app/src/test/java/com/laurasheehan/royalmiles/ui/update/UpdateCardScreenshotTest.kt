package com.laurasheehan.royalmiles.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.laurasheehan.royalmiles.data.update.AvailableUpdate
import com.laurasheehan.royalmiles.data.update.UpdateState
import com.laurasheehan.royalmiles.ui.theme.RoyalMilesTheme
import org.junit.Rule
import org.junit.Test

/** Every state of the update card, stacked, so the wording can be checked in one picture. */
class UpdateCardScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar",
        maxPercentDifference = 0.1,
    )

    private val update = AvailableUpdate(
        versionCode = 58,
        sha = "4f2c9a1",
        apkUrl = "https://example.invalid/royal-miles-4f2c9a1.apk",
        sha256 = "a".repeat(64),
        builtAt = "2026-09-27T08:12:00Z",
    )

    @Test
    fun updateCardStates() {
        paparazzi.snapshot {
            RoyalMilesTheme(darkTheme = true) {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    listOf(
                        UpdateState.Available(update),
                        UpdateState.Downloading(update, 0.42f),
                        UpdateState.NeedsPermission(update),
                        UpdateState.Installing(update),
                        UpdateState.Failed(update, "The download didn't match the published build, so it wasn't installed. Try again."),
                    ).forEach { UpdateCard(it, onInstall = {}, onAllowInstalls = {}, onLater = {}) }
                }
            }
        }
    }
}
