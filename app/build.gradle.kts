plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" // ДОБАВЛЕНО для Compose
    id("dagger.hilt.android.plugin")
    id("kotlin-kapt")
    id("com.google.devtools.ksp") // Версия указана в build.gradle (project level)
}

android {
    namespace = "com.docs.scanner"
    compileSdk = 35 // Обновлено

    defaultConfig {
        applicationId = "com.docs.scanner"
        minSdk = 24
        targetSdk = 35 // Обновлено
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

    // ИСПРАВЛЕНО: заменён kotlinOptions на compilerOptions
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }

    buildFeatures {
        compose = true
    }

    // УДАЛЕНО: composeOptions больше не нужен с новым плагином
    // composeOptions {
    //     kotlinCompilerExtensionVersion = "1.5.14"
    // }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0") // Обновлено
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7") // Обновлено
    implementation("androidx.activity:activity-compose:1.9.3") // Обновлено
    implementation(platform("androidx.compose:compose-bom:2024.12.01")) // Обновлено
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.52") // Обновлено
    kapt("com.google.dagger:hilt-android-compiler:2.52") // Обновлено
    ksp("com.google.dagger:hilt-compiler:2.52") // Обновлено

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coil for images
    implementation("io.coil-kt:coil-compose:2.7.0")

    // ML Kit Document Scanner
    implementation("com.google.mlkit:document-scanner:16.0.0-beta1")

    // Retrofit & Gson
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // Google Drive API
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.http-client:google-http-client-gson:1.45.1") // Обновлено
    implementation("com.google.api-client:google-api-client-android:2.7.0") // Обновлено
    implementation("com.google.apis:google-api-services-drive:v3-rev20241108-2.0.0") // Обновлено

    // EncryptedSharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0") // Обновлено

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

ksp {
    arg("incremental", "true")
    arg("room.incremental", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}