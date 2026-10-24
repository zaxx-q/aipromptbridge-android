package com.aipromptbridge.feature.textedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aipromptbridge.AppContainer
import com.aipromptbridge.core.engine.AiEngine
import com.aipromptbridge.core.model.ContentPart
import com.aipromptbridge.core.model.Role
import com.aipromptbridge.core.prompt.PromptAction
import com.aipromptbridge.core.prompt.PromptCatalog
import com.aipromptbridge.core.prompt.SettingKeys
import com.aipromptbridge.core.provider.FailureKind
import com.aipromptbridge.core.provider.GenerateResult
import com.aipromptbridge.core.session.ChatHistory
import com.aipromptbridge.core.session.ChatSeed
import com.aipromptbridge.core.session.ChatSession
import com.aipromptbridge.core.session.ContextMarker
import com.aipromptbridge.core.session.SessionRepository
import com.aipromptbridge.core.session.StoredMessage
import com.aipromptbridge.core.tool.ToolId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ChatAssistantState {
    data object Idle : ChatAssistantState
    data object Generating : ChatAssistantState
    data class Error(val message: String, val missingApiKey: Boolean) : ChatAssistantState
}

/**
 * Multi-turn chat used by Text Edit (about a text selection) and Snip (about a captured image).
 *
 * What the model sees is built by [ChatHistory]: stored text messages, with the first user message replaced by the
 * context-carrying version ([firstModelText]) on EVERY request, plus the optional [image] on that same message.
 * Sessions on disk stay text-only; the image lives in memory for as long as this ViewModel does.
 */
class ChatViewModel(
    private val engine: AiEngine,
    private val catalog: PromptCatalog,
    private val sessions: SessionRepository,
    val selectedText: String,
    val isReadOnly: Boolean,
    val initialActionLabel: String = "Selected Text",
    /** Session origin, e.g. "textedit:chat" or "snip:describe". */
    private val origin: String = "textedit:chat",
    private val title: String = selectedText.take(40).ifBlank { "Text Edit Chat" },
    /** Whose one-tap actions the chat offers: Text Edit's or Snip's. */
    private val barTool: ToolId = ToolId.TEXT_EDIT,
    /** Sent with the first user message of every request (in memory only). */
    private val image: ContentPart.Image? = null,
    /** An exchange that already happened (a Snip result) which this chat continues from. */
    private val seed: ChatSeed? = null,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<StoredMessage>>(emptyList())
    val messages: StateFlow<List<StoredMessage>> = _messages.asStateFlow()

    private val _assistantState = MutableStateFlow<ChatAssistantState>(ChatAssistantState.Idle)
    val assistantState: StateFlow<ChatAssistantState> = _assistantState.asStateFlow()

    private val _bar = MutableStateFlow<List<PromptAction>>(emptyList())
    val bar: StateFlow<List<PromptAction>> = _bar.asStateFlow()

    private val _profileLabel = MutableStateFlow("")
    val profileLabel: StateFlow<String> = _profileLabel.asStateFlow()

    @Volatile private var activeSession: ChatSession? = null
    @Volatile private var systemInstruction: String = ""
    private var job: Job? = null

    /** What the model is told the first user message was. Null until the first message of a text chat is sent. */
    private var firstModelText: String? = seed?.modelUserText

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val config = catalog.config()
            systemInstruction = config.tool(ToolId.TEXT_EDIT).setting(SettingKeys.CHAT_SYSTEM_INSTRUCTION).ifBlank {
                config.chatWindowSystemInstruction
            }
            _bar.value = config.tool(barTool).barActions()
            val profile = engine.resolveProfile()
            _profileLabel.value = "${profile.name} · ${profile.model}"

            ensureSession()
        }
    }

    /**
     * Creates the session once (including the seeded exchange, if any). Both [init] and [send] go through here, so a
     * message sent before start-up finished can never end up in a second, unseeded session. Blocking: IO only.
     */
    @Synchronized
    private fun ensureSession(): ChatSession {
        activeSession?.let { return it }
        val now = System.currentTimeMillis()
        var session = sessions.create(origin = origin, title = title, now = now)
        if (seed != null) {
            session = session
                .withMessage(Role.USER, seed.userText, now)
                .withMessage(Role.ASSISTANT, seed.assistantText, now)
            sessions.save(session)
            _messages.value = session.messages
        }
        activeSession = session
        return session
    }

    fun send(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isBlank() || _assistantState.value is ChatAssistantState.Generating) return

        // The first message of a text chat carries the task marker and the selected text for the model; the chat
        // itself only shows what the user typed. (A seeded chat already has its first message.)
        if (firstModelText == null) {
            firstModelText = "${ContextMarker.forAction(initialActionLabel)}\n\n$selectedText\n\n$trimmed"
        }

        job?.cancel()
        _assistantState.value = ChatAssistantState.Generating
        job = viewModelScope.launch {
            val base = withContext(Dispatchers.IO) { ensureSession() }
            val withUser = base.withMessage(Role.USER, trimmed, System.currentTimeMillis())
            commit(withUser)
            requestReply(withUser)
        }
    }

    fun runAction(action: PromptAction) {
        send(action.task.ifBlank { action.label })
    }

    /**
     * Asks again WITHOUT adding another user turn: after an error the failed question is still the last message and is
     * simply re-sent; after an answer, that answer is dropped and regenerated.
     */
    fun retry() {
        val session = activeSession ?: return
        val last = session.messages.lastOrNull() ?: return
        if (_assistantState.value is ChatAssistantState.Generating) return

        job?.cancel()
        _assistantState.value = ChatAssistantState.Generating
        job = viewModelScope.launch {
            val basis = if (last.role == "assistant") {
                session.withoutLastMessage(System.currentTimeMillis()).also { commit(it) }
            } else {
                session
            }
            requestReply(basis)
        }
    }

    fun stop() {
        job?.cancel()
        _assistantState.value = ChatAssistantState.Idle
    }

    private suspend fun requestReply(session: ChatSession) {
        val history = ChatHistory.build(systemInstruction, session.messages, firstModelText, image)
        when (val result = engine.chat(history, sessionProfile = session.profileOverride)) {
            is GenerateResult.Success -> {
                commit(session.withMessage(Role.ASSISTANT, result.content.trim(), System.currentTimeMillis()))
                _assistantState.value = ChatAssistantState.Idle
            }
            is GenerateResult.Failure -> {
                _assistantState.value = ChatAssistantState.Error(
                    message = result.message,
                    missingApiKey = result.kind == FailureKind.NO_API_KEY,
                )
            }
        }
    }

    /** Publishes [session] to the UI immediately, then persists it off the main thread. */
    private suspend fun commit(session: ChatSession) {
        activeSession = session
        _messages.value = session.messages
        withContext(Dispatchers.IO) { sessions.save(session) }
    }
}

class ChatViewModelFactory(
    private val container: AppContainer,
    private val selectedText: String,
    private val isReadOnly: Boolean,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ChatViewModel(
            container.engine,
            container.promptCatalog,
            container.sessions,
            selectedText,
            isReadOnly,
        ) as T
}
