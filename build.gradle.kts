// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.android.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.dependency.analysis)
}

// Dependency analysis, reporting only: `./gradlew buildHealth --max-workers=2` writes
// build/reports/dependency-analysis/build-health-report.txt. No other task depends on it
// and its advice never fails a build. Each analysed module applies the plugin too.
// Findings and known false positives: docs/architecture-baseline.md#dependencies.
dependencyAnalysis {
    issues {
        all {
            onAny { severity("warn") }
        }
    }
}

// Formatting: ktlint via Spotless, rules in .editorconfig. Rolled out without a mass
// reformat: only files changed since the ratchet ref are checked, and `check`/`build`
// never run it. -PspotlessRatchetFrom=<ref> compares against another ref; an empty
// value checks every file.
spotless {
    providers.gradleProperty("spotlessRatchetFrom").getOrElse("origin/main")
        .takeIf { it.isNotBlank() }
        ?.let { ratchetFrom(it) }
    isEnforceCheck = false

    kotlin {
        target("app/src/**/*.kt", "core/*/src/**/*.kt", "parser-core/src/**/*.kt", "shared/src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts", "core/*/*.gradle.kts")
        // A separate Gradle build with its own conventions.
        targetExclude("pennywise-web/**")
        ktlint(libs.versions.ktlint.get())
    }
}
