<div align="center">

<h1>LocationSpoofer</h1>

<p>基于 KernelSU + LSPosed 的高保真 Android 系统级虚拟定位模块</p>

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![KernelSU](https://img.shields.io/badge/Root-KernelSU-orange.svg)](https://kernelsu.org)
[![LSPosed](https://img.shields.io/badge/Framework-LSPosed-purple.svg)](https://github.com/LSPosed/LSPosed)

</div>

---

## ✨ 功能特性

| 功能 | 说明 |
|---|---|
| 🗺️ **地图可视化选点** | 集成高德 3D 地图，拖动准星精准选取目标坐标 |
| 📡 **GPS 全层级劫持** | Hook `android.location.Location` 全部方法，含平滑抖动模拟真实卫星漂移 |
| 📶 **Wi-Fi 环境克隆** | 通过 WiGLE API 拉取目标坐标周围的真实 Wi-Fi 热点指纹并注入 |
| 🔵 **BLE 信标屏蔽** | 拦截 `BluetoothLeScanner`，防止 iBeacon 泄露真实位置 |
| 🏗️ **基站信息伪造** | Hook `TelephonyManager` 返回虚假 Cell Location |
| 🕵️ **反 Mock 检测** | 抹除 `isFromMockProvider`、`isMock`（Android 13 的 `mMock` 字段）及高德 SDK `getMockData` |
| 🔀 **跨进程 IPC** | 使用 `ContentProvider` 在宿主 App 与注入进程之间高速同步配置 |
| 📦 **子进程覆盖** | 前缀匹配覆盖微信所有 `:appbrand` 小程序子进程 |

---

## 🏛️ 系统架构

```
┌─────────────────────────────────────────┐
│            宿主 App (LocationSpoofer)    │
│  ┌──────────┐  ┌──────────────────────┐ │
│  │ AMap UI  │  │   WigleClient        │ │
│  │ (地图选点) │  │  (WiGLE API 查询)   │ │
│  └────┬─────┘  └──────────┬───────────┘ │
│       │                   │             │
│  ┌────▼───────────────────▼───────────┐ │
│  │         SpooferProvider            │ │
│  │     (ContentProvider IPC 桥)       │ │
│  └────────────────────────────────────┘ │
│  ┌──────────────────────────────────┐   │
│  │        SpoofingService           │   │
│  │    (TestProvider 前台服务)        │   │
│  └──────────────────────────────────┘   │
└─────────────────────────────────────────┘
              ↓ LSPosed 注入
┌─────────────────────────────────────────┐
│           目标 App 进程                  │
│  ┌──────────────────────────────────┐   │
│  │         LocationHooker           │   │
│  │  • Location.getLatitude/Lng      │   │
│  │  • isFromMockProvider / isMock   │   │
│  │  • WifiManager.getScanResults    │   │
│  │  • WifiManager.getConnectionInfo │   │
│  │  • AMapLocation.getMockData      │   │
│  │  • BluetoothLeScanner.startScan  │   │
│  │  • TelephonyManager.getAllCell   │   │
│  └──────────────────────────────────┘   │
└─────────────────────────────────────────┘
```

---

## 📋 环境要求

- Android **8.0 (API 26)** 及以上
- [**KernelSU**](https://kernelsu.org) — Root 权限管理
- [**LSPosed**](https://github.com/LSPosed/LSPosed) — Xposed 框架
- 在 LSPosed 管理器中启用本模块并勾选目标应用

---

## 🚀 快速开始

### 1. 编译

```bash
git clone https://github.com/your-username/LocationSpoofer.git
cd LocationSpoofer
./gradlew assembleDebug
```

### 2. 安装

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 3. 配置

1. 在 **KernelSU** 中授予本应用 Root 权限
2. 在 **LSPosed 管理器** → 模块 → 启用 `LocationSpoofer`
3. 在作用域中勾选以下目标应用（按需选择）：

| 应用 | 包名 |
|---|---|
| 微信（含小程序） | `com.tencent.mm` |
| 超星学习通 | `com.chaoxing.mobile` |
| 高德地图 | `com.autonavi.minimap` |
| 百度地图 | `com.baidu.BaiduMap` |
| 钉钉 | `com.alibaba.android.rimet` |
| Google Play 服务 | `com.google.android.gms` |

4. **强制停止**目标应用后重新打开
5. 打开 LocationSpoofer → 在地图上拖动选点 → 点击 **启动虚拟定位**

---

## 🛠️ 技术栈

| 类别 | 技术 |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material3 |
| 架构 | MVVM + ViewModel + StateFlow |
| 依赖注入 | Koin |
| 地图 | AMap 3DMap SDK |
| 网络 | OkHttp 4 |
| Hook 框架 | LSPosed / Xposed API 93 |
| Root 管理 | KernelSU |
| Wi-Fi 数据 | WiGLE API v2 |

---

## ⚠️ 免责声明

本项目**仅供学习和研究使用**。使用本工具进行虚拟定位可能违反某些应用的服务条款。

作者不对使用本工具产生的任何法律责任或损失负责。请遵守当地法律法规，不要将其用于任何违法活动。

---

## 📜 开源许可

本项目采用 [GNU General Public License v3.0](LICENSE) 开源许可证。

```
Copyright (C) 2026 SuseOAA
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License.
```
