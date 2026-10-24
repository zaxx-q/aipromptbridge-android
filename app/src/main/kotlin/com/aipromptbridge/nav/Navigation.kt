package com.aipromptbridge.nav

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aipromptbridge.MainActivity
import com.aipromptbridge.core.tool.ToolId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Every screen of the launcher app. A sealed interface keeps `when (screen)` exhaustive, so adding a screen without
 * handling it is a compile error. (Navigation Compose was not needed: the graph is tiny and has no deep-link needs
 * beyond [Destination].)
 */
sealed interface Screen {
    data object Settings : Screen
    data object Profiles : Screen

    /** [originalName] == null creates a new profile. */
    data class ProfileEdit(val originalName: String?) : Screen
    data object ApiKeys : Screen
    data class KeyPool(val pool: String) : Screen
    data class Actions(val tool: ToolId) : Screen

    /** [actionId] == null creates a new action. */
    data class ActionEdit(val tool: ToolId, val actionId: String?) : Screen

    data object Modifiers : Screen

    /** [key] == null creates a new modifier. */
    data class ModifierEdit(val key: String?) : Screen
}

/** Where other activities (the text sheet, Snip) can send the user inside the launcher app. */
enum class Destination(val extraValue: String) {
    API_KEYS("api_keys"),
    TEXT_ACTIONS("text_actions"),
    SNIP_ACTIONS("snip_actions"),
    ;

    /** The screens to show, root first, so Back walks up to Settings. */
    fun stack(): List<Screen> = listOf(
        Screen.Settings,
        when (this) {
            API_KEYS -> Screen.ApiKeys
            TEXT_ACTIONS -> Screen.Actions(ToolId.TEXT_EDIT)
            SNIP_ACTIONS -> Screen.Actions(ToolId.SNIP)
        },
    )

    companion object {
        const val EXTRA = "com.aipromptbridge.DESTINATION"

        fun fromIntent(intent: Intent?): Destination? {
            val value = intent?.getStringExtra(EXTRA) ?: return null
            return entries.firstOrNull { it.extraValue == value }
        }
    }
}

/** Intent that opens the launcher app, optionally straight on [destination]. */
fun mainActivityIntent(context: Context, destination: Destination? = null): Intent =
    Intent(context, MainActivity::class.java).apply {
        if (destination != null) putExtra(Destination.EXTRA, destination.extraValue)
    }

/** Holds the back stack in a ViewModel so rotating the device does not throw the user back to the root. */
class NavViewModel(initialStack: List<Screen>) : ViewModel() {
    private val state = MutableStateFlow(initialStack.ifEmpty { listOf(Screen.Settings) })
    val stack: StateFlow<List<Screen>> = state.asStateFlow()

    fun push(screen: Screen) {
        state.value = state.value + screen
    }

    /** Swaps the current screen for [screen] (used by tab-like switches, so Back does not step through every tab). */
    fun replaceTop(screen: Screen) {
        state.value = state.value.dropLast(1) + screen
    }

    /** Returns false when already at the root (the caller should then finish the activity). */
    fun pop(): Boolean {
        if (state.value.size <= 1) return false
        state.value = state.value.dropLast(1)
        return true
    }
}

class NavViewModelFactory(private val initialStack: List<Screen>) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = NavViewModel(initialStack) as T
}
