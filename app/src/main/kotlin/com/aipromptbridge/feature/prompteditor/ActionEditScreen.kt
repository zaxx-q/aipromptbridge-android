package com.aipromptbridge.feature.prompteditor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aipromptbridge.AppContainer
import com.aipromptbridge.core.diff.DiffSegment
import com.aipromptbridge.core.diff.WordDiff
import com.aipromptbridge.core.profile.ConnectionProfile
import com.aipromptbridge.core.prompt.PromptAction
import com.aipromptbridge.core.prompt.PromptEditing
import com.aipromptbridge.core.prompt.PromptInput
import com.aipromptbridge.core.prompt.PromptRequest
import com.aipromptbridge.core.prompt.PromptType
import com.aipromptbridge.core.prompt.ResultDestination
import com.aipromptbridge.core.provider.GenerateResult
import com.aipromptbridge.core.tool.ToolId
import com.aipromptbridge.designsystem.AipbIcons
import com.aipromptbridge.designsystem.AipbMono
import com.aipromptbridge.designsystem.AipbTopBar
import com.aipromptbridge.designsystem.ConfirmDialog
import com.aipromptbridge.designsystem.DiffText
import com.aipromptbridge.designsystem.DropdownField
import com.aipromptbridge.designsystem.Panel
import com.aipromptbridge.designsystem.PrimaryButton
import com.aipromptbridge.designsystem.RowDivider
import com.aipromptbridge.designsystem.SecondaryButton
import com.aipromptbridge.designsystem.SectionHeader
import com.aipromptbridge.designsystem.SegmentedToggle
import com.aipromptbridge.designsystem.SkeletonLines
import com.aipromptbridge.designsystem.SwitchRow
import com.aipromptbridge.designsystem.TextAction
import com.aipromptbridge.designsystem.ToggleOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Deliberately flawed so grammar, tone and rewrite actions all have something to do. Shown to the user as-is. */
private const val SAMPLE_TEXT =
    "Me and my friend goes to the library yesterday, but it were closed. " +
        "We was really disappointed, so we decided to get some coffee instead."

private class LoadedEditor(val snapshot: EditorSnapshot, val profiles: List<ConnectionProfile>)

/** Everything the form edits, for the "unsaved changes" check. */
private data class FormValues(
    val label: String,
    val icon: String,
    val group: String,
    val systemPrompt: String,
    val task: String,
    val type: PromptType,
    val showInChat: Boolean,
    val autoApply: Boolean,
    val profile: String,
    val onBar: Boolean,
)

private sealed interface TestState {
    data object Idle : TestState
    data object Running : TestState
    data class Done(val text: String, val diff: List<DiffSegment>?) : TestState
    data class Error(val message: String) : TestState
}

/** [actionId] == null creates a new action. */
@Composable
fun ActionEditScreen(container: AppContainer, tool: ToolId, actionId: String?, onDone: () -> Unit) {
    val loaded by produceState<LoadedEditor?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            LoadedEditor(loadEditorSnapshot(container, tool), container.profileRepository.profiles())
        }
    }
    val data = loaded
    val title = if (actionId == null) "New action" else "Edit action"

    if (data == null) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.statusBarsPadding()) { AipbTopBar(title = title, onBack = onDone) }
        }
        return
    }

    val original = actionId?.let { data.snapshot.tool.actions[it] }
    if (actionId != null && original == null) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.statusBarsPadding()) {
                AipbTopBar(title = title, onBack = onDone)
                Text("This action no longer exists.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
            }
        }
        return
    }
    ActionForm(container, tool, data, original, onDone)
}

