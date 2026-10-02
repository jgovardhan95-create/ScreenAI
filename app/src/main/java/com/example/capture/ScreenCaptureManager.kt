package com.example.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

sealed class CaptureOutcome {
    data class Success(val bitmap: Bitmap) : CaptureOutcome()
    data class PermissionRequired(val message: String) : CaptureOutcome()
    data class Failure(val message: String) : CaptureOutcome()
}

object ScreenCaptureManager {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var captureWidth: Int = 1080
    private var captureHeight: Int = 1920
    private var captureDensityDpi: Int = DisplayMetrics.DENSITY_HIGH

    // Optional fallback bitmap captured from the active in-app window (e.g., Test Screen AI sandbox)
    @Volatile
    private var inAppFallbackBitmap: Bitmap? = null

    @Volatile
    private var lastProjectionFrame: Bitmap? = null

    private val _hasProjectionPermission = MutableStateFlow(false)
    val hasProjectionPermission: StateFlow<Boolean> = _hasProjectionPermission.asStateFlow()

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            cleanupVirtualDisplay()
            mediaProjection = null
            _hasProjectionPermission.value = false
        }
    }

    fun setInAppFallbackBitmap(bitmap: Bitmap?) {
        inAppFallbackBitmap = bitmap
    }

    fun hasActiveProjection(): Boolean {
        return mediaProjection != null && virtualDisplay != null && imageReader != null
    }

    fun initializeMediaProjection(
        context: Context,
        resultCode: Int,
        resultData: Intent
    ): Boolean {
        return try {
            stopProjection()
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)

            // Keep capture resolution crisp yet memory-friendly
            val scale = if (metrics.widthPixels > 1080) 1080f / metrics.widthPixels.toFloat() else 1f
            captureWidth = ((metrics.widthPixels * scale).toInt().coerceAtLeast(360) / 2) * 2
            captureHeight = ((metrics.heightPixels * scale).toInt().coerceAtLeast(640) / 2) * 2
            captureDensityDpi = metrics.densityDpi

            val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = mpManager.getMediaProjection(resultCode, resultData)
                ?: return false

            // CRITICAL for Android 14+ (API 34+): Must register callback BEFORE createVirtualDisplay
            projection.registerCallback(projectionCallback, mainHandler)

            val reader = ImageReader.newInstance(
                captureWidth,
                captureHeight,
                PixelFormat.RGBA_8888,
                2
            )

            val vDisplay = projection.createVirtualDisplay(
                "ScreenAI_OnDemandCapture",
                captureWidth,
                captureHeight,
                captureDensityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                mainHandler
            )

            mediaProjection = projection
            imageReader = reader
            virtualDisplay = vDisplay
            _hasProjectionPermission.value = true
            true
        } catch (e: Exception) {
            stopProjection()
            false
        }
    }

    /**
     * Captures a single frame ONLY when the user explicitly triggers an AI mode.
     * Never continuously polls or uploads frames in the background.
     */
    suspend fun captureScreenOnDemand(): CaptureOutcome = withContext(Dispatchers.Default) {
        val reader = imageReader
        if (mediaProjection == null || virtualDisplay == null || reader == null) {
            val fallback = inAppFallbackBitmap
            if (fallback != null && !fallback.isRecycled) {
                return@withContext CaptureOutcome.Success(
                    fallback.copy(Bitmap.Config.ARGB_8888, false)
                )
            }
            return@withContext CaptureOutcome.PermissionRequired(
                "Screen capture permission is required to read your screen. Please grant Screen Capture access."
            )
        }

        // Retry up to 4 times across 350ms to allow ImageReader to have a fresh post-overlay-hide frame
        repeat(4) { attempt ->
            if (attempt > 0) {
                delay(90)
            }
            val image: Image? = try {
                reader.acquireLatestImage()
            } catch (e: Exception) {
                null
            }

            if (image != null) {
                try {
                    val bitmap = imageToBitmap(image)
                    if (bitmap != null) {
                        lastProjectionFrame = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                        return@withContext CaptureOutcome.Success(bitmap)
                    }
                } finally {
                    runCatching { image.close() }
                }
            }
        }

        val cachedProjection = lastProjectionFrame
        if (cachedProjection != null && !cachedProjection.isRecycled) {
            return@withContext CaptureOutcome.Success(
                cachedProjection.copy(Bitmap.Config.ARGB_8888, false)
            )
        }

        // If ImageReader had no new surface change since the last static frame, check inAppFallbackBitmap
        val fallback = inAppFallbackBitmap
        if (fallback != null && !fallback.isRecycled) {
            return@withContext CaptureOutcome.Success(
                fallback.copy(Bitmap.Config.ARGB_8888, false)
            )
        }

        CaptureOutcome.Failure(
            "Could not capture a screen frame. Try scrolling slightly or re-enabling Screen Capture permission."
        )
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        return try {
            val width = image.width
            val height = image.height
            val planes = image.planes
            if (planes.isEmpty()) return null

            val buffer = planes[0].buffer
            buffer.rewind()
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width

            val paddedWidth = width + rowPadding / pixelStride
            val fullBitmap = Bitmap.createBitmap(
                paddedWidth.coerceAtLeast(width),
                height,
                Bitmap.Config.ARGB_8888
            )
            fullBitmap.copyPixelsFromBuffer(buffer)

            if (rowPadding == 0) {
                fullBitmap
            } else {
                val cropped = Bitmap.createBitmap(fullBitmap, 0, 0, width, height)
                fullBitmap.recycle()
                cropped
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun cleanupVirtualDisplay() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
    }

    fun clearTemporaryMemory() {
        inAppFallbackBitmap = null
        lastProjectionFrame = null
    }

    fun stopProjection() {
        cleanupVirtualDisplay()
        lastProjectionFrame = null
        runCatching {
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
        }
        mediaProjection = null
        _hasProjectionPermission.value = false
    }
}
