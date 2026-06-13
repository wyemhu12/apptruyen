# ============================================================
# ProGuard rules for AppTruyen release build
# ============================================================

# --- Kotlin ---
-keepattributes *Annotation*
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn kotlin.**

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# --- Hilt / Dagger ---
-dontwarn dagger.internal.codegen.**
-keepclassmembers,allowobfuscation class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
    @dagger.hilt.* <fields>;
}

# --- OkHttp 5 ---
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Jsoup ---
-keeppackagenames org.jsoup.nodes
-keep class org.jsoup.** { *; }
-dontwarn com.google.re2j.Matcher
-dontwarn com.google.re2j.Pattern

# --- Coil 3 ---
-dontwarn coil3.**

# --- DataStore / Protobuf ---
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# --- AndroidX Media (MediaSession) ---
-keep class androidx.media.** { *; }

# --- Compose ---
-dontwarn androidx.compose.**

# --- Keep data classes used by Room entities ---
-keep class com.personal.apptruyen.data.local.entity.** { *; }
-keep class com.personal.apptruyen.data.model.** { *; }

# --- Kotlinx Serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.personal.apptruyen.data.model.**$$serializer { *; }
-keepclassmembers class com.personal.apptruyen.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.personal.apptruyen.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
