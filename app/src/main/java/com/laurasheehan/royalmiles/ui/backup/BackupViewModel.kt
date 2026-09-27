package com.laurasheehan.royalmiles.ui.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laurasheehan.royalmiles.data.backup.BackupPreview
import com.laurasheehan.royalmiles.data.backup.Inspection
import com.laurasheehan.royalmiles.data.backup.LogBackup
import com.laurasheehan.royalmiles.data.backup.SafetyCopies
import com.laurasheehan.royalmiles.data.backup.SafetyCopy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class BackupUiState(
    val lastSavedAtMillis: Long? = null,
    val working: String? = null,
    /** A checked backup waiting for her to confirm, and where it came from. */
    val pending: Inspection.Ready? = null,
    val pendingSource: String? = null,
    val message: String? = null,
    val problem: String? = null,
    val safetyCopies: List<SafetyCopy> = emptyList(),
)

class BackupViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    private val backup = LogBackup(appContext)
    private val safety = SafetyCopies(appContext)
    private val prefs = appContext.getSharedPreferences("backup", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        BackupUiState(
            lastSavedAtMillis = prefs.getLong(KEY_LAST_SAVED, 0L).takeIf { it > 0 },
            safetyCopies = safety.list(),
        ),
    )
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun suggestedFileName(today: LocalDate = LocalDate.now()) = "royal-miles-backup-$today.db"

    fun save(uri: Uri) = run("Saving your backup…") {
        val preview = backup.exportTo(uri)
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_SAVED, now).apply()
        _state.update { it.copy(lastSavedAtMillis = now, message = "Saved: ${describe(preview)}.") }
    }

    fun check(uri: Uri) = run("Checking the backup…") {
        show(backup.inspect(uri), source = "the file you picked")
    }

    fun check(copy: SafetyCopy) = run("Checking the copy…") {
        show(backup.inspect(copy), source = copy.label.replaceFirstChar { it.lowercase() })
    }

    fun confirmRestore() {
        val ready = _state.value.pending ?: return
        run("Restoring…") {
            val preview = backup.restore(ready)
            _state.update {
                it.copy(
                    pending = null,
                    pendingSource = null,
                    safetyCopies = safety.list(),
                    message = "Restored: ${describe(preview)}. Your previous log was kept as a safety copy below.",
                )
            }
        }
    }

    fun cancelRestore() {
        _state.value.pending?.let(backup::discard)
        _state.update { it.copy(pending = null, pendingSource = null) }
    }

    private fun show(inspection: Inspection, source: String) {
        when (inspection) {
            is Inspection.Ready -> _state.update { it.copy(pending = inspection, pendingSource = source) }
            is Inspection.Problem -> _state.update { it.copy(problem = inspection.message) }
        }
    }

    private fun run(label: String, block: suspend () -> Unit) {
        if (_state.value.working != null) return
        _state.update { it.copy(working = label, message = null, problem = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (error: Exception) {
                _state.update { it.copy(problem = error.message ?: "That didn't work. Nothing was changed.") }
            } finally {
                _state.update { it.copy(working = null) }
            }
        }
    }

    companion object {
        private const val KEY_LAST_SAVED = "last_saved_at"

        fun describe(preview: BackupPreview): String {
            val range = if (preview.firstDate != null && preview.lastDate != null) {
                ", ${BackupText.date(preview.firstDate)} to ${BackupText.date(preview.lastDate)}"
            } else {
                ""
            }
            return "${preview.sessions} sessions, ${preview.completed} done$range"
        }
    }
}
