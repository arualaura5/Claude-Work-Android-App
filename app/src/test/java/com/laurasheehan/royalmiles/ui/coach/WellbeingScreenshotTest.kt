package com.laurasheehan.royalmiles.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.laurasheehan.royalmiles.ui.theme.RoyalMilesTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime

/** The wellbeing page as the mapper builds it from a coach payload of her real numbers. */
class WellbeingScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(fontScale = 1.15f),
        theme = "android:Theme.Material.Light.NoActionBar",
        renderingMode = SessionParams.RenderingMode.V_SCROLL,
        maxPercentDifference = 0.1,
    )

    private fun render(ui: WellbeingUi, dark: Boolean = false) = paparazzi.snapshot {
        RoyalMilesTheme(darkTheme = dark) {
            Box(Modifier.background(MaterialTheme.colorScheme.background).fillMaxWidth()) {
                WellbeingContent(ui)
            }
        }
    }

    private fun build(payload: com.laurasheehan.royalmiles.data.coach.CoachPayload) =
        WellbeingMapper.build(payload, WellbeingSamples.sessions, WellbeingSamples.today, LocalTime.of(8, 30))!!

    @Test
    fun wellbeingPage() = render(build(WellbeingSamples.payload()))

    @Test
    fun wellbeingPageDark() = render(build(WellbeingSamples.payload()), dark = true)

    @Test
    fun coachSuggestsAChange() = render(
        build(
            WellbeingSamples.payload(
                sessionCheck = """{"date":"2026-10-04","verdict":"shorten","reason":"Two short nights."}""",
                suggestion = """{"action":"replace","date":"2026-10-04","headline":"Make Sunday 8 km, not 10.","reason":"HRV 54 ms and two short nights.",
                    "replace_with":{"type":"LONG_RUN","title":"Long run","target_distance_km":8}}""",
            ),
        ),
    )
}
