package com.aipromptbridge.feature.snip

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface CaptureEvent {
    data class Frame(val bitmap: Bitmap) : CaptureEvent
    data class Failed(val reason: String) : CaptureEvent
}

/**
 * In-process hand-off from [ScreenCaptureService] to [SnipActivity] (both live in the same process).
 * A StateFlow keeps the latest event so a briefly-recreated activity still receives it.
 * Bitmaps are large; never put them in an Intent.
 */
object CaptureBus {
    private val _events = MutableStateFlow<CaptureEvent?>(null)
    val events: StateFlow<CaptureEvent?> = _events

    fun emit(event: CaptureEvent) {
        _events.value = event
    }

    /** Call before starting a new capture so a stale frame from a previous session is not delivered. */
    fun reset() {
        _events.value = null
    }
}
