# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

**Fake Loop** 是一个安卓蓝牙跳绳模拟器 App，将手机模拟为 BLE 跳绳设备，可通过第三方 App（如「乐跳」）连接并接收跳绳数据。该 App 使用 BLE Peripheral 模式广播「LR029429BD」设备名，对外暴露特征服务 FFF0，包含通知特征 FFF1 和读写特征 FFF2。

核心功能：
- 模拟跳绳计数数据包发送（每秒一个包）
- UI 提供开关（随机增量）、目标跳数/时间输入框、开始按钮、倒计时显示
- 数据包格式固定：`6F040B000000XXXX6930005703D802A8`，其中 XXXX 为跳数×10 的十六进制大写

## 目录结构

```
app/
├── build.gradle.kts          # 构建配置（Gradle Kotlin DSL）
├── src/
│   ├── main/
│   │   ├── java/com/fasa2333/fakeloop/
│   │   │   ├── MainActivity.kt          # 主 UI 与核心跳数发送逻辑
│   │   │   ├── BlePeripheralManager.kt   # BLE 外设管理器（广播/GATT 服务器）
│   │   │   └── ui/
│   │   │       ├── theme/Color.kt        # 调色板定义
│   │   │       ├── theme/Theme.kt        # 主题配置
│   │   │       └── theme/Type.kt         # 字体样式
│   │   └── AndroidManifest.xml
│   ├── androidTest/                     # Android 设备测试
│   └── test/                            # 本地单元测试
└── settings.gradle.kts                  # 根项目设置
README.md                                # 项目简介
```

## 关键文件

### MainActivity.kt

- **路径**: `app/src/main/java/com/fasa2333/fakeloop/MainActivity.kt`
- **角色**: App 唯一 Activity，管理 UI 和跳数发送协程
- **关键组件**:
  - `companion object` — SharedPreferences 键常量
  - `prefs` — 持久化设置（目标跳数、目标时间、随机开关）
  - `scope` — 协程作用域，管理发送循环
  - `MainContent` — Compose UI：开关 + 输入框 + 倒计时 + 开始按钮
  - 发送循环：每秒根据剩余时间/剩余跳数增量计算 → 组装 hex 包 → `notifySubscribers` 发送 → `delay(1000)` 固定间隔

### BlePeripheralManager.kt

- **路径**: `app/src/main/java/com/fasa2333/fakeloop/BlePeripheralManager.kt`
- **角色**: BLE 外设核心，管理广播、GATT 服务器、通知发送
- **关键部分**:
  - `startPeripheral`/`stopPeripheral` — 启动/停止广播和 GATT 服务器
  - `notifySubscribers(data: ByteArray)` — 遍历所有已订阅设备发送通知（数据包）
  - GATT 服务器回调：连接状态、描述符写请求（用于订阅管理）、特征读取/写入响应

### 测试文件

- `ExampleUnitTest.kt` — 本地单元测试（JUnit）
- `ExampleInstrumentedTest.kt` — Android 设备测试

## 构建与测试

### 构建

```bash
./gradlew assembleDebug    # 编译 debug APK
./gradlew assembleRelease  # 编译 release APK
```

### 运行

```bash
./gradlew installDebug    # 安装 debug APK 到设备
```

或直接使用 Android Studio 运行。

### 测试

```bash
./gradlew test            # 运行本地单元测试
./gradlew connectedAndroidTest  # 运行设备测试
```

## 开发指南

### 数据包格式

跳绳数据包为固定 18 字节十六进制字符串，格式：
```
6F040B000000XXXX6930005703D802A8
```
其中 `XXXX` 是当前跳数 × 10 的 4 位十六进制大写（例如 800 跳 → `1F40`）。

开始包固定为 `6F0201000072`。

### 发送流程

1. 点击「开始跳绳」按钮 → 发送开始包 → 进入协程循环
2. 每秒迭代一次：计算增量 → 组装包 → 发送 → `delay(1000)` 等待下一秒
3. 剩余时间归零时退出循环 → 发送结束状态（`isRunning = false`）

### 随机增量逻辑

- **随机开关开启**：目标跳数会在启动时附加 `(0..50).random()` 的一次性偏移
- **每秒增量** = `floor(剩余跳数 / 剩余秒数) + (0..2).random()`，并钳位到不超过剩余跳数

### 持久化存储

使用 Java 风格的 SharedPreferences API（项目缺少 `core-ktx` 库）：
```kotlin
prefs.edit().putInt(key, value).apply()  // 写入
prefs.getInt(key, defaultValue)          // 读取
```
支持键：`targetJumps`、`targetTime`、`randomEnabled`、`lastUsedTargetJumps`

## 注意事项

- BLE 权限需要在 Android S+ 上单独请求 `BLUETOOTH_ADVERTISE` / `BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN`；旧版本需 `ACCESS_FINE_LOCATION`。
- 设备名固定为 `LR029429BD`，若系统设置中修改了名称需对应修改此常量。
- 编译依赖：`compileSdk = release(36)`，需安装 Android SDK Build-Tools 36 及对应 Compose 库版本。
- 当前为版本 `1.1.0`（`versionCode = 3`），若需升级注意 `build.gradle.kts` 中 `versionCode` 增幅。
