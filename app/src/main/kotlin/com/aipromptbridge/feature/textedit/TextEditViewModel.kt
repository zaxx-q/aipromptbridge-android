package com.aipromptbridge.feature.textedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aipromptbridge.AppContainer
import com.aipromptbridge.core.diff.DiffSegment
import com.aipromptbridge.core.diff.WordDiff
import com.aipromptbridge.core.engine.AiEngine
import com.aipromptbridge.core.prompt.PromptAction
import com.aipromptbridge.core.prompt.PromptCatalog
import com.aipromptbridge.core.prompt.PromptComposer
import com.aipromptbridge.core.prompt.PromptInput
import com.aipromptbridge.core.prompt.PromptModifier
import com.aipromptbridge.core.prompt.PromptRequest
import com.aipromptbridge.core.prompt.ResultDestination
import com.aipromptbridge.core.provider.FailureKind
import com.aipromptbridge.core.provider.GenerateResult
import com.aipromptbridge.core.tool.ToolId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the sheet is showing. A small state machine: Idle -> Loading -> Result | Error (-> Loading again). */
sealed interface EditUiState {
    data object Idle : EditUiState
    data class Loading(val actionId: String, val actionLabel: String) : EditUiState

    /** [diff] is non-null only when the result may REPLACE the selection (edit actions). */
    data class Result(
        val actionId: String,
        val actionLabel: String,
        val text: String,
        val diff: List<DiffSegment>?,
    ) : EditUiState

    data class Error(val message: String, val missingApiKey: Boolean) : EditUiState
}

class TextEditViewModel(
    private val engine: AiEngine,
    private val catalog: PromptCatalog,
    val selectedText: String,
    val isReadOnly: Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow<EditUiState>(EditUiState.Idle)
    val state: StateFlow<EditUiState> = _state.asStateFlow()

    private val _bar = MutableStateFlow<List<PromptAction>>(emptyList())
    /** One-tap actions in user-defined order (PromptCatalog barOrder). */
    val bar: StateFlow<List<PromptAction>> = _bar.asStateFlow()

    private val _modifiers = MutableStateFlow<List<PromptModifier>>(emptyList())
    /** Toggleable instructions (Shorter, Formal, ...). Shared by all tools, so they come from the global config. */
    val modifiers: StateFlow<List<PromptModifier>> = _modifiers.asStateFlow()

    private val _activeModifiers = MutableStateFlow<Set<String>>(emptySet())
    /**
     * Keys of the modifiers that are switched on. Like the original popup they apply to the NEXT run (and every run
     * after it, including the free-text instruction) and are forgotten when the sheet closes.
     */
    val activeModifiers: StateFlow<Set<String>> = _activeModifiers.asStateFlow()

    private val _profileLabel = MutableStateFlow("")
    val profileLabel: StateFlow<String> = _profileLabel.asStateFlow()

    private val _autoReplace = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** Emits result text when an action with autoApply finishes (the activity then replaces + closes). */
    val autoReplace: SharedFlow<String> = _autoReplace.asSharedFlow()

    private var job: Job? = null
    private var lastRequest: Pair<PromptAction, String?>? = null
    @Volatile private var customAction: PromptAction? = null

    init {
        // Disk access (first prompt load merges + saves JSON) must not happen on the main thread.
        viewModelScope.launch(Dispatchers.IO) {
            val tool = catalog.tool(ToolId.TEXT_EDIT)
            customAction = tool.actions[PromptComposer.CUSTOM_ID]
            _bar.value = tool.barActions()
            _modifiers.value = catalog.config().modifiers
            val profile = engine.resolveProfile()
            _profileLabel.value = "${profile.name} · ${profile.model}"
        }
    }

    fun run(action: PromptAction, custom: String? = null) {
        job?.cancel()
        lastRequest = action to custom
        _state.value = EditUiState.Loading(action.id, action.label)
        job = viewModelScope.launch {
            val outcome = engine.runAction(
                ToolId.TEXT_EDIT,
                PromptRequest(
                    action,
                    PromptInput.Text(selectedText),
                    customInstruction = custom,
                    activeModifierKeys = _activeModifiers.value,
                ),
            )
            _state.value = when (val result = outcome.result) {
                is GenerateResult.Success -> {
                    val text = result.content.trim()
                    val mayReplace = outcome.destination == ResultDestination.REPLACE
                    if (mayReplace && action.autoApply && !isReadOnly) _autoReplace.tryEmit(text)
                    EditUiState.Result(
                        actionId = action.id,
                        actionLabel = action.label,
                        text = text,
                        diff = if (mayReplace) WordDiff.diff(selectedText, text) else null,
                    )
                }

                is GenerateResult.Failure ->
                    EditUiState.Error(result.message, missingApiKey = result.kind == FailureKind.NO_API_KEY)
            }
        }
    }

    /** Switches one modifier on/off. Call from the main thread (it is a read-modify-write on a StateFlow). */
    fun toggleModifier(key: String) {
        val current = _activeModifiers.value
        _activeModifiers.value = if (key in current) current - key else current + key
    }

    /** The free-text instruction field ("Or tell it what to change…") uses the special _custom action. */
    fun runCustom(instruction: String) {
        val action = customAction ?: return
        run(action, instruction)
    }

    fun retry() {
        lastRequest?.let { (action, custom) -> run(action, custom) }
    }

    /** Cancels the in-flight request (cancels the HTTP call too) and returns to idle. */
    fun stop() {
        job?.cancel()
        _state.value = EditUiState.Idle
    }
}

class TextEditViewModelFactory(
    private val container: AppContainer,
    private val selectedText: String,
    private val isReadOnly: Boolean,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        TextEditViewModel(container.engine, container.promptCatalog, selectedText, isReadOnly) as T
}
