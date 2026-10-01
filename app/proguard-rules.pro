# 保留行号，release 崩溃日志可读
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# WebView 注入接口兜底（当前未用，防后续登录页扩展被裁）
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 应用入口与 Compose 入口反射点
-keep class com.sammy.fbili.FBiliApp
-keep class com.sammy.fbili.MainActivity
