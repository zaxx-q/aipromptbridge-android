package com.aipromptbridge.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.aipromptbridge.AppContainer
import com.aipromptbridge.core.keys.KeyEditing
import com.aipromptbridge.core.keys.KeyEntry
import com.aipromptbridge.core.profile.ProfileEditing
import com.aipromptbridge.core.profile.ProfileResolver
import com.aipromptbridge.core.provider.ProviderRegistry
import com.aipromptbridge.designsystem.AipbIcons
import com.aipromptbridge.designsystem.AipbTopBar
import com.aipromptbridge.designsystem.ConfirmDialog
import com.aipromptbridge.designsystem.PrimaryButton
import com.aipromptbridge.designsystem.RowDivider
import com.aipromptbridge.designsystem.SettingsRow
import com.aipromptbridge.designsystem.StatusLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One line of the pool list. [pool] is the storage name; [title] is what the user reads. */
data class KeyPoolRow(val pool: String, val title: String, val keyCount: Int, val inUse: Boolean)

/** Display name for a pool: the provider's name for its own pool, otherwise the custom pool name. */
fun poolTitle(pool: String): String =
    ProviderRegistry.all.firstOrNull { it.keyPool == pool }?.displayName ?: "$pool (custom pool)"

/** Reads the key file (decrypts it), so call off the main thread. Pools in use first, then pools that have keys. */
fun loadKeyPoolRows(container: AppContainer): List<KeyPoolRow> {
    val activePool = ProfileEditing.effectivePool(ProfileResolver.resolve(container.profileRepository))
    return ProfileEditing.poolNames(container.profileRepository.profiles())
        .map { pool ->
            val count = container.keyRepository.loadKeys(pool).count { it.value.isNotBlank() }
            KeyPoolRow(pool, poolTitle(pool), count, inUse = pool == activePool)
        }
        .sortedWith(compareByDescending<KeyPoolRow> { it.inUse }.thenByDescending { it.keyCount > 0 })
}

// ======================================================================================================== pool list

