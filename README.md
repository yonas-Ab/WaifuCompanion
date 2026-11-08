# Sae AI Companion - Android Jetpack Compose Wrapper

High-performance native Android companion wrapper for Sae, the AI Waifu project running on localhost:8000 (or custom LAN/emulator IP).

## Features
- **Full-Screen WebView**: Hardware accelerated, automatic speech/audio playback without user gesture, custom WebChromeClient with auto-microphone granting.
- **Connection Configuration**: Dynamic Server IP and Port inputs, stored persistently in AndroidX DataStore with one-click Save & Reload.
- **Advanced Permissions**: Material 3 toggles for RECORD_AUDIO and SYSTEM_ALERT_WINDOW (Display over other apps).
- **Dark/Light Theme Shell**: Dynamic Material 3 theme switching.
- **Keep Screen On**: Window flag management to keep phone awake during interactive waifu sessions.
- **Web Console Log**: In-app live stream of console.log, console.warn, and console.error with source line tracking.
- **Floating Foreground Overlay Service**: Draggable, translucent WindowManager overlay with PixelFormat.TRANSLUCENT allowing Sae's transparent avatar to float over Android's home screen.

## How to Build in Android Studio
1. Open Android Studio (Ladybug, Koala, Jellyfish, or newer).
2. Select "Open" and choose this project folder.
3. Allow Gradle sync to complete (JDK 17 or 21).
4. Run on a physical device or emulator running Android 8.0+ (API 26+).

## Network Note for Localhost:
- When running on an **Android Emulator**, use `10.0.2.2` instead of `127.0.0.1` to access your computer's localhost.
- When running on a **Physical Phone**, use your computer's local Wi-Fi IP (e.g. `192.168.1.100:8000`).
