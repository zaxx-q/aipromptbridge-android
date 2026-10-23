package com.aipromptbridge.feature.snip

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aipromptbridge.AppContainer
import com.aipromptbridge.core.engine.AiEngine
import com.aipromptbridge.core.prompt.PromptAction
import com.aipromptbridge.core.prompt.PromptCatalog
import com.aipromptbridge.core.prompt.PromptComposer
import com.aipromptbridge.core.prompt.PromptInput
import com.aipromptbridge.core.prompt.PromptRequest
import com.aipromptbridge.core.prompt.ResponseMode
import com.aipromptbridge.core.prompt.ResultDestination
import com.aipromptbridge.core.provider.FailureKind
import com.aipromptbridge.core.provider.GenerateResult
import com.aipromptbridge.core.snip.PixelRect
import com.aipromptbridge.core.tool.ToolId
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Snip flow: Capturing -> Selecting (drag a region) -> Acting (action sheet). Failed can happen at any point. */
sealed interface SnipStage {
    data object Capturing : SnipStage
    data class Selecting(val screenshot: Bitmap) : SnipStage
    data class Acting(val screenshot: Bitmap, val crop: Bitmap, val pngBase64: String) : SnipStage
    data class Failed(val message: String) : SnipStage
}

sealed interface SnipResult {
    data object Idle : SnipResult
    data class Loading(val actionLabel: String) : SnipResult
    data class Done(val text: String, val destination: ResultDestination) : SnipResult
    data class Error(val message: String, val missingApiKey: Boolean) : SnipResult
}

class SnipViewModel(
    private val engine: AiEngine,
    private val catalog: PromptCatalog,
) : ViewModel() {

    private val _stage = MutableStateFlow<SnipStage>(SnipStage.Capturing)
    val stage: StateFlow<SnipStage> = _stage.asStateFlow()

    private val _result = MutableStateFlow<SnipResult>(SnipResult.Idle)
    val result: StateFlow<SnipResult> = _result.asStateFlow()

    private val _bar = MutableStateFlow<List<PromptAction>>(emptyList())
    val bar: StateFlow<List<PromptAction>> = _bar.asStateFlow()

    /** Where the result goes: SHOW (in the sheet), COPY (clipboard) or SHARE (system share sheet). */
    private val _responseMode = MutableStateFlow(ResponseMode.SHOW)
    val responseMode: StateFlow<ResponseMode> = _responseMode.asStateFlow()

    private var job: Job? = null
    @Volatile private var customAction: PromptAction? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val tool = catalog.tool(ToolId.SNIP)
            customAction = tool.actions[PromptComposer.CUSTOM_ID]
            _bar.value = tool.barActions()
        }
    }

    /** Only the first event after start matters; later (replayed) events are ignored. */
    fun onCaptureEvent(event: CaptureEvent) {
        if (_stage.value !is SnipStage.Capturing) return
        _stage.value = when (event) {
            is CaptureEvent.Frame -> SnipStage.Selecting(event.bitmap)
            is CaptureEvent.Failed -> SnipStage.Failed(event.reason)
        }
    }

    /** Crops the screenshot and PNG-encodes it OFF the main thread. */
    fun onSelection(rect: PixelRect) {
        val selecting = _stage.value as? SnipStage.Selecting ?: return
        viewModelScope.launch {
            val acting = withContext(Dispatchers.Default) {
                val crop = Bitmap.createBitmap(selecting.screenshot, rect.left, rect.top, rect.width, rect.height)
                val bytes = ByteArrayOutputStream().also { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                // TODO(size): downscale very large crops before encoding to keep requests small and fast.
                SnipStage.Acting(selecting.screenshot, crop, Base64.getEncoder().encodeToString(bytes))
            }
            _result.value = SnipResult.Idle
            _stage.value = acting
        }
    }

    /** Back to region selection with the same screenshot. */
    fun retake() {
        val acting = _stage.value as? SnipStage.Acting ?: return
        job?.cancel()
        _result.value = SnipResult.Idle
        _stage.value = SnipStage.Selecting(acting.screenshot)
    }

    fun setResponseMode(mode: ResponseMode) {
        _responseMode.value = mode
    }

    fun run(action: PromptAction, custom: String? = null) {
        val acting = _stage.value as? SnipStage.Acting ?: return
        job?.cancel()
        _result.value = SnipResult.Loading(action.label)
        job = viewModelScope.launch {
            val outcome = engine.runAction(
                ToolId.SNIP,
                PromptRequest(
                    action = action,
                    input = PromptInput.Image("image/png", acting.pngBase64),
                    customInstruction = custom,
                    responseMode = _responseMode.value,
                ),
            )
            _result.value = when (val result = outcome.result) {
                is GenerateResult.Success -> SnipResult.Done(result.content.trim(), outcome.destination)
                is GenerateResult.Failure ->
                    SnipResult.Error(result.message, missingApiKey = result.kind == FailureKind.NO_API_KEY)
            }
        }
    }

    fun runCustom(instruction: String) {
        val action = customAction ?: return
        run(action, instruction)
    }

    fun stop() {
        job?.cancel()
        _result.value = SnipResult.Idle
    }
}

class SnipViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SnipViewModel(container.engine, container.promptCatalog) as T
}
