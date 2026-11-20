# 保留行号信息，便于崩溃栈定位
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.furina.classtools.** {
    *** Companion;
}
-keepclasseswithmembers class com.furina.classtools.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.furina.classtools.**$$serializer { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# WorkManager（v2 起用于本机提醒）内部使用 Room，数据库实现类由 androidx.startup
# 在运行时通过反射实例化；R8 若裁掉无参构造会 release 启动即崩：
#   NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init>
# 保留 Room 生成库实现与构造器（release 冷启动回归由 docs/RELEASE_BUILD 验证）。
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
-dontwarn androidx.room.**
-dontwarn androidx.work.**

# 提醒 Worker 由 WorkManager 按类名反射创建，保留其构造器与类名。
-keep class com.furina.classtools.data.reminder.ReminderWorker { *; }

# -----------------------------------------------------------------------------
# OPPO PUSH（Heytap MCS SDK V4.0.0）
# - Manifest 声明的回调服务由系统/推送通道按类名拉起（不能混淆/裁剪）；
# - SDK 内部使用反射与 AIDL（com.mcs.aidl.*），R8 需要整包保留。
# -----------------------------------------------------------------------------
-keep class com.heytap.msp.** { *; }
-keep class com.heytap.mcssdk.** { *; }
-keep class com.mcs.aidl.** { *; }
-dontwarn com.heytap.**
-dontwarn com.mcs.**

# -----------------------------------------------------------------------------
# OPPO 账号服务 OAuth SDK（com.heytap.accountsdk:account-oauth-web-sdk:1.1.3）
# - AAR 内含 OAuthWebActivity（AppCompat）/ ResultReceiver / ContentProvider 客户端；
# - SDK 内部通过反射调用账号 App 与 IPC（AcOauthRefInvokeUtil / AcOauthBinderProvider），需保留类名。
# -----------------------------------------------------------------------------
-keep class com.platform.account.** { *; }
-keep class com.platform.usercenter.** { *; }
-dontwarn com.platform.**
-dontwarn com.heytap.accountsdk.**

# -----------------------------------------------------------------------------
# HONOR ID SDK（honorid-opensdk 8.0.1.351）
# - 通过 AIDL/IPC 与 com.hihonor.id 交互，内部有反射与 Parcelable；
# - 运行时类名不能混淆（否则 parseAuthResultFromIntent / 账号服务绑定失败）。
# -----------------------------------------------------------------------------
-keep class com.hihonor.cloudservice.** { *; }
-keep class com.hihonor.honorid.** { *; }
-keep class com.hihonor.cloudservice.framework.aidl.** { *; }
-dontwarn com.hihonor.**

# -----------------------------------------------------------------------------
# vivo 推送（vpush_clientSDK 4.1.7.0_517）
# - Manifest 声明自定义 Receiver，SDK 按类名回调；内部 AIDL/反射需要整包保留。
# -----------------------------------------------------------------------------
-keep class com.vivo.push.** { *; }
-keep class com.vivo.vms.** { *; }
-keep class com.furina.classtools.data.push.VivoPushReceiver { *; }
-dontwarn com.vivo.**
