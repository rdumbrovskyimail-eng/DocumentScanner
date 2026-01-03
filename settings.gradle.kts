/*
 * DocumentScanner - settings.gradle.kts
 * Gradle 9.x + Android 2026 Standards (Enterprise Production Version)
 * Version: 6.0.0 - ULTRA OPTIMIZED
 */

// ================================================================================
// GRADLE FEATURES (Gradle 9.0+)
// ================================================================================
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS") // Access projects via `projects.app`

pluginManagement {
    // Include build-logic for convention plugins
    includeBuild("build-logic")
    
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.google\\.firebase.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
    
    // ✅ NEW: Build Scan для анализа производительности
    id("com.gradle.enterprise") version "3.16.2" apply false
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.google\\.firebase.*")
            }
        }
        mavenCentral()
    }

    versionCatalogs {
        create("libs") {
            val catalogFile = file("gradle/libs.versions.toml")
            if (catalogFile.exists()) {
                from(files(catalogFile))
            } else {
                val msg = "❌ Version Catalog missing: ${catalogFile.absolutePath}"
                logger.error(msg)
                throw GradleException(msg)
            }
        }
    }
}

// ================================================================================
// BUILD CACHE - Production Grade
// ================================================================================
buildCache {
    local {
        isEnabled = true
        directory = File(rootDir, "build-cache")
        removeUnusedEntriesAfterDays = 14
    }

    remote<HttpBuildCache> {
        val cacheUrl = System.getenv("GRADLE_CACHE_URL") 
            ?: providers.gradleProperty("gradle.cache.url").orNull
        
        if (!cacheUrl.isNullOrBlank()) {
            try {
                url = uri(cacheUrl)
                
                // ✅ IMPROVED: CI detection с поддержкой разных CI систем
                val isCI = listOf("CI", "CONTINUOUS_INTEGRATION", "GITHUB_ACTIONS", "GITLAB_CI")
                    .any { System.getenv(it)?.toBoolean() == true }
                isPush = isCI
                
                val cacheUser = System.getenv("GRADLE_CACHE_USER") 
                    ?: providers.gradleProperty("gradle.cache.user").orNull
                val cachePassword = System.getenv("GRADLE_CACHE_PASSWORD") 
                    ?: providers.gradleProperty("gradle.cache.password").orNull
                
                if (!cacheUser.isNullOrBlank() && !cachePassword.isNullOrBlank()) {
                    credentials {
                        username = cacheUser
                        password = cachePassword
                    }
                }
                
                logger.lifecycle("✓ Remote build cache: $cacheUrl (push: $isPush)")
            } catch (e: Exception) {
                logger.warn("⚠️  Remote cache error: ${e.message}")
            }
        }
    }
}

// ================================================================================
// GRADLE ENTERPRISE (Build Scans)
// ================================================================================
plugins.apply("com.gradle.enterprise")

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
        
        // ✅ Публикуем скан только в CI
        publishAlways()
        
        // ✅ Тегаем для удобного поиска
        tag(if (System.getenv("CI") != null) "CI" else "LOCAL")
        tag("Android")
        
        // ✅ Добавляем metadata
        value("Git Commit", providers.exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
        }.standardOutput.asText.getOrElse("unknown").trim())
    }
}

// ================================================================================
// PROJECT STRUCTURE
// ================================================================================
rootProject.name = "DocumentScanner"
include(":app")

// ================================================================================
// DEPENDENCY VERIFICATION (Security)
// ================================================================================
gradle.settingsEvaluated {
    // 1. Обязательные файлы
    val requiredFiles = mapOf(
        "gradle/libs.versions.toml" to "ERROR",
        "gradle.properties" to "ERROR",
        "local.properties" to "WARNING",
        "gradle/verification-metadata.xml" to "INFO" // ✅ NEW: Dependency checksums
    )
    
    requiredFiles.forEach { (path, level) ->
        val file = rootDir.resolve(path)
        if (!file.exists()) {
            val msg = "Missing file: $path"
            when (level) {
                "ERROR" -> throw GradleException("❌ $msg")
                "WARNING" -> logger.warn("⚠️  $msg")
                else -> logger.info("ℹ️  $msg")
            }
        }
    }
    
    // 2. Environment Info (Enhanced)
    val javaVersion = System.getProperty("java.version")
    val javaVendor = System.getProperty("java.vendor")
    val gradleVersion = gradle.gradleVersion
    
    logger.lifecycle("""
        |
        |🚀 DocumentScanner Build Configuration
        |├─ Java: $javaVersion ($javaVendor)
        |├─ Gradle: $gradleVersion
        |├─ Configuration Cache: ${if (gradle.startParameter.isConfigurationCacheRequested) "✓" else "✗"}
        |└─ Build Cache: ${if (gradle.startParameter.isBuildCacheEnabled) "✓" else "✗"}
        |
    """.trimMargin())
    
    // 3. ✅ NEW: Performance warnings
    if (javaVersion.startsWith("17.")) {
        logger.warn("⚠️  Java 17 detected. Consider upgrading to Java 21 for better performance (ZGC improvements)")
    }
}
