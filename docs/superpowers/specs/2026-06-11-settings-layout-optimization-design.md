# 设置页面布局优化 — 设计文档

**日期**: 2026-06-11
**范围**: 局部精修（修复 bug、统一风格、收紧间距）

## 背景

HearEverything 设置页（`SettingsScreen.kt`）存在以下问题：

1. **UI 风格不统一** — AI 配置区用裸 `OutlinedTextField`，播放选项/外观用 `SettingsCard` 包裹
2. **设置项不够紧凑** — section 间用粗 `HorizontalDivider` 分隔，同 section 项各自独立成卡片
3. **存在 bug** — 第 287-289 行有两个重复的 `HorizontalDivider`
4. **存在无效设置项** — "后台播放"开关存读正常但无任何播放代码消费
5. **存在死代码** — `AiSettingsScreen.kt` 无任何引用，功能已内聚到 `SettingsScreen`

## 设计目标

- 统一所有设置项的卡片容器风格，采用 **iOS Grouped** 模式
- 移除无效开关和死代码
- 收紧间距，减少不必要的视觉分割

## 具体改动

### 1. 布局重构 — iOS Grouped 风格

**Section 内合并**：同一 section 下的多个设置项合并到一张 `SettingsCard` 中，项与项之间用 0.5dp 细线分隔。

示例（播放选项 section）：
```
┌──────────────────────────────────────┐
│  后台播放                     [开关]  │  ← 已移除
│  ────────────────────────────── 0.5px │
│  历史记录数量           20 首 ▸       │
└──────────────────────────────────────┘
```

**Section 间分隔**：用 `Spacer(16.dp)` 自然分隔，不再使用 `HorizontalDivider`。

**AI 配置区域**：Base URL 和 API Key 两输入框合并到一张卡片中，内部细线分隔。Key 状态文字和测试按钮保持在卡片外部。

```
AI 配置
┌──────────────────────────────────────┐
│  Base URL                             │
│  https://api.deepseek.com/v1          │
│  ────────────────────────────── 0.5px │
│  API Key                              │
│  ••••••••••••••••                     │
└──────────────────────────────────────┘
API Key 状态: 已输入 (32 字符)
┌──────────────────────────────────────┐
│            测试连接                    │
└──────────────────────────────────────┘
```

**统一容器样式**：
- 圆角：`RoundedCornerShape(12.dp)`
- 容器色：`surfaceVariant.copy(alpha = 0.5f)`
- 细线色：`outlineVariant.copy(alpha = 0.5f)`

### 2. 移除无效设置 — 后台播放

| 文件 | 操作 |
|------|------|
| `SettingsScreen.kt` | 删除 `SettingsToggle`(后台播放) 调用、删除 `backgroundPlayback` 状态订阅 |
| `SettingsViewModel.kt` | 删除 `_backgroundPlayback` state、`onBackgroundPlaybackChanged()`、`saveAll()` 中的持久化调用 |
| `SettingsRepository.kt` | 删除 `getBackgroundPlayback()`、`setBackgroundPlayback()` |
| `SettingsKeys.kt` | 保留 `BACKGROUND_PLAYBACK` 常量（不伤数据层，方便日后真正实现时复用） |

### 3. 修复 Bug

删除 `SettingsScreen.kt` 第 287-289 行重复的 `HorizontalDivider`。

### 4. 删除死代码

删除 `AiSettingsScreen.kt` — 无任何调用引用，其功能已完全在 `SettingsScreen` 中实现。

## 改动文件清单

| 文件 | 改动类型 |
|------|---------|
| `shared/.../ui/settings/SettingsScreen.kt` | 重构布局、修 bug、移除后台播放 |
| `shared/.../ui/settings/SettingsViewModel.kt` | 移除 backgroundPlayback 相关 |
| `shared/.../settings/SettingsRepository.kt` | 移除 backgroundPlayback 存取方法 |
| `shared/.../ui/settings/AiSettingsScreen.kt` | **删除** |

## 不变项

- 主题色彩（HearEverythingTheme）不变
- Section 分类结构不变（AI配置/播放选项/外观/定时关闭/存储管理）
- 各设置项的功能逻辑不变
- `SettingsKeys` 常量保留（包括 `BACKGROUND_PLAYBACK`）
- 测试文件涉及后台播放的部分需要同步更新

## 测试影响

- `SettingsRepositoryTest.kt` — 移除 `background playback` 相关测试用例
- `SettingsViewModelRobolectricTest.kt` — 移除 `background_playback_defaults_to_true` 测试

## 不纳入范围

- 不引入分组折叠/展开
- 不引入图标引导
- 不改变导航结构
- `SettingsKeys.LYRICS_SYNC` 等其他潜在无效设置 — 不在本次范围内
