package com.laurasheehan.royalmiles.ui.coach

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laurasheehan.royalmiles.data.coach.CoachPayload
import com.laurasheehan.royalmiles.data.coach.CoachRepository
import com.laurasheehan.royalmiles.data.coach.CoachState
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CoachUiState(
    val loading: Boolean = false,
    val payload: CoachPayload? = null,
    val sourceRemembered: Boolean = false,
    /** Days between the newest data in the payload and today. Null when there is no data date. */
    val dataAgeDays: Long? = null,
    val error: String? = null,
)

class CoachViewModel(
    private val repository: CoachRepository,
) : ViewModel() {

    private val _transient = MutableStateFlow(TransientState())

    val uiState: StateFlow<CoachUiState> = combine(
        repository.state,
        _transient,
    ) { state, transient ->
        val payload = (state as? CoachState.Loaded)?.payload
        CoachUiState(
            loading = transient.loading,
            payload = payload,
            sourceRemembered = (state as? CoachState.Loaded)?.sourceRemembered ?: false,
            dataAgeDays = payload?.let { daysSince(it.freshness.dbDailyMaxDate) },
            error = transient.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CoachUiState())

    fun import(uri: Uri) {
        viewModelScope.launch {
            _transient.value = TransientState(loading = true)
            val result = repository.import(uri)
            _transient.value = TransientState(
                loading = false,
                error = result.exceptionOrNull()?.message,
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _transient.value = TransientState(loading = true)
            val result = repository.refreshFromRememberedSource()
            _transient.value = TransientState(
                loading = false,
                // A null result means nothing is remembered yet, which is not an error worth showing.
                error = result?.exceptionOrNull()?.let {
                    "Couldn't re-read that file — pick it again. (${it.message})"
                },
            )
        }
    }

    fun dismissError() {
        _transient.value = _transient.value.copy(error = null)
    }

    private fun daysSince(date: String?): Long? {
        val parsed = date ?: return null
        return try {
            ChronoUnit.DAYS.between(LocalDate.parse(parsed), LocalDate.now())
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private data class TransientState(
        val loading: Boolean = false,
        val error: String? = null,
    )
}
