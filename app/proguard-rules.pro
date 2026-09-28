-keep class org.opencv.** { *; }
-keepclassmembers class org.opencv.** { *; }
-keepclassmembers class * { native <methods>; }
-dontwarn org.opencv.**

-keep class com.google.mlkit.vision.text.** { *; }
-keep class com.google.mlkit.vision.common.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-dontwarn com.google.mlkit.**

-keep class com.tom_roush.pdfbox.** { *; }
-keepclassmembers class com.tom_roush.pdfbox.** { *; }
-dontwarn java.awt.**
-dontwarn javax.imageio.**

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
