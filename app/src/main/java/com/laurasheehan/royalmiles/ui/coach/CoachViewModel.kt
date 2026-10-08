package com.laurasheehan.royalmiles.ui.coach

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laurasheehan.royalmiles.data.coach.CoachPayload
import com.laurasheehan.royalmiles.data.coach.CoachRepository
import com.laurasheehan.royalmiles.data.coach.CoachState
import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.data.coach.chat.ChatProtocol
import com.laurasheehan.royalmiles.data.coach.chat.ChatRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CoachUiState(
    val loading: Boolean = false,
    val payload: CoachPayload? = null,
    val sourceRemembered: Boolean = false,
    /** Days between the newest data in the payload and today. Null when there is no data date. */
    val dataAgeDays: Long? = null,
    val error: String? = null,
    /** The wellbeing page, when the payload carries a month of data. Null falls back to the older layout. */
    val wellbeing: WellbeingUi? = null,
    /** "Send to watch" for the session on the card; null when there is nothing to send or no chat link. */
    val watch: WatchUi? = null,
)

/** Whether the session on the card is on its way to her watch. Nothing goes there without her yes. */
data class WatchUi(
    val status: Status,
    val busy: Boolean = false,
    /** What just happened, in a line: shown under the button until the next change. */
    val message: String? = null,
) {
    enum class Status { NOT_SENT, SENT, CHANGED_SINCE_SENT }
}

class CoachViewModel(
    private val repository: CoachRepository,
    private val sessions: Flow<List<SessionEntity>> = flowOf(emptyList()),
    /** The coach chat link, which also carries "Send to watch". Null leaves the button off. */
    private val chat: ChatRepository? = null,
) : ViewModel() {

    private val _transient = MutableStateFlow(TransientState())
    private val _watch = MutableStateFlow(WatchState())

    // Re-reads the clock every few minutes, so the greeting, "tomorrow" and the next session stay
    // right when the tab is left open across midnight.
    private val clock = flow {
        while (true) {
            emit(Unit)
            delay(5 * 60 * 1000L)
        }
    }

    val uiState: StateFlow<CoachUiState> = combine(
        repository.state,
        _transient,
        sessions,
        clock,
        _watch,
    ) { state, transient, plan, _, watch ->
        val payload = (state as? CoachState.Loaded)?.payload
        val wellbeing = payload?.let { WellbeingMapper.build(it, plan, LocalDate.now(), LocalTime.now()) }
        CoachUiState(
            loading = transient.loading,
            payload = payload,
            sourceRemembered = (state as? CoachState.Loaded)?.sourceRemembered ?: false,
            dataAgeDays = payload?.let { daysSince(it.freshness.dbDailyMaxDate) },
            error = transient.error,
            wellbeing = wellbeing,
            watch = watchUi(wellbeing?.training, plan, watch),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CoachUiState())

    init {
        if (repository.isRemoteConnected()) refresh()
        loadWatch()
    }

    private fun watchUi(training: WellbeingUi.TrainingContext?, plan: List<SessionEntity>, watch: WatchState): WatchUi? {
        if (chat == null || !watch.linked || training == null || !training.canSendToWatch) return null
        val session = plan.firstOrNull { it.id == training.sessionId } ?: return null
        val sent = watch.sent[session.date.toString()]
        val status = when {
            sent == null -> WatchUi.Status.NOT_SENT
            ChatProtocol.sameAsSent(session, sent) -> WatchUi.Status.SENT
            else -> WatchUi.Status.CHANGED_SINCE_SENT
        }
        return WatchUi(status, busy = watch.busy, message = watch.message)
    }

    private fun loadWatch() {
        val link = chat ?: return
        if (!link.isConnected()) return
        viewModelScope.launch {
            link.watchSessions().onSuccess { sent -> _watch.value = _watch.value.copy(linked = true, sent = sent) }
        }
    }

    /** Her yes: this run, exactly as shown, goes to her watch. */
    fun sendToWatch(sessionId: Long) {
        val link = chat ?: return
        viewModelScope.launch {
            val session = sessions.first().firstOrNull { it.id == sessionId } ?: return@launch
            _watch.value = _watch.value.copy(busy = true, message = null)
            val result = link.sendToWatch(session, LocalDate.now())
            val sent = link.watchSessions().getOrNull() ?: _watch.value.sent
            _watch.value = _watch.value.copy(
                busy = false,
                sent = sent,
                message = result.fold(
                    onSuccess = { now ->
                        if (now) "On its way: on your watch after its next sync, in a few minutes."
                        else "Saved: it goes to your watch with the next refresh."
                    },
                    onFailure = { "Couldn't send it. ${it.message}" },
                ),
            )
        }
    }

    /** Takes a run she sent back off her watch. */
    fun takeOffWatch(sessionId: Long) {
        val link = chat ?: return
        viewModelScope.launch {
            val session = sessions.first().firstOrNull { it.id == sessionId } ?: return@launch
            _watch.value = _watch.value.copy(busy = true, message = null)
            val result = link.takeOffWatch(session.date)
            val sent = link.watchSessions().getOrNull() ?: _watch.value.sent
            _watch.value = _watch.value.copy(
                busy = false,
                sent = sent,
                message = result.fold(
                    onSuccess = { "Taken off: it leaves your watch on its next sync." },
                    onFailure = { "Couldn't take it off. ${it.message}" },
                ),
            )
        }
    }

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

    fun connect(address: String, key: String) {
        viewModelScope.launch {
            _transient.value = TransientState(loading = true)
            val result = repository.connectRemote(address, key)
            _transient.value = TransientState(
                loading = false,
                error = result.exceptionOrNull()?.let { "Couldn't connect to the cloud coach. ${it.message}" },
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
                    "Couldn't refresh coach data. ${it.message}"
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

    private data class WatchState(
        /** True once the chat Worker has answered, so the button never shows without a working link. */
        val linked: Boolean = false,
        val sent: Map<String, org.json.JSONObject> = emptyMap(),
        val busy: Boolean = false,
        val message: String? = null,
    )

    private data class TransientState(
        val loading: Boolean = false,
        val error: String? = null,
    )
}
