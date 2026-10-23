package com.aipromptbridge.feature.snip

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import com.aipromptbridge.R

/**
 * Captures ONE frame of the screen with MediaProjection and hands it to [SnipActivity] via [CaptureBus].
 *
 * Facts verified in the Android docs:
 *  - A foreground service of type `mediaProjection` is required (manifest: foregroundServiceType="mediaProjection";
 *    permission FOREGROUND_SERVICE_MEDIA_PROJECTION on API 34+), and the user must already have granted the capture request.
 *  - The projection token is one-time; register a MediaProjection.Callback so we clean up when the user/system stops it.
 *
 * STATUS: written from the documented API shape but NOT yet run on a device by the scaffold author.
 * See README Phase 7 for the verification checklist (blank frame? wrong size? consent prompt behaviour?).
 */
class ScreenCaptureService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var delivered = false
    private var released = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.let { readResultData(it) }
        if (resultCode != Activity.RESULT_OK || data == null) {
            fail("Screen capture permission was not granted.")
            return START_NOT_STICKY
        }

        // Give the consent dialog / quick settings shade a moment to animate away so they are not in the screenshot.
        handler.postDelayed({ startCapture(resultCode, data) }, CAPTURE_DELAY_MS)
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.capture_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_snip)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.capture_notification_text))
            .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    @Suppress("DEPRECATION")
    private fun readResultData(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        else intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

    private fun startCapture(resultCode: Int, data: Intent) {
        if (released) return
        try {
            val manager = getSystemService(MediaProjectionManager::class.java)
            val mediaProjection = manager.getMediaProjection(resultCode, data)
            projection = mediaProjection

            // The callback MUST be registered before createVirtualDisplay (Android 14+ requirement).
            mediaProjection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    handler.post {
                        if (!delivered) fail("Screen capture was stopped.")
                        release()
                    }
                }
            }, handler)

            val bounds = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            val width = bounds.width()
            val height = bounds.height()
            val density = resources.displayMetrics.densityDpi

            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader = reader
            reader.setOnImageAvailableListener({ onImageAvailable(it) }, handler)

            virtualDisplay = mediaProjection.createVirtualDisplay(
                "aipb-capture", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, handler,
            )
        } catch (e: Exception) {
            fail("Could not start screen capture: ${e.message}")
        }
    }

    private fun onImageAvailable(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        if (delivered) {
            image.close()
            return
        }
        delivered = true
        val bitmap = try {
            image.toBitmap()
        } finally {
            image.close()
        }
        CaptureBus.emit(CaptureEvent.Frame(bitmap))
        release()
    }

    /** Copies the RGBA plane into a Bitmap, removing the row padding the ImageReader may add. */
    private fun Image.toBitmap(): Bitmap {
        val plane = planes[0]
        val pixelStride = plane.pixelStride
        val rowPadding = plane.rowStride - pixelStride * width
        val padded = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(plane.buffer)
        if (rowPadding == 0) return padded
        val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
        padded.recycle()
        return cropped
    }

    private fun fail(reason: String) {
        CaptureBus.emit(CaptureEvent.Failed(reason))
        release()
    }

    private fun release() {
        if (released) return
        released = true
        virtualDisplay?.release()
        imageReader?.close()
        projection?.stop()
        virtualDisplay = null
        imageReader = null
        projection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "capture"
        private const val NOTIFICATION_ID = 1001
        private const val CAPTURE_DELAY_MS = 350L
    }
}
