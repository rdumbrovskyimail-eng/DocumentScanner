# ================================================================================
# DocumentScanner - ProGuard Rules
# Optimized for R8 Full Mode + Android 2026 Standards
# Version: 8.0.0 - PRODUCTION READY 101%
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
# GOOGLE DRIVE & GSON
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

# ML Kit Text Recognition specific
-keep class com.google.mlkit.vision.text.** { *; }
-keep class com.google.mlkit.nl.languageid.** { *; }

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

# OkHttp 5.x specific rules
-keep class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ============================================
# HILT & DAGGER
# ============================================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel { *; }

-dontwarn dagger.hilt.**
-dontwarn com.google.dagger.**

# Hilt FastInit support
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ============================================
# ROOM DATABASE
# ============================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

-dontwarn androidx.room.paging.**

# Room Paging 3 support
-keep class * extends androidx.paging.PagingSource

# ============================================
# ANDROIDX & COMPOSE
# ============================================
-keep class androidx.compose.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.navigation.** { *; }

-dontwarn androidx.compose.**
-dontwarn androidx.lifecycle.**

# Compose Compiler optimizations
-assumenosideeffects class androidx.compose.runtime.ComposerKt {
    boolean isTraceInProgress();
    void traceEventStart(int, int, int, java.lang.String);
    void traceEventEnd();
}

# ============================================
# SECURITY CRYPTO
# ============================================
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }

# Protect encrypted API keys storage
-keepclassmembers class com.docs.scanner.data.local.security.ApiKeyData {
    !private <fields>;
}

-keep class com.docs.scanner.data.local.security.EncryptedKeyStorage { *; }

# ============================================
# COIL IMAGE LOADING
# ============================================
-keep class coil.** { *; }
-keep class coil3.** { *; }
-dontwarn coil.**
-dontwarn coil3.**

# Coil 3.x specific rules
-keep class coil3.network.NetworkFetcher
-keep class coil3.network.okhttp.OkHttpNetworkFetcherFactory
-keep class coil.compose.** { *; }

# ============================================
# WORKMANAGER
# ============================================
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class * extends androidx.work.ListenableWorker

# ============================================
# TIMBER LOGGING
# ============================================
# Keep Timber class structure but remove logs in release
-keep class timber.log.Timber { *; }
-keep class timber.log.Timber$Tree { *; }

# ============================================
# APP SPECIFIC - DOMAIN MODELS
# ============================================
-keep class com.docs.scanner.BuildConfig { *; }
-keep class com.docs.scanner.data.model.** { *; }
-keep class com.docs.scanner.domain.model.** { *; }
-keep class com.docs.scanner.domain.core.** { *; }

# Keep all data classes with @Serializable
-keepclassmembers @kotlinx.serialization.Serializable class com.docs.scanner.** {
    <fields>;
    <methods>;
}

# Keep sealed interfaces/classes for domain errors and processing status
-keep class * extends com.docs.scanner.domain.core.DomainError { *; }
-keep class * extends com.docs.scanner.domain.core.ProcessingStatus { *; }

# Keep MLKit scanner enums and data classes
-keep class com.docs.scanner.data.remote.mlkit.OcrScriptMode { *; }
-keep class com.docs.scanner.data.remote.mlkit.ConfidenceLevel { *; }
-keep class com.docs.scanner.data.remote.mlkit.WordWithConfidence { *; }
-keep class com.docs.scanner.data.remote.mlkit.OcrResultWithConfidence { *; }
-keep class com.docs.scanner.data.remote.mlkit.OcrTestResult { *; }

# Keep MLKitScanner class
-keep class com.docs.scanner.data.remote.mlkit.MLKitScanner { *; }

# Keep Settings ViewModels and UI state
-keep class com.docs.scanner.presentation.screens.settings.SettingsViewModel { *; }
-keep class com.docs.scanner.presentation.screens.settings.LocalBackup { *; }
-keep class com.docs.scanner.presentation.screens.settings.components.MlkitSettingsState { *; }

# ============================================
# LOGGING REMOVAL (Release Only)
# ============================================
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
}

-assumenosideeffects class timber.log.Timber {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
}

# Remove BuildConfig.DEBUG checks in release
-assumenosideeffects class com.docs.scanner.BuildConfig {
    public static boolean DEBUG return false;
}

# Remove trace logging in production
-assumenosideeffects class androidx.tracing.Trace {
    public static void beginSection(java.lang.String);
    public static void endSection();
}

# ============================================
# OPTIMIZATION FLAGS
# ============================================
-optimizationpasses 5
-dontpreverify
-repackageclasses ''
-allowaccessmodification
-optimizations !code/simplification/cast,!field/*,!class/merging/*

# R8 Full Mode aggressive optimizations
-assumevalues class android.os.Build$VERSION {
    int SDK_INT return 26..36;
}

# Assume non-null for Kotlin
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkNotNull(java.lang.Object);
    static void checkNotNull(java.lang.Object, java.lang.String);
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullParameter(java.lang.Object, java.lang.String);
    static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkFieldIsNotNull(java.lang.Object, java.lang.String);
}

# ============================================
# SSL/TLS & CERTIFICATE PINNING
# ============================================
# Keep certificate pinning classes if implemented
-keep class okhttp3.CertificatePinner { *; }
-keep class okhttp3.CertificatePinner$Pin { *; }

# ============================================
# KEEP LINE NUMBERS FOR CRASH REPORTS
# ============================================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================
# ADDITIONAL SECURITY
# ============================================
# Remove sensitive method names in stack traces (production only)
# Uncomment for extra security:
# -obfuscationdictionary proguard-dictionary.txt
# -classobfuscationdictionary proguard-dictionary.txt
# -packageobfuscationdictionary proguard-dictionary.txt

# ════════════════════════════════════════════════════════════════════
# GOOGLE DRIVE SDK - Missing Classes Fix (R8 Compatibility)
# ════════════════════════════════════════════════════════════════════

# Apache HTTP (используется Google Drive API, но эти классы не нужны на Android)
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
-dontwarn org.apache.http.**
-dontwarn org.apache.commons.codec.**

# Joda Time (используется Google Tink crypto)
-dontwarn org.joda.time.**

# Keep Google HTTP Transport classes
-keep class com.google.api.client.http.** { *; }
-keep class com.google.api.client.json.** { *; }
-keep class com.google.api.client.util.** { *; }

# Keep Google Auth classes
-keep class com.google.auth.** { *; }
-keepclassmembers class com.google.auth.** { *; }

# ════════════════════════════════════════════════════════════════════

# ============================================
# END OF PROGUARD RULES
# ================================================================================