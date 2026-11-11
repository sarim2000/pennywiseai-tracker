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

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // PennyWiseTheme builds on MaterialExpressiveTheme and MotionScheme.expressive().
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }
}
