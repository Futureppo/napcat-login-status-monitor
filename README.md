## 项目简介

Napcat Login Status Monitor 是一个使用 Kotlin、Jetpack Compose、WorkManager 与 MVVM 架构实现的原生 Android 应用。用于定时监控后端接口状态，在检测到掉线时发送通知提醒。
支持系统：Android 12及以上。


## 功能特性

- 监控列表与卡片式展示：显示头像、QQ 号、状态与基础信息。
- 添加/编辑/删除监控：支持参数配置并持久化保存。
- 参数项：
  - API 地址（baseUrl）
  - Token（用于登录换取 Credential）
  - QQ 号（用于头像展示）
  - 查询间隔（秒）
  - 是否开启
- 后台监控：
  - 首次保存立即触发一次检查（加急的一次性 Work）。
  - 每个监控按独立间隔定时触发下一次检查。
  - 另有 15 分钟周期任务作为兜底。
- 通知：
  - 前台任务通知渠道（低重要级，无声），仅在 Worker 运行时显示。
  - 掉线报警通知渠道（高重要级，声音/震动）。
- 数据持久化：使用 Preferences DataStore 存储监控列表等配置。


## 运行环境

- Android Studio Koala 或更高版本
- JDK 17（Gradle 配置已使用 `jvmToolchain(17)`）
- Android Gradle Plugin 8.12.2


## 构建与运行

Debug 构建：

```bash
./gradlew assembleDebug
```

Release 构建（已启用 R8 与资源收缩）：

```bash
./gradlew assembleRelease
```

生成 App Bundle（推荐用于商店分发）：

```bash
./gradlew bundleRelease
```


## 必要权限与前台服务

`AndroidManifest.xml` 中：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<application
    android:usesCleartextTraffic="true"
    android:networkSecurityConfig="@xml/network_security_config">
    <service
        android:name="androidx.work.impl.foreground.SystemForegroundService"
        android:exported="false"
        android:foregroundServiceType="dataSync" />
    ...
</application>
```



## 使用说明

1. 启动应用后，点击右下角 “+” 添加监控。
2. 依次填写：API 地址、Token、QQ 号、查询间隔（秒）、是否开启，点击保存。
3. 列表展示：
   - 左侧圆形头像：`http://q.qlogo.cn/headimg_dl?dst_uin={uin}&spec=640&img_type=jpg`
   - 中间显示 “QQ: {uin}” 与 API 地址。
   - 右侧显示在线状态 online/offline。
4. 点击卡片进入详情可编辑参数或删除配置（带二次确认弹窗）。


## 后台任务与监控逻辑

- Worker 基于 `CoroutineWorker`：
  1) 启动时先调用 `setForeground(ForegroundInfo)`，进入前台以确保运行可靠性（Android 12+ 强制要求）。
  2) 通过 `baseUrl + /api/auth/login` 与 `token` 生成 `hash=sha256(token + ".napcat")`，换取 Base64 Credential。
  3) 携带 `Authorization: Bearer {Credential}` 向 `baseUrl + /api/QQLogin/GetQQLoginInfo` 发起空体 POST。
  4) 解析 `data.online` 为 online/offline；当从 Online 变为 Offline 时发送高重要级报警通知。
  5) 更新本地存储的最后状态。
- 调度策略：
  - 保存配置后，针对每条开启的监控，立即入队一个加急的一次性 Work；
  - 每次 Worker 完成后按照该监控的 `intervalSec` 再调度下一次；
  - 另有 “15 分钟周期 Work” 作为兜底，避免异常情况下长期不触发。




## 内存与性能优化

- 统一 OkHttp 单例，避免重复创建连接池与线程池。
- 图片加载使用 Coil，头像尺寸受控，避免不必要的大图缓存。
- Worker 的网络超时为 5 秒，减少长时间占用。
- 监控任务按 ID 独立队列串行调度，避免大并发。



## 目录结构概览

- `app/src/main/java/com/napcat/monitor/`
  - `MainActivity.kt`：Compose UI 列表与新增弹窗
  - `DetailActivity.kt`：详情编辑与删除
  - `MainViewModel.kt`：调度逻辑与 DataStore 读写
  - `worker/ApiStatusCheckWorker.kt`：后台检查与通知
  - `data/`：DataStore 扩展与模型
  - `network/HttpClientProvider.kt`：OkHttp 单例
  - `NotificationHelper.kt`：通知渠道与通知构建

