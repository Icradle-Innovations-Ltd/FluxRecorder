# ⚡ Flux Recorder

> **Capture the flow.** A native, high-performance Android screen recorder designed to feel like a premium system utility.

![Kotlin](https://img.shields.io/badge/Kotlin-1.9.20-7F52FF.svg?logo=kotlin&logoColor=white) ![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg?logo=android&logoColor=white) ![Min SDK](https://img.shields.io/badge/Min_SDK-24-green.svg) ![Target SDK](https://img.shields.io/badge/Target_SDK-34-orange.svg) ![License](https://img.shields.io/badge/License-MIT-blue.svg)

<p align="center">
  <img src="docs/screenshots/home_mockup.png" width="200" alt="Home Screen" />
  <img src="docs/screenshots/overlay_mockup.png" width="200" alt="Floating Controls" />
  <img src="docs/screenshots/tile_mockup.png" width="200" alt="Quick Tile" />
</p>

## 📖 About
**Flux Recorder** is an open-source alternative to OEM system recorders (like Samsung's or Pixel's). It prioritizes **performance and integration** over gimmicks. 

Built entirely in **Native Kotlin** using **Jetpack Compose**, it utilizes the low-level `MediaCodec` API to ensure zero-lag recording during high-fidelity gaming. The UI features a distinct "Electric Flux" dark theme optimized for AMOLED screens.

## ✨ Key Features

### 🎮 Native Integration (Samsung-Style)
* **Quick Settings Tile:** Launch the recorder immediately from your notification shade (`TileService`). No need to open the app drawer.
* **Floating Control Bubble:** A non-intrusive, transparent overlay (`WindowManager`) that floats over games to Pause, Stop, or Draw.
* **Shake-to-Stop:** Uses the accelerometer to end recording instantly without pulling down the status bar.

### ⚡ "Flux" Performance Engine
* **Zero-Lag Encoding:** Hardware-accelerated H.264/AVC encoding.
* **High Fidelity:** Supports **1080p, 2K (1440p), and 4K** at **60/90 FPS**.
* **Smart Bitrate:** Auto-calculates optimal bitrate based on screen resolution.

### 🎨 Creative & Audio
* **Pro Audio Mixing:** Record **Internal Audio** (Android 10+) and **Microphone** simultaneously.
* **Facecam (PiP):** Resizable, floating circle overlay using `CameraX`.
* **Live Annotations:** "Draw on Screen" mode to highlight areas during tutorials.

---

## 🎨 UI & Design System

Flux Recorder uses a custom **"Electric Flux"** theme designed for clarity and battery saving.

| Element | Color | Hex | Usage |
| :--- | :--- | :--- | :--- |
| **Primary** | **Electric Violet** | `#6200EE` | Main Actions, Toggles |
| **Secondary** | **Flux Cyan** | `#03DAC6` | Active States, Accents |
| **Background** | **Void Black** | `#121212` | True Dark (AMOLED Save) |
| **Overlay** | **Glass Black** | `#CC000000` | 80% Opacity Floating Bar |

---

## 🛠 Tech Stack

| Component | Technology | Reasoning |
| :--- | :--- | :--- |
| **Language** | Kotlin | Modern, null-safe native development. |
| **UI** | Jetpack Compose | Material 3 declarative UI. |
| **Architecture** | MVVM + Hilt | Clean architecture with Dependency Injection. |
| **Video Core** | `MediaCodec` + `MediaMuxer` | Low-level access for max performance. |
| **Screen Capture** | `MediaProjection` | Standard Android projection API. |
| **System** | `TileService` | For Quick Settings integration. |
| **Overlay** | `SYSTEM_ALERT_WINDOW` | For drawing over other apps. |

---

## 🚀 Getting Started

### Prerequisites
* Android Studio (Iguana/Koala or newer)
* JDK 17
* Physical Android Device (Emulators cannot record screen effectively).

### Installation
1.  Clone the repo:
    ```bash
    git clone [https://github.com/your-username/flux-recorder.git](https://github.com/your-username/flux-recorder.git)
    ```
2.  Open in Android Studio.
3.  Sync Gradle.
4.  Run on device.

### ⚠️ Android 14 Compatibility Note
Google introduced strict rules for `MediaProjection` in Android 14 (API 34).
1.  **Per-Session Consent:** The system permission dialog will appear **every time** you start a recording. This is mandatory OS behavior and cannot be bypassed.
2.  **Foreground Service:** The app must declare `foregroundServiceType="mediaProjection"` in the Manifest.

---

## 📂 Project Structure

```text
com.flux.recorder
├── core
│   ├── codec           # MediaCodec & MediaMuxer wrapper
│   └── audio           # AudioRecord & Mixing logic
├── di                  # Hilt Modules
├── service             # The heavy lifters
│   ├── RecorderService.kt        # Foreground Service keeping app alive
│   ├── FloatingControlService.kt # The "Bubble" UI logic
│   └── QuickTileService.kt       # Notification Shade integration
├── ui
│   ├── theme           # Electric Flux Color Palette
│   └── screens         # Jetpack Compose Screens
└── utils               # Permissions & File Management