# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve source file and line numbers for readable Crashlytics stack traces.
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*

# Keep analytics package types and DataStore/Room models from aggressive obfuscation.
-keep class com.example.analytics.** { *; }
-keep class com.example.data.** { *; }
-keep class com.example.worker.BatchValidationWorker { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# Preserve domain config/rule enums and data models serialized to JSON and prefs.
-keep class com.example.domain.CustomHttpConfig { *; }
-keep class com.example.domain.CustomParseRule { *; }
-keep class com.example.domain.CustomCheckpointRule { *; }
-keep class com.example.domain.ConfigBlock { *; }
-keep enum com.example.domain.** { *; }

# OkHttp / platform warnings safe to ignore on Android.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.bouncycastle.**

