package com.aipromptbridge.feature.snip

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aipromptbridge.core.prompt.ResponseMode
import com.aipromptbridge.core.prompt.ResultDestination
import com.aipromptbridge.designsystem.ActionChip
import com.aipromptbridge.designsystem.AipbBottomSheet
import com.aipromptbridge.designsystem.AipbIcons
import com.aipromptbridge.designsystem.AipbMono
import com.aipromptbridge.designsystem.InstructionField
import com.aipromptbridge.designsystem.Panel
import com.aipromptbridge.designsystem.SecondaryButton
import com.aipromptbridge.designsystem.SegmentedToggle
import com.aipromptbridge.designsystem.SkeletonLines
import com.aipromptbridge.designsystem.TextAction
import com.aipromptbridge.designsystem.ToggleOption
import com.aipromptbridge.feature.common.ErrorPanel
import com.aipromptbridge.feature.common.copyToClipboard
import com.aipromptbridge.feature.common.shareText

private val RESPONSE_MODES = listOf(ResponseMode.SHOW, ResponseMode.COPY, ResponseMode.SHARE)

/** Bottom sheet shown after a region was selected (Stitch: "Snip action bottom sheet"). */
@Composable
fun SnipActionSheet(
    viewModel: SnipViewModel,
    acting: SnipStage.Acting,
    onClose: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    val context = LocalContext.current
    val result by viewModel.result.collectAsStateWithLifecycle()
    val bar by viewModel.bar.collectAsStateWithLifecycle()
    val mode by viewModel.responseMode.collectAsStateWithLifecycle()
    var instruction by rememberSaveable { mutableStateOf("") }
    val thumbnail = remember(acting.crop) { acting.crop.asImageBitmap() }

    // COPY and SHARE act automatically when the result arrives; SHOW just displays it.
    LaunchedEffect(result) {
        val done = result
        if (done is SnipResult.Done) {
            when (done.destination) {
                ResultDestination.COPY -> context.copyToClipboard(done.text)
                ResultDestination.SHARE -> context.shareText(done.text)
                else -> Unit
            }
        }
    }

    AipbBottomSheet(onDismiss = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- header: measurement readout (mono), retake, close
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AipbIcons.Crop, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    "${acting.crop.width} × ${acting.crop.height} · PNG",
                    style = AipbMono,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                TextAction("Retake", onClick = viewModel::retake)
                IconButton(onClick = onClose) { Icon(AipbIcons.Close, contentDescription = "Close") }
            }

            // ---- the REAL cropped region (not a placeholder)
            Image(
                bitmap = thumbnail,
                contentDescription = "Selected region",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .clip(MaterialTheme.shapes.small),
            )

            // ---- where the result goes
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Send result to", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SegmentedToggle(
                    options = listOf(
                        ToggleOption("Show", "chat"),
                        ToggleOption("Copy", null),
                        ToggleOption("Share", null),
                    ),
                    selectedIndex = RESPONSE_MODES.indexOf(mode).coerceAtLeast(0),
                    onSelect = { viewModel.setResponseMode(RESPONSE_MODES[it]) },
                )
            }

            // ---- one-tap actions
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(bar, key = { it.id }) { action ->
                    ActionChip(
                        label = action.label,
                        iconId = action.icon,
                        selected = (result as? SnipResult.Loading)?.actionLabel == action.label,
                        onClick = { viewModel.run(action) },
                    )
                }
            }

            // ---- result area
            when (val current = result) {
                SnipResult.Idle -> Unit
                is SnipResult.Loading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Panel { SkeletonLines() }
                    TextAction("Stop", onClick = viewModel::stop)
                }

                is SnipResult.Done -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Panel {
                        Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                            // TODO(markdown): render Markdown here.
                            SelectionContainer { Text(current.text, style = MaterialTheme.typography.bodyLarge) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SecondaryButton("Copy", onClick = { context.copyToClipboard(current.text) }, icon = AipbIcons.Copy, modifier = Modifier.weight(1f))
                        SecondaryButton("Share", onClick = { context.shareText(current.text) }, icon = AipbIcons.Share, modifier = Modifier.weight(1f))
                    }
                    // TODO(chat-handoff): "Continue in chat" button -> SessionRepository + chat UI (README Phase 4).
                }

                is SnipResult.Error -> ErrorPanel(
                    message = current.message,
                    missingApiKey = current.missingApiKey,
                    onRetry = { viewModel.retake() },
                    onOpenSettings = onOpenSetup,
                )
            }

            InstructionField(
                value = instruction,
                onValueChange = { instruction = it },
                placeholder = "Ask about this image…",
                onSend = { viewModel.runCustom(instruction) },
                enabled = result !is SnipResult.Loading,
            )
        }
    }
}
