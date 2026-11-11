pluginManagement {
    // Convention plugins (pennywise.android.*), see docs/architecture.md.
    includeBuild("build-logic")
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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "pennywiseai-tracker"
include(":app")
include(":parser-core")
include(":shared")
include(":core:designsystem")
include(":feature:exchangerates")
include(":feature:learnedcategories")
include(":iosApp")
include(":baselineprofile")
