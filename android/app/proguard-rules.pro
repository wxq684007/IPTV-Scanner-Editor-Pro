# Chaquopy Python 集成（反射调用 Python 模块，必须保留）
-keep class com.chaquo.python.** { *; }
-keep class org.python.** { *; }
-dontwarn com.chaquo.python.**
-dontwarn org.python.**

# 应用自身代码：仅保留 Python 桥接入口和数据模型（@Serializable 反射）
# 其他 Kotlin 类由 R8 正常优化/混淆
-keep class com.iptv.scanner.editor.pro.data.** { *; }
-keep class com.iptv.scanner.editor.pro.mpv.MPVLib { *; }
-keep class com.iptv.scanner.editor.pro.mpv.MPVLib$* { *; }
-keepclassmembers class com.iptv.scanner.editor.pro.IptvApplication { <init>(...); }

# JNI native 方法（MPV 的 native 绑定）
-keepclasseswithmembernames class * {
    native <methods>;
}

# MPVLib 回调方法（libplayer.so 通过 JNI 反射调用 eventProperty/event/logMessage 等
# 非原生方法，R8 静态分析看不到 Java/Kotlin 端引用，必须显式保留，否则启动即闪退）
# 实测 R8 8.7.0 对 Kotlin object 的 -keep class { *; } 不完全生效（方法仍被移除），
# 源码中已加 @Keep 注解（MPVLib.kt），这里保留 -keep 规则作为双重保险。
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
-keep class is.xyz.mpv.MPVLib { *; }
-keep class is.xyz.mpv.MPVLib$* { *; }

# kotlinx-serialization（@Serializable 生成的序列化器）
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Compose（Compose BOM 已自带 R8 规则，无需手动 keep）
-dontwarn androidx.compose.**

# Coil 图片加载（反射加载解码器）
-keep class coil.** { *; }
-dontwarn coil.**

# Google ZXing（反射加载解码器）
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Media3 / ExoPlayer（多画面副画面播放器）
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
