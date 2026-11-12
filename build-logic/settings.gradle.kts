// Convention plugins for the Android library modules, included from the root
// settings.gradle.kts. Shares the root version catalog, so plugin versions live in
// one place. See docs/architecture.md.
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
