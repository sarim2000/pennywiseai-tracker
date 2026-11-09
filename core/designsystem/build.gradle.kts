// PennyWise's theme, design tokens and fonts. Depends on no project module.
// See docs/architecture.md for what belongs here.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dependency.analysis)
}

android {
    namespace = "com.pennywiseai.core.designsystem"
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

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // Theme.kt builds on MaterialExpressiveTheme and MotionScheme.expressive().
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    // ColorScheme, Typography and Shapes are part of this module's public API.
    api(libs.androidx.material3)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.core) // WindowCompat

    testImplementation(libs.junit)
}
