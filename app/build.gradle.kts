plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") // БЕЗ ВЕРСИИ (берется из корневого файла)
    id("dagger.hilt.android.plugin")
    id("kotlin-kapt")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.docs.scanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.docs.scanner"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "2.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX Core - ПОСЛЕДНЯЯ ВЕРСИЯ 1.17.0
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0") // ОБНОВЛЕНО
    implementation("androidx.activity:activity-compose:1.12.2") // ОБНОВЛЕНО
    
    // Compose BOM - ПОСЛЕДНЯЯ ВЕРСИЯ 2025.12.00 (17 декабря 2025)
    implementation(platform("androidx.compose:compose-bom:2025.12.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Hilt - ПОСЛЕДНЯЯ ВЕРСИЯ 2.57.2
    implementation("com.google.dagger:hilt-android:2.57.2")
    kapt("com.google.dagger:hilt-android-compiler:2.57.2")
    ksp("com.google.dagger:hilt-compiler:2.57.2")

    // Room - ПОСЛЕДНЯЯ СТАБИЛЬНАЯ ВЕРСИЯ 2.8.0 (10 сентября 2025)
    implementation("androidx.room:room-runtime:2.8.0")
    implementation("androidx.room:room-ktx:2.8.0")
    ksp("androidx.room:room-compiler:2.8.0")

    // Coil 3 - ПОСЛЕДНЯЯ ВЕРСИЯ 3.3.0 (22 июля 2025)
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")

    // ML Kit Document Scanner
    implementation("com.google.mlkit:document-scanner:16.0.0-beta1")

    // Retrofit & Gson - последние версии
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // Google Drive API - обновлены до последних версий
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.http-client:google-http-client-gson:1.45.1")
    implementation("com.google.api-client:google-api-client-android:2.7.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20241215-2.0.0")

    // EncryptedSharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Coroutines - последняя стабильная версия
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Debug зависимости
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

ksp {
    arg("incremental", "true")
    arg("room.incremental", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}