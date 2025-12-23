# Flux Recorder AI Coding Instructions

## Project Overview
Flux Recorder is a high-performance Android screen recorder targeting **API 24-36** using **Kotlin 2.0.21** and **Jetpack Compose**. The project implements system-level recording features like Quick Settings tiles, floating overlay controls, MediaCodec-based recording, and AMOLED-optimized UI.

**Current Status**: Core architecture implemented with Hilt DI, MediaProjection recording, and Compose UI. Package namespace is `com.flux.recorder`.

## Architecture & Package Structure

### Actual Implementation
```
com.flux.recorder/
├── FluxRecorderApplication.kt  # Hilt app entry point
├── MainActivity.kt              # Compose UI host, service binding
├── core/
│   ├── codec/                  # ✓ MediaCodec & MediaMuxer wrappers
│   │   ├── VideoEncoder.kt     # H.264/AVC hardware encoding
│   │   └── MediaMuxerWrapper.kt
│   └── projection/             # ✓ MediaProjection management
│       └── ScreenCaptureManager.kt
├── di/                         # ✓ Hilt dependency injection
│   └── AppModule.kt
├── service/                    # ✓ Foreground services
│   ├── RecorderService.kt      # Main recording engine
│   ├── FloatingControlService.kt
│   └── QuickTileService.kt
├── ui/
│   ├── screens/                # ✓ Compose screens
│   │   ├── HomeScreen.kt       # Permission & MediaProjection flow
│   │   ├── SettingsScreen.kt
│   │   └── RecordingsScreen.kt
│   └── theme/                  # ✓ Electric Flux theme implemented
│       ├── Color.kt            # ElectricViolet, FluxCyan, VoidBlack
│       ├── Theme.kt
│       └── Type.kt
├── data/                       # ✓ Data models
│   ├── RecordingSettings.kt    # Video quality, bitrate, FPS
│   ├── RecordingState.kt       # State flow for recording status
│   └── Recording.kt
└── utils/                      # ✓ Utilities
    ├── FileManager.kt          # Recording file management
    ├── NotificationHelper.kt   # Foreground notifications
    ├── PermissionManager.kt
    └── PreferencesManager.kt
```

## Build System & Dependencies

### Gradle Configuration
- **Build Tool**: Gradle 8.13.2 with Kotlin DSL
- **Version Catalog**: `gradle/libs.versions.toml` for centralized dependency management
- **JDK**: Java 11
- **Hilt**: Configured with `kapt` for DI code generation
- **CameraX**: For planned facecam feature
- **Accompanist Permissions**: For runtime permission handling

### Key Dependencies
```kotlin
// Already configured in libs.versions.toml:
- hilt-android: 2.50
- accompanist-permissions: 0.32.0
- kotlinx-coroutines: 1.7.3
```

## Android Recording Architecture

### MediaProjection Flow
1. User presses RECORD button in [HomeScreen.kt](app/src/main/java/com/flux/recorder/ui/screens/HomeScreen.kt)
2. Permission check: Audio, Camera (if facecam), Notifications (API 33+)
3. Launch MediaProjection intent → System shows "Start recording?" dialog
4. On user consent: Activity result returns to `mediaProjectionLauncher`
5. Callback fires `onStartRecording(resultCode, intent, settings)`
6. MainActivity starts [RecorderService.kt](app/src/main/java/com/flux/recorder/service/RecorderService.kt) as foreground service

### Recording Service Lifecycle
```kotlin
RecorderService.startRecording():
  1. Start foreground notification (required for API 26+)
  2. Initialize MediaProjection from result intent
  3. Create output file via FileManager
  4. Initialize VideoEncoder (MediaCodec with H.264/AVC)
  5. Create VirtualDisplay targeting encoder's input surface
  6. Initialize MediaMuxerWrapper for MP4 container
  7. Start coroutine-based recording loop
  8. Update RecordingState flow → UI reflects recording time
```

