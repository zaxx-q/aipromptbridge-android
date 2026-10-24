package com.aipromptbridge.feature.prompteditor

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aipromptbridge.AppContainer
import com.aipromptbridge.core.prompt.DefaultPrompts
import com.aipromptbridge.core.prompt.ModifierEditing
import com.aipromptbridge.core.prompt.PromptEditing
import com.aipromptbridge.core.prompt.PromptModifier
import com.aipromptbridge.designsystem.AipbIcons
import com.aipromptbridge.designsystem.AipbMono
import com.aipromptbridge.designsystem.AipbTopBar
import com.aipromptbridge.designsystem.ConfirmDialog
import com.aipromptbridge.designsystem.PrimaryButton
import com.aipromptbridge.designsystem.RowDivider
import com.aipromptbridge.designsystem.SecondaryButton
import com.aipromptbridge.designsystem.SectionHeader
import com.aipromptbridge.designsystem.SettingsRow
import com.aipromptbridge.designsystem.StatusLabel
import com.aipromptbridge.designsystem.SwitchRow
import com.aipromptbridge.designsystem.TextAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ======================================================================================================== list

/** Stateless modifier list. [modifiers] is null while loading. */
@Composable
fun ModifiersContent(
    modifiers: List<PromptModifier>?,
    defaults: List<PromptModifier>,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            AipbTopBar(
                title = "Modifiers",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onNew) { Icon(AipbIcons.Add, contentDescription = "New modifier") }
                },
            )
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Modifiers are switches in the text selection sheet. While one is on, its instruction is added to every " +
                        "action you run, for example Shorter or Formal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                RowDivider()
                for (entry in modifiers.orEmpty()) {
                    val status = ModifierEditing.status(modifiers.orEmpty(), defaults, entry.key)
                    SettingsRow(
                        title = entry.label,
                        subtitle = entry.tooltip,
                        icon = AipbIcons.byId(entry.icon),
                        onClick = { onEdit(entry.key) },
                        trailing = if (status == null) {
                            null
                        } else {
                            {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    StatusLabel(status)
                                    Icon(AipbIcons.Chevron, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                    )
                    RowDivider()
                }
            }
        }
    }
}

@Composable
fun ModifiersScreen(container: AppContainer, onBack: () -> Unit, onNew: () -> Unit, onEdit: (String) -> Unit) {
    var modifiers by remember { mutableStateOf<List<PromptModifier>?>(null) }
    val defaults = remember { DefaultPrompts.build().modifiers }
    // Re-read whenever this screen enters composition again (coming back from the editor).
    LaunchedEffect(Unit) { modifiers = withContext(Dispatchers.IO) { container.promptCatalog.config().modifiers } }
    ModifiersContent(modifiers, defaults, onBack, onNew, onEdit)
}

// ======================================================================================================== editor

private class LoadedModifiers(val current: List<PromptModifier>, val defaults: List<PromptModifier>)

/** [key] == null creates a new modifier. */
@Composable
fun ModifierEditScreen(container: AppContainer, key: String?, onDone: () -> Unit) {
    val loaded by produceState<LoadedModifiers?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            LoadedModifiers(container.promptCatalog.config().modifiers, DefaultPrompts.build().modifiers)
        }
    }
    val data = loaded
    val title = if (key == null) "New modifier" else "Edit modifier"
    if (data == null) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.statusBarsPadding()) { AipbTopBar(title = title, onBack = onDone) }
        }
        return
    }
    val original = key?.let { k -> data.current.firstOrNull { it.key == k } }
    if (key != null && original == null) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.statusBarsPadding()) {
                AipbTopBar(title = title, onBack = onDone)
                Text("This modifier no longer exists.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
            }
        }
        return
    }
    ModifierForm(container, data, original, onDone)
}

private data class ModifierValues(
    val label: String,
    val icon: String,
    val tooltip: String,
    val injection: String,
    val forcesChat: Boolean,
)

