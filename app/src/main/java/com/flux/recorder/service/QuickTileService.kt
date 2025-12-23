package com.flux.recorder.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

/**
 * Quick Settings Tile for instant recording access
 */
class QuickTileService : TileService() {
    
    companion object {
        private const val TAG = "QuickTileService"
    }
    
    override fun onStartListening() {
        super.onStartListening()
        updateTile(false)
    }
    
    override fun onClick() {
        super.onClick()
        
        // TODO: Implement recording toggle
        // For now, just update the tile state
        val isRecording = qsTile.state == Tile.STATE_ACTIVE
        updateTile(!isRecording)
        
        Log.d(TAG, "Quick tile clicked, recording: ${!isRecording}")
    }
    
    private fun updateTile(isRecording: Boolean) {
        qsTile?.apply {
            state = if (isRecording) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = if (isRecording) "Stop Recording" else "Start Recording"
            updateTile()
        }
    }
}
