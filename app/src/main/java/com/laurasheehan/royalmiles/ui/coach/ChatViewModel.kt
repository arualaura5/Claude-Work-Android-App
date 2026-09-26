package com.laurasheehan.royalmiles.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laurasheehan.royalmiles.data.PlanRepository
import com.laurasheehan.royalmiles.data.coach.CoachRepository
import com.laurasheehan.royalmiles.data.coach.applyCoachSuggestion
import com.laurasheehan.royalmiles.data.coach.chat.ChatMessage
import com.laurasheehan.royalmiles.data.coach.chat.ChatRepository
import com.laurasheehan.royalmiles.data.coach.chat.ChatUsage
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUiState(
    val connected: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val sending: Boolean = false,
    val researchMode: Boolean = false,
    val usage: ChatUsage? = null,
    val suggestedAddress: String? = null,
    val suggestedKey: String? = null,
    val connectError: String? = null,
    /** Bumped on every successful connect, so an open connection dialog knows to close. */
    val connectionVersion: Int = 0,
)

class ChatViewModel(
    private val chat: ChatRepository,
    private val plan: PlanRepository,
    coach: CoachRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private data class Transient(
        val connected: Boolean,
        val sending: Boolean = false,
        val researchMode: Boolean = false,
        val usage: ChatUsage? = null,
        val connectError: String? = null,
        val connectionVersion: Int = 0,
    )

    private val coachConnection = coach.cloudCredentials()
    private val transient = MutableStateFlow(Transient(connected = chat.isConnected()))

    val uiState: StateFlow<ChatUiState> = combine(chat.messages, transient) { messages, t ->
        ChatUiState(
            connected = t.connected,
            messages = messages,
            sending = t.sending,
            researchMode = t.researchMode,
            usage = t.usage,
            suggestedAddress = ChatRepository.suggestedAddress(coachConnection?.first),
            suggestedKey = coachConnection?.second,
            connectError = t.connectError,
            connectionVersion = t.connectionVersion,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState(connected = chat.isConnected()))

    init {
        if (chat.isConnected()) refreshUsage()
    }

    fun refreshUsage() {
        viewModelScope.launch {
            chat.usage().onSuccess { usage -> transient.value = transient.value.copy(usage = usage) }
        }
    }

    fun connect(address: String, key: String) {
        viewModelScope.launch {
            chat.connect(address, key)
                .onSuccess { usage ->
                    transient.value = transient.value.copy(
                        connected = true,
                        usage = usage,
                        connectError = null,
                        connectionVersion = transient.value.connectionVersion + 1,
                    )
                }
                .onFailure { error ->
                    transient.value = transient.value.copy(connectError = error.message ?: "Couldn't connect.")
                }
        }
    }

    fun toggleResearch() {
        transient.value = transient.value.copy(researchMode = !transient.value.researchMode)
    }

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || transient.value.sending) return
        viewModelScope.launch {
            transient.value = transient.value.copy(sending = true)
            val usage = if (transient.value.researchMode) {
                chat.research(message).getOrNull()?.usage
            } else {
                chat.ask(message, plan.observeSessions().first(), today()).getOrNull()?.usage
            }
            transient.value = transient.value.copy(sending = false, usage = usage ?: transient.value.usage)
        }
    }

    /** Applied through the same path as the daily coach's suggestion, only after she taps accept. */
    fun accept(message: ChatMessage) {
        val proposal = message.proposal ?: return
        viewModelScope.launch {
            val session = plan.observeSessions().first()
                .firstOrNull { it.date.toString() == proposal.date && it.isOutstanding }
            if (session == null) {
                chat.addNotice("There's no open session on ${proposal.date} any more, so nothing was changed.")
                return@launch
            }
            if (plan.applyCoachSuggestion(proposal, session)) {
                chat.setProposalState(message.id, ChatMessage.ProposalState.ACCEPTED)
            }
        }
    }

    fun dismiss(message: ChatMessage) {
        chat.setProposalState(message.id, ChatMessage.ProposalState.DISMISSED)
    }

    fun clearConversation() = chat.clearConversation()
}
