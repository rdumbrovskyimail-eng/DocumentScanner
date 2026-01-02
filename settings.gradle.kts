/*
 * DocumentScanner - settings.gradle.kts
 * Gradle 9.x + Android 2026 Standards (Enterprise Production Version)
 * Includes: Remote Build Cache, Version Catalog, Project Isolation
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
            from(files("gradle/libs.versions.toml"))
        }
    }
}

buildCache {
    // Local Cache: Для индивидуальной разработки
    local {
        isEnabled = true
        directory = File(rootDir, ".gradle/build-cache")
        removeUnusedEntriesAfterDays = 14
    }

    // Remote Cache: Для командной разработки и CI/CD
    remote<HttpBuildCache> {
        // Активируется только если задан URL (например, через gradle.properties или CI env)
        val cacheUrl = System.getenv("GRADLE_CACHE_URL") 
            ?: providers.gradleProperty("gradle.cache.url").orNull
        
        if (cacheUrl != null) {
            url = uri(cacheUrl)
            
            // Push разрешен только в CI (защита от загрязнения кэша локальными сборками)
            isPush = (System.getenv("CI")?.toBoolean() == true)
            
            // Credentials (опционально, для приватных cache серверов)
            credentials {
                username = System.getenv("GRADLE_CACHE_USER") 
                    ?: providers.gradleProperty("gradle.cache.user").orNull
                password = System.getenv("GRADLE_CACHE_PASSWORD") 
                    ?: providers.gradleProperty("gradle.cache.password").orNull
            }
        }
    }
}

rootProject.name = "DocumentScanner"
include(":app")

// ═══════════════════════════════════════════════════════════════════════════
// GRADLE 9+ ОПТИМИЗАЦИИ (активируются через gradle.properties)
// ═══════════════════════════════════════════════════════════════════════════
// 
// Добавьте в gradle.properties для максимальной производительности:
//
// # Configuration Cache (по умолчанию включен в Gradle 9+)
// org.gradle.configuration-cache=true
//
// # Project Isolation (ускоряет конфигурацию до 40%)
// org.gradle.unsafe.isolated-projects=true
//
// # Remote Build Cache URL (для CI/CD)
// # gradle.cache.url=https://your-cache-server.com/cache/
// # gradle.cache.user=ci_user
// # gradle.cache.password=secret_token
//
// ═══════════════════════════════════════════════════════════════════════════