@Composable
private fun ModifierForm(
    container: AppContainer,
    data: LoadedModifiers,
    original: PromptModifier?,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val shipped = original?.let { o -> data.defaults.firstOrNull { it.key == o.key } }
    val builtIn = shipped != null

    val initial = remember(original) {
        ModifierValues(
            label = original?.label.orEmpty(),
            icon = original?.icon ?: "auto_fix_high",
            tooltip = original?.tooltip.orEmpty(),
            injection = original?.injection.orEmpty(),
            forcesChat = original?.forcesChat ?: false,
        )
    }
    var label by rememberSaveable { mutableStateOf(initial.label) }
    var icon by rememberSaveable { mutableStateOf(initial.icon) }
    var tooltip by rememberSaveable { mutableStateOf(initial.tooltip) }
    var injection by rememberSaveable { mutableStateOf(initial.injection) }
    var forcesChat by rememberSaveable { mutableStateOf(initial.forcesChat) }

    var error by remember { mutableStateOf<String?>(null) }
    var showIconPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val dirty = ModifierValues(label, icon, tooltip, injection, forcesChat) != initial

    fun leave() {
        if (dirty) confirmDiscard = true else onDone()
    }

    fun save() {
        val edited = PromptModifier(
            key = original?.key.orEmpty(),
            icon = icon,
            label = label.trim(),
            tooltip = tooltip.trim(),
            injection = injection,
            forcesChat = forcesChat,
        )
        val problem = ModifierEditing.validate(edited)
        error = problem
        if (problem != null) return
        scope.launch {
            withContext(Dispatchers.IO) {
                container.promptCatalog.updateModifiers { current ->
                    if (original == null) {
                        ModifierEditing.create(current, edited.copy(key = ModifierEditing.newKey(edited.label, current.map { it.key }.toSet())))
                    } else {
                        ModifierEditing.update(current, edited)
                    }
                }
            }
            onDone()
        }
    }

    fun delete() {
        val target = original?.key ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                container.promptCatalog.updateModifiers { current -> ModifierEditing.delete(current, data.defaults, target) }
            }
            onDone()
        }
    }

    fun revert() {
        val base = shipped ?: return
        label = base.label
        icon = base.icon
        tooltip = base.tooltip
        injection = base.injection
        forcesChat = base.forcesChat
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
            AipbTopBar(title = if (original == null) "New modifier" else "Edit modifier", onBack = ::leave)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                if (original != null) {
                    Text(
                        "KEY: ${original.key}",
                        style = AipbMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                SectionHeader("Identity")
                Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Name") },
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
                            Box(contentAlignment = Alignment.Center) { Icon(AipbIcons.byId(icon), contentDescription = "Current icon") }
                        }
                        Text("Icon", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        SecondaryButton("Change", onClick = { showIconPicker = true }, icon = AipbIcons.Palette)
                    }
                    OutlinedTextField(
                        value = tooltip,
                        onValueChange = { tooltip = it },
                        label = { Text("Short description (optional)") },
                        minLines = 2,
                        maxLines = 4,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SectionHeader("Instruction")
                Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = injection,
                        onValueChange = { injection = it },
                        label = { Text("Added to the system prompt") },
                        supportingText = {
                            Text("Built-in modifiers wrap their text in a tag such as <modifier_name>. Keep that style so the model treats it as a separate instruction.")
                        },
                        minLines = 5,
                        maxLines = 12,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${injection.length} characters · ~${PromptEditing.estimateTokens(injection)} tokens",
                        style = AipbMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (builtIn) TextAction("Revert to default", onClick = ::revert)
                }

                SectionHeader("Behavior")
                SwitchRow(
                    title = "Show result instead of replacing",
                    subtitle = "While this modifier is on, results stay in the sheet and are never swapped into your text automatically.",
                    checked = forcesChat,
                    onCheckedChange = { forcesChat = it },
                )
                RowDivider()

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                    PrimaryButton("Save modifier", onClick = ::save, iconId = "check", modifier = Modifier.fillMaxWidth())
                    if (original != null && !builtIn) {
                        TextAction("Delete modifier", onClick = { confirmDelete = true }, destructive = true, modifier = Modifier.fillMaxWidth())
                    }
                    if (builtIn) {
                        Text(
                            "Built-in modifiers can be edited or reverted, but not deleted.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
            title = "Delete modifier?",
            message = "\"${original?.label}\" will be removed. This cannot be undone.",
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
            message = "Your edits to this modifier have not been saved.",
            confirmLabel = "Discard",
            onConfirm = {
                confirmDiscard = false
                onDone()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}
