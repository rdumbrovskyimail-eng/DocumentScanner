plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.docs.scanner"
    compileSdk = 35  // Стабильная версия вместо 36

    defaultConfig {
        applicationId = "com.docs.scanner"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "2.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        vectorDrawables {
            useSupportLibrary = true
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
            // Signing config для release
            // signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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
                "/META-INF/LICENSE.md",
                "/META-INF/LICENSE-notice.md"
            )
        }
    }
    
    lint {
        abortOnError = false
        checkReleaseBuilds = true
        warningsAsErrors = false
    }
}

dependencies {
    // ══════════════════════════════════════════════════════════════
    // ANDROIDX CORE
    // ══════════════════════════════════════════════════════════════
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    
    // FIX: Memory leak prevention
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // ══════════════════════════════════════════════════════════════
    // COMPOSE
    // ══════════════════════════════════════════════════════════════
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation("com.google.android.material:material:1.12.0")
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // ══════════════════════════════════════════════════════════════
    // HILT (DEPENDENCY INJECTION)
    // ══════════════════════════════════════════════════════════════
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // ══════════════════════════════════════════════════════════════
    // ROOM DATABASE
    // ══════════════════════════════════════════════════════════════
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ══════════════════════════════════════════════════════════════
    // NETWORKING
    // ══════════════════════════════════════════════════════════════
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.gson)

    // ══════════════════════════════════════════════════════════════
    // IMAGE LOADING
    // ══════════════════════════════════════════════════════════════
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // ══════════════════════════════════════════════════════════════
    // ML KIT
    // ══════════════════════════════════════════════════════════════
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.mlkit.document.scanner)

    // ══════════════════════════════════════════════════════════════
    // FIREBASE AI (GEMINI)
    // ══════════════════════════════════════════════════════════════
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-ai")

    // ══════════════════════════════════════════════════════════════
    // GOOGLE SERVICES
    // ══════════════════════════════════════════════════════════════
    implementation(libs.play.services.auth)
    implementation(libs.google.api.client.android)
    implementation(libs.google.http.client.gson)
    implementation(libs.google.api.services.drive)

    // ══════════════════════════════════════════════════════════════
    // SECURITY
    // ══════════════════════════════════════════════════════════════
    implementation(libs.androidx.security.crypto)

    // ══════════════════════════════════════════════════════════════
    // COROUTINES
    // ══════════════════════════════════════════════════════════════
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // ══════════════════════════════════════════════════════════════
    // DATASTORE
    // ══════════════════════════════════════════════════════════════
    implementation(libs.androidx.datastore.preferences)

    // ══════════════════════════════════════════════════════════════
    // LOGGING (FIX: Заменяет println)
    // ══════════════════════════════════════════════════════════════
    implementation("com.jakewharton.timber:timber:5.0.1")

    // ══════════════════════════════════════════════════════════════
    // TESTING (Расширено)
    // ══════════════════════════════════════════════════════════════
    testImplementation(libs.junit)
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
    arg("room.generateKotlin", "true")
}