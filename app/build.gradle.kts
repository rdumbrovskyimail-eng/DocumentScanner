import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// ================================================================================
// GOOGLE SERVICES LOGIC (SAFE)
// ================================================================================
val googleServicesFile = rootProject.file("app/google-services.json")

if (googleServicesFile.exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    logger.lifecycle("✓ Google Services: Enabled")
} else {
    logger.warn("⚠️  Google Services: Disabled (google-services.json missing)")
    logger.warn("    Firebase features will be unavailable.")
}

// ================================================================================
// SECRETS (Configuration Cache Safe)
// ================================================================================
val localProperties = providers.fileContents(
    rootProject.layout.projectDirectory.file("local.properties")
).asText.orNull?.let { content ->
    Properties().apply {
        load(content.byteInputStream())
    }
} ?: Properties()

fun getLocalProperty(key: String): String = 
    localProperties.getProperty(key) ?: System.getenv(key) ?: ""

// ================================================================================
// ANDROID CONFIG
// ================================================================================
android {
    namespace = "com.docs.scanner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.docs.scanner"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "3.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "GEMINI_API_KEY", "\"${getLocalProperty("GEMINI_API_KEY")}\"")
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"${getLocalProperty("GOOGLE_DRIVE_CLIENT_ID")}\"")
    }

    signingConfigs {
        create("release") {
            val keyStoreFile = file(getLocalProperty("RELEASE_STORE_FILE").ifEmpty { "release.keystore" })
            if (keyStoreFile.exists()) {
                storeFile = keyStoreFile
                storePassword = getLocalProperty("RELEASE_STORE_PASSWORD")
                keyAlias = getLocalProperty("RELEASE_KEY_ALIAS")
                keyPassword = getLocalProperty("RELEASE_KEY_PASSWORD")
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
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
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
            "-opt-in=kotlinx.coroutines.FlowPreview"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/LICENSE*",
                "/META-INF/DEPENDENCIES",
                "/META-INF/INDEX.LIST"
            )
        }
    }
}

// ================================================================================
// DEPENDENCIES
// ================================================================================
dependencies {
    // --- Bundles ---
    implementation(libs.bundles.compose)
    implementation(libs.bundles.networking)
    implementation(libs.bundles.room)
    implementation(libs.bundles.google.drive)
    implementation(libs.bundles.coroutines)

    // --- Core ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.material)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.biometric)
    
    // --- DI ---
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation)
    ksp(libs.hilt.compiler)
    ksp(libs.room.compiler)

    // --- Image Loading ---
    implementation(libs.coil.compose)
    implementation(libs.coil.network)

    // --- ML Kit ---
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.document.scanner)
    
    // --- Firebase ---
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.vertexai)
    implementation(libs.firebase.analytics)

    // --- Data ---
    implementation(libs.security.crypto)
    implementation(libs.datastore.prefs)

    // --- Utils ---
    implementation(libs.timber)
    debugImplementation(libs.leakcanary)

    // --- Desugaring ---
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // --- Testing ---
    testImplementation(libs.bundles.testing)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}