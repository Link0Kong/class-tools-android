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
