# HearEverything iOS 构建配置指南

## 前置条件

| 工具       | 最低版本    | 说明                             |
|-----------|------------|----------------------------------|
| macOS     | 13+        | iOS 编译需要 Xcode                |
| Xcode     | 15.0+      | 包含 Swift 5.9 + iOS 17 SDK      |
| CocoaPods | 1.14+      | SQLDelight native driver 依赖管理 |
| JDK       | 17         | Kotlin Gradle 插件编译             |

## 1. 安装 CocoaPods

```bash
# 如果未安装：
sudo gem install cocoapods

# 进入 iosApp 目录安装 pods：
cd iosApp
pod install
```

执行后生成 `iosApp.xcworkspace`。**此后始终用 `.xcworkspace` 打开项目，不要用 `.xcodeproj`。**

## 2. 创建 Xcode 项目

因为 `.xcodeproj` 是二进制格式，需要在 Xcode 中手动创建：

### Step 1: 创建项目
1. 打开 Xcode
2. **File → New → Project**
3. 选择 **iOS → App**
4. 配置：
   - Product Name: `iosApp`
   - Team: (你的开发者账号)
   - Organization Identifier: `com.example.aimusicplayer`
   - Interface: **SwiftUI**
   - Language: **Swift**
   - 取消勾选 "Include Tests"（或保留）
5. 保存到 `HearEverything/iosApp/` 目录

### Step 2: 删除自动生成的文件
Xcode 会创建默认的 `ContentView.swift` 和 `iOSApp.swift`，如果与现有文件冲突，保留现有的 KMM 桥梁文件（`ContentView.swift` / `iOSApp.swift`）并删除 Xcode 生成的模板代码。

### Step 3: 链接 Shared Framework
1. 选择 `iosApp` target → **General** → **Frameworks, Libraries, and Embedded Content**
2. 点击 `+` → **Add Other... → Add Files...**
3. 导航到并选择：
   ```
   shared/build/XCFrameworks/release/Shared.xcframework
   ```
   > 如果该文件不存在，先运行：
   > ```bash
   > cd .. && ./gradlew :shared:assembleSharedXCFramework
   > ```

### Step 4: 添加 Build Phase（自动构建 Kotlin Framework）
1. 选择 `iosApp` target → **Build Phases**
2. 点击 `+` → **New Run Script Phase**
3. 命名为 `Build Kotlin Framework`
4. 拖拽到 `Compile Sources` 之上
5. 脚本内容：
   ```bash
   cd "$SRCROOT/.."
   ./gradlew :shared:embedAndSignAppleFrameworkForXcode
   ```

### Step 5: 配置 Framework Search Paths
1. 选择 `iosApp` target → **Build Settings**
2. 搜索 `Framework Search Paths`
3. 添加：
   ```
   $(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)
   ```
4. 搜索 `Other Linker Flags`
5. 添加：`$(inherited) -framework Shared`

## 3. 编译与运行

### 命令行（Quick Check）
```bash
# 在项目根目录
./gradlew :shared:compileKotlinIosSimulatorArm64
```

### Xcode
1. 用 `.xcworkspace` 打开：
   ```bash
   open iosApp/iosApp.xcworkspace
   ```
2. 选择模拟器目标（如 `iPhone 15 Pro`）
3. **Product → Run**（⌘R）

### 真机部署
1. 在 Xcode 中选择实体设备
2. **Signing & Capabilities** 中配置你的 Team
3. 确保 `shared/build.gradle.kts` 中目标的 `iosArm64` 已启用
4. **Product → Run**

## 4. Info.plist 权限说明

| Key | 值 | 原因 |
|-----|---|------|
| `NSAppTransportSecurity.NSAllowsArbitraryLoads` | `true` | 音乐源 CDN 大量使用 HTTP（非 HTTPS） |
| `UIBackgroundModes[0]` | `audio` | AVAudioSession 后台音频播放 |

> 生产环境建议将 `NSAllowsArbitraryLoads` 改为 `NSExceptionDomains` 白名单。

## 5. expect/actual 声明校验清单

| commonMain expect | iosMain actual | 状态 |
|-------------------|---------------|------|
| `expect class SettingsStorage` | `actual class SettingsStorage` (NSUserDefaults) | ✅ |
| `expect class DatabaseDriverFactory` | `actual class DatabaseDriverFactory` (NativeSqliteDriver) | ✅ |
| `expect class MusicPlayer` | `actual class MusicPlayer` (AVPlayer + AVAudioSession) | ✅ |
| `expect class DownloadManager` | `actual class DownloadManager` (NSFileManager + SQLDelight) | ✅ |
| `expect fun getPlatform()` | `actual fun getPlatform()` (UIDevice) | ✅ |
| `interface CacheRepository` | `CacheRepositoryImpl` (commonMain 单例) | ✅ |
| `interface MusicPlatformApi` | NeteaseApi / QQMusicApi / KugouApi / BilibiliApi (commonMain) | ✅ |
| `interface FileStorage` | IosFileStorage → 已合并到 `DownloadManager` actual | ✅ |

## 6. 常见问题

### Q: `Shared framework not found`
确保运行过 `./gradlew :shared:embedAndSignAppleFrameworkForXcode`。

### Q: SQLDelight 找不到表
检查 `Podfile` 中 `SQLite.swift` 已安装（`pod install`），且版本与 `shared/build.gradle.kts` 中的 `sqldelight-native-driver:2.0.2` 匹配。

### Q: 网络请求失败
检查 `Info.plist` 中 `NSAppTransportSecurity` 是否启用 HTTP。

### Q: 后台播放不工作
确认：
1. `Info.plist` 中 `UIBackgroundModes` 包含 `audio`
2. `MusicPlayer` 的 `init` 中调用了 `AVAudioSession.setCategory(AVAudioSessionCategoryPlayback)`

## 7. 目录结构（iOS 相关）

```
HearEverything/
├── shared/
│   ├── build.gradle.kts              # Kotlin Multiplatform 配置 + iosTarget
│   └── src/
│       ├── commonMain/               # expect 声明
│       │   ├── .../settings/         # SettingsStorage (expect)
│       │   ├── .../player/           # MusicPlayer (expect)
│       │   ├── .../music/            # DownloadManager (expect)
│       │   └── .../cache/            # DatabaseDriverFactory (expect)
│       └── iosMain/                  # iOS actual 实现
│           ├── .../Platform.ios.kt
│           ├── .../MainViewController.kt
│           ├── .../settings/SettingsStorage.ios.kt
│           ├── .../cache/DatabaseDriverFactory.ios.kt
│           ├── .../player/MusicPlayer.ios.kt
│           └── .../music/
│               ├── DownloadManager.ios.kt
│               └── DownloadManagerFactory.ios.kt
└── iosApp/
    ├── iosApp.xcworkspace/           # (pod install 后生成)
    ├── Podcastfile                    # CocoaPods 依赖
    ├── Pods/                         # (pod install 后生成)
    └── iosApp/
        ├── iOSApp.swift              # SwiftUI App 入口
        ├── ContentView.swift         # Compose → UIKit bridge
        └── Info.plist                # 权限 + 后台模式配置
```
