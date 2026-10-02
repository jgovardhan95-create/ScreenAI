package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.AiMode
import com.example.data.ResponseLength
import com.example.imaging.ImageProcessor
import com.example.prompt.PromptGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app_name and tagline from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        val tagline = context.getString(R.string.app_tagline)
        assertEquals("ScreenAI Assistant", appName)
        assertEquals("Your AI assistant for anything on your screen.", tagline)
    }

    @Test
    fun `prompt generator builds mode-specific instructions`() {
        val answerPrompt = PromptGenerator.buildModePrompt(
            mode = AiMode.ANSWER,
            responseLength = ResponseLength.SHORT
        )
        assertTrue(answerPrompt.contains("ANSWER"))
        assertTrue(answerPrompt.contains("Output: 25"))

        val askPrompt = PromptGenerator.buildModePrompt(
            mode = AiMode.ASK_AI,
            customInstruction = "Find the error in this code",
            responseLength = ResponseLength.DETAILED
        )
        assertTrue(askPrompt.contains("Find the error in this code"))
    }

    @Test
    fun `image processor detects solid blank screen vs textured screen`() {
        val blankBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK)
        }
        assertTrue(ImageProcessor.isBitmapBlankOrSolid(blankBitmap))

        val patternedBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        for (y in 0 until 100) {
            for (x in 0 until 100) {
                patternedBitmap.setPixel(x, y, if (y < 50) Color.WHITE else Color.BLUE)
            }
        }
        assertFalse(ImageProcessor.isBitmapBlankOrSolid(patternedBitmap))
    }
}
