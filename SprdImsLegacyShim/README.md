# VP19 Android 8 IMS legacy API shim（仅本机验证）

该目录提供原厂 Android 8 `com.spreadtrum.ims` 在 Android 11 GSI 上进行类加载验证所需的最小 API 声明。

- 不包含 modem/RIL 命令。
- 不实现或声明 IMS 已注册。
- 不会被部署到设备；仅生成 `classes.dex`/JAR，用于验证原厂 `ims.apk` 的旧 Binder 依赖能否被满足。
- 后续如需设备测试，必须把 shim 作为 bootclasspath/system framework JAR 预装，且需完整回滚方案。
