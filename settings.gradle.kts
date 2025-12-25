pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// ✅ ИСПРАВЛЕНО: Упрощённая конфигурация Build Cache
buildCache {
    local {
        isEnabled = true
        directory = File(rootDir, "build-cache")
    }
}

rootProject.name = "DocumentScanner"
include(":app")
