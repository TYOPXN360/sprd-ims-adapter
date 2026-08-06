# 相机 180 度倒置问题——最终解决方案（Camera2 app 层）

VP19（SC8541E）Android 11 GSI 相机方向问题已通过修改 **AOSP Camera2 app** 解决。

## 最终方案（Camera2 3 处 smali patch）

| 修复点 | 文件 | 修改 |
|--------|------|------|
| **预览方向** | `PreviewTransformCalculator.toTransformMatrix` | return 前强制 `postScale(1, -1, cx, cy)`（垂直翻转，.locals 8→9） |
| **保存照片方向** | `TaskCompressImageToJpeg.run()` | `onJpegEncodeDone` 后、`createExif` 前，对 jpeg 数据 v14 调 `vp19FlipJpegData` |
| **review 缩略图方向** | `YuvImageBackendImageSaver$YuvImageProcessorListener.onResultUncompressed` | `cond_1` 分支 `updateCaptureIndicatorThumbnail` 前对 bitmap 做同样翻转（.locals 9→10） |
| **EXIF 无旋转** | `CameraUtil.getImageRotation` | 后置时强制返回 0（EXIF=1，图库不额外旋转） |

## 新增辅助方法

`TaskJpegEncode.vp19FlipJpegData(byte[] data)`（static）：
- 解码 jpeg → `Matrix.postScale(-1, 1, cx, cy)`（水平翻转）→ `Matrix.postRotate(90, cx, cy)`（旋转90）
- 重编码 JPEG(95) → 返回

**正确变换 = 水平翻转 + 旋转 90**（由 8 变体对比测试确定：v7-hflip-r90）。

## 关键发现

1. **照片保存路径**：`TaskCompressImageToJpeg.run()` 用 native 压缩 jpeg（v14 字节数组）直接保存——**在 `onJpegEncodeDone` 里翻转参数无效**（值传递），必须在 `run()` 的 v14 上翻转
2. **review 缩略图走 YUV 路径**（`YuvImageProcessorListener`），与保存的 jpeg 不同源——需单独翻转
3. **EXIF 恒为 1**（`getOrientationValueForRotation` 写死）——getImageRotation 只影响 native 旋转和 review 显示，不影响 EXIF
4. **`g=270`（EXIF 270）恒触发镜像**——图库对 EXIF 8 处理异常，必须避免

## 成果

- **预览**：正常（postScale(1,-1)）
- **相册照片**：正常（hflip+r90）
- **拍照瞬间 review 缩略图**：正常（hflip+r90）

## 文件

- 最终 APK：`vowifi/camera-fix/Camera2-camera-fix.apk`（SHA-256 `f7abb72c...`）
- 原版备份：`vowifi/camera-fix/Camera2.apk.orig`（SHA-256 `5edef295...`）

## 历史尝试（全部无效，已归档）

- `ro.camera.hw_rotation` prop / build.prop
- HAL `MOVZ #90→270` / 数据表 90→270
- framework `CameraCharacteristics.get(SENSOR_ORIENTATION)` +180
- `persist.sys.auto.detect.sensor=0`
- getImageRotation 强制 90/180/270 + jpeg 各种翻转组合（V/R180/R270/Mx/hflip+r90...）
