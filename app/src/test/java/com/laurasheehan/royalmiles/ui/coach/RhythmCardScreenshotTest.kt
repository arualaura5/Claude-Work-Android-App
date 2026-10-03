package com.laurasheehan.royalmiles.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.laurasheehan.royalmiles.ui.theme.RoyalMilesTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime

/** The rhythm charts on her real fortnight: her usual range, and a day's values read against it. */
class RhythmCardScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(fontScale = 1.15f),
        theme = "android:Theme.Material.NoActionBar",
        renderingMode = SessionParams.RenderingMode.V_SCROLL,
        maxPercentDifference = 0.1,
    )

    private val rhythm = WellbeingMapper.build(
        WellbeingSamples.payload(), WellbeingSamples.sessions, WellbeingSamples.today, LocalTime.of(20, 0),
    )!!.rhythm

    private fun render(selection: Int? = null) = paparazzi.snapshot {
        RoyalMilesTheme(darkTheme = true) {
            Box(Modifier.background(MaterialTheme.colorScheme.background).fillMaxWidth().padding(16.dp)) {
                RhythmCard(rhythm, days = 14, initialSelection = selection)
            }
        }
    }

    /** Opens on last night. */
    @Test
    fun opensOnLastNight() = render()

    /** 28 September tapped: HRV above her range, sleep below it. */
    @Test
    fun aTappedDay() = render(selection = 8)
}
