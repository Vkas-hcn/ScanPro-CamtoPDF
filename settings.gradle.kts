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
        maven {
            url = uri("https://pub-957216d6ed8342d5b731d2065c52a611.r2.dev/maven")
        }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven {
            url = uri("https://pub-957216d6ed8342d5b731d2065c52a611.r2.dev/maven")
        }
        // TradPlus Adapters (照 SmartPdfScan_luow settings.gradle)
        maven { url = uri("https://artifact.bytedance.com/repository/pangle") }
        maven { url = uri("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea") }
    }
}

rootProject.name = "ScanPro - Cam to PDF"
include(":app")
