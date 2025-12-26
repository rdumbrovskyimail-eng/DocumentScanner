# ============================================
# KOTLIN
# ============================================
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ============================================
# COROUTINES
# ============================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ✅ НОВОЕ: Избежать предупреждений о CancellationException
-dontwarn kotlinx.coroutines.CancellationException

# ============================================
# GSON
# ============================================
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ============================================
# RETROFIT
# ============================================
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ============================================
# OKHTTP
# ============================================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ============================================
# ROOM
# ============================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ============================================
# ML KIT
# ============================================
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# ============================================
# GEMINI API MODELS
# ============================================
-keep class com.docs.scanner.data.remote.gemini.** { *; }
-keep class com.docs.scanner.domain.model.** { *; }

# ============================================
# COMPOSE
# ============================================
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ✅ НОВОЕ: Compose runtime stability
-keepclassmembers class androidx.compose.runtime.** {
    <fields>;
    <methods>;
}

# ============================================
# HILT
# ============================================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel
-keepclasseswithmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

# ✅ НОВОЕ: ViewModels
-keep class com.docs.scanner.presentation.**.ViewModel { *; }
-keep class com.docs.scanner.presentation.**.*ViewModel { *; }

# ============================================
# BUILDCONFIG
# ============================================
# ✅ НОВОЕ: Keep BuildConfig
-keep class com.docs.scanner.BuildConfig { *; }

# ============================================
# ENCRYPTED STORAGE
# ============================================
# ✅ НОВОЕ: Keep security-crypto classes
-keep class androidx.security.crypto.** { *; }
-keep class com.docs.scanner.data.local.security.** { *; }

# ============================================
# DATASTORE
# ============================================
# ✅ НОВОЕ: Keep DataStore preferences
-keep class androidx.datastore.** { *; }
-keep class com.docs.scanner.data.local.preferences.** { *; }

# ============================================
# GOOGLE DRIVE API
# ============================================
# ✅ НОВОЕ: Keep Google API classes
-keep class com.google.api.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-dontwarn com.google.api.**

# ============================================
# SERIALIZATION
# ============================================
# ✅ НОВОЕ: Keep serialized names
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ============================================
# GENERAL
# ============================================
# ✅ НОВОЕ: Remove logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# ✅ НОВОЕ: Remove println in release
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
}

# ============================================
# SUPPRESS WARNINGS
# ============================================
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn edu.umd.cs.findbugs.annotations.**