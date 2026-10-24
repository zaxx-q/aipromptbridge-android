package com.aipromptbridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aipromptbridge.designsystem.AipbTheme
import com.aipromptbridge.feature.prompteditor.ActionEditScreen
import com.aipromptbridge.feature.prompteditor.ActionsScreen
import com.aipromptbridge.feature.prompteditor.ModifierEditScreen
import com.aipromptbridge.feature.prompteditor.ModifiersScreen
import com.aipromptbridge.feature.settings.ApiKeysScreen
import com.aipromptbridge.feature.settings.KeyPoolScreen
import com.aipromptbridge.feature.settings.ProfileEditScreen
import com.aipromptbridge.feature.settings.ProfilesScreen
import com.aipromptbridge.feature.settings.SettingsScreen
import com.aipromptbridge.feature.settings.resolveDarkTheme
import com.aipromptbridge.nav.Destination
import com.aipromptbridge.nav.NavViewModel
import com.aipromptbridge.nav.NavViewModelFactory
import com.aipromptbridge.nav.Screen

/**
 * Launcher activity: hosts Settings and everything reachable from it (profiles, API keys, prompt editor).
 * Other activities can open it straight on a screen with [com.aipromptbridge.nav.mainActivityIntent].
 */
class MainActivity : ComponentActivity() {

    private val nav: NavViewModel by viewModels {
        NavViewModelFactory(Destination.fromIntent(intent)?.stack() ?: listOf(Screen.Settings))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as AipbApp).container
        setContent {
            val settings by container.settingsStore.settings.collectAsStateWithLifecycle()
            val dark = resolveDarkTheme(settings.themeMode)
            // enableEdgeToEdge() picks icon colours from the SYSTEM theme; a forced Light/Dark choice must override it.
            SideEffect { applySystemBarIcons(dark) }
            AipbTheme(darkTheme = dark) {
                AppRoot(container = container, nav = nav, onExit = { finish() })
            }
        }
    }

    private fun applySystemBarIcons(dark: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }
}

@Composable
private fun AppRoot(container: AppContainer, nav: NavViewModel, onExit: () -> Unit) {
    val stack by nav.stack.collectAsStateWithLifecycle()
    BackHandler(enabled = stack.size > 1) { nav.pop() }

    when (val screen = stack.last()) {
        Screen.Settings -> SettingsScreen(container, onBack = onExit, navigate = { nav.push(it) })

        Screen.Profiles -> ProfilesScreen(
            container,
            onBack = { nav.pop() },
            onEdit = { nav.push(Screen.ProfileEdit(it)) },
        )
        is Screen.ProfileEdit -> ProfileEditScreen(container, screen.originalName, onDone = { nav.pop() })

        Screen.ApiKeys -> ApiKeysScreen(
            container,
            onBack = { nav.pop() },
            onOpenPool = { nav.push(Screen.KeyPool(it)) },
        )
        is Screen.KeyPool -> KeyPoolScreen(container, screen.pool, onBack = { nav.pop() })

        is Screen.Actions -> ActionsScreen(
            container,
            tool = screen.tool,
            onBack = { nav.pop() },
            onSelectTool = { nav.replaceTop(Screen.Actions(it)) },
            onNew = { nav.push(Screen.ActionEdit(it, null)) },
            onEdit = { tool, id -> nav.push(Screen.ActionEdit(tool, id)) },
            onOpenModifiers = { nav.push(Screen.Modifiers) },
        )
        is Screen.ActionEdit -> ActionEditScreen(container, screen.tool, screen.actionId, onDone = { nav.pop() })

        Screen.Modifiers -> ModifiersScreen(
            container,
            onBack = { nav.pop() },
            onNew = { nav.push(Screen.ModifierEdit(null)) },
            onEdit = { nav.push(Screen.ModifierEdit(it)) },
        )
        is Screen.ModifierEdit -> ModifierEditScreen(container, screen.key, onDone = { nav.pop() })
    }
}
