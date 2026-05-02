<div align="center">

<h1>LocationSpoofer</h1>

<p>基于 KernelSU + LSPosed 的高保真 Android 系统级虚拟定位模块</p>

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![KernelSU](https://img.shields.io/badge/Root-KernelSU-orange.svg)](https://kernelsu.org)
[![LSPosed](https://img.shields.io/badge/Framework-LSPosed-purple.svg)](https://github.com/LSPosed/LSPosed)
[![Telegram](https://img.shields.io/badge/Telegram-交流群-blue.svg)](https://t.me/+CsxZGItXdW40ZWVl)

</div>

---

> **📢 加入我们的 [Telegram 交流群](https://t.me/+CsxZGItXdW40ZWVl) ，虽然我也不知道为什么要有一个TG群，但是大家都想要，而且别人也有，那我也要有！**

---

## ✨ 功能特性

| 功能 | 说明 |
|---|---|
| 🗺️ **地图可视化选点** | 集成高德 3D 地图，支持准星拖拽、搜索历史位置及收藏夹 |
| 🔀 **路线规划系统** | 状态机驱动的多路点规划，支持撤销、重置及实时预览 |
| 🕹️ **虚拟摇杆控制** | 手动模拟模式下可通过浮动摇杆实时控制位置移动，支持平滑方位角过渡 |
| 🔄 **自动循环模拟** | 支持沿预设路线自动往返，可自定义步行、跑步、骑行、驾车等多种速度 |
| 🛰️ **高保真 GPS 劫持** | Hook `Location` 全层级方法，集成**步频模拟**与**卫星漂移抖动**，规避静态位置检测 |
| 📶 **Wi-Fi 环境克隆** | 基于 WiGLE API 实时拉取目标坐标周围的真实热点指纹（BSSID/SSID）并注入 |
| 🔵 **BLE 信标屏蔽** | 拦截蓝牙扫描，防止通过 iBeacon 等室内定位技术泄露真实位置 |
| 🏗️ **基站信息伪造** | 模拟 Cell Location 信息，提供完整的地理位置欺骗链路 |
| 🕵️ **深度反检测** | 抹除 `isMock` 标志位及高德 SDK 内部 Mock 检测，覆盖 Android 13+ 字段 |

---

## 🏛️ 系统架构

本项目采用 **MVVM** 架构，配合 **State-Machine** 处理复杂的路线规划流程：

```
┌─────────────────────────────────────────┐
│            LocationSpoofer (App)         │
│  ┌──────────┐  ┌──────────────────────┐ │
│  │ AMap UI  │  │  RouteStateMachine   │ │
│  │ (地图选点) │  │  (IDLE/READY/RUN...) │ │
│  └────┬─────┘  └──────────┬───────────┘ │
│       │                   │             │
│  ┌────▼───────────────────▼───────────┐ │
│  │         SpooferProvider            │ │
│  │     (ContentProvider IPC 桥)       │ │
│  └────────────────────────────────────┘ │
│  ┌──────────────────────────────────┐   │
│  │        SpoofingService           │   │
│  │    (前台服务 & 轨迹计算引擎)         │   │
│  └──────────────────────────────────┘   │
└─────────────────────────────────────────┘
              ↓ LSPosed 注入
┌─────────────────────────────────────────┐
│           目标 App 进程                  │
│  ┌──────────────────────────────────┐   │
│  │         LocationHooker           │   │
│  │  • GPS/BDS/GLONASS 劫持          │   │
│  │  • Wifi/Cell/Bluetooth 注入      │   │
│  │  • Anti-Mock & SDK 检测绕过       │   │
│  └──────────────────────────────────┘   │
└─────────────────────────────────────────┘
```

---

## 📋 环境要求

- **Android 8.0 (API 26)** 及以上
- [**KernelSU**](https://kernelsu.org) / Magisk (需要 Root 权限)
- [**LSPosed**](https://github.com/LSPosed/LSPosed) 框架
- 在 LSPosed 管理器中启用模块并勾选需要伪装的目标应用

---

## 🚀 快速开始

### 1. 编译与安装

```bash
# 克隆仓库
git clone https://github.com/your-username/LocationSpoofer.git
# 编译并安装
./gradlew installDebug
```

### 2. 配置说明

1. 在 **KernelSU** 中授予 Root 权限。
2. 在 **LSPosed** 中激活模块，建议勾选以下常用应用：
   - 微信 (`com.tencent.mm`)
   - 超星学习通 (`com.chaoxing.mobile`)
   - 钉钉 (`com.alibaba.android.rimet`)
3. **强制停止**目标应用后重新打开。

### 3. 使用技巧

- **定点模式**：直接在地图选点，点击“启动”即可。
- **路线模式**：
  1. 点击“路线规划”，在地图上依次点击标记路点。
  2. 选择“手动（摇杆）”或“自动循环”。
  3. 点击“开始模拟”。

---

## 🛠️ 技术栈

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3 (Material Design 3)
- **Framework**: LSPosed / Xposed API 93
- **Map**: AMap 3DMap SDK
- **Data**: Koin (DI), OkHttp 4, Coroutines Flow
- **Simulation**: TrajectorySimulator (Haversine 算法 + 方位角插值)

---

## ⚠️ 免责声明

本项目**仅供学习和技术研究使用**。请勿将本工具用于任何违法违规活动（包括但不限于虚假打卡、作弊等）。
用户在使用本工具时应遵守当地法律法规，作者不对因使用本工具导致的任何账号封禁、法律责任或损失承担责任。

---

## 📜 开源许可

本项目采用 [GNU General Public License v3.0](LICENSE) 开源许可证。

```
Copyright (C) 2026 SuseOAA
```
