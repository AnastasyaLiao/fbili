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

**未实测（环境受限，需在用户手机复测）**：
- 扫码/短信登录真机链路：接口与状态机已按官方协议实现并编译通过，未走完一次真实登录（需手机 B站 App 扫码）。
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
