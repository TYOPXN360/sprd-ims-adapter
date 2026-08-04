# VP19 Spreadtrum IMS Adapter

在 **Android 11 GSI**（LineageOS/PHH）上为 **VP19 智能手表**（展讯 SC8541E / Android 8 vendor）恢复 **IMS/VoLTE 通话能力**的 Android 11 MMTEL 适配器。

## 这是什么

VP19 手表原厂是 Android 8.1（展讯 SC8541E），vendor 分区保留 Android 8。刷入 Android 11 GSI 后：

- 原厂 IMS 组件（`com.spreadtrum.ims`）是 Android 8 API，无法在 Android 11 framework 中直接运行
- GSI 的 `treble-overlay-telephony-sprd-ims` 期望的 `com.spreadtrum.ims` 包不存在
- CS 语音未注册（LTE-only 网络），**通话只能走 IMS/VoLTE**

本适配器是一个独立的 **Android 11 `ImsService`**（`com.vp19.sprdims.adapter.prototype`），它：

1. 作为 MMTEL IMS 服务被 `ImsResolver` 绑定
2. 通过展讯私有 HIDL 接口 `vendor.sprd.hardware.radio@1.0::IExtRadio/slot1` 直接驱动原厂 modem
3. 复用原厂 ims.apk 里的 29 个 HIDL generated 类（`IExtRadio` / `IIMSRadioResponse` / `IIMSRadioIndication`），通过 `smali` 回编进 APK
4. 实现完整的通话闭环：**拨出、来电、接听、挂断、对方挂断检测**

## 功能状态

| 功能 | 状态 |
|------|------|
| IMS 注册（enableIMS → P-CSCF 地址 → framework REGISTERED） | ✅ |
| 拨出电话（`videoPhoneDial`，事务 0x8c） | ✅ |
| 来电接入（`notifyIncomingCall`，含去重） | ✅ |
| 接听（`acceptCall`，事务 0x27） | ✅ |
| 挂断（`hangup(serial, callIndex)`，事务 0x0d） | ✅ |
| 对方挂断检测（`getIMSCurrentCalls` 空列表 → terminated） | ✅ |
| 来电号码显示 | ❌（GSI framework 的 extras 清洗导致号码丢失，未 patch bootclasspath） |

## 工作原理

```
Android 11 framework (ImsResolver)
  └─ com.vp19.sprdims.adapter.prototype.SprdImsService (MMTEL)
       ├─ ImsRegistrationImplBase.onRegistered(LTE)
       ├─ SprdMmTelFeature (capability: VOICE)
       ├─ SprdImsCallSession (通话状态机)
       └─ SprdHidlRegistrationBridge
            └─ vendor.sprd.hardware.radio@1.0::IExtRadio/slot1 (HIDL)
                 ├─ setIMSResponseFunctions()   注册回调
                 ├─ enableIMS()                 启用 IMS
                 ├─ videoPhoneDial()            拨号 (0x8c)
                 ├─ acceptCall()                接听 (0x27)
                 ├─ hangup()                    挂断 (0x0d)
                 ├─ getIMSCurrentCalls()        查询呼叫列表 (0xc4)
                 └─ IIMSRadioIndication         来电/状态推送
```

关键点：

- **HIDL 回调类**（`Vp19ImsRadioResponse` / `Vp19ImsRadioIndication`）由原厂 ims.apk 的 29 个 HIDL generated 类 + 生成的 smali 构成，随 APK 编译进 `classes2.dex`
- **标准 radio 回调**（`Vp19StdRadioResponse` / `Vp19StdRadioIndication`，129+45 方法）注册到 `IExtRadio.setResponseFunctions`，接收 `dial`/`hangup`/`callStateChanged` 等标准响应
- 由于 GSI 的 BOOTCLASSPATH 缺少 `android.hidl.base.V1_0` 和 `android.hardware.radio.V1_0`，这些类从 `android-all-11.jar` 提取后**合入 APK 的 DEX**
- 拨号用展讯专属 `videoPhoneDial`（而非标准 `dial`——CS 域在本机未注册）

