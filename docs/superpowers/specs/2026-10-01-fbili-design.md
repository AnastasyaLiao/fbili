# fbili 设计文档（bili_you 原生 Kotlin 重写）

日期：2026-10-01 ｜ 状态：v1.0.0 已交付 APK

## 目标与特色

把已停更的 Flutter 客户端 bili_you 重写为原生 Kotlin/Compose 应用 fbili，达成三条产品支柱：

1. **没有 bug**：根治 bili_you 两大顽疾（偶发连不上网、登录必闪退）。
2. **深色模式**：内置浅色/深色/跟随系统，持久化。
3. **极低发热耗电**：面向 ≤2GB 内存低端机，Android 5.0（API 21）起支持。

> Android 4.x 无法支持：Jetpack Compose 的硬下限是 API 21。这是平台约束，不是实现取舍。

## 技术栈与架构

- Kotlin 2.0.21 + Jetpack Compose (BOM 2024.09.03, Material3)，单 Activity。
- AGP 8.7.3 / Gradle 8.11.1 / JDK 21（gradle.properties 已钉 `org.gradle.java.home`，本机默认 JDK 26 不受 AGP 支持）。
- compileSdk 35，minSdk 21，targetSdk 34。
- 网络：OkHttp 4 + kotlinx.serialization；播放：Media3 ExoPlayer 1.4.1；图片：Coil 2.7.0。

包结构（`app/src/main/java/com/sammy/fbili/`）：

| 包 | 职责 |
|---|---|
| `net/` | Api（B站 web API 面）、Wbi 签名、buvid3/4、Models、登录 |
| `data/` | Account（cookie/SESSDATA 持久化）、Settings |
| `ui/` | home/bangumi/rank/search/video/player/danmaku/fav/history/user/dynamic/downloads/login/common |
| `dl/` | Downloader（缓存任务队列，最多并发 2） |

页面流转用自写轻量路由（`AppNav`），不引入 Navigation 依赖，减包体减开销。

## 对 bili_you 两个致命 bug 的修复

**偶发连不上网**：bili_you 的 Dio 拦截器在 wbi 取 mixin key 失败时让所有请求直接抛错。fbili 里 Wbi 的 key 获取失败会退化为无签名请求（多数接口仍可用），并且 `today()` 按 UTC 日切计算，跨时区/长时间挂后台不会因日期字符串过期而签名全废。全部 API 调用套 `runCatching`，失败落到各页 ErrorBox + 重试。

**登录必闪退**：原实现轮询扫码接口对 `code`/`status` 字段做非空强转，B站返回缺字段时即崩。fbili 的扫码登录（`passport/login/web/qrcode` + poll，状态码 0/86101/86090/86038 全部显式处理）与短信验证码登录、WebView H5 登录 cookie 收割，所有字段带默认值，任何一步失败只提示不崩。

## 性能与功耗决策（低端机向）

- 静态调色板，禁用 dynamic color；无过度动画。
- 图片：Coil 内存缓存 = 堆的 8%（`ActivityManager.memoryClass`），磁盘缓存 300MB 封顶，关 crossfade。
- 弹幕：自绘 `DanmakuView`，Choreographer 单循环 + detach 安全标志，屏幕外弹幕 LRU 缓存仅 4 条轨道池；无弹幕时零帧回调。
- 播放：硬解 H.264 优先；DASH 用 MergingMediaSource(视频流+音频流)；播放中 5 分钟一次心跳，退出即停。
- 轮询：播放进度 400ms，`mutableStateOf` 等值写入不触发重组，开销可忽略。
- R8 刻意关闭（`isMinifyEnabled=false`）：正确性优先，release APK 已仅 11MB（Flutter 原版 50MB+）。
- 下载目录：外部私有目录不可用时自动回退内部存储（兼容畸形 ROM/模拟器）。

## 功能面（对齐 bili_you）

首页推荐/热门/排行榜/直播、番剧、搜索（用户/影视占位除外）、视频详情+分 P+选流+弹幕（收发）、点赞/投币/收藏、评论回复、动态、历史、关注/取关、个人主页、离线缓存、本地播放、扫码+短信+WebView 三种登录、深色模式设置、360P~1080P 高帧率画质选择。

