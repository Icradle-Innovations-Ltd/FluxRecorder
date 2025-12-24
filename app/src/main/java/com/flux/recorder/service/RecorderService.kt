package com.flux.recorder.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.flux.recorder.core.audio.AudioRecorder
import com.flux.recorder.core.codec.AudioEncoder
import com.flux.recorder.core.codec.MediaMuxerWrapper
import com.flux.recorder.core.codec.VideoEncoder
import com.flux.recorder.core.projection.ScreenCaptureManager
import com.flux.recorder.data.AudioSource
import com.flux.recorder.data.RecordingSettings
import com.flux.recorder.data.RecordingState
import com.flux.recorder.utils.FileManager
import com.flux.recorder.utils.NotificationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.coroutineContext
import java.io.File

/**
 * Foreground service that manages the screen recording process
 */
class RecorderService : Service() {
    
    private val binder = RecorderBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private lateinit var fileManager: FileManager
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var screenCaptureManager: ScreenCaptureManager
    
    private var videoEncoder: VideoEncoder? = null
    private var audioEncoder: AudioEncoder? = null
    private var audioRecorder: AudioRecorder? = null
    
    private var muxer: MediaMuxerWrapper? = null
    private var outputFile: File? = null
    private var recordingJob: Job? = null
    private var audioJob: Job? = null
    
    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()
    
    private var startTime: Long = 0
    private var pausedDuration: Long = 0
    private var pauseStartTime: Long = 0
    
    companion object {
        private const val TAG = "RecorderService"
        const val ACTION_START_RECORDING = "com.flux.recorder.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.flux.recorder.STOP_RECORDING"
        const val ACTION_PAUSE_RECORDING = "com.flux.recorder.PAUSE_RECORDING"
        const val ACTION_RESUME_RECORDING = "com.flux.recorder.RESUME_RECORDING"
        
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_SETTINGS = "settings"
    }
    
