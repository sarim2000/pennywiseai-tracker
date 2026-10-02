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
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# LiteRT-LM
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**

# Room, Hilt, Compose, WorkManager, kotlinx.serialization and Markdown ship
# their own consumer rules, and nothing here serializes via reflection (backup
# uses kotlinx.serialization codegen). Blanket `-keep X.** { *; }` rules for them
# disabled R8 on ~70% of classes (all of material-icons-extended) — Play Console
# flagged it as 34% DEX optimisation. Only add a keep with a concrete crash.

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# General Android
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Protobuf
-dontwarn com.google.protobuf.Internal$ProtoMethodMayReturnNull
-dontwarn com.google.protobuf.Internal$ProtoNonnullApi
-dontwarn com.google.protobuf.ProtoField
-dontwarn com.google.protobuf.ProtoPresenceBits
-dontwarn com.google.protobuf.ProtoPresenceCheckedField

# SLF4J (used by OpenCSV)
-dontwarn org.slf4j.impl.StaticLoggerBinder

# PDFBox Android (used for PDF statement parsing)
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.filter.JPXFilter
-dontwarn com.tom_roush.pdfbox.pdmodel.graphics.image.SampledImageReader
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn org.bouncycastle.**
-dontwarn org.apache.harmony.**
-dontwarn javax.xml.stream.**