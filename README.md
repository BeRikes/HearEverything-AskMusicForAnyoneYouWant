# 🎵 HearEverything

> AI 驱动的跨平台音乐播放器 — 用自然语言搜歌、播放、下载，聚合多平台音源。

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.20-blueviolet?logo=kotlin)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.7.3-blue?logo=jetpackcompose)](https://www.jetbrains.com/compose-multiplatform/)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20iOS-brightgreen)]()
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

---

## ✨ 核心特性

- **🤖 AI 自然语言搜歌** — 支持"播放周杰伦的晴天"、"下载一些轻音乐"等自然语言指令，AI 自动解析为搜歌/播放/下载动作
- **🔍 多平台聚合搜索** — 依次从 B站、网易云音乐、QQ音乐、酷狗 搜索，自动过滤 VIP/付费歌曲
- **📱 跨平台** — Kotlin Multiplatform + Compose Multiplatform，一套代码同时运行 Android 和 iOS
- **💾 本地缓存** — SQLite 缓存歌曲元数据和播放链接，加速重复访问
- **📥 下载管理** — 支持歌曲下载到本地，离线播放
- **📋 歌单导入** — 支持多个音乐平台的歌单链接解析和批量导入
- **🎨 Material3 主题** — 网易云红 + QQ蓝紫品牌色，默认暗色模式，支持主题切换
- **🎤 歌词显示** — 全屏播放器支持平滑滚动歌词
- **🧠 智能推荐** — 基于播放历史的歌曲推荐引擎
- **📦 数据导出/导入** — 支持歌单和设置的 ZIP 备份还原
- **⏰ 睡眠定时器** — 定时停止播放

## 📸 界面预览

<div align="center">
  <table>
    <tr>
      <td><img src="image/AI对话.jpg" width="200" alt="AI对话"/></td>
      <td><img src="image/当前歌单.jpg" width="200" alt="当前歌单"/></td>
      <td><img src="image/播放器界面.jpg" width="200" alt="播放器界面"/></td>
    </tr>
    <tr>
      <td align="center"><b>AI 对话</b></td>
      <td align="center"><b>当前歌单</b></td>
      <td align="center"><b>播放器</b></td>
    </tr>
    <tr>
      <td><img src="image/歌词界面.jpg" width="200" alt="歌词界面"/></td>
      <td><img src="image/下载.jpg" width="200" alt="下载"/></td>
      <td><img src="image/设置.jpg" width="200" alt="设置"/></td>
    </tr>
    <tr>
      <td align="center"><b>歌词</b></td>
      <td align="center"><b>下载</b></td>
      <td align="center"><b>设置</b></td>
    </tr>
  </table>
</div>

## 🏗 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin 2.1.20 |
| UI 框架 | Compose Multiplatform 1.7.3 (Material3) |
| 网络 | Ktor 3.0.3 |
| 本地存储 | SQLDelight 2.0.2 (SQLite) |
| 图片加载 | Coil 3.0.4 |
| 序列化 | kotlinx-serialization 1.7.3 |
| 协程 | kotlinx-coroutines 1.9.0 |
| Android 播放器 | Media3 ExoPlayer |
| iOS 播放器 | AVPlayer + AVAudioSession |
| 构建系统 | Gradle 8.12.1 + AGP 8.7.3 |

## 📐 播放流水线

```
用户自然语言输入
    ↓
AI 解析 (AiAssistant → AiCommand)
    ↓
多平台搜索 (Bilibili → Netease → QQ → Kugou)
    ↓
URL 解析 (MusicPlatformApi.getPlayUrl)
    ↓
本地缓存 (SQLDelight SQLite)
    ↓
播放 (Android: ExoPlayer / iOS: AVPlayer)
```

## 🚀 快速开始

### 前置条件

- **JDK 17+**
- **Android Studio** (推荐最新版，用于 Android 构建)
- **Xcode 15.0+** + **CocoaPods 1.14+** (仅 iOS)
- **macOS** (仅 iOS 构建需要)

### Android

```bash
# 编译整个 App
./gradlew :androidApp:assembleDebug

# 安装到模拟器/设备
./gradlew :androidApp:installDebug
```

### iOS

> 详细步骤见 [iosApp/SETUP.md](iosApp/SETUP.md)

```bash
# 1. 安装 CocoaPods 依赖
cd iosApp && pod install && cd ..

# 2. 编译 Shared Framework
./gradlew :shared:embedAndSignAppleFrameworkForXcode

# 3. 用 Xcode 打开 iosApp/iosApp.xcworkspace 运行
```

### 运行测试

```bash
# 跨平台通用测试
./gradlew :shared:allTests

# Android Unit Test (Robolectric)
./gradlew :shared:testDebugUnitTest

# Android Instrumented Test (需要模拟器)
./gradlew :shared:connectedDebugAndroidTest
```

## 📁 项目结构

```
HearEverything/
├── shared/                     # KMP 核心模块
│   └── src/
│       ├── commonMain/         # 跨平台共享代码 (业务逻辑 + Compose UI)
│       │   ├── ai/             # AI 对话解析
│       │   ├── music/          # 搜歌、下载、歌单管理
│       │   ├── network/music/  # 各平台 API 适配 (网易/QQ/酷狗/B站)
│       │   ├── player/         # 播放器接口 (expect)
│       │   ├── cache/          # SQLDelight 缓存
│       │   ├── settings/       # 设置存储
│       │   ├── ui/             # Compose UI 组件 (4 Tab + 播放器)
│       │   └── recommendation/ # 推荐引擎
│       ├── androidMain/        # Android 平台实现 (actual)
│       ├── iosMain/            # iOS 平台实现 (actual)
│       └── commonTest/         # 跨平台测试 + 测试夹具
├── androidApp/                 # Android 宿主应用 (薄壳)
├── iosApp/                     # iOS 宿主应用 (薄壳)
├── scripts/                    # 工具脚本 (加密验证、推荐评估)
├── docs/                       # 设计文档与实施计划
└── icon-*/                     # 应用图标素材
```

## 🔒 安全说明

- 本项目仅用于学习和研究目的
- 请遵守各音乐平台的服务条款
- **请勿**将本项目用于商业用途或大规模爬取
- 项目中不包含任何 API 密钥或 Token，使用前需自行配置
- 音乐资源版权归各平台所有，下载的音乐请于 24 小时内删除

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！在提交 PR 之前：

1. 确保现有测试通过：`./gradlew :shared:allTests`
2. 添加必要的测试覆盖
3. 遵循项目现有的代码风格和架构约定

## 📄 License

[MIT](LICENSE)

---

> 🤖 部分代码由 [Claude Code](https://claude.ai/code) 辅助生成
