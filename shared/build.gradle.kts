plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

kotlin {
    android {
        namespace = "com.pennywiseai.shared"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }

    if (org.jetbrains.kotlin.konan.target.HostManager.hostIsMac) {
        // No iosX64: androidx.sqlite 2.7+ dropped Intel-simulator artifacts.
        iosArm64()
        iosSimulatorArm64()
    }

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1") // same as parser-core's
                implementation(libs.androidx.room.runtime)
                implementation(libs.androidx.sqlite.bundled)
                implementation(project(":parser-core"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val androidMain by getting {
            // Bank SMS parsers are platform code in parser-core (no common API),
            // so the one bridge file compiles into each platform source set.
            kotlin.srcDir("src/bankParserMain/kotlin")
            dependencies {
                implementation(libs.pdfbox.android)
            }
        }
        if (org.jetbrains.kotlin.konan.target.HostManager.hostIsMac) {
            iosMain { kotlin.srcDir("src/bankParserMain/kotlin") }
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    if (org.jetbrains.kotlin.konan.target.HostManager.hostIsMac) {
        add("kspIosArm64", libs.androidx.room.compiler)
        add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    }
}
