package com.example.imaging

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max

sealed class ProcessedScreenImage {
    data class Success(
        val base64Jpeg: String,
        val width: Int,
        val height: Int,
        val previewBitmap: Bitmap
    ) : ProcessedScreenImage()

    data class Error(val reason: String, val isBlankScreen: Boolean = false) : ProcessedScreenImage()
}

object ImageProcessor {

    private const val MAX_DIMENSION = 1280
    private const val JPEG_QUALITY = 84

    suspend fun processCapturedBitmap(rawBitmap: Bitmap?): ProcessedScreenImage =
        withContext(Dispatchers.Default) {
            if (rawBitmap == null || rawBitmap.isRecycled || rawBitmap.width <= 10 || rawBitmap.height <= 10) {
                return@withContext ProcessedScreenImage.Error(
                    reason = "Screenshot capture failed or returned an empty frame. Please try again.",
                    isBlankScreen = false
                )
            }

            if (isBitmapBlankOrSolid(rawBitmap)) {
                return@withContext ProcessedScreenImage.Error(
                    reason = "The captured screen appears completely blank or is protected by Android's FLAG_SECURE privacy flag.",
                    isBlankScreen = true
                )
            }

            val scaledBitmap = scaleBitmapIfNeeded(rawBitmap, MAX_DIMENSION)
            val outputStream = ByteArrayOutputStream()
            val compressed = scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            if (!compressed) {
                return@withContext ProcessedScreenImage.Error(
                    reason = "Failed to encode captured screen image for AI processing."
                )
            }

            val bytes = outputStream.toByteArray()
            if (bytes.isEmpty()) {
                return@withContext ProcessedScreenImage.Error(
                    reason = "Encoded screenshot was empty."
                )
            }

            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            ProcessedScreenImage.Success(
                base64Jpeg = base64,
                width = scaledBitmap.width,
                height = scaledBitmap.height,
                previewBitmap = scaledBitmap
            )
        }

    /**
     * Samples a 10x10 grid across the inner 80% of the bitmap to detect if the screenshot
     * is completely black/blank (such as DRM or FLAG_SECURE windows).
     */
    fun isBitmapBlankOrSolid(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 20 || h < 20) return true

        val startX = (w * 0.1f).toInt()
        val endX = (w * 0.9f).toInt()
        val startY = (h * 0.1f).toInt()
        val endY = (h * 0.9f).toInt()

        val stepX = max(1, (endX - startX) / 11)
        val stepY = max(1, (endY - startY) / 13)

        val firstPixel = bitmap.getPixel(startX, startY)
        val firstR = Color.red(firstPixel)
        val firstG = Color.green(firstPixel)
        val firstB = Color.blue(firstPixel)

        var totalDiff = 0L
        var samples = 0

        var y = startY
        while (y < endY) {
            var x = startX
            while (x < endX) {
                val p = bitmap.getPixel(x, y)
                val diff = abs(Color.red(p) - firstR) +
                    abs(Color.green(p) - firstG) +
                    abs(Color.blue(p) - firstB)
                totalDiff += diff
                samples++
                x += stepX
            }
            y += stepY
        }

        if (samples == 0) return true
        val averageVariance = totalDiff.toFloat() / samples.toFloat()
        return averageVariance < 1.5f
    }

    private fun scaleBitmapIfNeeded(source: Bitmap, maxDimension: Int): Bitmap {
        val width = source.width
        val height = source.height
        val longestSide = max(width, height)
        if (longestSide <= maxDimension) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }
        val ratio = maxDimension.toFloat() / longestSide.toFloat()
        val targetWidth = (width * ratio).toInt().coerceAtLeast(1)
        val targetHeight = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    /**
     * Privacy rule: Screenshots are never saved permanently unless the user explicitly taps Save.
     */
    suspend fun saveScreenshotExplicitly(context: Context, bitmap: Bitmap): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val filename = "ScreenAI_${System.currentTimeMillis()}.jpg"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + "/ScreenAI"
                        )
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: throw IllegalStateException("Could not create MediaStore entry")
                    resolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    "Saved to Pictures/ScreenAI/$filename"
                } else {
                    val dir = File(context.cacheDir, "screen_captures").apply { mkdirs() }
                    val file = File(dir, filename)
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    "Saved to ${file.name}"
                }
            }
        }
}