### Critical Android 14+ Requirements
- **Foreground service type**: Manifest MUST declare `foregroundServiceType="mediaProjection"`
- **Per-session consent**: MediaProjection permission dialog shown EVERY recording (cannot be bypassed)
- **Service binding**: MainActivity binds to RecorderService to observe `recordingState: StateFlow`

## UI Patterns

### "Electric Flux" Theme (Implemented)
```kotlin
// In Color.kt
ElectricViolet = Color(0xFF6200EE)   // Primary actions
FluxCyan = Color(0xFF03DAC6)          // Active states
VoidBlack = Color(0xFF121212)         // AMOLED background
TextPrimary = Color(0xFFFFFFFF)
TextSecondary = Color(0xFFB0B0B0)
RecordingRed = Color(0xFFFF5252)      // Recording indicator
```

### Compose Navigation
- State-based navigation in `FluxRecorderApp()` composable
- Screens: "home", "settings", "recordings"
- Settings persisted via [PreferencesManager.kt](app/src/main/java/com/flux/recorder/utils/PreferencesManager.kt)

## Development Workflow

### Building & Running
```bash
# Clean build
./gradlew clean assembleDebug

# Install on device
./gradlew installDebug

# View logs (filter for app)
adb logcat | grep -i "flux\|recorder"
```

### Testing Requirements
⚠️ **Physical Android device REQUIRED** - Emulators cannot use MediaProjection API for screen recording.

### Debugging Recording Issues
Common failure points:
1. **Service not starting**: Check AndroidManifest service declaration matches package
2. **Black recording**: VirtualDisplay not properly bound to encoder surface
3. **Crash on stop**: Ensure encoder.signalEndOfStream() before muxer.release()
4. **No audio**: Requires RECORD_AUDIO permission + AudioRecord implementation

## Critical Implementation Details

### Package Consistency
⚠️ **Recent fix**: AndroidManifest now uses fully qualified names:
```xml
<application android:name="com.flux.recorder.FluxRecorderApplication">
  <activity android:name="com.flux.recorder.MainActivity" />
  <service android:name="com.flux.recorder.service.RecorderService" />
```
Do NOT use relative paths (`.MainActivity`) - causes service binding failures.

### Permissions Handling
```kotlin
// In HomeScreen.kt
val multiplePermissionsState = rememberMultiplePermissionsState(
    permissions = buildList {
        add(android.Manifest.permission.RECORD_AUDIO)
        if (settings.enableFacecam) add(android.Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) add(POST_NOTIFICATIONS)
        // Storage permissions vary by API level
    }
)
```

### MediaCodec Encoder Setup
```kotlin
// In VideoEncoder.kt
MediaFormat.createVideoFormat(MIME_TYPE_H264, width, height)
  .setInteger(KEY_COLOR_FORMAT, COLOR_FormatSurface)  // Critical for VirtualDisplay
  .setInteger(KEY_BIT_RATE, bitrate)
  .setInteger(KEY_FRAME_RATE, frameRate)
  .setInteger(KEY_I_FRAME_INTERVAL, 1)  // I-frame every 1 second
```

### State Management
```kotlin
// RecorderService exposes StateFlow
val recordingState: StateFlow<RecordingState>

// UI collects state
recorderService?.recordingState?.collectAsState()?.value
```

## File Organization
- Kotlin source: `app/src/main/java/com/flux/recorder/`
- Resources: `app/src/main/res/`
- Manifest: `app/src/main/AndroidManifest.xml`
- Tests: `app/src/test/` (unit) and `app/src/androidTest/` (instrumented)

## Common Pitfalls
- **Don't** use relative package names in AndroidManifest
- **Don't** forget `foregroundServiceType="mediaProjection"` in service declaration
- **Don't** test MediaProjection in emulator
- **Always** start foreground notification BEFORE creating MediaProjection
- **Always** release encoder before muxer in stopRecording()
- **Check** target SDK compatibility when using Android APIs (currently SDK 35)

## Security
- **Snyk scanning**: Run `snyk_code_scan` on new code (see `.github/instructions/snyk_rules.instructions.md`)
- ProGuard rules in `proguard-rules.pro`
