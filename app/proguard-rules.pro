# ================================================================================
# DocumentScanner - ProGuard Rules
# Optimized for R8 Full Mode + Android 2026 Standards
# Version: 5.0.0 - Final Gold Master
# ================================================================================

# ============================================
# KOTLIN & COROUTINES
# ============================================
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlinx.coroutines.CancellationException
-dontwarn kotlinx.coroutines.flow.**

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}

-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <init>(...);
}

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# ============================================
# GOOGLE DRIVE & GSON (Legacy Support)
# ============================================
-keepattributes Signature
-keepattributes *Annotation*

-keep class com.google.gson.** { *; }
-keep class com.google.api.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }

-dontwarn com.google.api.**
-dontwarn com.google.common.**

-keep class * implements com.google.api.client.json.GenericJson { *; }

# ============================================
# FIREBASE VERTEX AI & ML KIT
# ============================================
-keep class com.google.firebase.** { *; }
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }

-dontwarn com.google.firebase.**
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# ============================================
# RETROFIT & OKHTTP
# ============================================
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ============================================
# HILT & DAGGER
# ============================================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel { *; }

-dontwarn dagger.hilt.**
-dontwarn com.google.dagger.**

# ============================================
# ROOM DATABASE
# ============================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

-dontwarn androidx.room.paging.**

# ============================================
# ANDROIDX & COMPOSE
# ============================================
-keep class androidx.compose.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.navigation.** { *; }

-dontwarn androidx.compose.**
-dontwarn androidx.lifecycle.**

# ============================================
# SECURITY CRYPTO
# ============================================
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }

# ============================================
# COIL IMAGE LOADING
# ============================================
-keep class coil3.** { *; }
-dontwarn coil3.**

# ============================================
# APP SPECIFIC
# ============================================
-keep class com.docs.scanner.BuildConfig { *; }
-keep class com.docs.scanner.data.model.** { *; }
-keep class com.docs.scanner.domain.model.** { *; }

-keepclassmembers @kotlinx.serialization.Serializable class com.docs.scanner.** {
    <fields>;
    <methods>;
}

# ============================================
# LOGGING REMOVAL (Release)
# ============================================
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

-assumenosideeffects class timber.log.Timber {
    public static *** v(...);
    public static *** d(...);
}

# ============================================
# OPTIMIZATION FLAGS
# ============================================
-optimizationpasses 5
-dontpreverify
-repackageclasses ''
-allowaccessmodification
-optimizations !code/simplification/cast,!field/*,!class/merging/*