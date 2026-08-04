# tools

本目录包含从源码构建 VP19 Spreadtrum IMS adapter APK 的完整脚本。

## 文件

| 文件 | 用途 |
|------|------|
| `build.sh` | 一键构建脚本：反汇编原厂 ims.apk → 生成回调 → 编译主类 → D8 合并单 dex → 签名 |
| `gen_ims_callbacks.py` | 生成展讯 HIDL 回调（`Vp19ImsRadioResponse` / `Vp19ImsRadioIndication`）的 smali |
| `gen_std_radio_callbacks.py` | 生成标准 radio 回调（`Vp19StdRadioResponse` / `Vp19StdRadioIndication`）的 Java 源码 |
| `ExtractRadioApi.java` | 反射列出 `android.hardware.radio.V1_0.IRadioResponse` / `IRadioIndication` 的抽象方法（供生成器使用） |

## 为什么要这些生成步骤

适配器 APK 需要四类回调类，它们**不在** `SprdImsAdapterPrototype/app` 的 Java 源码里，而是构建时生成的：

1. **展讯 HIDL 回调**（smali）：`Vp19ImsRadioResponse` / `Vp19ImsRadioIndication`
   继承原厂 ims.apk 的 `IIMSRadioResponse$Stub` / `IIMSRadioIndication$Stub`（`vendor.sprd.hardware.radio.V1_0`）。
   这些 Stub 来自原厂 Android 8 ims.apk，无法用普通 Java 编译（引用 vendor HIDL 接口），
   所以用 apktool 反汇编后以 smali 形式重编。生成器为每个方法注入：
   - 日志（`Vp19SprdIms` tag）
   - IMS 注册信号 → `SprdImsService$RegistrationBridgeListener.onImsRegistered()`
   - 呼叫状态变化 → `SprdImsService.requestImsCurrentCalls()`（重新查询 modem 呼叫列表）
   - `getIMSCurrentCallsResponse` → `SprdImsService.onImsCurrentCallsRaw()`（解析 Call 列表驱动状态机）

2. **标准 radio 回调**（Java）：`Vp19StdRadioResponse` / `Vp19StdRadioIndication`
   继承 `android.hardware.radio.V1_0.IRadioResponse$Stub` / `IRadioIndication$Stub`。
   注册到 `IExtRadio.setResponseFunctions()`，接收标准 `dial`/`hangup`/`callStateChanged`
   响应（展讯回调只覆盖 IMS 扩展，标准响应需要这套）。
   方法签名由 `ExtractRadioApi` 从 `android-all-11.jar` 反射提取，生成器输出 no-op 覆盖
   （`dialResponse` / `callStateChanged` 等打日志）。

3. **缺失的 framework 类型**（.class 合并）：GSI 的 BOOTCLASSPATH 缺少
   `android.hidl.base.V1_0`（`IBase` 等）和 `android.hardware.radio.V1_0`
   （`RadioResponseInfo` / `UusInfo` 等），导致回调类 `ClassNotFoundException`。
   `build.sh` 从 `android-all-11.jar` 提取这些类，与主 dex 一起 D8 合并进 APK。

## 使用

```bash
ANDROID_SDK=/path/to/android-sdk \
ANDROID_ALL_JAR=/path/to/android-all-11.jar \
JDK=/path/to/jdk-17 \
APKTOOL_JAR=/path/to/apktool.jar \
STOCK_IMS_APK=/path/to/stock-ims.apk \
PLATFORM_PK8=/path/to/platform.pk8 \
PLATFORM_CERT=/path/to/platform.x509.pem \
bash tools/build.sh
```

产物：`out/SprdImsAdapterPrototype.apk`（平台签名）。

## 部署后

```bash
adb push out/SprdImsAdapterPrototype.apk /system/priv-app/SprdImsAdapterPrototype/
adb shell setprop persist.dbg.volte_avail_ovr0 1
adb shell setprop persist.dbg.volte_avail_ovr 1
```

（需 root，`/system` 可写；`persist.*` 属性重启保留。）