@Composable
private fun ActionForm(
    container: AppContainer,
    tool: ToolId,
    data: LoadedEditor,
    original: PromptAction?,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val config = data.snapshot.tool
    val defaults = data.snapshot.defaults
    val shipped = original?.let { defaults.actions[it.id] }
    val groups = PromptEditing.assignableGroups(config)

    val initial = remember(original) {
        FormValues(
            label = original?.label.orEmpty(),
            icon = original?.icon ?: "auto_fix_high",
            group = original?.let { PromptEditing.groupOf(config, it.id) } ?: PromptEditing.CUSTOM_GROUP,
            systemPrompt = original?.systemPrompt.orEmpty(),
            task = original?.task.orEmpty(),
            type = original?.promptType ?: if (tool == ToolId.TEXT_EDIT) PromptType.EDIT else PromptType.GENERAL,
            showInChat = original?.showInChat ?: false,
            autoApply = original?.autoApply ?: false,
            profile = original?.connectionProfile.orEmpty(),
            onBar = original?.let { PromptEditing.isOnBar(config, it.id) } ?: true,
        )
    }

    var label by rememberSaveable { mutableStateOf(initial.label) }
    var icon by rememberSaveable { mutableStateOf(initial.icon) }
    var group by rememberSaveable { mutableStateOf(initial.group) }
    var systemPrompt by rememberSaveable { mutableStateOf(initial.systemPrompt) }
    var task by rememberSaveable { mutableStateOf(initial.task) }
    var typeName by rememberSaveable { mutableStateOf(initial.type.name) }
    var showInChat by rememberSaveable { mutableStateOf(initial.showInChat) }
    var autoApply by rememberSaveable { mutableStateOf(initial.autoApply) }
    var profile by rememberSaveable { mutableStateOf(initial.profile) }
    var onBar by rememberSaveable { mutableStateOf(initial.onBar) }

    var error by remember { mutableStateOf<String?>(null) }
    var showIconPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var test by remember { mutableStateOf<TestState>(TestState.Idle) }
    var testJob by remember { mutableStateOf<Job?>(null) }

    val type = PromptType.valueOf(typeName)
    val isText = tool == ToolId.TEXT_EDIT

    fun values() = FormValues(label, icon, group, systemPrompt, task, type, showInChat, autoApply, profile, onBar)
    val dirty = values() != initial

    fun draft(): PromptAction = PromptAction(
        id = original?.id.orEmpty(),
        label = label.trim(),
        icon = icon,
        systemPrompt = systemPrompt,
        task = task,
        promptType = type,
        showInChat = showInChat,
        autoApply = autoApply,
        connectionProfile = profile.ifBlank { null },
        isDefault = original?.isDefault ?: false,
    )

    fun leave() {
        if (dirty) confirmDiscard = true else onDone()
    }

    fun save() {
        val action = draft()
        val problem = PromptEditing.validate(action)
        error = problem
        if (problem != null) return
        scope.launch {
            withContext(Dispatchers.IO) {
                container.promptCatalog.updateTool(tool) { current ->
                    if (original == null) {
                        val id = PromptEditing.newId(action.label, current.actions.keys)
                        PromptEditing.create(current, action.copy(id = id), group, onBar)
                    } else {
                        PromptEditing.update(current, defaults, action, group, onBar)
                    }
                }
            }
            onDone()
        }
    }

    fun delete() {
        val id = original?.id ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                container.promptCatalog.updateTool(tool) { current -> PromptEditing.delete(current, defaults, id) }
            }
            onDone()
        }
    }

    fun revert() {
        val base = shipped ?: return
        label = base.label
        icon = base.icon
        systemPrompt = base.systemPrompt
        task = base.task
        typeName = base.promptType.name
        showInChat = base.showInChat
        autoApply = base.autoApply
        profile = base.connectionProfile.orEmpty()
    }

    fun runTest() {
        val action = draft().let { if (it.id.isBlank()) it.copy(id = "test") else it }
        val problem = PromptEditing.validate(action)
        error = problem
        if (problem != null) return
        testJob?.cancel()
        test = TestState.Running
        testJob = scope.launch {
            val outcome = container.engine.runAction(ToolId.TEXT_EDIT, PromptRequest(action, PromptInput.Text(SAMPLE_TEXT)))
            test = when (val result = outcome.result) {
                is GenerateResult.Success -> {
                    val text = result.content.trim()
                    val diff = if (outcome.destination == ResultDestination.REPLACE) WordDiff.diff(SAMPLE_TEXT, text) else null
                    TestState.Done(text, diff)
                }
                is GenerateResult.Failure -> TestState.Error(result.message)
            }
        }
    }

    BackHandler(enabled = dirty) { confirmDiscard = true }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            AipbTopBar(title = if (original == null) "New action" else "Edit action", onBack = ::leave)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                if (original != null) {
                    Text(
                        "ID: ${original.id}",
                        style = AipbMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                SectionHeader("Action identity")
                Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Action name") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(AipbIcons.byId(icon), contentDescription = "Current icon")
                            }
                        }
                        Text("Icon", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        SecondaryButton("Change", onClick = { showIconPicker = true }, icon = AipbIcons.Palette)
                    }
                }

                SectionHeader("Organization")
                DropdownField(
                    label = "Group",
                    valueText = group,
                    options = groups,
                    onSelect = { group = groups[it] },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                SectionHeader("Prompt definition")
                Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        label = { Text("System instruction") },
                        minLines = 5,
                        maxLines = 12,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${systemPrompt.length} characters · ~${PromptEditing.estimateTokens(systemPrompt)} tokens",
                        style = AipbMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = task,
                        onValueChange = { task = it },
                        label = { Text("Task (sent with the selected text)") },
                        minLines = 2,
                        maxLines = 6,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (shipped != null) {
                        TextAction("Revert to default", onClick = ::revert)
                    }
                }

                SectionHeader("Output")
                Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SegmentedToggle(
                        options = listOf(ToggleOption("Result only"), ToggleOption("With explanation")),
                        selectedIndex = if (type == PromptType.EDIT) 0 else 1,
                        onSelect = { typeName = if (it == 0) PromptType.EDIT.name else PromptType.GENERAL.name },
                    )
                    Text(
                        if (type == PromptType.EDIT) {
                            if (isText) "Returns only the processed text, so it can replace the selection."
                            else "Returns only the extracted or processed text."
                        } else {
                            "May explain or add context. The result is shown instead of replacing anything."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SectionHeader("Inference model")
                val profileOptions = listOf("Use active profile") + data.profiles.map { "${it.name} · ${it.model}" }
                val profileValue = when {
                    profile.isBlank() -> profileOptions.first()
                    else -> data.profiles.firstOrNull { it.name == profile }?.let { "${it.name} · ${it.model}" } ?: "$profile (missing)"
                }
                DropdownField(
                    label = "Connection profile",
                    valueText = profileValue,
                    options = profileOptions,
                    onSelect = { index -> profile = if (index == 0) "" else data.profiles[index - 1].name },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                SectionHeader("Interaction behavior")
                SwitchRow(
                    title = "Show in bar",
                    subtitle = "Appears as a one-tap chip in the sheet.",
                    checked = onBar,
                    onCheckedChange = { onBar = it },
                )
                if (isText && type == PromptType.EDIT) {
                    RowDivider()
                    SwitchRow(
                        title = "Replace selection automatically",
                        subtitle = "Apply the result without the review step.",
                        checked = autoApply,
                        onCheckedChange = { autoApply = it },
                    )
                    RowDivider()
                    SwitchRow(
                        title = "Show result instead of replacing",
                        subtitle = "Show the result in the sheet; nothing is replaced.",
                        checked = showInChat,
                        onCheckedChange = { showInChat = it },
                    )
                }

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                    PrimaryButton("Save action", onClick = ::save, iconId = "check", modifier = Modifier.fillMaxWidth())
                    if (isText) {
                        SecondaryButton(
                            "Test on sample text",
                            onClick = ::runTest,
                            icon = AipbIcons.Test,
                            enabled = test != TestState.Running,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TestPanel(test, onStop = {
                            testJob?.cancel()
                            test = TestState.Idle
                        })
                    }
                    if (original != null) {
                        TextAction("Delete action", onClick = { confirmDelete = true }, destructive = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    if (showIconPicker) {
        IconPickerDialog(
            selected = icon,
            onSelect = {
                icon = it
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete action?",
            message = if (shipped != null) {
                "\"${original?.label}\" will be removed. You can bring built-in actions back from the Actions list."
            } else {
                "\"${original?.label}\" will be removed. This cannot be undone."
            },
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard changes?",
            message = "Your edits to this action have not been saved.",
            confirmLabel = "Discard",
            onConfirm = {
                confirmDiscard = false
                onDone()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun TestPanel(state: TestState, onStop: () -> Unit) {
    if (state == TestState.Idle) return
    Panel(modifier = Modifier.fillMaxWidth()) {
        Text("Sample text", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(SAMPLE_TEXT, style = MaterialTheme.typography.bodyLarge)
        RowDivider()
        Text("Result", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (state) {
            TestState.Idle -> Unit
            TestState.Running -> {
                SkeletonLines()
                TextAction("Stop", onClick = onStop)
            }
            is TestState.Done -> {
                if (state.diff != null) DiffText(state.diff) else Text(state.text, style = MaterialTheme.typography.bodyLarge)
            }
            is TestState.Error -> Text(state.message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Grid of the icons the app can draw. Four per row keeps every tile a 48dp target on narrow phones. */
@Composable
internal fun IconPickerDialog(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose an icon", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (rowIds in AipbIcons.pickerIds.chunked(4)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (id in rowIds) {
                            val isSelected = id == selected
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable(role = Role.Button) { onSelect(id) },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(AipbIcons.byId(id), contentDescription = id.replace('_', ' '))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextAction("Cancel", onClick = onDismiss) },
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    )
}