## 构建

```bash
# 见 tools/README.md 获取完整参数说明
ANDROID_SDK=/path/to/android-sdk \
ANDROID_ALL_JAR=/path/to/android-all-11.jar \
JDK=/path/to/jdk-17 \
APKTOOL_JAR=/path/to/apktool.jar \
STOCK_IMS_APK=/path/to/stock-ims.apk \
PLATFORM_PK8=/path/to/platform.pk8 \
PLATFORM_CERT=/path/to/platform.x509.pem \
bash tools/build.sh
```

构建产物：`out/SprdImsAdapterPrototype.apk`（平台签名）。

**注意**：`Vp19ImsRadioResponse` / `Vp19ImsRadioIndication`（展讯 HIDL 回调）和
`Vp19StdRadioResponse` / `Vp19StdRadioIndication`（标准回调）是通过
`tools/gen_ims_callbacks.py` / `tools/gen_std_radio_callbacks.py` 从原厂 ims.apk
反汇编 + 生成后合入 DEX 的，**不在 app 的 Java 源码中**。完整构建需要执行
`tools/build.sh`（详见 `tools/README.md`）。

## 部署

APK 需**平台签名**（AOSP testkey 或厂商 key），推送到 `/system/priv-app/SprdImsAdapterPrototype/`，并确保 overlay `config_ims_mmtel_package` 指向本包：

```bash
adb push SprdImsAdapterPrototype.apk /system/priv-app/SprdImsAdapterPrototype/
# 使 framework 认为 VoLTE 可用（绕过 CarrierConfig 的 carrier_volte_available_bool=false）
adb shell setprop persist.dbg.volte_avail_ovr0 1
adb shell setprop persist.dbg.volte_avail_ovr 1
```

## 目录结构

```
SprdImsAdapterPrototype/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/vp19/sprdims/adapter/prototype/
│           ├── SprdImsService.java          # ImsService 入口 + 注册上报
│           ├── SprdMmTelFeature.java        # MMTEL feature + 来电通知
│           ├── SprdImsCallSession.java      # 通话会话状态机（拨号/接听/挂断）
│           └── SprdHidlRegistrationBridge.java  # HIDL 回调注册 + enableIMS
├── phh-sprd-overlay-replacement/           # 替换 PHH overlay（config_ims_mmtel_package）
├── ims-config-overlay/                      # IMS 配置 overlay
└── build.gradle / settings.gradle

SprdImsLegacyShim/                          # Android 8 展讯 IMS 扩展接口的编译期 shim（本地链接验证用）

tools/
├── build.sh                        # 一键构建脚本（反汇编→生成→编译→合并→签名）
├── gen_ims_callbacks.py            # 生成展讯 HIDL 回调 smali
├── gen_std_radio_callbacks.py      # 生成标准 radio 回调 Java
├── ExtractRadioApi.java            # 反射提取标准 radio 接口抽象方法
└── README.md                       # 构建工具链说明
```

## 背景与限制

- **设备**：VP19 / S10 Max / sl8541e_1h10，展讯 SC8541E，Android 8.1 vendor + kernel 4.4.83
- **网络**：中国联通 LTE-only（CS 语音未注册），通话依赖 IMS/VoLTE
- **来电号码显示**：号码已传递到 framework（profile 含 `EXTRA_OI`），但 GSI framework 的 `ImsCallProfile.maybeCleanseExtras`/`filterValues` 在真实 Binder 传输下清除了 extras，导致 `ImsPhoneConnection` 读不到号码。修复需 patch bootclasspath（framework.jar / telephony-common.jar），有启动校验风险，未做。
- **VoWiFi**：未实现（原厂 VoWiFi 组件为 Android 8，不在本适配器范围）

## 免责声明

本项目为逆向工程/DIY 产物，仅供个人研究学习。使用前请备份数据，刷机有风险。
