// An Android library that renders Compose: the SDK, JVM and Compose settings every
// such module shares with :app. Namespace and dependencies stay in the module.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

// Coverage, advisory: only with -Pcoverage, so the normal build stays uninstrumented.
// ./gradlew -Pcoverage :<module>:koverXmlReportDebug. See docs/architecture.md.
// A bare -Pcoverage or -Pcoverage=true turns it on; -Pcoverage=false leaves it off.
if (providers.gradleProperty("coverage").map { it.isEmpty() || it.toBoolean() }.getOrElse(false)) {
    apply(plugin = "org.jetbrains.kotlinx.kover")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // PennyWiseTheme builds on MaterialExpressiveTheme and MotionScheme.expressive().
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }
}

// Same stability config as :app, so BigDecimal / java.time fields (e.g. ExchangeRateSnapshot)
// don't make a module's composables unstable.
composeCompiler {
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose_compiler_config.conf"))
}
