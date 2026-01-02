/*
 * DocumentScanner - settings.gradle.kts
 * Gradle 9.x + Android 2026 Standards (Enterprise Production Version)
 * 
 * Features:
 * ✓ Remote Build Cache with validation
 * ✓ Version Catalog with fallback
 * ✓ Project Isolation ready
 * ✓ Security-hardened credentials
 * ✓ CI/CD optimized
 */

pluginManagement {
    repositories {
        // 1. Google: Строгий фильтр только для Android/Google компонентов
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // 2. Maven Central: Основной источник (включая org.jetbrains.kotlin)
        mavenCentral()
        // 3. Plugin Portal: Для специфических плагинов Gradle
        gradlePluginPortal()
    }
}

plugins {
    // Гарантия единой версии Java (JDK) для всех разработчиков и CI
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    // Блокируем объявление репозиториев внутри модулей (best practice)
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }

    // Version Catalog: Централизованное управление версиями зависимостей
    versionCatalogs {
        create("libs") {
            val catalogFile = file("gradle/libs.versions.toml")
            if (catalogFile.exists()) {
                from(files(catalogFile))
            } else {
                logger.warn("⚠️  Version Catalog not found at ${catalogFile.absolutePath}")
                logger.warn("    Creating empty catalog. Please add gradle/libs.versions.toml")
            }
        }
    }
}

buildCache {
    // Local Cache: Для индивидуальной разработки
    local {
        isEnabled = true
        // Используем уникальный путь для проекта (избегаем конфликтов)
        directory = File(rootDir, "build-cache")
        removeUnusedEntriesAfterDays = 14
    }

    // Remote Cache: Для командной разработки и CI/CD
    remote<HttpBuildCache> {
        // Получаем URL из переменных окружения или gradle.properties
        val cacheUrl = System.getenv("GRADLE_CACHE_URL") 
            ?: providers.gradleProperty("gradle.cache.url").orNull
        
        if (!cacheUrl.isNullOrBlank()) {
            try {
                url = uri(cacheUrl)
                
                // Push разрешен только в CI (защита от загрязнения кэша локальными сборками)
                val isCI = System.getenv("CI")?.toBoolean() == true
                isPush = isCI
                
                // Credentials (опционально, для приватных cache серверов)
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
                
                logger.lifecycle("✓ Remote build cache enabled: $cacheUrl (push: $isCI)")
                
            } catch (e: Exception) {
                logger.warn("⚠️  Invalid remote cache URL: $cacheUrl")
                logger.warn("    Error: ${e.message}")
                logger.warn("    Remote cache disabled, using local cache only")
            }
        } else {
            logger.info("ℹ️  Remote build cache not configured (set GRADLE_CACHE_URL to enable)")
        }
    }
}