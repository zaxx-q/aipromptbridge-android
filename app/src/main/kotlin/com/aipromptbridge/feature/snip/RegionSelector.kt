package com.aipromptbridge.feature.snip

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.aipromptbridge.core.snip.CropMath
import com.aipromptbridge.core.snip.PixelRect
import com.aipromptbridge.designsystem.AipbIcons
import com.aipromptbridge.designsystem.AipbMono
import com.aipromptbridge.designsystem.PrimaryButton
import com.aipromptbridge.designsystem.SecondaryButton
import com.aipromptbridge.designsystem.TextAction

/**
 * Full-screen frozen screenshot where the user drags a rectangle (Stitch: "Snip region selection").
 * Visual language: dimmed outside, print-style crop marks on the corners, mono pixel readout (a measurement).
 *
 * TODO(handles): after the first drag, allow resizing/moving the rectangle with corner handles.
 */
@Composable
fun RegionSelector(
    screenshot: Bitmap,
    onCancel: () -> Unit,
    onConfirm: (PixelRect) -> Unit,
) {
    var start by remember { mutableStateOf<Offset?>(null) }
    var end by remember { mutableStateOf<Offset?>(null) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val image = remember(screenshot) { screenshot.asImageBitmap() }
    val density = LocalDensity.current
    val accent = MaterialTheme.colorScheme.primary
    val dim = Color(0x99151B2F)

    val s = start
    val e = end
    val selection: Rect? =
        if (s != null && e != null) Rect(minOf(s.x, e.x), minOf(s.y, e.y), maxOf(s.x, e.x), maxOf(s.y, e.y)) else null

    val pixelRect: PixelRect? =
        if (selection != null && viewSize.width > 0 && viewSize.height > 0) {
            CropMath.viewToBitmap(
                selection.left, selection.top, selection.right, selection.bottom,
                viewSize.width.toFloat(), viewSize.height.toFloat(),
                screenshot.width, screenshot.height,
            )
        } else null

    val minSidePx = with(density) { 24.dp.toPx() }
    val canConfirm = selection != null && selection.width >= minSidePx && selection.height >= minSidePx

    Box(Modifier.fillMaxSize().onSizeChanged { viewSize = it }) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> start = offset; end = offset },
                        onDrag = { change, _ -> end = change.position },
                    )
                },
        ) {
            if (selection == null) {
                drawRect(dim)
            } else {
                clipRect(selection.left, selection.top, selection.right, selection.bottom, ClipOp.Difference) {
                    drawRect(dim)
                }
                drawRect(accent, topLeft = selection.topLeft, size = selection.size, style = Stroke(width = 1.dp.toPx()))
                drawCornerMarks(selection, accent, length = 24.dp.toPx(), thickness = 4.dp.toPx())
            }
        }

        // Hint
        Surface(
            color = Color(0xE6151B2F),
            contentColor = Color.White,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(AipbIcons.Crop, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Drag to select a region", style = MaterialTheme.typography.labelLarge)
            }
        }

        // Pixel readout, bottom-right of the selection (pixels of the REAL bitmap, not of the view)
        if (selection != null && pixelRect != null) {
            val readoutWidthPx = with(density) { 132.dp.toPx() }
            val gapPx = with(density) { 12.dp.toPx() }
            Surface(
                color = Color(0xE6151B2F),
                contentColor = Color.White,
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.offset {
                    IntOffset(
                        (selection.right - readoutWidthPx).toInt().coerceAtLeast(0),
                        (selection.bottom + gapPx).toInt(),
                    )
                },
            ) {
                Text(
                    "${pixelRect.width} × ${pixelRect.height}",
                    style = AipbMono,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    maxLines = 1,
                )
            }
        }

        // Bottom bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            Row(
                Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextAction("Cancel", onClick = onCancel)
                SecondaryButton(
                    "Full screen",
                    onClick = { onConfirm(PixelRect(0, 0, screenshot.width, screenshot.height)) },
                    icon = AipbIcons.Fullscreen,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    "Use selection",
                    onClick = { pixelRect?.let(onConfirm) },
                    enabled = canConfirm,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** L-shaped crop marks on the four corners (the "registration mark" look from print proofing). */
private fun DrawScope.drawCornerMarks(rect: Rect, color: Color, length: Float, thickness: Float) {
    fun mark(x: Float, y: Float, dx: Float, dy: Float) {
        drawLine(color, Offset(x, y), Offset(x + dx * length, y), thickness, StrokeCap.Square)
        drawLine(color, Offset(x, y), Offset(x, y + dy * length), thickness, StrokeCap.Square)
    }
    mark(rect.left, rect.top, 1f, 1f)
    mark(rect.right, rect.top, -1f, 1f)
    mark(rect.left, rect.bottom, 1f, -1f)
    mark(rect.right, rect.bottom, -1f, -1f)
}
