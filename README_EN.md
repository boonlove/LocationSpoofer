<div align="center">

<h1>LocationSpoofer</h1>

<p>High-fidelity Android system-level location spoofing module based on KernelSU + LSPosed</p>

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![KernelSU](https://img.shields.io/badge/Root-KernelSU-orange.svg)](https://kernelsu.org)
[![LSPosed](https://img.shields.io/badge/Framework-LSPosed-purple.svg)](https://github.com/LSPosed/LSPosed)
[![Telegram](https://img.shields.io/badge/Telegram-Group-blue.svg)](https://t.me/+CsxZGItXdW40ZWVl)

[简体中文](README.md) | [English](README_EN.md)

</div>

---

> **📢 Join our [Telegram Group](https://t.me/+CsxZGItXdW40ZWVl). To be honest, I don't know why we need one, but everyone seems to want it, and others have it, so I must have one too!**

---

## ✨ Features

| Feature | Description |
|---|---|
| 🌍 **Multi-lang & Dual-Map** | Supports **Chinese/English/Arabic**; Auto-switches to **Google Maps** overseas, and AMap in China. |
| 🗺️ **Visual Map Selection** | Integrated AMap 3D, supports crosshair dragging, historical search, and favorites. |
| 🔀 **Route Planning System** | State-machine driven multi-point planning with undo, reset, and real-time preview. |
| 🕹️ **Virtual Joystick Control** | Real-time movement control via floating joystick in manual mode with smooth bearing transitions. |
| 🔄 **Auto-Loop Simulation** | Automatic back-and-forth movement along preset routes with customizable speeds (Walking, Running, Cycling, Driving). |
| 🛰️ **High-fidelity GPS Hijacking** | Hooks all levels of `Location` methods, integrated **step frequency simulation** and **satellite drift jitter** to avoid static detection. |
| 📶 **Wi-Fi Environment Cloning** | Real-time injection of real AP fingerprints (BSSID/SSID) around target coordinates using WiGLE API. |
| 🔵 **BLE Beacon Shielding** | Intercepts Bluetooth scans to prevent indoor location leaks via iBeacon etc. |
| 🏗️ **Cell Info Forgery** | Simulates Cell Location information to provide a complete geo-spoofing chain. |
| 🕵️ **Deep Anti-detection** | Erases `isMock` flags and AMap SDK internal mock detection, covering Android 13+ fields. |
| 🔐 **Secure CI/CD & APIs** | API keys are isolated via `local.yml` locally and injected via GitHub Actions Secrets during CI builds. |

---

## 🏛️ System Architecture

Adopts **MVVM** architecture combined with a **State-Machine** for complex route planning:

```
┌─────────────────────────────────────────┐
│            LocationSpoofer (App)         │
│  ┌──────────┐  ┌──────────────────────┐ │
│  │ Dual-Map │  │  RouteStateMachine   │ │
│  │(AMap/GMap)│  │  (IDLE/READY/RUN...) │ │
│  └────┬─────┘  └──────────┬───────────┘ │
│       │                   │             │
│  ┌────▼───────────────────▼───────────┐ │
│  │         SpooferProvider            │ │
│  │     (ContentProvider IPC Bridge)   │ │
│  └────────────────────────────────────┘ │
│  ┌──────────────────────────────────┐   │
│  │        SpoofingService           │   │
│  │    (Foreground Service & Engine)   │   │
│  └──────────────────────────────────┘   │
└─────────────────────────────────────────┘
              ↓ LSPosed Injection
┌─────────────────────────────────────────┐
│           Target App Process             │
│  ┌──────────────────────────────────┐   │
│  │         LocationHooker           │   │
│  │  • GPS/BDS/GLONASS Hijacking     │   │
│  │  • Wifi/Cell/Bluetooth Injection │   │
│  │  • Anti-Mock & SDK Bypass        │   │
│  └──────────────────────────────────┘   │
└─────────────────────────────────────────┘
```

---

## 📋 Requirements

- **Android 8.0 (API 26)** or higher
- [**KernelSU**](https://kernelsu.org) / Magisk (Root access required)
- [**LSPosed**](https://github.com/LSPosed/LSPosed) Framework
- Enable the module in LSPosed Manager and select target apps for spoofing.

---

## 🚀 Quick Start

### 1. Build and Install

```bash
# Clone the repository
git clone https://github.com/your-username/LocationSpoofer.git
# Build and install
./gradlew installDebug
```

### 2. Configuration

1. Grant Root access in **KernelSU**.
2. Activate the module in **LSPosed**, recommended apps:
   - WeChat (`com.tencent.mm`)
   - Chaoxing (`com.chaoxing.mobile`)
   - DingTalk (`com.alibaba.android.rimet`)
3. **Force Stop** the target app and reopen it.

### 3. Tips

- **Fixed Mode**: Select a point on the map and click "Start".
- **Route Mode**:
  1. Click "Route Planning", then tap points on the map.
  2. Choose "Manual (Joystick)" or "Auto Loop".
  3. Click "Start Simulation".

---

## 🛠️ Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Framework**: LSPosed / Xposed API 93
- **Map**: AMap 3DMap SDK / Google Maps SDK / FusedLocationProvider
- **Data**: Koin (DI), OkHttp 4, Coroutines Flow
- **Simulation**: TrajectorySimulator (Haversine Algorithm + Bearing Interpolation)

---

## ⚠️ Disclaimer

This project is for **educational and research purposes only**. Do not use this tool for any illegal activities (including but not limited to fraudulent clock-ins, cheating, etc.).
Users should comply with local laws and regulations. The author is not responsible for any account bans, legal liabilities, or losses caused by the use of this tool.

---

## 📜 License

This project is licensed under the [GNU General Public License v3.0](LICENSE).

```
Copyright (C) 2026 SuseOAA
```
