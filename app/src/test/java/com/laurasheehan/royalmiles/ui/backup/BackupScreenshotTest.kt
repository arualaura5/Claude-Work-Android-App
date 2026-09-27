package com.laurasheehan.royalmiles.ui.backup

import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.laurasheehan.royalmiles.data.backup.BackupPreview
import com.laurasheehan.royalmiles.data.backup.Inspection
import com.laurasheehan.royalmiles.data.backup.SafetyCopy
import com.laurasheehan.royalmiles.ui.theme.RoyalMilesTheme
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

class BackupScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar",
        maxPercentDifference = 0.1,
    )

    private val now = 1_790_500_000_000L
    private val day = 86_400_000L

    private val copies = listOf(
        SafetyCopy(File("a.db"), "before-restore", now - 2 * day, "Before a restore replaced your log"),
        SafetyCopy(File("b.db"), "before-upgrade-v10-to-v11", now - 9 * day, "Before the app upgraded its database (version 10 to 11)"),
    )

    @Composable
    private fun content(state: BackupUiState) = BackupContent(
        state = state,
        onBack = {},
        onSave = {},
        onPickFile = {},
        onPickCopy = {},
        onConfirm = {},
        onCancel = {},
        nowMillis = now,
    )

    @Test
    fun backupScreen() {
        paparazzi.snapshot {
            RoyalMilesTheme(darkTheme = true) {
                content(
                    BackupUiState(
                        lastSavedAtMillis = now - 3 * day,
                        message = "Saved: 312 sessions, 98 done, 3 Feb 2026 to 1 Nov 2026.",
                        safetyCopies = copies,
                    ),
                )
            }
        }
    }

    @Test
    fun restorePreview() {
        val preview = BackupPreview("2026-09-24T19:02:00Z", "af19d0c", 10, 9, 305, 96, LocalDate.of(2026, 2, 3), LocalDate.of(2026, 11, 1))
        paparazzi.snapshot {
            RoyalMilesTheme(darkTheme = true) {
                content(
                    BackupUiState(
                        lastSavedAtMillis = null,
                        pending = Inspection.Ready(File("candidate.db"), preview),
                        pendingSource = "the file you picked",
                    ),
                )
            }
        }
    }
}
