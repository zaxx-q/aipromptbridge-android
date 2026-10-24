package com.aipromptbridge.feature.snip

import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.aipromptbridge.AipbApp
import com.aipromptbridge.R
import kotlinx.coroutines.launch

/**
 * Quick Settings tile = the Snip trigger. Tapping it collapses the shade and opens [SnipActivity].
 * On Android 14+ (API 34) startActivityAndCollapse must be given a PendingIntent; the Intent overload is deprecated.
 */
class SnipTileService : TileService() {

    /** Android has no query API for "is my tile added?", so remember it from the system callbacks. */
    override fun onTileAdded() = recordTileAdded(this, true)

    override fun onTileRemoved() = recordTileAdded(this, false)

    override fun onStartListening() {
        qsTile?.let {
            it.state = Tile.STATE_INACTIVE
            it.updateTile()
        }
    }

    override fun onClick() {
        val intent = Intent(this, SnipActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}

/** Saves the tile state for the Settings screen. Writes a tiny file, so it runs off the main thread. */
private fun recordTileAdded(context: Context, added: Boolean) {
    val container = (context.applicationContext as AipbApp).container
    container.appScope.launch { container.settingsStore.update { it.copy(snipTileAdded = added) } }
}

/** Asks the system to show the "Add tile?" prompt (API 33+). Safe to call on older versions: it does nothing. */
fun requestAddSnipTile(context: Context) {
    if (Build.VERSION.SDK_INT < 33) return
    val manager = context.getSystemService(StatusBarManager::class.java) ?: return
    manager.requestAddTileService(
        ComponentName(context, SnipTileService::class.java),
        context.getString(R.string.tile_label),
        Icon.createWithResource(context, R.drawable.ic_tile_snip),
        context.mainExecutor,
    ) { result ->
        Log.d("AIPromptBridge", "requestAddTileService result=$result")
        if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
            result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
        ) {
            recordTileAdded(context, true)
        }
    }
}
