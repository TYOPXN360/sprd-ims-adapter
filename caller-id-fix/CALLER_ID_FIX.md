# 来电号码显示修复（Incoming Caller ID Fix）

VP19（S10 Max / SC8541E）在 Android 11 GSI 下，来电号码显示为「未知」的根因分析与修复方案。

## 问题现象

- 使用自研 IMS adapter（`com.vp19.sprdims.adapter.prototype`）时，VoLTE 来电正常响铃，但 InCallUI 显示号码为「未知」。
- `dumpsys telecom` 中 `Call.handle` 为 `null`，dialer 的 `AnswerFragment.setPrimary` 显示 `number: null`。

## 根因链（逐步排查确认）

1. **号码已到 framework**：adapter 的 `getCallProfile()` 返回 `EXTRA_OI=号码`，framework 的 `ImsPhoneConnection.mAddress=18928163048`（探针确认）。
2. **framework → Telecom 上报成功**：`ConnectionService` 的 `addConnection` 上报 `setAddress(callId, uri, presentation)`，Telecom 的 `ConnectionServiceWrapper.setAddress` 收到且 `getCall()` 命中（探针确认）。
3. **Telecom `Call.setHandle()` 被跳过**：`Call` 构造时执行 `setHandle(null, 1)`（单参重载默认 `presentation=1`），预置了 `mHandlePresentation=1`。后续 `handleCreateConnectionSuccess` 调用 `setHandle(号码, 1)` 时，因 `presentation(1) == mHandlePresentation(1)` 被 `if-eq p2, v0, :cond_8` 分支直接跳过，`mHandle` 保持 `null`。
4. **`toParcelableCall` 也不传 handle**：`ParcelableCallUtils.toParcelableCall` 中 `if-ne presentation, 1, :cond_a`——只有 `presentation == 1` 才传 `Call.getHandle()`。由于 `mHandlePresentation` 被跳过逻辑卡在 1（而 handle 为 null），dialer 收到的 `Call.Details.handle` 为 `null`，UI 显示「未知」。

**死锁本质**：`Call` 构造预置 `mHandlePresentation=1`，使「presentation=1 的 setHandle 被跳过」与「toParcelableCall 只在 presentation=1 时传 handle」互相冲突。

## 修复方案（2 个改动，打破死锁）

### 1. TeleService：`updateAddress` 强制 `presentation=1`

文件：`TeleService.apk` → `com/android/services/telephony/TelephonyConnection.smali` → `updateAddress()`

在 `Connection.setAddress(uri, presentation)` 前，把 presentation 固定为 1：

```smali
# 原：invoke-virtual {v1}, Connection.getNumberPresentation() → move-result v1
# 改：强制 1
const/4 v1, 0x1
```

目的：保证 `ParcelableConnection.getHandlePresentation() == 1`，使 `toParcelableCall` 愿意传递 handle。

### 2. Telecom：`Call` 构造器 `setHandle(null)` 默认 presentation `1 → 3`

文件：`Telecom.apk` → `com/android/server/telecom/Call.smali` → 构造器

```smali
# 原：invoke-virtual {p0, v2}, Call;->setHandle(Landroid/net/Uri;)V  （单参，默认 presentation=1）
# 改：显式传 3
const/4 v3, 0x3
invoke-virtual {p0, v2, v3}, Lcom/android/server/telecom/Call;->setHandle(Landroid/net/Uri;I)V
```

目的：构造时 `mHandlePresentation=3` 而非 1，这样后续 `handleCreateConnectionSuccess` 的 `setHandle(号码, 1)` 因 `1 != 3` 不再被跳过，`mHandle` 正确设置为号码，且 `getHandlePresentation()` 变为 1，`toParcelableCall` 正常传 handle。

### 3. framework：无改动（原版）

所有 framework 探针（`VP19PCH` 等）已移除，`framework.jar` 为原版。

## 验证结果

来电时（RINGING 状态）：

```
Telecom:  Call id=TC@1, handle=tel:18928163048
Dialer:   AnswerFragment.setPrimary - PrimaryInfo, number: *** **** ****
```

号码正常显示。

## 产物（AOSP testkey 签名）

存放于 `ims-caller-id-patch/final/`：

| 文件 | SHA-256 | 说明 |
|------|---------|------|
| `TeleService-callerid-clean-signed.apk` | `94f12d2c015593949786d08763e3238b96f0858cb10935b1b63d6a5dc021a117` | presentation=1 修复（无探针） |
| `Telecom-ctor3c-signed.apk` | `faf35721bb52b9f0d55a7bf717b2860d299c2d3b767b33f5d2df492c133e0864` | 构造器 setHandle 1→3 修复 |
| `Dialer.apk.orig` | `7aef5c8ec643978421ba4c94f92728863bf17d14ee52e6cd9852df7aff01f382` | 原版（回滚用） |

## 回滚方法

- TeleService / Telecom：替换回原版 APK（AOSP testkey 签名），重启。
- framework：原版无需回滚（本修复不改 framework）。若曾改动，替换回原版并删除 `/system/framework/arm64/boot-*.{oat,vdex,art}` 全部编译产物，重启。

## 相关组件

- IMS adapter：`com.vp19.sprdims.adapter.prototype`（见仓库根目录 `SprdImsAdapterPrototype/`）
- 平台签名：AOSP testkey（`tools/platform.pk8` / `tools/platform.x509.pem`）
- GSI：LineageOS 18.1（Android 11）
