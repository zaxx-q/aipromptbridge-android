package com.aipromptbridge.feature.textedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aipromptbridge.core.diff.WordDiff
import com.aipromptbridge.designsystem.ActionChip
import com.aipromptbridge.designsystem.Aipb
import com.aipromptbridge.designsystem.AipbBottomSheet
import com.aipromptbridge.designsystem.AipbIcons
import com.aipromptbridge.designsystem.DiffText
import com.aipromptbridge.designsystem.InstructionField
import com.aipromptbridge.designsystem.Panel
import com.aipromptbridge.designsystem.PrimaryButton
import com.aipromptbridge.designsystem.ProfileChip
import com.aipromptbridge.designsystem.SecondaryButton
import com.aipromptbridge.designsystem.SegmentedToggle
import com.aipromptbridge.designsystem.SkeletonLines
import com.aipromptbridge.designsystem.TextAction
import com.aipromptbridge.designsystem.ToggleOption
import com.aipromptbridge.feature.common.ErrorPanel

/**
 * The TextEdit bottom sheet (Stitch screens: idle / loading / result / chat).
 * Layout: header (mode toggle, profile chip, close) -> preview OR chips+result -> chips -> instruction field.
 */
@Composable
fun TextEditSheet(
    viewModel: TextEditViewModel,
    onDismiss: () -> Unit,
    onReplace: (String) -> Unit,
    onCopy: (String) -> Unit,
    onOpenSetup: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bar by viewModel.bar.collectAsStateWithLifecycle()
    val profileLabel by viewModel.profileLabel.collectAsStateWithLifecycle()
    var modeIndex by rememberSaveable { mutableIntStateOf(0) } // 0 = Edit, 1 = Chat
    var instruction by rememberSaveable { mutableStateOf("") }

    AipbBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SegmentedToggle(
                    options = listOf(ToggleOption("Edit", "edit_note"), ToggleOption("Chat", "chat")),
                    selectedIndex = modeIndex,
                    onSelect = { modeIndex = it },
                )
                ProfileChip(profileLabel, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(AipbIcons.Close, contentDescription = "Close") }
            }

            if (modeIndex == 1) {
                ChatModePlaceholder()
            } else {
                EditMode(
                    viewModel = viewModel,
                    state = state,
                    bar = bar,
                    instruction = instruction,
                    onInstructionChange = { instruction = it },
                    onReplace = onReplace,
                    onCopy = onCopy,
                    onOpenSetup = onOpenSetup,
                )
            }
        }
    }
}

@Composable
private fun EditMode(
    viewModel: TextEditViewModel,
    state: EditUiState,
    bar: List<com.aipromptbridge.core.prompt.PromptAction>,
    instruction: String,
    onInstructionChange: (String) -> Unit,
    onReplace: (String) -> Unit,
    onCopy: (String) -> Unit,
    onOpenSetup: () -> Unit,
) {
    val busy = state is EditUiState.Loading
    val activeActionId = when (state) {
        is EditUiState.Loading -> state.actionId
        is EditUiState.Result -> state.actionId
        else -> null
    }

    // Idle: show the selected text first. Any other state: chips first, then the result area (matches the Stitch result screen).
    if (state is EditUiState.Idle) SelectionPreview(viewModel.selectedText, viewModel.isReadOnly)

    ActionBar(
        actions = bar,
        activeActionId = activeActionId,
        onRun = { viewModel.run(it) },
        onOpenSetup = onOpenSetup,
    )

    when (state) {
        is EditUiState.Idle -> Unit
        is EditUiState.Loading -> LoadingBlock(onStop = viewModel::stop)
        is EditUiState.Result -> ResultBlock(
            result = state,
            canReplace = state.diff != null && !viewModel.isReadOnly,
            onReplace = { onReplace(state.text) },
            onCopy = { onCopy(state.text) },
            onRetry = viewModel::retry,
        )
        is EditUiState.Error -> ErrorPanel(
            message = state.message,
            missingApiKey = state.missingApiKey,
            onRetry = viewModel::retry,
            onOpenSettings = onOpenSetup,
        )
    }

    InstructionField(
        value = instruction,
        onValueChange = onInstructionChange,
        placeholder = "Or tell it what to change…",
        onSend = { viewModel.runCustom(instruction) },
        enabled = !busy,
    )
}

@Composable
private fun SelectionPreview(text: String, readOnly: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Panel {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(AipbIcons.Quote, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        // TODO(source-app): show "From <app name>" here using Activity.referrer + PackageManager (needs a <queries> entry).
        Text(
            text = if (readOnly) "${text.length} characters · read-only field, results can be copied" else "${text.length} characters",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionBar(
    actions: List<com.aipromptbridge.core.prompt.PromptAction>,
    activeActionId: String?,
    onRun: (com.aipromptbridge.core.prompt.PromptAction) -> Unit,
    onOpenSetup: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(actions, key = { it.id }) { action ->
                ActionChip(
                    label = action.label,
                    iconId = action.icon,
                    selected = action.id == activeActionId,
                    onClick = { onRun(action) },
                )
            }
        }
        // TODO(prompt-editor): this should open the Prompt Editor ("Edit actions"). Until it exists it opens setup.
        FilledTonalIconButton(onClick = onOpenSetup) { Icon(AipbIcons.Tune, contentDescription = "Edit actions") }
    }
}

@Composable
private fun LoadingBlock(onStop: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Panel { SkeletonLines() }
        TextAction("Stop", onClick = onStop)
    }
}

@Composable
private fun ResultBlock(
    result: EditUiState.Result,
    canReplace: Boolean,
    onReplace: () -> Unit,
    onCopy: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel {
            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                val diff = result.diff
                if (diff != null) {
                    DiffText(diff)
                } else {
                    SelectionContainer {
                        // TODO(markdown): render Markdown for general (explain/summarize) results.
                        Text(result.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            val status = if (result.diff != null) {
                val changes = WordDiff.changeCount(result.diff)
                (if (changes == 1) "1 change" else "$changes changes") + " · ${result.text.length} chars"
            } else {
                "${result.text.length} chars"
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(AipbIcons.Check, contentDescription = null, tint = Aipb.extra.success, modifier = Modifier.size(18.dp))
                Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (canReplace) {
            PrimaryButton("Replace selection", onClick = onReplace, modifier = Modifier.fillMaxWidth(), iconId = "check")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Copy", onClick = onCopy, icon = AipbIcons.Copy, modifier = Modifier.weight(1f))
                SecondaryButton("Retry", onClick = onRetry, icon = AipbIcons.Retry, modifier = Modifier.weight(1f))
            }
        } else {
            // Read-only host field or a "show" result: copy is the primary action.
            PrimaryButton("Copy", onClick = onCopy, modifier = Modifier.fillMaxWidth(), iconId = null)
            SecondaryButton("Retry", onClick = onRetry, icon = AipbIcons.Retry, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ChatModePlaceholder() {
    // TODO(chat): implement Chat mode (README, Phase 4). Plan:
    //  - collapse the selection into a one-line "N characters selected" pill (expandable)
    //  - message list: user messages right-aligned in a Panel, AI replies as plain text (Markdown)
    //  - composer reuses InstructionField; send calls AiEngine.chat(...) with PromptsConfig.chatWindowSystemInstruction
    //  - persist via SessionRepository (container.sessions), origin "textedit:<actionId>", first user message
    //    prefixed with ContextMarker.forAction(label)
    Panel {
        Text("Chat mode is not implemented yet.", style = MaterialTheme.typography.titleMedium)
        Text(
            "Switch back to Edit to use the one-tap actions. See README Phase 4 for the chat plan.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
