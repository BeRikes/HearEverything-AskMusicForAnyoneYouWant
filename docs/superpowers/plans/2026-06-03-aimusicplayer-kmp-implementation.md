# AIMusicPlayer KMP Project — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Scaffold a complete Kotlin Multiplatform project with Compose Multiplatform UI targeting Android and iOS, replacing the existing Android-only HearEverything project.

**Architecture:** Standard KMP structure — `shared` module holds commonMain/androidMain/iosMain with Compose UI; `androidApp` is a thin Android shell; `iosApp` is a SwiftUI wrapper that hosts the Compose view via UIViewController. Version catalog (`libs.versions.toml`) manages all dependency versions. Aliyun mirrors retained for China-based downloads.

**Tech Stack:** Kotlin 2.1.20, Compose Multiplatform 1.7.3, AGP 8.7.3, Ktor 3.0.3, kotlinx-coroutines 1.9.0, kotlinx-serialization 1.7.3, kotlinx-datetime 0.6.1, kermit 2.0.5, Gradle 8.11

---

## File Structure Map

```
AIMusicPlayer/
├── build.gradle.kts                    # [MODIFY] Root: apply plugins false
├── settings.gradle.kts                 # [MODIFY] Module includes + repos + project name
├── gradle.properties                   # [MODIFY] Add KMP/Android properties
├── gradle/
│   └── libs.versions.toml              # [CREATE] All dependency versions
├── shared/
│   ├── build.gradle.kts                # [CREATE] KMP + Compose plugin config
│   └── src/
│       ├── commonMain/kotlin/com/example/aimusicplayer/
│       │   ├── Platform.kt             # [CREATE] expect class Platform
│       │   ├── App.kt                  # [CREATE] @Composable fun App()
│       │   ├── network/.gitkeep        # [CREATE] Package placeholder
│       │   ├── cache/.gitkeep          # [CREATE] Package placeholder
│       │   ├── player/.gitkeep         # [CREATE] Package placeholder
│       │   ├── ai/.gitkeep             # [CREATE] Package placeholder
│       │   └── ui/.gitkeep             # [CREATE] Package placeholder
│       ├── androidMain/kotlin/com/example/aimusicplayer/
│       │   └── Platform.android.kt     # [CREATE] actual class Platform
│       └── iosMain/kotlin/com/example/aimusicplayer/
│           ├── Platform.ios.kt         # [CREATE] actual class Platform
│           └── MainViewController.kt   # [CREATE] ComposeUIViewController factory
├── androidApp/
│   ├── build.gradle.kts                # [CREATE] Android app consuming shared
│   └── src/main/
│       ├── AndroidManifest.xml          # [CREATE] Manifest with MainActivity
│       └── kotlin/com/example/aimusicplayer/
│           └── MainActivity.kt         # [CREATE] ComponentActivity entry
└── iosApp/
    └── iosApp/
        ├── iOSApp.swift                # [CREATE] @main SwiftUI App
        └── ContentView.swift           # [CREATE] UIViewControllerRepresentable
```

---

### Task 1: Write version catalog

**Files:**
- Create: `gradle/libs.versions.toml`

- [ ] **Step 1: Write libs.versions.toml**

```toml
[versions]
agp = "8.7.3"
kotlin = "2.1.20"
compose-multiplatform = "1.7.3"
kotlinx-coroutines = "1.9.0"
kotlinx-datetime = "0.6.1"
kotlinx-serialization = "1.7.3"
ktor = "3.0.3"
kermit = "2.0.5"
androidx-activityCompose = "1.9.3"
androidx-core = "1.15.0"

[libraries]
# Kotlinx
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinx-datetime" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }

# Ktor
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }

# Logging
kermit = { module = "co.touchlab:kermit", version.ref = "kermit" }

# AndroidX
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "androidx-activityCompose" }
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "androidx-core" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

---

### Task 2: Rewrite root build files

**Files:**
- Modify: `settings.gradle.kts` (full rewrite)
- Modify: `build.gradle.kts` (full rewrite)
- Modify: `gradle.properties` (add KMP properties)

- [ ] **Step 1: Rewrite settings.gradle.kts**

```kotlin
pluginManagement {
    repositories {
        // Chinese mirrors for fast downloads
        maven(url = "https://maven.aliyun.com/repository/google/")
        maven(url = "https://maven.aliyun.com/repository/gradle-plugin/")
        maven(url = "https://maven.aliyun.com/repository/public/")
        maven(url = "https://maven.aliyun.com/repository/jcenter/")
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven(url = "https://maven.aliyun.com/repository/google/")
        maven(url = "https://maven.aliyun.com/repository/public/")
        google()
        mavenCentral()
    }
}

rootProject.name = "AIMusicPlayer"
include(":shared")
include(":androidApp")
```

- [ ] **Step 2: Rewrite build.gradle.kts (root)**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
}
```

- [ ] **Step 3: Rewrite gradle.properties**

```properties
# Project-wide Gradle settings.
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.configuration-cache=true
kotlin.code.style=official

# KMP
kotlin.mpp.androidSourceSetLayoutVersion=2
kotlin.mpp.applyDefaultHierarchyTemplate=true

# Android
android.useAndroidX=true
android.nonTransitiveRClass=true
```

---

### Task 3: Create shared module build file and directory structure

**Files:**
- Create: `shared/build.gradle.kts`
- Create: Directory tree under `shared/src/`

- [ ] **Step 1: Create directory structure**

