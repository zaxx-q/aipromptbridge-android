package com.aipromptbridge.feature.textedit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aipromptbridge.core.diff.WordDiff
import com.aipromptbridge.core.prompt.PromptAction
import com.aipromptbridge.core.prompt.PromptModifier
import com.aipromptbridge.core.session.StoredMessage
import com.aipromptbridge.designsystem.ActionChip
import com.aipromptbridge.designsystem.Aipb
import com.aipromptbridge.designsystem.AipbBottomSheet
import com.aipromptbridge.designsystem.AipbIcons
import com.aipromptbridge.designsystem.AipbMono
import com.aipromptbridge.designsystem.DiffText
import com.aipromptbridge.designsystem.InstructionField
import com.aipromptbridge.designsystem.Panel
import com.aipromptbridge.designsystem.PrimaryButton
import com.aipromptbridge.designsystem.ProfileChip
import com.aipromptbridge.designsystem.SecondaryButton
import com.aipromptbridge.designsystem.SegmentedToggle
import com.aipromptbridge.designsystem.SkeletonLines
import com.aipromptbridge.designsystem.TextAction
import com.aipromptbridge.designsystem.ToggleIconButton
import com.aipromptbridge.designsystem.ToggleOption
import com.aipromptbridge.feature.common.ErrorPanel

/**
 * The TextEdit bottom sheet (Stitch screens: idle / loading / result / chat).
 * Layout: header (mode toggle, profile chip, close) -> content (EditMode or ChatMode).
 */
@Composable
fun TextEditSheet(
    viewModel: TextEditViewModel,
    chatViewModel: ChatViewModel? = null,
    onDismiss: () -> Unit,
    onReplace: (String) -> Unit,
    onCopy: (String) -> Unit,
    onOpenSetup: () -> Unit,
    onEditActions: () -> Unit,
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
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Close") }
            }

            if (modeIndex == 1) {
                if (chatViewModel != null) {
                    ChatMode(
                        viewModel = chatViewModel,
                        onReplace = onReplace,
                        onCopy = onCopy,
                        onOpenSetup = onOpenSetup,
                        onEditActions = onEditActions,
                    )
                } else {
                    SelectionPreview(viewModel.selectedText, viewModel.isReadOnly)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EditMode(
                        viewModel = viewModel,
                        state = state,
                        bar = bar,
                        instruction = instruction,
                        onInstructionChange = { instruction = it },
                        onReplace = onReplace,
                        onCopy = onCopy,
                        onOpenSetup = onOpenSetup,
                        onEditActions = onEditActions,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditMode(
    viewModel: TextEditViewModel,
    state: EditUiState,
    bar: List<PromptAction>,
    instruction: String,
    onInstructionChange: (String) -> Unit,
    onReplace: (String) -> Unit,
    onCopy: (String) -> Unit,
    onOpenSetup: () -> Unit,
    onEditActions: () -> Unit,
) {
    val busy = state is EditUiState.Loading
    val activeActionId = when (state) {
        is EditUiState.Loading -> state.actionId
        is EditUiState.Result -> state.actionId
        else -> null
    }
    val modifiers by viewModel.modifiers.collectAsStateWithLifecycle()
    val activeModifiers by viewModel.activeModifiers.collectAsStateWithLifecycle()
    var showModifiers by rememberSaveable { mutableStateOf(false) }

    // Idle: show the selected text first. Any other state: chips first, then the result area.
    if (state is EditUiState.Idle) SelectionPreview(viewModel.selectedText, viewModel.isReadOnly)

    ActionBar(
        actions = bar,
        activeActionId = activeActionId,
        onRun = { viewModel.run(it) },
        onEditActions = onEditActions,
        modifierBar = if (modifiers.isEmpty()) null else ModifierBarState(
            expanded = showModifiers,
            activeCount = activeModifiers.size,
            onToggle = { showModifiers = !showModifiers },
        ),
    )
    // Plain `if` (not AnimatedVisibility): a hidden AnimatedVisibility still gets spacing in a spacedBy Column.
    if (showModifiers && modifiers.isNotEmpty()) {
        ModifierRow(modifiers = modifiers, active = activeModifiers, onToggle = viewModel::toggleModifier)
    }

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
fun ChatMode(
    viewModel: ChatViewModel,
    onReplace: (String) -> Unit,
    onCopy: (String) -> Unit,
    onOpenSetup: () -> Unit,
    onEditActions: () -> Unit,
    /** What sits above the messages. Text Edit shows the selected text; Snip shows the captured image. */
    header: @Composable () -> Unit = { CollapsibleSelectionPill(viewModel.selectedText) },
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val assistantState by viewModel.assistantState.collectAsStateWithLifecycle()
    val bar by viewModel.bar.collectAsStateWithLifecycle()
    var composerText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, assistantState) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        header()

        // Scrollable Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(max = 340.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(messages) { msg ->
                if (msg.role == "user") {
                    UserMessageBubble(msg.text)
                } else {
                    AssistantMessageBlock(
                        text = msg.text,
                        canReplace = !viewModel.isReadOnly,
                        onReplace = { onReplace(msg.text) },
                        onCopy = { onCopy(msg.text) },
                        onRetry = viewModel::retry,
                    )
                }
            }

            if (assistantState is ChatAssistantState.Generating) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Panel { SkeletonLines() }
                        TextAction("Stop", onClick = viewModel::stop)
                    }
                }
            }

            if (assistantState is ChatAssistantState.Error) {
                val error = assistantState as ChatAssistantState.Error
                item {
                    ErrorPanel(
                        message = error.message,
                        missingApiKey = error.missingApiKey,
                        onRetry = viewModel::retry,
                        onOpenSettings = onOpenSetup,
                    )
                }
            }
        }

        // Quick Action Chips Row
        if (bar.isNotEmpty()) {
            ActionBar(
                actions = bar,
                activeActionId = null,
                onRun = { viewModel.runAction(it) },
                onEditActions = onEditActions,
            )
        }

        // Composer Input Bar
        InstructionField(
            value = composerText,
            onValueChange = { composerText = it },
            placeholder = "Ask a question or request changes…",
            onSend = {
                viewModel.send(composerText)
                composerText = ""
            },
            enabled = assistantState !is ChatAssistantState.Generating,
        )
    }
}

