import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
}

android {
    namespace = "com.pennywiseai.tracker"
    compileSdk = 36
    
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.pennywiseai.tracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 81
        versionName = "2.15.46"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Load RSA public key from local.properties
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            val localProperties = Properties()
            localProperties.load(localPropertiesFile.inputStream())
            
            val rsaPublicKey = localProperties.getProperty("RSA_PUBLIC_KEY", "")
            buildConfigField("String", "RSA_PUBLIC_KEY", "\"$rsaPublicKey\"")
        } else {
            // Fallback empty key for CI/CD builds
            buildConfigField("String", "RSA_PUBLIC_KEY", "\"\"")
        }
    }

    signingConfigs {
        // Only create signing config for non-F-Droid builds
        if (!gradle.startParameter.taskNames.any { it.contains("fdroid", ignoreCase = true) }) {
            val localPropertiesFile = rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                create("release") {
                    val localProperties = Properties()
                    localProperties.load(localPropertiesFile.inputStream())
                    
                    val keystorePath = localProperties.getProperty("RELEASE_STORE_FILE", "")
                    if (keystorePath.isNotEmpty()) {
                        storeFile = file(keystorePath)
                        storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD", "")
                        keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS", "")
                        keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD", "")
                    }
                }
            }
        }
    }
    
    flavorDimensions += "version"
    productFlavors {
        create("fdroid") {
            dimension = "version"
            // F-Droid builds will use their own signing
            // Only include ARM architectures for F-Droid (no x86 emulator support)
            ndk {
                abiFilters += setOf("arm64-v8a", "armeabi-v7a")
            }
        }
        create("standard") {
            dimension = "version"
            isDefault = true
            // Standard flavor includes all architectures (including x86 for emulators)
        }
    }

    splits {
        abi {
            // Disable splits for F-Droid builds and bundle builds
            //noinspection WrongGradleMethod
            val runTasks = gradle.startParameter.taskNames.map { it.lowercase() }
            //noinspection WrongGradleMethod
            val isBundleBuild = runTasks.any { it.contains("bundle") }   // e.g., :app:bundleRelease
            //noinspection WrongGradleMethod
            val isFdroidBuild = runTasks.any { it.contains("fdroid") }

            isEnable = !(isBundleBuild || isFdroidBuild)

            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }


    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // Only apply signing config to standard flavor
            for (flavor in productFlavors) {
                if (flavor.name == "standard") {
                    // Check if release signing config exists
                    val releaseSigningConfig = signingConfigs.findByName("release")
                    // Only use release signing if keystore is configured
                    if (releaseSigningConfig != null && releaseSigningConfig.storeFile != null) {
                        signingConfig = releaseSigningConfig
                    }
                }
            }
            
            // Include debug symbols for native crashes
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

// Configure Room schema export
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

// Copy changelog from fastlane to generated assets for What's New dialog
val generatedAssetsDir = layout.buildDirectory.dir("generated/assets/changelog")

tasks.register<Copy>("copyChangelog") {
    val versionCode = android.defaultConfig.versionCode
    val changelogDir = rootProject.file("fastlane/metadata/android/en-US/changelogs")
    val changelogFile = file("$changelogDir/$versionCode.txt")
    val defaultFile = file("$changelogDir/default.txt")

    from(if (changelogFile.exists()) changelogFile else defaultFile)
    into(generatedAssetsDir)
    rename { "whats_new.txt" }
}

android.sourceSets["main"].assets.srcDir(generatedAssetsDir)

tasks.matching { it.name.startsWith("merge") && it.name.contains("Assets") }.configureEach {
    dependsOn("copyChangelog")
}

// Lint tasks also need to depend on copyChangelog since they analyze generated assets
tasks.matching { it.name.contains("lint") || it.name.contains("Lint") }.configureEach {
    dependsOn("copyChangelog")
}

dependencies {
    // Local modules
    implementation(project(":parser-core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    
    // Material Icons Extended
    implementation(libs.androidx.compose.material.icons.extended)
    
    // Color Picker for Compose
    implementation(libs.colorpicker.compose)
    
    // Splash Screen API
    implementation(libs.androidx.core.splashscreen)
    
    // Lifecycle and ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    
    // Kotlin Serialization
    implementation(libs.kotlinx.serialization.json)

    // Ktor for HTTP requests
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    
    // Gson for backup/restore
    implementation(libs.gson)
    
    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Biometric Authentication
    implementation(libs.androidx.biometric)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    
    // Hilt WorkManager integration
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Glance Widget
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // MediaPipe for LLM inference
    implementation(libs.tasks.genai)
    
    // Google Play In-App Updates (only for standard flavor)
    "standardImplementation"(libs.app.update)
    "standardImplementation"(libs.app.update.ktx)
    
    // Google Play In-App Reviews (only for standard flavor)
    "standardImplementation"(libs.review)
    "standardImplementation"(libs.review.ktx)
    
    testImplementation(libs.junit)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    
    // Markdown support
    implementation(libs.markdown)
    
    // OpenCSV for CSV export
    implementation(libs.opencsv)
    testImplementation(kotlin("test"))
}
