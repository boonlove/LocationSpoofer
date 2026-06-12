<div align="center">

<h1>LocationSpoofer</h1>

<p>基于 KernelSU + LSPosed 的高保真 Android 系统级虚拟定位与无线环境伪装模块</p>
<p>High-fidelity Android system-level location spoofing and wireless environment simulation module based on KernelSU + LSPosed</p>

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![KernelSU](https://img.shields.io/badge/Root-KernelSU-orange.svg)](https://kernelsu.org)
[![LSPosed](https://img.shields.io/badge/Framework-LSPosed-purple.svg)](https://github.com/LSPosed/LSPosed)
[![Telegram](https://img.shields.io/badge/Telegram-交流群-blue.svg)](https://t.me/+CsxZGItXdW40ZWVl)

[简体中文](README.md) | [English](README_EN.md)

</div>

---

> **📢 本项目由 [`HuangZhuoRui/LocationSpoofer`](https://github.com/HuangZhuoRui/LocationSpoofer) 分叉而来**。<br>
> **📢 加入我们的 [Telegram 交流群](https://t.me/+CsxZGItXdW40ZWVl) ，虽然我也不知道为什么要有一个TG群，但是大家都想要，而且别人也有，那我也要有！**

---

## ✨ 功能特性

| 功能模块 | 技术细节与核心优势 |
|---|---|
| 🌍 **多语言与双地图** | • 支持 **中文 (简体)**、**English**、**العربية** 语言界面。<br>• 国内设备自动使用高德 3D 地图，海外无缝切换至 **Google Maps** 进行可视化操作。 |
| 🗺️ **地图可视化选点** | • 集成高德 3D / 谷歌地图，支持十字准星拖拽微调、搜索历史以及常用位置收藏夹。 |
| 🔀 **路线规划系统** | • 状态机驱动的多路点路线规划，支持撤销、重置、起终点交换以及路线轨迹实时预览。 |
| 🕹️ **虚拟摇杆控制** | • 悬浮窗虚拟摇杆可实时微调模拟位置，支持平滑的方位角过渡与转向阻尼。 |
| 🔄 **自动循环模拟** | • 沿预设路线自动往返/循环运行。支持步行、跑步、骑行、驾车等多种档位速度，可实时调节。 |
| 🛰️ **高保真 GPS 劫持** | • Hook 系统 `Location` 全层级 API。<br>• 集成**步频物理模拟**（带正交方向抖动）与**高斯海拔起伏**，打破静态坐标检测。 |
| 📶 **Wi-Fi 环境克隆** | • 伪造当前连接的 Wi-Fi（BSSID/SSID/RSSI/频率/WiFi标准），按需拉取或自动生成周边 Wi-Fi 列表。<br>• 针对反作弊 SDK，采用**主流品牌路由器真实 OUI 前缀匹配**，非随机伪造。 |
| 🏗️ **基站信息伪造** | • 深度伪造 Cell Location，支持 GSM、WCDMA、CDMA、4G LTE 甚至 **5G NR 移动基站** 完整蜂窝指纹（MCC/MNC/LAC/CID/TAC/PCI/NCI）注入。 |
| 🔵 **BLE 蓝牙信标屏蔽** | • 拦截系统 BLE 扫描，防止 App 通过室内 iBeacon 或附近蓝牙设备定位技术泄露真实位置。 |
| 📍 **独立应用坐标系适配** | • **解决偏移痛点**：支持为目标 App 单独指定 `GCJ-02` (高德/腾讯)、`WGS-84` (GPS) 或 `BD-09` (百度) 坐标系。<br>• 集中式预计算，避免每次 Hook 产生高额三角函数计算开销。 |
| 🕵️‍♂️ **实地环境扫街采集** | • **实景克隆**：支持开启背景扫描，自动记录移动路径下的真实基站、Wi-Fi 及蓝牙特征。<br>• 采集数据自动存储于 Room 本地数据库，支持在地图上以热力图展示覆盖范围。<br>• 支持 **JSON 导入/导出**，用户可一键分享或备份特定物理空间的无线电环境。 |
| 🛡️ **深度反检测机制** | • **堆栈清洗**：动态拦截 `Throwable.getStackTrace` 并抹除所有与 Xposed/LSPosed 相关的调用帧。<br>• **防 Mock 抹除**：彻底清除 API 中的 `isMock` 标志位，覆盖 Android 13+ 底层 `mMock` 内部字段。<br>• **接口隐藏**：隐藏 LocationManager 中的 "mock" / "test" 提供者名，强制将其汇报为原生 "gps" 信号。 |

---

## 🏛️ 系统架构

本项目采用 **MVVM** 架构，并利用 Root 权限规避了 Android 11+ 的沙盒可见性隔离，实现零权限跨进程配置传递：

```
┌─────────────────────────────────────────────┐
│            LocationSpoofer (宿主 App)       │
│  ┌──────────┐  ┌──────────────────────────┐ │
│  │ Dual-Map │  │    RouteStateMachine     │ │
│  │(高德/谷歌)│  │    (IDLE/READY/RUN...)   │ │
│  └────┬─────┘  └────────────┬─────────────┘ │
│       │                     │               │
│  ┌────▼─────────────────────▼─────────────┐ │
│  │            ConfigManager                 │ │
│  │  (将配置序列化，通过 Root 权限写入 Temp)   │ │
│  └──────────────────┬───────────────────────┘ │
│  ┌──────────────────▼─────────────────────┐ │
│  │           SpoofingService               │ │
│  │       (前台通知服务 & 轨迹计算引擎)       │ │
│  └────────────────────────────────────────┘ │
└─────────────────────┬───────────────────────┘
                      │ (写入配置 JSON)
                      ▼
        ┌───────────────────────────┐
        │ /data/local/tmp/ 配置文件  │
        │    (chmod 777 + chcon)    │
        └─────────────┬─────────────┘
                      │ (读取配置，800ms 内存缓存)
                      ▼ LSPosed 注入
┌─────────────────────────────────────────────┐
│              目标 App 进程                  │
│  ┌────────────────────────────────────────┐  │
│  │            LocationHooker              │  │
│  │  • Location API / Baidu / Tencent SDK  │  │
│  │  • WiFi & 蜂窝基站 (2G-5G NR) 环境注入    │  │
│  │  • 蓝牙 BLE 扫描过滤                    │  │
│  │  • Anti-Mock & Xposed 堆栈清洗反检测   │  │
│  └────────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
```

> [!NOTE]
> **关于跨进程通信 (IPC) 的设计决策**：
> 目标 App 进程在沙盒内运行时，无法通过 `ContentProvider` 直接访问外部宿主 App（常因 Android 11+ 包可见性及 SELinux 策略导致 `Failed to find provider info` 错误）。
> 因此，宿主 App 借助 Root 权限将配置以 JSON 格式写入 `/data/local/tmp/locationspoofer_config.json`，并赋予 `777` 权限及 `shell_data_file` SELinux 上下文。所有沙盒内注入的目标 App 均可直接免权限同步读取，极大提升了跨进程的稳定性与兼容性。

---

## 📋 环境要求

- **Android 8.0 (API 26)** 及以上系统
- 已获取 Root 权限，推荐使用 [**KernelSU**](https://kernelsu.org) / APatch / Magisk
- 已安装 [**LSPosed**](https://github.com/LSPosed/LSPosed) 框架
- 在 LSPosed 管理器中启用本模块，并勾选需要进行定位伪装的目标应用

---

## 🚀 快速开始

### 1. 编译与安装

```bash
# 克隆仓库
git clone https://github.com/your-username/LocationSpoofer.git

# 编译并安装到设备
./gradlew installDebug
```

### 2. 基础配置指南

1. 启动应用，在 **KernelSU** / Magisk 管理器中授予其 Root 权限（用于管理本地 JSON 配置文件）。
2. 在 **LSPosed** 管理器中激活此模块，并勾选你需要伪装的目标应用，例如：
   - 微信 (`com.tencent.mm`)
   - 超星学习通 (`com.chaoxing.mobile`)
   - 钉钉 (`com.alibaba.android.rimet`)
3. 激活后，**强制停止**目标应用并重新打开以使其生效。

### 3. 使用技巧

- **定点伪装**：在地图上拖动或通过上方搜索栏搜索地点，点击“启动模拟”即可。
- **路线模拟**：
  1. 点击地图下方的“路线规划”进入规划模式，在地图上依次点击放置多个路点。
  2. 选定移动速度（步行、跑步、骑行、驾车）。
  3. 点击“开始模拟”，可切换至自动循环或使用手动摇杆控制移动。
- **防止偏移**：如果发现微信、学习通等应用定位产生几百米偏移，请进入应用主页，点击右上角进入 **坐标系适配**，为对应应用单独将默认的 `GCJ-02` 坐标系切换为 `WGS-84` (原生 GPS 坐标系) 或 `BD-09` (百度地图坐标系) 即可。
- **Wi-Fi 与基站伪装**：
  - **云端查询模式**：可配合 WiGLE 开发者 Token，自动拉取指定定位周边的真实 Wi-Fi 热点。
  - **实地扫街模式**：点击“环境扫街采集”并在物理世界中移动，本应用会自动在背景记录真实的无线网络和基站信息。之后定位至该点时，可完美重现当时的无线信号指纹。

---

## 🛠️ 技术栈

- **开发语言**: Kotlin (100%)
- **界面开发**: Jetpack Compose + Material Design 3
- **依赖注入**: Koin
- **数据持久化**: Room Database (SQLite)
- **Xposed 框架**: LSPosed API 93 / libxposed
- **地图服务**: AMap 3DMap SDK (中国大陆) / Google Maps SDK & Google Places SDK (海外)
- **模拟算法**: TrajectorySimulator (Haversine 距离算法 + 航向角平滑插值 + 步数正交抖动)

---

## 🎁 鸣谢

- [LocationSpoofer](https://github.com/HuangZhuoRui/LocationSpoofer): 上游

---

## ⚠️ 免责声明

本项目**仅供学习研究、技术交流以及个人合法合规测试使用**。
请勿将本工具用于任何违法违规或违反服务协议的活动（包括但不限于虚假打卡、考试作弊、商业欺诈等）。
使用本模块造成的任何账号封禁、数据丢失、法律纠纷或其他直接/间接损失，均由使用者自行承担，作者不对此承担任何责任。

---

## 📜 开源许可

本项目采用 [GNU General Public License v3.0](LICENSE) 开源许可证。

```
Copyright (C) 2026 SuseOAA
```