@Composable
fun CollapsibleSelectionPill(selectedText: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(
                    onClickLabel = if (expanded) "Collapse selected text" else "Expand selected text",
                    role = Role.Button,
                ) { expanded = !expanded },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Outlined.FormatQuote,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    text = "${selectedText.length} characters selected",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "\"${selectedText.replace("\n", " ").take(28)}...\"",
                    style = AipbMono.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SelectionContainer {
                    Text(
                        text = selectedText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun UserMessageBubble(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
fun AssistantMessageBlock(
    text: String,
    canReplace: Boolean,
    onReplace: () -> Unit,
    onCopy: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SelectionContainer {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Actions row (Copy / Replace / Retry)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = onCopy) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Box(Modifier.size(6.dp))
                Text("Copy", style = MaterialTheme.typography.labelMedium)
            }

            if (canReplace) {
                TextButton(onClick = onReplace) {
                    Icon(
                        Icons.Outlined.FindReplace,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Box(Modifier.size(6.dp))
                    Text(
                        "Replace",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            TextButton(onClick = onRetry) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Box(Modifier.size(6.dp))
                Text("Retry", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun SelectionPreview(text: String, readOnly: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Panel {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.FormatQuote, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(
            text = if (readOnly) "${text.length} characters · read-only field, results can be copied" else "${text.length} characters",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** State of the Tune button that shows/hides the modifier row (null = this bar has no modifiers, e.g. chat mode). */
private class ModifierBarState(val expanded: Boolean, val activeCount: Int, val onToggle: () -> Unit)

@Composable
private fun ActionBar(
    actions: List<PromptAction>,
    activeActionId: String?,
    onRun: (PromptAction) -> Unit,
    onEditActions: () -> Unit,
    modifierBar: ModifierBarState? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(actions, key = { it.id }) { action ->
                ActionChip(
                    label = action.label,
                    iconId = action.icon,
                    selected = action.id == activeActionId,
                    onClick = { onRun(action) },
                )
            }
            item(key = "edit-actions") { TextAction("Edit actions", onClick = onEditActions) }
        }
        if (modifierBar != null) {
            ToggleIconButton(
                icon = Icons.Outlined.Tune,
                label = "Modifiers",
                checked = modifierBar.expanded || modifierBar.activeCount > 0,
                onClick = modifierBar.onToggle,
                stateText = if (modifierBar.activeCount > 0) "${modifierBar.activeCount} on" else null,
            )
        }
    }
}

/** One toggle chip per modifier. They change the NEXT run (and every run after it) until switched off. */
@Composable
private fun ModifierRow(modifiers: List<PromptModifier>, active: Set<String>, onToggle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Modifiers adjust every action you run next, including your own instruction.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(modifiers, key = { it.key }) { modifier ->
                ActionChip(
                    label = modifier.label,
                    iconId = modifier.icon,
                    selected = modifier.key in active,
                    onClick = { onToggle(modifier.key) },
                    toggle = true,
                )
            }
        }
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
                Icon(Icons.Outlined.Check, contentDescription = null, tint = Aipb.extra.success, modifier = Modifier.size(18.dp))
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
            PrimaryButton("Copy", onClick = onCopy, modifier = Modifier.fillMaxWidth(), iconId = null)
            SecondaryButton("Retry", onClick = onRetry, icon = AipbIcons.Retry, modifier = Modifier.fillMaxWidth())
        }
    }
}