    override fun onCreate() {
        super.onCreate()
        fileManager = FileManager(this)
        notificationHelper = NotificationHelper(this)
        screenCaptureManager = ScreenCaptureManager(this)
        Log.d(TAG, "RecorderService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                val settings = intent.getParcelableExtra<RecordingSettings>(EXTRA_SETTINGS)
                
                if (resultData != null && settings != null) {
                    startRecording(resultCode, resultData, settings)
                }
            }
            ACTION_STOP_RECORDING -> stopRecording()
            ACTION_PAUSE_RECORDING -> pauseRecording()
            ACTION_RESUME_RECORDING -> resumeRecording()
        }
        
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    private fun startRecording(resultCode: Int, data: Intent, settings: RecordingSettings) {
        if (_recordingState.value !is RecordingState.Idle) {
            Log.w(TAG, "Recording already in progress")
            return
        }
        
        try {
            // Start foreground service
            val notification = notificationHelper.createRecordingNotification(
                "Recording",
                "Screen recording in progress..."
            )
            startForeground(NotificationHelper.NOTIFICATION_ID, notification)
            
            // Initialize MediaProjection
            if (!screenCaptureManager.initializeProjection(resultCode, data)) {
                _recordingState.value = RecordingState.Error("Failed to initialize screen capture")
                stopSelf()
                return
            }
            
            // Create output file
            outputFile = fileManager.createRecordingFile()
            
            // Calculate dimensions based on orientation
            var width = settings.videoQuality.width
            var height = settings.videoQuality.height
            
            val (screenWidth, screenHeight) = screenCaptureManager.getScreenDimensions()
            val isScreenPortrait = screenHeight > screenWidth
            val isSettingPortrait = height > width
            
            // Swap if orientations don't match
            if (isScreenPortrait && !isSettingPortrait) {
                // Phone is in Portrait, but settings are Landscape -> Swap to Portrait
                val temp = width
                width = height
                height = temp
            } else if (!isScreenPortrait && isSettingPortrait) {
                // Phone is in Landscape, but settings are Portrait -> Swap to Landscape
                val temp = width
                width = height
                height = temp
            }
            
            // Initialize encoder
            val bitrate = settings.calculateBitrate()
            videoEncoder = VideoEncoder(
                width,
                height,
                bitrate,
                settings.frameRate.fps
            )
            
            val surface = videoEncoder?.prepare()
            if (surface == null) {
                _recordingState.value = RecordingState.Error("Failed to initialize encoder")
                stopSelf()
                return
            }
            
            // Create virtual display
            val virtualDisplay = screenCaptureManager.createVirtualDisplay(
                surface,
                width,
                height,
                screenCaptureManager.getScreenDensity()
            )
            
            if (virtualDisplay == null) {
                _recordingState.value = RecordingState.Error("Failed to create virtual display")
                stopSelf()
                return
            }
            
            // Setup Audio Encoder & Recorder
            var audioEnabled = false
            Log.d(TAG, "Audio Source Setting: ${settings.audioSource}")
            
            if (settings.audioSource != AudioSource.NONE) {
                Log.d(TAG, "Initializing audio encoder and recorder...")
                audioEncoder = AudioEncoder() // Default settings
                audioEncoder?.prepare()
                
                audioRecorder = AudioRecorder()
                
                // Start audio recording with the specified source
                val success = audioRecorder?.start(
                    screenCaptureManager.getMediaProjection(), 
                    settings.audioSource
                ) ?: false
                
                if (success) {
                    audioEnabled = true
                    Log.d(TAG, "✅ Audio recording enabled: ${settings.audioSource}")
                } else {
                    Log.e(TAG, "❌ Failed to start audio recorder for source: ${settings.audioSource}")
                    audioEncoder?.release()
                    audioEncoder = null
                }
            } else {
                Log.w(TAG, "⚠️ Audio source is NONE - no audio will be recorded")
            }
            
            // Initialize muxer
            muxer = MediaMuxerWrapper(outputFile!!).apply {
                prepare()
            }
            
            // Start recording loop
            startTime = System.currentTimeMillis()
            _recordingState.value = RecordingState.Recording(0)
            
            recordingJob = serviceScope.launch {
                recordingLoop()
            }
            
            if (audioEnabled) {
                audioJob = serviceScope.launch(Dispatchers.IO) {
                    audioLoop()
                }
            }
            
            Log.d(TAG, "Recording started")
            
            // Start facecam if enabled
            if (settings.enableFacecam) {
                val facecamIntent = Intent(this, FloatingControlService::class.java)
                startService(facecamIntent)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            _recordingState.value = RecordingState.Error(e.message ?: "Unknown error")
            stopSelf()
        }
    }
    
    private suspend fun recordingLoop() {
        var videoTrackAdded = false
        
        while (coroutineContext.isActive && _recordingState.value is RecordingState.Recording) {
            try {
                // Get encoded video data
                val output = videoEncoder?.getEncodedData() ?: VideoEncoder.EncoderOutput.TryAgain
                
                when (output) {
                    is VideoEncoder.EncoderOutput.FormatChanged -> {
                        Log.d(TAG, "Encoder format changed")
                        val format = videoEncoder?.getOutputFormat()
                        if (format != null && !videoTrackAdded) {
                            muxer?.addVideoTrack(format)
                            videoTrackAdded = true
                            Log.d(TAG, "Video track added to muxer")
                        }
                    }
                    is VideoEncoder.EncoderOutput.Data -> {
                        val (buffer, bufferInfo, bufferIndex) = output
                        
                        if (videoTrackAdded && (bufferInfo.flags and android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            muxer?.writeVideoSample(buffer, bufferInfo)
                        }
                        
                        videoEncoder?.releaseOutputBuffer(bufferIndex)
                    }
                    is VideoEncoder.EncoderOutput.TryAgain -> {
                        // No data available yet, just continue
                    }
                }
                
                // Update duration
                val currentDuration = System.currentTimeMillis() - startTime - pausedDuration
                _recordingState.value = RecordingState.Recording(currentDuration)
                
                delay(10) // Small delay to prevent busy waiting
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in recording loop", e)
                break
            }
        }
    }
    
    private suspend fun audioLoop() {
        var audioTrackAdded = false
        val bufferSize = audioRecorder?.getBufferSize() ?: 4096
        val audioBuffer = ByteArray(bufferSize)
        var readCount = 0
        
        Log.d(TAG, "🎤 Starting audio loop with buffer size: $bufferSize")
        
        while (coroutineContext.isActive && _recordingState.value is RecordingState.Recording) {
            try {
                // 1. Read Audio
                val readResult = audioRecorder?.read(audioBuffer, bufferSize) ?: -1
                
                if (readResult > 0) {
                    readCount++
                    if (readCount % 100 == 0) {
                        Log.d(TAG, "🎤 Audio read count: $readCount, bytes: $readResult")
                    }
                    
                    val timestampUs = (System.currentTimeMillis() - startTime - pausedDuration) * 1000
                    
                    // 2. Encode Audio
                    audioEncoder?.encode(audioBuffer, readResult, timestampUs)
                    
                    // 3. Retrieve Encoded Data
                    var outputAvailable = true
                    while (outputAvailable) {
                        val output = audioEncoder?.getEncodedData() ?: AudioEncoder.Output.TryAgain
                        
                        when (output) {
                            is AudioEncoder.Output.Data -> {
                                if (output.buffer != null && output.info.size > 0) {
                                    if (audioTrackAdded) {
                                        muxer?.writeAudioSample(output.buffer, output.info)
                                    } else {
                                        Log.w(TAG, "⚠️ Audio data available but track not added yet")
                                    }
                                }
                                audioEncoder?.releaseOutputBuffer(output.index)
                            }
                            is AudioEncoder.Output.FormatChanged -> {
                                val format = audioEncoder?.getOutputFormat()
                                if (format != null && !audioTrackAdded) {
                                    muxer?.addAudioTrack(format)
                                    audioTrackAdded = true
                                    Log.d(TAG, "✅ Audio track added to muxer")
                                }
                            }
                            is AudioEncoder.Output.TryAgain -> {
                                outputAvailable = false
                            }
                        }
                    }
                } else {
                    if (readCount == 0 && readResult == -1) {
                        Log.e(TAG, "❌ Audio recorder returning -1 (no data)")
                    }
                    delay(5)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in audio loop", e)
                break
            }
        }
        
        Log.d(TAG, "🎤 Audio loop ended. Total reads: $readCount, Track added: $audioTrackAdded")
    }
    
    private fun pauseRecording() {
        val currentState = _recordingState.value
        if (currentState is RecordingState.Recording) {
            pauseStartTime = System.currentTimeMillis()
            _recordingState.value = RecordingState.Paused(currentState.durationMs)
            
            val notification = notificationHelper.createRecordingNotification(
                "Recording Paused",
                "Tap to resume",
                isRecording = false
            )
            notificationHelper.updateNotification(notification)
        }
    }
    
    private fun resumeRecording() {
        val currentState = _recordingState.value
        if (currentState is RecordingState.Paused) {
            pausedDuration += System.currentTimeMillis() - pauseStartTime
            _recordingState.value = RecordingState.Recording(currentState.durationMs)
            
            val notification = notificationHelper.createRecordingNotification(
                "Recording",
                "Screen recording in progress..."
            )
            notificationHelper.updateNotification(notification)
        }
    }
    
    private fun stopRecording() {
        Log.d(TAG, "Stopping recording")
        
        // Cancel recording jobs
        recordingJob?.cancel()
        recordingJob = null
        
        audioJob?.cancel()
        audioJob = null
        
        // Signal end of stream
        videoEncoder?.signalEndOfStream()
        
        // Small delay to ensure last frames are written
        Thread.sleep(100)
        
        // Release resources
        muxer?.release()
        muxer = null
        
        videoEncoder?.release()
        videoEncoder = null
        
        audioRecorder?.stop()
        audioRecorder = null
        
        audioEncoder?.release()
        audioEncoder = null
        
        screenCaptureManager.stop()
        
        // Make sure file is visible in gallery
        outputFile?.let { file ->
            // First, scan the original private file (just in case)
            android.media.MediaScannerConnection.scanFile(
                this,
                arrayOf(file.absolutePath),
                arrayOf("video/mp4"),
                null
            )
            
            // Now copy to public "Movies/FluxRecorder" directory
            val publicFile = fileManager.copyToPublicGallery(file)
            
            // If we got a public file (legacy storage), scan that too
            if (publicFile != null) {
                android.media.MediaScannerConnection.scanFile(
                    this,
                    arrayOf(publicFile.absolutePath),
                    arrayOf("video/mp4"),
                    null
                )
                Log.d(TAG, "Copied and scanned public file: ${publicFile.absolutePath}")
            } else {
                Log.d(TAG, "Copied to MediaStore (Scoped Storage)")
            }
            
            // Delete the private file to avoid duplicates
            try {
                if (file.exists()) {
                    val deleted = file.delete()
                    Log.d(TAG, "Deleted private original file: $deleted")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete private file", e)
            }
        }
        
        // Stop facecam if running
        // TODO: Implement actual floating control stop logic properly
        val facecamIntent = Intent(this, FloatingControlService::class.java)
        stopService(facecamIntent)
        
        // Update state
        _recordingState.value = RecordingState.Idle
        
        // Stop foreground and service
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        
        Log.d(TAG, "Recording stopped, file saved: ${outputFile?.absolutePath}")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopRecording()
        Log.d(TAG, "RecorderService destroyed")
    }
    
    inner class RecorderBinder : Binder() {
        fun getService(): RecorderService = this@RecorderService
    }
}
