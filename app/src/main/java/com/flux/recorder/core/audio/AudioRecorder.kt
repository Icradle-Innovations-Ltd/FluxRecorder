package com.flux.recorder.core.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.MediaRecorder
import android.os.Build
import android.util.Log

/**
 * Handles Audio capture (Mic or System Internal)
 */
class AudioRecorder {
    
    private var audioRecord: AudioRecord? = null
    private var bufferSize = 0
    private var isRecording = false
    
    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
    
    /**
     * Start audio recording
     * @param mediaProjection Required for System Audio (Internal)
     * @param useMic If true, records microphone. If false, records System Audio (requires mediaProjection)
     */
    @SuppressLint("MissingPermission")
    fun start(mediaProjection: MediaProjection?, useMic: Boolean): Boolean {
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2
        
        try {
            if (!useMic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaProjection != null) {
                // System Audio Capture (Internal)
                val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()
                
                audioRecord = AudioRecord.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AUDIO_FORMAT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG)
                            .build()
                    )
                    .setAudioPlaybackCaptureConfig(config)
                    .setBufferSizeInBytes(bufferSize)
                    .build()
                    
                Log.d(TAG, "Initialized System Audio Recorder")
                
            } else {
                // Microphone Capture
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
                Log.d(TAG, "Initialized Microphone Recorder")
            }
            
            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord?.startRecording()
                isRecording = true
                return true
            } else {
                Log.e(TAG, "AudioRecord not initialized")
                return false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecorder", e)
            return false
        }
    }
    
    /**
     * Read audio data into buffer
     */
    fun read(buffer: ByteArray, size: Int): Int {
        if (!isRecording) return -1
        return audioRecord?.read(buffer, 0, size) ?: -1
    }
    
    fun getBufferSize(): Int = bufferSize
    
    fun stop() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            Log.d(TAG, "AudioRecorder stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecorder", e)
        }
    }
}
