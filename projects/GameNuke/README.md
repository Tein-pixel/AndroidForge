# Game Nuke — Nuke Engine Omni Booster

Kotlin + Jetpack Compose, Android 10+ (API 29), target API 35.

Five tabs: Tweaks, Games, Wipe, Tasks, Console. Shizuku permission is required for shell operations. EncryptedSharedPreferences stores successful switch selections.

Safety/compatibility: thermal bypass, raw system-directory deletion, and root-only kernel writes are not executed. CPU/GPU/kernel, SurfaceFlinger, BBR, and refresh-rate support varies by ROM. Commands are logged with exit status; switches update only when the shell command reports success. Battery temperature is shown as battery temperature, not CPU temperature.

Build: AGP 8.7.3, Gradle 8.9, JDK 17, Kotlin 2.0.21, Compose BOM 2024.12.01.
