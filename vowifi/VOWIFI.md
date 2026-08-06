# VoWiFi 支持（Wi-Fi Calling）

在自研 IMS adapter（`com.vp19.sprdims.adapter.prototype`）中完善 Wi-Fi Calling（VoWiFi / WFC）支持。

> 注：当前所在地区无 VoWiFi 网络，本功能仅完成代码/构建层面完善，未做实际通测试。

## 背景

原厂 `ims.apk`（Android 8）内置完整的 `com.spreadtrum.ims.vowifi` 状态机（15 个顶层类、92 个文件），
其中 `ImsService.onCreate()` **无条件创建 `VoWifiServiceImpl`**。本 adapter 采用轻量方案：
直接用 `vendor.sprd.hardware.radio@1.0::IExtRadio` 的私有 HIDL 命令实现等价逻辑，
不依赖 `SprdVoWifiService.apk` 外部组件。

## 新增文件

| 文件 | 作用 |
|------|------|
| `SprdImsConfigImplBase.java` | `ImsConfigImplBase` 子类；处理 framework 的 WFC 配置查询/设置（`getConfigInt/getConfigString/setConfig`），VoWiFi 开关变化驱动 modem |
| `SprdVoWifiController.java` | VoWiFi 控制器；反射调用 modem 私有命令 + 处理 indication 回调 |

## 修改文件

| 文件 | 改动 |
|------|------|
| `SprdImsService.java` | 新增 `getConfig(int)`（返回 config 对象）、`reportWfcRegistered()`（上报 WFC 注册状态） |
| `tools/gen_ims_callbacks.py` | callback smali 生成器新增 3 处 VoWiFi 转发 |

## 使用的 modem 命令（IExtRadio，反射调用）

- `notifyVoWifiEnable(int serial, boolean enable)` — 启用/禁用 VoWiFi 承载
- `enableWiFiParamReport(int serial, boolean enable)` — WiFi 参数上报
- `getIMSVoiceCallAvailability(int serial)` — 查询语音可用性（bit 0x2 = VoWiFi）

## indication 回调转发

| 回调 | 转发目标 |
|------|----------|
| `IMSWifiParamInd(int slotId, ArrayList)` | `SprdVoWifiController.onWifiParamIndication` → `SprdImsService.reportWfcRegistered()` |
| `IMSNetworkInfoChangedInd(int slotId, ImsNetworkInfo)` | `SprdVoWifiController.onNetworkInfoChanged` |
| `getIMSVoiceCallAvailabilityResponse(RadioResponseInfo, int)` | `SprdVoWifiController.onVoiceCallAvailability` |

## 构建产物

- `vowifi/SprdImsAdapterVowifi.apk`（SHA-256 `944492a18dd8cffa5a54cc3648ef918550d72c47710252aa55e282ec4a183501`）
- 单 `classes.dex`（691 KB）：adapter 主类 + 29 个原厂 HIDL 类 + 标准 radio/hidl-base 类 + callback（含 VoWiFi 转发）
- AOSP testkey 签名（SHA-256 `c8a2e9bc...`，与 GSI 平台签名一致）
- 部署方式：替换 `/system/priv-app/SprdImsAdapterPrototype/SprdImsAdapterPrototype.apk`

## 构建方式

完整构建链见 `tools/build.sh`（需 `ANDROID_ALL_JAR` 提供 framework API 与 hidl-base/radio 类；
本机环境也可从设备 `framework.jar` + 原厂 `ims.apk` 的 smali 提取等价类）。

## 2026-08-06 补充：挂断修复

拨号后（对方响铃中）挂断无效的根因：`SprdImsCallSession.terminate()` 用
`Integer.parseInt(callId)` 作为 modem callIndex，但 `callId` 是 framework 的
session id（如 "11"），不是 modem 的 call index（单呼叫为 1），导致
`hangup(serial, 11)` 挂错目标、modem 本地释放但网络未释放。

修复：`terminate()` 固定 `callIndex = 1`（单卡单呼叫场景）。

## 部署组合（全部功能正常）

- adapter v3（`SprdImsAdapterVowifi3.apk`，SHA-256 `c8047cc4...`）
- Telecom 原版（`7341923d`）
- TeleService callerid（`94f12d2c`，presentation=1）
- framework 原版（`6300526e`）
- 验证：来电号码显示、去电、挂断全部正常
