# fbili

一个给低端安卓老机器写的哔哩哔哩第三方客户端。Kotlin + Jetpack Compose 原生实现，
整包约 2.5 MB，最低支持 Android 5.0（API 21），在 2GB 内存以下的设备上流畅运行。

> 本项目交互与接口层设计参考了 Flutter 客户端 [bili_you](https://github.com/lucinhu/bili_you)
> （该项目已停止维护），代码为 Kotlin 原生重写，基本是原创。

## 特性

- **轻**：R8 全量混淆 + 资源收缩，release APK ≈ 2.5 MB；图片仅 8% 堆内存缓存 + 300MB 磁盘缓存
- **省电低发热**：弹幕 30fps 且静止时零重绘；列表按需加载、离屏即停
- **深色模式**：跟随系统 / 强制深色，深色档为纯黑底（OLED 省电）
- **核心功能**：首页推荐流（双列）、搜索（双列结果 + 历史搜索）、视频播放（多清晰度 / 分P /
  弹幕）、收藏、历史、关注、扫码与手机号登录、离线缓存
- **无广告、无推送、无多余权限**：仅 INTERNET 与网络状态两项权限

## 构建

```bash
# JDK 21 + Gradle 8.11+
gradle assembleRelease
# 产物 app/build/outputs/apk/release/app-release.apk
```

发布签名为自签密钥，不入库。如需自行签名，在仓库根目录 `local.properties` 中加入：

```properties
fbili.storeFile=fbili.jks
fbili.storePassword=<你的口令>
fbili.keyAlias=<你的别名>
fbili.keyPassword=<你的口令>
```

## 技术栈

Kotlin 2.0 · Compose BOM 2024.09 · Media3 (ExoPlayer) · OkHttp · Coil · kotlinx.serialization · DataStore

## 说明

仅供学习交流使用。接口与数据归属哔哩哔哩，请勿用于任何商业用途。
