package com.example.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Lightweight translucent Activity that requests Android's official MediaProjection
 * screen capture consent dialog when triggered from the floating overlay or home screen.
 */
class ScreenCaptureAcquireActivity : ComponentActivity() {

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            FloatingOverlayService.startWithProjection(
                context = applicationContext,
                resultCode = result.resultCode,
                resultData = result.data!!,
                notifyControllerAfterInit = true
            )
        } else {
            OverlayStateController.onPermissionAcquiredFromActivity(applicationContext, granted = false)
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (mpManager != null) {
            captureLauncher.launch(mpManager.createScreenCaptureIntent())
        } else {
            OverlayStateController.onPermissionAcquiredFromActivity(applicationContext, granted = false)
            finish()
        }
    }

    companion object {
        fun launch(context: Context) {
            val intent = Intent(context, ScreenCaptureAcquireActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            context.startActivity(intent)
        }
    }
}
