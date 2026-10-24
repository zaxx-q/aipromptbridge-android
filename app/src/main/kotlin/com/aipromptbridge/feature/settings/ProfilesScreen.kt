package com.aipromptbridge.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aipromptbridge.AppContainer
import com.aipromptbridge.core.profile.ConnectionProfile
import com.aipromptbridge.core.profile.ProfileBook
import com.aipromptbridge.core.profile.ProfileEditing
import com.aipromptbridge.core.profile.ProfileField
import com.aipromptbridge.core.profile.ProfileIssue
import com.aipromptbridge.core.provider.ProviderDefinition
import com.aipromptbridge.core.provider.ProviderRegistry
import com.aipromptbridge.designsystem.ActionChip
import com.aipromptbridge.designsystem.AipbIcons
import com.aipromptbridge.designsystem.AipbMono
import com.aipromptbridge.designsystem.AipbTopBar
import com.aipromptbridge.designsystem.ConfirmDialog
import com.aipromptbridge.designsystem.PrimaryButton
import com.aipromptbridge.designsystem.RowDivider
import com.aipromptbridge.designsystem.SettingsRow
import com.aipromptbridge.designsystem.StatusLabel
import com.aipromptbridge.designsystem.TextAction
import com.aipromptbridge.designsystem.subtitleWithMono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reads profiles.json. If profiles exist but none is marked active (only possible by hand-editing the file), the first
 * one is made active and saved: `ProfileResolver` would otherwise silently ignore every saved profile.
 * Blocking; call off the main thread.
 */
fun loadProfileBook(container: AppContainer): ProfileBook {
    val repository = container.profileRepository
    val book = ProfileBook(repository.profiles(), repository.activeProfileName())
    if (book.profiles.isNotEmpty() && book.active() == null) {
        val healed = book.copy(activeName = book.profiles.first().name)
        repository.save(healed.profiles, healed.activeName)
        return healed
    }
    return book
}

private fun providerName(profile: ConnectionProfile): String =
    ProviderRegistry.find(profile.provider)?.displayName ?: profile.provider

// ======================================================================================================== list

/** Stateless profile list. [book] is null while loading. */
@Composable
fun ProfilesContent(
    book: ProfileBook?,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onSetActive: (String) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            AipbTopBar(
                title = "Connection profiles",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onAdd) { Icon(AipbIcons.Add, contentDescription = "Add profile") }
                },
            )
            when {
                book == null -> Unit
                book.profiles.isEmpty() -> Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        "No profiles yet. Until you add one, requests use the built-in default " +
                            "(${providerName(ConnectionProfile.FALLBACK)}, ${ConnectionProfile.FALLBACK.model}).",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PrimaryButton("Add profile", onClick = onAdd, modifier = Modifier.fillMaxWidth())
                }
                else -> Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "A profile is a provider, a model and its settings. The active one answers every request unless an action picks another.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    RowDivider()
                    for (profile in book.profiles) {
                        val active = profile.name == book.activeName
                        SettingsRow(
                            title = profile.name,
                            subtitle = subtitleWithMono(providerName(profile), profile.model),
                            onClick = { onEdit(profile.name) },
                            trailing = {
                                if (active) {
                                    StatusLabel("Active", accent = true)
                                } else {
                                    TextAction("Set active", onClick = { onSetActive(profile.name) })
                                }
                            },
                        )
                        RowDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun ProfilesScreen(container: AppContainer, onBack: () -> Unit, onEdit: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var book by remember { mutableStateOf<ProfileBook?>(null) }
    LaunchedEffect(Unit) { book = withContext(Dispatchers.IO) { loadProfileBook(container) } }

    ProfilesContent(
        book = book,
        onBack = onBack,
        onAdd = { onEdit(null) },
        onEdit = { name -> onEdit(name) },
        onSetActive = { name ->
            val current = book
            if (current != null) {
                scope.launch {
                    val updated = ProfileEditing.setActive(current, name)
                    withContext(Dispatchers.IO) { container.profileRepository.save(updated.profiles, updated.activeName) }
                    book = updated
                }
            }
        },
    )
}

// ======================================================================================================== editor

private class LoadedProfile(val book: ProfileBook, val initial: ConnectionProfile)

@Composable
fun ProfileEditScreen(container: AppContainer, originalName: String?, onDone: () -> Unit) {
    val loaded by produceState<LoadedProfile?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            val book = loadProfileBook(container)
            val existing = originalName?.let { name -> book.profiles.firstOrNull { it.name == name } }
            val initial = existing ?: ConnectionProfile.forProvider(
                ProviderRegistry.all.first(),
                name = if (book.profiles.isEmpty()) ConnectionProfile.DEFAULT_NAME else ProfileEditing.uniqueName("New profile", book.profiles),
            )
            LoadedProfile(book, initial)
        }
    }

    val data = loaded
    if (data == null) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.statusBarsPadding()) {
                AipbTopBar(title = if (originalName == null) "New profile" else "Edit profile", onBack = onDone)
            }
        }
    } else {
        ProfileForm(container, data.book, originalName, data.initial, onDone)
    }
}

