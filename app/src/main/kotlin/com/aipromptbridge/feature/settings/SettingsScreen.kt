package com.aipromptbridge.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aipromptbridge.AppContainer
import com.aipromptbridge.core.keys.KeyEditing
import com.aipromptbridge.core.profile.ProfileEditing
import com.aipromptbridge.core.profile.ProfileResolver
import com.aipromptbridge.core.settings.DefaultResponse
import com.aipromptbridge.core.settings.ThemeMode
import com.aipromptbridge.core.tool.ToolId
import com.aipromptbridge.designsystem.Aipb
import com.aipromptbridge.designsystem.AipbIcons
import com.aipromptbridge.designsystem.AipbTopBar
import com.aipromptbridge.designsystem.ChoiceDialog
import com.aipromptbridge.designsystem.RowDivider
import com.aipromptbridge.designsystem.RowValue
import com.aipromptbridge.designsystem.SectionHeader
import com.aipromptbridge.designsystem.SettingsRow
import com.aipromptbridge.designsystem.StatusLabel
import com.aipromptbridge.designsystem.SwitchRow
import com.aipromptbridge.designsystem.TextAction
import com.aipromptbridge.designsystem.subtitleWithMono
import com.aipromptbridge.feature.snip.requestAddSnipTile
import com.aipromptbridge.nav.Screen
import com.aipromptbridge.tools.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the "Connection" rows show: the profile requests resolve to right now, and how many keys it can use. */
data class ConnectionSummary(
    val profileName: String,
    val model: String,
    /** True when no profile was ever saved, so the hard-coded fallback profile is what requests use. */
    val usingBuiltInDefault: Boolean,
    val keyCount: Int,
)

/** Reads profiles.json and the encrypted key file, so call it off the main thread. */
fun loadConnectionSummary(container: AppContainer): ConnectionSummary {
    val saved = container.profileRepository.profiles()
    val profile = ProfileResolver.resolve(container.profileRepository)
    val pool = ProfileEditing.effectivePool(profile)
    val keys = container.keyRepository.loadKeys(pool).count { it.value.isNotBlank() }
    return ConnectionSummary(profile.name, profile.model, usingBuiltInDefault = saved.isEmpty(), keyCount = keys)
}

data class SettingsUiState(
    /** null while loading. */
    val connection: ConnectionSummary?,
    val themeMode: ThemeMode,
    val defaultResponse: DefaultResponse,
    val textMenuEnabled: Boolean,
    val snipTileAdded: Boolean,
    /** Android 13+ can show the "add tile" prompt from inside the app. */
    val canRequestTile: Boolean,
)

class SettingsCallbacks(
    val onBack: () -> Unit,
    val onOpenProfiles: () -> Unit,
    val onOpenKeys: () -> Unit,
    val onToggleTextMenu: (Boolean) -> Unit,
    val onAddTile: () -> Unit,
    val onOpenActions: (ToolId) -> Unit,
    val onOpenModifiers: () -> Unit,
    val onThemeChange: (ThemeMode) -> Unit,
    val onDefaultResponseChange: (DefaultResponse) -> Unit,
)

fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "System default"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

fun responseLabel(mode: DefaultResponse): String = when (mode) {
    DefaultResponse.SHOW -> "Show"
    DefaultResponse.COPY -> "Copy"
    DefaultResponse.SHARE -> "Share"
}