## 验证矩阵

**实测通过（Android 5.0 AVD, 320x640）**：冷启动 803ms 零 FATAL；首页真实 WBI 签名数据；视频详情完整；深色模式生效并持久化；我的/设置/离线缓存/本地视频页；缓存按钮触发下载流程无崩溃；release APK apksigner 签名校验通过（v2）。

**实测通过（AVD 1080x2400，登录态）**：在线播放（桌面 Safari UA 后 CDN 不再 403，DASH 流正常出画面）；历史记录/我的收藏/我的关注三页列表+点播全通；播放界面 bili_you 规格（原始比例 contain、手势、简介/评论、分P）；登录态跨页同步（OkHttp cookie domain 去前导点后，SESSDATA 等真实随请求发出）。

**实测通过（2026-10-01 批次，R8 开启后 2.5MB APK）**：底部导航仅剩首页/我的；设置改 480P→force-stop→重启仍为 480P（DataStore 持久化）且登录态跨重装保留；收藏夹返回键+四夹首视频封面；视频页无相关推荐、评论头像（34dp 圆头像）正常；暂停时中央播放键可点（z-order 修复）、圆点进度条+拖动时间预览；首页下拉刷新；搜索页返回键/胶囊输入框/圆角搜索按钮/历史搜索词条持久化+清空。修复两处实测暴露的接口问题：旧 `/x/v2/reply` 对未签名请求静默返回空列表→迁移到 WBI 签名 `/x/v2/reply/wbi/main`；新端点头像字段改名 `face`→`avatar`，模型双字段兼容。

**实测通过（Android 8.1 / API 27 AVD 1080x1920，3.45MB 现发布包）**：`aapt dump badging` 确认包 minSdk 21；`install -r` 成功；冷启动 `am start -W` TotalTime 831ms，logcat 零 FATAL/AndroidRuntime；首页推荐流真实数据+封面图加载；点开视频详情页并真实在线播放（进度推进至 1:30/23:28，圆点进度条控制层正常）；系统深色切换后 App 纯黑底生效。

**Android 4.x 结论（不可运行，非缺陷）**：APK manifest minSdkVersion=21，Android 4.x（API 15–19）包管理器会以 INSTALL_FAILED_OLDER_SDK 直接拒装；且 Compose、AndroidX（Activity 1.9 等）、Media3、OkHttp 4.x 全线官方下限即 API 21，低于 21 需放弃 Compose 重写 UI 层。本机亦无 API 19 系统镜像（Apple Silicon 模拟器不支持 x86 老镜像），无法真装演示，判定依据为包与依赖的硬下限。

**1.0.2（2026-10-11）手机号登录根修——1.0.1 的 WebView 方案作废**：
1.0.1 那句"API 27 崩是镜像环境问题"判错了。对照实验（同一 AVD、同一个 Chromium 61 内核、用系统自带的
webview_shell 独立加载，与 fbili 无关）：`baidu.com` 正常、`m.bilibili.com` 正常，
只有 `passport.bilibili.com/login` 必然把渲染进程打到 SIGSEGV（`Renderer process crash detected (code 11)`）。
B站已下线全部 H5/移动登录路由（实测一律 302→`www.bilibili.com/404`），PC 登录页是唯一活的网页入口、
又是 Android 8.x 老内核跑不动的 SPA——所以这条路在低端机上救不回来，**手机号登录改成原生表单 + B站 web 接口**
（`/x/passport-login/captcha` → 极验滑块 → `/web/sms/send` → `/web/login/sms`，与 bili_you 同一套协议）。
WebView 只留给极验滑块（ES5，实测 61 上 onReady/onSuccess 正常）。两个只能照抄 B站自己封装
（passport chunk 838 `initGeeCode`）才对的细节：`product` 必须是 `bind` 而不是 `popup`（popup 下 `verify()`
静默不渲染滑块），`challenge` 必须用 `getValidate()` 回传的那把（用最初那把 B站判 -105 验证码错误）。

