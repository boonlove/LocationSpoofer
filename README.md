<div align="center">

<h1>LocationSpoofer</h1>

<p>基于 KernelSU + LSPosed 的高保真 Android 系统级虚拟定位与无线环境伪装模块</p>
<p>High-fidelity Android system-level location spoofing and wireless environment simulation module based on KernelSU + LSPosed</p>

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
[![Android CI status](https://github.com/boonlove/LocationSpoofer/actions/workflows/android.yml/badge.svg)](https://github.com/boonlove/LocationSpoofer/actions)
[![Downloads](https://img.shields.io/github/downloads/boonlove/LocationSpoofer/total.svg)](https://github.com/boonlove/LocationSpoofer/releases)
[![Latest Release Downloads](https://img.shields.io/github/downloads/boonlove/LocationSpoofer/latest/total.svg)](https://github.com/boonlove/LocationSpoofer/releases/latest)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![KernelSU](https://img.shields.io/badge/Root-KernelSU-orange.svg)](https://kernelsu.org)
[![LSPosed](https://img.shields.io/badge/Framework-LSPosed-purple.svg)](https://github.com/LSPosed/LSPosed)
[![Telegram](https://img.shields.io/badge/Telegram-交流群-blue.svg)](https://t.me/+CsxZGItXdW40ZWVl)

[简体中文](README.md) | [English](README_EN.md)

</div>

---

> **📢 本项目由 [`HuangZhuoRui/LocationSpoofer`](https://github.com/HuangZhuoRui/LocationSpoofer) 分叉而来**。<br>
> **📢 加入我们的 [Telegram 交流群](https://t.me/+CsxZGItXdW40ZWVl) 进行**~~技术探讨、催更、~~**吹牛逼、搞抽象。**
>
> **📖 查看 [LocationSpoofer 详细使用教程](https://docs.google.com/document/d/1fFEz3k7ATdN2dwY1L3RJn1QuzgokIsslNa88-vUPxPk/edit?usp=sharing)**

---

## 📖 项目简介

在现代 Android 系统的风控环境中，传统的“模拟位置（Mock Location）”开发者选项早已被各类反作弊 SDK（如高德风控、腾讯安全、网易易盾等）列为高风险特征。它们不仅能够轻松检测到 `isFromMockProvider` 标志，还会通过收集周围的以下信息来识别异常：

*   **Wi-Fi BSSID 列表**
*   **移动基站蜂窝小区的蜂窝指纹**
*   **附近 BLE 蓝牙信标**
*   甚至通过对定位数据序列进行傅里叶变换（FFT）来识别规律的静态或线性模拟轨迹。

**LocationSpoofer** 是专为应对此类高强度风控而设计的**系统级虚拟定位与无线电环境克隆方案**。
它基于 **KernelSU / Magisk / APatch** 获取底层 Root 权限，并利用 **LSPosed (libxposed)** 框架注入目标进程，以极高的物理契合度拦截并伪造所有与定位、网络环境相关的底层 API 响应，确保应用在无法察觉的情况下获取高度一致的虚假位置指纹。

> [!TIP]
> **🌟 最新版本重要更新**
> 
> 新增了更完整的 **Wi-Fi 环境模拟** 与 **移动基站模拟**：可从本地扫街数据、WiGLE 云端热点数据以及 OpenCellID 基站数据中构建目标坐标附近的真实无线电指纹，并在目标 App 进程内同步伪造 `WifiManager`、`TelephonyManager`、`PhoneStateListener` 与 `TelephonyCallback` 等接口返回，让位置、Wi-Fi、运营商、基站小区与信号强度保持一致。

---

## ✨ 核心特性与技术内幕

### 1. 🌐 地图引擎与自适应坐标系翻译器
* **地图无缝切换**：国内设备自动初始化高德或者百度 3D 地图，海外设备自动切换至 Google Maps 进行路线规划与十字准星微调。
* **独立应用坐标系适配**：微信、学习通、百度地图等不同应用对定位接口的期望坐标系不同。若直接传入 GCJ-02 会在百度地图上产生数百米偏移。LocationSpoofer 支持为每个目标 App 单独指定 `GCJ-02` (火星坐标)、`WGS-84` (GPS 原始坐标) 或 `BD-09` (百度坐标)。
* **零延迟翻译**：在 Xposed 挂钩时，直接从主进程预先计算好的经纬度数据读取，规避高频 Hook 场景下的三角函数开销。

### 2. 🛰️ 高保真 GPS 物理引擎与随机游走抖动
真实 GPS 芯片输出 of coordinates 因电离层闪烁、多径效应及接收机噪声，天然具有高斯分布的白噪声。若返回静态坐标或机械的直线坐标，极易被反作弊检测。
* **Ornstein-Uhlenbeck 随机过程**：我们引入了高斯随机游走物理模型来生成自然位置抖动，其状态方程定义为：
  $$\mathrm{d}X_t = -\alpha X_t \mathrm{d}t + \sigma \mathrm{d}W_t$$
  其中 $\sigma$ 为漂移强度，$\alpha$ 为均值回归系数（本模块设定为 `0.05`，即每秒将当前漂移拉回 5%）。它既产生了符合物理规律的低频缓慢漂移，又使漂移量在 3-Sigma 原则下严格有界（硬性限制在 4 米内），防止漂移无限发散引起“位置瞬移”告警。
* **步频横向抖动（Gait Jitter）**：当处于步行或跑步模式时，引擎自动沿当前移动方向的正交横向上，根据步频周期的统计概率施加 `0.15 * N(0,1)` 米的高斯横向位移，完美模拟真实人行时身体左右晃动的步态特征。
* **海拔高度与精度（GDOP）慢漂移**：精度值（Accuracy）及海拔高度（Altitude）不再是死板的常量，而是在基准值附近进行布朗运动式的缓慢波动，模拟大气对流层延迟与卫星几何分布的自然变化。

### 3. 🛡️ 全方位反检测套件（Stealth & Anti-Detection）
* **深度调用栈清洗（Stack Traces Scrubbing）**：动态拦截 `Throwable.getStackTrace` 和 `Thread.getStackTrace`，一旦发现调用栈中包含 `de.robv.android.xposed`、`io.github.libxposed`、`lsposed` 等敏感调用帧，自动抹除，让反作弊 SDK 无法在调用链回溯中发现 Xposed 框架。
* **类加载隔离**：Hook `Class.forName` 和 `ClassLoader.loadClass`，对于上述敏感类名的查询直接拦截并抛出 `ClassNotFoundException`。
* **Mock 属性彻底抹除**：
  * 将 `Location.isFromMockProvider()` 和 `Location.isMock()` 的返回值永久强制覆写为 `false`。
  * 针对 Android 12/13+ 系统，通过反射强制将 `Location` 内部私有字段 `mMock` 和 `mIsFromMockProvider` 重写为 `false`，并移除 Extra Bundle 中可能残留的 `mockLocation` 标记。
  * 拦截 `AppOpsManager` 的 `OP_MOCK_LOCATION (58)` 权限查询，强制返回 `MODE_IGNORED (1)`，令目标 App 认为系统没有向任何软件授权模拟位置。
  * 拦截 `Settings.Secure` 中 `mock_location` 及 `allow_mock_location` 的读取，返回 `0`（关闭状态）。
  * 隐藏 `LocationManager` 中的虚拟 Test Provider，将其一律重命名并伪装为系统的原生 `gps` 提供者。

### 4. 📶 无线电环境克隆与空间热力插值引擎
大多数反作弊 SDK 会采集周边的无线电指纹。如果你定位到北京，但手机扫出来的 Wi-Fi 列表全在你上海的家里，瞬间就会被标记异常。
* **实地扫街扫描器（EnvironmentScanner）**：支持在背景运行扫描，以 10s 间隔自动保存你真实走过的物理世界中的 Wi-Fi 接入点（SSID/BSSID/RSSI/频率/信道/Wi-Fi标准）、基站小区信息（GSM, WCDMA, CDMA, LTE, 甚至是 5G NR 的完整蜂窝指纹 MCC/MNC/LAC/CID/TAC/PCI/NCI 及 dbm 信号强度）以及附近 BLE 蓝牙信标。
* **空间反距离加权（IDW）插值**：当虚拟定位在地图上运动时，系统会在 Room 本地数据库中检索 50 米范围内的历史采集点。使用反距离平方比作为权重：
  $$w_i = \frac{1}{d_i^2}$$
  对周边所有 Wi-Fi RSSI 信号强度、蜂窝 dbm 级蓝牙 RSSI 进行动态差值计算。这使手机在虚拟移动时，Wi-Fi 信号会在后台平滑衰减和增强，模拟最真实的信号过渡，绝无指纹突变带来的安全隐患。
* **Wi-Fi 扫描与连接态模拟**：Hook `WifiManager.getScanResults()`、`getConnectionInfo()`、`getConfiguredNetworks()`、`getDhcpInfo()` 等接口，按目标坐标返回伪造的 SSID/BSSID/RSSI/频率/信道/加密能力，同时同步 Wi-Fi 开关状态、连接网络、网关与 DHCP 信息。
* **真实品牌 OUI 前缀匹配**：在无本地采集数据的空白区域，系统采用 TP-Link、Huawei、ZTE、Xiaomi、Cisco 等真实中国主流品牌路由器的合法 MAC 前缀（Organizationally Unique Identifier）生成虚拟 Wi-Fi BSSID，拒绝因随机生成非法 MAC 而被反作弊厂商识破。
* **云端联合伪装 (WiGLE API)**：可配置 WiGLE API 密钥，实时在线拉取全球指定经纬度周围真实物理存在的 Wi-Fi 热点，并写入本地环境数据库，后续可离线复用。
* **OpenCellID 基站数据导入**：可配置 OpenCellID API Key，按目标坐标查询附近真实蜂窝小区，自动完成 GCJ-02 到 WGS-84 坐标转换、bbox 扩展查询、字段归一化与本地缓存。
* **移动基站与运营商接口模拟**：覆盖 `TelephonyManager.getAllCellInfo()`、`getCellLocation()`、`getNetworkOperator()`、`getServiceState()`、`getSignalStrength()`、`listen()`、`requestCellInfoUpdate()`、`registerTelephonyCallback()` 等读取路径，支持 GSM/WCDMA/LTE/NR 小区信息、MCC/MNC/LAC/CID/TAC/PCI/NCI、RSRP/dbm 信号强度与运营商名称模拟；当云端数据缺少 LTE/NR 时，会补充合成 LTE 主小区以兼容只读取 4G 的检测软件。

### 5. 🛰️ 卫星矩阵与 NMEA 协议生成器
* **GNSS Status 劫持**：Hook 系统的 `GnssStatus` 类，模拟多达 20+ 颗包括 GPS、北斗、GLONASS 的卫星分布矩阵，注入真实的 PRN 标识、信噪比（CNR）、俯仰角、方位角等，并正确汇报 `usedInFix`（参与定位计算）状态。
* **NMEA 语句流动态拼装**：劫持 `OnNmeaMessageListener`。根据当前的模拟位置、航向角、速度和模拟出的卫星，在内存中动态组装符合国家标准的原始 `\$GPGGA`, `\$GPRMC`, `\$GPGSA`, `\$GPGSV` 语句并计算校验和（Checksum）实时输出，满足对底层 NMEA 信号进行硬核校验的 App。

### 6. 🔀 智能路线导航与红绿灯停候系统
* **真实物理路网拟合**：支持点对点或多路点航线设计。开启“使用真实路线”后，系统将通过路径搜索算法拟合实际道路轮廓，防止轨迹穿墙或走直线。
* **红绿灯智能识别**：路线规划成功后，引擎会自动解析路段中的红绿灯总数，并随机分发至折线拐角处。当模拟器运行到红绿灯点时，会**自动停驻 15 秒**再重新加速，完美模拟真实的车辆行车特征。
* **悬浮窗摇杆**：提供悬浮窗虚拟摇杆，可在前台直接手动微调坐标。支持根据摇杆拉伸幅度在 0 ~ 10 m/s 之间平滑过渡，并在转向时应用航向角阻尼。

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
                      │ (读取配置，守护线程 1000ms 缓存)
                      ▼ LSPosed 注入
┌─────────────────────────────────────────────┐
│              目标 App 进程                  │
│  ┌────────────────────────────────────────┐  │
│  │            LocationHooker              │  │
│  │  • Location API / Baidu / Tencent SDK  │  │
│  │  • WiFi & 蜂窝基站 (2G-5G NR) 环境注入    │  │
│  │  • 蓝牙 BLE 扫描过滤                    │  │
│  │  • Anti-Mock & Xposed 堆栈清洗反检测   │  │
│  │  • GnssStatus 卫星 & NMEA 模拟          │  │
│  └────────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
```

> [!NOTE]
> **关于跨进程通信 (IPC) 的设计决策**：
> 目标 App 进程在沙盒内运行时，由于 Android 11+ 包可见性及 SELinux 策略，若使用 `ContentProvider` 会导致主线程卡顿并产生 `Failed to find provider info` 错误。
> 宿主 App 借助 Root 权限将配置以 JSON 格式写入 `/data/local/tmp/locationspoofer_config.json`，并赋予 `777` 权限及 `shell_data_file` SELinux 上下文。
> 目标沙盒内的 `LocationHooker` 启动一个**后台守护线程**，每 1000ms 异步读取文件并存储在 Volatile 内存中。主线程的 Hook 方法读取配置时永远是 0-IO 延迟，彻底杜绝了因频繁读取配置导致的目标 App 界面丢帧与卡顿。

---

## 📋 环境要求

* **系统版本**：Android 8.0 (API 26) 及以上
* **Root 管理器**：已获取 Root 权限，强烈推荐使用 [**KernelSU**](https://kernelsu.org) / APatch，亦支持 Magisk。
* **Xposed 框架**：已安装并激活 [**LSPosed**](https://github.com/LSPosed/LSPosed)（或较新版本的 LSPosed 支线版本）。

---

## 🚀 快速开始

### 1. 编译与安装

```bash
# 克隆本仓库到本地
git clone https://github.com/your-username/LocationSpoofer.git

# 编译 Debug 版本并直接安装至设备
./gradlew installDebug
```

### 2. 权限与激活步骤
1. 打开 **KernelSU / Magisk / APatch** 管理器，授予 LocationSpoofer **Root 权限**（用于在 `/data/local/tmp` 写入共享配置 JSON 文件）。
2. 打开 **LSPosed** 管理器，找到 LocationSpoofer 模块，将其勾选**启用**。
3. 在模块的作用域（Scope）中，**勾选需要进行定位伪装的目标应用**（如微信、钉钉、超星学习通等）。
4. **强行停止**勾选的目标 App（或重启手机）以使其加载 Xposed 挂钩逻辑。

### 3. 使用场景最佳实践

#### 💻 定点环境欺骗
1. 启动 LocationSpoofer，在主页地图中长按或利用搜索栏搜寻指定地点。
2. 在下方抽屉中选择开启需要伪装的项目：**伪造 Wi-Fi 数据**、**模拟基站数据**、**模拟蓝牙数据**、**开启轻微抖动**。
3. 点击“启动模拟”即可接管系统 GPS。

#### 🚗 高仿真路线往返模拟
1. 点击主页下方的“路线规划”进入选点阶段，在地图上依次点击标记多个路点。
2. 设定控制模式为“循环（自动）”，并选择你的移动档位（步行、跑步、骑行、驾驶，或自定义速度值）。
3. **启用“使用真实路线规划”**（在联网情况下，系统会自动拉取高德/谷歌路网进行精准曲线拟合，并配置红绿灯停留等待）。
4. 点击“保存路线”以便于下次直接从“路线库”中一键加载。
5. 点击“开始模拟”，此时可随时启动悬浮窗，使用“手动（摇杆）”通过悬浮窗摇杆在前台操控实时前行或后退。

#### 🕵️‍♂️ 无线电信号采集（实地扫街）
1. 在物理世界中活动前，进入 LocationSpoofer “设置” -> 开启 **“环境图谱与扫街”** 采集模式。
2. 此时模块会自动在后台静默扫描并保存当前位置对应的 Wi-Fi/基站/蓝牙特征到本地 Room 数据库。
3. 采集完成后，可以随时在“管理本地采集数据”中编辑采集点备注、剔除无效数据，或者以 **JSON 文件格式导出** 备份、共享给他人导入。
4. 下次当你在虚拟定位中选择模拟该经纬度时，系统将通过 **IDW 插值算法** 完美重现采集到的物理信号场。

#### 📍 解决特定 App 定位偏移
* 如果你发现微信、学习通等应用定位产生了几百米的固定漂移：
  * 进入 LocationSpoofer “设置” -> 点击 **“配置应用坐标系”**。
  * 点击“添加应用包名”，找到产生偏移的目标 App。
  * 将其底层的坐标标准修改为 `WGS-84` (原生 GPS 坐标) 或 `BD-09` (百度地图坐标)，系统将在 Hook 层自动转换，地图即可完美重合。

---

## 🛠️ 技术栈与依赖库

* **开发语言**：100% Kotlin
* **界面框架**：Jetpack Compose & Material Design 3
* **依赖注入**：Koin
* **底层数据库**：Room Database (SQLite)
* **网络请求**：OkHttp 3 & Kotlinx Serialization
* **地图组件**：AMap 3DMap SDK (中国大陆设备) / Google Maps & Places SDK (海外设备)
* **Xposed 框架**：LSPosed API 93 / libxposed (service 模式)

---

## 🎁 鸣谢

- [LocationSpoofer](https://github.com/HuangZhuoRui/LocationSpoofer): 上游

---

## ⚠️ 免责声明

本程序**仅供学习研究、技术交流以及个人合法合规测试（如开发者定位测试、设备兼容性调试）使用**。
使用者请勿将本工具用于任何违法违规或违反相关平台服务协议的活动（包括但不限于虚假打卡、网络考试作弊、商业欺诈等）。
使用本模块造成的任何账号封禁、数据丢失、法律纠纷或其他直接/间接损失，均由使用者自行承担，作者不对此承担任何责任。

---

## 📜 开源协议

本项目基于 [GNU General Public License v3.0](LICENSE) 协议开源。

```
Copyright (C) 2026 SuseOAA
```
