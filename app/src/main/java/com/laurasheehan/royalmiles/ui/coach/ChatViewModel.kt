package com.laurasheehan.royalmiles.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laurasheehan.royalmiles.data.PlanRepository
import com.laurasheehan.royalmiles.data.coach.CoachRepository
import com.laurasheehan.royalmiles.data.coach.applyCoachSuggestion
import com.laurasheehan.royalmiles.data.coach.chat.ChatMessage
import com.laurasheehan.royalmiles.data.coach.chat.ChatRepository
import com.laurasheehan.royalmiles.data.coach.chat.ChatUsage
import com.laurasheehan.royalmiles.data.coach.chat.MemoryNote
import com.laurasheehan.royalmiles.data.coach.chat.MemoryProposal
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
    /** What the coach knows about her; null until the list has been loaded. */
    val memoryNotes: List<MemoryNote>? = null,
    val memoryError: String? = null,
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
        val memoryNotes: List<MemoryNote>? = null,
        val memoryError: String? = null,
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
            memoryNotes = t.memoryNotes,
            memoryError = t.memoryError,
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

    fun send(text: String) = send(text, transient.value.researchMode)

    /** Asks the failed question again, as chat or research, whichever it was. */
    fun retry(notice: ChatMessage) {
        if (transient.value.sending) return
        val failed = chat.takeFailed(notice.id) ?: return
        send(failed.text, failed.research)
    }

    private fun send(text: String, research: Boolean) {
        val message = text.trim()
        if (message.isEmpty() || transient.value.sending) return
        viewModelScope.launch {
            transient.value = transient.value.copy(sending = true)
            val usage = if (research) {
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

    /** Saves the coach's entry for her athlete file, as she edited it. Only called from her tap. */
    fun remember(message: ChatMessage, proposal: MemoryProposal) {
        viewModelScope.launch {
            chat.remember(proposal, message.id)
                .onSuccess { notes -> transient.value = transient.value.copy(memoryNotes = notes, memoryError = null) }
                .onFailure { error -> chat.addNotice(error.message ?: "That entry wasn't saved.") }
        }
    }

    fun notNow(message: ChatMessage) = chat.setMemoryState(message.id, ChatMessage.MemoryState.DISMISSED)

    /** Undo on a saved entry: removed, or the entry it updated is put back. */
    fun unremember(message: ChatMessage) {
        viewModelScope.launch {
            chat.unremember(message.id)
                .onSuccess { notes -> transient.value = transient.value.copy(memoryNotes = notes, memoryError = null) }
                .onFailure { error -> chat.addNotice(error.message ?: "That entry wasn't undone.") }
        }
    }

    /** Edit on a saved entry: the old wording is replaced by hers. */
    fun rewrite(message: ChatMessage, proposal: MemoryProposal) {
        viewModelScope.launch {
            chat.unremember(message.id)
            chat.remember(proposal, message.id)
                .onSuccess { notes -> transient.value = transient.value.copy(memoryNotes = notes, memoryError = null) }
                .onFailure { error -> chat.addNotice(error.message ?: "That entry wasn't saved.") }
        }
    }

    fun loadMemory() {
        viewModelScope.launch {
            chat.memoryNotes()
                .onSuccess { notes -> transient.value = transient.value.copy(memoryNotes = notes, memoryError = null) }
                .onFailure { error -> transient.value = transient.value.copy(memoryError = error.message ?: "Couldn't load your athlete file.") }
        }
    }

    fun forget(note: MemoryNote) {
        viewModelScope.launch {
            chat.forget(note.id)
                .onSuccess { notes -> transient.value = transient.value.copy(memoryNotes = notes, memoryError = null) }
                .onFailure { error -> transient.value = transient.value.copy(memoryError = error.message ?: "Couldn't remove that entry.") }
        }
    }
}