```bash
mkdir -p shared/src/commonMain/kotlin/com/example/aimusicplayer/network
mkdir -p shared/src/commonMain/kotlin/com/example/aimusicplayer/cache
mkdir -p shared/src/commonMain/kotlin/com/example/aimusicplayer/player
mkdir -p shared/src/commonMain/kotlin/com/example/aimusicplayer/ai
mkdir -p shared/src/commonMain/kotlin/com/example/aimusicplayer/ui
mkdir -p shared/src/androidMain/kotlin/com/example/aimusicplayer
mkdir -p shared/src/iosMain/kotlin/com/example/aimusicplayer
```

- [ ] **Step 2: Write shared/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kermit)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

android {
    namespace = "com.example.aimusicplayer.shared"
    compileSdk = 35
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
```

---

### Task 4: Create commonMain source files

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/aimusicplayer/Platform.kt`
- Create: `shared/src/commonMain/kotlin/com/example/aimusicplayer/App.kt`

- [ ] **Step 1: Write Platform.kt (expect declaration)**

```kotlin
package com.example.aimusicplayer

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
```

- [ ] **Step 2: Write App.kt (shared Compose UI)**

```kotlin
package com.example.aimusicplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun App() {
    MaterialTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Hello KMP Music Player 🎵")
        }
    }
}
```

---

### Task 5: Create platform-specific implementations

**Files:**
- Create: `shared/src/androidMain/kotlin/com/example/aimusicplayer/Platform.android.kt`
- Create: `shared/src/iosMain/kotlin/com/example/aimusicplayer/Platform.ios.kt`
- Create: `shared/src/iosMain/kotlin/com/example/aimusicplayer/MainViewController.kt`

- [ ] **Step 1: Write Platform.android.kt**

```kotlin
package com.example.aimusicplayer

class AndroidPlatform : Platform {
    override val name: String = "Android ${android.os.Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()
```

- [ ] **Step 2: Write Platform.ios.kt**

```kotlin
package com.example.aimusicplayer

import platform.UIKit.UIDevice

class IOSPlatform : Platform {
    override val name: String =
        UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()
```

- [ ] **Step 3: Write MainViewController.kt**

```kotlin
package com.example.aimusicplayer

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController { App() }
```

---

### Task 6: Create androidApp module

**Files:**
- Create: `androidApp/build.gradle.kts`
- Create: `androidApp/src/main/AndroidManifest.xml`
- Create: `androidApp/src/main/kotlin/com/example/aimusicplayer/MainActivity.kt`

- [ ] **Step 1: Create directory structure**

```bash
mkdir -p androidApp/src/main/kotlin/com/example/aimusicplayer
mkdir -p androidApp/src/main/res/values
```

- [ ] **Step 2: Write androidApp/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    sourceSets {
        androidMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
        }
    }
}

android {
    namespace = "com.example.aimusicplayer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.aimusicplayer"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
```

- [ ] **Step 3: Write AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:label="AIMusicPlayer"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 4: Write MainActivity.kt**

```kotlin
package com.example.aimusicplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App()
        }
    }
}
```

---

### Task 7: Create iosApp Swift files

**Files:**
- Create: `iosApp/iosApp/iOSApp.swift`
- Create: `iosApp/iosApp/ContentView.swift`

- [ ] **Step 1: Create directory structure**

```bash
mkdir -p iosApp/iosApp
```

- [ ] **Step 2: Write iOSApp.swift**

```swift
import SwiftUI

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

- [ ] **Step 3: Write ContentView.swift**

```swift
import UIKit
import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
    }
}
```

---

### Task 8: Create package placeholder files

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/aimusicplayer/network/.gitkeep`
- Create: `shared/src/commonMain/kotlin/com/example/aimusicplayer/cache/.gitkeep`
- Create: `shared/src/commonMain/kotlin/com/example/aimusicplayer/player/.gitkeep`
- Create: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ai/.gitkeep`
- Create: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/.gitkeep`

- [ ] **Step 1: Create .gitkeep files for empty packages**

```bash
touch shared/src/commonMain/kotlin/com/example/aimusicplayer/network/.gitkeep
touch shared/src/commonMain/kotlin/com/example/aimusicplayer/cache/.gitkeep
touch shared/src/commonMain/kotlin/com/example/aimusicplayer/player/.gitkeep
touch shared/src/commonMain/kotlin/com/example/aimusicplayer/ai/.gitkeep
touch shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/.gitkeep
```

---

### Task 9: Clean up old files and finalize

**Files:**
- Delete: `app/` directory (old Android-only module)
- Delete: `.idea/` directory (IDE will regenerate)
- Modify: `.gitignore` (update for KMP)

- [ ] **Step 1: Remove old app module**

```bash
rm -rf app
```

- [ ] **Step 2: Remove old IDE files (optional, IDE regenerates)**

```bash
rm -rf .idea
```

- [ ] **Step 3: Update .gitignore for KMP**

```gitignore
*.iml
.gradle
/local.properties
/.idea
.DS_Store
/build
/captures
.externalNativeBuild
.cxx
*.xcworkspace
*.xcuserdata
**/build
/shared/build
/androidApp/build
```

- [ ] **Step 4: Verify final project structure**

```bash
find . -type f -not -path './.git/*' -not -path './gradle/wrapper/*' -not -path './build/*' -not -name '*.jar' | sort
```

Expected: All 15+ files listed in the File Structure Map exist and no old `app/` files remain.

---

### Task 10: Verify Gradle configuration

- [ ] **Step 1: Run Gradle sync check (dry-run)**

```bash
./gradlew projects
```

Expected: Lists `:shared` and `:androidApp` as subprojects. If the Gradle wrapper version doesn't match, this step may warn — note any issues but don't block.

---

## Execution Order

Tasks MUST run in order: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10

Each task depends on files created in previous tasks. Task 10 is a verification gate.
