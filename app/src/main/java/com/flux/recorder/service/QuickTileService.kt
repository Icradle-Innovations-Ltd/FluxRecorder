package com.flux.recorder.service

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.flux.recorder.MainActivity

/**
 * Quick Settings Tile for instant recording access
 * Note: Due to MediaProjection restrictions, we can't start recording directly from here.
 * Instead, we launch MainActivity which handles the MediaProjection permission flow.
 */
class QuickTileService : TileService() {
    
    companion object {
        private const val TAG = "QuickTileService"
        const val ACTION_TOGGLE_RECORDING = "com.flux.recorder.TOGGLE_RECORDING"
    }
    
    override fun onStartListening() {
        super.onStartListening()
        // Always show as inactive since we can't track recording state here
        updateTile(false)
    }
    
    override fun onClick() {
        super.onClick()
        
        Log.d(TAG, "Quick tile clicked - launching MainActivity for recording")
        
        // Launch MainActivity with special action to start recording
        val intent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_TOGGLE_RECORDING
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        // Start activity for MediaProjection permission flow
        startActivityAndCollapse(intent)
    }
    
    private fun updateTile(isRecording: Boolean) {
        qsTile?.apply {
            state = if (isRecording) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = if (isRecording) "Stop Recording" else "Start Recording"
            updateTile()
        }
    }
}
