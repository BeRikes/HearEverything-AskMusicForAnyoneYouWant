# AIMusicPlayer KMP Project — Design Spec

**Date:** 2026-06-03
**Status:** Approved

## Overview

Create a Kotlin Multiplatform project "AIMusicPlayer" with Compose Multiplatform UI, targeting Android and iOS.

## Technology Stack

| Component | Version | Notes |
|-----------|---------|-------|
| Kotlin | 2.1.20 | Meets 2.0+ requirement |
| Compose Multiplatform | 1.7.3 | Meets 1.6+ requirement |
| AGP | 8.7.3 | Compatible with Kotlin 2.1.x |
| Gradle | 8.11 | Latest 8.x |

## Module Structure

- **shared** — KMP module with `commonMain`, `androidMain`, `iosMain` source sets
- **androidApp** — Android application module consuming `shared`
- **iosApp** — iOS application (SwiftUI wrapper consuming `shared` framework)

## Dependencies

### commonMain
- org.jetbrains.kotlinx:kotlinx-coroutines-core
- org.jetbrains.kotlinx:kotlinx-datetime
- io.ktor:ktor-client-core
- io.ktor:ktor-client-content-negotiation
- io.ktor:ktor-serialization-kotlinx-json
- org.jetbrains.kotlinx:kotlinx-serialization-json
- co.touchlab:kermit

### androidMain
- io.ktor:ktor-client-okhttp

### iosMain
- io.ktor:ktor-client-darwin

## Package Structure (commonMain)

```
com.example.aimusicplayer/
├── App.kt              # Shared Compose UI
├── Platform.kt          # expect declarations
├── network/             # Ktor HTTP client
├── cache/               # Local caching
├── player/              # Music player logic
├── ai/                  # AI features
└── ui/                  # UI components & theme
```

## Entry Points

- **Android:** `MainActivity.kt` — standard ComponentActivity with setContent
- **iOS:** `ContentView.swift` — SwiftUI wrapper using `MainViewController`

## Initial UI

Display centered "Hello KMP Music Player 🎵" text on both platforms.

## Repository Configuration

Preserve Aliyun mirrors from existing project for fast dependency downloads in China.
