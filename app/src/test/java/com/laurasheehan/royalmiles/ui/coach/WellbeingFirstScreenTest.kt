package com.laurasheehan.royalmiles.ui.coach

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.laurasheehan.royalmiles.ui.theme.RoyalMilesTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime

/** What she sees on opening the Coach tab, at her phone's larger text size: the whole brief should fit. */
class WellbeingFirstScreenTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(fontScale = 1.15f),
        theme = "android:Theme.Material.NoActionBar",
        maxPercentDifference = 0.1,
    )

    @Test
    fun coachTabFirstScreen() {
        val payload = WellbeingSamples.payload()
        val ui = WellbeingMapper.build(payload, WellbeingSamples.sessions, WellbeingSamples.today, LocalTime.of(20, 16))!!
        paparazzi.snapshot {
            RoyalMilesTheme(darkTheme = true) {
                CoachContent(
                    state = CoachUiState(payload = payload, sourceRemembered = true, wellbeing = ui),
                    onRefresh = {}, onConnect = {}, onPick = {}, onOpenChat = {},
                )
            }
        }
    }
}