/** Stateless Settings screen (Stitch: `aipromptbridge_settings`). Previewable; all behaviour arrives via [callbacks]. */
@Composable
fun SettingsContent(state: SettingsUiState, callbacks: SettingsCallbacks) {
    var showTheme by rememberSaveable { mutableStateOf(false) }
    var showResponse by rememberSaveable { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            AipbTopBar(title = "Settings", onBack = callbacks.onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                // ---- Connection
                SectionHeader("Connection")
                val connection = state.connection
                SettingsRow(
                    title = "Connection profiles",
                    icon = AipbIcons.Provider,
                    subtitle = connection?.let {
                        subtitleWithMono(if (it.usingBuiltInDefault) "Built-in default" else it.profileName, it.model)
                    },
                    onClick = callbacks.onOpenProfiles,
                )
                RowDivider()
                SettingsRow(
                    title = "API keys",
                    icon = AipbIcons.Key,
                    subtitle = connection?.let { KeyEditing.summary(it.keyCount) },
                    subtitleIsError = connection != null && connection.keyCount == 0,
                    onClick = callbacks.onOpenKeys,
                )
                RowDivider()

                // ---- Triggers
                SectionHeader("Triggers")
                SwitchRow(
                    title = "Text selection menu",
                    subtitle = "Show AIPromptBridge in the menu when you select text in other apps.",
                    checked = state.textMenuEnabled,
                    onCheckedChange = callbacks.onToggleTextMenu,
                    icon = AipbIcons.TextSelection,
                )
                RowDivider()
                when {
                    state.snipTileAdded -> SettingsRow(
                        title = "Quick Settings tile",
                        subtitle = "Added. To remove it, edit your Quick Settings tiles.",
                        icon = AipbIcons.Snip,
                        trailing = { StatusLabel("Added", accent = true) },
                    )
                    state.canRequestTile -> SettingsRow(
                        title = "Quick Settings tile",
                        subtitle = "Start Snip & Ask from the notification shade.",
                        icon = AipbIcons.Snip,
                        trailing = { TextAction("Add", onClick = callbacks.onAddTile) },
                    )
                    else -> SettingsRow(
                        title = "Quick Settings tile",
                        subtitle = "Open Quick Settings, tap Edit, and drag \"Snip & Ask\" into your tiles.",
                        icon = AipbIcons.Snip,
                    )
                }
                RowDivider()

                // ---- Behaviour
                SectionHeader("Behavior")
                SettingsRow(
                    title = "Default response mode",
                    subtitle = "Where Snip & Ask sends its result. Text Edit actions choose for themselves.",
                    icon = AipbIcons.Wand,
                    onClick = { showResponse = true },
                    trailing = { RowValue(responseLabel(state.defaultResponse)) },
                )
                RowDivider()

                // ---- Prompts
                SectionHeader("Actions")
                SettingsRow(
                    title = "Text Edit actions",
                    subtitle = "Edit, add and reorder the actions in the selection sheet.",
                    icon = AipbIcons.Edit,
                    onClick = { callbacks.onOpenActions(ToolId.TEXT_EDIT) },
                )
                RowDivider()
                SettingsRow(
                    title = "Snip & Ask actions",
                    subtitle = "Edit, add and reorder the actions for captured regions.",
                    icon = AipbIcons.Crop,
                    onClick = { callbacks.onOpenActions(ToolId.SNIP) },
                )
                RowDivider()
                SettingsRow(
                    title = "Modifiers",
                    subtitle = "Switches like Shorter or Formal that adjust every text action.",
                    icon = AipbIcons.Tune,
                    onClick = callbacks.onOpenModifiers,
                )
                RowDivider()

                // ---- Appearance
                SectionHeader("Appearance")
                SettingsRow(
                    title = "Theme",
                    subtitle = "System, Light or Dark.",
                    icon = AipbIcons.Palette,
                    onClick = { showTheme = true },
                    trailing = { RowValue(themeLabel(state.themeMode)) },
                )
                RowDivider()

                // ---- Tools (what each tool does and whether it is finished)
                SectionHeader("Tools")
                for ((index, tool) in ToolRegistry.all.withIndex()) {
                    if (index > 0) RowDivider()
                    SettingsRow(
                        title = tool.title,
                        subtitle = tool.howToUse,
                        trailing = {
                            Text(
                                if (tool.implemented) "Ready" else "In progress",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (tool.implemented) Aipb.extra.success else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
                RowDivider()
            }
        }
    }

    if (showTheme) {
        ChoiceDialog(
            title = "Theme",
            options = ThemeMode.entries.map { it to themeLabel(it) },
            selected = state.themeMode,
            onSelect = {
                callbacks.onThemeChange(it)
                showTheme = false
            },
            onDismiss = { showTheme = false },
        )
    }
    if (showResponse) {
        ChoiceDialog(
            title = "Default response mode",
            options = listOf(
                DefaultResponse.SHOW to "Show in the sheet",
                DefaultResponse.COPY to "Copy to clipboard",
                DefaultResponse.SHARE to "Open the share sheet",
            ),
            selected = state.defaultResponse,
            onSelect = {
                callbacks.onDefaultResponseChange(it)
                showResponse = false
            },
            onDismiss = { showResponse = false },
        )
    }
}

/** Connects [SettingsContent] to the container: loads the connection summary and persists preference changes. */
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit, navigate: (Screen) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle()

    // Re-read whenever this screen enters composition again (e.g. coming back from the key or profile screens).
    val connection by produceState<ConnectionSummary?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { loadConnectionSummary(container) }
    }
    var textMenuEnabled by remember { mutableStateOf(TextSelectionTrigger.isEnabled(context)) }

    SettingsContent(
        state = SettingsUiState(
            connection = connection,
            themeMode = settings.themeMode,
            defaultResponse = settings.defaultResponse,
            textMenuEnabled = textMenuEnabled,
            snipTileAdded = settings.snipTileAdded,
            canRequestTile = Build.VERSION.SDK_INT >= 33,
        ),
        callbacks = SettingsCallbacks(
            onBack = onBack,
            onOpenProfiles = { navigate(Screen.Profiles) },
            onOpenKeys = { navigate(Screen.ApiKeys) },
            onToggleTextMenu = { enabled ->
                TextSelectionTrigger.setEnabled(context, enabled)
                textMenuEnabled = enabled
            },
            onAddTile = { requestAddSnipTile(context) },
            onOpenActions = { tool -> navigate(Screen.Actions(tool)) },
            onOpenModifiers = { navigate(Screen.Modifiers) },
            onThemeChange = { mode ->
                scope.launch(Dispatchers.IO) { container.settingsStore.update { it.copy(themeMode = mode) } }
            },
            onDefaultResponseChange = { mode ->
                scope.launch(Dispatchers.IO) { container.settingsStore.update { it.copy(defaultResponse = mode) } }
            },
        ),
    )
}
