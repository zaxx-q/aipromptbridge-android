package com.aipromptbridge.feature.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aipromptbridge.core.settings.SettingsStore
import com.aipromptbridge.core.settings.ThemeMode
import com.aipromptbridge.designsystem.AipbTheme

/** Resolves the stored preference to a concrete light/dark decision. Must be called from composition. */
@Composable
fun resolveDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** [AipbTheme] driven by the user's System / Light / Dark choice; every activity wraps its content in this. */
@Composable
fun AipbAppTheme(store: SettingsStore, content: @Composable () -> Unit) {
    val settings by store.settings.collectAsStateWithLifecycle()
    AipbTheme(darkTheme = resolveDarkTheme(settings.themeMode), content = content)
}
