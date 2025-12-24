package com.flux.recorder.core.codec

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * Combines video and audio tracks into MP4 container
 */
class MediaMuxerWrapper(private val outputFile: File) {
    
    private var mediaMuxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var isMuxerStarted = false
    private var videoFormatReceived = false
    private var audioFormatReceived = false
    
    companion object {
        private const val TAG = "MediaMuxerWrapper"
    }
    
    /**
     * Initialize the muxer
     */
    fun prepare() {
        try {
            mediaMuxer = MediaMuxer(
                outputFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            Log.d(TAG, "MediaMuxer initialized: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaMuxer", e)
            throw e
        }
    }
    
    /**
     * Add video track
     */
    fun addVideoTrack(format: MediaFormat): Int {
        val muxer = mediaMuxer ?: throw IllegalStateException("Muxer not initialized")
        
        videoTrackIndex = muxer.addTrack(format)
        videoFormatReceived = true
        Log.d(TAG, "Video track added: $videoTrackIndex")
        
        tryStartMuxer()
        return videoTrackIndex
    }
    
    /**
     * Add audio track
     */
    fun addAudioTrack(format: MediaFormat): Int {
        val muxer = mediaMuxer ?: throw IllegalStateException("Muxer not initialized")
        
        audioTrackIndex = muxer.addTrack(format)
        audioFormatReceived = true
        Log.d(TAG, "Audio track added: $audioTrackIndex")
        
        tryStartMuxer()
        return audioTrackIndex
    }
    
    private var expectAudio = false

    /**
     * Set if audio track is expected
     */
    fun setAudioExpected(expected: Boolean) {
        expectAudio = expected
    }
    
    /**
     * Start muxer when all tracks are added
     */
    private fun tryStartMuxer() {
        if (!isMuxerStarted && videoFormatReceived && (!expectAudio || audioFormatReceived)) {
            // Start muxer when video track is ready (audio is optional)
            mediaMuxer?.start()
            isMuxerStarted = true
            Log.d(TAG, "MediaMuxer started")
        }
    }
    
    /**
     * Write video sample
     */
    fun writeVideoSample(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (!isMuxerStarted) {
            Log.w(TAG, "Muxer not started, dropping video sample")
            return
        }
        
        if (videoTrackIndex < 0) {
            Log.w(TAG, "Video track not added, dropping sample")
            return
        }
        
        try {
            mediaMuxer?.writeSampleData(videoTrackIndex, buffer, bufferInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing video sample", e)
        }
    }
    
    /**
     * Write audio sample
     */
    fun writeAudioSample(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (!isMuxerStarted) {
            Log.w(TAG, "Muxer not started, dropping audio sample")
            return
        }
        
        if (audioTrackIndex < 0) {
            Log.w(TAG, "Audio track not added, dropping sample")
            return
        }
        
        try {
            mediaMuxer?.writeSampleData(audioTrackIndex, buffer, bufferInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing audio sample", e)
        }
    }
    
    /**
     * Stop and release muxer
     */
    fun release() {
        try {
            if (isMuxerStarted) {
                mediaMuxer?.stop()
                isMuxerStarted = false
            }
            
            mediaMuxer?.release()
            mediaMuxer = null
            
            Log.d(TAG, "MediaMuxer released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaMuxer", e)
        }
    }
    
    /**
     * Check if muxer is started
     */
    fun isStarted(): Boolean {
        return isMuxerStarted
    }
}
