# Flux Recorder AI Coding Instructions

## Project Overview
Flux Recorder is a high-performance Android screen recorder targeting **API 24-36** using **Kotlin 2.0.21** and **Jetpack Compose**. The project aims to be a system-level recorder alternative with features like Quick Settings tiles, floating overlay controls, MediaCodec-based recording, and AMOLED-optimized UI.

**Current Status**: Early scaffolding phase - README describes target architecture but implementation is minimal (basic Compose app with default theme).

## Architecture & Package Structure

### Package Naming Discrepancy
⚠️ **Critical**: README references `com.flux.recorder` but actual namespace is `com.icradleinnovations.myapplication`. When creating new classes:
- Use existing namespace: `com.icradleinnovations.myapplication`
- Organize under planned subdirectories: `core/`, `di/`, `service/`, `ui/`, `utils/`

### Planned Architecture (from README)
```
com.icradleinnovations.myapplication/
├── core/codec/          # MediaCodec & MediaMuxer wrappers (not implemented)
├── core/audio/          # AudioRecord mixing (not implemented)
├── di/                  # Hilt modules (dependency not added)
├── service/             # Foreground services (not implemented)
│   ├── RecorderService.kt
│   ├── FloatingControlService.kt
│   └── QuickTileService.kt
├── ui/theme/            # ✓ EXISTS: Basic Material3 theme
└── utils/               # Permissions & file management (not implemented)
```

## Build System & Dependencies

### Gradle Setup
- **Build Tool**: Gradle 8.13.2 with Kotlin DSL
- **Version Catalog**: `gradle/libs.versions.toml` uses centralized dependency management
- **JDK**: Java 11 (not JDK 17 as README states)

### Missing Dependencies
README mentions these but they're **not configured**:
- **Hilt**: For dependency injection (add `com.google.dagger:hilt-android`)
- **CameraX**: For facecam feature
- **MediaProjection permissions**: Not in AndroidManifest

When adding dependencies, use the version catalog pattern:
```kotlin
// In libs.versions.toml
[versions]
hilt = "2.48"

[libraries]
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }

// In app/build.gradle.kts
implementation(libs.hilt.android)
```

## UI & Design System

### "Electric Flux" Theme (Not Implemented)
Current theme uses default Material3 colors (Purple80/Purple40). README specifies:
- Primary: `#6200EE` (Electric Violet)
- Secondary: `#03DAC6` (Flux Cyan)  
- Background: `#121212` (Void Black - AMOLED optimized)
- Overlay: `#CC000000` (Glass Black, 80% opacity)

**Action Item**: Update `Color.kt` and `Theme.kt` to match Electric Flux palette.

### Compose Patterns
- Uses `enableEdgeToEdge()` for modern edge-to-edge layouts
- Material3 with `Scaffold` for consistent structure
- Preview annotations for Compose tooling

## Android-Specific Considerations

### Permissions & Manifest
Current manifest is minimal. For planned features, you'll need:
```xml
<!-- Screen Recording -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"/>

<!-- Audio Recording -->
<uses-permission android:name="android.permission.RECORD_AUDIO"/>

<!-- Overlay Controls -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>

<!-- File Storage -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
```

### Android 14 (API 34) Breaking Changes
- **MediaProjection** requires per-session user consent (cannot be bypassed)
- Foreground services need explicit type: `foregroundServiceType="mediaProjection"`
- Reference: AndroidManifest needs updating before implementing recording

## Development Workflow

### Building & Running
```bash
# Sync dependencies
./gradlew build

# Install debug APK
./gradlew installDebug

# Run tests
./gradlew test
./gradlew connectedAndroidTest
```

### Testing Requirements
⚠️ **Physical device required** - Emulators cannot use MediaProjection API for screen recording.

### Security & Code Quality
- **Snyk scanning**: Run security scans on new code (see `.github/instructions/snyk_rules.instructions.md`)
- ProGuard rules in `proguard-rules.pro` (currently using defaults)

## Critical Implementation Gaps

Before implementing features from README:
1. **Add Hilt** for DI architecture
2. **Update theme** to Electric Flux colors
3. **Request permissions** in runtime (Android 6.0+ requirement)
4. **Configure foreground services** in manifest
5. **Create MediaCodec wrapper** before attempting recording

## File Organization
- Kotlin source: `app/src/main/java/com/icradleinnovations/myapplication/`
- Resources: `app/src/main/res/`
- Tests: `app/src/test/` (unit) and `app/src/androidTest/` (instrumented)
- Build artifacts: `app/build/` (ignored in git)

## Common Pitfalls
- **Don't** assume Hilt is configured (it's not)
- **Don't** use `com.flux.recorder` package name (outdated)
- **Don't** test MediaProjection in emulator
- **Always** check target SDK when using Android APIs (currently SDK 36)
