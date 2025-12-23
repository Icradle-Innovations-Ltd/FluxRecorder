package com.flux.recorder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import com.flux.recorder.data.RecordingSettings
import com.flux.recorder.data.RecordingState
import com.flux.recorder.service.RecorderService
import com.flux.recorder.ui.screens.HomeScreen
import com.flux.recorder.ui.screens.RecordingsScreen
import com.flux.recorder.ui.screens.SettingsScreen
import com.flux.recorder.ui.theme.FluxRecorderTheme
import com.flux.recorder.ui.theme.VoidBlack
import com.flux.recorder.utils.FileManager
import com.flux.recorder.utils.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var preferencesManager: PreferencesManager
    
    @Inject
    lateinit var fileManager: FileManager
    
    private var recorderService: RecorderService? = null
    private var serviceBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RecorderService.RecorderBinder
            recorderService = binder.getService()
            serviceBound = true
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            recorderService = null
            serviceBound = false
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            FluxRecorderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = VoidBlack
                ) {
                    FluxRecorderApp(
                        preferencesManager = preferencesManager,
                        fileManager = fileManager,
                        onStartRecording = { resultCode, data, settings ->
                            startRecordingService(resultCode, data, settings)
                        },
                        onStopRecording = {
                            stopRecordingService()
                        },
                        recordingState = recorderService?.recordingState?.collectAsState()?.value 
                            ?: RecordingState.Idle
                    )
                }
            }
        }
    }
    
    private fun startRecordingService(resultCode: Int, data: Intent, settings: RecordingSettings) {
        val intent = Intent(this, RecorderService::class.java).apply {
            action = RecorderService.ACTION_START_RECORDING
            putExtra(RecorderService.EXTRA_RESULT_CODE, resultCode)
            putExtra(RecorderService.EXTRA_RESULT_DATA, data)
            putExtra(RecorderService.EXTRA_SETTINGS, settings)
        }
        
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    private fun stopRecordingService() {
        val intent = Intent(this, RecorderService::class.java).apply {
            action = RecorderService.ACTION_STOP_RECORDING
        }
        startService(intent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }
}

@Composable
fun FluxRecorderApp(
    preferencesManager: PreferencesManager,
    fileManager: FileManager,
    onStartRecording: (Int, Intent, RecordingSettings) -> Unit,
    onStopRecording: () -> Unit,
    recordingState: RecordingState
) {
    var currentScreen by remember { mutableStateOf("home") }
    var settings by remember { mutableStateOf(preferencesManager.getRecordingSettings()) }
    var recordings by remember { mutableStateOf(fileManager.getAllRecordings()) }
    
    when (currentScreen) {
        "home" -> {
            HomeScreen(
                recordingState = recordingState,
                settings = settings,
                onStartRecording = { resultCode, data ->
                    onStartRecording(resultCode, data, settings)
                },
                onStopRecording = onStopRecording,
                onNavigateToSettings = { currentScreen = "settings" },
                onNavigateToRecordings = {
                    recordings = fileManager.getAllRecordings()
                    currentScreen = "recordings"
                }
            )
        }
        "settings" -> {
            SettingsScreen(
                settings = settings,
                onSettingsChanged = { newSettings ->
                    settings = newSettings
                    preferencesManager.saveRecordingSettings(newSettings)
                },
                onNavigateBack = { currentScreen = "home" }
            )
        }
        "recordings" -> {
            RecordingsScreen(
                recordings = recordings,
                onNavigateBack = { currentScreen = "home" },
                onDeleteRecording = { file ->
                    fileManager.deleteRecording(file)
                    recordings = fileManager.getAllRecordings()
                },
                onShareRecording = { file ->
                    // TODO: Implement share functionality
                }
            )
        }
    }
}