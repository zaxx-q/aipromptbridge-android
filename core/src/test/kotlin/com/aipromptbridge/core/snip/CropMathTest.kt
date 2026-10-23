package com.aipromptbridge.core.snip

import kotlin.test.Test
import kotlin.test.assertEquals

class CropMathTest {
    @Test
    fun scalesViewSelectionToBitmapPixels() {
        // view 1000x2000 shows a 1080x2400 bitmap -> scale 1.08 x / 1.2 y
        val rect = CropMath.viewToBitmap(100f, 200f, 500f, 700f, 1000f, 2000f, 1080, 2400)
        assertEquals(PixelRect(108, 240, 540, 840), rect)
        assertEquals(432, rect.width)
        assertEquals(600, rect.height)
    }

    @Test
    fun dragDirectionDoesNotMatter() {
        val a = CropMath.viewToBitmap(100f, 200f, 500f, 700f, 1000f, 2000f, 1080, 2400)
        val b = CropMath.viewToBitmap(500f, 700f, 100f, 200f, 1000f, 2000f, 1080, 2400)
        assertEquals(a, b)
    }

    @Test
    fun clampsSelectionToTheBitmap() {
        val rect = CropMath.viewToBitmap(-50f, -50f, 5000f, 5000f, 1000f, 2000f, 1080, 2400)
        assertEquals(PixelRect(0, 0, 1080, 2400), rect)
    }

    @Test
    fun neverReturnsAnEmptyRect() {
        val rect = CropMath.viewToBitmap(300f, 300f, 300f, 300f, 1000f, 2000f, 1080, 2400)
        assertEquals(1, rect.width)
        assertEquals(1, rect.height)
    }
}
