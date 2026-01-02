/*
 * DocumentScanner - settings.gradle.kts
 * Gradle 9.x + Android 2026 Standards (Enterprise Production Version)
 * Version: 5.0.0 - Final Gold Master
 */

pluginManagement {
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
                isPush = (System.getenv("CI")?.toBoolean() == true)
                
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

rootProject.name = "DocumentScanner"
include(":app")

// ================================================================================
// STARTUP CHECKS
// ================================================================================
gradle.settingsEvaluated {
    // 1. File Existence Check
    val filesToCheck = mapOf(
        "gradle/libs.versions.toml" to "ERROR",
        "gradle.properties" to "ERROR",
        "local.properties" to "WARNING"
    )
    
    filesToCheck.forEach { (path, level) ->
        if (!rootDir.resolve(path).exists()) {
            val msg = "Missing file: $path"
            if (level == "ERROR") throw GradleException("❌ $msg") 
            else logger.warn("⚠️  $msg")
        }
    }
    
    // 2. Environment Info
    logger.lifecycle("🚀 DocumentScanner Build: JDK ${System.getProperty("java.version")}")
}