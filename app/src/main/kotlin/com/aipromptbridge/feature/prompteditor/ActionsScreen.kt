package com.aipromptbridge.feature.prompteditor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aipromptbridge.AppContainer
import com.aipromptbridge.core.prompt.PromptAction
import com.aipromptbridge.core.prompt.PromptEditing
import com.aipromptbridge.core.prompt.ToolPromptConfig
import com.aipromptbridge.core.tool.ToolId
import com.aipromptbridge.designsystem.AipbIcons
import com.aipromptbridge.designsystem.AipbMono
import com.aipromptbridge.designsystem.AipbTopBar
import com.aipromptbridge.designsystem.RowDivider
import com.aipromptbridge.designsystem.SectionHeader
import com.aipromptbridge.designsystem.SegmentedToggle
import com.aipromptbridge.designsystem.SettingsRow
import com.aipromptbridge.designsystem.StatusLabel
import com.aipromptbridge.designsystem.TextAction
import com.aipromptbridge.designsystem.ToggleOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Stateless Actions list (Stitch: `aipromptbridge_actions_prompt_editor`). [snapshot] is null while loading. */
@Composable
fun ActionsContent(
    tool: ToolId,
    snapshot: EditorSnapshot?,
    onBack: () -> Unit,
    onSelectTool: (ToolId) -> Unit,
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
    onToggleBar: (String, Boolean) -> Unit,
    onMoveOnBar: (String, Int) -> Unit,
    onRestore: (String) -> Unit,
    onOpenModifiers: () -> Unit,
) {
    var showRestore by rememberSaveable { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                AipbTopBar(title = "Actions", onBack = onBack)
                SegmentedToggle(
                    options = listOf(ToggleOption("Text", "edit_note"), ToggleOption("Snip", "content_cut")),
                    selectedIndex = if (tool == ToolId.TEXT_EDIT) 0 else 1,
                    onSelect = { onSelectTool(if (it == 0) ToolId.TEXT_EDIT else ToolId.SNIP) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (snapshot != null) {
                    ActionsList(snapshot, onEdit, onToggleBar, onMoveOnBar, onShowRestore = { showRestore = true }, onOpenModifiers = onOpenModifiers)
                }
            }
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(AipbIcons.Add, contentDescription = null) },
                text = { Text("New action", style = MaterialTheme.typography.labelLarge) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.small,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                ),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp),
            )
        }
    }

    if (showRestore && snapshot != null) {
        val deleted = PromptEditing.deletedDefaultActions(snapshot.tool, snapshot.defaults)
        RestoreDialog(
            actions = deleted,
            onRestore = {
                onRestore(it)
                if (deleted.size <= 1) showRestore = false
            },
            onDismiss = { showRestore = false },
        )
    }
}

@Composable
private fun ActionsList(
    snapshot: EditorSnapshot,
    onEdit: (String) -> Unit,
    onToggleBar: (String, Boolean) -> Unit,
    onMoveOnBar: (String, Int) -> Unit,
    onShowRestore: () -> Unit,
    onOpenModifiers: () -> Unit,
) {
    val config: ToolPromptConfig = snapshot.tool
    val bar = config.barActions()
    val deletedCount = PromptEditing.deletedDefaultActions(config, snapshot.defaults).size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 96.dp), // room for the floating "New action" button
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${bar.size} on bar", style = AipbMono, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "Switch actions on to show them in the sheet. Reorder the bar below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }

        if (bar.isNotEmpty()) {
            SectionHeader("Bar order")
            bar.forEachIndexed { index, action ->
                BarOrderRow(
                    action = action,
                    canMoveUp = index > 0,
                    canMoveDown = index < bar.lastIndex,
                    onMove = { delta -> onMoveOnBar(action.id, delta) },
                )
                RowDivider()
            }
        }

        for (section in PromptEditing.sections(config)) {
            SectionHeader(section.groupName, trailing = "${section.actions.count { PromptEditing.isOnBar(config, it.id) }} on bar")
            for (action in section.actions) {
                ActionRow(
                    action = action,
                    status = snapshot.statusOf(action),
                    onBar = PromptEditing.isOnBar(config, action.id),
                    onClick = { onEdit(action.id) },
                    onToggleBar = { on -> onToggleBar(action.id, on) },
                )
                RowDivider()
            }
        }

        SectionHeader("Modifiers")
        SettingsRow(
            title = "Modifiers",
            subtitle = "Switches like Shorter or Formal that add an instruction to every action.",
            onClick = onOpenModifiers,
        )
        RowDivider()

        if (deletedCount > 0) {
            SectionHeader("Deleted")
            SettingsRow(
                title = "Restore built-in actions",
                subtitle = "$deletedCount deleted",
                onClick = onShowRestore,
            )
            RowDivider()
        }
    }
}

