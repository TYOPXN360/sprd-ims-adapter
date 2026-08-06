# 相机 180 度倒置问题调查记录

VP19（SC8541E）Android 11 GSI 下相机预览/照片 180 度上下倒置。原厂 Android 8 正常。
本记录完整归档调查过程与结论，供后续继续。

## 现象

- 后置摄像头，预览和照片都 180 度上下倒置
- `dumpsys media.camera`：`Facing: Back, Orientation: 90`，`android.sensor.orientation=90`
- 屏幕竖屏 410×502（ROTATION_0）
- 所有相机 app（AOSP Camera2 等）都倒

## 已确认的事实

1. **HAL 报告 `sensor.orientation=90`**（Camera2 metadata）
2. **app 层旋转计算正确**：`getImageRotation(sensor=90, device=0, front=false) => 90°`（AOSP Camera2 探针确认）
3. **framework 层把 sensor.orientation +180（90→270）后 app 读到 270，但仍倒** → **倒置与 orientation 元数据无关**
4. **HAL 的 sensor orientation 是运行时从 sensor 芯片自动检测**（`getSensorStaticInfo` 检测逻辑），非静态数据
5. **`persist.sys.auto.detect.sensor` 关闭检测后仍倒** → **是 sensor 物理安装方向**（硬件 180 度）

## 已尝试的方案（全部无效）

| 方案 | 结果 |
|------|------|
| `ro.camera.hw_rotation=180`（运行时 setprop） | 无效（HAL 不读） |
| `ro.camera.hw_rotation=180`（vendor build.prop + 重启） | 无效 |
| `persist.vendor.camera.sensorrotation=180` | 无效 |
| HAL 4 处 `MOVZ #90` → `#270` | 无效（90 非立即数，sensor 检测） |
| HAL 数据表 0x8eab0 的 90 → 270 | 无效 |
| HAL `translateLocalToFwMetadata` 的 `[x8,0x7c]` 值改 270 | 无效 |
| framework `CameraCharacteristics.get(SENSOR_ORIENTATION)` +180 | app 读到 270，但仍倒（已回滚） |
| `persist.sys.auto.detect.sensor=0`（关闭 sensor 检测） | 仍倒 |

## HAL 逆向关键发现（用 rizin）

- HAL：`/vendor/lib64/hw/camera.sp9832e.so`（SHA-256 `1b6b6df7...`）
- `getSensorStaticInfo`（vaddr 0x59420）：sensor 数据从 `persist.sys.auto.detect.sensor` + 运行时检测读
- `initStaticParameters`（0x5a188）：`0x5a278 MOVZ W11,#90 → CSEL → STR [X20,#0x2308]`（条件存 90）
- `translateLocalToFwMetadata`（0x61ec8）：写 SENSOR 段 tag（0x000e0010 等），非 sensor.orientation（0x000e000e）
- sensor.orientation（0x000e000e）的精确写入点**未定位**（多层虚函数间接调用）
- `setSensorRotation`/`setSensorOrientation`（0x2f12c/0x2f140）：app 传参的 setter，AOSP Camera2 不传

## 结论

**相机倒置根因是 sensor 物理安装方向（硬件 180 度）**，与 orientation 元数据、app 旋转计算、framework 处理均无关。
修复需要 **HAL 层 preview buffer 180 度翻转 patch**（深逆向 sensor 检测 + preview 路径），投入极大。

## 工具（已就绪，可继续）

- **rizin 0.9.1**（ARM64 反汇编器）：`/mnt/TY/android/android-project/vp19/tools-rizin/`（clone 自 rizinorg/rizin + 静态二进制）
  - 用法：`bin/rizin -q -e scr.color=0 -c 's 0x<vaddr>; pd <n>' /tmp/cam-hal.so.orig`
- HAL 备份：`/tmp/cam-hal.so.orig`
- 设备 HAL 备份：`/data/local/tmp/camera.sp9832e.so.orig`

## 后续方向（若继续）

1. 追 `getSensorStaticInfo` 检测逻辑 → 找 sensor 方向写入点（0x59514 检测分支）
2. 在 HAL 的 preview buffer 处理（`SprdCamera3HWI` 的 buffer 路径）做 180 翻转
3. 或 patch HAL 的 `sensorrotation` 默认值（如果它控制 buffer 翻转）

## 环境状态

当前全部恢复原版：
- framework.jar `6300526e`
- Camera2.apk `5edef295`
- camera.sp9832e.so `1b6b6df7`
- `persist.sys.auto.detect.sensor=1`
