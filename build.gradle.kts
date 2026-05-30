/*
 * DocumentScanner - Root build.gradle.kts
 * Top-level build configuration
 * Version: 8.0.0 - STABLE PRODUCTION CLASS PATHS
 */

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    
    // Google & Firebase plugins classpath definitions
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
    id("com.google.firebase.firebase-perf") version "1.4.2" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
} 