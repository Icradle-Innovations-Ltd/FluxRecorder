package com.flux.recorder.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.flux.recorder.core.camera.CameraOverlay

/**
 * Service for floating control overlay
 * TODO: Implement WindowManager overlay with pause/stop/draw controls
 */
class FloatingControlService : Service() {
    
    private var cameraOverlay: CameraOverlay? = null
    
    companion object {
        private const val TAG = "FloatingControlService"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FloatingControlService created")
        cameraOverlay = CameraOverlay(this)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "FloatingControlService started")
        cameraOverlay?.show()
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "FloatingControlService destroyed")
        cameraOverlay?.stop()
        cameraOverlay = null
    }
}
