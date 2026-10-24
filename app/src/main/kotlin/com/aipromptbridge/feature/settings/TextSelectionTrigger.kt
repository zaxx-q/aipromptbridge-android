package com.aipromptbridge.feature.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.aipromptbridge.feature.textedit.ProcessTextActivity

/**
 * The "Text selection menu" trigger. Android lists an app in the selection menu iff an enabled activity declares the
 * PROCESS_TEXT intent filter, so switching the trigger on/off means enabling/disabling [ProcessTextActivity]. The
 * component state is stored by the system, so it is also the source of truth (nothing to keep in settings.json).
 */
object TextSelectionTrigger {
    private fun component(context: Context) = ComponentName(context, ProcessTextActivity::class.java)

    /** DEFAULT means "whatever the manifest says", and the manifest enables it. */
    fun isEnabled(context: Context): Boolean =
        when (context.packageManager.getComponentEnabledSetting(component(context))) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            -> false
            else -> true
        }

    fun setEnabled(context: Context, enabled: Boolean) {
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        // DONT_KILL_APP: flipping the trigger must not restart the running settings screen.
        context.packageManager.setComponentEnabledSetting(component(context), state, PackageManager.DONT_KILL_APP)
    }
}