@Composable
fun ApiKeysContent(rows: List<KeyPoolRow>?, onBack: () -> Unit, onOpenPool: (String) -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            AipbTopBar(title = "API keys", onBack = onBack)
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Keys are stored encrypted on this device. Add several keys to one provider and the next key is used " +
                        "when one is rate limited or out of quota.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                RowDivider()
                for (row in rows.orEmpty()) {
                    SettingsRow(
                        title = row.title,
                        icon = AipbIcons.Key,
                        subtitle = KeyEditing.summary(row.keyCount),
                        subtitleIsError = row.inUse && row.keyCount == 0,
                        onClick = { onOpenPool(row.pool) },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (row.inUse) StatusLabel("In use", accent = true)
                                Icon(AipbIcons.Chevron, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
fun ApiKeysScreen(container: AppContainer, onBack: () -> Unit, onOpenPool: (String) -> Unit) {
    // Re-read each time the screen enters composition, so counts are fresh after editing a pool.
    val rows by produceState<List<KeyPoolRow>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { loadKeyPoolRows(container) }
    }
    ApiKeysContent(rows = rows, onBack = onBack, onOpenPool = onOpenPool)
}

// ======================================================================================================== one pool

/** Runs saves one after another (never concurrently), on a scope that outlives the screen. */
private class SerialSaver(private val scope: CoroutineScope) {
    private var last: Job? = null

    /** Call from the main thread only. */
    fun submit(block: () -> Unit) {
        val previous = last
        last = scope.launch {
            previous?.join()
            block()
        }
    }
}

private val MonoStyle = SpanStyle(fontFamily = FontFamily.Monospace)

private fun maskedText(value: String): AnnotatedString = buildAnnotatedString {
    withStyle(MonoStyle) { append(KeyEditing.mask(value)) }
}

@Composable
fun KeyPoolContent(
    title: String,
    entries: List<KeyEntry>?,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            AipbTopBar(title = title, onBack = onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                val list = entries.orEmpty()
                Text(
                    if (list.size > 1) {
                        "Keys are tried in this order. When a request is rate limited or rejected for quota or authorization, " +
                            "the next key is used."
                    } else {
                        "Keys are stored encrypted on this device and are never shown in full."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                RowDivider()
                list.forEachIndexed { index, entry ->
                    SettingsRow(
                        title = entry.name,
                        subtitle = maskedText(entry.value),
                        onClick = { onEdit(index) },
                        trailing = {
                            IconButton(onClick = { onDelete(index) }) {
                                Icon(AipbIcons.Delete, contentDescription = "Delete ${entry.name}")
                            }
                        },
                    )
                    RowDivider()
                }
                if (entries != null && list.isEmpty()) {
                    Text(
                        "No keys saved for this provider yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            PrimaryButton(
                "Add key",
                onClick = onAdd,
                iconId = "add",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }
    }
}

private sealed interface KeyDialogState {
    data object Closed : KeyDialogState
    data object Adding : KeyDialogState
    data class Editing(val index: Int) : KeyDialogState
    data class Deleting(val index: Int) : KeyDialogState
}

@Composable
fun KeyPoolScreen(container: AppContainer, pool: String, onBack: () -> Unit) {
    val saver = remember(container) { SerialSaver(container.appScope) }
    var entries by remember { mutableStateOf<List<KeyEntry>?>(null) }
    var dialog by remember { mutableStateOf<KeyDialogState>(KeyDialogState.Closed) }
    LaunchedEffect(pool) { entries = withContext(Dispatchers.IO) { container.keyRepository.loadKeys(pool) } }

    /** Shows the new list immediately; the write (and the pool-cache refresh) happens in order on the app scope. */
    fun persist(updated: List<KeyEntry>) {
        entries = updated
        saver.submit {
            container.keyRepository.saveKeys(pool, updated)
            container.keyPools.invalidate(pool)
        }
    }

    KeyPoolContent(
        title = poolTitle(pool),
        entries = entries,
        onBack = onBack,
        onAdd = { dialog = KeyDialogState.Adding },
        onEdit = { index -> dialog = KeyDialogState.Editing(index) },
        onDelete = { index -> dialog = KeyDialogState.Deleting(index) },
    )

    val current = entries.orEmpty()
    when (val state = dialog) {
        KeyDialogState.Closed -> Unit
        KeyDialogState.Adding -> KeyDialog(
            title = "Add key",
            initialName = "",
            namePlaceholder = KeyEditing.nextName(current),
            keyRequired = true,
            isDuplicate = { KeyEditing.containsValue(current, it) },
            onSave = { name, key ->
                persist(KeyEditing.add(current, name, key))
                dialog = KeyDialogState.Closed
            },
            onDismiss = { dialog = KeyDialogState.Closed },
        )
        is KeyDialogState.Editing -> {
            val entry = current.getOrNull(state.index)
            if (entry != null) {
                KeyDialog(
                    title = "Edit key",
                    initialName = entry.name,
                    namePlaceholder = entry.name,
                    keyRequired = false,
                    keyLabel = "New key (leave empty to keep ${KeyEditing.mask(entry.value)})",
                    isDuplicate = { it.trim() != entry.value && KeyEditing.containsValue(current, it) },
                    onSave = { name, key ->
                        persist(KeyEditing.replaceValue(KeyEditing.rename(current, state.index, name), state.index, key))
                        dialog = KeyDialogState.Closed
                    },
                    onDismiss = { dialog = KeyDialogState.Closed },
                )
            }
        }
        is KeyDialogState.Deleting -> {
            val entry = current.getOrNull(state.index)
            if (entry != null) {
                ConfirmDialog(
                    title = "Delete key?",
                    message = "\"${entry.name}\" will be removed from this device. This cannot be undone.",
                    confirmLabel = "Delete",
                    onConfirm = {
                        persist(KeyEditing.remove(current, state.index))
                        dialog = KeyDialogState.Closed
                    },
                    onDismiss = { dialog = KeyDialogState.Closed },
                )
            }
        }
    }
}

/**
 * Add / edit dialog. The secret is held with plain `remember` on purpose: `rememberSaveable` would copy it into the
 * saved-instance-state bundle. The cost is that rotating the device clears the key field.
 */
@Composable
private fun KeyDialog(
    title: String,
    initialName: String,
    namePlaceholder: String,
    keyRequired: Boolean,
    isDuplicate: (String) -> Boolean,
    onSave: (name: String, key: String) -> Unit,
    onDismiss: () -> Unit,
    keyLabel: String = "API key",
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var key by remember { mutableStateOf("") }
    val duplicate = key.isNotBlank() && isDuplicate(key)
    val canSave = !duplicate && (!keyRequired || key.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    placeholder = { Text(namePlaceholder) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(keyLabel) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = duplicate,
                    supportingText = if (duplicate) {
                        { Text("This key is already saved in this pool.") }
                    } else {
                        null
                    },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, key) }, enabled = canSave) {
                Text("Save", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelLarge) }
        },
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    )
}
