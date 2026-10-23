package com.aipromptbridge.feature.settings

import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aipromptbridge.AppContainer
import com.aipromptbridge.core.keys.KeyEntry
import com.aipromptbridge.core.profile.ConnectionProfile
import com.aipromptbridge.core.profile.ProfileResolver
import com.aipromptbridge.core.provider.ProviderDefinition
import com.aipromptbridge.core.provider.ProviderRegistry
import com.aipromptbridge.designsystem.ActionChip
import com.aipromptbridge.designsystem.Aipb
import com.aipromptbridge.designsystem.Panel
import com.aipromptbridge.designsystem.PrimaryButton
import com.aipromptbridge.designsystem.SecondaryButton
import com.aipromptbridge.feature.snip.requestAddSnipTile
import com.aipromptbridge.tools.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Minimal first-run setup: pick a provider, set a model, paste ONE API key, save.
 * This is deliberately small (README Phase 3). The full Settings screen from the Stitch design
 * (connection profiles list, multi-key pools, triggers, behaviour, theme) is README Phase 5.
 */
@Composable
fun SetupScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var provider: ProviderDefinition by remember { mutableStateOf(ProviderRegistry.all.first()) }
    var model by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var hasKey by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    // Load the saved profile and whether a key exists (disk access -> IO dispatcher).
    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            val profile = ProfileResolver.resolve(container.profileRepository)
            val definition = ProviderRegistry.find(profile.provider) ?: ProviderRegistry.all.first()
            val keyPresent = container.keyRepository.loadKeys(profile.apiKeyPool.ifBlank { definition.keyPool }).isNotEmpty()
            Triple(profile, definition, keyPresent)
        }
        provider = loaded.second
        model = loaded.first.model
        baseUrl = loaded.first.baseUrl
        hasKey = loaded.third
    }

    fun selectProvider(definition: ProviderDefinition) {
        provider = definition
        model = definition.defaultModel
        baseUrl = ""
        status = null
        scope.launch {
            hasKey = withContext(Dispatchers.IO) { container.keyRepository.loadKeys(definition.keyPool).isNotEmpty() }
        }
    }

    fun save() {
        val definition = provider
        if (definition.requiresBaseUrl && baseUrl.isBlank()) {
            status = "A custom provider needs a base URL (for example https://my-server/v1)."
            return
        }
        scope.launch {
            withContext(Dispatchers.IO) {
                val profile = ConnectionProfile(
                    name = ConnectionProfile.DEFAULT_NAME,
                    provider = definition.id,
                    model = model.trim().ifBlank { definition.defaultModel },
                    baseUrl = baseUrl.trim(),
                )
                container.profileRepository.save(listOf(profile), profile.name)
                if (apiKey.isNotBlank()) {
                    container.keyRepository.saveKeys(definition.keyPool, listOf(KeyEntry("Key 1", apiKey.trim())))
                    container.keyPools.invalidate(definition.keyPool)
                }
            }
            hasKey = withContext(Dispatchers.IO) { container.keyRepository.loadKeys(definition.keyPool).isNotEmpty() }
            apiKey = ""
            status = "Saved."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("AIPromptBridge", style = MaterialTheme.typography.headlineSmall)
            Text(
                "AI actions for the text and screens you are already working in.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Connection", style = MaterialTheme.typography.titleLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ProviderRegistry.all, key = { it.id }) { definition ->
                    ActionChip(
                        label = definition.displayName,
                        iconId = "public",
                        selected = definition.id == provider.id,
                        onClick = { selectProvider(definition) },
                    )
                }
            }
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            if (provider.requiresBaseUrl) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(if (hasKey) "API key (saved — type to replace)" else "API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton("Save", onClick = ::save, modifier = Modifier.fillMaxWidth())
            status?.let { message ->
                val isOk = message == "Saved."
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isOk) Aipb.extra.success else MaterialTheme.colorScheme.error,
                )
            }
            // TODO(multi-key): support several keys per provider with rotation (KeyPool already does the rotating).
            // TODO(profiles): list / add / edit / delete ConnectionProfiles and pick the active one (README Phase 5).
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tools", style = MaterialTheme.typography.titleLarge)
            for (tool in ToolRegistry.all) {
                Panel {
                    Text(tool.title, style = MaterialTheme.typography.titleMedium)
                    Text(tool.description, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        tool.howToUse,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (tool.implemented) "Ready" else "In progress",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (tool.implemented) Aipb.extra.success else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (tool.id == com.aipromptbridge.core.tool.ToolId.SNIP && Build.VERSION.SDK_INT >= 33) {
                        SecondaryButton("Add Quick Settings tile", onClick = { requestAddSnipTile(context) })
                    }
                }
            }
        }
    }
}