@Composable
private fun ActionGlyph(iconId: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(AipbIcons.byId(iconId), contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun ActionRow(
    action: PromptAction,
    status: String?,
    onBar: Boolean,
    onClick: () -> Unit,
    onToggleBar: (Boolean) -> Unit,
) {
    val preview = action.task.ifBlank { action.systemPrompt }.replace('\n', ' ').trim()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // The text part opens the editor; the switch is a separate target so one tap never does both.
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ActionGlyph(action.icon)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        action.label,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (status != null) StatusLabel(status)
                }
                if (preview.isNotEmpty()) {
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Switch(
            checked = onBar,
            onCheckedChange = onToggleBar,
            modifier = Modifier.semantics { contentDescription = "Show ${action.label} in the bar" },
        )
    }
}

@Composable
private fun BarOrderRow(action: PromptAction, canMoveUp: Boolean, canMoveDown: Boolean, onMove: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(AipbIcons.byId(action.icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Text(action.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
            Icon(AipbIcons.ArrowUp, contentDescription = "Move ${action.label} earlier")
        }
        IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
            Icon(AipbIcons.ArrowDown, contentDescription = "Move ${action.label} later")
        }
    }
}

@Composable
private fun RestoreDialog(actions: List<PromptAction>, onRestore: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore built-in actions", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (actions.isEmpty()) {
                    Text("Nothing left to restore.", style = MaterialTheme.typography.bodyLarge)
                }
                for (action in actions) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(AipbIcons.byId(action.icon), contentDescription = null, modifier = Modifier.size(24.dp))
                        Text(action.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        TextAction("Restore", onClick = { onRestore(action.id) })
                    }
                }
            }
        },
        confirmButton = { TextAction("Done", onClick = onDismiss) },
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    )
}

/** Connects [ActionsContent] to the prompt catalog. Edits are applied through the pure [PromptEditing] functions. */
@Composable
fun ActionsScreen(
    container: AppContainer,
    tool: ToolId,
    onBack: () -> Unit,
    onSelectTool: (ToolId) -> Unit,
    onNew: (ToolId) -> Unit,
    onEdit: (ToolId, String) -> Unit,
    onOpenModifiers: () -> Unit,
) {
    var snapshot by remember { mutableStateOf<EditorSnapshot?>(null) }

    // Reload when the tool tab changes and whenever this screen re-enters composition (coming back from the editor).
    LaunchedEffect(tool) {
        snapshot = null
        snapshot = withContext(Dispatchers.IO) { loadEditorSnapshot(container, tool) }
    }

    /** Persists on the app scope (survives leaving the screen), then refreshes what is shown. */
    fun mutate(transform: (ToolPromptConfig, ToolPromptConfig) -> ToolPromptConfig) {
        val defaults = snapshot?.defaults ?: return
        container.appScope.launch {
            container.promptCatalog.updateTool(tool) { current -> transform(current, defaults) }
            snapshot = loadEditorSnapshot(container, tool)
        }
    }

    ActionsContent(
        tool = tool,
        snapshot = snapshot,
        onBack = onBack,
        onSelectTool = onSelectTool,
        onNew = { onNew(tool) },
        onEdit = { id -> onEdit(tool, id) },
        onToggleBar = { id, on -> mutate { config, _ -> PromptEditing.setOnBar(config, id, on) } },
        onMoveOnBar = { id, delta -> mutate { config, _ -> PromptEditing.moveOnBar(config, id, delta) } },
        onRestore = { id -> mutate { config, defaults -> PromptEditing.resetToDefault(config, defaults, id) } },
        onOpenModifiers = onOpenModifiers,
    )
}
