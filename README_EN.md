<div align="center">

<h1>LocationSpoofer</h1>

<p>High-fidelity Android system-level location spoofing and wireless environment simulation module based on KernelSU + LSPosed</p>

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
[![Android CI status](https://github.com/boonlove/LocationSpoofer/actions/workflows/android.yml/badge.svg)](https://github.com/boonlove/LocationSpoofer/actions)
[![Downloads](https://img.shields.io/github/downloads/boonlove/LocationSpoofer/total.svg)](https://github.com/boonlove/LocationSpoofer/releases)
[![Latest Release Downloads](https://img.shields.io/github/downloads/boonlove/LocationSpoofer/latest/total.svg)](https://github.com/boonlove/LocationSpoofer/releases/latest)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![KernelSU](https://img.shields.io/badge/Root-KernelSU-orange.svg)](https://kernelsu.org)
[![LSPosed](https://img.shields.io/badge/Framework-LSPosed-purple.svg)](https://github.com/LSPosed/LSPosed)
[![Telegram](https://img.shields.io/badge/Telegram-Group-blue.svg)](https://t.me/+CsxZGItXdW40ZWVl)

[简体中文](README.md) | [English](README_EN.md)

</div>

---

> **📢 This project forked from [`HuangZhuoRui/LocationSpoofer`](https://github.com/HuangZhuoRui/LocationSpoofer).**<br>
> **📢 Join our [Telegram Group](https://t.me/+CsxZGItXdW40ZWVl) for** ~~technical discussion, updates,~~ **bragging and shitposting.**
>
> **📖 Read the [LocationSpoofer Detailed Tutorial](https://docs.google.com/document/d/1fFEz3k7ATdN2dwY1L3RJn1QuzgokIsslNa88-vUPxPk/edit?usp=sharing)**

---

## 📖 Introduction

In modern Android risk control environments, the standard developer options feature "Mock Location" has long been flagged as a high-risk indicator by major anti-cheat SDKs (such as AMap risk control, Tencent Security, NetEase EasyShield, etc.). These SDKs do not merely verify the standard `isFromMockProvider` API flag; they also gather environmental signals such as:

*   **Surrounding Wi-Fi BSSID lists**
*   **Cellular tower cell IDs (cellular fingerprints)**
*   **Local BLE beacons**
*   Furthermore, they perform Fast Fourier Transform (FFT) analysis on location coordinate sequences to detect artificial static coordinates or deterministic linear trajectory patterns.

**LocationSpoofer** is a **system-level virtual positioning and radio environment cloning solution** designed specifically to counter these deep anti-cheating mechanisms. 
By leveraging **KernelSU / Magisk / APatch** for root privileges and the **LSPosed (libxposed)** framework to inject hook routines into targeted processes, LocationSpoofer intercepts and fakes all positioning and wireless networking API responses with high physical fidelity. This ensures the target apps receive highly consistent location fingerprints without detecting any virtualization.

> [!TIP]
> **🌟 Major Update in Latest Version**
> 
> The latest version adds more complete **Wi-Fi environment simulation** and **cell tower simulation**. It can build realistic radio fingerprints around the spoofed coordinates from local street-scan records, WiGLE cloud Wi-Fi data, and OpenCellID cell tower data, then inject consistent responses into target app processes through `WifiManager`, `TelephonyManager`, `PhoneStateListener`, and `TelephonyCallback` APIs.

---

## ✨ Core Features & Technical Deep Dive

### 1. 🌐 Map Engine & Adaptive Coordinate Translator
* **Map Swapping**: Automatically boots the AMap 3D SDK or Baidu Maps SDK on devices located within Mainland China, and switches to Google Maps & Places SDK overseas for visual crosshair adjustments and route planning.
* **Per-App Coordinate System Adapter**: Different applications (such as WeChat, XuexiTong, Baidu Maps, etc.) expect different coordinate systems. Throwing raw GCJ-02 coordinates directly to Baidu Maps causes a fixed offset of 300-500 meters. LocationSpoofer lets you specify `GCJ-02` (Mars Coordinates), `WGS-84` (Standard GPS Coordinates), or `BD-09` (Baidu Coordinates) on a per-app basis.
* **Zero-Latency Calculations**: Coordinate translation is computed on-the-fly inside the Xposed hooks by fetching pre-calculated values from the host app, bypassing expensive trigonometric calls in high-frequency callback contexts.

### 2. 🛰️ High-Fidelity GPS Physics Engine with Random Walks
Raw GPS receivers output coordinates that naturally contain Gaussian white noise due to ionospheric scintillations, multipath propagation, and clock offsets. Static or straight-line mock sequences are easily identified by FFT spectrum analysis.
* **Ornstein-Uhlenbeck Process**: We implement a mathematical model of physical random walks to generate natural location jitters:
  $$\mathrm{d}X_t = -\alpha X_t \mathrm{d}t + \sigma \mathrm{d}W_t$$
  where $\sigma$ is the jitter intensity, and $\alpha$ is the mean-reversion coefficient (configured to `0.05` to pull the drift back towards the true coordinate by 5% every second). This creates realistic low-frequency slowly-varying drift characteristics while keeping the displacement bounded within 3-Sigma limits (hard-clamped to a maximum of 4 meters) to prevent location jumps.
* **Gait Lateral Jitter**: When walking or running, the engine automatically calculates step-frequency statistical intervals and applies a perpendicular lateral displacement of `0.15 * N(0,1)` meters. This perfectly simulates the natural left-right swaying motion of a walking human.
* **Altitude & Accuracy (GDOP) Drift**: Horizontal accuracy (Accuracy) and vertical elevation (Altitude) are no longer locked as static numbers. They fluctuate slowly in a Brownian motion pattern to simulate tropospheric delay changes and changes in satellite geometries.

### 3. 🛡️ Stealth & Anti-Detection Suite
* **Deep Call Stack Cleaning**: Intercepts `Throwable.getStackTrace` and `Thread.getStackTrace` to scan stack frames. Any calling frames referencing `de.robv.android.xposed`, `io.github.libxposed`, or `lsposed` are dynamically expunged to prevent SDK trace detections.
* **Classloader Isolation**: Hooks `Class.forName` and `ClassLoader.loadClass` to throw a `ClassNotFoundException` whenever an application attempts to probe for Xposed classes.
* **Mock Flag Eraser**:
  * Forces `Location.isFromMockProvider()` and `Location.isMock()` to always return `false`.
  * Reflectively overwrites the private internal fields `mMock` and `mIsFromMockProvider` in the `Location` class to `false` (Android 12/13+ compatibility), while wiping custom mock values from the location Extras Bundle.
  * Intercepts `AppOpsManager`'s `OP_MOCK_LOCATION (58)` operation checks and forces a return value of `MODE_IGNORED (1)`.
  * Hooks secure settings queries (e.g. `mock_location`, `allow_mock_location`) in `Settings.Secure` to return `0` (disabled status).
  * Replaces test/mock providers in `LocationManager` and forces them to report as native `gps` signals.

### 4. 📶 Radio Environment Cloning & Spatial Heatmap Interpolation
Anti-cheat engines compare your GPS coordinates against the Wi-Fi scan results reported by your device. If you are virtually located in Beijing, but your device reports Wi-Fi BSSIDs from your home in Shanghai, you will be flagged immediately.
* **On-Site Environment Scanner**: Background sweep utility collects and logs Wi-Fi networks (SSID/BSSID/RSSI/frequency/channel/WiFi standard), Cell Towers (GSM, WCDMA, CDMA, LTE, and 5G NR configurations containing MCC/MNC/LAC/CID/TAC/PCI/NCI and signal dbm), and BLE Bluetooth beacons.
* **Spatial Inverse Distance Weighting (IDW) Interpolation**: During virtual movements, the Xposed module searches the local Room SQLite DB for physical records within a 50-meter radius of the target coordinates. It computes weights based on the inverse square distance:
  $$w_i = \frac{1}{d_i^2}$$
  to interpolate nearby Wi-Fi RSSI, Cell signal strength dbm, and BLE Bluetooth RSSI. As you move, the signals dynamically fade and strengthen in a smooth gradient, avoiding abrupt jumps that trigger fraud alerts.
* **Wi-Fi Scan and Connection-State Simulation**: Hooks `WifiManager.getScanResults()`, `getConnectionInfo()`, `getConfiguredNetworks()`, `getDhcpInfo()`, and related APIs to return coordinate-matched SSID/BSSID/RSSI/frequency/channel/capability data, along with consistent Wi-Fi enabled state, connected network, gateway, and DHCP details.
* **Brand OUI Prefix Matching**: When generating fake scans in non-recorded zones, the generator assigns real MAC prefixes (OUI) belonging to mainstream network manufacturers (e.g. TP-Link, Huawei, ZTE, Xiaomi, Cisco, Netgear) instead of random MAC addresses.
* **Cloud WiGLE API Integration**: Integrates WiGLE developer API tokens to pull real Wi-Fi network coordinates around the spoofed latitude and longitude in real-time, then stores them locally for later offline replay.
* **OpenCellID Cell Tower Import**: Supports OpenCellID API keys to query real nearby cellular towers for the target coordinates, with GCJ-02 to WGS-84 coordinate conversion, bbox expansion, field normalization, and local caching.
* **Cellular and Carrier API Simulation**: Covers `TelephonyManager.getAllCellInfo()`, `getCellLocation()`, `getNetworkOperator()`, `getServiceState()`, `getSignalStrength()`, `listen()`, `requestCellInfoUpdate()`, `registerTelephonyCallback()`, and related read paths. It supports GSM/WCDMA/LTE/NR identities, MCC/MNC/LAC/CID/TAC/PCI/NCI, RSRP/dbm signal strength, and carrier name spoofing. When cloud data lacks LTE/NR records, the module prepends a synthetic LTE primary cell for apps that only inspect 4G cell information.

### 5. 🛰️ Satellite Sky Matrix & NMEA Protocol Generator
* **GNSS Status Hijacking**: Hooks `GnssStatus` to simulate a fully populated constellation of 20+ active satellites (GPS, BeiDou, GLONASS) detailing unique PRN IDs, signal-to-noise ratios (CNR/SNR), elevations, azimuths, and Used-In-Fix status flags.
* **Dynamic NMEA Naming & Calculations**: Intercepts `OnNmeaMessageListener` and constructs matching raw NMEA-0183 sentences (such as `\$GPGGA`, `\$GPRMC`, `\$GPGSA`, `\$GPGSV`) in memory based on current coordinates, velocities, and bearing inputs, calculating the proper Checksum to bypass deep hardware queries.

### 6. 🔀 Smart Route Navigation & Traffic Light Waits
* **Road Network Snapping**: Fits custom multi-point routes to physical roads using routing APIs, preventing straight-line navigation through buildings.
* **Traffic Light Simulator**: Analyzes route segments and populates nodes with traffic light tags. The route simulator will **automatically pause for 15 seconds** at these coordinates to replicate real driving waits.
* **Compose Floating Joystick**: A float window containing an interactive joystick overlay to fine-tune coords on-the-fly. Leverages smooth bearing transitions and steering damping adjustments.

---

## 🏛️ System Architecture

This project is built on the **MVVM** architecture, implementing a custom Root-privileged IPC mechanism to bypass sandboxing restrictions and package visibility limits on Android 11+:

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
                      │ (Reads Config, Daemon Thread 1000ms Cache)
                      ▼ LSPosed Injection
┌─────────────────────────────────────────────┐
│              Target App Process             │
│  ┌────────────────────────────────────────┐  │
│  │            LocationHooker              │  │
│  │  • Location API / Baidu / Tencent SDK  │  │
│  │  • WiFi & Cellular (2G-5G NR) Injection│  │
│  │  • Bluetooth BLE Scan Filtering        │  │
│  │  • Anti-Mock & Xposed Stack Cleaning   │  │
│  │  • GnssStatus Satellite & NMEA Mocking │  │
│  └────────────────────────────────────────]  │
└─────────────────────────────────────────────┘
```

> [!NOTE]
> **IPC Design Decisions**:
> Sandboxed app processes cannot query a custom `ContentProvider` on Android 11+ due to package visibility rules and SELinux isolation, resulting in slow responses or `Failed to find provider info` crashes.
> To address this, the host app uses root shell permissions to write configuration parameters to `/data/local/tmp/locationspoofer_config.json`, changing permissions to `777` and applying the `shell_data_file` SELinux context.
> The sandboxed module's `LocationHooker` launches a **background daemon thread** that polls the file every 1000ms and updates a volatile in-memory cache. The main thread hooks fetch settings from memory with 0-IO latency, completely preventing UI drop-frames.

---

## 📋 Requirements

* **OS Version**: Android 8.0 (API 26) or higher.
* **Root Manager**: Root access is required (recommended: [**KernelSU**](https://kernelsu.org) / APatch / Magisk).
* **Xposed Hook framework**: Installed and enabled [**LSPosed**](https://github.com/LSPosed/LSPosed) (or fork variants).

---

## 🚀 Quick Start

### 1. Build & Install

```bash
# Clone the repository
git clone https://github.com/your-username/LocationSpoofer.git

# Build and install the Debug APK directly to your device
./gradlew installDebug
```

### 2. Module Activation
1. Launch **KernelSU / Magisk / APatch** and grant Root permissions to LocationSpoofer.
2. Open **LSPosed Manager**, find **LocationSpoofer** in the modules tab, and **enable it**.
3. Under the module's scope, **check the target apps** you wish to spoof (e.g. WeChat, DingTalk, XuexiTong).
4. **Force stop** target apps or reboot your phone to apply the hooks.

### 3. Usage Best Practices

#### 💻 Fixed Point Mocking
1. Launch LocationSpoofer, tap/drag the crosshair on the map, or use the search bar to find target locations.
2. In the bottom drawer, enable spoofing options: **Mock Wi-Fi**, **Mock Cell Tower**, **Mock Bluetooth**, and **Enable Jitter**.
3. Tap "Start Simulation" to take over standard system GPS feeds.

#### 🚗 Road-snapped Loop Navigation
1. Tap "Route Planning" at the bottom of the map page and mark waypoints on the road.
2. Select "Loop (Auto)" mode, select your travel speed class (Walking, Running, Cycling, Driving, or custom speed value).
3. **Turn on "Use Real Route Planning"** (pulls snapping curves and inserts traffic light stops).
4. Tap "Save Route" to save it for future one-tap reuse.
5. Tap "Start Simulation". You can toggle the floating joystick from the main page to adjust positions and speeds on-the-fly.

#### 🕵️‍♂️ Signal Scanning & Spatial Replay
1. Before walking outdoors, open Settings -> toggle **"Environment Map & Street Scan"** scanning mode.
2. The scanner will run in the background, logging physical Wi-Fi/Cell/Bluetooth signals into local Room tables.
3. You can review collected points in the management page, edit tags, or **export as a JSON file** to share with others.
4. When spoofing coords later, the engine uses **IDW interpolation** to replay the exact radio fingerprint transitions.

#### 📍 Fixing Per-App Map Offsets
* If WeChat, XuexiTong, or maps show a static 300-500 meters shift:
  * Go to Settings -> Tap **"Configure App Coordinate System"**.
  * Add the package name of the target app.
  * Change its coordinate standard from default GCJ-02 to `WGS-84` or `BD-09`. The hooks translate coordinates automatically.

---

## 🛠️ Tech Stack

* **Language**: 100% Kotlin
* **UI**: Jetpack Compose & Material Design 3
* **Dependency Injection**: Koin
* **Local Storage**: Room Database (SQLite)
* **Networking**: OkHttp 3 & Kotlinx Serialization
* **Map SDKs**: AMap 3DMap SDK (China Mainland) / Google Maps & Places SDK (Overseas)
* **Xposed Hooking**: LSPosed API 93 / libxposed (Service mode)

---

## 🎁 Credit

- [LocationSpoofer](https://github.com/HuangZhuoRui/LocationSpoofer): upstream

---

## ⚠️ Disclaimer

This program is intended **solely for educational, academic, and developer testing purposes** (such as debugging coordinate-dependent apps).
Do not use this tool for any illegal activities or violations of third-party agreements (including fake attendance checks, exam cheating, commercial fraud, etc.).
The author is not responsible for any banned accounts, data losses, legal issues, or other direct/indirect damages arising from the use of this software.

---

## 📜 License

This project is licensed under the [GNU General Public License v3.0](LICENSE).

```
Copyright (C) 2026 SuseOAA
```
