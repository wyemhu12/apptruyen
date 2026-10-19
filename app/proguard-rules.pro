# ============================================================
# ProGuard rules for AppTruyen release build
# ============================================================

# --- Kotlin ---
-keepattributes *Annotation*
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn kotlin.**

# --- Room 3 ---
-keep class * extends androidx.room3.RoomDatabase { *; }
-keep @androidx.room3.Entity class *
-keep @androidx.room3.Dao class *
-keepclassmembers class * extends androidx.room3.RoomDatabase {
    <init>();
}
-keep class * extends androidx.room3.RoomDatabaseConstructor {
    <init>();
    public *** initialize();
}

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
-keep class org.jsoup.nodes.** { *; }
-keep class org.jsoup.select.** { *; }
-keep class org.jsoup.parser.** { *; }
-dontwarn org.jsoup.**
-dontwarn com.google.re2j.Matcher
-dontwarn com.google.re2j.Pattern

# --- Coil 3 ---
-dontwarn coil3.**

# --- DataStore / Protobuf ---
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# --- AndroidX Media3 (MediaSessionService) ---
-keepclassmembers class * extends androidx.media3.session.MediaSessionService {
    <init>();
}
-keep class androidx.media3.session.MediaSessionService
-keep class androidx.media3.session.MediaSession
-keep class androidx.media3.common.MediaMetadata

# --- Compose ---
-dontwarn androidx.compose.**

# --- Keep data classes used by Room entities & kotlinx.serialization ---
-keep class com.personal.apptruyen.data.local.entity.** { *; }
-keep @kotlinx.serialization.Serializable class com.personal.apptruyen.data.model.** { *; }

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