@Composable
private fun ProfileForm(
    container: AppContainer,
    book: ProfileBook,
    originalName: String?,
    initial: ConnectionProfile,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var providerId by rememberSaveable { mutableStateOf(initial.provider) }
    var model by rememberSaveable { mutableStateOf(initial.model) }
    var baseUrl by rememberSaveable { mutableStateOf(initial.baseUrl) }
    var keyPool by rememberSaveable { mutableStateOf(initial.apiKeyPool) }
    var temperature by rememberSaveable { mutableStateOf(initial.temperature?.toString().orEmpty()) }
    var maxTokens by rememberSaveable { mutableStateOf(initial.maxOutputTokens?.toString().orEmpty()) }
    var timeout by rememberSaveable { mutableStateOf(initial.requestTimeoutSeconds.toString()) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var issues by remember { mutableStateOf<List<ProfileIssue>>(emptyList()) }
    var confirmDelete by remember { mutableStateOf(false) }

    val provider = ProviderRegistry.find(providerId)
    val others = book.profiles.filter { it.name != originalName }
    fun errorFor(field: ProfileField): String? = issues.firstOrNull { it.field == field }?.message

    fun selectProvider(definition: ProviderDefinition) {
        if (definition.id == providerId) return
        val old = ProviderRegistry.find(providerId)
        // Keep a model the user typed themselves; replace one that was just the previous provider's default.
        if (model.isBlank() || model == old?.defaultModel) model = definition.defaultModel
        providerId = definition.id
        baseUrl = "" // an override URL for the previous provider would send requests to the wrong server
    }

    fun save() {
        val extra = mutableListOf<ProfileIssue>()
        val temperatureText = temperature.trim()
        val temperatureValue = temperatureText.toDoubleOrNull()
        if (temperatureText.isNotEmpty() && temperatureValue == null) {
            extra += ProfileIssue(ProfileField.TEMPERATURE, "Enter a number such as 0.7, or leave it empty.")
        }
        val tokensText = maxTokens.trim()
        val tokensValue = tokensText.toIntOrNull()
        if (tokensText.isNotEmpty() && tokensValue == null) {
            extra += ProfileIssue(ProfileField.MAX_TOKENS, "Enter a whole number, or leave it empty.")
        }
        val timeoutValue = timeout.trim().toIntOrNull()
        if (timeoutValue == null) extra += ProfileIssue(ProfileField.TIMEOUT, "Enter the timeout in whole seconds.")

        val draft = ConnectionProfile(
            name = name,
            provider = providerId,
            model = model,
            baseUrl = baseUrl,
            apiKeyPool = keyPool,
            temperature = temperatureValue,
            maxOutputTokens = tokensValue,
            requestTimeoutSeconds = timeoutValue ?: ConnectionProfile.FALLBACK.requestTimeoutSeconds,
            streaming = initial.streaming,
            thinking = initial.thinking,
        )
        val found = extra + ProfileEditing.validate(draft, others)
        issues = found
        if (found.isNotEmpty()) {
            if (found.any { it.field in ADVANCED_FIELDS }) showAdvanced = true
            return
        }
        scope.launch {
            withContext(Dispatchers.IO) {
                val updated = ProfileEditing.upsert(book, originalName, draft)
                container.profileRepository.save(updated.profiles, updated.activeName)
            }
            onDone()
        }
    }

    fun delete() {
        val target = originalName ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                val updated = ProfileEditing.delete(book, target)
                container.profileRepository.save(updated.profiles, updated.activeName)
            }
            onDone()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            AipbTopBar(title = if (originalName == null) "New profile" else "Edit profile", onBack = onDone)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = errorFor(ProfileField.NAME) != null,
                    supportingText = (errorFor(ProfileField.NAME) ?: renameHint(originalName, name))?.let { { Text(it) } },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Provider", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ProviderRegistry.all, key = { it.id }) { definition ->
                            ActionChip(
                                label = definition.displayName,
                                iconId = "public",
                                selected = definition.id == providerId,
                                onClick = { selectProvider(definition) },
                            )
                        }
                    }
                    errorFor(ProfileField.PROVIDER)?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }

                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    singleLine = true,
                    textStyle = AipbMono,
                    isError = errorFor(ProfileField.MODEL) != null,
                    supportingText = errorFor(ProfileField.MODEL)?.let { { Text(it) } },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (provider?.requiresBaseUrl == true || showAdvanced) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text(if (provider?.requiresBaseUrl == true) "Base URL" else "Base URL (optional)") },
                        singleLine = true,
                        textStyle = AipbMono,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        isError = errorFor(ProfileField.BASE_URL) != null,
                        supportingText = (errorFor(ProfileField.BASE_URL) ?: provider?.defaultBaseUrl?.takeIf { it.isNotEmpty() }?.let { "Default: $it" })
                            ?.let { { Text(it) } },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (showAdvanced) {
                    OutlinedTextField(
                        value = keyPool,
                        onValueChange = { keyPool = it },
                        label = { Text("Key pool (optional)") },
                        singleLine = true,
                        textStyle = AipbMono,
                        supportingText = { Text("Empty uses the provider's own keys (${provider?.keyPool ?: providerId}).") },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = temperature,
                        onValueChange = { temperature = it },
                        label = { Text("Temperature (optional)") },
                        singleLine = true,
                        textStyle = AipbMono,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = errorFor(ProfileField.TEMPERATURE) != null,
                        supportingText = errorFor(ProfileField.TEMPERATURE)?.let { { Text(it) } },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = maxTokens,
                        onValueChange = { maxTokens = it },
                        label = { Text("Max output tokens (optional)") },
                        singleLine = true,
                        textStyle = AipbMono,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = errorFor(ProfileField.MAX_TOKENS) != null,
                        supportingText = errorFor(ProfileField.MAX_TOKENS)?.let { { Text(it) } },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = timeout,
                        onValueChange = { timeout = it },
                        label = { Text("Request timeout (seconds)") },
                        singleLine = true,
                        textStyle = AipbMono,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = errorFor(ProfileField.TIMEOUT) != null,
                        supportingText = errorFor(ProfileField.TIMEOUT)?.let { { Text(it) } },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                TextAction(
                    if (showAdvanced) "Hide advanced options" else "Advanced options",
                    onClick = { showAdvanced = !showAdvanced },
                )

                PrimaryButton("Save profile", onClick = ::save, modifier = Modifier.fillMaxWidth())
                if (originalName != null) {
                    if (ProfileEditing.canDelete(book, originalName)) {
                        TextAction("Delete profile", onClick = { confirmDelete = true }, destructive = true, modifier = Modifier.fillMaxWidth())
                    } else {
                        Text(
                            "This is your only profile, so it cannot be deleted.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete profile?",
            message = "\"$originalName\" will be removed. Its API keys stay saved. Actions that use it by name will fall back to the active profile.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

private val ADVANCED_FIELDS = setOf(ProfileField.TEMPERATURE, ProfileField.MAX_TOKENS, ProfileField.TIMEOUT)

/** Shown while renaming: other places refer to profiles by NAME, so they would silently stop matching. */
private fun renameHint(originalName: String?, current: String): String? =
    if (originalName != null && current.trim() != originalName) {
        "Actions and chats that use \"$originalName\" by name will fall back to the active profile."
    } else {
        null
    }
