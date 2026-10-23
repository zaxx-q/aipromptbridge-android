package com.aipromptbridge.core.snip

/** Integer pixel rectangle (right/bottom exclusive). Android-free so the maths is unit-testable. */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** Converts a selection drawn on screen (view coordinates) into a crop rectangle in the captured bitmap's pixels. */
object CropMath {
    /**
     * @param selection any two opposite corners in view pixels (order does not matter, may extend outside the view)
     * @return a rectangle clamped to the bitmap, at least 1x1 px
     */
    fun viewToBitmap(
        selectionLeft: Float, selectionTop: Float, selectionRight: Float, selectionBottom: Float,
        viewWidth: Float, viewHeight: Float,
        bitmapWidth: Int, bitmapHeight: Int,
    ): PixelRect {
        require(viewWidth > 0f && viewHeight > 0f) { "view size must be positive" }
        val scaleX = bitmapWidth / viewWidth
        val scaleY = bitmapHeight / viewHeight

        val l = minOf(selectionLeft, selectionRight).coerceIn(0f, viewWidth)
        val r = maxOf(selectionLeft, selectionRight).coerceIn(0f, viewWidth)
        val t = minOf(selectionTop, selectionBottom).coerceIn(0f, viewHeight)
        val b = maxOf(selectionTop, selectionBottom).coerceIn(0f, viewHeight)

        val left = (l * scaleX).toInt().coerceIn(0, bitmapWidth - 1)
        val top = (t * scaleY).toInt().coerceIn(0, bitmapHeight - 1)
        val right = (r * scaleX).toInt().coerceIn(left + 1, bitmapWidth)
        val bottom = (b * scaleY).toInt().coerceIn(top + 1, bitmapHeight)
        return PixelRect(left, top, right, bottom)
    }
}
