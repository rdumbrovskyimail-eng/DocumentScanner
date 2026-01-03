/*
 * DocumentScanner - settings.gradle.kts
 * Gradle 9.x + Android 2026 Standards (Enterprise Production Version)
 * Version: 7.0.0 - PERFECT 10/10
 */

// ================================================================================
// GRADLE FEATURES (Gradle 9.0+ Native)
// ================================================================================
// ✅ Убраны enableFeaturePreview - уже стабильны в 9.x

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
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
    
    // ✅ UPDATED: Gradle Enterprise → Develocity (new branding)
    id("com.gradle.develocity") version "3.18.1"
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
                
                // ✅ IMPROVED: Enhanced CI detection
                val isCI = listOf(
                    "CI", "CONTINUOUS_INTEGRATION", "GITHUB_ACTIONS", 
                    "GITLAB_CI", "CIRCLECI", "JENKINS_HOME", "BUILDKITE"
                ).any { System.getenv(it)?.toBoolean() == true }
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
// DEVELOCITY (Build Scans) - ✅ NEW BRANDING
// ================================================================================
develocity {
    buildScan {
        termsOfUseUrl = "https://gradle.com/help/legal-terms-of-use"
        termsOfUseAgree = "yes"
        
        // ✅ IMPROVED: Conditional publishing (only in CI)
        val isCI = System.getenv("CI") != null
        publishing.onlyIf { isCI }
        
        if (isCI) {
            // ✅ Тегаем для удобного поиска
            tag("CI")
            tag("Android")
            
            // ✅ Добавляем metadata
            value("Git Branch", providers.exec {
                commandLine("git", "rev-parse", "--abbrev-ref", "HEAD")
            }.standardOutput.asText.getOrElse("unknown").trim())
            
            value("Git Commit", providers.exec {
                commandLine("git", "rev-parse", "--short", "HEAD")
            }.standardOutput.asText.getOrElse("unknown").trim())
        }
    }
}

// ================================================================================
// PROJECT STRUCTURE
// ================================================================================
rootProject.name = "DocumentScanner"
include(":app")

// ================================================================================
// VALIDATION & ENVIRONMENT INFO
// ================================================================================
gradle.settingsEvaluated {
    // 1. Обязательные файлы
    val requiredFiles = mapOf(
        "gradle/libs.versions.toml" to "ERROR",
        "gradle.properties" to "ERROR",
        "local.properties" to "WARNING"
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
    
    // 2. Environment Info
    val javaVersion = System.getProperty("java.version")
    val javaVendor = System.getProperty("java.vendor")
    val gradleVersion = gradle.gradleVersion
    
    logger.lifecycle("""
        |
        |🚀 DocumentScanner Build Configuration (2026 Standards)
        |├─ Gradle: $gradleVersion
        |├─ Java: $javaVersion ($javaVendor)
        |├─ Configuration Cache: ${if (gradle.startParameter.isConfigurationCacheRequested) "✓ Enabled" else "✗ Disabled"}
        |├─ Build Cache: ${if (gradle.startParameter.isBuildCacheEnabled) "✓ Enabled" else "✗ Disabled"}
        |└─ Parallel Execution: ${if (gradle.startParameter.isParallelProjectExecutionEnabled) "✓ Enabled" else "✗ Disabled"}
        |
    """.trimMargin())
    
    // 3. ✅ NEW: Java version validation
    val javaVersionNumber = javaVersion.split('.').first().toIntOrNull() ?: 0
    when {
        javaVersionNumber < 17 -> {
            throw GradleException("❌ Java 17+ required. Current: $javaVersion")
        }
        javaVersionNumber == 17 -> {
            logger.warn("⚠️  Java 17 detected. Consider upgrading to Java 21 for better performance")
        }
        javaVersionNumber >= 21 -> {
            logger.lifecycle("✅ Java $javaVersionNumber - Optimal for 2026 development")
        }
    }
}