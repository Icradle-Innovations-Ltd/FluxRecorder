package com.flux.recorder.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.flux.recorder.core.codec.MediaMuxerWrapper
import com.flux.recorder.core.codec.VideoEncoder
import com.flux.recorder.core.projection.ScreenCaptureManager
import com.flux.recorder.data.RecordingSettings
import com.flux.recorder.data.RecordingState
import com.flux.recorder.utils.FileManager
import com.flux.recorder.utils.NotificationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private var muxer: MediaMuxerWrapper? = null
    private var outputFile: File? = null
    private var recordingJob: Job? = null
    
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
            
            // Initialize encoder
            val bitrate = settings.calculateBitrate()
            videoEncoder = VideoEncoder(
                settings.videoQuality.width,
                settings.videoQuality.height,
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
                settings.videoQuality.width,
                settings.videoQuality.height,
                screenCaptureManager.getScreenDensity()
            )
            
            if (virtualDisplay == null) {
                _recordingState.value = RecordingState.Error("Failed to create virtual display")
                stopSelf()
                return
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
            
            Log.d(TAG, "Recording started")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            _recordingState.value = RecordingState.Error(e.message ?: "Unknown error")
            stopSelf()
        }
    }
    
    private suspend fun recordingLoop() {
        var videoTrackAdded = false
        
        while (currentCoroutineContext().isActive && _recordingState.value is RecordingState.Recording) {
            try {
                // Get encoded video data
                val encodedData = videoEncoder?.getEncodedData()
                
                if (encodedData != null) {
                    val (buffer, bufferInfo, bufferIndex) = encodedData
                    
                    if (buffer != null && bufferInfo.size > 0) {
                        // Add video track on first frame
                        if (!videoTrackAdded && (bufferInfo.flags and android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            val format = videoEncoder?.getOutputFormat()
                            if (format != null) {
                                muxer?.addVideoTrack(format)
                                videoTrackAdded = true
                            }
                        }
                        
                        // Write video sample
                        if (videoTrackAdded && (bufferInfo.flags and android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            muxer?.writeVideoSample(buffer, bufferInfo)
                        }
                    }
                    
                    videoEncoder?.releaseOutputBuffer(bufferIndex)
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
        
        // Cancel recording job
        recordingJob?.cancel()
        recordingJob = null
        
        // Signal end of stream
        videoEncoder?.signalEndOfStream()
        
        // Small delay to ensure last frames are written
        Thread.sleep(100)
        
        // Release resources
        muxer?.release()
        muxer = null
        
        videoEncoder?.release()
        videoEncoder = null
        
        screenCaptureManager.stop()
        
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
