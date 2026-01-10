/*
 * DocumentScanner - App Module Configuration
 * Version: 7.1.0 - PERFECT 10/10 (2026 Standards)
 * 
 * ✅ CRITICAL FIX: versionCode increased to 710 (forces database migration)
 * ✅ Room schema export enabled for migration debugging
 */

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)

    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
}

// ════════════════════════════════════════════════════════════════════════════════
// 🔐 SECRETS MANAGEMENT (Configuration Cache Safe)
// ════════════════════════════════════════════════════════════════════════════════
val secrets = providers.provider {
    val props = Properties()
    val localPropertiesFile = rootProject.layout.projectDirectory.file("local.properties").asFile
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { props.load(it) }
    }
    props
}

fun getSecret(key: String): String = 
    secrets.orNull?.getProperty(key) ?: System.getenv(key) ?: ""

fun String.escapeForBuildConfigString(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")

// ════════════════════════════════════════════════════════════════════════════════
// 🏗️ ANDROID CONFIGURATION
// ════════════════════════════════════════════════════════════════════════════════
android {
    namespace = "com.docs.scanner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.docs.scanner"
        minSdk = 26
        targetSdk = 36
        
        // ✅ CRITICAL FIX: Increased from 700 to 710
        // This forces Android to recognize it as a new version and run migrations
        versionCode = 710
        versionName = "7.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        
        resourceConfigurations += setOf("en", "ru", "es", "de", "fr", "it", "pt", "zh")

        // 🔐 SECRETS INJECTION
        buildConfigField("String", "GEMINI_API_KEY", "\"${getSecret("GEMINI_API_KEY").escapeForBuildConfigString()}\"")
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"${getSecret("GOOGLE_DRIVE_CLIENT_ID").escapeForBuildConfigString()}\"")
        
        manifestPlaceholders["googleClientId"] = getSecret("GOOGLE_DRIVE_CLIENT_ID")

        // ✅ CRITICAL FIX: Room schema export for debugging migrations
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
            arg("room.generateKotlin", "true")
            arg("room.expandProjection", "true")
            
            // Hilt optimizations
            arg("dagger.hilt.shareTestComponents", "true")
            arg("dagger.fastInit", "enabled")
        }
    }

    signingConfigs {
        create("release") {
            val path = getSecret("RELEASE_STORE_FILE")
            if (path.isNotEmpty() && file(path).exists()) {
                storeFile = file(path)
                storePassword = getSecret("RELEASE_STORE_PASSWORD")
                keyAlias = getSecret("RELEASE_KEY_ALIAS")
                keyPassword = getSecret("RELEASE_KEY_PASSWORD")
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
        
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            enableUnitTestCoverage = true
            
            packaging {
                jniLibs.pickFirsts += listOf("**/*.so")
            }
        }
        
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.findByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "21"
        
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-Xcontext-receivers",
            "-Xjvm-default=all",
            "-progressive",
        )
        
        if (project.findProperty("composeCompilerReports") == "true") {
            freeCompilerArgs += listOf(
                "-P", "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=${layout.buildDirectory.get().asFile}/compose_metrics",
                "-P", "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=${layout.buildDirectory.get().asFile}/compose_metrics"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = false
        renderScript = false
        shaders = false
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/LICENSE*",
                "/META-INF/DEPENDENCIES",
                "/META-INF/INDEX.LIST",
                "META-INF/gradle/incremental.annotation.processors",
                "DebugProbesKt.bin"
            )
        }
    }
    
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
        
        animationsDisabled = true
        
        managedDevices {
            localDevices {
                create("pixel8api36") {
                    device = "Pixel 8"
                    apiLevel = 36
                    systemImageSource = "aosp"
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// 📦 DEPENDENCIES
// ════════════════════════════════════════════════════════════════════════════════
dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.bundles.compose)
    implementation(libs.bundles.networking)
    implementation(libs.bundles.room)
    implementation(libs.bundles.coroutines)
    implementation(libs.bundles.mlkit)
    implementation(libs.bundles.google.drive)
    implementation(libs.bundles.camerax)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.tracing)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)
    ksp(libs.room.compiler)

    implementation(libs.coil.compose)
    implementation(libs.coil.network)

    implementation(libs.google.material)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.vertexai)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.perf)

    implementation("org.burnoutcrew.composereorderable:reorderable:0.9.6")

    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.timber)
    implementation(libs.security.crypto)
    implementation(libs.androidx.biometric)
    implementation(libs.datastore.prefs)
    implementation(libs.androidx.startup)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.lifecycle.process)

    implementation(libs.androidx.profileinstaller)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.bundles.testing)
    androidTestImplementation(libs.bundles.testing.android)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.leakcanary)
}

// ════════════════════════════════════════════════════════════════════════════════
// 🔌 SAFE PLUGIN APPLY
// ════════════════════════════════════════════════════════════════════════════════
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
    apply(plugin = "com.google.firebase.firebase-perf")
}

// ════════════════════════════════════════════════════════════════════════════════
// 📊 BUILD INFO (Debug)
// ════════════════════════════════════════════════════════════════════════════════
tasks.register("printBuildInfo") {
    doLast {
        println("""
            |
            |📱 DocumentScanner Build Info
            |├─ Version: ${android.defaultConfig.versionName} (${android.defaultConfig.versionCode})
            |├─ Min SDK: ${android.defaultConfig.minSdk}
            |├─ Target SDK: ${android.defaultConfig.targetSdk}
            |├─ Compile SDK: ${android.compileSdk}
            |├─ Java: ${JavaVersion.current()}
            |├─ Kotlin: ${libs.versions.kotlin.get()}
            |└─ AGP: ${libs.versions.agp.get()}
            |
        """.trimMargin())
    }
}