**实测通过（release 1.0.2 包，API 27 / Chromium 61 AVD）**：装机后默认即纯黑深色主题；首页推荐流；
点播真实推进（10 秒墙钟走 11 秒进度，`0:10→0:21`）；「缓存」→ 悬浮窗从展开卡收成 74% 角标 → 完成后
离线缓存页列出 7.1MB 文件 → 本地播放出画面；手机号登录表单渲染、极验滑块出图并可点通。
登录链路端到端：滑块通过后 `sms/send` 收下 validate、只因测试号码是假号才回"手机号格式错误"（未发真短信）。

**播放这三处是根修，不是缓解**：
- `playSingle` 原先走 ExoPlayer 默认数据源（Dalvik UA、无 Referer）→ B站 CDN 实测 403
  （同一 URL：无头 403 / 只给 Referer 403 / Referer+桌面 UA 才 206）。改为与 DASH 共用带 UA+Referer 的数据源。
- 视频 surface 原先放在 `AndroidView(update{})` 里，播放状态每 400ms 刷新就 `setVideoSurface` 一次，
  解码器被反复重配，画面停在第 0 帧、进度卡在 0:00。改为 `SurfaceHolder` 回调只在创建/销毁时各绑一次，
  视频页/直播页/本地播放页统一用 `ui/player/VideoSurface.kt`。
- `playurl` 迁到 `/x/player/wbi/playurl`（下载用的 `playurlMp4` 同步），并加**备用线路兜底**：
  B站给的 `base_url` 常是 mcdn P2P 边缘（实测 `*.edge.mountaintoys.cn:4483`、`*.mcdn.bilivideo.cn:8082`），
  `backup_url` 才是 upos 镜像；取流报错或缓冲区两次采样之间没有增长才判定线路死了并换线。
  踩坑：第一版看门狗只看"没在播就换线"，在满载机器上把正常冷启动反复打断，release 包实测
  `line 0 → line 1` 永远起不来——这条是自查出来的，别照着写。

**主题**：播放页（视频/直播/本地）整页恒深色（`CinemaTheme`）——这些页自带纯黑底，跟随系统浅色时会出现
"黑底 + 浅色主题黑字"，字直接看不见；主题设置默认档由"跟随系统"改为"深色"（手动选过的人不受影响）。

**离线缓存悬浮窗**：应用内覆盖层（不申请 SYSTEM_ALERT_WINDOW）。有任务时出现，展开卡 4.5 秒后自动收成百分比
角标，可上下拖动，点角标重新展开、点卡片跳缓存页，全部任务完成后角标显示"已完成"再自动消失。
配套把 `dl/Task` 的进度字段改成 Compose 快照状态——原先是普通 `var`，下载线程写的进度界面收不到刷新。

**未实测（环境受限，需在用户手机复测）**：
- 真实发短信登录：需要主人手机号，接口与滑块链路已实测到"只差真号真短信"。
- 扫码登录真机走完一次：接口与状态机已实现，需手机 B站 App 扫码。
- 本地文件播放（playFile 路径）：API 21 模拟器 FUSE 导致推送文件对应用不可见，改为「缓存」入口验证；下载器已加内部存储回退，真机可用。
- 搜索结果双列布局：`/x/web-interface/search/type` 对本机出口 IP（AWS 数据中心）返回 HTML 风控页（已改为友好提示），代码路径与已验证的推荐流共用同一卡片组件，境内网络预期正常。

**已知限制**：弹幕发送仅本端显示（B站 web 发弹幕需风控校验，失败静默）；大会员付费番剧/直播仅尽力而为；1080P 高帧率需登录大会员账号，未登录自动降档。

## 构建与交付

```bash
# 仓库根目录（Gradle 8.11.1 + JDK 21）
gradle assembleRelease
# 产物 app/build/outputs/apk/release/app-release.apk
```

发布签名为自签 `fbili.jks`，不入库（.gitignore）；凭据放在本机 `local.properties`
（`fbili.storeFile / fbili.storePassword / fbili.keyAlias / fbili.keyPassword` 四项），请自行备份。
