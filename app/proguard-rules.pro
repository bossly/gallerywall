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

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# WorkManager / CoroutineWorker
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keepclassmembers class * extends androidx.work.CoroutineWorker {
    <init>(android.content.Context,androidx.work.WorkerParameters);
}

# Google MediaPipe Rules
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Google Protobuf Rules
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# Preserve all generated protobuf message fields and methods
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
    <methods>;
}

-keepclassmembers class * extends com.google.protobuf.GeneratedMessage {
    <fields>;
    <methods>;
}

-keepclassmembers class * extends com.google.protobuf.GeneratedMessageV3 {
    <fields>;
    <methods>;
}

# Flogger Rules
-keep class com.google.common.flogger.** { *; }
-dontwarn com.google.common.flogger.**