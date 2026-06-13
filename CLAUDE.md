# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目简介

HearEverything（工程名 AIMusicPlayer）是一个 AI 驱动的跨平台音乐播放器。用户通过自然语言对话搜歌、播放、下载，后端聚合网易云、QQ音乐、酷狗、B站等多平台音源。Kotlin Multiplatform + Compose Multiplatform 实现，目标 Android 和 iOS。

## 常用命令

### 构建

```bash
# 编译 shared 模块 (Android target, debug)
./gradlew :shared:compileDebugKotlinAndroid

# 编译整个 Android App
./gradlew :androidApp:assembleDebug

# 查看所有子项目
./gradlew projects
```

### 测试

```bash
# 运行 commonTest（跨平台共享测试，JVM 上运行）
./gradlew :shared:allTests

# 仅运行 commonTest（不含 Android 特定测试）
./gradlew :shared:jvmTest

# 运行 Android Unit Test（Robolectric，不需要设备）
./gradlew :shared:testDebugUnitTest

# 运行 Android Instrumented Test（需要模拟器/设备）
./gradlew :shared:connectedDebugAndroidTest

# 运行 androidApp 的 instrumented test
./gradlew :androidApp:connectedDebugAndroidTest
```

### 代码覆盖率

```bash
# 使用 Kover 生成覆盖率报告
./gradlew :shared:koverHtmlReport
```

### Gradle Wrapper

项目使用 Gradle 8.11，JDK 17+。由于项目配置了阿里云 Maven 镜像，在中国大陆下载依赖更快。

## 架构概览

### 模块结构

- **shared/** — KMP 核心模块，包含所有业务逻辑和 Compose UI
  - `commonMain/` — 跨平台共享代码（UI、网络、缓存、播放器逻辑）
  - `androidMain/` — Android 平台实现（ExoPlayer、DataStore、OkHttp）
  - `iosMain/` — iOS 平台实现（AVPlayer、UserDefaults、Darwin Ktor engine）
- **androidApp/** — Android 薄壳，仅含 `MainActivity` 和集成测试
- **iosApp/** — iOS 薄壳，SwiftUI 包装 Compose UI

### 依赖注入方式

项目**不使用** DI 框架。`App()` composable 在入口处通过工厂 lambda 参数接收平台依赖（`createMusicPlayer`、`createDownloadManager`、`createCacheRepository` 等），内部用 `remember {}` 创建并 memoize。平台宿主（`MainActivity`/`MainViewController`）负责提供实际的工厂实现。

### 播放器核心流水线

1. **AI 解析** — `AiAssistant` 将用户自然语言转为 `AiCommand`（搜索/播放/下载）
2. **多平台搜索** — `MusicSearchManager` 按顺序（B站 → 网易云 → QQ）查询各平台 API，搜前过滤 VIP/付费歌曲（`fee > 0`）
3. **URL 解析** — 通过 `MusicPlatformApi.getPlayUrl()` 获取音频直链
4. **本地缓存** — SQLDelight (SQLite) 缓存成功解析的歌曲元数据和 URL
5. **播放** — `MusicPlayer` (expect/actual: Android→ExoPlayer, iOS→AVPlayer)

### 平台 API 适配层

所有音乐平台实现 `MusicPlatformApi` 接口（`commonMain`），提供 `search()`/`getPlayUrl()`/`getLyrics()`。已实现四个平台：

- `NeteaseApi` — 网易云音乐（weapi 加密，需 RSA+AES）
- `QQMusicApi` — QQ音乐（VKey 签名）
- `KugouApi` — 酷狗音乐（内网代理解密 + SSL bypass）
- `BilibiliApi` — B站（公开 API，无需鉴权）

加密/签名工具在 `network.music.crypto` 包下。`scripts/verify_nc_encrypt.py` 可用于独立验证网易云加密逻辑。

### UI 导航结构

4 个底部 Tab（`Screen` 枚举，在 `App.kt` 中定义）：

- **对话** (`MainScreen`) — AI 对话 + 搜索结果展示
- **播放列表** (`PlaylistScreen`) — 当前播放队列 + 歌单导入
- **下载** (`LibraryScreen`) — 本地下载的歌曲
- **设置** (`SettingsScreen`) — 主题/缓存/AI 配置

底部始终显示 `MiniPlayerBar`（迷你播放器），点击展开 `NowPlayingOverlay`（全屏播放器 + 歌词）。

### ViewModel 体系

使用自定义 `ViewModel` 基类（`com.example.aimusicplayer.viewmodel.ViewModel`），非 AndroidX。自带 `CoroutineScope(SupervisorJob() + Dispatchers.Main)`，需要手动调用 `onCleared()`。所有 ViewModel 通过 `remember {}` 在 `App()` 中创建。

### 自定义主题

`HearEverythingTheme` — Material3 包装，品牌色：**网易云红**（Primary）+ **QQ蓝紫**（Secondary/Tertiary）。暗色模式为默认，`SettingsKeys.THEME_MODE_*` 控制主题切换。

### 测试策略

- `commonTest/` — 跨平台逻辑测试（kotlin.test + Fake/Mock）
- `androidUnitTest/` — Robolectric 驱动的 Compose UI 测试（不需要设备）
- `androidInstrumentedTest/` — 真机/模拟器集成测试
- 测试夹具在 `fixtures/` 包：`FakeMusicPlatformApi`、`FakeCacheRepository`、`FakeSettingsStorage`

### 关键依赖版本（版本目录）

版本统一在 `gradle/libs.versions.toml` 管理：Kotlin 2.1.20 / Compose Multiplatform 1.7.3 / AGP 8.7.3 / Ktor 3.0.3 / kotlinx-coroutines 1.9.0 / SQLDelight 2.0.2 / Coil 3.0.4 / Kermit 2.0.5 / Kover 0.9.1

### KMP expect/actual 约定

所有平台差异通过 expect/actual 机制处理，常见于：
- `MusicPlayer` — 播放器引擎
- `DatabaseDriverFactory` — SQLite 驱动
- `SettingsStorage` — 键值存储
- `CryptoProvider` — 加密原语（MD5 等）
- `SslBypass` — SSL 证书校验绕过
- `KugouClientFactory` — 酷狗需要特殊 TLS 配置
- `PlaylistManager` — 文件系统操作
