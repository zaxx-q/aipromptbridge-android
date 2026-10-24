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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aipromptbridge.AipbApp
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
import com.aipromptbridge.feature.textedit.ChatMode
import com.aipromptbridge.feature.textedit.ChatViewModel

private val RESPONSE_MODES = listOf(ResponseMode.SHOW, ResponseMode.COPY, ResponseMode.SHARE)

/**
 * Bottom sheet shown after a region was selected (Stitch: "Snip action bottom sheet").
 *
 * Two views share one header: the RESULT view (actions, response mode, result) and, after "Continue in chat", the CHAT
 * view. The chat is never placed inside the scrolling result column: it contains its own LazyColumn, and a lazy list in
 * a vertically scrolling parent crashes ("infinite maximum height"). It sits directly in the sheet column, bounded.
 */
@Composable
fun SnipActionSheet(
    viewModel: SnipViewModel,
    acting: SnipStage.Acting,
    onClose: () -> Unit,
    onOpenSetup: () -> Unit,
    onEditActions: () -> Unit,
) {
    val context = LocalContext.current
    val result by viewModel.result.collectAsStateWithLifecycle()
    val bar by viewModel.bar.collectAsStateWithLifecycle()
    val mode by viewModel.responseMode.collectAsStateWithLifecycle()
    var instruction by rememberSaveable { mutableStateOf("") }
    /** [SnipResult.Done.runId] of the exchange being discussed, or -1 while the result view is shown. */
    var chatRunId by rememberSaveable { mutableIntStateOf(-1) }
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

    // The chat only exists for the result it was started from. Running another action, or retaking, shows the result view.
    val done = result as? SnipResult.Done
    val chatting = done != null && done.runId == chatRunId

    AipbBottomSheet(onDismiss = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- header: measurement readout (mono), retake / back, close
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AipbIcons.Crop, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    "${acting.crop.width} × ${acting.crop.height} · PNG",
                    style = AipbMono,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                if (chatting) {
                    TextAction("Back to result", onClick = { chatRunId = -1 })
                } else {
                    TextAction("Retake", onClick = viewModel::retake)
                }
                IconButton(onClick = onClose) { Icon(AipbIcons.Close, contentDescription = "Close") }
            }

            if (chatting && done != null) {
                SnipChat(
                    done = done,
                    acting = acting,
                    thumbnail = thumbnail,
                    onOpenSetup = onOpenSetup,
                    onEditActions = onEditActions,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
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
                            SecondaryButton(
                                "Continue in chat",
                                onClick = { chatRunId = current.runId },
                                icon = AipbIcons.byId("chat"),
                                modifier = Modifier.fillMaxWidth(),
                            )
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
    }
}

/**
 * Chat about the captured region. One ChatViewModel per result, keyed by [SnipResult.Done.runId]: leaving to the result
 * view and coming back resumes the same conversation, and rotation keeps it (the VM is scoped to the activity).
 */
@Composable
private fun SnipChat(
    done: SnipResult.Done,
    acting: SnipStage.Acting,
    thumbnail: ImageBitmap,
    onOpenSetup: () -> Unit,
    onEditActions: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember(context) { (context.applicationContext as AipbApp).container }
    val chat: ChatViewModel = viewModel(
        key = "snip-chat-${done.runId}",
        factory = SnipChatViewModelFactory(container, done, acting.pngBase64),
    )
    ChatMode(
        viewModel = chat,
        onReplace = {}, // read-only chat: there is no selection to replace, so the Replace button is never shown
        onCopy = { context.copyToClipboard(it) },
        onOpenSetup = onOpenSetup,
        onEditActions = onEditActions,
        header = { SnipChatHeader(thumbnail) },
    )
}

@Composable
private fun SnipChatHeader(thumbnail: ImageBitmap) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(
            bitmap = thumbnail,
            contentDescription = "Selected region",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .heightIn(max = 56.dp)
                .widthIn(max = 96.dp)
                .clip(MaterialTheme.shapes.small),
        )
        Text(
            "The image is sent with every message.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}
