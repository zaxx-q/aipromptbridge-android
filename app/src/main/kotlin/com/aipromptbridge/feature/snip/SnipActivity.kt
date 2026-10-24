package com.aipromptbridge.feature.snip

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aipromptbridge.AipbApp
import com.aipromptbridge.designsystem.Panel
import com.aipromptbridge.designsystem.PrimaryButton
import com.aipromptbridge.feature.settings.AipbAppTheme
import com.aipromptbridge.nav.Destination
import com.aipromptbridge.nav.mainActivityIntent

/**
 * Hosts the whole Snip flow in ONE transparent activity (an activity that is already in the foreground may start
 * further UI, which avoids background-activity-start limits):
 *   consent dialog -> ScreenCaptureService grabs a frame -> RegionSelector -> SnipActionSheet.
 *
 * While in [SnipStage.Capturing] this activity draws NOTHING, otherwise it would appear in the screenshot.
 */
class SnipActivity : ComponentActivity() {

    private val viewModel: SnipViewModel by viewModels {
        SnipViewModelFactory((application as AipbApp).container)
    }

    private val consentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode != RESULT_OK || data == null) {
            finish() // user declined
        } else {
            val service = Intent(this, ScreenCaptureService::class.java)
                .putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                .putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
            startForegroundService(service)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AipbAppTheme((application as AipbApp).container.settingsStore) {
                SnipScreen(
                    viewModel = viewModel,
                    onClose = { finish() },
                    onOpenSetup = {
                        startActivity(mainActivityIntent(this, Destination.API_KEYS))
                        finish()
                    },
                    onEditActions = {
                        startActivity(mainActivityIntent(this, Destination.SNIP_ACTIONS))
                        finish()
                    },
                )
            }
        }

        // Only ask for consent on a fresh start (not after rotation / process-kept recreation).
        if (savedInstanceState == null) {
            CaptureBus.reset()
            val manager = getSystemService(MediaProjectionManager::class.java)
            consentLauncher.launch(manager.createScreenCaptureIntent())
        }
    }
}

@Composable
private fun SnipScreen(viewModel: SnipViewModel, onClose: () -> Unit, onOpenSetup: () -> Unit, onEditActions: () -> Unit) {
    val stage by viewModel.stage.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        CaptureBus.events.collect { event -> if (event != null) viewModel.onCaptureEvent(event) }
    }

    when (val current = stage) {
        SnipStage.Capturing -> Box(Modifier.fillMaxSize()) // intentionally empty & transparent

        is SnipStage.Selecting -> RegionSelector(
            screenshot = current.screenshot,
            onCancel = onClose,
            onConfirm = viewModel::onSelection,
        )

        is SnipStage.Acting -> {
            val background = remember(current.screenshot) { current.screenshot.asImageBitmap() }
            Box(Modifier.fillMaxSize()) {
                Image(background, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                SnipActionSheet(
                    viewModel = viewModel,
                    acting = current,
                    onClose = onClose,
                    onOpenSetup = onOpenSetup,
                    onEditActions = onEditActions,
                )
            }
        }

        is SnipStage.Failed -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Panel {
                    Text("Could not capture the screen", style = MaterialTheme.typography.titleMedium)
                    Text(current.message, style = MaterialTheme.typography.bodyLarge)
                }
                PrimaryButton("Close", onClick = onClose)
            }
        }
    }
}
