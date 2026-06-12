<div align="center">

<h1>LocationSpoofer</h1>

<p>High-fidelity Android system-level location spoofing and wireless environment simulation module based on KernelSU + LSPosed</p>

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![KernelSU](https://img.shields.io/badge/Root-KernelSU-orange.svg)](https://kernelsu.org)
[![LSPosed](https://img.shields.io/badge/Framework-LSPosed-purple.svg)](https://github.com/LSPosed/LSPosed)
[![Telegram](https://img.shields.io/badge/Telegram-Group-blue.svg)](https://t.me/+CsxZGItXdW40ZWVl)

[简体中文](README.md) | [English](README_EN.md)

</div>

---

> **📢 This project forked from [`HuangZhuoRui/LocationSpoofer`](https://github.com/HuangZhuoRui/LocationSpoofer).**<br>
> **📢 Join our [Telegram Group](https://t.me/+CsxZGItXdW40ZWVl). To be honest, I don't know why we need one, but everyone seems to want it, and others have it, so I must have one too!**

---

## ✨ Features

| Feature | Technical Details & Core Advantages |
|---|---|
| 🌍 **Multi-language & Dual-Map** | • Supports **Chinese (Simplified)**, **English**, and **Arabic** UI languages.<br>• Automatically utilizes AMap 3D SDK in domestic environments and switches to **Google Maps** overseas. |
| 🗺️ **Visual Map Selection** | • Drag-and-drop crosshair marker alignment, search history, and favorite locations bookmarking. |
| 🔀 **Route Planning System** | • State-machine driven multi-waypoint planning, supports undoing/resetting, start/end swapping, and real-time route previews. |
| 🕹️ **Virtual Joystick Control** | • Floating joystick controller for real-time manual movement with smooth bearing transitions and steering damping. |
| 🔄 **Auto-Loop Simulation** | • Automatically loops back and forth along preset routes. Supports customizable speeds (Walking, Running, Cycling, Driving) adjustable on-the-fly. |
| 🛰️ **High-fidelity GPS Hijacking** | • Hooks system-wide `Location` APIs at all levels.<br>• Integrates **physical step frequency simulation** (with perpendicular jitter) and **Gaussian altitude fluctuations** to bypass static coordinate checks. |
| 📶 **Wi-Fi Environment Cloning** | • Spoofs connected Wi-Fi (SSID, BSSID, RSSI, frequency, WiFi standard) and fakes nearby scan lists.<br>• Uses **real brand router OUI prefixes** (e.g., TP-Link, Huawei, Cisco, Dell) instead of completely random MAC addresses to bypass SDK vendor verification. |
| 🏗️ **Cell Tower Spoofing** | • Spoofs Cell Location details, supporting GSM, WCDMA, CDMA, 4G LTE, and even **5G NR cell networks** with complete cell identifiers (MCC, MNC, LAC, CID, TAC, PCI, NCI). |
| 🔵 **BLE Beacon Shielding** | • Intercepts system Bluetooth scans, faking or hiding iBeacons/BLE devices to prevent indoor positioning leaks. |
| 📍 **Per-App Coordinate System Adapter** | • **Solves Map Offset Shifts**: Allows choosing target coordinate systems per-app: `GCJ-02` (AMap/Tencent), `WGS-84` (GPS), or `BD-09` (Baidu).<br>• Employs centralized pre-computation to avoid expensive sine/cosine calls during high-frequency callbacks. |
| 🕵️‍♂️ **On-site Environment Scanning** | • **Environment Fingerprint Capture**: Background scanning automatically scans and records real cellular towers, Wi-Fi APs, and Bluetooth devices along your path.<br>• Captured data is stored in a local Room database and visualized as a heatmap coverage area.<br>• Supports **JSON Import/Export**, enabling users to share or back up custom wireless profiles. |
| 🛡️ **Advanced Anti-Detection** | • **Call Stack Cleaning**: Dynamically intercepts `Throwable.getStackTrace` to wipe out Xposed/LSPosed class frame traces.<br>• **Mock Location Flag Eraser**: Strips the `isMock` flag on standard API locations and dynamically clears internal private fields (like `mMock` in Android 13+).<br>• **Interface Hiding**: Filters out mock/test provider names in `LocationManager` and forces them to report as "gps". |

---

## 🏛️ System Architecture

This project adopts the **MVVM** architecture and implements a root-privileged configuration mechanism to bypass package visibility restrictions and SELinux isolation on Android 11+:

```
┌─────────────────────────────────────────────┐
│          LocationSpoofer (Host App)         │
│  ┌──────────┐  ┌──────────────────────────┐ │
│  │ Dual-Map │  │    RouteStateMachine     │ │
│  │(AMap/GMap)│  │    (IDLE/READY/RUN...)   │ │
│  └────┬─────┘  └────────────┬─────────────┘ │
│       │                     │               │
│  ┌────▼─────────────────────▼─────────────┐ │
│  │            ConfigManager                 │ │
│  │   (Writes serialized config to Temp via  │ │
│  │    Root permissions)                     │ │
│  └──────────────────┬───────────────────────┘ │
│  ┌──────────────────▼─────────────────────┐ │
│  │           SpoofingService               │ │
│  │       (Foreground Notification & Engine) │ │
│  └────────────────────────────────────────┘ │
└─────────────────────┬───────────────────────┘
                      │ (Writes Config JSON)
                      ▼
        ┌───────────────────────────┐
        │ /data/local/tmp/ Config   │
        │    (chmod 777 + chcon)    │
        └─────────────┬─────────────┘
                      │ (Reads Config, 800ms Memory Cache)
                      ▼ LSPosed Injection
┌─────────────────────────────────────────────┐
│              Target App Process             │
│  ┌────────────────────────────────────────┐  │
│  │            LocationHooker              │  │
│  │  • Location API / Baidu / Tencent SDK  │  │
│  │  • WiFi & Cellular (2G-5G NR) Injection│  │
│  │  • Bluetooth BLE Scan Filtering        │  │
│  │  • Anti-Mock & Xposed Stack Cleaning   │  │
│  └────────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
```

> [!NOTE]
> **IPC Design Decision**:
> When a target app is running inside its sandbox, querying a custom `ContentProvider` often fails with a `Failed to find provider info` error due to package visibility rules and SELinux policies on Android 11+.
> To ensure high reliability, the host app uses Root privileges to write the target config into `/data/local/tmp/locationspoofer_config.json`, changing permissions to `777` and applying the `shell_data_file` SELinux context. Sandboxed modules can then read it directly, bypassing sandbox restrictions.

---

## 📋 Requirements

- **Android 8.0 (API 26)** or higher
- Root access (recommended: [**KernelSU**](https://kernelsu.org) / APatch / Magisk)
- Installed [**LSPosed**](https://github.com/LSPosed/LSPosed) framework
- Enable the module in LSPosed Manager and check target apps for spoofing.

---

## 🚀 Quick Start

### 1. Build and Install

```bash
# Clone the repository
git clone https://github.com/your-username/LocationSpoofer.git

# Compile and install to your device
./gradlew installDebug
```

### 2. Basic Configuration

1. Open the app and grant Root access in **KernelSU** or Magisk Manager (needed for updating configuration files).
2. Activate the module in **LSPosed Manager** and choose your target apps:
   - WeChat (`com.tencent.mm`)
   - XuexiTong (`com.chaoxing.mobile`)
   - DingTalk (`com.alibaba.android.rimet`)
3. **Force stop** and restart the selected apps.

### 3. Tips & Tricks

- **Single point spoofing**: Tap/drag to target coordinate on the map and hit "Start Spoofing".
- **Route simulation**:
  1. Click "Route Planning" on the bottom menu and tap the map to place waypoints.
  2. Pick your speed (Walk, Run, Cycle, Drive).
  3. Start the simulation; choose between automatic loop or using the floating joystick.
- **Bypass Map Shifting**: If you notice a 300-500 meters map shift in apps like WeChat, go to the app home page, tap **Coordinate System Adapter**, and change the coordinate system for that package from default `GCJ-02` to `WGS-84` (Standard GPS) or `BD-09` (Baidu Maps).
- **Wi-Fi & Cell Tower Spoofing**:
  - **WiGLE Mode**: Set your WiGLE developer token to automatically query and pull real Wi-Fi router APs nearby.
  - **On-site Scan Mode**: Toggle background environment scanning while walking to save real-world cell/Wi-Fi configurations. It stores them in a local DB for high-fidelity offline simulation.

---

## 🛠️ Tech Stack

- **Language**: Kotlin (100%)
- **UI**: Jetpack Compose + Material Design 3
- **DI**: Koin
- **Storage**: Room Database (SQLite)
- **Xposed Framework**: LSPosed API 93 / libxposed
- **Map SDKs**: AMap 3DMap SDK (China) / Google Maps SDK & Google Places SDK (Global)
- **Engine**: TrajectorySimulator (Haversine distance calculations + smooth heading interpolation + perpendicular steps jitter)

---

## 🎁 Credit

- [LocationSpoofer](https://github.com/HuangZhuoRui/LocationSpoofer): upstream

---

## ⚠️ Disclaimer

This project is intended **solely for educational, academic, and testing purposes**.
Do not use this tool for any illegal activities or violations of third-party user agreements (including fake attendance checking, exam cheating, commercial fraud, etc.).
The author is not responsible for any banned accounts, data losses, legal issues, or other direct/indirect damages arising from the use of this software.

---

## 📜 License

This project is licensed under the [GNU General Public License v3.0](LICENSE).

```
Copyright (C) 2026 SuseOAA
```
