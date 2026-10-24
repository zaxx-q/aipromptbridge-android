package com.aipromptbridge.feature

import androidx.compose.runtime.Composable
import com.aipromptbridge.core.keys.KeyEntry
import com.aipromptbridge.core.profile.ConnectionProfile
import com.aipromptbridge.core.profile.ProfileBook
import com.aipromptbridge.core.prompt.DefaultPrompts
import com.aipromptbridge.core.settings.DefaultResponse
import com.aipromptbridge.core.settings.ThemeMode
import com.aipromptbridge.core.tool.ToolId
import com.aipromptbridge.designsystem.AipbTheme
import com.aipromptbridge.designsystem.ThemePreviews
import com.aipromptbridge.feature.prompteditor.ActionsContent
import com.aipromptbridge.feature.prompteditor.EditorSnapshot
import com.aipromptbridge.feature.prompteditor.ModifiersContent
import com.aipromptbridge.feature.settings.ApiKeysContent
import com.aipromptbridge.feature.settings.ConnectionSummary
import com.aipromptbridge.feature.settings.KeyPoolContent
import com.aipromptbridge.feature.settings.KeyPoolRow
import com.aipromptbridge.feature.settings.ProfilesContent
import com.aipromptbridge.feature.settings.SettingsCallbacks
import com.aipromptbridge.feature.settings.SettingsContent
import com.aipromptbridge.feature.settings.SettingsUiState

/*
 * Previews of the stateless screen content (Light + Dark). Compare against design/stitch/<screen>/screen.png.
 * The sample values below exist only for previews; the real screens always render data from the repositories.
 */

@ThemePreviews
@Composable
private fun SettingsPreview() = AipbTheme {
    SettingsContent(
        state = SettingsUiState(
            connection = ConnectionSummary("Default", "gemini-3.5-flash", usingBuiltInDefault = false, keyCount = 2),
            themeMode = ThemeMode.SYSTEM,
            defaultResponse = DefaultResponse.SHOW,
            textMenuEnabled = true,
            snipTileAdded = false,
            canRequestTile = true,
        ),
        callbacks = SettingsCallbacks(
            onBack = {},
            onOpenProfiles = {},
            onOpenKeys = {},
            onToggleTextMenu = {},
            onAddTile = {},
            onOpenActions = {},
            onOpenModifiers = {},
            onThemeChange = {},
            onDefaultResponseChange = {},
        ),
    )
}

@ThemePreviews
@Composable
private fun SettingsNoKeysPreview() = AipbTheme {
    SettingsContent(
        state = SettingsUiState(
            connection = ConnectionSummary("Default", "gemini-3.5-flash", usingBuiltInDefault = true, keyCount = 0),
            themeMode = ThemeMode.DARK,
            defaultResponse = DefaultResponse.COPY,
            textMenuEnabled = false,
            snipTileAdded = true,
            canRequestTile = true,
        ),
        callbacks = SettingsCallbacks({}, {}, {}, {}, {}, {}, {}, {}, {}),
    )
}

@ThemePreviews
@Composable
private fun ProfilesPreview() = AipbTheme {
    ProfilesContent(
        book = ProfileBook(
            listOf(
                ConnectionProfile("Fast", "google", "gemini-3.5-flash"),
                ConnectionProfile("Smart", "openai", "gpt-5"),
            ),
            activeName = "Fast",
        ),
        onBack = {},
        onAdd = {},
        onEdit = {},
        onSetActive = {},
    )
}

@ThemePreviews
@Composable
private fun ApiKeysPreview() = AipbTheme {
    ApiKeysContent(
        rows = listOf(
            KeyPoolRow("google", "Google Gemini", keyCount = 2, inUse = true),
            KeyPoolRow("openai", "OpenAI", keyCount = 0, inUse = false),
        ),
        onBack = {},
        onOpenPool = {},
    )
}

@ThemePreviews
@Composable
private fun KeyPoolPreview() = AipbTheme {
    KeyPoolContent(
        title = "Google Gemini",
        entries = listOf(KeyEntry("Key 1", "abcd-1234-efgh-5678"), KeyEntry("Spare", "zzzz-0000-yyyy-9999")),
        onBack = {},
        onAdd = {},
        onEdit = {},
        onDelete = {},
    )
}

@ThemePreviews
@Composable
private fun ActionsPreview() = AipbTheme {
    val defaults = DefaultPrompts.build().tool(ToolId.TEXT_EDIT)
    ActionsContent(
        tool = ToolId.TEXT_EDIT,
        snapshot = EditorSnapshot(defaults, defaults),
        onBack = {},
        onSelectTool = {},
        onNew = {},
        onEdit = {},
        onToggleBar = { _, _ -> },
        onMoveOnBar = { _, _ -> },
        onRestore = {},
        onOpenModifiers = {},
    )
}

@ThemePreviews
@Composable
private fun ModifiersPreview() = AipbTheme {
    val defaults = DefaultPrompts.build().modifiers
    ModifiersContent(
        modifiers = defaults.mapIndexed { i, m -> if (i == 0) m.copy(injection = m.injection + "\nKeep it brief.") else m },
        defaults = defaults,
        onBack = {},
        onNew = {},
        onEdit = {},
    )
}
