# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Xposed Framework
-keep class de.robv.android.xposed.** { *; }
-keep class com.suseoaa.locationspoofer.xposed.LocationHooker { *; }
-keep interface de.robv.android.xposed.** { *; }

# AMap 3DMap & Location SDK
-keep class com.amap.api.** { *; }
-keep class com.autonavi.** { *; }
-dontwarn com.amap.api.**
-dontwarn com.autonavi.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.** { *; }

# Models / Utils
-keep class com.suseoaa.locationspoofer.utils.ConfigManager { *; }
-keep class com.suseoaa.locationspoofer.utils.LSPosedManager { *; }
-keep class com.suseoaa.locationspoofer.provider.** { *; }

# General safety for Android lifecycle
-keep class * extends android.app.Application { *; }
-keep class * extends android.app.Activity { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.ContentProvider { *; }