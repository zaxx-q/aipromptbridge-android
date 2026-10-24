package com.aipromptbridge.feature.textedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aipromptbridge.AppContainer
import com.aipromptbridge.core.engine.AiEngine
import com.aipromptbridge.core.model.ChatMessage
import com.aipromptbridge.core.model.Role
import com.aipromptbridge.core.prompt.PromptAction
import com.aipromptbridge.core.prompt.PromptCatalog
import com.aipromptbridge.core.prompt.SettingKeys
import com.aipromptbridge.core.provider.FailureKind
import com.aipromptbridge.core.provider.GenerateResult
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

class ChatViewModel(
    private val engine: AiEngine,
    private val catalog: PromptCatalog,
    private val sessions: SessionRepository,
    val selectedText: String,
    val isReadOnly: Boolean,
    val initialActionLabel: String = "Selected Text",
) : ViewModel() {

    private val _messages = MutableStateFlow<List<StoredMessage>>(emptyList())
    val messages: StateFlow<List<StoredMessage>> = _messages.asStateFlow()

    private val _assistantState = MutableStateFlow<ChatAssistantState>(ChatAssistantState.Idle)
    val assistantState: StateFlow<ChatAssistantState> = _assistantState.asStateFlow()

    private val _bar = MutableStateFlow<List<PromptAction>>(emptyList())
    val bar: StateFlow<List<PromptAction>> = _bar.asStateFlow()

    private val _profileLabel = MutableStateFlow("")
    val profileLabel: StateFlow<String> = _profileLabel.asStateFlow()

    private var activeSession: ChatSession? = null
    private var systemInstruction: String = ""
    private var job: Job? = null
    private var lastUserMessage: String? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val config = catalog.config()
            val tool = config.tool(ToolId.TEXT_EDIT)
            systemInstruction = tool.setting(SettingKeys.CHAT_SYSTEM_INSTRUCTION).ifBlank {
                config.chatWindowSystemInstruction
            }
            _bar.value = tool.barActions()
            val profile = engine.resolveProfile()
            _profileLabel.value = "${profile.name} · ${profile.model}"

            // Create or initialize the session
            val title = selectedText.take(40).ifBlank { "Text Edit Chat" }
            val session = sessions.create(
                origin = "textedit:chat",
                title = title,
                now = System.currentTimeMillis(),
            )
            activeSession = session
        }
    }

    fun send(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isBlank() || _assistantState.value is ChatAssistantState.Generating) return

        job?.cancel()
        lastUserMessage = trimmed
        _assistantState.value = ChatAssistantState.Generating

        job = viewModelScope.launch {
            val session = activeSession ?: withContext(Dispatchers.IO) {
                sessions.create(
                    origin = "textedit:chat",
                    title = selectedText.take(40).ifBlank { "Text Edit Chat" },
                    now = System.currentTimeMillis(),
                ).also { activeSession = it }
            }

            val isFirstMessage = session.messages.isEmpty()
            val messageToSend = if (isFirstMessage) {
                "${ContextMarker.forAction(initialActionLabel)}\n\n$selectedText\n\n$trimmed"
            } else {
                trimmed
            }

            val now = System.currentTimeMillis()
            // In the UI, display the user's actual prompt trimmed
            val updatedUserSession = session.withMessage(Role.USER, trimmed, now)
            activeSession = updatedUserSession
            _messages.value = updatedUserSession.messages

            withContext(Dispatchers.IO) {
                sessions.save(updatedUserSession)
            }

            // Build full messages list to send to engine
            val historyForAi = mutableListOf<ChatMessage>()
            if (systemInstruction.isNotBlank()) {
                historyForAi += ChatMessage.system(systemInstruction)
            }
            // For AI request, if first message, include context marker + selected text
            val stored = updatedUserSession.messages
            stored.forEachIndexed { index, msg ->
                val role = when (msg.role) {
                    "assistant" -> Role.ASSISTANT
                    "system" -> Role.SYSTEM
                    else -> Role.USER
                }
                val content = if (index == 0 && role == Role.USER) messageToSend else msg.text
                historyForAi += ChatMessage(role, listOf(com.aipromptbridge.core.model.ContentPart.Text(content)))
            }

            when (val result = engine.chat(historyForAi, sessionProfile = updatedUserSession.profileOverride)) {
                is GenerateResult.Success -> {
                    val reply = result.content.trim()
                    val sessionWithReply = updatedUserSession.withMessage(Role.ASSISTANT, reply, System.currentTimeMillis())
                    activeSession = sessionWithReply
                    _messages.value = sessionWithReply.messages
                    _assistantState.value = ChatAssistantState.Idle
                    withContext(Dispatchers.IO) {
                        sessions.save(sessionWithReply)
                    }
                }
                is GenerateResult.Failure -> {
                    _assistantState.value = ChatAssistantState.Error(
                        message = result.message,
                        missingApiKey = result.kind == FailureKind.NO_API_KEY,
                    )
                }
            }
        }
    }

    fun runAction(action: PromptAction) {
        val prompt = action.task.ifBlank { action.label }
        send(prompt)
    }

    fun retry() {
        val lastUser = lastUserMessage ?: return
        // Remove the last assistant message if there was one, or just re-send
        send(lastUser)
    }

    fun stop() {
        job?.cancel()
        _assistantState.value = ChatAssistantState.Idle
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
